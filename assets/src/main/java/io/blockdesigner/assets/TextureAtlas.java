package io.blockdesigner.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.assets.model.RenderLayer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All block textures stitched into one ARGB image. Each sprite is surrounded by a replicated border so that
 * mipmapping and filtering don't bleed neighbouring textures into each other.
 */
public final class TextureAtlas {
    public static final String MISSING = "blockdesigner:missing";
    /** Plain white, for flat tinted shapes (placeholder boxes). */
    public static final String WHITE = "blockdesigner:white";
    /** Border replicated around each sprite; animation updates rewrite it with each frame. */
    public static final int PAD = 4;
    private static final int MAX_SIZE = 16384;

    /**
     * A placed texture. UVs are the inner (unpadded) rectangle in 0..1 atlas space. {@code animated}: its
     * {@code .mcmeta} makes it an animation (fire, lava, sea lanterns), whose frames {@link #animations()} holds.
     */
    public record Sprite(String id, float u0, float v0, float u1, float v1, int width, int height, RenderLayer layer, int averageRgb, boolean missing,
                         boolean animated) {
        /** Atlas U for a model UV in 0..16 texture pixels. */
        public float u(float px) {
            return u0 + (u1 - u0) * px / 16f;
        }

        public float v(float px) {
            return v0 + (v1 - v0) * px / 16f;
        }
    }

    private final int width, height;
    private final int[] argb;
    private final Map<String, Sprite> sprites;
    private final Sprite missing;
    private final List<Animation> animations;

    private TextureAtlas(int width, int height, int[] argb, Map<String, Sprite> sprites, List<Animation> animations) {
        this.width = width;
        this.height = height;
        this.argb = argb;
        this.sprites = Collections.unmodifiableMap(sprites);
        this.missing = sprites.get(MISSING);
        this.animations = List.copyOf(animations);
    }

    /**
     * An animated sprite, played the way Minecraft plays it: {@code sequence[i]} shows for {@code times[i]} game ticks
     * (20 a second), then the next, looping; with {@code interpolate} each frame fades into the next.
     *
     * @param x      left of the sprite's inner rectangle in atlas pixels
     * @param y      top of the sprite's inner rectangle in atlas pixels
     * @param frames each frame's ARGB pixels, {@code width x height}, row-major
     */
    public record Animation(String id, int x, int y, int width, int height, int[][] frames, int[] sequence, int[] times, boolean interpolate) {
        /** Length of one loop in ticks. */
        public int loopTicks() {
            int n = 0;
            for (int t : times) n += t;
            return Math.max(1, n);
        }

        /** Something that changes whenever the picture at {@code tick} does (for skipping unchanged uploads). */
        public long key(long tick) {
            if (interpolate) return Math.floorMod(tick, loopTicks());
            return sequence[step(tick)];
        }

        /** Index into {@link #sequence} shown at {@code tick}. */
        private int step(long tick) {
            long t = Math.floorMod(tick, loopTicks());
            for (int i = 0; i < times.length; i++) {
                if (t < times[i]) return i;
                t -= times[i];
            }
            return 0;
        }

        /** The picture at {@code tick} ({@code width x height} ARGB), blended between frames when interpolated. */
        public int[] pixels(long tick) {
            int i = step(tick);
            int[] cur = frames[sequence[i]];
            if (!interpolate || times[i] <= 1) return cur;
            long into = Math.floorMod(tick, loopTicks());
            for (int k = 0; k < i; k++) into -= times[k];
            float f = into / (float) times[i];
            if (f <= 0) return cur;
            int[] next = frames[sequence[(i + 1) % sequence.length]];
            int[] out = new int[cur.length];
            // As the game does: colour mixes toward the next frame, alpha stays the current frame's.
            for (int p = 0; p < out.length; p++) {
                int a = cur[p], b = next[p];
                int r = mix(a >> 16 & 255, b >> 16 & 255, f), g = mix(a >> 8 & 255, b >> 8 & 255, f), bl = mix(a & 255, b & 255, f);
                out[p] = (a & 0xFF000000) | r << 16 | g << 8 | bl;
            }
            return out;
        }

        private static int mix(int a, int b, float f) {
            return Math.round(a + (b - a) * f);
        }
    }

    /** The animated sprites (empty when there are none). */
    public List<Animation> animations() {
        return animations;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Row-major ARGB pixels, top row first. */
    public int[] pixels() {
        return argb;
    }

    public Map<String, Sprite> sprites() {
        return sprites;
    }

    /** Sprite for a texture id such as {@code minecraft:block/stone}; the missing-texture sprite if absent. */
    public Sprite sprite(String id) {
        Sprite s = sprites.get(id);
        return s == null ? missing : s;
    }

    public Optional<Sprite> find(String id) {
        return Optional.ofNullable(sprites.get(id));
    }

    /** Copies one sprite's pixels out as an image (for palette thumbnails). */
    public BufferedImage spriteImage(Sprite s) {
        int x0 = Math.round(s.u0 * width), y0 = Math.round(s.v0 * height);
        BufferedImage img = new BufferedImage(s.width, s.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s.height; y++) for (int x = 0; x < s.width; x++) img.setRGB(x, y, argb[(y0 + y) * width + x0 + x]);
        return img;
    }

    /** Texture id → file path, e.g. {@code create:block/shaft} → {@code assets/create/textures/block/shaft.png}. */
    static String texturePath(String id) {
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        return "assets/" + ns + "/textures/" + id.substring(c + 1) + ".png";
    }

    public static String normalizeId(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** A texture read for packing; {@code anim} holds its animation (frames and timing), or is null. */
    private record Loaded(String id, int w, int h, int[] px, RenderLayer layer, int avg, boolean missing, Anim anim) {
        Loaded(String id, int w, int h, int[] px, RenderLayer layer, int avg, boolean missing) {
            this(id, w, h, px, layer, avg, missing, null);
        }
    }

    private record Anim(int[][] frames, int[] sequence, int[] times, boolean interpolate) {
    }

    public static TextureAtlas build(AssetStack assets, Collection<String> textureIds) {
        ObjectMapper json = new ObjectMapper();
        List<Loaded> loaded = new ArrayList<>();
        loaded.add(missingTexture());
        int[] white = new int[16 * 16];
        java.util.Arrays.fill(white, 0xFFFFFFFF);
        loaded.add(new Loaded(WHITE, 16, 16, white, RenderLayer.SOLID, 0xFFFFFF, false));
        for (String rawId : new LinkedHashSet<>(textureIds)) {
            String id = normalizeId(rawId);
            if (id.equals(MISSING) || id.equals(WHITE)) continue;
            String path = texturePath(id);
            Optional<byte[]> bytes = assets.read(path);
            if (bytes.isEmpty()) continue;
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes.get()));
                if (img == null) continue;
                int w = img.getWidth();
                Optional<byte[]> mcmeta = assets.read(path + ".mcmeta");
                int h = frameHeight(img, mcmeta, json);
                int[] px = img.getRGB(0, 0, w, h, null, 0, w);
                Loaded l = analyse(id, w, h, px);
                Anim anim = animation(img, w, h, mcmeta, json);
                loaded.add(anim == null ? l : new Loaded(l.id, w, h, px, l.layer, l.avg, false, anim));
            } catch (IOException | RuntimeException e) {
                // corrupt texture: leave it to fall back to the missing sprite
            }
        }
        return pack(loaded);
    }

    /** Height of the first animation frame (square by default), or the whole height for a still texture. */
    private static int frameHeight(BufferedImage img, Optional<byte[]> mcmeta, ObjectMapper json) {
        int w = img.getWidth(), h = img.getHeight();
        if (mcmeta.isPresent()) {
            try {
                JsonNode anim = json.readTree(mcmeta.get()).path("animation");
                if (!anim.isMissingNode()) {
                    int fw = anim.path("width").asInt(0), fh = anim.path("height").asInt(0);
                    if (fh > 0) return Math.min(h, fh);
                    if (fw > 0) return Math.min(h, fw);
                    return Math.min(h, w);
                }
            } catch (IOException ignored) {
                // fall through
            }
        }
        // Without an .mcmeta a texture is a still picture, whatever its shape (64x128 witch and strider skins).
        return h;
    }

    /**
     * The animation a {@code .mcmeta} describes for a strip of {@code w x h} frames stacked downwards, or null when
     * the texture doesn't animate. Frames are indices or {@code {"index", "time"}}; the default time is
     * {@code frametime} (1 tick); without a list the frames play top to bottom.
     */
    private static Anim animation(BufferedImage img, int w, int h, Optional<byte[]> mcmeta, ObjectMapper json) {
        if (mcmeta.isEmpty() || h <= 0) return null;
        JsonNode anim;
        try {
            anim = json.readTree(mcmeta.get()).path("animation");
        } catch (IOException e) {
            return null;
        }
        if (anim.isMissingNode()) return null;
        int count = img.getHeight() / h;
        if (count < 2) return null;
        int[][] frames = new int[count][];
        for (int i = 0; i < count; i++) frames[i] = img.getRGB(0, i * h, w, h, null, 0, w);
        int frametime = Math.max(1, anim.path("frametime").asInt(1));
        List<int[]> seq = new ArrayList<>();
        if (anim.path("frames").isArray()) {
            for (JsonNode f : anim.path("frames")) {
                int index = f.isObject() ? f.path("index").asInt(-1) : f.asInt(-1);
                int time = f.isObject() ? Math.max(1, f.path("time").asInt(frametime)) : frametime;
                if (index >= 0 && index < count) seq.add(new int[]{index, time});
            }
        } else {
            for (int i = 0; i < count; i++) seq.add(new int[]{i, frametime});
        }
        if (seq.size() < 2) return null;
        int[] sequence = seq.stream().mapToInt(a -> a[0]).toArray(), times = seq.stream().mapToInt(a -> a[1]).toArray();
        return new Anim(frames, sequence, times, anim.path("interpolate").asBoolean(false));
    }

    private static Loaded analyse(String id, int w, int h, int[] px) {
        int partial = 0, clear = 0;
        long r = 0, g = 0, b = 0, n = 0;
        for (int p : px) {
            int a = p >>> 24;
            if (a == 0) clear++;
            else if (a < 255) partial++;
            if (a >= 128) {
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                b += p & 255;
                n++;
            }
        }
        RenderLayer layer = partial > px.length / 100 ? RenderLayer.TRANSLUCENT : (clear + partial) > 0 ? RenderLayer.CUTOUT : RenderLayer.SOLID;
        int avg = n == 0 ? 0 : (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        return new Loaded(id, w, h, px, layer, avg, false);
    }

    private static Loaded missingTexture() {
        int[] px = new int[16 * 16];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) px[y * 16 + x] = ((x < 8) ^ (y < 8)) ? 0xFFF800F8 : 0xFF101010;
        return new Loaded(MISSING, 16, 16, px, RenderLayer.SOLID, 0x840084, true);
    }

    private static TextureAtlas pack(List<Loaded> textures) {
        List<Loaded> order = new ArrayList<>(textures);
        order.sort(Comparator.comparingInt((Loaded t) -> t.h).reversed().thenComparing(Loaded::id));
        long area = 0;
        for (Loaded t : order) area += (long) (t.w + 2 * PAD) * (t.h + 2 * PAD);
        int size = 256;
        while ((long) size * size < area) size *= 2;
        while (size <= MAX_SIZE) {
            int[][] placement = tryPack(order, size, size);
            if (placement != null) return assemble(order, placement, size, size);
            size *= 2;
        }
        throw new IllegalStateException("Textures do not fit in a " + MAX_SIZE + "² atlas");
    }

    /** Shelf packing; returns x/y per texture or null if it doesn't fit. */
    private static int[][] tryPack(List<Loaded> order, int w, int h) {
        int[][] pos = new int[order.size()][];
        int x = 0, y = 0, shelf = 0;
        for (int i = 0; i < order.size(); i++) {
            Loaded t = order.get(i);
            int tw = t.w + 2 * PAD, th = t.h + 2 * PAD;
            if (x + tw > w) {
                x = 0;
                y += shelf;
                shelf = 0;
            }
            if (y + th > h || tw > w) return null;
            pos[i] = new int[]{x, y};
            x += tw;
            shelf = Math.max(shelf, th);
        }
        return pos;
    }

    private static TextureAtlas assemble(List<Loaded> order, int[][] pos, int w, int h) {
        int[] atlas = new int[w * h];
        Map<String, Sprite> sprites = new HashMap<>();
        List<Animation> animations = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Loaded t = order.get(i);
            int ox = pos[i][0] + PAD, oy = pos[i][1] + PAD;
            for (int y = -PAD; y < t.h + PAD; y++) {
                int sy = Math.clamp(y, 0, t.h - 1);
                for (int x = -PAD; x < t.w + PAD; x++) {
                    int sx = Math.clamp(x, 0, t.w - 1);
                    atlas[(oy + y) * w + ox + x] = t.px[sy * t.w + sx];
                }
            }
            sprites.put(t.id, new Sprite(t.id, ox / (float) w, oy / (float) h, (ox + t.w) / (float) w, (oy + t.h) / (float) h,
                    t.w, t.h, t.layer, t.avg, t.missing, t.anim != null));
            if (t.anim != null) {
                animations.add(new Animation(t.id, ox, oy, t.w, t.h, t.anim.frames(), t.anim.sequence(), t.anim.times(), t.anim.interpolate()));
            }
        }
        return new TextureAtlas(w, h, atlas, sprites, animations);
    }
}
