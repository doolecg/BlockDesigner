package io.blockdesigner.render;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Duplicated layers share one GPU mesh until one of them is edited or clipped differently. */
class InstancedMeshIT {

    @Test
    void copiesShareAMeshUntilEdited() throws Exception {
        var jars = new McInstallLocator().scan().jars();
        Assumptions.assumeTrue(!jars.isEmpty(), "no Minecraft install found");
        BlockAssets assets = BlockAssets.open(jars.getFirst().jar(), List.of(), List.of(), null);

        Structure s = new Structure();
        for (int x = 0; x < 40; x++) for (int y = 0; y < 20; y++) for (int z = 0; z < 40; z++) {
            if ((x + y + z) % 3 == 0) s.set(x, y, z, BlockState.of("stone_bricks"));
        }
        Scene scene = new Scene();
        Layer a = new Layer("a", s);
        scene.add(a);
        try (ViewportRenderer gpu = new ViewportRenderer(assets.atlas(), f -> {
        })) {
            gpu.ready().get(20, TimeUnit.SECONDS);
            try (SceneRenderer sr = new SceneRenderer(scene, assets, gpu, () -> {
            })) {
                settle(sr);
                assertThat(sr.meshCount()).isEqualTo(1);

                Layer b = a.duplicate("b");
                b.setOffset(new BlockPos(60, 0, 0));
                Layer c = a.duplicate("c");
                c.setOffset(new BlockPos(0, 0, 60));
                scene.add(b);
                scene.add(c);
                // No new meshing work: the copies draw the first layer's mesh.
                assertThat(sr.pending()).isZero();
                assertThat(sr.meshCount()).isEqualTo(1);
                var draws = sr.layerDraws(null);
                assertThat(draws).hasSize(3);
                assertThat(draws).extracting(FrameRequest.LayerDraw::layerId).containsOnly(draws.getFirst().layerId());
                assertThat(render(gpu, sr, scene)).isTrue();

                // Editing one copy splits it off; the others keep sharing.
                b.structure().set(1, 1, 1, BlockState.of("gold_block"));
                scene.fireBlocksChanged(b, new Box(1, 1, 1, 1, 1, 1));
                assertThat(sr.meshCount()).isEqualTo(2);
                settle(sr);
                draws = sr.layerDraws(null);
                assertThat(draws.get(0).layerId()).isEqualTo(draws.get(2).layerId()).isNotEqualTo(draws.get(1).layerId());

                // A Y slice clips each layer at its own height: copies at different heights need their own mesh.
                c.setOffset(new BlockPos(0, 5, 60));
                scene.firePropertiesChanged(c);
                sr.setSlice(Integer.MIN_VALUE, 10);
                assertThat(sr.meshCount()).isEqualTo(3);
                sr.setSlice(Integer.MIN_VALUE, Integer.MAX_VALUE);
                assertThat(sr.meshCount()).isEqualTo(2);
                settle(sr);

                scene.remove(a);
                scene.remove(c);
                assertThat(sr.meshCount()).isEqualTo(1);
            }
        }
    }

    private static void settle(SceneRenderer sr) throws InterruptedException {
        sr.sync();
        long end = System.currentTimeMillis() + 20_000;
        while (sr.pending() > 0 && System.currentTimeMillis() < end) Thread.sleep(5);
        assertThat(sr.pending()).isZero();
    }

    /** Renders the scene from above and checks something was drawn. */
    private static boolean render(ViewportRenderer gpu, SceneRenderer sr, Scene scene) throws Exception {
        Box b = scene.worldBounds().orElseThrow();
        Camera cam = new Camera();
        cam.setAngles((float) Math.toRadians(35), (float) Math.toRadians(40));
        cam.frame(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        int w = 320, h = 200;
        float[] vp = new float[16];
        cam.viewProjection(w / (float) h).get(vp);
        var eye = cam.eye();
        FrameRequest req = new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, sr.layerDraws(null), List.of(),
                FrameRequest.Theme.DARK, false, 0, new float[]{0, 0}, 1, Float.POSITIVE_INFINITY);
        var frame = gpu.capture(req).get(20, TimeUnit.SECONDS);
        int[] px = new int[w * h];
        frame.pixels().get(px);
        int differing = 0;
        for (int p : px) if (p != px[0]) differing++;
        return differing > w * h / 10;
    }
}
