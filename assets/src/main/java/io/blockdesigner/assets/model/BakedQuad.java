package io.blockdesigner.assets.model;

import io.blockdesigner.assets.Dir;
import io.blockdesigner.assets.TextureAtlas;

/**
 * A textured quad in block space (0..1), ready for meshing.
 *
 * @param pos    4 vertices × xyz, counter-clockwise seen from the front
 * @param uv     4 vertices × uv, in atlas space (0..1)
 * @param normal unit face normal
 * @param face   nearest axis direction of the normal (for shading/AO)
 * @param cull   face is hidden when the neighbour in this direction is a full opaque face; null = never culled
 * @param tint   RGB multiplier (0xFFFFFF = none)
 * @param shade  apply directional shading
 * @param sprite the texture this quad samples
 */
public record BakedQuad(float[] pos, float[] uv, float[] normal, Dir face, Dir cull, int tint, RenderLayer layer, boolean shade, TextureAtlas.Sprite sprite) {

    public float x(int v) {
        return pos[v * 3];
    }

    public float y(int v) {
        return pos[v * 3 + 1];
    }

    public float z(int v) {
        return pos[v * 3 + 2];
    }
}
