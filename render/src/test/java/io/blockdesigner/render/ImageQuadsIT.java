package io.blockdesigner.render;

import io.blockdesigner.assets.AssetStack;
import io.blockdesigner.assets.TextureAtlas;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Draws image quads (plugin scene objects such as reference images) offscreen; skipped where OpenGL 3.3 isn't available. */
class ImageQuadsIT {
    private static final int W = 200, H = 200;

    /** A two-pixel picture, red on the left and blue on the right. */
    private static final int[] RED_BLUE = {0xFFFF0000, 0xFF0000FF};

    private static int[] render(ViewportRenderer gpu, List<FrameRequest.ImageQuad> images) throws Exception {
        Camera cam = new Camera();
        cam.setAngles(0, 0);
        cam.setTarget(0, 0, 0);
        cam.setDistance(5);
        float[] vp = new float[16];
        cam.viewProjection(1).get(vp);
        var eye = cam.eye();
        FrameRequest req = new FrameRequest(W, H, vp, new float[]{eye.x, eye.y, eye.z}, List.of(), List.of(), FrameRequest.Theme.DARK,
                false, -100, new float[]{0, 0}, 1, Float.POSITIVE_INFINITY, images);
        ViewportRenderer.Frame f = gpu.capture(req).get(20, TimeUnit.SECONDS);
        int[] px = new int[W * H];
        f.pixels().get(px);
        return px;
    }

    private static FrameRequest.ImageQuad quad(float[] uv, float opacity, FrameRequest.ImageDepth depth) {
        // A 2×2 square facing +Z (the camera), corners bottom-left, bottom-right, top-right, top-left.
        return new FrameRequest.ImageQuad(RED_BLUE, 2, 1, RED_BLUE, new float[]{-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0}, uv, opacity, depth);
    }

    private static int red(int argb) {
        return (argb >> 16) & 255;
    }

    private static int blue(int argb) {
        return argb & 255;
    }

    @Test
    void drawsImagesTheRightWayRoundAndCropsOutsideTheirUv() throws Exception {
        TextureAtlas atlas = TextureAtlas.build(new AssetStack(List.of()), List.of());
        try (ViewportRenderer gpu = new ViewportRenderer(atlas, f -> {
        })) {
            try {
                gpu.ready().get(20, TimeUnit.SECONDS);
            } catch (Exception noGl) {
                Assumptions.abort("No OpenGL 3.3 context here: " + noGl.getMessage());
            }
            float[] full = {0, 1, 1, 1, 1, 0, 0, 0};
            int left = (H / 2) * W + W / 2 - 20, right = (H / 2) * W + W / 2 + 20;
            for (FrameRequest.ImageDepth depth : FrameRequest.ImageDepth.values()) {
                int[] px = render(gpu, List.of(quad(full, 1, depth)));
                assertThat(red(px[left])).as(depth + " left is red").isGreaterThan(200);
                assertThat(blue(px[right])).as(depth + " right is blue").isGreaterThan(200);
            }
            int[] background = render(gpu, List.of());
            // Offset by half the picture: the right half of the quad is past the picture's edge, so it's see-through.
            int[] shifted = render(gpu, List.of(quad(new float[]{0.5f, 1, 1.5f, 1, 1.5f, 0, 0.5f, 0}, 1, FrameRequest.ImageDepth.IN_SCENE)));
            assertThat(shifted[right]).isEqualTo(background[right]);
            assertThat(blue(shifted[(H / 2) * W + W / 2 - 2])).isGreaterThan(200);
            // Half opacity mixes with the sky.
            int[] half = render(gpu, List.of(quad(full, 0.5f, FrameRequest.ImageDepth.IN_FRONT)));
            assertThat(red(half[left])).isBetween(100, 200);
        }
    }
}
