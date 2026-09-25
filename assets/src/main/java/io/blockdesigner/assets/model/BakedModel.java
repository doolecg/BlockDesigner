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
 */
public record BakedModel(List<BakedQuad> quads, boolean[] opaqueFaces, boolean ambientOcclusion, boolean missing) {

    public static final BakedModel EMPTY = new BakedModel(List.of(), new boolean[6], false, false);

    public boolean isOpaque(Dir side) {
        return opaqueFaces[side.ordinal()];
    }

    /** True when every side is solid (a plain full cube like stone), used for AO and interior culling. */
    public boolean isFullCube() {
        for (boolean b : opaqueFaces) if (!b) return false;
        return true;
    }
}
