package io.blockdesigner.core.edit;

import java.util.Random;

/**
 * Smooth, low-frequency 3D noise for brushes: Perlin gradient noise, two octaves (the second half the size and a
 * quarter as strong), so the result rolls gently instead of speckling block by block, and has no grid-aligned repeats. Fixed in
 * world space per seed, so going over the same place again gives the same pattern.
 */
public final class SoftNoise {
    private final int[] perm = new int[512];

    public SoftNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1), t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static final SoftNoise[] cache = new SoftNoise[1];
    private static long cachedSeed;

    /** The generator for a seed (the last one is kept, as a stroke uses one seed for every cell). */
    public static synchronized SoftNoise of(long seed) {
        if (cache[0] == null || cachedSeed != seed) {
            cache[0] = new SoftNoise(seed);
            cachedSeed = seed;
        }
        return cache[0];
    }

    /** About -1..1, varying over roughly {@code scale} blocks. */
    public double at(double x, double y, double z, double scale) {
        double s = 1 / Math.max(0.5, scale), sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < 2; o++) {
            // Each octave is shifted so their lattices never line up.
            sum += amp * perlin(x * s + o * 17.31, y * s + o * 5.77, z * s + o * 11.13);
            norm += amp;
            amp *= 0.25;
            s *= 2;
        }
        return Math.clamp(sum / norm * 1.6, -1, 1);
    }

    private double perlin(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255, Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int a = perm[X] + Y, aa = perm[a] + Z, ab = perm[a + 1] + Z, b = perm[X + 1] + Y, ba = perm[b] + Z, bb = perm[b + 1] + Z;
        return lerp(w, lerp(v, lerp(u, grad(perm[aa], x, y, z), grad(perm[ba], x - 1, y, z)),
                        lerp(u, grad(perm[ab], x, y - 1, z), grad(perm[bb], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(perm[aa + 1], x, y, z - 1), grad(perm[ba + 1], x - 1, y, z - 1)),
                        lerp(u, grad(perm[ab + 1], x, y - 1, z - 1), grad(perm[bb + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y, v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
