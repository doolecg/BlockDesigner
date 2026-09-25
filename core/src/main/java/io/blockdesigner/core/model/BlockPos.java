package io.blockdesigner.core.model;

/** Immutable integer block position. */
public record BlockPos(int x, int y, int z) implements Comparable<BlockPos> {
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos add(BlockPos o) {
        return add(o.x, o.y, o.z);
    }

    public BlockPos subtract(BlockPos o) {
        return add(-o.x, -o.y, -o.z);
    }

    /** Packs a position into a long (26 bits x, 12 bits y, 26 bits z), matching vanilla's layout. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public long pack() {
        return pack(x, y, z);
    }

    public static BlockPos unpack(long v) {
        int x = (int) (v >> 38);
        int y = (int) (v << 52 >> 52);
        int z = (int) (v << 26 >> 38);
        return new BlockPos(x, y, z);
    }

    @Override
    public int compareTo(BlockPos o) {
        if (y != o.y) return Integer.compare(y, o.y);
        if (z != o.z) return Integer.compare(z, o.z);
        return Integer.compare(x, o.x);
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
