package io.blockdesigner.core.place;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Build-mode shapes placed by right-dragging (in the spirit of the Effortless Building mod). A shape is defined by
 * the cell where the drag started ({@code a}), the cell under the mouse now ({@code b}) and, for 3D shapes, a
 * height set with the mouse wheel.
 */
public final class ShapeTool {
    /** Largest number of blocks one shape may place. */
    public static final int MAX_BLOCKS = 250_000;

    public enum Shape {
        SINGLE("Single block", "No shape: normal placing", Plane.NONE),
        LINE("Line", "A straight line along X, Y or Z", Plane.AXIS),
        WALL("Wall", "A flat vertical rectangle", Plane.VERTICAL),
        FLOOR("Floor", "A flat horizontal rectangle", Plane.HORIZONTAL),
        BOX("Box", "A solid box; scroll to set the height", Plane.HORIZONTAL),
        HOLLOW_BOX("Room", "Walls, floor and ceiling of a box; scroll to set the height", Plane.HORIZONTAL),
        WALLS("Walls", "Four walls around a rectangle, no floor or ceiling; scroll to set the height", Plane.HORIZONTAL),
        CIRCLE("Circle", "A filled disc around the start", Plane.HORIZONTAL),
        RING("Ring", "A circle outline around the start", Plane.HORIZONTAL),
        CYLINDER("Cylinder", "A round tower wall; scroll to set the height", Plane.HORIZONTAL),
        SPHERE("Sphere", "A hollow ball resting on the start", Plane.HORIZONTAL),
        DOME("Dome", "A hollow half ball over the start", Plane.HORIZONTAL),
        PYRAMID("Pyramid", "A stepped pyramid shell centred on the start", Plane.HORIZONTAL);

        public final String label, description;
        /** Where the end point is found while dragging. */
        public final Plane plane;

        Shape(String label, String description, Plane plane) {
            this.label = label;
            this.description = description;
            this.plane = plane;
        }

        /** Shapes whose height comes from the mouse wheel rather than the drag. */
        public boolean usesHeight() {
            return this == BOX || this == HOLLOW_BOX || this == WALLS || this == CYLINDER;
        }

        /**
         * Shapes that grow out of the face the drag started on: up from a top face, down from an underside and
         * sideways from a side. Floor stays flat and horizontal whatever face it starts on.
         */
        public boolean followsFace() {
            return plane == Plane.HORIZONTAL && this != FLOOR;
        }
    }

    /** How the drag end is picked: along an axis, on a vertical plane, or on the start's horizontal plane. */
    public enum Plane { NONE, AXIS, VERTICAL, HORIZONTAL }

    private ShapeTool() {
    }

    /**
     * The cells of a shape growing along {@code up}, the unit normal of the face the drag started on (null or +Y is
     * the usual upwards shape). The shape is built upright around {@code a} and turned so its up is {@code up};
     * {@code b} is expected on the plane through {@code a} across {@code up}.
     */
    public static List<BlockPos> cells(Shape shape, BlockPos a, BlockPos b, int height, BlockPos up) {
        if (upright(shape, up)) return cells(shape, a, b, height);
        List<BlockPos> local = cells(shape, a, toLocal(a, b, up), height);
        List<BlockPos> out = new ArrayList<>(local.size());
        for (BlockPos p : local) out.add(toWorld(a, p, up));
        return out;
    }

    /** {@link #estimate(Shape, BlockPos, BlockPos, int)} for a shape growing along {@code up}. */
    public static long estimate(Shape shape, BlockPos a, BlockPos b, int height, BlockPos up) {
        return estimate(shape, a, upright(shape, up) ? b : toLocal(a, b, up), height);
    }

    private static boolean upright(Shape shape, BlockPos up) {
        return up == null || !shape.followsFace() || (up.x() == 0 && up.y() > 0 && up.z() == 0);
    }

    /** The axis (0 x, 1 y, 2 z) of a unit normal. */
    private static int axis(BlockPos n) {
        return n.x() != 0 ? 0 : n.z() != 0 ? 2 : 1;
    }

    /** {@code p} in the shape's upright frame around {@code a}: the up axis is swapped into Y, flipped when negative. */
    private static BlockPos toLocal(BlockPos a, BlockPos p, BlockPos up) {
        int[] v = {p.x() - a.x(), p.y() - a.y(), p.z() - a.z()};
        int k = axis(up), s = up.x() + up.y() + up.z() < 0 ? -1 : 1;
        int t = v[1];
        v[1] = v[k];
        v[k] = t;
        v[1] *= s;
        return new BlockPos(a.x() + v[0], a.y() + v[1], a.z() + v[2]);
    }

    /** The inverse of {@link #toLocal}. */
    private static BlockPos toWorld(BlockPos a, BlockPos p, BlockPos up) {
        int[] v = {p.x() - a.x(), p.y() - a.y(), p.z() - a.z()};
        int k = axis(up), s = up.x() + up.y() + up.z() < 0 ? -1 : 1;
        v[1] *= s;
        int t = v[1];
        v[1] = v[k];
        v[k] = t;
        return new BlockPos(a.x() + v[0], a.y() + v[1], a.z() + v[2]);
    }

    /**
     * The cells of a shape, in a stable order. {@code height} (≥1) is used by the shapes that {@link Shape#usesHeight()}.
     * Returns an empty list if the shape would exceed {@link #MAX_BLOCKS}.
     */
    public static List<BlockPos> cells(Shape shape, BlockPos a, BlockPos b, int height) {
        int h = Math.max(1, height);
        if (estimate(shape, a, b, h) > MAX_BLOCKS) return List.of();
        Set<BlockPos> out = new LinkedHashSet<>();
        switch (shape) {
            case SINGLE -> out.add(a);
            case LINE -> line(a, b, out);
            case WALL, FLOOR -> box(Box.of(a, b), out, false);
            case BOX -> box(footprint(a, b, h), out, false);
            case HOLLOW_BOX -> box(footprint(a, b, h), out, true);
            case WALLS -> walls(footprint(a, b, h), out);
            case CIRCLE -> disc(a, radius(a, b), a.y(), out, false);
            case RING -> disc(a, radius(a, b), a.y(), out, true);
            case CYLINDER -> {
                int r = radius(a, b);
                for (int y = a.y(); y < a.y() + h; y++) disc(a, r, y, out, true);
            }
            case SPHERE -> {
                int r = radius(a, b);
                ball(new BlockPos(a.x(), a.y() + r, a.z()), r, out, false);
            }
            case DOME -> ball(a, radius(a, b), out, true);
            case PYRAMID -> pyramid(a, Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z())), out);
        }
        if (out.size() > MAX_BLOCKS) return List.of();
        return new ArrayList<>(out);
    }

    /** Size of the shape's bounding box, or null when empty. */
    public static Box bounds(List<BlockPos> cells) {
        if (cells.isEmpty()) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : cells) {
            x0 = Math.min(x0, p.x());
            y0 = Math.min(y0, p.y());
            z0 = Math.min(z0, p.z());
            x1 = Math.max(x1, p.x());
            y1 = Math.max(y1, p.y());
            z1 = Math.max(z1, p.z());
        }
        return new Box(x0, y0, z0, x1, y1, z1);
    }

    /** Close upper bound of the block count, computed without generating, to refuse enormous shapes at once. */
    public static long estimate(Shape shape, BlockPos a, BlockPos b, int height) {
        long dx = Math.abs(b.x() - a.x()) + 1L, dy = Math.abs(b.y() - a.y()) + 1L, dz = Math.abs(b.z() - a.z()) + 1L;
        long h = Math.max(1, height);
        long r = radius(a, b), d = 2 * r + 1;
        long half = Math.max(Math.abs(b.x() - a.x()), Math.abs(b.z() - a.z()));
        return switch (shape) {
            case SINGLE -> 1;
            case LINE -> Math.max(dx, Math.max(dy, dz));
            case WALL, FLOOR -> dx * dy * dz;
            case BOX -> dx * dz * h;
            case HOLLOW_BOX -> 2 * (dx * dz + dx * h + dz * h);
            case WALLS -> 2 * (dx + dz) * h;
            case CIRCLE -> d * d;
            case RING -> 8 * (r + 1);
            case CYLINDER -> 8 * (r + 1) * h;
            case SPHERE, DOME -> 16 * (r + 1) * (r + 1);
            case PYRAMID -> 4 * (half + 1) * (half + 1) + (2 * half + 1) * (2 * half + 1);
        };
    }

    private static Box footprint(BlockPos a, BlockPos b, int h) {
        return new Box(Math.min(a.x(), b.x()), a.y(), Math.min(a.z(), b.z()), Math.max(a.x(), b.x()), a.y() + h - 1, Math.max(a.z(), b.z()));
    }

    private static int radius(BlockPos a, BlockPos b) {
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private static void box(Box bx, Set<BlockPos> out, boolean hollow) {
        for (int y = bx.minY(); y <= bx.maxY(); y++)
            for (int z = bx.minZ(); z <= bx.maxZ(); z++)
                for (int x = bx.minX(); x <= bx.maxX(); x++) {
                    boolean surface = x == bx.minX() || x == bx.maxX() || y == bx.minY() || y == bx.maxY() || z == bx.minZ() || z == bx.maxZ();
                    if (!hollow || surface) out.add(new BlockPos(x, y, z));
                }
    }

    private static void walls(Box bx, Set<BlockPos> out) {
        for (int y = bx.minY(); y <= bx.maxY(); y++)
            for (int z = bx.minZ(); z <= bx.maxZ(); z++)
                for (int x = bx.minX(); x <= bx.maxX(); x++) {
                    if (x == bx.minX() || x == bx.maxX() || z == bx.minZ() || z == bx.maxZ()) out.add(new BlockPos(x, y, z));
                }
    }

    /** 3D line through cell centres, stepping along the longest axis so it has no gaps. */
    private static void line(BlockPos a, BlockPos b, Set<BlockPos> out) {
        int dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        int n = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            out.add(new BlockPos((int) Math.round(a.x() + dx * t), (int) Math.round(a.y() + dy * t), (int) Math.round(a.z() + dz * t)));
        }
    }

    /** A disc of radius r (r = 0 is one block); the outline keeps only cells with an outside neighbour. */
    private static void disc(BlockPos c, int r, int y, Set<BlockPos> out, boolean outline) {
        double lim = (r + 0.5) * (r + 0.5);
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > lim) continue;
                if (outline && r > 0 && (dx + 1) * (dx + 1) + dz * dz <= lim && (dx - 1) * (dx - 1) + dz * dz <= lim
                        && dx * dx + (dz + 1) * (dz + 1) <= lim && dx * dx + (dz - 1) * (dz - 1) <= lim) continue;
                out.add(new BlockPos(c.x() + dx, y, c.z() + dz));
            }
    }

    /** A hollow ball (or its upper half) one block thick. */
    private static void ball(BlockPos c, int r, Set<BlockPos> out, boolean upperHalf) {
        double lim = (r + 0.5) * (r + 0.5);
        for (int dy = upperHalf ? 0 : -r; dy <= r; dy++)
            for (int dz = -r; dz <= r; dz++)
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy + dz * dz > lim) continue;
                    boolean inner = r > 0 && inside(dx + 1, dy, dz, lim) && inside(dx - 1, dy, dz, lim) && inside(dx, dy + 1, dz, lim)
                            && inside(dx, dy - 1, dz, lim) && inside(dx, dy, dz + 1, lim) && inside(dx, dy, dz - 1, lim);
                    if (!inner) out.add(new BlockPos(c.x() + dx, c.y() + dy, c.z() + dz));
                }
    }

    private static boolean inside(int x, int y, int z, double lim) {
        return x * x + y * y + z * z <= lim;
    }

    /** Stepped pyramid shell: each layer one block smaller on every side. */
    private static void pyramid(BlockPos c, int half, Set<BlockPos> out) {
        for (int i = 0; i <= half; i++) {
            int s = half - i, y = c.y() + i;
            for (int dz = -s; dz <= s; dz++)
                for (int dx = -s; dx <= s; dx++) {
                    boolean edge = Math.abs(dx) == s || Math.abs(dz) == s;
                    if (edge || i == half) out.add(new BlockPos(c.x() + dx, y, c.z() + dz));
                }
        }
    }
}
