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

    /**
     * Per-section lookup tables: each padded cell's baked model and whether it is a full opaque cube. Built once per
     * section so the culling and AO inner loops are plain array reads rather than hash lookups per face and vertex.
     */
    private static final class Cells {
        final BlockState[] states;
        final BakedModel[] models = new BakedModel[SectionSnapshot.PADDED * SectionSnapshot.PADDED * SectionSnapshot.PADDED];
        final boolean[] full = new boolean[models.length];

        Cells(SectionSnapshot snap, BlockAssets assets) {
            states = snap.states();
            java.util.IdentityHashMap<BlockState, BakedModel> seen = new java.util.IdentityHashMap<>();
            BlockState last = null;
            BakedModel lastModel = null;
            for (int i = 0; i < states.length; i++) {
                BlockState s = states[i];
                if (s.isAir()) continue;
                BakedModel m;
                if (s == last) {
                    m = lastModel;
                } else {
                    m = seen.get(s);
                    if (m == null) {
                        m = assets.model(s);
                        seen.put(s, m);
                    }
                    last = s;
                    lastModel = m;
                }
                models[i] = m;
                full[i] = m.isFullCube();
            }
        }
    }

    public MeshData mesh(SectionSnapshot snap) {
        MeshData out = new MeshData();
        if (snap.empty) return out;
        Cells cells = new Cells(snap, assets);
        int ox = snap.sx * SectionSnapshot.SIZE, oy = snap.sy * SectionSnapshot.SIZE, oz = snap.sz * SectionSnapshot.SIZE;
        for (int y = 0; y < SectionSnapshot.SIZE; y++) {
            for (int z = 0; z < SectionSnapshot.SIZE; z++) {
                for (int x = 0; x < SectionSnapshot.SIZE; x++) {
                    int c = SectionSnapshot.cell(x, y, z);
                    BakedModel model = cells.models[c];
                    if (model == null) continue;
                    // A full cube buried on all six sides can't show a single face.
                    if (cells.full[c] && buried(cells, c)) continue;
                    BlockState state = cells.states[c];
                    var extra = snap.data(x, y, z);
                    float open = snap.open(x, y, z);
                    if (extra != null || open > 0) model = assets.blockEntityModel(state, extra, open);
                    for (BakedQuad q : model.quads()) {
                        if (q.cull() != null && culled(cells, c, state, model, q)) continue;
                        emit(out.layers[q.layer().ordinal()], cells, model, q, x, y, z, ox, oy, oz);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Vertex data for entities (layer-local positions): their models, lit by the sun like blocks but without culling
     * or ambient occlusion. {@code yMin..yMax} is the slice view: entities standing outside it are left out.
     */
    public MeshData meshEntities(java.util.List<io.blockdesigner.core.model.StructureEntity> entities, int yMin, int yMax) {
        MeshData out = new MeshData();
        for (var e : entities) {
            if (e.y() < yMin || e.y() >= (long) yMax + 1) continue;
            float ex = (float) e.x(), ey = (float) e.y(), ez = (float) e.z();
            for (BakedQuad q : assets.entityQuads(e)) {
                MeshData.Builder b = out.layers[q.layer().ordinal()];
                float[] n = q.normal();
                int tr = (q.tint() >> 16) & 255, tg = (q.tint() >> 8) & 255, tb = q.tint() & 255;
                int rgba = (tr << 24) | (tg << 16) | (tb << 8) | 0xFF;
                for (int v = 0; v < 4; v++) {
                    b.vertex(ex + q.x(v), ey + q.y(v), ez + q.z(v), q.uv()[v * 2], q.uv()[v * 2 + 1], rgba, n[0], n[1], n[2]);
                }
                b.quads++;
            }
        }
        return out;
    }

    private static final int DX = 1, DY = SectionSnapshot.PADDED * SectionSnapshot.PADDED, DZ = SectionSnapshot.PADDED;

    private static int step(Dir d) {
        return d.dx * DX + d.dy * DY + d.dz * DZ;
    }

    private static boolean buried(Cells cells, int c) {
        boolean[] f = cells.full;
        return f[c + DX] && f[c - DX] && f[c + DY] && f[c - DY] && f[c + DZ] && f[c - DZ];
    }

    private boolean culled(Cells cells, int c, BlockState self, BakedModel selfModel, BakedQuad q) {
        Dir d = q.cull();
        int n = c + step(d);
        BakedModel nm = cells.models[n];
        if (nm == null) return false;
        if (nm.isOpaque(d.opposite())) return true;
        // Neighbouring identical see-through cubes (glass, ice, stained glass) hide the faces between them.
        return cells.states[n] == self && q.layer() != RenderLayer.SOLID && selfModel.quads().size() == 6;
    }

    private static boolean occludes(Cells cells, int x, int y, int z) {
        return cells.full[SectionSnapshot.cell(x, y, z)];
    }

    private void emit(MeshData.Builder b, Cells snap, BakedModel model, BakedQuad q, int x, int y, int z, int ox, int oy, int oz) {
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
    private float vertexAo(Cells snap, Dir face, int x, int y, int z, float px, float py, float pz) {
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
