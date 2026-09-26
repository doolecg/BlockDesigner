package io.blockdesigner.plugin;

import io.blockdesigner.plugin.ToolEvent.Vec3;

import java.util.Objects;

/**
 * Where a {@link SceneObject} sits: position, rotation and scale, like an object's transform in Blender. A point of
 * the object's own space goes to the world as {@code position + R · (scale ∘ local)}, where {@code R} turns about X,
 * then Y, then Z (Blender's default XYZ Euler order). Immutable; the Move, Rotate and Scale tools and the plugin's own
 * fields all work through {@link ObjectHandle#setPose}. Since API 3.
 *
 * @param position world position of the object's origin (blocks)
 * @param rotation Euler angles in degrees about X, Y and Z
 * @param scale    size along the object's own X, Y and Z axes (1 = as drawn)
 */
public record Pose(Vec3 position, Vec3 rotation, Vec3 scale) {

    public static final Pose IDENTITY = new Pose(new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));

    public Pose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
    }

    /** At {@code x, y, z}, unturned, scale 1. */
    public static Pose at(double x, double y, double z) {
        return IDENTITY.withPosition(new Vec3(x, y, z));
    }

    public Pose withPosition(Vec3 p) {
        return new Pose(p, rotation, scale);
    }

    public Pose withRotation(Vec3 r) {
        return new Pose(position, r, scale);
    }

    public Pose withScale(Vec3 s) {
        return new Pose(position, rotation, s);
    }

    /** Moved by {@code dx, dy, dz} world blocks. */
    public Pose translated(double dx, double dy, double dz) {
        return withPosition(new Vec3(position.x() + dx, position.y() + dy, position.z() + dz));
    }

    /**
     * Turned {@code degrees} about the world axis {@code axis} (0 X, 1 Y, 2 Z; right-handed) through {@code pivot}:
     * the object turns and its position swings round the pivot, as when rotating with the gizmo.
     */
    public Pose rotatedAbout(int axis, double degrees, Vec3 pivot) {
        double[] turn = axisRotation(axis, degrees);
        double[] r = multiply(turn, rotationMatrix());
        double[] rel = {position.x() - pivot.x(), position.y() - pivot.y(), position.z() - pivot.z()};
        double[] p = apply(turn, rel);
        return new Pose(new Vec3(pivot.x() + p[0], pivot.y() + p[1], pivot.z() + p[2]), euler(r), scale);
    }

    /** Scaled by {@code fx, fy, fz} along the object's own axes, about its origin (the Scale tool). */
    public Pose scaledBy(double fx, double fy, double fz) {
        return withScale(new Vec3(scale.x() * fx, scale.y() * fy, scale.z() * fz));
    }

    /** The rotation as a 3×3 matrix, row-major: {@code m[row * 3 + column]}. */
    public double[] rotationMatrix() {
        return multiply(axisRotation(2, rotation.z()), multiply(axisRotation(1, rotation.y()), axisRotation(0, rotation.x())));
    }

    /** A point of the object's own space in world coordinates. */
    public Vec3 toWorld(double x, double y, double z) {
        double[] p = apply(rotationMatrix(), new double[]{x * scale.x(), y * scale.y(), z * scale.z()});
        return new Vec3(position.x() + p[0], position.y() + p[1], position.z() + p[2]);
    }

    public Vec3 toWorld(Vec3 local) {
        return toWorld(local.x(), local.y(), local.z());
    }

    /** A world point in the object's own space (an axis scaled to 0 maps to 0). */
    public Vec3 toLocal(Vec3 world) {
        double[] m = rotationMatrix();
        double[] d = {world.x() - position.x(), world.y() - position.y(), world.z() - position.z()};
        // The rotation is orthonormal: its inverse is its transpose.
        double x = m[0] * d[0] + m[3] * d[1] + m[6] * d[2];
        double y = m[1] * d[0] + m[4] * d[1] + m[7] * d[2];
        double z = m[2] * d[0] + m[5] * d[1] + m[8] * d[2];
        return new Vec3(div(x, scale.x()), div(y, scale.y()), div(z, scale.z()));
    }

    /** The world direction of the object's own axis {@code axis} (0 X, 1 Y, 2 Z), unit length. */
    public Vec3 axis(int axis) {
        double[] m = rotationMatrix();
        return new Vec3(m[axis], m[3 + axis], m[6 + axis]);
    }

    private static double div(double a, double b) {
        return Math.abs(b) < 1e-12 ? 0 : a / b;
    }

    /** Right-handed rotation of {@code degrees} about world axis 0, 1 or 2, row-major. */
    static double[] axisRotation(int axis, double degrees) {
        double r = Math.toRadians(degrees), c = Math.cos(r), s = Math.sin(r);
        // Whole quarter turns come out exact, so poses turned by the gizmo don't collect rounding noise.
        if (Math.abs(c) < 1e-15) c = 0;
        if (Math.abs(s) < 1e-15) s = 0;
        return switch (axis) {
            case 0 -> new double[]{1, 0, 0, 0, c, -s, 0, s, c};
            case 1 -> new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
            case 2 -> new double[]{c, -s, 0, s, c, 0, 0, 0, 1};
            default -> throw new IllegalArgumentException("axis must be 0, 1 or 2: " + axis);
        };
    }

    static double[] multiply(double[] a, double[] b) {
        double[] out = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
        }
        return out;
    }

    private static double[] apply(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    /** XYZ Euler angles (degrees) of a rotation matrix {@code Rz · Ry · Rx}, rounded to 1e-9 against drift. */
    static Vec3 euler(double[] m) {
        double sy = Math.clamp(-m[6], -1, 1);
        double y = Math.asin(sy), x, z;
        if (Math.abs(sy) < 1 - 1e-9) {
            x = Math.atan2(m[7], m[8]);
            z = Math.atan2(m[3], m[0]);
        } else {
            // Gimbal lock (Y at ±90°): only X ± Z is defined; put it all in X.
            x = Math.atan2(-m[5], m[4]);
            z = 0;
        }
        return new Vec3(tidy(Math.toDegrees(x)), tidy(Math.toDegrees(y)), tidy(Math.toDegrees(z)));
    }

    private static double tidy(double deg) {
        double r = Math.rint(deg * 1e9) / 1e9;
        return r == 0 ? 0 : r; // no -0.0
    }
}
