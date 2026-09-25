package io.blockdesigner.assets;

import java.util.Locale;

/** The six axis directions, in Minecraft's order. */
public enum Dir {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

    private static final Dir[] VALUES = values();

    public final int dx, dy, dz;

    Dir(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public Dir opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public static Dir byName(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "down", "bottom" -> DOWN;
            case "up", "top" -> UP;
            case "north" -> NORTH;
            case "south" -> SOUTH;
            case "west" -> WEST;
            case "east" -> EAST;
            default -> null;
        };
    }

    /** The direction whose normal is closest to the given vector. */
    public static Dir nearest(float x, float y, float z) {
        Dir best = UP;
        float bestDot = -Float.MAX_VALUE;
        for (Dir d : VALUES) {
            float dot = d.dx * x + d.dy * y + d.dz * z;
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }
        return best;
    }

    public static Dir[] all() {
        return VALUES;
    }
}
