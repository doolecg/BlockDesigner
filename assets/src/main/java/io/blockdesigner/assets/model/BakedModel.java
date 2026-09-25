package io.blockdesigner.assets.model;

import io.blockdesigner.assets.Dir;

import java.util.List;

/**
 * All quads for one block state.
 *
 * @param opaqueFaces per {@link Dir#ordinal()}: true when that side is completely covered by solid texels, so the
 *                    neighbouring block's touching face can be culled
 * @param ambientOcclusion model allows smooth AO
 * @param missing     no model could be resolved; the quads are a placeholder cube
 * @param boxes       the hitbox: one {minX, minY, minZ, maxX, maxY, maxZ} box (0..1 block space) per model element, so
 *                    picking and the outline follow the model (a slab's half, a torch's stick, a fence's post and arms)
 */
public record BakedModel(List<BakedQuad> quads, boolean[] opaqueFaces, boolean ambientOcclusion, boolean missing, List<float[]> boxes) {

    public static final BakedModel EMPTY = new BakedModel(List.of(), new boolean[6], false, false, List.of());

    /** A model whose hitbox is the bounds of all its quads. */
    public BakedModel(List<BakedQuad> quads, boolean[] opaqueFaces, boolean ambientOcclusion, boolean missing) {
        this(quads, opaqueFaces, ambientOcclusion, missing, quads.isEmpty() ? List.of() : List.of(bounds(quads)));
    }

    /** True when the hitbox is the whole cell (or unknown), so picking can treat it as a plain cube. */
    public boolean fullHitbox() {
        if (boxes.isEmpty()) return true;
        for (float[] b : boxes) if (b[0] <= 0.001f && b[1] <= 0.001f && b[2] <= 0.001f && b[3] >= 0.999f && b[4] >= 0.999f && b[5] >= 0.999f) return true;
        return false;
    }

    /** Bounds of some quads, at least 1/16 thick on every axis so flat models (rails, carpets) can still be hit. */
    public static float[] bounds(List<BakedQuad> quads) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BakedQuad q : quads) {
            for (int v = 0; v < 4; v++) {
                float[] p = {q.x(v), q.y(v), q.z(v)};
                for (int a = 0; a < 3; a++) {
                    b[a] = Math.min(b[a], p[a]);
                    b[a + 3] = Math.max(b[a + 3], p[a]);
                }
            }
        }
        float min = 1 / 16f;
        for (int a = 0; a < 3; a++) {
            b[a] = Math.max(0, b[a]);
            b[a + 3] = Math.min(1, b[a + 3]);
            if (b[a + 3] - b[a] < min) {
                float c = (b[a] + b[a + 3]) / 2;
                b[a] = Math.max(0, c - min / 2);
                b[a + 3] = Math.min(1, b[a] + min);
            }
        }
        return b;
    }

    public boolean isOpaque(Dir side) {
        return opaqueFaces[side.ordinal()];
    }

    /** True when every side is solid (a plain full cube like stone), used for AO and interior culling. */
    public boolean isFullCube() {
        for (boolean b : opaqueFaces) if (!b) return false;
        return true;
    }
}
