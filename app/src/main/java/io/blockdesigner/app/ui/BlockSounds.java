package io.blockdesigner.app.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generic place / break thuds, synthesised at start-up (no game files needed) and mixed on one low-latency audio line.
 * Like Minecraft, each play picks a variant with a slightly different pitch so repeated clicks don't sound robotic.
 * If there is no audio device the sounds quietly do nothing.
 */
final class BlockSounds {
    private static final float RATE = 44100;
    private static final int VARIANTS = 5, CHUNK = 256;

    private final float[][] place = new float[VARIANTS][], breaks = new float[VARIANTS][];
    private final List<Voice> voices = new ArrayList<>();
    private final Random random = new Random();
    private SourceDataLine line;
    private Thread mixer;
    private int lastPlace = -1, lastBreak = -1;

    private record Voice(float[] samples, float gain, int[] pos) {
    }

    BlockSounds() {
        Random r = new Random(7);
        for (int i = 0; i < VARIANTS; i++) {
            float pitch = 0.86f + 0.07f * i;
            place[i] = thud(pitch, r);
            breaks[i] = crunch(pitch, r);
        }
    }

    void place(double volume) {
        lastPlace = play(place, lastPlace, volume * 0.9);
    }

    void breakBlock(double volume) {
        lastBreak = play(breaks, lastBreak, volume);
    }

    private int play(float[][] set, int last, double volume) {
        if (volume <= 0 || !open()) return last;
        int i = random.nextInt(VARIANTS);
        if (i == last) i = (i + 1) % VARIANTS;
        synchronized (voices) {
            // A held burst never piles up more than a few overlapping thuds.
            if (voices.size() >= 6) voices.removeFirst();
            voices.add(new Voice(set[i], (float) volume, new int[]{0}));
            voices.notifyAll();
        }
        return i;
    }

    private boolean open() {
        if (line != null) return true;
        if (mixer != null) return false;
        try {
            AudioFormat f = new AudioFormat(RATE, 16, 1, true, false);
            SourceDataLine l = AudioSystem.getSourceDataLine(f);
            l.open(f, CHUNK * 2 * 8);
            l.start();
            line = l;
        } catch (Exception | LinkageError e) {
            mixer = new Thread(() -> { });
            return false;
        }
        mixer = new Thread(this::mix, "block-sounds");
        mixer.setDaemon(true);
        mixer.start();
        return true;
    }

    private void mix() {
        byte[] out = new byte[CHUNK * 2];
        float[] acc = new float[CHUNK];
        while (true) {
            synchronized (voices) {
                while (voices.isEmpty()) {
                    try {
                        voices.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                java.util.Arrays.fill(acc, 0);
                for (var it = voices.iterator(); it.hasNext(); ) {
                    Voice v = it.next();
                    int p = v.pos[0];
                    int n = Math.min(CHUNK, v.samples.length - p);
                    for (int k = 0; k < n; k++) acc[k] += v.samples[p + k] * v.gain;
                    v.pos[0] = p + n;
                    if (v.pos[0] >= v.samples.length) it.remove();
                }
            }
            for (int k = 0; k < CHUNK; k++) {
                // Soft clip so overlapping thuds never crackle.
                float x = (float) Math.tanh(acc[k]);
                int s = Math.round(x * 32000);
                out[k * 2] = (byte) s;
                out[k * 2 + 1] = (byte) (s >> 8);
            }
            line.write(out, 0, out.length);
        }
    }

    // ---- synthesis -------------------------------------------------------------------------------------------

    /** Placing: a soft, woody knock — a quick downward pitch sweep over a little filtered noise. */
    private static float[] thud(float pitch, Random r) {
        int n = (int) (RATE * 0.085f / pitch);
        float[] s = new float[n];
        double phase = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double f = (70 + 150 * Math.exp(-t / 0.012)) * pitch;
            phase += 2 * Math.PI * f / RATE;
            double env = Math.min(1, t / 0.0015) * Math.exp(-t / 0.022);
            lp += 0.18 * ((r.nextDouble() * 2 - 1) - lp);
            double click = t < 0.004 ? (r.nextDouble() * 2 - 1) * (1 - t / 0.004) * 0.35 : 0;
            s[i] = (float) (env * (0.75 * Math.sin(phase) + 0.45 * lp) + click);
        }
        return s;
    }

    /** Breaking: a crunchier knock — grainy low-passed noise bursts over a deeper thump. */
    private static float[] crunch(float pitch, Random r) {
        int n = (int) (RATE * 0.14f / pitch);
        float[] s = new float[n];
        double phase = 0, lp = 0;
        // A handful of grains in the first 60 ms give it texture.
        double[] grains = new double[5];
        for (int g = 0; g < grains.length; g++) grains[g] = r.nextDouble() * 0.06;
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double f = (55 + 110 * Math.exp(-t / 0.015)) * pitch;
            phase += 2 * Math.PI * f / RATE;
            lp += 0.32 * ((r.nextDouble() * 2 - 1) - lp);
            double grain = 0;
            for (double g0 : grains) if (t >= g0 && t < g0 + 0.012) grain += Math.exp(-(t - g0) / 0.004);
            double env = Math.min(1, t / 0.001) * Math.exp(-t / 0.035);
            s[i] = (float) (env * (0.55 * Math.sin(phase) + 0.7 * lp) + 0.35 * lp * grain);
        }
        return s;
    }
}
