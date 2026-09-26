package io.blockdesigner.render;

import io.blockdesigner.assets.TextureAtlas;
import io.blockdesigner.assets.model.RenderLayer;
import io.blockdesigner.render.gl.Shader;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Owns an OpenGL 3.3 context on a dedicated thread and renders {@link FrameRequest}s offscreen (MSAA), handing
 * finished frames back as ARGB pixel buffers (top row first). All GL objects live and die on that thread.
 */
public final class ViewportRenderer implements AutoCloseable {
    /** A finished frame. Pixels are premultiplied ARGB ints, top row first; return it with {@link #recycle}. */
    public record Frame(IntBuffer pixels, int width, int height, long sequence) {
    }

    private static final int SAMPLES = 4;
    private static final Runnable WAKE = () -> {
    };

    private final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final AtomicReference<FrameRequest> pending = new AtomicReference<>();
    private final Consumer<Frame> onFrame;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile String glInfo = "";
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    // ---- render-thread state ------------------------------------------------------------------------------------
    private long window;
    private Shader blockShader, skyShader, gridShader, lineShader;
    private int atlasTex, emptyVao, gridVao, gridVbo, lineVao, lineVbo, ebo;
    private int eboQuads;
    private int msaaFbo, msaaColor, msaaDepth, resolveFbo, resolveColor;
    private int fbWidth, fbHeight;
    private final Map<String, Map<Long, GpuSection>> meshes = new HashMap<>();
    private final List<IntBuffer> pool = new ArrayList<>();
    private IntBuffer readBuf;
    // Per-frame scratch for frustum culling (render thread only).
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f cullVp = new Matrix4f(), cullModel = new Matrix4f(), cullMvp = new Matrix4f();
    private int sectionsDrawn, sectionsTotal;

    private static final class GpuSection {
        final int[] vao = new int[3], vbo = new int[3], quads = new int[3];
        float cx, cy, cz;
        /** Half size of the box culled against the view (8 for a 16³ section). */
        float hx = 8, hy = 8, hz = 8;
        float sortKey;
    }

    public ViewportRenderer(TextureAtlas atlas, Consumer<Frame> onFrame) {
        this.onFrame = onFrame;
        this.thread = Thread.ofPlatform().name("blockdesigner-render").daemon().unstarted(() -> run(atlas));
        thread.start();
    }

    /** Completes when the GL context is up (or exceptionally if it failed). */
    public CompletableFuture<Void> ready() {
        return ready;
    }

    public String glInfo() {
        return glInfo;
    }

    /** Replaces any not-yet-rendered request; only the newest frame is drawn. */
    public void requestFrame(FrameRequest r) {
        pending.set(r);
        tasks.offer(WAKE);
    }

    /** Renders one frame outside the viewport flow (e.g. for screenshots). */
    public CompletableFuture<Frame> capture(FrameRequest r) {
        CompletableFuture<Frame> f = new CompletableFuture<>();
        tasks.offer(() -> {
            try {
                f.complete(render(r, false));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    public void uploadSection(String layerId, long sectionKey, MeshData data, float cx, float cy, float cz) {
        tasks.offer(() -> upload(layerId, sectionKey, data, cx, cy, cz, 8, 8, 8));
    }

    /** Uploads a mesh whose contents fit a box of half size {@code hx, hy, hz} around the centre (entity meshes). */
    public void uploadMesh(String layerId, long key, MeshData data, float cx, float cy, float cz, float hx, float hy, float hz) {
        tasks.offer(() -> upload(layerId, key, data, cx, cy, cz, hx, hy, hz));
    }

    public void removeSection(String layerId, long sectionKey) {
        tasks.offer(() -> {
            Map<Long, GpuSection> m = meshes.get(layerId);
            if (m != null) delete(m.remove(sectionKey));
        });
    }

    public void removeLayer(String layerId) {
        tasks.offer(() -> {
            Map<Long, GpuSection> m = meshes.remove(layerId);
            if (m != null) m.values().forEach(this::delete);
        });
    }

    /** Returns a frame's buffer to the pool once its pixels have been copied. */
    public void recycle(Frame f) {
        tasks.offer(() -> {
            if (pool.size() < 3) pool.add(f.pixels());
        });
    }

    @Override
    public void close() {
        running = false;
        tasks.offer(WAKE);
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- render thread ------------------------------------------------------------------------------------------

    private void run(TextureAtlas atlas) {
        try {
            initContext();
            initResources(atlas);
            ready.complete(null);
        } catch (Throwable t) {
            ready.completeExceptionally(t);
            return;
        }
        try {
            while (running) {
                Runnable t = tasks.poll(100, TimeUnit.MILLISECONDS);
                while (t != null) {
                    t.run();
                    t = tasks.poll();
                }
                FrameRequest r = pending.getAndSet(null);
                if (r != null && running) {
                    Frame f = render(r, true);
                    if (f != null) onFrame.accept(f);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            dispose();
        }
    }

    private void initContext() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        window = glfwCreateWindow(16, 16, "BlockDesigner GL", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Could not create an OpenGL 3.3 context");
        glfwMakeContextCurrent(window);
        GLCapabilities caps = GL.createCapabilities();
        glInfo = glGetString(GL_RENDERER) + " · OpenGL " + glGetString(GL_VERSION);
        if (!caps.OpenGL33) throw new IllegalStateException("OpenGL 3.3 required, got " + glInfo);
    }

    private void initResources(TextureAtlas atlas) {
        blockShader = new Shader("block");
        skyShader = new Shader("sky");
        gridShader = new Shader("grid");
        lineShader = new Shader("line");
        emptyVao = glGenVertexArrays();

        // Atlas: ARGB ints -> RGBA bytes, nearest magnification for crisp pixels, mipmaps for distance.
        int[] px = atlas.pixels();
        ByteBuffer rgba = ByteBuffer.allocateDirect(px.length * 4).order(ByteOrder.nativeOrder());
        for (int p : px) rgba.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        rgba.flip();
        atlasTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, atlasTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, atlas.width(), atlas.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 4);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8f, glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)));
        }

        gridVao = glGenVertexArrays();
        gridVbo = glGenBuffers();
        glBindVertexArray(gridVao);
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
        glBufferData(GL_ARRAY_BUFFER, new float[]{-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1}, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);

        lineVao = glGenVertexArrays();
        lineVbo = glGenBuffers();
        glBindVertexArray(lineVao);
        glBindBuffer(GL_ARRAY_BUFFER, lineVbo);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, 16, 12);
        glBindVertexArray(0);

        ebo = glGenBuffers();
        ensureIndexCapacity(65536);
    }

    /** One shared quad index buffer (0,1,2, 0,2,3 per quad), rebound into every VAO when it grows. */
    private void ensureIndexCapacity(int quads) {
        if (quads <= eboQuads) return;
        int cap = Math.max(quads, eboQuads * 2);
        IntBuffer idx = ByteBuffer.allocateDirect(cap * 6 * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int q = 0; q < cap; q++) {
            int b = q * 4;
            idx.put(b).put(b + 1).put(b + 2).put(b).put(b + 2).put(b + 3);
        }
        idx.flip();
        glBindVertexArray(0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
        eboQuads = cap;
        for (Map<Long, GpuSection> m : meshes.values()) {
            for (GpuSection s : m.values()) {
                for (int vao : s.vao) {
                    if (vao == 0) continue;
                    glBindVertexArray(vao);
                    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
                }
            }
        }
        glBindVertexArray(0);
    }

    private void upload(String layerId, long key, MeshData data, float cx, float cy, float cz, float hx, float hy, float hz) {
        Map<Long, GpuSection> m = meshes.computeIfAbsent(layerId, k -> new HashMap<>());
        delete(m.remove(key));
        if (data.isEmpty()) return;
        GpuSection s = new GpuSection();
        s.cx = cx;
        s.cy = cy;
        s.cz = cz;
        s.hx = hx;
        s.hy = hy;
        s.hz = hz;
        for (RenderLayer rl : RenderLayer.values()) {
            int quads = data.quadCount(rl);
            if (quads == 0) continue;
            ensureIndexCapacity(quads);
            int i = rl.ordinal();
            s.vao[i] = glGenVertexArrays();
            s.vbo[i] = glGenBuffers();
            s.quads[i] = quads;
            glBindVertexArray(s.vao[i]);
            glBindBuffer(GL_ARRAY_BUFFER, s.vbo[i]);
            ByteBuffer src = data.vertices(rl);
            // Staged through malloc'd memory: allocateDirect per upload is slow and leaves cleanup to the GC.
            ByteBuffer direct = MemoryUtil.memAlloc(src.remaining());
            try {
                direct.put(src).flip();
                glBufferData(GL_ARRAY_BUFFER, direct, GL_STATIC_DRAW);
            } finally {
                MemoryUtil.memFree(direct);
            }
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, MeshData.STRIDE, 0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, MeshData.STRIDE, 12);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, MeshData.STRIDE, 20);
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 3, GL_BYTE, true, MeshData.STRIDE, 24);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        }
        glBindVertexArray(0);
        m.put(key, s);
    }

    private void delete(GpuSection s) {
        if (s == null) return;
        for (int i = 0; i < 3; i++) {
            if (s.vao[i] != 0) glDeleteVertexArrays(s.vao[i]);
            if (s.vbo[i] != 0) glDeleteBuffers(s.vbo[i]);
        }
    }

    private void ensureFramebuffer(int w, int h) {
        if (w == fbWidth && h == fbHeight && msaaFbo != 0) return;
        if (msaaFbo != 0) {
            glDeleteFramebuffers(msaaFbo);
            glDeleteRenderbuffers(msaaColor);
            glDeleteRenderbuffers(msaaDepth);
            glDeleteFramebuffers(resolveFbo);
            glDeleteRenderbuffers(resolveColor);
        }
        int samples = Math.min(SAMPLES, glGetInteger(GL_MAX_SAMPLES));
        msaaFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        msaaColor = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msaaColor);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColor);
        msaaDepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msaaDepth);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, msaaDepth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("MSAA framebuffer incomplete");

        resolveFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
        resolveColor = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, resolveColor);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, resolveColor);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        fbWidth = w;
        fbHeight = h;
    }

    private Frame render(FrameRequest r, boolean pooled) {
        int w = Math.max(1, r.width()), h = Math.max(1, r.height());
        ensureFramebuffer(w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        glViewport(0, 0, w, h);
        glColorMask(true, true, true, true);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        // Keep destination alpha at 1 so the frame is opaque for the UI compositor.
        glColorMask(true, true, true, false);

        float[] vp = r.viewProj();
        FrameRequest.Theme theme = r.theme();

        // 1. Sky gradient
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        skyShader.use();
        skyShader.setMat4("uInvViewProj", invert(vp));
        skyShader.setRgb("uZenith", theme.zenith());
        skyShader.setRgb("uHorizon", theme.horizon());
        skyShader.setRgb("uGround", theme.ground());
        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        // 2. Opaque + cutout geometry
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);
        blockShader.use();
        blockShader.set("uAtlas", 0);
        blockShader.setMat4("uViewProj", vp);
        blockShader.set("uSunDir", 0.45f, 0.8f, 0.35f);
        blockShader.set("uEye", r.eye()[0], r.eye()[1], r.eye()[2]);
        blockShader.setRgb("uFogColor", theme.horizon());
        blockShader.set("uFogDistance", Float.isFinite(r.fogDistance()) ? r.fogDistance() : 1e9f);

        List<FrameRequest.LayerDraw> solidLayers = new ArrayList<>(), ghostLayers = new ArrayList<>();
        for (FrameRequest.LayerDraw ld : r.layers()) (ld.opacity() < 0.999f ? ghostLayers : solidLayers).add(ld);
        // Frustum-cull each draw's sections once; every pass below reuses the result.
        Map<FrameRequest.LayerDraw, List<GpuSection>> visible = new java.util.IdentityHashMap<>();
        sectionsDrawn = sectionsTotal = 0;
        cullVp.set(vp);
        for (FrameRequest.LayerDraw ld : r.layers()) visible.put(ld, visibleSections(ld));

        for (FrameRequest.LayerDraw ld : solidLayers) {
            bindLayer(ld, 1f);
            drawLayer(visible.get(ld), RenderLayer.SOLID, 0f);
            drawLayer(visible.get(ld), RenderLayer.CUTOUT, 0.5f);
        }

        // 3. Ground grid (blended, no depth writes so it never hides blocks)
        glFrontFace(GL_CCW);
        if (r.showGrid()) {
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);
            gridShader.use();
            gridShader.setMat4("uViewProj", vp);
            float ext = Math.max(64f, Math.min(1024f, dist(r.eye(), r.gridCenter()[0], r.gridY(), r.gridCenter()[1]) * 3f));
            gridShader.set("uCenter", r.gridCenter()[0], r.gridY(), r.gridCenter()[1]);
            gridShader.set("uExtent", ext);
            gridShader.set("uHeight", r.gridY());
            gridShader.setRgb("uMinorColor", theme.gridMinor());
            gridShader.setRgb("uMajorColor", theme.gridMajor());
            gridShader.setRgb("uAxisXColor", theme.axisX());
            gridShader.setRgb("uAxisZColor", theme.axisZ());
            glBindVertexArray(gridVao);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            glEnable(GL_CULL_FACE);
        }

        // 4. Translucent geometry, farthest sections first
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        blockShader.use();
        for (FrameRequest.LayerDraw ld : solidLayers) {
            bindLayer(ld, 1f);
            drawSorted(ld, visible.get(ld), r.eye());
        }

        // 5. Ghost layers: depth prepass so only the nearest surface shows, then a blended colour pass
        for (FrameRequest.LayerDraw ld : ghostLayers) {
            glDepthMask(true);
            glColorMask(false, false, false, false);
            List<GpuSection> vis = visible.get(ld);
            bindLayer(ld, 1f);
            drawLayer(vis, RenderLayer.SOLID, 0f);
            drawLayer(vis, RenderLayer.CUTOUT, 0.5f);
            glColorMask(true, true, true, false);
            glDepthMask(false);
            glDepthFunc(GL_EQUAL);
            bindLayer(ld, ld.opacity());
            drawLayer(vis, RenderLayer.SOLID, 0f);
            drawLayer(vis, RenderLayer.CUTOUT, 0.5f);
            glDepthFunc(GL_LEQUAL);
            drawSorted(ld, vis, r.eye());
        }
        glFrontFace(GL_CCW);

        // 6. Overlay lines: a faint always-visible pass, then a crisp depth-tested pass
        if (!r.lines().isEmpty()) drawLines(r.lines(), vp);

        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);

        // 7. Resolve MSAA and read back
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
        glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, resolveFbo);
        IntBuffer out = takeBuffer(w * h, pooled);
        if (readBuf == null || readBuf.capacity() != w * h) readBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        IntBuffer tmp = readBuf;
        tmp.clear();
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, tmp);
        // GL rows are bottom-up; flip into the output buffer.
        for (int y = 0; y < h; y++) {
            tmp.limit((h - y) * w).position((h - 1 - y) * w);
            out.position(y * w);
            out.put(tmp);
        }
        tmp.clear();
        out.clear();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return new Frame(out, w, h, r.sequence());
    }

    private IntBuffer takeBuffer(int size, boolean pooled) {
        if (pooled) {
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i).capacity() == size) return pool.remove(i);
            }
        }
        return ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    private void bindLayer(FrameRequest.LayerDraw ld, float opacity) {
        blockShader.setMat4("uModel", ld.model());
        blockShader.set("uOpacity", opacity);
        float strength = ld.tintStrength();
        blockShader.set("uTint", ((ld.tintRgb() >> 16) & 255) / 255f, ((ld.tintRgb() >> 8) & 255) / 255f, (ld.tintRgb() & 255) / 255f, strength);
        glFrontFace(ld.mirrored() ? GL_CW : GL_CCW);
    }

    /** Sections of a draw whose 16³ box intersects the view frustum (in the draw's model space). */
    private List<GpuSection> visibleSections(FrameRequest.LayerDraw ld) {
        Map<Long, GpuSection> m = meshes.get(ld.layerId());
        if (m == null || m.isEmpty()) return List.of();
        cullModel.set(ld.model());
        cullVp.mul(cullModel, cullMvp);
        frustum.set(cullMvp, false);
        List<GpuSection> out = new ArrayList<>(m.size());
        for (GpuSection s : m.values()) {
            if (frustum.testAab(s.cx - s.hx, s.cy - s.hy, s.cz - s.hz, s.cx + s.hx, s.cy + s.hy, s.cz + s.hz)) out.add(s);
        }
        sectionsTotal += m.size();
        sectionsDrawn += out.size();
        return out;
    }

    /** Sections drawn / held in the last frame (after frustum culling). Render thread value; for diagnostics. */
    public String cullStats() {
        return sectionsDrawn + "/" + sectionsTotal;
    }

    private void drawLayer(List<GpuSection> sections, RenderLayer rl, float cutoff) {
        if (sections.isEmpty()) return;
        blockShader.set("uAlphaCutoff", cutoff);
        int i = rl.ordinal();
        for (GpuSection s : sections) {
            if (s.quads[i] == 0) continue;
            glBindVertexArray(s.vao[i]);
            glDrawElements(GL_TRIANGLES, s.quads[i] * 6, GL_UNSIGNED_INT, 0);
        }
    }

    private void drawSorted(FrameRequest.LayerDraw ld, List<GpuSection> sections, float[] eye) {
        if (sections.isEmpty()) return;
        int i = RenderLayer.TRANSLUCENT.ordinal();
        float[] model = ld.model();
        List<GpuSection> list = new ArrayList<>();
        for (GpuSection s : sections) if (s.quads[i] > 0) list.add(s);
        if (list.isEmpty()) return;
        // Distance once per section, not per comparison.
        for (GpuSection s : list) s.sortKey = distTo(model, s, eye);
        list.sort((a, b) -> Float.compare(b.sortKey, a.sortKey));
        blockShader.set("uAlphaCutoff", 0.004f);
        glDisable(GL_CULL_FACE);
        for (GpuSection s : list) {
            glBindVertexArray(s.vao[i]);
            glDrawElements(GL_TRIANGLES, s.quads[i] * 6, GL_UNSIGNED_INT, 0);
        }
        glEnable(GL_CULL_FACE);
    }

    private static float distTo(float[] m, GpuSection s, float[] eye) {
        float x = m[0] * s.cx + m[4] * s.cy + m[8] * s.cz + m[12];
        float y = m[1] * s.cx + m[5] * s.cy + m[9] * s.cz + m[13];
        float z = m[2] * s.cx + m[6] * s.cy + m[10] * s.cz + m[14];
        return dist(eye, x, y, z);
    }

    private static float dist(float[] eye, float x, float y, float z) {
        float dx = eye[0] - x, dy = eye[1] - y, dz = eye[2] - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void drawLines(List<FrameRequest.Line> lines, float[] vp) {
        ByteBuffer buf = ByteBuffer.allocateDirect(lines.size() * 2 * 16).order(ByteOrder.nativeOrder());
        for (FrameRequest.Line l : lines) {
            int a = l.argb();
            buf.putFloat(l.x0()).putFloat(l.y0()).putFloat(l.z0()).put((byte) (a >> 16)).put((byte) (a >> 8)).put((byte) a).put((byte) (a >>> 24));
            buf.putFloat(l.x1()).putFloat(l.y1()).putFloat(l.z1()).put((byte) (a >> 16)).put((byte) (a >> 8)).put((byte) a).put((byte) (a >>> 24));
        }
        buf.flip();
        glBindBuffer(GL_ARRAY_BUFFER, lineVbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STREAM_DRAW);
        lineShader.use();
        lineShader.setMat4("uViewProj", vp);
        glBindVertexArray(lineVao);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        // Faint x-ray pass so outlines behind blocks stay readable
        glDisable(GL_DEPTH_TEST);
        glBlendColor(0, 0, 0, 0.25f);
        glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA);
        glDrawArrays(GL_LINES, 0, lines.size() * 2);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDrawArrays(GL_LINES, 0, lines.size() * 2);
    }

    private static float[] invert(float[] m) {
        org.joml.Matrix4f mat = new org.joml.Matrix4f();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.floats(m);
            mat.set(fb);
        }
        mat.invert();
        float[] out = new float[16];
        mat.get(out);
        return out;
    }

    private void dispose() {
        for (Map<Long, GpuSection> m : meshes.values()) m.values().forEach(this::delete);
        meshes.clear();
        if (window != NULL) {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
