package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders a real schematic offscreen and writes a PNG; skipped without a Minecraft install or test file. */
class RenderSmokeIT {

    @Test
    void rendersWatchtower() throws Exception {
        var jars = new McInstallLocator().scan().jars();
        Path file = Path.of("..", "testdata", "Pinecrest Watchtower.litematic");
        Assumptions.assumeTrue(!jars.isEmpty() && Files.exists(file));

        BlockAssets assets = BlockAssets.open(jars.getFirst().jar(), List.of(), List.of(), null);
        Structure s = Schematics.read(file).merged();
        Scene scene = new Scene();
        Layer layer = new Layer("Watchtower", s);
        scene.add(layer);

        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            gpu.ready().get(20, TimeUnit.SECONDS);
            System.out.println("GL: " + gpu.glInfo());
            long t0 = System.nanoTime();
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                sr.sync();
                while (sr.pending() > 0) Thread.sleep(10);
                System.out.printf("Meshed %d sections in %d ms%n", s.sectionKeys().size(), (System.nanoTime() - t0) / 1_000_000);

                Box b = s.bounds().orElseThrow();
                Camera cam = new Camera();
                cam.setAngles((float) Math.toRadians(35), (float) Math.toRadians(25));
                cam.frame(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
                int w = 1280, h = 800;
                float[] vp = new float[16];
                cam.viewProjection(w / (float) h).get(vp);
                var eye = cam.eye();
                var lines = new java.util.ArrayList<FrameRequest.Line>();
                Overlays.box(lines, b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1, 0xFF7C9CFF);
                FrameRequest req = new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), lines,
                        FrameRequest.Theme.DARK, true, 0, new float[]{(b.minX() + b.maxX()) / 2f, (b.minZ() + b.maxZ()) / 2f}, 1);
                ViewportRenderer.Frame frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                int[] px = new int[w * h];
                frame.pixels().get(px);
                img.setRGB(0, 0, w, h, px, 0, w);
                Path out = Path.of(System.getProperty("java.io.tmpdir"), "blockdesigner-render.png");
                ImageIO.write(img, "png", out.toFile());
                System.out.println("Render written to " + out);
                // Something other than background must have been drawn in the centre.
                assertThat(px[(h / 2) * w + w / 2]).isNotEqualTo(px[5]);
            }
        }
    }

    @Test
    void pickerHitsTopFace() {
        Structure s = new Structure();
        s.set(0, 0, 0, io.blockdesigner.core.model.BlockState.of("stone"));
        Scene scene = new Scene();
        Layer l = new Layer("a", s);
        l.setOffset(new BlockPos(10, 5, 10));
        scene.add(l);
        var hit = Picker.pick(scene, new org.joml.Vector3f(10.5f, 20, 10.5f), new org.joml.Vector3f(0, -1, 0), 100, x -> true);
        assertThat(hit).isPresent();
        assertThat(hit.get().world()).isEqualTo(new BlockPos(10, 5, 10));
        assertThat(hit.get().normal()).isEqualTo(new BlockPos(0, 1, 0));
        assertThat(hit.get().adjacentWorld()).isEqualTo(new BlockPos(10, 6, 10));
    }
}
