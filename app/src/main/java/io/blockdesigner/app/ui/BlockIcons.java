package io.blockdesigner.app.ui;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.Dir;
import io.blockdesigner.assets.TextureAtlas;
import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.core.model.BlockState;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Small block icons from the texture atlas (side texture for cubes, top for flat blocks), cached per asset set. */
public final class BlockIcons {
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
        BakedQuad best = null;
        for (Dir want : model.isFullCube() ? new Dir[]{Dir.SOUTH, Dir.UP} : new Dir[]{Dir.UP, Dir.SOUTH, Dir.NORTH}) {
            for (BakedQuad q : model.quads()) {
                if (q.face() == want) {
                    best = q;
                    break;
                }
            }
            if (best != null) break;
        }
        if (best == null && !model.quads().isEmpty()) best = model.quads().getFirst();
        if (best == null) return new WritableImage(1, 1);
        TextureAtlas atlas = assets.atlas();
        TextureAtlas.Sprite s = best.sprite();
        int w = s.width(), h = s.height();
        int x0 = Math.round(s.u0() * atlas.width()), y0 = Math.round(s.v0() * atlas.height());
        int[] px = new int[w * h];
        int tint = best.tint();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = atlas.pixels()[(y0 + y) * atlas.width() + x0 + x];
                if (tint != 0xFFFFFF) {
                    int r = ((p >> 16) & 255) * ((tint >> 16) & 255) / 255, g = ((p >> 8) & 255) * ((tint >> 8) & 255) / 255,
                            b = (p & 255) * (tint & 255) / 255;
                    p = (p & 0xFF000000) | r << 16 | g << 8 | b;
                }
                px[y * w + x] = p;
            }
        }
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return img;
    }
}
