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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps GPU meshes in step with a {@link Scene}. Scene events mark sections dirty; {@link #sync()} (called on the
 * scene's owning thread, e.g. each UI pulse) snapshots them and meshes them on worker threads, then uploads.
 *
 * <p>Meshes are instanced: layers whose blocks are identical (same {@link Structure#contentId()}, e.g. duplicated
 * layers) and that are clipped the same way share one {@link Group}, so they are meshed and uploaded once and drawn
 * several times with their own model matrices. Editing one copy splits it off into its own group.
 */
public final class SceneRenderer implements Scene.Listener, AutoCloseable {
    private static final int S = Structure.SECTION_SIZE;

    private final Scene scene;
    private final ViewportRenderer gpu;
    private final SectionMesher mesher;
    private final ExecutorService pool;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Runnable onMeshReady;
    // World-space Y slice (inclusive); ghost layers are never clipped.
    private int sliceMin = Integer.MIN_VALUE, sliceMax = Integer.MAX_VALUE;

    private final Map<String, Group> groupsByKey = new HashMap<>();
    private final Map<String, Group> groupOf = new HashMap<>();
    private int nextMesh;

    /** One GPU mesh and the layers drawing it. Owner-thread state except {@link #pending}, {@link #dead} and versions. */
    private static final class Group {
        final String meshId;
        String key;
        final Set<String> members = new LinkedHashSet<>();
        final Set<Long> dirty = new HashSet<>();
        /** Local Y range the mesh is clipped to (MIN/MAX = unclipped). */
        int clipLo = Integer.MIN_VALUE, clipHi = Integer.MAX_VALUE;
        /** Mesh drawn instead until this group's first full mesh is uploaded, so a split-off copy doesn't blink. */
        String fallback;
        final AtomicInteger pending = new AtomicInteger();
        final Map<Long, Integer> versions = new HashMap<>();
        volatile boolean dead;

        Group(String meshId, String key) {
            this.meshId = meshId;
            this.key = key;
        }
    }

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
        for (Layer l : scene.layers()) regroup(l);
    }

    /** Sections still being meshed (for a progress indicator). */
    public int pending() {
        int d = 0;
        for (Group g : groupsByKey.values()) d += g.dirty.size();
        return d + inFlight.get();
    }

    /** Sections being meshed right now; safe to call from any thread. */
    public int inFlight() {
        return inFlight.get();
    }

    /** Distinct meshes held for the scene's layers (fewer than layers when copies share one). */
    public int meshCount() {
        return groupsByKey.size();
    }

    // ---- grouping (owner thread) -------------------------------------------------------------------------------

    private boolean sliced() {
        return sliceMin != Integer.MIN_VALUE || sliceMax != Integer.MAX_VALUE;
    }

    /** Layer-local → world Y shift for clipping, or null when the layer is drawn unclipped. */
    private Integer clipBase(Layer l) {
        return l.ghost() || !sliced() ? null : l.offset().y();
    }

    private String key(Layer l) {
        Integer base = clipBase(l);
        return l.structure().contentId() + "|" + (base == null ? "*" : base.toString());
    }

    private static int clipLocal(int worldY, int base) {
        if (worldY == Integer.MIN_VALUE || worldY == Integer.MAX_VALUE) return worldY;
        return Math.clamp((long) worldY - base, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** Puts the layer in the group matching its current content and clip, creating or re-keying groups as needed. */
    private Group regroup(Layer l) {
        String k = key(l);
        Group g = groupOf.get(l.id());
        if (g != null && g.key.equals(k)) return g;
        Group target = groupsByKey.get(k);
        if (g != null) {
            if (target == null && g.members.size() == 1) {
                // Alone in its group: keep the mesh and only re-mesh what the new clip changes.
                groupsByKey.remove(g.key);
                g.key = k;
                groupsByKey.put(k, g);
                refreshClip(g, l);
                return g;
            }
            leave(l, g);
        }
        if (target == null) {
            target = new Group("mesh-" + nextMesh++, k);
            if (g != null && !g.dead) target.fallback = g.meshId;
            Integer base = clipBase(l);
            target.clipLo = base == null ? Integer.MIN_VALUE : clipLocal(sliceMin, base);
            target.clipHi = base == null ? Integer.MAX_VALUE : clipLocal(sliceMax, base);
            groupsByKey.put(k, target);
            for (long s : l.structure().sectionKeyArray()) target.dirty.add(s);
        }
        target.members.add(l.id());
        groupOf.put(l.id(), target);
        return target;
    }

    private void leave(Layer l, Group g) {
        g.members.remove(l.id());
        groupOf.remove(l.id());
        if (g.members.isEmpty()) {
            groupsByKey.remove(g.key);
            g.dead = true;
            g.dirty.clear();
            gpu.removeLayer(g.meshId);
        }
    }

    /** Re-meshes the sections of a group whose visible part changes under the layer's current clip. */
    private void refreshClip(Group g, Layer owner) {
        Integer base = clipBase(owner);
        int lo = base == null ? Integer.MIN_VALUE : clipLocal(sliceMin, base);
        int hi = base == null ? Integer.MAX_VALUE : clipLocal(sliceMax, base);
        if (lo == g.clipLo && hi == g.clipHi) return;
        for (long key : owner.structure().sectionKeyArray()) {
            // Local span of the section plus the one-block border its culling reads.
            int y0 = BlockPos.unpack(key).y() * S - 1, y1 = y0 + S + 1;
            if (changed(y0, y1, g.clipLo, g.clipHi, lo, hi)) g.dirty.add(key);
        }
        g.clipLo = lo;
        g.clipHi = hi;
    }

    /** Whether any Y in {@code y0..y1} is inside one range but not the other. */
    private static boolean changed(int y0, int y1, int aMin, int aMax, int bMin, int bMax) {
        int lo = Math.max(y0, Math.min(aMin, bMin)), hi = Math.min(y1, Math.max(aMax, bMax));
        for (long y = lo; y <= hi; y++) {
            if ((y >= aMin && y <= aMax) != (y >= bMin && y <= bMax)) return true;
        }
        return false;
    }

    private Layer owner(Group g) {
        for (String id : g.members) {
            Layer l = scene.find(id).orElse(null);
            if (l != null) return l;
        }
        return null;
    }

    // ---- Scene.Listener (owner thread) ------------------------------------------------------------------------

    @Override
    public void layerAdded(Layer layer, int index) {
        regroup(layer);
    }

    @Override
    public void layerPropertiesChanged(Layer layer) {
        // A clipped layer that moves vertically (or stops being a ghost) shows a different part of itself.
        regroup(layer);
    }

    @Override
    public void layerRemoved(Layer layer) {
        Group g = groupOf.get(layer.id());
        if (g != null) leave(layer, g);
    }

    @Override
    public void blocksChanged(Layer layer, Box b) {
        Group g = regroup(layer);
        // One block of padding: neighbours' culling and AO depend on the changed blocks.
        for (int sy = Math.floorDiv(b.minY() - 1, S); sy <= Math.floorDiv(b.maxY() + 1, S); sy++)
            for (int sz = Math.floorDiv(b.minZ() - 1, S); sz <= Math.floorDiv(b.maxZ() + 1, S); sz++)
                for (int sx = Math.floorDiv(b.minX() - 1, S); sx <= Math.floorDiv(b.maxX() + 1, S); sx++)
                    g.dirty.add(Structure.sectionKey(sx, sy, sz));
    }

    public void markAll(Layer layer) {
        Group g = regroup(layer);
        for (long s : layer.structure().sectionKeyArray()) g.dirty.add(s);
    }

    /**
     * Only draws blocks whose world Y lies in {@code minY..maxY} (inclusive); pass {@link Integer#MIN_VALUE} /
     * {@link Integer#MAX_VALUE} to show everything. Re-meshes only the sections whose visible content changes.
     */
    public void setSlice(int minY, int maxY) {
        if (minY == sliceMin && maxY == sliceMax) return;
        sliceMin = minY;
        sliceMax = maxY;
        for (Layer l : scene.layers()) regroup(l);
        for (Group g : List.copyOf(groupsByKey.values())) {
            Layer owner = owner(g);
            if (owner != null) refreshClip(g, owner);
        }
    }

    /** Re-meshes everything (e.g. after assets change). */
    public void rebuildAll() {
        for (Group g : groupsByKey.values()) {
            gpu.removeLayer(g.meshId);
            Layer owner = owner(g);
            if (owner != null) for (long s : owner.structure().sectionKeyArray()) g.dirty.add(s);
        }
    }

    /** Snapshots dirty sections and hands them to the meshing pool. Call on the scene's owning thread. */
    public void sync() {
        for (Group g : groupsByKey.values()) {
            if (g.dirty.isEmpty()) continue;
            Layer layer = owner(g);
            if (layer == null) {
                g.dirty.clear();
                continue;
            }
            boolean clipped = g.clipLo != Integer.MIN_VALUE || g.clipHi != Integer.MAX_VALUE;
            for (long key : g.dirty) {
                BlockPos sp = BlockPos.unpack(key);
                SectionSnapshot snap = clipped
                        ? SectionSnapshot.capture(layer.structure(), sp.x(), sp.y(), sp.z(), g.clipLo, g.clipHi)
                        : SectionSnapshot.capture(layer.structure(), sp.x(), sp.y(), sp.z());
                int v;
                synchronized (g.versions) {
                    v = g.versions.merge(key, 1, Integer::sum);
                }
                inFlight.incrementAndGet();
                g.pending.incrementAndGet();
                pool.submit(() -> {
                    try {
                        MeshData md = mesher.mesh(snap);
                        if (isCurrent(g, key, v)) {
                            gpu.uploadSection(g.meshId, key, md, sp.x() * S + 8f, sp.y() * S + 8f, sp.z() * S + 8f);
                        }
                    } finally {
                        g.pending.decrementAndGet();
                        inFlight.decrementAndGet();
                        onMeshReady.run();
                    }
                });
            }
            g.dirty.clear();
        }
    }

    private static boolean isCurrent(Group g, long key, int v) {
        if (g.dead) return false;
        synchronized (g.versions) {
            Integer cur = g.versions.get(key);
            return cur != null && cur == v;
        }
    }

    // ---- frame building -------------------------------------------------------------------------------------

    /** The mesh a layer draws with (shared by its copies); null before the layer is known. */
    private String meshFor(Layer l) {
        Group g = groupOf.get(l.id());
        if (g == null) return null;
        if (g.fallback != null) {
            if (g.pending.get() > 0 || !g.dirty.isEmpty()) return g.fallback;
            g.fallback = null;
        }
        return g.meshId;
    }

    /** Draw list for visible layers, bottom first. Ghost layers render translucent. */
    public List<FrameRequest.LayerDraw> layerDraws(String highlightLayerId) {
        List<FrameRequest.LayerDraw> out = new ArrayList<>();
        for (Layer l : scene.layers()) {
            if (!l.visible()) continue;
            String mesh = meshFor(l);
            if (mesh == null) continue;
            out.add(new FrameRequest.LayerDraw(mesh, modelMatrix(l.offset(), l.transform()), l.transform().mirror() != Transform.Mirror.NONE,
                    l.ghost() ? 0.45f : 1f, l.ghost() ? 0x9FC2FF : 0xFFFFFF, l.ghost() ? 0.25f : 0f));
        }
        return out;
    }

    /** Extra draw for a floating placement preview of an existing layer's meshes at another position. */
    public FrameRequest.LayerDraw ghostDraw(Layer layer, BlockPos offset, Transform t, int tint) {
        String mesh = meshFor(layer);
        return new FrameRequest.LayerDraw(mesh == null ? layer.id() : mesh, modelMatrix(offset, t), t.mirror() != Transform.Mirror.NONE, 0.55f, tint, 0.3f);
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
