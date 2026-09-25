package io.blockdesigner.core.transform;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A quarter turn about any axis, e.g. tipping a structure over onto its side. {@link Transform} only covers horizontal
 * turns, so a tilt is applied by rewriting blocks and their directional properties.
 *
 * <p>Some blocks have no tipped form (stairs, slabs, torches...). Their properties stay as they were whenever the
 * turned value would be invalid for that block.
 */
public final class Tilt {
    private static final String[] DIRS = {"down", "up", "north", "south", "west", "east"};
    private static final int[][] VECS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private final int ax, ay, az, q;

    /**
     * @param axis unit axis vector (one of the six directions)
     * @param turn +1 turns anticlockwise when viewed from the tip of {@code axis} (right-hand rule), -1 clockwise
     */
    public Tilt(BlockPos axis, int turn) {
        if (Math.abs(axis.x()) + Math.abs(axis.y()) + Math.abs(axis.z()) != 1) throw new IllegalArgumentException("Not a unit axis: " + axis);
        if (turn != 1 && turn != -1) throw new IllegalArgumentException("turn must be ±1");
        this.ax = axis.x();
        this.ay = axis.y();
        this.az = axis.z();
        this.q = turn;
    }

    /** Turns a vector: {@code (a·v)a + q(a × v)}. */
    public BlockPos apply(int vx, int vy, int vz) {
        int dot = ax * vx + ay * vy + az * vz;
        return new BlockPos(dot * ax + q * (ay * vz - az * vy), dot * ay + q * (az * vx - ax * vz), dot * az + q * (ax * vy - ay * vx));
    }

    /** Turns a position about the centre of block {@code pivot}. */
    public BlockPos apply(BlockPos p, BlockPos pivot) {
        return apply(p.x() - pivot.x(), p.y() - pivot.y(), p.z() - pivot.z()).add(pivot);
    }

    /** Turns a direction name ("north", "up"...); returns non-directions unchanged. */
    public String apply(String dir) {
        int i = index(dir);
        if (i < 0) return dir;
        BlockPos v = apply(VECS[i][0], VECS[i][1], VECS[i][2]);
        return name(v.x(), v.y(), v.z());
    }

    /**
     * Turns a block state's directional properties. {@code valid} says whether a candidate state exists for that
     * block; any property whose turned value fails it keeps its old value.
     */
    public BlockState apply(BlockState s, Predicate<BlockState> valid) {
        if (s.properties().isEmpty()) return s;
        BlockState out = s;
        if (s.has("face") && s.has("facing")) {
            out = attempt(out, faceAttached(s), valid);
        } else {
            for (String key : new String[]{"facing", "vertical_direction"}) {
                if (s.has(key)) out = attempt(out, out.with(key, apply(s.get(key))), valid);
            }
        }
        if (s.has("axis")) {
            String a = s.get("axis");
            int i = switch (a) {
                case "x" -> 5;
                case "y" -> 1;
                case "z" -> 3;
                default -> -1;
            };
            if (i >= 0) {
                BlockPos v = apply(VECS[i][0], VECS[i][1], VECS[i][2]);
                out = attempt(out, out.with("axis", v.x() != 0 ? "x" : v.y() != 0 ? "y" : "z"), valid);
            }
        }
        if (s.has("orientation")) {
            // Jigsaw / crafter: "<front>_<top>".
            String[] parts = s.get("orientation").split("_");
            if (parts.length == 2 && index(parts[0]) >= 0 && index(parts[1]) >= 0) {
                out = attempt(out, out.with("orientation", apply(parts[0]) + "_" + apply(parts[1])), valid);
            }
        }
        boolean allSix = true;
        for (String d : DIRS) allSix &= s.has(d);
        if (allSix) {
            // Six-sided connection flags (mushroom blocks, glow lichen, sculk veins...).
            Map<String, String> props = new HashMap<>(out.properties());
            for (String d : DIRS) props.put(apply(d), s.get(d));
            out = attempt(out, out.withProperties(props), valid);
        }
        return out;
    }

    /** Buttons, levers, grindstones: turn the side they are attached to, then express it as face + facing. */
    private BlockState faceAttached(BlockState s) {
        String facing = s.get("facing");
        String attached = switch (s.get("face")) {
            case "floor" -> "down";
            case "ceiling" -> "up";
            default -> opposite(facing);
        };
        String turned = apply(attached);
        if (turned.equals("down") || turned.equals("up")) {
            String f = apply(facing);
            if (f.equals("up") || f.equals("down")) f = facing;
            return s.with("face", turned.equals("down") ? "floor" : "ceiling").with("facing", f);
        }
        return s.with("face", "wall").with("facing", opposite(turned));
    }

    private static BlockState attempt(BlockState current, BlockState candidate, Predicate<BlockState> valid) {
        return valid.test(candidate) ? candidate : current;
    }

    private static int index(String dir) {
        for (int i = 0; i < DIRS.length; i++) if (DIRS[i].equals(dir)) return i;
        return -1;
    }

    private static String name(int x, int y, int z) {
        for (int i = 0; i < VECS.length; i++) if (VECS[i][0] == x && VECS[i][1] == y && VECS[i][2] == z) return DIRS[i];
        throw new IllegalStateException();
    }

    private static String opposite(String dir) {
        int i = index(dir);
        return i < 0 ? dir : DIRS[i ^ 1];
    }
}
