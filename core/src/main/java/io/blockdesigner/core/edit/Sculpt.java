package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Voxel sculpting brushes: draw, erase, smooth, erode, fill, pinch, and the terrain brushes raise, lower, flatten and
 * slope. Each {@link #apply} is one dab of the brush around a centre block; it reads and writes through a {@link World}
 * and returns the cells it changed.
 */
public final class Sculpt {
    private Sculpt() {
    }

    public enum Mode {
        DRAW("Draw", 'D', "Add blocks"),
        ERASE("Erase", 'E', "Remove blocks"),
        SMOOTH("Smooth", 'S', "Knock off bumps and fill dents"),
        ERODE("Erode", 'R', "Wear away exposed corners and edges"),
        FILL("Fill", 'F', "Fill pits and crevices"),
        PINCH("Pinch", 'P', "Pull blocks toward the centre (Ctrl: push out)"),
        RAISE("Raise", 'U', "Raise the ground into a hill that slopes away"),
        LOWER("Lower", 'L', "Sink the ground with the same falloff"),
        FLATTEN("Flatten", 'T', "Level the ground to the height you click"),
        SLOPE("Slope", 'O', "Ramp up from where the stroke started (Ctrl: cut down)");

        public final String label;
        public final char key;
        public final String description;

        Mode(String label, char key, String description) {
            this.label = label;
            this.key = key;
            this.description = description;
        }

        /** Ctrl while painting: the opposite brush where there is one. */
        public Mode inverse() {
            return switch (this) {
                case DRAW -> ERASE;
                case ERASE -> DRAW;
                case ERODE -> FILL;
                case FILL -> ERODE;
                case RAISE -> LOWER;
                case LOWER -> RAISE;
                default -> this;
            };
        }

        /** Terrain brushes work on columns (a cylinder), the others on the brush shape. */
        public boolean terrain() {
            return this == RAISE || this == LOWER || this == FLATTEN || this == SLOPE;
        }
    }

    public interface World {
        BlockState get(BlockPos p);

        void set(BlockPos p, BlockState s);
    }

    /**
     * @param size     1 = one block; n = a shape (2n-1) blocks across
     * @param cube     cube instead of sphere
     * @param strength 1–5: iterations for smooth / erode / fill / pinch, height for raise / lower, grade for slope
     */
    public record Brush(int size, boolean cube, int strength) {
        public int radius() {
            return Math.max(0, size - 1);
        }
    }

    /**
     * One dab.
     *
     * @param invert     Ctrl held: pinch pushes out, slope cuts down (the other inverses are picked by the caller)
     * @param material   the block to add (null: take it from the neighbours)
     * @param slopeStart where a slope stroke began (its base height and position)
     * @param yAllowed   world Y levels that may change (the slice view)
     */
    public static List<BlockPos> apply(Mode mode, boolean invert, Brush b, BlockPos center, World w, Supplier<BlockState> material,
                                       BlockPos slopeStart, IntPredicate yAllowed) {
        return apply(mode, invert, b, center, null, w, material, slopeStart, yAllowed);
    }

    /**
     * As above, with the aimed face's normal: smoothing a top (or bottom) face smooths the terrain's heightmap, a side
     * face smooths in 3D. Without a normal it is worked out from the blocks around the centre.
     */
    public static List<BlockPos> apply(Mode mode, boolean invert, Brush b, BlockPos center, BlockPos normal, World w,
                                       Supplier<BlockState> material, BlockPos slopeStart, IntPredicate yAllowed) {
        Edits e = new Edits(w, yAllowed);
        switch (mode) {
            case DRAW -> {
                for (BlockPos p : shape(b, center)) if (w.get(p).isAir()) e.set(p, pick(material, w, p));
            }
            case ERASE -> {
                for (BlockPos p : shape(b, center)) if (!w.get(p).isAir()) e.set(p, BlockState.AIR);
            }
            case SMOOTH -> {
                boolean top = normal != null ? normal.y() != 0
                        : w.get(center.add(0, 1, 0)).isAir() && !w.get(center.add(0, -1, 0)).isAir();
                if (top) smoothHeights(b, center, e);
                else for (int i = 0; i <= b.strength(); i++) smooth3d(b, center, e, material);
            }
            case ERODE -> {
                for (int i = 0; i < b.strength(); i++) erode(b, center, e);
            }
            case FILL -> {
                for (int i = 0; i < b.strength(); i++) fill(b, center, e, material);
            }
            case PINCH -> {
                for (int i = 0; i < b.strength(); i++) pinch(b, center, e, invert);
            }
            case RAISE, LOWER -> heights(b, center, e, material, mode == Mode.RAISE);
            case FLATTEN -> flatten(b, center, e, material);
            case SLOPE -> slope(b, center, e, material, slopeStart != null ? slopeStart : center, invert);
        }
        return e.changed();
    }

    /** The cells of the brush shape around {@code c}. */
    public static List<BlockPos> shape(Brush b, BlockPos c) {
        int r = b.radius();
        List<BlockPos> out = new ArrayList<>();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++)
                    if (b.cube() || x * x + y * y + z * z <= (r + 0.5) * (r + 0.5)) out.add(c.add(x, y, z));
        return out;
    }

    // ---- shape brushes ---------------------------------------------------------------------------------------

    /**
     * 3D smoothing: each cell becomes solid or empty by the share of solid blocks in a cube around it (3–7 blocks
     * wide, growing with the brush), with a small dead zone so edges don't flicker between passes. Counts come from a
     * 3D prefix sum, so even big brushes stay fast. Double-buffered, so the result doesn't depend on order.
     */
    private static void smooth3d(Brush b, BlockPos c, Edits e, Supplier<BlockState> material) {
        int r = b.radius(), k = Math.clamp(Math.round((r + 1) / 2f), 1, 3), m = r + k, n = 2 * m + 1;
        int[] sum = new int[(n + 1) * (n + 1) * (n + 1)];
        for (int x = 0; x < n; x++)
            for (int y = 0; y < n; y++)
                for (int z = 0; z < n; z++) {
                    int v = e.get(c.add(x - m, y - m, z - m)).isAir() ? 0 : 1;
                    sum[idx(n, x + 1, y + 1, z + 1)] = v + sum[idx(n, x, y + 1, z + 1)] + sum[idx(n, x + 1, y, z + 1)] + sum[idx(n, x + 1, y + 1, z)]
                            - sum[idx(n, x, y, z + 1)] - sum[idx(n, x, y + 1, z)] - sum[idx(n, x + 1, y, z)] + sum[idx(n, x, y, z)];
                }
        double vol = Math.pow(2 * k + 1, 3);
        Map<BlockPos, BlockState> next = new LinkedHashMap<>();
        for (BlockPos p : shape(b, c)) {
            int x0 = p.x() - c.x() + m - k, y0 = p.y() - c.y() + m - k, z0 = p.z() - c.z() + m - k;
            int x1 = x0 + 2 * k + 1, y1 = y0 + 2 * k + 1, z1 = z0 + 2 * k + 1;
            int count = sum[idx(n, x1, y1, z1)] - sum[idx(n, x0, y1, z1)] - sum[idx(n, x1, y0, z1)] - sum[idx(n, x1, y1, z0)]
                    + sum[idx(n, x0, y0, z1)] + sum[idx(n, x0, y1, z0)] + sum[idx(n, x1, y0, z0)] - sum[idx(n, x0, y0, z0)];
            double f = count / vol;
            boolean is = !e.get(p).isAir();
            if (is && f < 0.42) next.put(p, BlockState.AIR);
            else if (!is && f > 0.58) next.put(p, common(e, p, material));
        }
        next.forEach(e::set);
    }

    private static int idx(int n, int x, int y, int z) {
        return (x * (n + 1) + y) * (n + 1) + z;
    }

    /**
     * Terrain smoothing, like WorldEdit's //smooth: finds each column's surface, blurs the heights (strength passes,
     * wider for bigger brushes) and raises or lowers every column to its new height, softly towards the brush rim.
     * The top block stays on top (grass stays grass) and new ground below it copies what was under it.
     */
    private static void smoothHeights(Brush b, BlockPos c, Edits e) {
        int r = b.radius(), k = Math.max(1, (r + 1) / 2), m = r + k, reach = r + 6;
        int n = 2 * m + 1;
        double[] h = new double[n * n];
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                Integer t = top(e, c.x() + x - m, c.z() + z - m, c.y(), reach);
                h[x * n + z] = t == null ? Double.NaN : t;
            }
        double[] cur = h.clone();
        for (int pass = 0; pass < b.strength() + 1; pass++) {
            double[] nxt = cur.clone();
            for (int x = k; x < n - k; x++)
                for (int z = k; z < n - k; z++) {
                    if (Double.isNaN(cur[x * n + z])) continue;
                    double total = 0, weight = 0;
                    for (int dx = -k; dx <= k; dx++)
                        for (int dz = -k; dz <= k; dz++) {
                            double v = cur[(x + dx) * n + z + dz];
                            if (Double.isNaN(v)) continue;
                            // Tent weights: nearer columns count more.
                            double wgt = (k + 1 - Math.abs(dx)) * (k + 1 - Math.abs(dz));
                            total += v * wgt;
                            weight += wgt;
                        }
                    if (weight > 0) nxt[x * n + z] = total / weight;
                }
            cur = nxt;
        }
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z) / (r + 0.5);
                if (d > 1 && !b.cube()) continue;
                int i = (x + m) * n + (z + m);
                if (Double.isNaN(h[i])) continue;
                double fall = b.cube() ? 1 : Math.min(1, 1.6 * Math.cos(Math.min(1, d) * Math.PI / 2));
                int from = (int) h[i], to = (int) Math.round(h[i] + (cur[i] - h[i]) * fall);
                if (to == from) continue;
                int cx = c.x() + x, cz = c.z() + z;
                BlockState topBlock = e.get(new BlockPos(cx, from, cz));
                BlockState below = e.get(new BlockPos(cx, from - 1, cz));
                BlockState fill = below.isAir() ? topBlock : below;
                if (to > from) {
                    for (int y = from; y < to; y++) e.set(new BlockPos(cx, y, cz), fill);
                    e.set(new BlockPos(cx, to, cz), topBlock);
                } else {
                    for (int y = from; y > to; y--) e.set(new BlockPos(cx, y, cz), BlockState.AIR);
                    e.set(new BlockPos(cx, to, cz), topBlock);
                }
            }
    }

    /** Removes solid cells with three or more open faces (corners first, then edges as it repeats). */
    private static void erode(Brush b, BlockPos c, Edits e) {
        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos p : shape(b, c)) if (!e.get(p).isAir() && openFaces(e, p) >= 3) gone.add(p);
        for (BlockPos p : gone) e.set(p, BlockState.AIR);
    }

    /** Fills air cells with four or more solid faces around them. */
    private static void fill(Brush b, BlockPos c, Edits e, Supplier<BlockState> material) {
        Map<BlockPos, BlockState> add = new LinkedHashMap<>();
        for (BlockPos p : shape(b, c)) if (e.get(p).isAir() && 6 - openFaces(e, p) >= 4) add.put(p, common(e, p, material));
        add.forEach(e::set);
    }

    /** Moves each block one step toward the centre (or away with {@code out}) along its longest axis, if there's room. */
    private static void pinch(Brush b, BlockPos c, Edits e, boolean out) {
        List<BlockPos> cells = new ArrayList<>(shape(b, c));
        // Inner blocks move first when pinching (so outer ones can follow), outer first when pushing.
        cells.sort((p, q) -> Integer.compare(dist2(p, c), dist2(q, c)) * (out ? -1 : 1));
        for (BlockPos p : cells) {
            BlockState s = e.get(p);
            if (s.isAir() || p.equals(c)) continue;
            int dx = c.x() - p.x(), dy = c.y() - p.y(), dz = c.z() - p.z();
            int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
            int sx = 0, sy = 0, sz = 0;
            if (ax >= ay && ax >= az) sx = Integer.signum(dx);
            else if (ay >= az) sy = Integer.signum(dy);
            else sz = Integer.signum(dz);
            if (out) {
                sx = -sx;
                sy = -sy;
                sz = -sz;
            }
            BlockPos to = p.add(sx, sy, sz);
            if (!e.get(to).isAir()) continue;
            e.set(to, s);
            e.set(p, BlockState.AIR);
        }
    }

    // ---- terrain brushes -------------------------------------------------------------------------------------

    /** Columns of the brush circle, with a 0..1 falloff (1 in the middle, soft towards the rim). */
    private static Map<BlockPos, Double> columns(Brush b, BlockPos c) {
        int r = b.radius();
        Map<BlockPos, Double> out = new LinkedHashMap<>();
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z) / (r + 0.5);
                if (d > 1 && !b.cube()) continue;
                double f = b.cube() ? 1 : Math.cos(Math.min(1, d) * Math.PI / 2);
                out.put(new BlockPos(c.x() + x, 0, c.z() + z), f * f);
            }
        return out;
    }

    /** The highest solid block of a column near {@code y}, or null. */
    private static Integer top(Edits e, int x, int z, int y, int reach) {
        for (int yy = y + reach; yy >= y - reach; yy--) {
            if (!e.get(new BlockPos(x, yy, z)).isAir()) return yy;
        }
        return null;
    }

    private static void heights(Brush b, BlockPos c, Edits e, Supplier<BlockState> material, boolean up) {
        int reach = b.radius() + 8;
        for (var col : columns(b, c).entrySet()) {
            int x = col.getKey().x(), z = col.getKey().z();
            int dh = (int) Math.round(b.strength() * col.getValue());
            if (dh == 0) continue;
            Integer t = top(e, x, z, c.y(), reach);
            if (t == null) continue;
            BlockPos topPos = new BlockPos(x, t, z);
            if (up) {
                BlockState m = material != null && material.get() != null ? material.get() : e.get(topPos);
                for (int i = 1; i <= dh; i++) e.set(topPos.add(0, i, 0), m);
            } else {
                for (int i = 0; i < dh; i++) e.set(topPos.add(0, -i, 0), BlockState.AIR);
            }
        }
    }

    /** Everything above the clicked height goes; gaps down to the ground below it are filled. */
    private static void flatten(Brush b, BlockPos c, Edits e, Supplier<BlockState> material) {
        int h = c.y(), reach = b.radius() + 2 + b.strength() * 2;
        for (BlockPos col : columns(b, c).keySet()) {
            int x = col.x(), z = col.z();
            for (int y = h + 1; y <= h + reach; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!e.get(p).isAir()) e.set(p, BlockState.AIR);
            }
            Integer ground = top(e, x, z, h - reach / 2, reach / 2);
            BlockPos level = new BlockPos(x, h, z);
            if (ground == null) {
                // No ground below: still close gaps in the surface being levelled (a hole with ground around it).
                int around = 0;
                for (int[] f : FACES) if (f[1] == 0 && !e.get(level.add(f[0], 0, f[2])).isAir()) around++;
                if (e.get(level).isAir() && around >= 2) e.set(level, common(e, level, material));
                continue;
            }
            if (ground >= h) continue;
            BlockState m = material != null && material.get() != null ? material.get() : e.get(new BlockPos(x, ground, z));
            for (int y = ground + 1; y <= h; y++) e.set(new BlockPos(x, y, z), m);
        }
    }

    /** A ramp: height rises from the stroke's start by strength/4 blocks per block of distance (or cuts down to it). */
    private static void slope(Brush b, BlockPos c, Edits e, Supplier<BlockState> material, BlockPos start, boolean down) {
        double grade = b.strength() / 4.0;
        int reach = b.radius() + 24;
        for (BlockPos col : columns(b, c).keySet()) {
            int x = col.x(), z = col.z();
            double dist = Math.hypot(x - start.x(), z - start.z());
            int target = start.y() + (int) Math.round(dist * grade * (down ? -1 : 1));
            Integer t = top(e, x, z, target, reach);
            if (!down) {
                if (t == null || t >= target) continue;
                BlockState m = material != null && material.get() != null ? material.get() : e.get(new BlockPos(x, t, z));
                for (int y = t + 1; y <= target; y++) e.set(new BlockPos(x, y, z), m);
            } else {
                if (t == null || t <= target) continue;
                for (int y = t; y > target; y--) e.set(new BlockPos(x, y, z), BlockState.AIR);
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static int openFaces(Edits e, BlockPos p) {
        int n = 0;
        for (int[] f : FACES) if (e.get(p.add(f[0], f[1], f[2])).isAir()) n++;
        return n;
    }

    /** The held block, or else the most common block around {@code p}. */
    private static BlockState common(Edits e, BlockPos p, Supplier<BlockState> material) {
        if (material != null) {
            BlockState m = material.get();
            if (m != null && !m.isAir()) return m;
        }
        Map<BlockState, Integer> counts = new HashMap<>();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    BlockState s = e.get(p.add(x, y, z));
                    if (!s.isAir()) counts.merge(s, 1, Integer::sum);
                }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(BlockState.of("stone"));
    }

    private static BlockState pick(Supplier<BlockState> material, World w, BlockPos p) {
        BlockState m = material != null ? material.get() : null;
        return m != null ? m : BlockState.of("stone");
    }

    private static int dist2(BlockPos a, BlockPos b) {
        int x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
        return x * x + y * y + z * z;
    }

    /** Writes through to the world (skipping levels the slice hides) and remembers what changed. */
    private static final class Edits {
        final World w;
        final IntPredicate yAllowed;
        final Map<BlockPos, Boolean> changed = new LinkedHashMap<>();

        Edits(World w, IntPredicate yAllowed) {
            this.w = w;
            this.yAllowed = yAllowed != null ? yAllowed : y -> true;
        }

        BlockState get(BlockPos p) {
            return w.get(p);
        }

        void set(BlockPos p, BlockState s) {
            if (s == null || !yAllowed.test(p.y()) || w.get(p) == s) return;
            w.set(p, s);
            changed.put(p, Boolean.TRUE);
        }

        List<BlockPos> changed() {
            return new ArrayList<>(changed.keySet());
        }
    }
}
