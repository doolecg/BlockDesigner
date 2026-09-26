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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every mob in the catalog, each drawn on its own tile of a contact sheet (mobs-all-&lt;version&gt;.png) to check against
 * the game by eye, and each drawn with a real model rather than its placeholder box. Uses the Minecraft version in
 * -Dblockdesigner.mcVersion or $MC_VERSION (default 26.3); skipped without a Minecraft install. The sheet goes to
 * -Dblockdesigner.renderDir (or the temp dir).
 */
class AllMobsRenderIT {
    private static final int TILE = 256, COLUMNS = 10;
    private static final List<String> NEWER = List.of("minecraft:happy_ghast", "minecraft:copper_golem", "minecraft:nautilus",
            "minecraft:zombie_nautilus", "minecraft:parched", "minecraft:creaking", "minecraft:camel_husk");

    @Test
    void everyMobHasAModel() throws Exception {
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty());
        String want = System.getProperty("blockdesigner.mcVersion", System.getenv().getOrDefault("MC_VERSION", "26.3"));
        var jar = jars.stream().filter(j -> j.version().id().startsWith(want)).findFirst().orElse(jars.getFirst());
        BlockAssets assets = BlockAssets.open(jar.jar(), List.of(), List.of(), null);

        List<EntityTypes.Kind> mobs = EntityTypes.all().stream().filter(k -> !k.id().endsWith("painting") && !k.id().endsWith("item_frame")).toList();
        List<String> boxes = new ArrayList<>();
        Structure s = new Structure();
        List<StructureEntity> placed = new ArrayList<>();
        // In a row, far enough apart that each tile shows only its own mob.
        double x = 0;
        for (EntityTypes.Kind k : mobs) {
            double w = Math.max(2, k.width() + 2);
            StructureEntity e = EntityTypes.create(k.id(), x + w / 2, 1, 0.5, 180);
            s.addEntity(e);
            placed.add(e);
            // The placeholder is one box: 6 quads.
            if (assets.entityQuads(e).size() <= 6) boxes.add(k.id());
            x += w + Math.max(4, k.height() * 2);
        }
        for (int bx = -2; bx <= (int) x + 2; bx++) for (int bz = -12; bz <= 6; bz++) s.set(new BlockPos(bx, 0, bz), BlockState.of("stone"));

        Scene scene = new Scene();
        scene.add(new Layer("mobs", s));
        Path dir = Path.of(System.getProperty("blockdesigner.renderDir", System.getProperty("java.io.tmpdir")));
        int rows = (mobs.size() + COLUMNS - 1) / COLUMNS;
        BufferedImage sheet = new BufferedImage(COLUMNS * TILE, rows * TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            try {
                gpu.ready().get(20, TimeUnit.SECONDS);
            } catch (Exception noGl) {
                Assumptions.abort("No OpenGL 3.3 context here: " + noGl.getMessage());
            }
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                sr.sync();
                while (sr.pending() > 0) Thread.sleep(10);
                for (int i = 0; i < placed.size(); i++) {
                    double[] b = EntityTypes.box(placed.get(i));
                    BufferedImage tile = shot(gpu, sr, new float[]{(float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5]});
                    int tx = (i % COLUMNS) * TILE, ty = (i / COLUMNS) * TILE;
                    g.drawImage(tile, tx, ty, null);
                    String name = mobs.get(i).name();
                    g.setColor(Color.BLACK);
                    g.drawString(name, tx + 7, ty + 19);
                    g.setColor(boxes.contains(mobs.get(i).id()) ? Color.RED : Color.WHITE);
                    g.drawString(name, tx + 6, ty + 18);
                }
            }
        }
        g.dispose();
        Path out = dir.resolve("mobs-all-" + want + ".png");
        ImageIO.write(sheet, "png", out.toFile());
        System.out.println("Render written to " + out);
        System.out.println("Placeholder boxes: " + boxes);
        // Mobs added after 1.21.1 have no textures there, so older versions draw them as boxes.
        if (!want.startsWith("26.")) boxes.removeAll(NEWER);
        assertThat(boxes).as("mobs drawn as their placeholder box").isEmpty();
        // Eyes glow (drawn unlit), as the game's "eyes" layers do.
        for (String glowing : List.of("minecraft:enderman", "minecraft:spider", "minecraft:phantom", "minecraft:ender_dragon")) {
            assertThat(assets.entityQuads(EntityTypes.create(glowing, 0, 0, 0, 0))).as(glowing + " eyes")
                    .anyMatch(io.blockdesigner.assets.model.BakedQuad::glow);
        }
    }

    private static BufferedImage shot(ViewportRenderer gpu, SceneRenderer sr, float[] box) throws Exception {
        Camera cam = new Camera();
        // From the front and a little to the side and above: the mobs face north, towards -z.
        cam.setAngles((float) Math.toRadians(20 + 180), (float) Math.toRadians(15));
        cam.frame(box[0], box[1], box[2], box[3], box[4], box[5]);
        float[] vp = new float[16];
        cam.viewProjection(1f).get(vp);
        var eye = cam.eye();
        FrameRequest req = new FrameRequest(TILE, TILE, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), List.of(),
                FrameRequest.Theme.LIGHT, false, 0, new float[]{6, 3}, 1, Float.POSITIVE_INFINITY);
        ViewportRenderer.Frame frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[TILE * TILE];
        frame.pixels().get(px);
        img.setRGB(0, 0, TILE, TILE, px, 0, TILE);
        return img;
    }
}
