package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/** Block (and block-entity) edits within one layer, stored as before/after pairs per local position. */
public final class BlockChange implements Change {
    record Entry(BlockState before, CompoundTag beforeNbt, BlockState after, CompoundTag afterNbt) {
    }

    private final String layerId;
    private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
    private Box bounds;

    BlockChange(String layerId) {
        this.layerId = layerId;
    }

    void record(BlockPos pos, BlockState before, CompoundTag beforeNbt, BlockState after, CompoundTag afterNbt) {
        Entry prev = entries.get(pos);
        if (prev != null) {
            before = prev.before;
            beforeNbt = prev.beforeNbt;
        }
        if (before == after && java.util.Objects.equals(beforeNbt, afterNbt)) {
            entries.remove(pos);
        } else {
            entries.put(pos, new Entry(before, beforeNbt, after, afterNbt));
        }
        Box b = new Box(pos.x(), pos.y(), pos.z(), pos.x(), pos.y(), pos.z());
        bounds = bounds == null ? b : bounds.union(b);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Box bounds() {
        return bounds;
    }

    public String layerId() {
        return layerId;
    }

    @Override
    public void undo(Scene scene) {
        scene.find(layerId).ifPresent(layer -> apply(scene, layer, true));
    }

    @Override
    public void redo(Scene scene) {
        scene.find(layerId).ifPresent(layer -> apply(scene, layer, false));
    }

    private void apply(Scene scene, Layer layer, boolean undo) {
        var s = layer.structure();
        for (var e : entries.entrySet()) {
            Entry en = e.getValue();
            s.set(e.getKey(), undo ? en.before : en.after);
            CompoundTag nbt = undo ? en.beforeNbt : en.afterNbt;
            s.setBlockEntity(e.getKey(), nbt == null ? null : nbt.copy());
        }
        if (bounds != null) scene.fireBlocksChanged(layer, bounds);
    }

    @Override
    public boolean absorb(Change next) {
        if (!(next instanceof BlockChange o) || !o.layerId.equals(layerId)) return false;
        o.entries.forEach((p, e) -> record(p, e.before, e.beforeNbt, e.after, e.afterNbt));
        return true;
    }
}
