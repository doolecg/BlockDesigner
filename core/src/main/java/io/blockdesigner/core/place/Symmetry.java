package io.blockdesigner.core.place;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.transform.Transform;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirror and radial symmetry for building, like Effortless Building: every placed or broken block is repeated in
 * mirror images across up to three planes and/or around a vertical axis.
 *
 * <p>The centre is kept in half blocks ({@code cx2 = 2 × x}), so it can sit on a block's centre (odd) or on the
 * boundary between two blocks (even): mirroring about a block centre keeps that block's column, mirroring about a
 * boundary gives an even-width build.
 *
 * @param mirrorX       mirror across the plane x = cx2 / 2 (east ↔ west)
 * @param mirrorY       mirror across the plane y = cy2 / 2 (up ↔ down)
 * @param mirrorZ       mirror across the plane z = cz2 / 2 (north ↔ south)
 * @param radial        number of copies around the vertical axis through the centre (1 = off)
 * @param flipStates    turn stairs, doors, logs… to match each copy
 */
public record Symmetry(boolean mirrorX, boolean mirrorY, boolean mirrorZ, int radial, int cx2, int cy2, int cz2, boolean flipStates) {

    public static final Symmetry OFF = new Symmetry(false, false, false, 1, 1, 1, 1, true);

    public Symmetry {
        radial = Math.clamp(radial, 1, 32);
    }

    public boolean active() {
        return mirrorX || mirrorY || mirrorZ || radial > 1;
    }

    /** Centre in block coordinates (x.5 when on a block's centre). */
    public double centerX() {
        return cx2 / 2.0;
    }

    public double centerY() {
        return cy2 / 2.0;
    }

    public double centerZ() {
        return cz2 / 2.0;
    }

    /** Symmetry centred on a block's centre. */
    public Symmetry centeredOn(BlockPos p, boolean blockCentre) {
        int off = blockCentre ? 1 : 0;
        return new Symmetry(mirrorX, mirrorY, mirrorZ, radial, 2 * p.x() + off, 2 * p.y() + off, 2 * p.z() + off, flipStates);
    }

    /** Number of copies each block becomes (including itself), before overlaps are merged. */
    public int copies() {
        return radial * (mirrorX ? 2 : 1) * (mirrorY ? 2 : 1) * (mirrorZ ? 2 : 1);
    }

    /**
     * The block and all its symmetric copies, original first. Copies landing on the same cell keep the first one.
     * {@code state} may be null when only positions matter (breaking).
     */
    public Map<BlockPos, BlockState> apply(BlockPos p, BlockState state) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        out.put(p, state);
        if (!active()) return out;
        BlockTransformer bt = BlockTransformer.defaults();
        if (radial > 1) {
            double cx = centerX(), cz = centerZ();
            for (int k = 1; k < radial; k++) {
                double a = 2 * Math.PI * k / radial, cos = Math.cos(a), sin = Math.sin(a);
                double dx = p.x() + 0.5 - cx, dz = p.z() + 0.5 - cz;
                // Same turning direction as Transform (east → south): x' = x·cos − z·sin, z' = x·sin + z·cos.
                int nx = (int) Math.floor(cx + dx * cos - dz * sin + 1e-6), nz = (int) Math.floor(cz + dx * sin + dz * cos + 1e-6);
                BlockState s = state;
                if (s != null && flipStates) {
                    int quarter = Math.floorMod((int) Math.round(k * 4.0 / radial), 4);
                    s = bt.apply(s, Transform.rotation(quarter));
                }
                out.putIfAbsent(new BlockPos(nx, p.y(), nz), s);
            }
        }
        if (mirrorX) mirror(out, 0, bt);
        if (mirrorZ) mirror(out, 2, bt);
        if (mirrorY) mirror(out, 1, bt);
        return out;
    }

    private void mirror(Map<BlockPos, BlockState> out, int axis, BlockTransformer bt) {
        Map<BlockPos, BlockState> add = new LinkedHashMap<>();
        for (var e : out.entrySet()) {
            BlockPos q = e.getKey();
            BlockPos m = switch (axis) {
                case 0 -> new BlockPos(cx2 - q.x() - 1, q.y(), q.z());
                case 1 -> new BlockPos(q.x(), cy2 - q.y() - 1, q.z());
                default -> new BlockPos(q.x(), q.y(), cz2 - q.z() - 1);
            };
            BlockState s = e.getValue();
            if (s != null && flipStates) {
                s = switch (axis) {
                    case 0 -> bt.apply(s, new Transform(0, Transform.Mirror.X));
                    case 2 -> bt.apply(s, new Transform(0, Transform.Mirror.Z));
                    default -> flipVertical(s);
                };
            }
            add.putIfAbsent(m, s);
        }
        add.forEach(out::putIfAbsent);
    }

    /** Upside down: stairs and trapdoors swap halves, slabs swap top and bottom, up/down facings swap. */
    static BlockState flipVertical(BlockState s) {
        BlockState out = s;
        String half = s.get("half");
        if ("top".equals(half)) out = out.with("half", "bottom");
        else if ("bottom".equals(half)) out = out.with("half", "top");
        String type = s.get("type");
        if ("top".equals(type)) out = out.with("type", "bottom");
        else if ("bottom".equals(type)) out = out.with("type", "top");
        String facing = s.get("facing");
        if ("up".equals(facing)) out = out.with("facing", "down");
        else if ("down".equals(facing)) out = out.with("facing", "up");
        String face = s.get("face");
        if ("floor".equals(face)) out = out.with("face", "ceiling");
        else if ("ceiling".equals(face)) out = out.with("face", "floor");
        String hanging = s.get("hanging");
        if (hanging != null && s.name().endsWith("lantern")) out = out.with("hanging", "true".equals(hanging) ? "false" : "true");
        return out;
    }
}
