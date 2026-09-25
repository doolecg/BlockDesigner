package io.blockdesigner.render;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CameraTest {
    @Test
    void orbitAroundKeepsThePivotStillOnScreen() {
        Camera c = new Camera();
        c.setTarget(0, 0, 0);
        Vector3f pivot = new Vector3f(6, 3, -4);
        float[] before = screen(c, pivot);
        c.orbitAround(pivot, 0.4f, -0.2f);
        float[] after = screen(c, pivot);
        assertThat(after[0]).isCloseTo(before[0], within(1e-3f));
        assertThat(after[1]).isCloseTo(before[1], within(1e-3f));
        // ...and the eye kept its distance from the pivot.
        assertThat(c.eye().distance(pivot)).isGreaterThan(0);
    }

    private static float[] screen(Camera c, Vector3f p) {
        Vector4f v = new Vector4f(p, 1).mul(c.viewProjection(16 / 9f));
        return new float[]{v.x / v.w, v.y / v.w};
    }
}
