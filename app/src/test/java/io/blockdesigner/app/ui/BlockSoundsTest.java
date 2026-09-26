package io.blockdesigner.app.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockSoundsTest {
    static final float RATE = 44100;

    @Test
    void paintRecordingSplitsIntoIntroLoopAndTail() {
        float[][] parts = BlockSounds.splitLoop(BlockSounds.loadRaw("paint"));
        assertThat(parts).isNotNull();
        float[] intro = parts[0], loop = parts[1], tail = parts[2];
        // The bundled paint.wav: silence, a ~0.3 s swell, a steady middle, a fade-out.
        assertThat(intro.length / RATE).isBetween(0.1f, 0.6f);
        assertThat(loop.length / RATE).isGreaterThan(0.3f);
        assertThat(tail.length / RATE).isBetween(0.1f, 0.8f);
        // Leading silence is gone: the first sample of the stroke is already (quietly) sounding within 50 ms.
        float early = 0;
        for (int i = 0; i < (int) (RATE * 0.05); i++) early = Math.max(early, Math.abs(intro[i]));
        assertThat(early).isGreaterThan(0.001f);
        // The loop's seam: wrapping from the last sample to the first is no bigger a step than the loop's usual ones.
        float maxStep = 0;
        for (int i = 1; i < loop.length; i++) maxStep = Math.max(maxStep, Math.abs(loop[i] - loop[i - 1]));
        assertThat(Math.abs(loop[0] - loop[loop.length - 1])).isLessThanOrEqualTo(maxStep);
        // The tail starts from nothing (it fades in over the loop's fade-out).
        assertThat(Math.abs(tail[0])).isLessThan(1e-4f);
        for (float[] p : parts) for (float v : p) assertThat(Math.abs(v)).isLessThan(1f);
    }

    @Test
    void uiSoundsAreShortAndSoft() {
        float[] click = BlockSounds.blip(2600, 1900, 0.018, 0.35, 0.55);
        assertThat(click.length / RATE).isLessThan(0.03f);
        float peak = 0;
        for (float v : click) peak = Math.max(peak, Math.abs(v));
        assertThat(peak).isCloseTo(0.55f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(Math.abs(click[click.length - 1])).isLessThan(0.01f);
    }
}
