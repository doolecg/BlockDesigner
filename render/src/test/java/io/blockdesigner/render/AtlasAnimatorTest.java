package io.blockdesigner.render;

import io.blockdesigner.assets.AssetSource;
import io.blockdesigner.assets.AssetStack;
import io.blockdesigner.assets.TextureAtlas;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasAnimatorTest {
    private static final int RED = 0xFFFF0000, GREEN = 0xFF00FF00, BLUE = 0xFF0000FF;

    /** A texture strip of solid 16×16 frames, top to bottom. */
    private static byte[] strip(int... colours) throws IOException {
        BufferedImage img = new BufferedImage(16, 16 * colours.length, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < colours.length; f++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, f * 16 + y, colours[f]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static TextureAtlas atlas(Map<String, byte[]> files) {
        AssetSource src = new AssetSource() {
            public String label() {
                return "test";
            }

            public Optional<byte[]> read(String path) {
                return Optional.ofNullable(files.get(path));
            }

            public List<String> list(String prefix) {
                return files.keySet().stream().filter(p -> p.startsWith(prefix)).toList();
            }

            public void close() {
            }
        };
        List<String> ids = files.keySet().stream().filter(p -> p.endsWith(".png"))
                .map(p -> "minecraft:block/" + p.substring(p.lastIndexOf('/') + 1, p.length() - 4)).toList();
        return TextureAtlas.build(new AssetStack(List.of(src)), ids);
    }

    private static Map<String, byte[]> files(Object... pathsAndContents) {
        Map<String, byte[]> m = new HashMap<>();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Object c = pathsAndContents[i + 1];
            m.put("assets/minecraft/textures/block/" + pathsAndContents[i], c instanceof String s ? s.getBytes(StandardCharsets.UTF_8) : (byte[]) c);
        }
        return m;
    }

    /** The colour an update puts at the middle of a sprite, or null when the update doesn't cover it. */
    private static Integer centre(List<AtlasAnimator.Update> updates, TextureAtlas atlas, TextureAtlas.Sprite s) {
        int cx = Math.round(s.u0() * atlas.width()) + 8, cy = Math.round(s.v0() * atlas.height()) + 8;
        for (AtlasAnimator.Update u : updates) {
            if (u.level() != 0 || cx < u.x() || cy < u.y() || cx >= u.x() + u.w() || cy >= u.y() + u.h()) continue;
            return u.argb()[(cy - u.y()) * u.w() + cx - u.x()];
        }
        return null;
    }

    @Test
    void playsFramesAtTheirFrameTimeAndLoops() throws IOException {
        TextureAtlas atlas = atlas(files("fire.png", strip(RED, GREEN, BLUE), "fire.png.mcmeta", "{\"animation\":{\"frametime\":2}}",
                "stone.png", strip(0xFF808080)));
        assertThat(atlas.sprite("minecraft:block/fire").animated()).isTrue();
        assertThat(atlas.sprite("minecraft:block/stone").animated()).isFalse();
        assertThat(atlas.animations()).hasSize(1);
        TextureAtlas.Sprite fire = atlas.sprite("minecraft:block/fire");

        AtlasAnimator anim = new AtlasAnimator(atlas);
        List<AtlasAnimator.Update> first = anim.step(0);
        assertThat(centre(first, atlas, fire)).isEqualTo(RED);
        // One update per mip level, each half the size of the one above.
        assertThat(first).extracting(AtlasAnimator.Update::level).containsExactly(0, 1, 2, 3, 4);
        AtlasAnimator.Update l0 = first.get(0), l4 = first.get(4);
        assertThat(l0.x() % 16).isZero();
        assertThat(l4.w()).isEqualTo(l0.w() >> 4);
        assertThat(l4.argb()).hasSize(l4.w() * l4.h());

        assertThat(anim.step(1)).as("same frame: nothing to upload").isEmpty();
        assertThat(centre(anim.step(2), atlas, fire)).isEqualTo(GREEN);
        assertThat(centre(anim.step(4), atlas, fire)).isEqualTo(BLUE);
        assertThat(centre(anim.step(6), atlas, fire)).isEqualTo(RED);
    }

    @Test
    void followsTheFrameListWithPerFrameTimes() throws IOException {
        TextureAtlas atlas = atlas(files("lava.png", strip(RED, GREEN, BLUE),
                "lava.png.mcmeta", "{\"animation\":{\"frames\":[2,{\"index\":0,\"time\":3}]}}"));
        TextureAtlas.Animation a = atlas.animations().getFirst();
        assertThat(a.loopTicks()).isEqualTo(4);
        assertThat(a.pixels(0)[0]).isEqualTo(BLUE);
        assertThat(a.pixels(1)[0]).isEqualTo(RED);
        assertThat(a.pixels(3)[0]).isEqualTo(RED);
        assertThat(a.pixels(4)[0]).isEqualTo(BLUE);
    }

    @Test
    void interpolatedFramesFadeIntoTheNext() throws IOException {
        TextureAtlas atlas = atlas(files("magma.png", strip(RED, BLUE), "magma.png.mcmeta", "{\"animation\":{\"frametime\":4,\"interpolate\":true}}"));
        TextureAtlas.Animation a = atlas.animations().getFirst();
        assertThat(a.pixels(0)[0]).isEqualTo(RED);
        int half = a.pixels(2)[0];
        assertThat(half >> 16 & 255).isBetween(126, 129);
        assertThat(half & 255).isBetween(126, 129);
        assertThat(half >>> 24).isEqualTo(255);
        // Every tick is a new picture.
        assertThat(a.key(1)).isNotEqualTo(a.key(2));
    }

    @Test
    void stillTexturesDoNotAnimate() throws IOException {
        TextureAtlas atlas = atlas(files("stone.png", strip(0xFF808080), "tall.png", strip(RED, GREEN)));
        assertThat(atlas.animations()).isEmpty();
        assertThat(new AtlasAnimator(atlas).isEmpty()).isTrue();
    }
}
