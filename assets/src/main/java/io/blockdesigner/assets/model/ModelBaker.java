package io.blockdesigner.assets.model;

import io.blockdesigner.assets.BlockTints;
import io.blockdesigner.assets.Dir;
import io.blockdesigner.assets.TextureAtlas;
import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns resolved JSON models into {@link BakedQuad}s: element geometry, per-face UVs and UV rotation, element
 * rotation (with rescale), whole-model x/y rotation, uvlock, tinting and render-layer selection.
 */
public final class ModelBaker {
    private final TextureAtlas atlas;
    private final BlockTints tints;

    public TextureAtlas atlas() {
        return atlas;
    }

    public ModelBaker(TextureAtlas atlas, BlockTints tints) {
        this.atlas = atlas;
        this.tints = tints;
    }

    /** One element-to-bake: a model with the rotation it is applied with. */
    public record Placed(ModelLoader.ResolvedModel model, int x, int y, boolean uvlock) {
    }

    public BakedModel bake(BlockState state, List<Placed> parts) {
        List<BakedQuad> quads = new ArrayList<>();
        List<float[]> boxes = new ArrayList<>();
        boolean ao = true;
        for (Placed p : parts) {
            ao &= p.model.ambientOcclusion();
            for (ModelLoader.Element e : p.model.elements()) {
                List<BakedQuad> element = new ArrayList<>();
                for (Map.Entry<Dir, ModelLoader.Face> f : e.faces().entrySet()) {
                    String tex = p.model.resolve(f.getValue().texture());
                    TextureAtlas.Sprite sprite = atlas.sprite(tex == null ? TextureAtlas.MISSING : tex);
                    element.add(bakeFace(state, e, f.getKey(), f.getValue(), sprite, p.x, p.y, p.uvlock));
                }
                quads.addAll(element);
                // Each element's (rotated) bounds is one hitbox, as Minecraft's shapes are unions of boxes.
                if (!element.isEmpty()) boxes.add(BakedModel.bounds(element));
            }
        }
        return new BakedModel(List.copyOf(quads), opaqueFaces(quads), ao, false, List.copyOf(boxes));
    }

    /** Simple textured box in 0..16 space, used for fallback models (chests, signs, fluids...). */
    public record BoxSpec(float[] from, float[] to, String texture, int tintIndex, RenderLayer forceLayer) {
    }

    public BakedModel bakeBoxes(BlockState state, List<BoxSpec> boxes, int yRotation, boolean missing) {
        List<BakedQuad> quads = new ArrayList<>();
        for (BoxSpec b : boxes) {
            TextureAtlas.Sprite sprite = atlas.sprite(b.texture == null ? TextureAtlas.MISSING : b.texture);
            ModelLoader.Element e = new ModelLoader.Element(b.from, b.to, null, true, Map.of());
            for (Dir d : Dir.all()) {
                ModelLoader.Face face = new ModelLoader.Face(null, "", null, 0, b.tintIndex);
                BakedQuad q = bakeFace(state, e, d, face, sprite, 0, yRotation, false);
                if (b.forceLayer != null) q = new BakedQuad(q.pos(), q.uv(), q.normal(), q.face(), q.cull(), q.tint(), b.forceLayer, q.shade(), q.sprite());
                quads.add(q);
            }
        }
        // Fallback boxes never cull neighbours: they approximate shapes the game draws with special renderers.
        return new BakedModel(List.copyOf(quads), new boolean[6], true, missing);
    }

    private BakedQuad bakeFace(BlockState state, ModelLoader.Element e, Dir dir, ModelLoader.Face face, TextureAtlas.Sprite sprite,
                               int rotX, int rotY, boolean uvlock) {
        float[] f = e.from(), t = e.to();
        float[] pos = facePositions(dir, f, t);
        float[] uvRect = face.uv() != null ? face.uv() : defaultUv(dir, f, t);

        // Corner UVs: vertex i -> corner (i + rotation/90) % 4 of (u0,v0),(u0,v1),(u1,v1),(u1,v0).
        float[][] corners = {{uvRect[0], uvRect[1]}, {uvRect[0], uvRect[3]}, {uvRect[2], uvRect[3]}, {uvRect[2], uvRect[1]}};
        int shift = face.rotation() / 90;
        float[] uv16 = new float[8];
        for (int i = 0; i < 4; i++) {
            float[] c = corners[(i + shift) & 3];
            uv16[i * 2] = c[0];
            uv16[i * 2 + 1] = c[1];
        }

        if (e.rotation() != null) applyElementRotation(pos, e.rotation());

        float[] normal = computeNormal(pos, dir);
        Dir cull = face.cull();
        if (rotX != 0 || rotY != 0) {
            for (int i = 0; i < 4; i++) rotateModel(pos, i * 3, rotX, rotY);
            float[] n = normal.clone();
            rotateVec(n, rotX, rotY);
            normal = n;
            if (cull != null) {
                float[] c = {cull.dx, cull.dy, cull.dz};
                rotateVec(c, rotX, rotY);
                cull = Dir.nearest(c[0], c[1], c[2]);
            }
        }
        Dir faceDir = Dir.nearest(normal[0], normal[1], normal[2]);
        if (uvlock && (rotX != 0 || rotY != 0)) lockUv(pos, faceDir, uv16);

        float[] uv = new float[8];
        for (int i = 0; i < 4; i++) {
            uv[i * 2] = sprite.u(uv16[i * 2]);
            uv[i * 2 + 1] = sprite.v(uv16[i * 2 + 1]);
        }
        float[] pos01 = new float[12];
        for (int i = 0; i < 12; i++) pos01[i] = pos[i] / 16f;
        int tint = tints.tint(state, face.tintIndex());
        return new BakedQuad(pos01, uv, normal, faceDir, cull, tint, sprite.layer(), e.shade(), sprite);
    }

    /** Vertex positions (0..16) in Minecraft's per-face order. */
    static float[] facePositions(Dir d, float[] f, float[] t) {
        float x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
        return switch (d) {
            case DOWN -> new float[]{x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1};
            case UP -> new float[]{x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0};
            case NORTH -> new float[]{x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0};
            case SOUTH -> new float[]{x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1};
            case WEST -> new float[]{x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1};
            case EAST -> new float[]{x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0};
        };
    }

    /** UV rectangle derived from element bounds when a face doesn't specify one. */
    static float[] defaultUv(Dir d, float[] f, float[] t) {
        return switch (d) {
            case DOWN -> new float[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case UP -> new float[]{f[0], f[2], t[0], t[2]};
            case NORTH -> new float[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case SOUTH -> new float[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case WEST -> new float[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            case EAST -> new float[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    /** World-aligned UVs for uvlock: project each vertex onto the face plane. */
    private static void lockUv(float[] pos, Dir d, float[] uv16) {
        for (int i = 0; i < 4; i++) {
            float x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
            float u, v;
            switch (d) {
                case DOWN -> {
                    u = x;
                    v = 16 - z;
                }
                case UP -> {
                    u = x;
                    v = z;
                }
                case NORTH -> {
                    u = 16 - x;
                    v = 16 - y;
                }
                case SOUTH -> {
                    u = x;
                    v = 16 - y;
                }
                case WEST -> {
                    u = z;
                    v = 16 - y;
                }
                default -> {
                    u = 16 - z;
                    v = 16 - y;
                }
            }
            uv16[i * 2] = u;
            uv16[i * 2 + 1] = v;
        }
    }

    private static void applyElementRotation(float[] pos, ModelLoader.ElementRotation r) {
        double a = Math.toRadians(r.angle());
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        float scale = r.rescale() && Math.abs(cos) > 1e-4 ? 1f / cos : 1f;
        float ox = r.origin()[0], oy = r.origin()[1], oz = r.origin()[2];
        for (int i = 0; i < 12; i += 3) {
            float x = pos[i] - ox, y = pos[i + 1] - oy, z = pos[i + 2] - oz;
            float nx = x, ny = y, nz = z;
            switch (r.axis()) {
                case 'x' -> {
                    ny = (y * cos - z * sin) * scale;
                    nz = (y * sin + z * cos) * scale;
                }
                case 'y' -> {
                    nx = (x * cos + z * sin) * scale;
                    nz = (-x * sin + z * cos) * scale;
                }
                default -> {
                    nx = (x * cos - y * sin) * scale;
                    ny = (x * sin + y * cos) * scale;
                }
            }
            pos[i] = nx + ox;
            pos[i + 1] = ny + oy;
            pos[i + 2] = nz + oz;
        }
    }

    /** Whole-model rotation about the block centre: X first, then Y (both clockwise in Minecraft's convention). */
    private static void rotateModel(float[] p, int i, int rotX, int rotY) {
        float[] v = {p[i] - 8, p[i + 1] - 8, p[i + 2] - 8};
        rotateVec(v, rotX, rotY);
        p[i] = v[0] + 8;
        p[i + 1] = v[1] + 8;
        p[i + 2] = v[2] + 8;
    }

    private static void rotateVec(float[] v, int rotX, int rotY) {
        for (int k = 0; k < Math.floorMod(rotX, 360) / 90; k++) {
            // x: up -> north, i.e. (y, z) -> (z... ) : a -90° right-handed turn about +X.
            float y = v[1], z = v[2];
            v[1] = z;
            v[2] = -y;
        }
        for (int k = 0; k < Math.floorMod(rotY, 360) / 90; k++) {
            // y: north -> east, a -90° right-handed turn about +Y.
            float x = v[0], z = v[2];
            v[0] = -z;
            v[2] = x;
        }
    }

    private static float[] computeNormal(float[] p, Dir fallback) {
        float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        float bx = p[9] - p[0], by = p[10] - p[1], bz = p[11] - p[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return new float[]{fallback.dx, fallback.dy, fallback.dz};
        return new float[]{nx / len, ny / len, nz / len};
    }

    /** A side is opaque when solid quads lying on it (possibly several, like a stair's back) cover the whole face. */
    private static boolean[] opaqueFaces(List<BakedQuad> quads) {
        boolean[][] covered = new boolean[6][256];
        for (BakedQuad q : quads) {
            if (q.cull() == null || q.layer() != RenderLayer.SOLID || q.face() != q.cull()) continue;
            Dir d = q.cull();
            float min0 = 1, min1 = 1, max0 = 0, max1 = 0, plane = 0;
            for (int v = 0; v < 4; v++) {
                float a, b, c;
                switch (d) {
                    case DOWN, UP -> {
                        a = q.x(v);
                        b = q.z(v);
                        c = q.y(v);
                    }
                    case NORTH, SOUTH -> {
                        a = q.x(v);
                        b = q.y(v);
                        c = q.z(v);
                    }
                    default -> {
                        a = q.z(v);
                        b = q.y(v);
                        c = q.x(v);
                    }
                }
                min0 = Math.min(min0, a);
                max0 = Math.max(max0, a);
                min1 = Math.min(min1, b);
                max1 = Math.max(max1, b);
                plane += c / 4f;
            }
            boolean onBoundary = (d == Dir.DOWN || d == Dir.NORTH || d == Dir.WEST) ? plane < 1e-4f : plane > 1 - 1e-4f;
            if (!onBoundary) continue;
            int a0 = Math.round(min0 * 16), a1 = Math.round(max0 * 16), b0 = Math.round(min1 * 16), b1 = Math.round(max1 * 16);
            boolean[] grid = covered[d.ordinal()];
            for (int b = Math.max(0, b0); b < Math.min(16, b1); b++)
                for (int a = Math.max(0, a0); a < Math.min(16, a1); a++) grid[b * 16 + a] = true;
        }
        boolean[] out = new boolean[6];
        for (int d = 0; d < 6; d++) {
            boolean all = true;
            for (boolean c : covered[d]) all &= c;
            out[d] = all;
        }
        return out;
    }
}
