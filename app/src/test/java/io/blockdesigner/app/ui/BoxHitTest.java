package io.blockdesigner.app.ui;

import io.blockdesigner.core.model.Box;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Which side of a layer's bounding box the mouse ray meets (for turning and moving layers by their box). */
class BoxHitTest {
    static final Box BOX = new Box(0, 0, 0, 9, 4, 9); // cells 0..9, so walls at 0 and 10 (y 0 and 5)

    static float[] hit(float ox, float oy, float oz, float dx, float dy, float dz) {
        return ViewportPane.boxHit(new Vector3f(ox, oy, oz), new Vector3f(dx, dy, dz).normalize(), BOX);
    }

    @Test
    void entersByTheSideItFaces() {
        // From above, looking down: the top (y, +1).
        assertThat(hit(5, 20, 5, 0, -1, 0)).containsExactly(15f, 1f, 1f);
        // From the west, looking east: the west side (x, -1), even when aiming low.
        assertThat(hit(-10, 1, 5, 1, 0, 0)).containsExactly(10f, 0f, -1f);
        // From the south at an angle, hitting the south wall (z, +1).
        float[] s = hit(5, 3, 30, 0, -0.1f, -1);
        assertThat(s[1]).isEqualTo(2f);
        assertThat(s[2]).isEqualTo(1f);
    }

    @Test
    void missesAndInsides() {
        assertThat(hit(50, 50, 50, 1, 0, 0)).isNull();
        // From inside, the side the ray leaves by.
        float[] in = hit(5, 2, 5, 0, 0, -1);
        assertThat(in[1]).isEqualTo(2f);
        assertThat(in[2]).isEqualTo(-1f);
    }
}
