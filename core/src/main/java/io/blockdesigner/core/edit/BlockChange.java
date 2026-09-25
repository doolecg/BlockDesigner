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
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

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
        int x = pos.x(), y = pos.y(), z = pos.z();
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Box bounds() {
        return minX > maxX ? null : new Box(minX, minY, minZ, maxX, maxY, maxZ);
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
        Box bounds = bounds();
        if (bounds != null) scene.fireBlocksChanged(layer, bounds);
    }

    @Override
    public boolean absorb(Change next) {
        if (!(next instanceof BlockChange o) || !o.layerId.equals(layerId)) return false;
        o.entries.forEach((p, e) -> record(p, e.before, e.beforeNbt, e.after, e.afterNbt));
        return true;
    }
}
