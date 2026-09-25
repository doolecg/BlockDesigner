package io.blockdesigner.core.transform;

import io.blockdesigner.core.model.BlockPos;

/**
 * A horizontal transform: a mirror applied first, then {@code rotation} clockwise quarter turns (viewed from above)
 * around the local origin. Minecraft axes: +X east, +Z south.
 */
public record Transform(int rotation, Mirror mirror) {
    public static final Transform IDENTITY = new Transform(0, Mirror.NONE);

    public enum Mirror {
        /** No mirroring. */
        NONE,
        /** Flip along X (x → -x): east and west swap. */
        X,
        /** Flip along Z (z → -z): north and south swap. */
        Z
    }

    public Transform {
        rotation = Math.floorMod(rotation, 4);
        if (mirror == null) mirror = Mirror.NONE;
    }

    public static Transform rotation(int quarterTurns) {
        return new Transform(quarterTurns, Mirror.NONE);
    }

    public boolean isIdentity() {
        return rotation == 0 && mirror == Mirror.NONE;
    }

    public BlockPos apply(BlockPos p) {
        return apply(p.x(), p.y(), p.z());
    }

    public BlockPos apply(int x, int y, int z) {
        if (mirror == Mirror.X) x = -x;
        else if (mirror == Mirror.Z) z = -z;
        for (int i = 0; i < rotation; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new BlockPos(x, y, z);
    }

    /** Applies the transform to a continuous position (entities), treating block centres consistently with {@link #apply}. */
    public double[] apply(double x, double y, double z) {
        // Transform about block centres so an entity inside block (bx, bz) ends up inside apply(bx, bz).
        x -= 0.5;
        z -= 0.5;
        double[] r = applyRaw(x, z);
        return new double[]{r[0] + 0.5, y, r[1] + 0.5};
    }

    private double[] applyRaw(double x, double z) {
        if (mirror == Mirror.X) x = -x;
        else if (mirror == Mirror.Z) z = -z;
        for (int i = 0; i < rotation; i++) {
            double nx = -z;
            z = x;
            x = nx;
        }
        return new double[]{x, z};
    }

    /** Result of applying this transform and then {@code next}. */
    public Transform then(Transform next) {
        int[] a = matrix(), b = next.matrix();
        // b · a  (a is applied first)
        int[] m = {b[0] * a[0] + b[1] * a[2], b[0] * a[1] + b[1] * a[3], b[2] * a[0] + b[3] * a[2], b[2] * a[1] + b[3] * a[3]};
        return fromMatrix(m);
    }

    /** Transform that undoes this one. */
    public Transform inverse() {
        if (mirror == Mirror.NONE) return new Transform(-rotation, Mirror.NONE);
        // Any mirror-then-rotate is a reflection, and reflections are their own inverse.
        return this;
    }

    /** Row-major 2×2 matrix acting on (x, z). */
    private int[] matrix() {
        BlockPos ex = apply(1, 0, 0), ez = apply(0, 0, 1);
        return new int[]{ex.x(), ez.x(), ex.z(), ez.z()};
    }

    private static Transform fromMatrix(int[] m) {
        for (Mirror mirror : Mirror.values()) {
            for (int r = 0; r < 4; r++) {
                Transform t = new Transform(r, mirror);
                if (java.util.Arrays.equals(t.matrix(), m)) return t;
            }
        }
        throw new IllegalStateException("Not a lattice transform");
    }

    /** Yaw (degrees, Minecraft convention: 0 = south, 90 = west) after this transform. */
    public float applyYaw(float yaw) {
        float y = yaw;
        if (mirror == Mirror.X) y = -y;
        else if (mirror == Mirror.Z) y = 180 - y;
        y += 90f * rotation;
        y %= 360f;
        if (y < 0) y += 360f;
        return y;
    }
}
