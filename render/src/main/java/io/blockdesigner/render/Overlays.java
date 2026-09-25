package io.blockdesigner.render;

import java.util.List;

/** Helpers that build overlay line lists (box outlines, crosses). */
public final class Overlays {
    private Overlays() {
    }

    /** Wireframe of the box from (x0,y0,z0) to (x1,y1,z1) in world units. */
    public static void box(List<FrameRequest.Line> out, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : e) {
            float[] a = c[edge[0]], b = c[edge[1]];
            out.add(new FrameRequest.Line(a[0], a[1], a[2], b[0], b[1], b[2], argb));
        }
    }

    /** Outline of one block cell, slightly inflated so it doesn't z-fight with the block's faces. */
    public static void block(List<FrameRequest.Line> out, int x, int y, int z, int argb) {
        float e = 0.004f;
        box(out, x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e, argb);
    }

    /** RGB axis gizmo at a point: X red, Y green, Z blue. */
    public static void axes(List<FrameRequest.Line> out, float x, float y, float z, float len) {
        out.add(new FrameRequest.Line(x, y, z, x + len, y, z, 0xFFE5484D));
        out.add(new FrameRequest.Line(x, y, z, x, y + len, z, 0xFF46C46E));
        out.add(new FrameRequest.Line(x, y, z, x, y, z + len, 0xFF3E9BFF));
    }
}
