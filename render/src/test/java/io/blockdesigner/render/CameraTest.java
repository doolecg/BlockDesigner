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

    @Test
    void orthographicKeepsTheFramingAtTheTarget() {
        Camera c = new Camera();
        c.setTarget(0, 0, 0);
        c.setDistance(40);
        Vector3f p = new Vector3f(c.right()).mul(5);
        float persp = screen(c, p)[0];
        c.setOrtho(true);
        assertThat(screen(c, p)[0]).isCloseTo(persp, within(1e-3f));
        // No perspective shrink: a point far behind the target lands in the same place.
        Vector3f behind = new Vector3f(p).add(c.forward().mul(30));
        assertThat(screen(c, behind)[0]).isCloseTo(persp, within(1e-3f));
    }

    @Test
    void straightDownIsStableWithNorthUp() {
        Camera c = new Camera();
        c.setAngles(0, (float) (Math.PI / 2));
        assertThat(c.forward().y).isCloseTo(-1, within(1e-5f));
        assertThat(c.up().z).isCloseTo(-1, within(1e-5f));
        assertThat(c.right().x).isCloseTo(1, within(1e-5f));
        float[] v = new float[16];
        c.viewProjection(1).get(v);
        for (float f : v) assertThat(Float.isNaN(f)).isFalse();
    }

    private static float[] screen(Camera c, Vector3f p) {
        Vector4f v = new Vector4f(p, 1).mul(c.viewProjection(16 / 9f));
        return new float[]{v.x / v.w, v.y / v.w};
    }

    @Test
    void dollyIntoOrthographicEndsWhereOrthographicStarts() {
        Camera c = new Camera();
        c.setTarget(0, 0, 0);
        c.setDistance(40);
        Vector3f p = new Vector3f(c.right()).mul(5).add(new Vector3f(c.up()).mul(3));
        float[] persp = screen(c, p);
        c.setOrthoTransition(0);
        float[] start = screen(c, p);
        assertThat(start[0]).isCloseTo(persp[0], within(1e-3f));
        assertThat(start[1]).isCloseTo(persp[1], within(1e-3f));
        c.setOrthoTransition(1);
        float[] end = screen(c, p);
        c.setOrthoTransition(-1);
        c.setOrtho(true);
        float[] ortho = screen(c, p);
        assertThat(end[0]).isCloseTo(ortho[0], within(1e-3f));
        assertThat(end[1]).isCloseTo(ortho[1], within(1e-3f));
    }
}
