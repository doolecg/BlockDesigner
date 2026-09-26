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
 * Fire, campfires, magma and sea lanterns play their animations: two frames a few game ticks apart differ, and the
 * frames say they need redrawing. A plain stone floor doesn't. Skipped without a Minecraft install; the two frames
 * go to the folder in -Dblockdesigner.renderDir (or the temp dir) as PNGs.
 */
class AnimatedTexturesRenderIT {

    @Test
    void animatedBlocksMoveAndStillOnesDont() throws Exception {
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty());
        BlockAssets assets = BlockAssets.open(jars.getFirst().jar(), List.of(), List.of(), null);
        // A campfire from the palette is lit, as in the game.
        BlockState campfire = assets.registry().get("campfire").orElseThrow().defaultState();
        assertThat(campfire.get("lit")).isEqualTo("true");

        Structure floor = new Structure();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 4; z++) floor.set(new BlockPos(x, 0, z), BlockState.of("stone"));
        Structure lit = new Structure();
        lit.set(new BlockPos(1, 1, 1), assets.registry().complete(BlockState.of("fire")));
        lit.set(new BlockPos(3, 1, 1), campfire);
        lit.set(new BlockPos(5, 1, 1), BlockState.of("magma_block"));
        lit.set(new BlockPos(6, 1, 2), BlockState.of("sea_lantern"));
        lit.set(new BlockPos(2, 1, 2), assets.registry().complete(BlockState.of("soul_fire")));

        Scene scene = new Scene();
        scene.add(new Layer("floor", floor));
        Path dir = Path.of(System.getProperty("blockdesigner.renderDir", System.getProperty("java.io.tmpdir")));
        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            try {
                gpu.ready().get(20, TimeUnit.SECONDS);
            } catch (Exception noGl) {
                Assumptions.abort("No OpenGL 3.3 context here: " + noGl.getMessage());
            }
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                settle(sr);
                assertThat(shot(gpu, sr, null).animated()).as("a stone floor doesn't animate").isFalse();

                scene.add(new Layer("lit", lit));
                settle(sr);
                ViewportRenderer.Frame a = shot(gpu, sr, dir.resolve("animated-a.png"));
                int[] pa = pixels(a);
                long t0 = ViewportRenderer.tick();
                while (ViewportRenderer.tick() < t0 + 3) Thread.sleep(10);
                ViewportRenderer.Frame b = shot(gpu, sr, dir.resolve("animated-b.png"));
                int[] pb = pixels(b);
                assertThat(a.animated()).isTrue();
                assertThat(b.animated()).isTrue();
                int changed = 0;
                for (int i = 0; i < pa.length; i++) if (pa[i] != pb[i]) changed++;
                assertThat(changed).as("pixels that changed between ticks").isGreaterThan(500);
            }
        }
    }

    private static void settle(SceneRenderer sr) throws InterruptedException {
        sr.sync();
        while (sr.pending() > 0) Thread.sleep(10);
    }

    private static int[] pixels(ViewportRenderer.Frame f) {
        int[] px = new int[f.width() * f.height()];
        f.pixels().get(px);
        f.pixels().rewind();
        return px;
    }

    private static ViewportRenderer.Frame shot(ViewportRenderer gpu, SceneRenderer sr, Path out) throws Exception {
        Camera cam = new Camera();
        cam.setAngles((float) Math.toRadians(200), (float) Math.toRadians(25));
        cam.frame(0, 0, 0, 8, 3, 4);
        int w = 800, h = 500;
        float[] vp = new float[16];
        cam.viewProjection(w / (float) h).get(vp);
        var eye = cam.eye();
        FrameRequest req = new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), List.of(),
                FrameRequest.Theme.LIGHT, false, 0, new float[]{6, 3}, 1, Float.POSITIVE_INFINITY);
        ViewportRenderer.Frame frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
        if (out != null) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, pixels(frame), 0, w);
            ImageIO.write(img, "png", out.toFile());
            System.out.println("Render written to " + out);
        }
        return frame;
    }
}
