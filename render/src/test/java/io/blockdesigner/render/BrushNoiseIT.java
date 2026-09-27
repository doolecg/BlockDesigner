package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.edit.Sculpt;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Renders brush dabs side by side (clean sphere, noisy sphere, splatter, noisy cube) to check by eye (BRUSH_NOISE=1). */
class BrushNoiseIT {

    @Test
    void dabs() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("BRUSH_NOISE")));
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty(), "no Minecraft install found");
        BlockAssets assets = BlockAssets.open(jars.getFirst().jar(), List.of(), List.of(), null);
        Structure s = new Structure();
        for (int x = -12; x < 100; x++) for (int z = -12; z < 12; z++) s.set(x, 0, z, BlockState.of("grass_block"));
        Sculpt.Brush[] brushes = {new Sculpt.Brush(9, Sculpt.Shape.SPHERE, 1), new Sculpt.Brush(9, Sculpt.Shape.SPHERE, 1, 0.6, 6, 5),
                new Sculpt.Brush(9, Sculpt.Shape.SPLATTER, 1, 0, 8, 5), new Sculpt.Brush(9, Sculpt.Shape.CUBE, 1, 0.5, 5, 9)};
        for (int i = 0; i < brushes.length; i++)
            for (BlockPos p : Sculpt.shape(brushes[i], new BlockPos(i * 24, 9, 0))) s.set(p.x(), p.y(), p.z(), BlockState.of("stone"));
        Scene scene = new Scene();
        scene.add(new Layer("a", s));
        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            gpu.ready().get(20, TimeUnit.SECONDS);
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                sr.sync();
                long end = System.currentTimeMillis() + 30_000;
                while (sr.pending() > 0 && System.currentTimeMillis() < end) Thread.sleep(5);
                Camera cam = new Camera();
                cam.setAngles((float) Math.toRadians(20), (float) Math.toRadians(20));
                cam.frame(-10, 0, -10, 84, 18, 10);
                int w = 1600, h = 600;
                float[] vp = new float[16];
                cam.viewProjection(w / (float) h).get(vp);
                var eye = cam.eye();
                FrameRequest req = new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), List.of(),
                        FrameRequest.Theme.DARK, false, 0, new float[]{0, 0}, 1, Float.POSITIVE_INFINITY);
                var frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
                int[] px = new int[w * h];
                frame.pixels().get(px);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, w, h, px, 0, w);
                Path out = Path.of(System.getProperty("java.io.tmpdir"), "brush-noise.png");
                javax.imageio.ImageIO.write(img, "png", out.toFile());
                System.out.println("wrote " + out);
            }
        }
    }
}
