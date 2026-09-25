package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.transform.Transform;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps GPU meshes in step with a {@link Scene}. Scene events mark sections dirty; {@link #sync()} (called on the
 * scene's owning thread, e.g. each UI pulse) snapshots them and meshes them on worker threads, then uploads.
 */
public final class SceneRenderer implements Scene.Listener, AutoCloseable {
    private static final int S = Structure.SECTION_SIZE;

    private final Scene scene;
    private final ViewportRenderer gpu;
    private final SectionMesher mesher;
    private final ExecutorService pool;
    private final Map<String, Set<Long>> dirty = new HashMap<>();
    private final Map<String, Map<Long, Integer>> versions = new HashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Runnable onMeshReady;

    /**
     * @param onMeshReady invoked from a worker thread after a mesh has been queued for upload; request a redraw
     */
    public SceneRenderer(Scene scene, BlockAssets assets, ViewportRenderer gpu, Runnable onMeshReady) {
        this.scene = scene;
        this.gpu = gpu;
        this.mesher = new SectionMesher(assets);
        this.onMeshReady = onMeshReady;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        this.pool = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("blockdesigner-mesher-", 0).daemon().factory());
        scene.addListener(this);
        for (Layer l : scene.layers()) markAll(l);
    }

    /** Sections still being meshed (for a progress indicator). */
    public int pending() {
        int d = 0;
        for (Set<Long> s : dirty.values()) d += s.size();
        return d + inFlight.get();
    }

    /** Sections being meshed right now; safe to call from any thread. */
    public int inFlight() {
        return inFlight.get();
    }

    // ---- Scene.Listener (owner thread) ------------------------------------------------------------------------

    @Override
    public void layerAdded(Layer layer, int index) {
        markAll(layer);
    }

    @Override
    public void layerRemoved(Layer layer) {
        dirty.remove(layer.id());
        synchronized (versions) {
            versions.remove(layer.id());
        }
        gpu.removeLayer(layer.id());
    }

    @Override
    public void blocksChanged(Layer layer, Box b) {
        Set<Long> set = dirty.computeIfAbsent(layer.id(), k -> new HashSet<>());
        // One block of padding: neighbours' culling and AO depend on the changed blocks.
        for (int sy = Math.floorDiv(b.minY() - 1, S); sy <= Math.floorDiv(b.maxY() + 1, S); sy++)
            for (int sz = Math.floorDiv(b.minZ() - 1, S); sz <= Math.floorDiv(b.maxZ() + 1, S); sz++)
                for (int sx = Math.floorDiv(b.minX() - 1, S); sx <= Math.floorDiv(b.maxX() + 1, S); sx++)
                    set.add(Structure.sectionKey(sx, sy, sz));
    }

    public void markAll(Layer layer) {
        dirty.computeIfAbsent(layer.id(), k -> new HashSet<>()).addAll(layer.structure().sectionKeys());
    }

    /** Re-meshes everything (e.g. after assets change). */
    public void rebuildAll() {
        for (Layer l : scene.layers()) {
            gpu.removeLayer(l.id());
            markAll(l);
        }
    }

    /** Snapshots dirty sections and hands them to the meshing pool. Call on the scene's owning thread. */
    public void sync() {
        if (dirty.isEmpty()) return;
        for (var e : dirty.entrySet()) {
            Layer layer = scene.find(e.getKey()).orElse(null);
            if (layer == null) continue;
            for (long key : e.getValue()) {
                BlockPos sp = BlockPos.unpack(key);
                SectionSnapshot snap = SectionSnapshot.capture(layer.structure(), sp.x(), sp.y(), sp.z());
                int v;
                synchronized (versions) {
                    v = versions.computeIfAbsent(layer.id(), k -> new HashMap<>()).merge(key, 1, Integer::sum);
                }
                String id = layer.id();
                inFlight.incrementAndGet();
                pool.submit(() -> {
                    try {
                        MeshData md = mesher.mesh(snap);
                        if (isCurrent(id, key, v)) {
                            gpu.uploadSection(id, key, md, sp.x() * S + 8f, sp.y() * S + 8f, sp.z() * S + 8f);
                        }
                    } finally {
                        inFlight.decrementAndGet();
                        onMeshReady.run();
                    }
                });
            }
        }
        dirty.clear();
    }

    private boolean isCurrent(String id, long key, int v) {
        synchronized (versions) {
            Map<Long, Integer> m = versions.get(id);
            Integer cur = m == null ? null : m.get(key);
            return cur != null && cur == v;
        }
    }

    // ---- frame building -------------------------------------------------------------------------------------

    /** Draw list for visible layers, bottom first. Ghost layers render translucent. */
    public List<FrameRequest.LayerDraw> layerDraws(String highlightLayerId) {
        List<FrameRequest.LayerDraw> out = new ArrayList<>();
        for (Layer l : scene.layers()) {
            if (!l.visible()) continue;
            boolean hl = l.id().equals(highlightLayerId);
            out.add(new FrameRequest.LayerDraw(l.id(), modelMatrix(l.offset(), l.transform()), l.transform().mirror() != Transform.Mirror.NONE,
                    l.ghost() ? 0.45f : 1f, l.ghost() ? 0x9FC2FF : 0xFFFFFF, l.ghost() ? 0.25f : hl ? 0.0f : 0f));
        }
        return out;
    }

    /** Extra draw for a floating placement preview of an existing layer's meshes at another position. */
    public static FrameRequest.LayerDraw ghostDraw(String layerId, BlockPos offset, Transform t, int tint) {
        return new FrameRequest.LayerDraw(layerId, modelMatrix(offset, t), t.mirror() != Transform.Mirror.NONE, 0.55f, tint, 0.3f);
    }

    /**
     * Continuous equivalent of {@code offset + transform(local)}: the lattice transform is applied about block
     * centres, so block (x, z) lands exactly on block {@code transform(x, z)}.
     */
    public static float[] modelMatrix(BlockPos offset, Transform t) {
        BlockPos ex = t.apply(1, 0, 0), ez = t.apply(0, 0, 1);
        Matrix4f r = new Matrix4f(
                ex.x(), 0, ex.z(), 0,
                0, 1, 0, 0,
                ez.x(), 0, ez.z(), 0,
                0, 0, 0, 1);
        Matrix4f m = new Matrix4f().translate(offset.x() + 0.5f, offset.y(), offset.z() + 0.5f).mul(r).translate(-0.5f, 0, -0.5f);
        float[] out = new float[16];
        m.get(out);
        return out;
    }

    @Override
    public void close() {
        scene.removeListener(this);
        pool.shutdownNow();
    }
}
