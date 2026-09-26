package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.worldedit.WorldEdit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Blocks in world coordinates across every layer, the way the view shows them: reads see the visible layers merged
 * (the top-most block wins), so fences join fences in other layers, stairs form corners with them and the brushes
 * sculpt terrain spread over several layers. A write changes the cell in the layer that holds a block there (and clears
 * it from any layer below); an empty cell is filled in the target layer. Locked layers are read but never written.
 * Rotated and mirrored layers work as expected. Every layer written gets one {@link SceneEditor.BlockSession}; call
 * {@link #close()} to commit them.
 */
final class LayeredEdit implements WorldEdit.World, AutoCloseable {
    private final Workspace ws;
    private final String label, mergeKey;
    /** Layers read from, bottom first. */
    private final List<Layer> layers;
    private Layer target;
    private final Supplier<Layer> newTarget;
    private final Map<Layer, SceneEditor.BlockSession> sessions = new LinkedHashMap<>();
    /** World bounds of each layer, grown as we write: reads outside them are air without any lookup. */
    private final Map<Layer, int[]> reach = new HashMap<>();
    private final List<BlockPos> written = new ArrayList<>();
    private final BlockTransformer bt = BlockTransformer.defaults();

    /**
     * @param layers    layers to read, bottom first (the visible ones)
     * @param target    where blocks go in empty cells; null to make a layer with {@code newTarget} on the first write
     * @param mergeKey  sessions with the same key merge into one undo step (a held place / break burst), or null
     */
    LayeredEdit(Workspace ws, List<Layer> layers, Layer target, Supplier<Layer> newTarget, String label, String mergeKey) {
        this.ws = ws;
        this.label = label;
        this.mergeKey = mergeKey;
        this.layers = new ArrayList<>(layers);
        this.target = target;
        this.newTarget = newTarget;
        if (target != null && !this.layers.contains(target)) this.layers.add(target);
        for (Layer l : this.layers) l.worldBounds().ifPresent(b -> reach.put(l, new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
    }

    /** Cells written so far, in order (world positions). */
    List<BlockPos> written() {
        return written;
    }

    Layer target() {
        return target;
    }

    /** A layer's block at a world position, turned to face the way the world sees it. */
    BlockState read(Layer l, BlockPos p) {
        int[] b = reach.get(l);
        if (b == null || p.x() < b[0] || p.y() < b[1] || p.z() < b[2] || p.x() > b[3] || p.y() > b[4] || p.z() > b[5]) return BlockState.AIR;
        BlockPos lp = l.toLocal(p);
        SceneEditor.BlockSession s = sessions.get(l);
        BlockState st = s != null ? s.get(lp.x(), lp.y(), lp.z()) : l.structure().get(lp);
        return st.isAir() ? st : bt.apply(st, l.transform());
    }

    /** The layer showing a block at {@code p} (the top-most), or null when the cell is empty. */
    Layer holder(BlockPos p) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            if (!read(layers.get(i), p).isAir()) return layers.get(i);
        }
        return null;
    }

    @Override
    public BlockState get(BlockPos p) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            BlockState st = read(layers.get(i), p);
            if (!st.isAir()) return st;
        }
        return BlockState.AIR;
    }

    @Override
    public void set(BlockPos p, BlockState st) {
        set(p, st, null, false);
    }

    @Override
    public void set(BlockPos p, BlockState st, CompoundTag blockEntity) {
        set(p, st, blockEntity, true);
    }

    private void set(BlockPos p, BlockState st, CompoundTag nbt, boolean exactNbt) {
        boolean placed = false;
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (read(l, p).isAir()) continue;
            if (l.locked()) {
                // A locked layer's block stays, and hides this cell from the layers under it.
                placed = true;
                break;
            }
            // Replace in the top-most layer holding a block here; clear the cell in any layer under it.
            write(l, p, placed ? BlockState.AIR : st, placed ? null : nbt, exactNbt || placed);
            placed = true;
        }
        if (!placed && !st.isAir()) {
            Layer t = target();
            if (t == null && newTarget != null) {
                t = newTarget.get();
                target = t;
                if (!layers.contains(t)) layers.add(t);
            }
            if (t == null || t.locked()) return;
            write(t, p, st, nbt, exactNbt);
        }
        written.add(p);
    }

    /** Writes a block into one specific layer (placing into the active layer even where another layer shows through). */
    void setIn(Layer l, BlockPos p, BlockState st) {
        if (l.locked()) return;
        if (!layers.contains(l)) layers.add(l);
        write(l, p, st, null, false);
        written.add(p);
    }

    private void write(Layer l, BlockPos p, BlockState st, CompoundTag nbt, boolean exactNbt) {
        SceneEditor.BlockSession s = sessions.computeIfAbsent(l, x -> ws.editor().edit(x, label, mergeKey));
        BlockPos lp = l.toLocal(p);
        BlockState local = st.isAir() ? st : bt.apply(st, l.transform().inverse());
        if (exactNbt) s.setExact(lp.x(), lp.y(), lp.z(), local, nbt);
        else s.set(lp.x(), lp.y(), lp.z(), local);
        int[] b = reach.computeIfAbsent(l, k -> new int[]{p.x(), p.y(), p.z(), p.x(), p.y(), p.z()});
        b[0] = Math.min(b[0], p.x());
        b[1] = Math.min(b[1], p.y());
        b[2] = Math.min(b[2], p.z());
        b[3] = Math.max(b[3], p.x());
        b[4] = Math.max(b[4], p.y());
        b[5] = Math.max(b[5], p.z());
    }

    @Override
    public CompoundTag blockEntity(BlockPos p) {
        Layer l = holder(p);
        if (l == null) return null;
        BlockPos lp = l.toLocal(p);
        SceneEditor.BlockSession s = sessions.get(l);
        return s != null ? s.blockEntity(lp.x(), lp.y(), lp.z()) : l.structure().blockEntity(lp);
    }

    // ---- entities ------------------------------------------------------------------------------------------------

    @Override
    public List<StructureEntity> entities(Box box) {
        List<StructureEntity> out = new ArrayList<>();
        for (Layer l : layers) {
            for (StructureEntity e : l.structure().entities()) {
                StructureEntity w = io.blockdesigner.core.model.EntityTypes.transform(e, l.transform(), l.offset().x(), l.offset().y(), l.offset().z());
                if (inside(box, w)) out.add(w);
            }
        }
        return out;
    }

    @Override
    public void addEntity(StructureEntity world) {
        Layer t = target();
        if (t == null && newTarget != null) {
            t = newTarget.get();
            target = t;
            if (!layers.contains(t)) layers.add(t);
        }
        if (t == null || t.locked()) return;
        StructureEntity local = SceneEditor.toLocal(t, t.transform().inverse(), world);
        ws.editor().editEntities(t, label, mergeKey, list -> list.add(local));
    }

    @Override
    public int removeEntities(Box box) {
        int n = 0;
        for (Layer l : layers) {
            if (l.locked()) continue;
            List<StructureEntity> gone = new ArrayList<>();
            for (StructureEntity e : l.structure().entities()) {
                StructureEntity w = io.blockdesigner.core.model.EntityTypes.transform(e, l.transform(), l.offset().x(), l.offset().y(), l.offset().z());
                if (inside(box, w)) gone.add(e);
            }
            if (gone.isEmpty()) continue;
            n += gone.size();
            ws.editor().editEntities(l, label, mergeKey, list -> list.removeAll(gone));
        }
        return n;
    }

    private static boolean inside(Box b, StructureEntity e) {
        return e.x() >= b.minX() && e.x() < b.maxX() + 1 && e.y() >= b.minY() && e.y() < b.maxY() + 1 && e.z() >= b.minZ() && e.z() < b.maxZ() + 1;
    }

    // ---- neighbours ---------------------------------------------------------------------------------------------

    /**
     * Fences, walls, panes, redstone, rails and stairs around what was written join up with it, whichever layer they
     * are in, as when placing by hand. Skipped for huge edits (the cost grows with the number of cells).
     */
    void reconnect() {
        reconnect(List.copyOf(written));
    }

    void reconnect(List<BlockPos> changed) {
        if (changed.isEmpty() || changed.size() > 200_000) return;
        var updates = BlockPlacement.reconnect(changed, this::get);
        int n = written.size();
        updates.forEach(this::set);
        // Shape updates aren't edits of their own: keep them out of what the caller counts.
        written.subList(n, written.size()).clear();
    }

    boolean touched() {
        return !sessions.isEmpty();
    }

    /** Shows what was written so far (fires change events) without ending the edit, for edits spread over a drag. */
    void flush() {
        sessions.values().forEach(SceneEditor.BlockSession::flush);
    }

    /** Puts back every block written and records nothing (entities added or removed stay as they are). */
    void rollback() {
        sessions.values().forEach(SceneEditor.BlockSession::rollback);
        sessions.clear();
        written.clear();
    }

    @Override
    public void close() {
        sessions.values().forEach(SceneEditor.BlockSession::close);
        sessions.clear();
    }
}
