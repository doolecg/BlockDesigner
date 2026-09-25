package io.blockdesigner.app.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Place / break sounds, mixed on one low-latency audio line: the bundled {@code sounds/place.wav} and
 * {@code sounds/break.wav} (more variants can be added as place-2.wav, break-2.wav…; each play picks a different one).
 * Every other click plays at a slightly random pitch (like Minecraft's pitch variation), the ones between at the
 * original pitch. Recordings are mixed down to mono, trimmed of silence and levelled on load. If they are missing,
 * simple synthesised thuds stand in; with no audio device the sounds quietly do nothing.
 */
final class BlockSounds {
    private static final float RATE = 44100;
    // 128-frame chunks into a 1024-frame line buffer: a new sound starts within ~23 ms of the click.
    private static final int VARIANTS = 5, CHUNK = 128, BUFFER_FRAMES = 1024;

    private final float[][] place, breaks;
    private final List<Voice> voices = new ArrayList<>();
    private final Random random = new Random();
    private SourceDataLine line;
    private Thread mixer;
    private int lastPlace = -1, lastBreak = -1;
    /** How far the pitch may wander on a varied click (±8%), and the click counter that alternates them. */
    private static final double PITCH_SPREAD = 0.08;
    private int clicks;

    /** A playing sound: read at {@code rate} samples per output sample (above 1 is higher and shorter). */
    private record Voice(float[] samples, float gain, double rate, double[] pos) {
    }

    BlockSounds() {
        float[][] p = loadRecordings("place"), b = loadRecordings("break");
        // A missing recording falls back to the other one, then to the synthesised thuds.
        if (p.length == 0) p = b;
        if (b.length == 0) b = p;
        if (p.length == 0) {
            p = new float[VARIANTS][];
            b = new float[VARIANTS][];
            Random r = new Random(7);
            for (int i = 0; i < VARIANTS; i++) {
                float pitch = 0.9f + 0.05f * i;
                p[i] = thud(pitch, r);
                b[i] = crunch(pitch, r);
            }
        }
        place = p;
        breaks = b;
        // Open the audio device now (it can take a few hundred ms), so the first click isn't late.
        Thread warm = new Thread(this::open, "block-sounds-open");
        warm.setDaemon(true);
        warm.start();
    }

    /** The bundled {@code <name>.wav}, {@code <name>-2.wav}… as mono float samples at {@link #RATE}, levelled. */
    private static float[][] loadRecordings(String name) {
        List<float[]> out = new ArrayList<>();
        for (int i = 1; i <= 32; i++) {
            var url = BlockSounds.class.getResource("/io/blockdesigner/app/sounds/" + name + (i == 1 ? "" : "-" + i) + ".wav");
            if (url == null) break;
            try (var in = AudioSystem.getAudioInputStream(url)) {
                float[] mono = level(toMono(in));
                if (mono.length > 0) out.add(mono);
            } catch (Exception e) {
                // unreadable file: skip that variant
            }
        }
        return out.toArray(new float[0][]);
    }

    /** Decodes to 16-bit PCM, averages the channels and resamples to 44.1 kHz if needed. */
    private static float[] toMono(javax.sound.sampled.AudioInputStream in) throws java.io.IOException {
        AudioFormat src = in.getFormat();
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, src.getChannels(),
                src.getChannels() * 2, src.getSampleRate(), false);
        javax.sound.sampled.AudioInputStream s16 = src.matches(pcm) ? in : AudioSystem.getAudioInputStream(pcm, in);
        byte[] b = s16.readAllBytes();
        int ch = pcm.getChannels(), frames = b.length / (2 * ch);
        float[] m = new float[frames];
        for (int f = 0; f < frames; f++) {
            float sum = 0;
            for (int c = 0; c < ch; c++) {
                int o = (f * ch + c) * 2;
                sum += (short) ((b[o] & 255) | (b[o + 1] << 8)) / 32768f;
            }
            m[f] = sum / ch;
        }
        if (Math.abs(pcm.getSampleRate() - RATE) < 1) return m;
        double step = pcm.getSampleRate() / RATE;
        float[] r = new float[(int) (frames / step)];
        for (int i = 0; i < r.length; i++) {
            double x = i * step;
            int x0 = (int) x;
            float t = (float) (x - x0);
            r[i] = m[Math.min(x0, frames - 1)] * (1 - t) + m[Math.min(x0 + 1, frames - 1)] * t;
        }
        return r;
    }

    /** Brings every recording to the same loudness (RMS of the loud part), never clipping. */
    private static float[] level(float[] s) {
        double peak = 0, sum = 0;
        int n = 0;
        for (float v : s) peak = Math.max(peak, Math.abs(v));
        if (peak < 1e-5) return new float[0];
        // Trim near-silence at both ends (e.g. MP3 encoder padding) so a click sounds the instant it happens.
        int a = 0, b = s.length;
        while (a < b && Math.abs(s[a]) < peak * 0.02) a++;
        while (b > a && Math.abs(s[b - 1]) < peak * 0.01) b--;
        s = java.util.Arrays.copyOfRange(s, Math.max(0, a - 16), Math.min(s.length, b + 64));
        for (float v : s) {
            if (Math.abs(v) < peak * 0.1) continue;
            sum += v * v;
            n++;
        }
        double rms = Math.sqrt(sum / Math.max(1, n));
        float k = (float) Math.min(0.28 / rms, 0.95 / peak);
        for (int i = 0; i < s.length; i++) s[i] *= k;
        return s;
    }

    void place(double volume) {
        lastPlace = play(place, lastPlace, volume * 0.9);
    }

    void breakBlock(double volume) {
        lastBreak = play(breaks, lastBreak, volume);
    }

    private int play(float[][] set, int last, double volume) {
        // Never block the click on the audio device: it is opened at start-up, and until it's ready there is no sound.
        if (volume <= 0 || line == null) return last;
        int i = random.nextInt(set.length);
        if (i == last && set.length > 1) i = (i + 1) % set.length;
        synchronized (voices) {
            // A held burst never piles up more than a few overlapping thuds.
            if (voices.size() >= 6) voices.removeFirst();
            // Every other click gets a slightly random pitch; the rest play as recorded.
            double rate = clicks++ % 2 == 1 ? 1 + (random.nextDouble() * 2 - 1) * PITCH_SPREAD : 1;
            voices.add(new Voice(set[i], (float) volume, rate, new double[]{0}));
            voices.notifyAll();
        }
        return i;
    }

    private synchronized boolean open() {
        if (line != null) return true;
        if (mixer != null) return false;
        try {
            AudioFormat f = new AudioFormat(RATE, 16, 1, true, false);
            SourceDataLine l = AudioSystem.getSourceDataLine(f);
            l.open(f, BUFFER_FRAMES * 2);
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
                // No waiting when idle: the line keeps getting (silent) chunks so it never drains. A drained line
                // can take a moment to restart on Windows, which is what made some clicks late.
                java.util.Arrays.fill(acc, 0);
                for (var it = voices.iterator(); it.hasNext(); ) {
                    Voice v = it.next();
                    double p = v.pos[0];
                    float[] smp = v.samples;
                    for (int k = 0; k < CHUNK; k++) {
                        int i0 = (int) p;
                        if (i0 >= smp.length - 1) {
                            p = smp.length;
                            break;
                        }
                        float t = (float) (p - i0);
                        acc[k] += (smp[i0] * (1 - t) + smp[i0 + 1] * t) * v.gain;
                        p += v.rate;
                    }
                    v.pos[0] = p;
                    if (p >= smp.length - 1) it.remove();
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

    /** Placing: a deep, soft "thock": a low body tone that drops in pitch, with a short muffled knock on top. */
    private static float[] thud(float pitch, Random r) {
        return voice(pitch, r, 0.15f, 95, 120, 0.018, 0.055, 330, 1.4, 0.022, 1.3, 1500, 0);
    }

    /** Breaking: the same deep knock, a little longer and crumblier (a few extra muffled grains). */
    private static float[] crunch(float pitch, Random r) {
        return voice(pitch, r, 0.2f, 85, 100, 0.025, 0.07, 390, 1.0, 0.04, 1.7, 1900, 4);
    }

    /**
     * One percussive voice: {@code base + sweep·e^(-t/sweepTau)} Hz sine under an {@code bodyTau} decay, plus noise
     * through a band-pass at {@code knockHz} under a {@code knockTau} decay, all low-passed at {@code lowpassHz} and
     * normalised, so it stays warm with no clicky or hissy top end.
     */
    private static float[] voice(float pitch, Random r, float seconds, double base, double sweep, double sweepTau, double bodyTau,
                                 double knockHz, double knockQ, double knockTau, double knockGain, double lowpassHz, int grains) {
        int n = (int) (RATE * seconds / pitch);
        float[] s = new float[n];
        // RBJ band-pass for the knock.
        double w0 = 2 * Math.PI * knockHz * pitch / RATE, alpha = Math.sin(w0) / (2 * knockQ);
        double a0 = 1 + alpha, b0 = alpha / a0, b2 = -alpha / a0, a1 = -2 * Math.cos(w0) / a0, a2 = (1 - alpha) / a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0, phase = 0, lp = 0;
        double lpA = 1 - Math.exp(-2 * Math.PI * lowpassHz / RATE);
        double[] grainAt = new double[grains];
        for (int g = 0; g < grains; g++) grainAt[g] = 0.01 + r.nextDouble() * 0.07;
        double peak = 1e-9;
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double attack = Math.min(1, t / 0.003);
            phase += 2 * Math.PI * (base + sweep * Math.exp(-t / sweepTau)) * pitch / RATE;
            double body = Math.sin(phase) * attack * Math.exp(-t / bodyTau);
            double x = r.nextDouble() * 2 - 1;
            double bp = b0 * x + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = bp;
            double env = Math.exp(-t / knockTau);
            for (double g0 : grainAt) if (t >= g0) env += 0.5 * Math.exp(-(t - g0) / 0.008);
            double v = body + knockGain * bp * 4 * attack * env;
            lp += lpA * (v - lp);
            // Fade the last 8 ms so the tail never clicks.
            double tail = Math.min(1, (n - i) / (RATE * 0.008));
            s[i] = (float) (lp * tail);
            peak = Math.max(peak, Math.abs(s[i]));
        }
        float k = (float) (0.85 / peak);
        for (int i = 0; i < n; i++) s[i] *= k;
        return s;
    }
}
