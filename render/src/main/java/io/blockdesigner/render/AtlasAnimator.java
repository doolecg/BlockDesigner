package io.blockdesigner.render;

import io.blockdesigner.assets.TextureAtlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays the atlas's animated sprites (fire, lava, campfire flames, sea lanterns) as Minecraft does, 20 ticks a
 * second: for a tick it gives the atlas rectangles whose pixels changed, at every mip level, ready to upload over the
 * atlas texture. Each rectangle is the sprite with its border, widened to whole 16-pixel blocks so the smaller mip
 * levels come out exactly as {@code glGenerateMipmap} made them, neighbours included.
 */
final class AtlasAnimator {
    /** Mip levels 0..4, as the viewport's atlas texture has. */
    static final int LEVELS = 5;
    private static final int ALIGN = 1 << (LEVELS - 1);
    private static final int PAD = TextureAtlas.PAD;

    /** Pixels to write at {@code level}: {@code w × h} ARGB from {@code (x, y)}, top row first. */
    record Update(int level, int x, int y, int w, int h, int[] argb) {
    }

    private static final class Slot {
        final TextureAtlas.Animation anim;
        /** The aligned rectangle it rewrites, in level-0 atlas pixels. */
        final int x0, y0, w, h;
        long lastKey = Long.MIN_VALUE;

        Slot(TextureAtlas.Animation anim, int x0, int y0, int w, int h) {
            this.anim = anim;
            this.x0 = x0;
            this.y0 = y0;
            this.w = w;
            this.h = h;
        }
    }

    private final int atlasWidth;
    private final List<Slot> slots = new ArrayList<>();
    /** Level-0 pixels of every 16×16 block an animation touches, kept current (sprites can share a block). */
    private final Map<Long, int[]> blocks = new HashMap<>();

    AtlasAnimator(TextureAtlas atlas) {
        atlasWidth = atlas.width();
        int[] px = atlas.pixels();
        for (TextureAtlas.Animation a : atlas.animations()) {
            int x0 = Math.floorDiv(a.x() - PAD, ALIGN) * ALIGN, y0 = Math.floorDiv(a.y() - PAD, ALIGN) * ALIGN;
            int x1 = Math.min(atlas.width(), ceil(a.x() + a.width() + PAD)), y1 = Math.min(atlas.height(), ceil(a.y() + a.height() + PAD));
            x0 = Math.max(0, x0);
            y0 = Math.max(0, y0);
            slots.add(new Slot(a, x0, y0, x1 - x0, y1 - y0));
            for (int by = y0; by < y1; by += ALIGN) {
                for (int bx = x0; bx < x1; bx += ALIGN) {
                    int fbx = bx, fby = by;
                    blocks.computeIfAbsent(key(bx, by), k -> {
                        int[] b = new int[ALIGN * ALIGN];
                        for (int y = 0; y < ALIGN; y++) System.arraycopy(px, (fby + y) * atlasWidth + fbx, b, y * ALIGN, ALIGN);
                        return b;
                    });
                }
            }
        }
    }

    private static int ceil(int v) {
        return Math.floorDiv(v + ALIGN - 1, ALIGN) * ALIGN;
    }

    private static long key(int bx, int by) {
        return (long) bx << 32 | (by & 0xFFFFFFFFL);
    }

    boolean isEmpty() {
        return slots.isEmpty();
    }

    /** The rectangles to upload for {@code tick} (game ticks, 20 a second); empty when nothing changed. */
    List<Update> step(long tick) {
        List<Slot> changed = new ArrayList<>();
        for (Slot s : slots) {
            long k = s.anim.key(tick);
            if (k == s.lastKey) continue;
            s.lastKey = k;
            write(s.anim, s.anim.pixels(tick));
            changed.add(s);
        }
        // Read back after every write: a slot's rectangle may cover a neighbour that changed this tick too.
        List<Update> out = new ArrayList<>();
        for (Slot s : changed) {
            int[] level = read(s);
            int w = s.w, h = s.h;
            out.add(new Update(0, s.x0, s.y0, w, h, level));
            for (int l = 1; l < LEVELS; l++) {
                level = half(level, w, h);
                w >>= 1;
                h >>= 1;
                out.add(new Update(l, s.x0 >> l, s.y0 >> l, w, h, level));
            }
        }
        return out;
    }

    /** Writes a frame (and its replicated border) into the kept blocks. */
    private void write(TextureAtlas.Animation a, int[] frame) {
        int fw = a.width(), fh = a.height();
        for (int y = -PAD; y < fh + PAD; y++) {
            int sy = Math.clamp(y, 0, fh - 1);
            for (int x = -PAD; x < fw + PAD; x++) {
                int sx = Math.clamp(x, 0, fw - 1);
                int ax = a.x() + x, ay = a.y() + y;
                int[] b = blocks.get(key(Math.floorDiv(ax, ALIGN) * ALIGN, Math.floorDiv(ay, ALIGN) * ALIGN));
                if (b != null) b[Math.floorMod(ay, ALIGN) * ALIGN + Math.floorMod(ax, ALIGN)] = frame[sy * fw + sx];
            }
        }
    }

    private int[] read(Slot s) {
        int[] out = new int[s.w * s.h];
        for (int by = 0; by < s.h; by += ALIGN) {
            for (int bx = 0; bx < s.w; bx += ALIGN) {
                int[] b = blocks.get(key(s.x0 + bx, s.y0 + by));
                for (int y = 0; y < ALIGN; y++) System.arraycopy(b, y * ALIGN, out, (by + y) * s.w + bx, ALIGN);
            }
        }
        return out;
    }

    /** The next mip level down: each pixel the average of a 2×2 block (straight alpha, as glGenerateMipmap). */
    static int[] half(int[] src, int w, int h) {
        int hw = w >> 1, hh = h >> 1;
        int[] out = new int[hw * hh];
        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < hw; x++) {
                int i = 2 * y * w + 2 * x;
                int p0 = src[i], p1 = src[i + 1], p2 = src[i + w], p3 = src[i + w + 1];
                int a = avg(p0 >>> 24, p1 >>> 24, p2 >>> 24, p3 >>> 24);
                int r = avg(p0 >> 16 & 255, p1 >> 16 & 255, p2 >> 16 & 255, p3 >> 16 & 255);
                int g = avg(p0 >> 8 & 255, p1 >> 8 & 255, p2 >> 8 & 255, p3 >> 8 & 255);
                int b = avg(p0 & 255, p1 & 255, p2 & 255, p3 & 255);
                out[y * hw + x] = a << 24 | r << 16 | g << 8 | b;
            }
        }
        return out;
    }

    private static int avg(int a, int b, int c, int d) {
        return (a + b + c + d + 2) >> 2;
    }
}
