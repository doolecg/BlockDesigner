package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.EntityTypes;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the mob models, paintings, frames, decorated pots and open chests offscreen to PNGs, to check them by eye
 * against the game. Skipped without a Minecraft install; images go to -Dblockdesigner.renderDir (or the temp dir).
 */
class MobsRenderIT {

    @Test
    void rendersMobs() throws Exception {
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty());
        String want = System.getProperty("blockdesigner.mcVersion", "1.21.1");
        var jar = jars.stream().filter(j -> j.version().id().startsWith(want)).findFirst().orElse(jars.getFirst());
        BlockAssets assets = BlockAssets.open(jar.jar(), List.of(), List.of(), null);

        Structure s = new Structure();
        for (int x = -1; x <= 30; x++) for (int z = -2; z <= 10; z++) s.set(new BlockPos(x, 0, z), BlockState.of("grass_block").with("snowy", "false"));
        String[] row1 = {"pig", "cow", "mooshroom", "sheep", "chicken", "wolf", "villager", "wandering_trader", "iron_golem"};
        String[] row2 = {"zombie", "husk", "drowned", "skeleton", "stray", "wither_skeleton", "creeper", "armor_stand", "horse", "cat"};
        int x = 0;
        for (String id : row1) {
            // Facing north (towards the camera), so their faces show.
            s.addEntity(EntityTypes.create(id, x + 0.5, 1, 0.5, 180));
            x += id.equals("iron_golem") ? 3 : 2;
        }
        x = 0;
        for (String id : row2) {
            StructureEntity e = EntityTypes.create(id, x + 0.5, 1, 4.5, 180);
            if (id.equals("armor_stand")) e.nbt().putBoolean("ShowArms", true);
            s.addEntity(e);
            x += 2;
        }
        // A pink sheep, a baby pig and a farmer from the desert, turned to show their sides.
        StructureEntity pink = EntityTypes.create("sheep", 22.5, 1, 0.5, 90);
        pink.nbt().putByte("Color", 6);
        s.addEntity(pink);
        StructureEntity baby = EntityTypes.create("pig", 24.5, 1, 0.5, 45);
        baby.nbt().putInt("Age", -24000);
        s.addEntity(baby);
        StructureEntity farmer = EntityTypes.create("villager", 26.5, 1, 0.5, 180);
        farmer.nbt().put("VillagerData", new CompoundTag().putString("type", "minecraft:desert").putString("profession", "minecraft:farmer").putInt("level", 1));
        s.addEntity(farmer);

        // A wall with paintings and frames, decorated pots and open containers.
        for (int wx = 0; wx <= 12; wx++) for (int wy = 1; wy <= 4; wy++) s.set(new BlockPos(wx, wy, 8), BlockState.of("stone_bricks"));
        int[][] tiles = {{1, 2}, {4, 2}, {8, 2}};
        String[] variants = {"kebab", "pool", "match"};
        for (int i = 0; i < 3; i++) {
            CompoundTag nbt = new CompoundTag().putString("id", "minecraft:painting").putByte("facing", 2).putString("variant", "minecraft:" + variants[i]);
            int tx = tiles[i][0], ty = tiles[i][1], tz = 7;
            nbt.putInt("TileX", tx).putInt("TileY", ty).putInt("TileZ", tz);
            double[] c = EntityTypes.hangingPosition(tx, ty, tz, 2, nbt);
            s.addEntity(new StructureEntity(c[0], c[1], c[2], nbt));
        }
        CompoundTag frame = new CompoundTag().putString("id", "minecraft:item_frame").putByte("Facing", 2).putInt("TileX", 11).putInt("TileY", 2).putInt("TileZ", 7);
        double[] fc = EntityTypes.hangingPosition(11, 2, 7, 2, frame);
        s.addEntity(new StructureEntity(fc[0], fc[1], fc[2], frame));
        s.set(new BlockPos(14, 1, 7), BlockState.parse("minecraft:decorated_pot[cracked=false,facing=south,waterlogged=false]"));
        s.set(new BlockPos(16, 1, 7), BlockState.parse("minecraft:decorated_pot[cracked=false,facing=south,waterlogged=false]"));
        s.setBlockEntity(new BlockPos(16, 1, 7), new CompoundTag().put("sherds", io.blockdesigner.core.nbt.ListTag.of(
                new io.blockdesigner.core.nbt.StringTag("minecraft:brick"), new io.blockdesigner.core.nbt.StringTag("minecraft:angler_pottery_sherd"),
                new io.blockdesigner.core.nbt.StringTag("minecraft:heart_pottery_sherd"), new io.blockdesigner.core.nbt.StringTag("minecraft:skull_pottery_sherd"))));
        s.set(new BlockPos(18, 1, 7), BlockState.parse("minecraft:chest[facing=south,type=single,waterlogged=false]"));
        s.set(new BlockPos(20, 1, 7), BlockState.parse("minecraft:red_shulker_box[facing=up]"));

        Scene scene = new Scene();
        Layer layer = new Layer("mobs", s);
        scene.add(layer);
        Path dir = Path.of(System.getProperty("blockdesigner.renderDir", System.getProperty("java.io.tmpdir")));
        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            gpu.ready().get(20, TimeUnit.SECONDS);
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                sr.setOpen(layer, new BlockPos(18, 1, 7), 1f);
                sr.setOpen(layer, new BlockPos(20, 1, 7), 0.6f);
                sr.sync();
                while (sr.pending() > 0) Thread.sleep(10);
                // The mobs face north: look south at them.
                shot(gpu, sr, dir.resolve("mobs-row1.png"), 15, 10, new float[]{-1, 1, -1, 20, 3.5f, 2});
                shot(gpu, sr, dir.resolve("mobs-row2.png"), 15, 10, new float[]{-1, 1, 3, 20, 3, 6});
                shot(gpu, sr, dir.resolve("mobs-side.png"), 110, 12, new float[]{-1, 1, -1, 8, 3.5f, 2});
                shot(gpu, sr, dir.resolve("mobs-variants.png"), 15, 12, new float[]{21, 1, -1, 28, 3, 2});
                shot(gpu, sr, dir.resolve("mobs-wall.png"), 0, 10, new float[]{0, 1, 6, 21, 5, 8});
                shot(gpu, sr, dir.resolve("mobs-pots.png"), 20, 25, new float[]{13, 1, 6, 21, 3, 8});
            }
        }
    }

    private static void shot(ViewportRenderer gpu, SceneRenderer sr, Path out, float yawDeg, float pitchDeg, float[] area) throws Exception {
        Camera cam = new Camera();
        cam.setAngles((float) Math.toRadians(yawDeg + 180), (float) Math.toRadians(pitchDeg));
        cam.frame(area[0], area[1], area[2], area[3], area[4], area[5]);
        cam.setDistance(cam.distance() * 0.55f);
        int w = 1600, h = 900;
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
