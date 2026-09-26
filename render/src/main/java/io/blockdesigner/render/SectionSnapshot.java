package io.blockdesigner.render;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;

/**
 * An immutable copy of one 16³ section plus a one-block border, taken on the thread that owns the structure so that
 * meshing can run on worker threads without touching live data.
 */
public final class SectionSnapshot {
    public static final int SIZE = Structure.SECTION_SIZE;
    static final int PADDED = SIZE + 2;

    final int sx, sy, sz;
    private final BlockState[] states;
    final boolean empty;
    /** Block entity data the look depends on (banner patterns, pot sherds), by section-local index; empty for most sections. */
    private final java.util.Map<Integer, io.blockdesigner.core.nbt.CompoundTag> data;
    /** How far open chests and shulker boxes are shown (0..1), by section-local index; empty unless one is open. */
    private final java.util.Map<Integer, Float> open;

    /** How far the container at a layer-local position is shown open (0 when shut). */
    @FunctionalInterface
    public interface Openness {
        float at(int x, int y, int z);

        Openness NONE = (x, y, z) -> 0;
    }

    private SectionSnapshot(int sx, int sy, int sz, BlockState[] states, boolean empty, java.util.Map<Integer, io.blockdesigner.core.nbt.CompoundTag> data,
                            java.util.Map<Integer, Float> open) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.states = states;
        this.empty = empty;
        this.data = data;
        this.open = open;
    }

    public static SectionSnapshot capture(Structure s, int sx, int sy, int sz) {
        return capture(s, sx, sy, sz, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static SectionSnapshot capture(Structure s, int sx, int sy, int sz, int minY, int maxY) {
        return capture(s, sx, sy, sz, minY, maxY, null);
    }

    /**
     * As {@link #capture(Structure, int, int, int)}, treating blocks outside local {@code minY..maxY} as air (slice
     * view), with containers opened as far as {@code opened} says (null: all shut).
     */
    public static SectionSnapshot capture(Structure s, int sx, int sy, int sz, int minY, int maxY, Openness opened) {
        BlockState[] st = new BlockState[PADDED * PADDED * PADDED];
        int bx = sx * SIZE - 1, by = sy * SIZE - 1, bz = sz * SIZE - 1;
        // One pass per overlapped section rather than a hash lookup per cell.
        s.copyRegion(bx, by, bz, PADDED, PADDED, PADDED, st);
        boolean clipped = minY > by || maxY < by + PADDED - 1;
        if (clipped) {
            for (int y = 0; y < PADDED; y++) {
                int ly = by + y;
                if (ly >= minY && ly <= maxY) continue;
                java.util.Arrays.fill(st, y * PADDED * PADDED, (y + 1) * PADDED * PADDED, BlockState.AIR);
            }
        }
        boolean empty = true;
        boolean anyData = !s.blockEntities().isEmpty() || opened != null;
        java.util.Map<Integer, io.blockdesigner.core.nbt.CompoundTag> data = java.util.Map.of();
        java.util.Map<Integer, Float> open = java.util.Map.of();
        for (int y = 1; y <= SIZE; y++) {
            for (int z = 1; z <= SIZE; z++) {
                int row = (y * PADDED + z) * PADDED;
                for (int x = 1; x <= SIZE; x++) {
                    BlockState b = st[row + x];
                    if (b.isAir()) continue;
                    empty = false;
                    if (anyData && io.blockdesigner.assets.BlockAssets.usesBlockEntity(b)) {
                        var nbt = s.blockEntity(new io.blockdesigner.core.model.BlockPos(bx + x, by + y, bz + z));
                        if (nbt != null) {
                            if (data.isEmpty()) data = new java.util.HashMap<>();
                            data.put(index(x - 1, y - 1, z - 1), nbt.copy());
                        }
                    }
                    if (opened != null && io.blockdesigner.assets.BlockAssets.opens(b)) {
                        float o = opened.at(bx + x, by + y, bz + z);
                        if (o > 0) {
                            if (open.isEmpty()) open = new java.util.HashMap<>();
                            open.put(index(x - 1, y - 1, z - 1), o);
                        }
                    }
                }
            }
            if (!empty && !anyData) break;
        }
        return new SectionSnapshot(sx, sy, sz, st, empty, data, open);
    }

    private static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    /** Block entity data kept for this block (banner patterns), or null. Section-local coordinates 0..15. */
    io.blockdesigner.core.nbt.CompoundTag data(int x, int y, int z) {
        return data.isEmpty() ? null : data.get(index(x, y, z));
    }

    /** How far the container at section-local coordinates is shown open (0 when shut or not a container). */
    float open(int x, int y, int z) {
        if (open.isEmpty()) return 0;
        Float o = open.get(index(x, y, z));
        return o == null ? 0 : o;
    }

    /** State at section-local coordinates; valid range is -1..16 on each axis. */
    BlockState get(int x, int y, int z) {
        return states[((y + 1) * PADDED + (z + 1)) * PADDED + (x + 1)];
    }

    /** Index into the padded arrays for section-local coordinates (-1..16). */
    static int cell(int x, int y, int z) {
        return ((y + 1) * PADDED + (z + 1)) * PADDED + (x + 1);
    }

    /** The padded state array (read-only by convention), indexed by {@link #cell}. */
    BlockState[] states() {
        return states;
    }

    public boolean isEmpty() {
        return empty;
    }
}
