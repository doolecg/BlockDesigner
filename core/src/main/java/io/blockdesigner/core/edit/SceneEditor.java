package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;

import java.util.List;
import java.util.function.Consumer;

/**
 * The single entry point for undoable edits to a {@link Scene}. UI tools, commands and plugins all go through here, so every
 * change lands on the undo stack and fires scene events.
 */
public final class SceneEditor {
    private final Scene scene;
    private final UndoStack undo;

    public SceneEditor(Scene scene) {
        this.scene = scene;
        this.undo = new UndoStack(scene);
    }

    public Scene scene() {
        return scene;
    }

    public UndoStack undoStack() {
        return undo;
    }

    // ---- Block editing -------------------------------------------------------------------------------------------

    /**
     * An open block-editing session on one layer. Edits apply immediately (so the viewport updates live);
     * {@link #flush()} fires change events for what was touched so far, and {@link #commit()} pushes one undo step.
     */
    public final class BlockSession implements AutoCloseable {
        private final Layer layer;
        private final Transaction tx;
        private final BlockChange change;
        /** Bounds of edits not yet announced (min > max when there are none). */
        private int dMinX = Integer.MAX_VALUE, dMinY = Integer.MAX_VALUE, dMinZ = Integer.MAX_VALUE;
        private int dMaxX = Integer.MIN_VALUE, dMaxY = Integer.MIN_VALUE, dMaxZ = Integer.MIN_VALUE;
        private boolean closed;

        private BlockSession(Layer layer, String label, String mergeKey) {
            this.layer = layer;
            this.tx = new Transaction(label, mergeKey);
            this.change = new BlockChange(layer.id());
        }

        public Layer layer() {
            return layer;
        }

        public Structure structure() {
            return layer.structure();
        }

        public BlockState get(int x, int y, int z) {
            return layer.structure().get(x, y, z);
        }

        /** Sets a block in layer-local coordinates. */
        public void set(int x, int y, int z, BlockState state) {
            set(x, y, z, state, null);
        }

        public void set(int x, int y, int z, BlockState state, CompoundTag blockEntity) {
            checkOpen();
            Structure s = layer.structure();
            BlockPos p = new BlockPos(x, y, z);
            BlockState before = s.get(x, y, z);
            boolean anyNbt = blockEntity != null || !s.blockEntities().isEmpty();
            CompoundTag beforeNbt = anyNbt ? s.blockEntity(p) : null;
            if (beforeNbt != null) beforeNbt = beforeNbt.copy();
            s.set(x, y, z, state);
            if (blockEntity != null) s.setBlockEntity(p, blockEntity.copy());
            CompoundTag afterNbt = anyNbt ? s.blockEntity(p) : null;
            change.record(p, before, beforeNbt, state, afterNbt == null ? null : afterNbt.copy());
            if (x < dMinX) dMinX = x;
            if (y < dMinY) dMinY = y;
            if (z < dMinZ) dMinZ = z;
            if (x > dMaxX) dMaxX = x;
            if (y > dMaxY) dMaxY = y;
            if (z > dMaxZ) dMaxZ = z;
        }

        public void fill(Box box, BlockState state) {
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                    for (int x = box.minX(); x <= box.maxX(); x++) set(x, y, z, state);
        }

        public int changedCount() {
            return change.size();
        }

        /** Fires a blocks-changed event covering everything edited since the last flush. */
        public void flush() {
            if (dMinX <= dMaxX) {
                Box dirty = new Box(dMinX, dMinY, dMinZ, dMaxX, dMaxY, dMaxZ);
                dMinX = dMinY = dMinZ = Integer.MAX_VALUE;
                dMaxX = dMaxY = dMaxZ = Integer.MIN_VALUE;
                scene.fireBlocksChanged(layer, dirty);
            }
        }

        public void commit() {
            checkOpen();
            flush();
            closed = true;
            if (!change.isEmpty()) {
                tx.add(change);
                undo.push(tx);
            }
        }

        /** Reverts every edit made in this session without recording history. */
        public void rollback() {
            checkOpen();
            closed = true;
            change.undo(scene);
        }

        @Override
        public void close() {
            if (!closed) commit();
        }

        private void checkOpen() {
            if (closed) throw new IllegalStateException("Session already closed");
        }
    }

    public BlockSession edit(Layer layer, String label) {
        return new BlockSession(layer, label, null);
    }

    public BlockSession edit(Layer layer, String label, String mergeKey) {
        return new BlockSession(layer, label, mergeKey);
    }

    // ---- Layer editing -------------------------------------------------------------------------------------------

    public void addLayer(Layer layer) {
        addLayer(layer, scene.layers().size());
    }

    public void addLayer(Layer layer, int index) {
        scene.add(layer, index);
        undo.push(new Transaction("Add layer " + layer.name()).add(new LayerChange.Add(layer, index)));
        scene.setActive(layer);
    }

    /** Records an undo step for a layer that is already in the scene (e.g. after an interactive placement). */
    public void recordAdded(Layer layer, String label) {
        int idx = scene.indexOf(layer);
        if (idx < 0) return;
        undo.push(new Transaction(label).add(new LayerChange.Add(layer, idx)));
    }

    public void removeLayer(Layer layer) {
        int idx = scene.indexOf(layer);
        if (idx < 0) return;
        scene.remove(layer);
        undo.push(new Transaction("Delete layer " + layer.name()).add(new LayerChange.Remove(layer, idx)));
    }

    public void reorderLayer(Layer layer, int newIndex) {
        int from = scene.indexOf(layer);
        if (from < 0 || from == newIndex) return;
        scene.move(layer, newIndex);
        undo.push(new Transaction("Reorder " + layer.name()).add(new LayerChange.Reorder(layer.id(), from, scene.indexOf(layer))));
    }

    /** Applies {@code mutator} to a layer's properties as one undoable step. */
    public void modifyLayer(Layer layer, String label, String mergeKey, Consumer<Layer> mutator) {
        LayerChange.Props before = LayerChange.Props.of(layer);
        mutator.accept(layer);
        LayerChange.Props after = LayerChange.Props.of(layer);
        if (before.equals(after)) return;
        scene.firePropertiesChanged(layer);
        undo.push(new Transaction(label, mergeKey).add(new LayerChange.Properties(layer.id(), before, after)));
    }

    /**
     * Nudges layers by a world-space delta. Calls sharing {@code mergeKey} within the merge window become one undo step,
     * which is how a burst of modifier+scroll moves undoes in one go.
     */
    public void nudge(List<Layer> layers, int dx, int dy, int dz, String mergeKey) {
        Transaction tx = new Transaction(layers.size() == 1 ? "Move " + layers.getFirst().name() : "Move " + layers.size() + " layers", mergeKey);
        for (Layer l : layers) {
            if (l.locked()) continue;
            LayerChange.Props before = LayerChange.Props.of(l);
            l.setOffset(l.offset().add(dx, dy, dz));
            scene.firePropertiesChanged(l);
            tx.add(new LayerChange.Properties(l.id(), before, LayerChange.Props.of(l)));
        }
        undo.push(tx);
    }

    /** Replaces every layer with {@code replacement} as one undoable step (used to restore snapshots). */
    public void replaceAll(List<Layer> replacement, String label) {
        Transaction tx = new Transaction(label);
        for (Layer l : List.copyOf(scene.layers())) {
            int idx = scene.indexOf(l);
            scene.remove(l);
            tx.add(new LayerChange.Remove(l, idx));
        }
        for (Layer l : replacement) {
            scene.add(l);
            tx.add(new LayerChange.Add(l, scene.indexOf(l)));
        }
        if (!replacement.isEmpty()) scene.setActive(replacement.getLast());
        undo.push(tx);
    }

    /** Merges {@code upper} into {@code lower} (world positions preserved) and removes {@code upper}. */
    public void mergeDown(Layer upper, Layer lower) {
        Transaction tx = new Transaction("Merge " + upper.name() + " into " + lower.name());
        Structure flat = Scene.flatten(List.of(upper));
        BlockSession s = new BlockSession(lower, tx.label(), null);
        flat.forEachBlock((x, y, z, st) -> {
            BlockPos local = lower.toLocal(new BlockPos(x, y, z));
            BlockState ls = io.blockdesigner.core.transform.BlockTransformer.defaults().apply(st, lower.transform().inverse());
            CompoundTag be = flat.blockEntity(new BlockPos(x, y, z));
            s.set(local.x(), local.y(), local.z(), ls, be);
        });
        s.flush();
        s.closed = true;
        if (!s.change.isEmpty()) tx.add(s.change);
        int idx = scene.indexOf(upper);
        scene.remove(upper);
        tx.add(new LayerChange.Remove(upper, idx));
        scene.setActive(lower);
        undo.push(tx);
    }
}
