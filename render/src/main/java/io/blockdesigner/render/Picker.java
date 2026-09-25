package io.blockdesigner.render;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.function.Predicate;

/** CPU ray casting against layer voxels (no GPU readback), plus a ground-plane fallback. */
public final class Picker {
    private Picker() {
    }

    /**
     * @param layer   layer that was hit
     * @param local   hit block in layer-local coordinates
     * @param world   hit block in world coordinates
     * @param normal  world-space face normal of the hit side (unit axis vector)
     * @param distance distance along the ray
     */
    public record Hit(Layer layer, BlockPos local, BlockPos world, BlockPos normal, float distance) {
        /** The empty cell in front of the hit face, where a new block would go (world coordinates). */
        public BlockPos adjacentWorld() {
            return world.add(normal);
        }
    }

    public static Optional<Hit> pick(Scene scene, Vector3f origin, Vector3f dir, float maxDist, Predicate<Layer> filter) {
        return pick(scene, origin, dir, maxDist, filter, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** As above, ignoring blocks whose world Y is outside {@code minY..maxY} (the slice view hides them). */
    public static Optional<Hit> pick(Scene scene, Vector3f origin, Vector3f dir, float maxDist, Predicate<Layer> filter, int minY, int maxY) {
        Hit best = null;
        for (Layer l : scene.layers()) {
            if (!l.visible() || !filter.test(l)) continue;
            Matrix4f model = new Matrix4f().set(SceneRenderer.modelMatrix(l.offset(), l.transform()));
            Matrix4f inv = new Matrix4f(model).invert();
            Vector3f o = inv.transformPosition(new Vector3f(origin));
            Vector3f d = inv.transformDirection(new Vector3f(dir)).normalize();
            float limit = best == null ? maxDist : best.distance;
            long base = l.ghost() ? 0 : l.offset().y();
            int lo = l.ghost() ? Integer.MIN_VALUE : (int) Math.max(Integer.MIN_VALUE, minY - base);
            int hi = l.ghost() ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, maxY - base);
            Hit h = march(l, o, d, limit, model, lo, hi);
            if (h != null && (best == null || h.distance < best.distance)) best = h;
        }
        return Optional.ofNullable(best);
    }

    /** Amanatides–Woo voxel traversal in layer-local space. */
    private static Hit march(Layer l, Vector3f o, Vector3f d, float maxDist, Matrix4f model, int minY, int maxY) {
        var s = l.structure();
        var bounds = s.bounds();
        if (bounds.isEmpty()) return null;
        var b = bounds.get();
        // Clip the ray to the layer's bounding box first; big empty space is skipped instantly.
        float[] t = clip(o, d, b.minX(), b.minY(), b.minZ(), b.maxX() + 1f, b.maxY() + 1f, b.maxZ() + 1f);
        if (t == null || t[0] > maxDist) return null;
        float t0 = Math.max(0, t[0]) + 1e-4f;
        float px = o.x + d.x * t0, py = o.y + d.y * t0, pz = o.z + d.z * t0;
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int stepX = d.x > 0 ? 1 : -1, stepY = d.y > 0 ? 1 : -1, stepZ = d.z > 0 ? 1 : -1;
        float tdx = d.x == 0 ? Float.MAX_VALUE : Math.abs(1f / d.x);
        float tdy = d.y == 0 ? Float.MAX_VALUE : Math.abs(1f / d.y);
        float tdz = d.z == 0 ? Float.MAX_VALUE : Math.abs(1f / d.z);
        float tmx = d.x == 0 ? Float.MAX_VALUE : ((stepX > 0 ? x + 1 - px : px - x) * tdx) + t0;
        float tmy = d.y == 0 ? Float.MAX_VALUE : ((stepY > 0 ? y + 1 - py : py - y) * tdy) + t0;
        float tmz = d.z == 0 ? Float.MAX_VALUE : ((stepZ > 0 ? z + 1 - pz : pz - z) * tdz) + t0;
        int nx = 0, ny = 0, nz = 0;
        // Entry face normal from the box clip.
        if (t[0] >= 0) {
            int[] n = entryNormal(o, d, t[0], b.minX(), b.minY(), b.minZ(), b.maxX() + 1f, b.maxY() + 1f, b.maxZ() + 1f);
            nx = n[0];
            ny = n[1];
            nz = n[2];
        }
        float tcur = t0;
        float tEnd = Math.min(maxDist, t[1]);
        while (tcur <= tEnd) {
            if (y >= minY && y <= maxY && !s.get(x, y, z).isAir()) {
                BlockPos local = new BlockPos(x, y, z);
                Vector3f wn = model.transformDirection(new Vector3f(nx, ny, nz));
                return new Hit(l, local, l.toWorld(local), new BlockPos(Math.round(wn.x), Math.round(wn.y), Math.round(wn.z)), tcur);
            }
            if (tmx < tmy && tmx < tmz) {
                x += stepX;
                tcur = tmx;
                tmx += tdx;
                nx = -stepX;
                ny = 0;
                nz = 0;
            } else if (tmy < tmz) {
                y += stepY;
                tcur = tmy;
                tmy += tdy;
                nx = 0;
                ny = -stepY;
                nz = 0;
            } else {
                z += stepZ;
                tcur = tmz;
                tmz += tdz;
                nx = 0;
                ny = 0;
                nz = -stepZ;
            }
        }
        return null;
    }

    /** Slab test; returns {tNear, tFar} or null when the ray misses. */
    static float[] clip(Vector3f o, Vector3f d, float x0, float y0, float z0, float x1, float y1, float z1) {
        float tmin = -Float.MAX_VALUE, tmax = Float.MAX_VALUE;
        float[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z}, mins = {x0, y0, z0}, maxs = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(ds[i]) < 1e-9f) {
                if (os[i] < mins[i] || os[i] > maxs[i]) return null;
                continue;
            }
            float a = (mins[i] - os[i]) / ds[i], b = (maxs[i] - os[i]) / ds[i];
            tmin = Math.max(tmin, Math.min(a, b));
            tmax = Math.min(tmax, Math.max(a, b));
        }
        return tmax >= Math.max(tmin, 0) ? new float[]{tmin, tmax} : null;
    }

    private static int[] entryNormal(Vector3f o, Vector3f d, float t, float x0, float y0, float z0, float x1, float y1, float z1) {
        float px = o.x + d.x * t, py = o.y + d.y * t, pz = o.z + d.z * t;
        float eps = 1e-3f;
        if (Math.abs(px - x0) < eps) return new int[]{-1, 0, 0};
        if (Math.abs(px - x1) < eps) return new int[]{1, 0, 0};
        if (Math.abs(py - y0) < eps) return new int[]{0, -1, 0};
        if (Math.abs(py - y1) < eps) return new int[]{0, 1, 0};
        if (Math.abs(pz - z0) < eps) return new int[]{0, 0, -1};
        return new int[]{0, 0, 1};
    }

    /** Intersection of the ray with the horizontal plane {@code y = planeY}, as the block cell just above it. */
    public static Optional<BlockPos> pickGround(Vector3f o, Vector3f d, float planeY) {
        if (Math.abs(d.y) < 1e-6f) return Optional.empty();
        float t = (planeY - o.y) / d.y;
        if (t < 0 || t > 5000) return Optional.empty();
        return Optional.of(new BlockPos((int) Math.floor(o.x + d.x * t), (int) Math.floor(planeY), (int) Math.floor(o.z + d.z * t)));
    }
}
