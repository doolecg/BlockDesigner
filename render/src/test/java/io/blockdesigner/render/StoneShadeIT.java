package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Prints how bright a stone wall's faces come out, to compare with the texture (set STONE_SHADE=1). */
class StoneShadeIT {

    @Test
    void stoneFaces() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("STONE_SHADE")));
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty(), "no Minecraft install found");
        BlockAssets assets = BlockAssets.open(jars.getFirst().jar(), List.of(), List.of(), null);
        System.out.println("jar " + jars.getFirst().jar());
        Structure s = new Structure();
        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) for (int z = 0; z < 64; z++) s.set(x, y, z, BlockState.of("stone"));
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
                // Straight down at the top, then straight at each side, close up (little mipmapping).
                float[][] views = {{0, 89.9f}, {0, 0}, {90, 0}, {180, 0}, {270, 0}};
                String[] names = {"top", "north", "east", "south", "west"};
                for (int i = 0; i < views.length; i++) {
                    Camera cam = new Camera();
                    cam.setAngles((float) Math.toRadians(views[i][0]), (float) Math.toRadians(views[i][1]));
                    cam.frame(24, 24, 24, 40, 40, 40);
                    int w = 256, h = 256;
                    float[] vp = new float[16];
                    cam.viewProjection(1f).get(vp);
                    var eye = cam.eye();
                    FrameRequest req = new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), List.of(),
                            FrameRequest.Theme.DARK, false, 0, new float[]{0, 0}, 1, Float.POSITIVE_INFINITY);
                    var frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
                    int[] px = new int[w * h];
                    frame.pixels().get(px);
                    double sum = 0;
                    int n = 0;
                    for (int y = h / 4; y < h * 3 / 4; y++)
                        for (int x = w / 4; x < w * 3 / 4; x++) {
                            int p = px[y * w + x];
                            sum += ((p >> 16 & 255) + (p >> 8 & 255) + (p & 255)) / 3.0;
                            n++;
                        }
                    System.out.printf("%s mean %.1f (pixel %08X)%n", names[i], sum / n, px[h / 2 * w + w / 2]);
                }
            }
        }
    }
}
