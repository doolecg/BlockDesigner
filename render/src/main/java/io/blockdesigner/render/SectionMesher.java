package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.Dir;
import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.assets.model.RenderLayer;
import io.blockdesigner.core.model.BlockState;

/**
 * Builds vertex data for a {@link SectionSnapshot}: hidden-face culling against neighbours and vanilla-style smooth
 * ambient occlusion from full-cube neighbours.
 */
public final class SectionMesher {
    /** Brightness for 0..3 unoccluded neighbours around a vertex. */
    private static final float[] AO_LEVELS = {0.45f, 0.62f, 0.8f, 1.0f};

    private final BlockAssets assets;

    public SectionMesher(BlockAssets assets) {
        this.assets = assets;
    }

    public MeshData mesh(SectionSnapshot snap) {
        MeshData out = new MeshData();
        if (snap.empty) return out;
        int ox = snap.sx * SectionSnapshot.SIZE, oy = snap.sy * SectionSnapshot.SIZE, oz = snap.sz * SectionSnapshot.SIZE;
        for (int y = 0; y < SectionSnapshot.SIZE; y++) {
            for (int z = 0; z < SectionSnapshot.SIZE; z++) {
                for (int x = 0; x < SectionSnapshot.SIZE; x++) {
                    BlockState state = snap.get(x, y, z);
                    if (state.isAir()) continue;
                    BakedModel model = assets.model(state);
                    for (BakedQuad q : model.quads()) {
                        if (q.cull() != null && culled(snap, state, model, q, x, y, z)) continue;
                        emit(out.layers[q.layer().ordinal()], snap, model, q, x, y, z, ox, oy, oz);
                    }
                }
            }
        }
        return out;
    }

    private boolean culled(SectionSnapshot snap, BlockState self, BakedModel selfModel, BakedQuad q, int x, int y, int z) {
        Dir c = q.cull();
        BlockState n = snap.get(x + c.dx, y + c.dy, z + c.dz);
        if (n.isAir()) return false;
        BakedModel nm = assets.model(n);
        if (nm.isOpaque(c.opposite())) return true;
        // Neighbouring identical see-through cubes (glass, ice, stained glass) hide the faces between them.
        return n == self && q.layer() != RenderLayer.SOLID && selfModel.quads().size() == 6;
    }

    private boolean occludes(SectionSnapshot snap, int x, int y, int z) {
        BlockState s = snap.get(x, y, z);
        return !s.isAir() && assets.model(s).isFullCube();
    }

    private void emit(MeshData.Builder b, SectionSnapshot snap, BakedModel model, BakedQuad q, int x, int y, int z, int ox, int oy, int oz) {
        float[] n = q.normal();
        float shadeTint = 1f;
        int tr = (q.tint() >> 16) & 255, tg = (q.tint() >> 8) & 255, tb = q.tint() & 255;
        Dir face = q.face();
        boolean axisAligned = Math.abs(n[0] * face.dx + n[1] * face.dy + n[2] * face.dz) > 0.99f;
        for (int v = 0; v < 4; v++) {
            float px = q.x(v), py = q.y(v), pz = q.z(v);
            float ao = 1f;
            if (model.ambientOcclusion() && axisAligned) ao = vertexAo(snap, face, x, y, z, px, py, pz);
            float f = ao * shadeTint;
            int rgba = ((int) (tr * f) << 24) | ((int) (tg * f) << 16) | ((int) (tb * f) << 8) | 0xFF;
            b.vertex(ox + x + px, oy + y + py, oz + z + pz, q.uv()[v * 2], q.uv()[v * 2 + 1], rgba, n[0], n[1], n[2]);
        }
        b.quads++;
    }

    /**
     * Samples the three blocks touching a vertex on the outside of its face (two edges and the corner). Faces inset
     * from the block boundary sample the block's own layer instead of the neighbour's.
     */
    private float vertexAo(SectionSnapshot snap, Dir face, int x, int y, int z, float px, float py, float pz) {
        float along = switch (face) {
            case DOWN -> py;
            case UP -> 1 - py;
            case NORTH -> pz;
            case SOUTH -> 1 - pz;
            case WEST -> px;
            case EAST -> 1 - px;
        };
        int layerOff = along < 0.01f ? 1 : 0;
        int bx = x + face.dx * layerOff, by = y + face.dy * layerOff, bz = z + face.dz * layerOff;
        int s1x = 0, s1y = 0, s1z = 0, s2x = 0, s2y = 0, s2z = 0;
        switch (face) {
            case DOWN, UP -> {
                s1x = px < 0.5f ? -1 : 1;
                s2z = pz < 0.5f ? -1 : 1;
            }
            case NORTH, SOUTH -> {
                s1x = px < 0.5f ? -1 : 1;
                s2y = py < 0.5f ? -1 : 1;
            }
            default -> {
                s1z = pz < 0.5f ? -1 : 1;
                s2y = py < 0.5f ? -1 : 1;
            }
        }
        boolean side1 = occludes(snap, bx + s1x, by + s1y, bz + s1z);
        boolean side2 = occludes(snap, bx + s2x, by + s2y, bz + s2z);
        boolean corner = occludes(snap, bx + s1x + s2x, by + s1y + s2y, bz + s1z + s2z);
        int level = side1 && side2 ? 0 : 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0));
        return AO_LEVELS[level];
    }
}
