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

    private SectionSnapshot(int sx, int sy, int sz, BlockState[] states, boolean empty) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.states = states;
        this.empty = empty;
    }

    public static SectionSnapshot capture(Structure s, int sx, int sy, int sz) {
        BlockState[] st = new BlockState[PADDED * PADDED * PADDED];
        int bx = sx * SIZE - 1, by = sy * SIZE - 1, bz = sz * SIZE - 1;
        boolean empty = true;
        int i = 0;
        for (int y = 0; y < PADDED; y++) {
            for (int z = 0; z < PADDED; z++) {
                for (int x = 0; x < PADDED; x++, i++) {
                    BlockState b = s.get(bx + x, by + y, bz + z);
                    st[i] = b;
                    if (empty && !b.isAir() && x > 0 && y > 0 && z > 0 && x <= SIZE && y <= SIZE && z <= SIZE) empty = false;
                }
            }
        }
        return new SectionSnapshot(sx, sy, sz, st, empty);
    }

    /** State at section-local coordinates; valid range is -1..16 on each axis. */
    BlockState get(int x, int y, int z) {
        return states[((y + 1) * PADDED + (z + 1)) * PADDED + (x + 1)];
    }

    public boolean isEmpty() {
        return empty;
    }
}
