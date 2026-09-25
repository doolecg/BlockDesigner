package io.blockdesigner.app.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.assets.AssetStack;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.Dir;
import io.blockdesigner.assets.TextureAtlas;
import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.assets.model.RenderLayer;
import io.blockdesigner.core.model.BlockState;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inventory-style block icons, cached per asset set. Like Minecraft's GUI: blocks whose item model is a block model are
 * drawn as a small 3D render (so slabs look like slabs, stairs like stairs); items with a flat {@code item/generated}
 * model (flowers, torches, doors, panes...) show their flat item texture.
 */
public final class BlockIcons {
    /** Output size of 3D icons; rendered at twice this and averaged down for smooth edges. */
    static final int SIZE = 64, SS = 2;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<BlockAssets, Map<String, Image>> CACHE = new WeakHashMap<>();

    private BlockIcons() {
    }

    public static Image icon(BlockAssets assets, BlockState state) {
        Map<String, Image> cache;
        synchronized (CACHE) {
            cache = CACHE.computeIfAbsent(assets, a -> new ConcurrentHashMap<>());
        }
        return cache.computeIfAbsent(state.toString(), k -> render(assets, state));
    }

    private static Image render(BlockAssets assets, BlockState state) {
        BakedModel model = assets.model(state);
        Optional<String> flat = flatItemTexture(assets.assetStack(), state);
        if (flat.isPresent()) {
            Image img = flatIcon(assets, flat.get(), firstTint(model));
            if (img != null) return img;
        }
        if (model.quads().isEmpty()) return new WritableImage(1, 1);
        return isometric(assets.atlas(), model);
    }

    // ---- item model lookup ----------------------------------------------------------------------------------

    /**
     * The layer0 texture when the block's item uses a flat generated model, else empty (draw it in 3D). Reads the
     * 1.21.4+ item definition ({@code items/<id>.json}) or the older {@code models/item/<id>.json}.
     */
    static Optional<String> flatItemTexture(AssetStack stack, BlockState state) {
        String ns = state.namespace(), path = state.path();
        String modelId = null;
        Optional<JsonNode> def = readJson(stack, "assets/" + ns + "/items/" + path + ".json");
        if (def.isPresent()) {
            JsonNode m = def.get().path("model");
            if (m.path("type").asText("").endsWith("special")) return Optional.empty();  // chests, beds, heads: 3D
            modelId = firstModelId(m);
        }
        if (modelId == null) modelId = ns + ":item/" + path;

        String layer0 = null;
        for (int depth = 0; depth < 12 && modelId != null; depth++) {
            String id = normalize(modelId);
            String p = id.substring(id.indexOf(':') + 1);
            if (p.startsWith("block/")) return Optional.empty();
            if (p.equals("item/generated") || p.equals("builtin/generated") || p.equals("item/handheld")) {
                return Optional.ofNullable(layer0);
            }
            if (p.equals("builtin/entity")) return Optional.empty();
            Optional<JsonNode> node = readJson(stack, "assets/" + id.substring(0, id.indexOf(':')) + "/models/" + p + ".json");
            if (node.isEmpty()) return Optional.empty();
            if (layer0 == null && node.get().path("textures").has("layer0")) layer0 = node.get().path("textures").path("layer0").asText();
            modelId = node.get().has("parent") ? node.get().path("parent").asText() : null;
        }
        return Optional.empty();
    }

    /** First "model" id inside an item definition (select / condition / range_dispatch nest them). */
    private static String firstModelId(JsonNode n) {
        if (n == null || n.isMissingNode()) return null;
        if (n.isObject()) {
            JsonNode model = n.get("model");
            if (model != null && model.isTextual()) return model.asText();
            for (var it = n.elements(); it.hasNext(); ) {
                String s = firstModelId(it.next());
                if (s != null) return s;
            }
        } else if (n.isArray()) {
            for (JsonNode c : n) {
                String s = firstModelId(c);
                if (s != null) return s;
            }
        }
        return null;
    }

    private static Optional<JsonNode> readJson(AssetStack stack, String path) {
        try {
            return stack.read(path).map(b -> {
                try {
                    return JSON.readTree(b);
                } catch (Exception e) {
                    return null;
                }
            });
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String normalize(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static int firstTint(BakedModel m) {
        for (BakedQuad q : m.quads()) if (q.tint() != 0xFFFFFF) return q.tint();
        return 0xFFFFFF;
    }

    // ---- flat item icons ------------------------------------------------------------------------------------

    private static Image flatIcon(BlockAssets assets, String texture, int tint) {
        String id = normalize(texture);
        int[] px;
        int w, h;
        Optional<TextureAtlas.Sprite> sprite = assets.atlas().find(id);
        if (sprite.isPresent() && !sprite.get().missing()) {
            TextureAtlas atlas = assets.atlas();
            TextureAtlas.Sprite s = sprite.get();
            w = s.width();
            h = s.height();
            int x0 = Math.round(s.u0() * atlas.width()), y0 = Math.round(s.v0() * atlas.height());
            px = new int[w * h];
            for (int y = 0; y < h; y++) System.arraycopy(atlas.pixels(), (y0 + y) * atlas.width() + x0, px, y * w, w);
        } else {
            // Item textures are not in the block atlas: read the PNG (first frame of animated strips).
            String ns = id.substring(0, id.indexOf(':')), p = id.substring(id.indexOf(':') + 1);
            Optional<byte[]> bytes = assets.assetStack().read("assets/" + ns + "/textures/" + p + ".png");
            if (bytes.isEmpty()) return null;
            BufferedImage img;
            try {
                img = ImageIO.read(new ByteArrayInputStream(bytes.get()));
            } catch (Exception e) {
                return null;
            }
            if (img == null) return null;
            w = img.getWidth();
            h = Math.min(img.getHeight(), w);
            px = img.getRGB(0, 0, w, h, null, 0, w);
        }
        if (tint != 0xFFFFFF) for (int i = 0; i < px.length; i++) px[i] = multiply(px[i], tint, 1f);
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return out;
    }

    // ---- 3D (isometric) icons -------------------------------------------------------------------------------

    /**
     * Software-rasterises the baked model with Minecraft's GUI block transform (rotate 225° about Y, then 30° about X,
     * orthographic) and its directional face shading. The unit cube always fills the same space, so partial blocks
     * keep their true size and position (a bottom slab sits low, like in the inventory).
     */
    static Image isometric(TextureAtlas atlas, BakedModel model) {
        int[] out = isometricPixels(atlas, model);
        WritableImage img = new WritableImage(SIZE, SIZE);
        img.getPixelWriter().setPixels(0, 0, SIZE, SIZE, PixelFormat.getIntArgbInstance(), out, 0, SIZE);
        return img;
    }

    /** {@link #isometric} as {@value #SIZE}×{@value #SIZE} ARGB pixels. */
    static int[] isometricPixels(TextureAtlas atlas, BakedModel model) {
        int n = SIZE * SS;
        float[] depth = new float[n * n];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        int[] color = new int[n * n];

        // Fit the projected unit cube into the image.
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            float[] p = project(c & 1, (c >> 1) & 1, (c >> 2) & 1);
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        float scale = n * 0.94f / Math.max(maxX - minX, maxY - minY);
        float offX = n / 2f - (minX + maxX) / 2f * scale, offY = n / 2f - (minY + maxY) / 2f * scale;

        // Opaque and cutout first, translucent (blended, no depth writes) after.
        for (int pass = 0; pass < 2; pass++) {
            for (BakedQuad q : model.quads()) {
                boolean translucent = q.layer() == RenderLayer.TRANSLUCENT;
                if ((pass == 1) != translucent) continue;
                float[] sx = new float[4], sy = new float[4], sz = new float[4];
                for (int v = 0; v < 4; v++) {
                    float[] p = project(q.x(v), q.y(v), q.z(v));
                    sx[v] = p[0] * scale + offX;
                    sy[v] = p[1] * scale + offY;
                    sz[v] = p[2];
                }
                float shade = q.shade() ? shade(q.face()) : 1f;
                triangle(atlas, q, sx, sy, sz, 0, 1, 2, shade, translucent, n, depth, color);
                triangle(atlas, q, sx, sy, sz, 0, 2, 3, shade, translucent, n, depth, color);
            }
        }

        // Average SS×SS samples (premultiplied) into the output.
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < SS; j++) {
                    for (int i = 0; i < SS; i++) {
                        int c = color[(y * SS + j) * n + x * SS + i];
                        int ca = c >>> 24;
                        a += ca;
                        r += ((c >> 16) & 255) * ca;
                        g += ((c >> 8) & 255) * ca;
                        b += (c & 255) * ca;
                    }
                }
                if (a == 0) continue;
                out[y * SIZE + x] = (a / (SS * SS)) << 24 | (r / a) << 16 | (g / a) << 8 | (b / a);
            }
        }
        return out;
    }

    private static final float COS_Y = (float) Math.cos(Math.toRadians(225)), SIN_Y = (float) Math.sin(Math.toRadians(225));
    private static final float COS_X = (float) Math.cos(Math.toRadians(30)), SIN_X = (float) Math.sin(Math.toRadians(30));

    /** Block-space point (0..1) to {screen x, screen y (down), depth (larger is nearer)}. */
    private static float[] project(float x, float y, float z) {
        x -= 0.5f;
        y -= 0.5f;
        z -= 0.5f;
        float x1 = x * COS_Y + z * SIN_Y, z1 = -x * SIN_Y + z * COS_Y;
        float y2 = y * COS_X - z1 * SIN_X, z2 = y * SIN_X + z1 * COS_X;
        return new float[]{x1, -y2, z2};
    }

    /** Minecraft's directional block shading. */
    private static float shade(Dir d) {
        return switch (d) {
            case UP -> 1f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.6f;
        };
    }

    private static void triangle(TextureAtlas atlas, BakedQuad q, float[] sx, float[] sy, float[] sz, int a, int b, int c, float shade,
                                 boolean blend, int n, float[] depth, int[] color) {
        float area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sx[c] - sx[a]) * (sy[b] - sy[a]);
        if (Math.abs(area) < 1e-6f) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(sx[a], Math.min(sx[b], sx[c])))), x1 = Math.min(n - 1, (int) Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[c]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(sy[a], Math.min(sy[b], sy[c])))), y1 = Math.min(n - 1, (int) Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[c]))));
        float[] uv = q.uv();
        TextureAtlas.Sprite s = q.sprite();
        int aw = atlas.width(), ah = atlas.height();
        int sx0 = Math.round(s.u0() * aw), sx1 = Math.max(sx0 + 1, Math.round(s.u1() * aw));
        int sy0 = Math.round(s.v0() * ah), sy1 = Math.max(sy0 + 1, Math.round(s.v1() * ah));
        int[] tex = atlas.pixels();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((sx[b] - px) * (sy[c] - py) - (sx[c] - px) * (sy[b] - py)) / area;
                float w1 = ((sx[c] - px) * (sy[a] - py) - (sx[a] - px) * (sy[c] - py)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < -1e-5f || w1 < -1e-5f || w2 < -1e-5f) continue;
                float z = w0 * sz[a] + w1 * sz[b] + w2 * sz[c];
                int i = y * n + x;
                if (z < depth[i] - 1e-4f) continue;
                float u = w0 * uv[a * 2] + w1 * uv[b * 2] + w2 * uv[c * 2];
                float v = w0 * uv[a * 2 + 1] + w1 * uv[b * 2 + 1] + w2 * uv[c * 2 + 1];
                int tx = Math.clamp((int) Math.floor(u * aw), sx0, sx1 - 1), ty = Math.clamp((int) Math.floor(v * ah), sy0, sy1 - 1);
                int p = tex[ty * aw + tx];
                int alpha = p >>> 24;
                if (q.layer() == RenderLayer.CUTOUT && alpha < 128) continue;
                if (alpha == 0) continue;
                int shaded = multiply(p, q.tint(), shade);
                if (blend) {
                    color[i] = over(shaded, color[i]);
                } else {
                    color[i] = q.layer() == RenderLayer.SOLID ? shaded | 0xFF000000 : shaded;
                    depth[i] = z;
                }
            }
        }
    }

    /** Multiplies RGB by a tint colour and a brightness, keeping alpha. */
    private static int multiply(int argb, int tint, float k) {
        int r = (int) (((argb >> 16) & 255) * ((tint >> 16) & 255) / 255f * k);
        int g = (int) (((argb >> 8) & 255) * ((tint >> 8) & 255) / 255f * k);
        int b = (int) ((argb & 255) * (tint & 255) / 255f * k);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Straight-alpha "source over destination". */
    private static int over(int src, int dst) {
        float sa = (src >>> 24) / 255f, da = (dst >>> 24) / 255f;
        float oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        int r = (int) ((((src >> 16) & 255) * sa + ((dst >> 16) & 255) * da * (1 - sa)) / oa);
        int g = (int) ((((src >> 8) & 255) * sa + ((dst >> 8) & 255) * da * (1 - sa)) / oa);
        int b = (int) (((src & 255) * sa + (dst & 255) * da * (1 - sa)) / oa);
        return Math.round(oa * 255) << 24 | r << 16 | g << 8 | b;
    }
}
