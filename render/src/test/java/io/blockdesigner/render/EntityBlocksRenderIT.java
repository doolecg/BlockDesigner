package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders chests, beds, signs and heads (the blocks the game draws with code) offscreen to PNGs, to check their models
 * by eye. Skipped without a Minecraft install. The images go to the folder in -Dblockdesigner.renderDir (or the temp dir).
 */
class EntityBlocksRenderIT {

    @Test
    void rendersChestsBedsSignsAndHeads() throws Exception {
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty());
        var jar = jars.stream().filter(j -> j.version().id().startsWith("1.21.1")).findFirst().orElse(jars.getFirst());
        BlockAssets assets = BlockAssets.open(jar.jar(), List.of(), List.of(), null);

        Structure s = new Structure();
        for (int x = -1; x <= 13; x++) for (int z = -1; z <= 9; z++) s.set(new BlockPos(x, 0, z), BlockState.of("stone"));
        put(s, 0, 1, 0, "chest[facing=south,type=single,waterlogged=false]");
        // Facing south, a "left" half has its partner to the west (Minecraft's ChestBlock.getConnectedDirection).
        put(s, 3, 1, 0, "chest[facing=south,type=left,waterlogged=false]");
        put(s, 2, 1, 0, "chest[facing=south,type=right,waterlogged=false]");
        put(s, 5, 1, 0, "ender_chest[facing=east,waterlogged=false]");
        // A bed's facing points from the foot to the head.
        put(s, 7, 1, 1, "red_bed[facing=south,occupied=false,part=head]");
        put(s, 7, 1, 0, "red_bed[facing=south,occupied=false,part=foot]");
        put(s, 9, 1, 0, "blue_bed[facing=east,occupied=false,part=foot]");
        put(s, 10, 1, 0, "blue_bed[facing=east,occupied=false,part=head]");
        put(s, 0, 1, 3, "oak_sign[rotation=0,waterlogged=false]");
        put(s, 2, 1, 3, "birch_sign[rotation=2,waterlogged=false]");
        s.set(new BlockPos(4, 1, 2), BlockState.of("oak_planks"));
        put(s, 4, 1, 3, "spruce_wall_sign[facing=south,waterlogged=false]");
        s.set(new BlockPos(6, 3, 3), BlockState.of("oak_planks"));
        put(s, 6, 2, 3, "cherry_hanging_sign[attached=false,rotation=0,waterlogged=false]");
        s.set(new BlockPos(8, 2, 2), BlockState.of("oak_planks"));
        put(s, 8, 2, 3, "oak_wall_hanging_sign[facing=south,waterlogged=false]");
        put(s, 0, 1, 6, "skeleton_skull[powered=false,rotation=0]");
        put(s, 2, 1, 6, "zombie_head[powered=false,rotation=0]");
        put(s, 4, 1, 6, "creeper_head[powered=false,rotation=2]");
        put(s, 6, 1, 6, "player_head[powered=false,rotation=0]");
        put(s, 8, 1, 6, "piglin_head[powered=false,rotation=0]");
        put(s, 11, 1, 6, "dragon_head[powered=false,rotation=0]");
        s.set(new BlockPos(12, 1, 3), BlockState.of("oak_planks"));
        put(s, 12, 1, 4, "wither_skeleton_wall_skull[facing=south,powered=false]");
        put(s, 0, 1, 8, "red_banner[rotation=0]");
        put(s, 2, 1, 8, "blue_banner[rotation=4]");
        s.set(new BlockPos(4, 2, 7), BlockState.of("oak_planks"));
        put(s, 4, 2, 8, "yellow_wall_banner[facing=south]");
        put(s, 6, 1, 8, "shulker_box[facing=up]");
        put(s, 8, 1, 8, "purple_shulker_box[facing=north]");
        put(s, 10, 1, 8, "lime_shulker_box[facing=east]");
        // Patterned banners: the 1.20.5+ form, and the older short codes.
        put(s, 12, 1, 8, "white_banner[rotation=0]");
        s.setBlockEntity(new BlockPos(12, 1, 8), new io.blockdesigner.core.nbt.CompoundTag().put("patterns", io.blockdesigner.core.nbt.ListTag.of(
                new io.blockdesigner.core.nbt.CompoundTag().putString("pattern", "minecraft:stripe_bottom").putString("color", "red"),
                new io.blockdesigner.core.nbt.CompoundTag().putString("pattern", "minecraft:creeper").putString("color", "black"),
                new io.blockdesigner.core.nbt.CompoundTag().putString("pattern", "minecraft:border").putString("color", "blue"))));
        put(s, 13, 1, 8, "yellow_banner[rotation=0]");
        s.setBlockEntity(new BlockPos(13, 1, 8), new io.blockdesigner.core.nbt.CompoundTag().put("Patterns", io.blockdesigner.core.nbt.ListTag.of(
                new io.blockdesigner.core.nbt.CompoundTag().putString("Pattern", "cr").putInt("Color", 11),
                new io.blockdesigner.core.nbt.CompoundTag().putString("Pattern", "mc").putInt("Color", 14))));

        Scene scene = new Scene();
        scene.add(new Layer("entities", s));
        Path dir = Path.of(System.getProperty("blockdesigner.renderDir", System.getProperty("java.io.tmpdir")));
        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            gpu.ready().get(20, TimeUnit.SECONDS);
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                sr.sync();
                while (sr.pending() > 0) Thread.sleep(10);
                shot(gpu, sr, dir.resolve("entity-blocks-front.png"), 25, 28, new float[]{-1, 0, -1, 14, 4, 8});
                shot(gpu, sr, dir.resolve("entity-blocks-back.png"), 200, 30, new float[]{-1, 0, -1, 14, 4, 8});
                shot(gpu, sr, dir.resolve("entity-blocks-signs.png"), 190, 15, new float[]{-1, 0, 2, 10, 4, 5});
                shot(gpu, sr, dir.resolve("entity-blocks-beds.png"), 150, 35, new float[]{5, 0, -1, 12, 3, 3});
                shot(gpu, sr, dir.resolve("entity-blocks-wallsign.png"), 200, 12, new float[]{3.5f, 1, 2.5f, 5.5f, 2.2f, 3.5f});
                shot(gpu, sr, dir.resolve("entity-blocks-banners.png"), 195, 18, new float[]{-1, 0, 7, 12, 3, 9});
                shot(gpu, sr, dir.resolve("entity-blocks-patterns.png"), 180, 8, new float[]{11.5f, 1, 7.5f, 14, 3, 9});
            }
        }
    }

    private static void put(Structure s, int x, int y, int z, String state) {
        s.set(new BlockPos(x, y, z), BlockState.parse("minecraft:" + state));
    }

    private static void shot(ViewportRenderer gpu, SceneRenderer sr, Path out, float yawDeg, float pitchDeg, float[] area) throws Exception {
        Camera cam = new Camera();
        // Yaw 0 looks north, so ~200 looks roughly south; the scene's fronts face south.
        cam.setAngles((float) Math.toRadians(yawDeg + 180), (float) Math.toRadians(pitchDeg));
        cam.frame(area[0], area[1], area[2], area[3], area[4], area[5]);
        cam.setDistance(cam.distance() * 0.75f);
        int w = 1400, h = 900;
        float[] vp = new float[16];
        cam.viewProjection(w / (float) h).get(vp);
        var eye = cam.eye();
        FrameRequest req = new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), List.of(),
                FrameRequest.Theme.LIGHT, false, 0, new float[]{6, 3}, 1, Float.POSITIVE_INFINITY);
        ViewportRenderer.Frame frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        frame.pixels().get(px);
        img.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(img, "png", out.toFile());
        System.out.println("Render written to " + out);
        assertThat(px[(h / 2) * w + w / 2]).isNotEqualTo(px[5]);
    }
}
