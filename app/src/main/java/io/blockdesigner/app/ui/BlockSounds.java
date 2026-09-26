package io.blockdesigner.app.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The app's sounds, mixed on one low-latency audio line. Place / break: the bundled {@code sounds/place.wav} and
 * {@code sounds/break.wav} (more variants can be added as place-2.wav, break-2.wav…; each play picks a different one).
 * Every other click plays at a slightly random pitch (like Minecraft's pitch variation), the ones between at the
 * original pitch. Recordings are mixed down to mono, trimmed of silence and levelled on load. If they are missing,
 * simple synthesised thuds stand in; with no audio device the sounds quietly do nothing.
 * <p>
 * Painting: {@code sounds/paint.wav} is split by its loudness into an intro (played once when a stroke starts), a
 * steady middle that loops (crossfaded so the seam doesn't click) while the stroke goes on, and the tail, which plays
 * when the stroke ends. UI sounds (in the spirit of the Extra Sounds mod) are the same place / break recordings,
 * played lower and quieter: the hotbar tick (which buttons and menu items share) and putting a block in the hotbar
 * use the place sound, picking a block up the break sound.
 */
final class BlockSounds {
    private static final float RATE = 44100;
    // 128-frame chunks into a 1024-frame line buffer: a new sound starts within ~23 ms of the click.
    private static final int VARIANTS = 5, CHUNK = 128, BUFFER_FRAMES = 1024;

    private final float[][] place, breaks;
    /** The paint sound's parts (null without a paint.wav): intro, crossfaded loop, and the tail (fading in). */
    private final float[] paintIntro, paintLoop, paintTail;
    // Paint voice state, guarded by voices: 0 off, 1 intro, 2 looping, 3 fading out (the tail is then a normal voice).
    private int paintState, paintPos;
    private float paintGain, paintFade;
    /** Fade used when painting stops and the tail takes over, so the two meet without a click. */
    private static final float PAINT_XFADE = 0.04f;
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
        float[][] paint = splitLoop(loadRaw("paint"));
        paintIntro = paint == null ? null : paint[0];
        paintLoop = paint == null ? null : paint[1];
        paintTail = paint == null ? null : paint[2];
        // Open the audio device now (it can take a few hundred ms), so the first click isn't late.
        Thread warm = new Thread(this::open, "block-sounds-open");
        warm.setDaemon(true);
        warm.start();
    }

    /**
     * Splits a painting recording into intro, loop and tail by its loudness: the loop is the steady middle (where the
     * sound stays above 55% of its loudest), trimmed of silence at both ends. The loop's end is crossfaded into its
     * start so it repeats without a seam, and the whole sound is levelled a little softer than a click, as it plays
     * for as long as the stroke lasts. Null without a recording.
     */
    static float[][] splitLoop(float[][] recordings) {
        if (recordings.length == 0) return null;
        float[] s = recordings[0];
        int win = (int) (RATE * 0.02), windows = s.length / win;
        if (windows < 8) return null;
        double[] env = new double[windows];
        double max = 0;
        for (int w = 0; w < windows; w++) {
            double sum = 0;
            for (int i = w * win; i < (w + 1) * win; i++) sum += s[i] * s[i];
            env[w] = Math.sqrt(sum / win);
            max = Math.max(max, env[w]);
        }
        int first = 0, last = windows - 1;
        while (first < last && env[first] < max * 0.03) first++;
        while (last > first && env[last] < max * 0.03) last--;
        int a = first, b = last;
        while (a < last && env[a] < max * 0.55) a++;
        while (b > a && env[b] < max * 0.55) b--;
        // Too short a steady part to loop well: loop everything that isn't silence.
        if ((b - a) * win < RATE * 0.25) {
            a = first;
            b = last;
        }
        int start = first * win, loopA = a * win, loopB = (b + 1) * win, end = Math.min(s.length, (last + 1) * win);
        int n = loopB - loopA, x = (int) Math.min(RATE * 0.12, n / 4.0);
        float[] loop = new float[n - x];
        for (int i = 0; i < loop.length; i++) {
            if (i < x) {
                // Equal-power crossfade of the loop's last x samples into its first x.
                double t = (double) i / x;
                loop[i] = (float) (s[loopA + i] * Math.sqrt(t) + s[loopA + n - x + i] * Math.sqrt(1 - t));
            } else {
                loop[i] = s[loopA + i];
            }
        }
        float[] intro = java.util.Arrays.copyOfRange(s, start, loopA);
        // The tail carries on from where the loop wraps (sample n - x of the steady part) to the end of the sound.
        float[] tail = java.util.Arrays.copyOfRange(s, loopA + n - x, end);
        int fade = Math.min(tail.length, (int) (RATE * PAINT_XFADE));
        for (int i = 0; i < fade; i++) tail[i] *= (float) i / fade;
        // Level by the loop, which is what's heard most.
        double sum = 0, peak = 1e-6;
        for (float v : loop) {
            sum += v * v;
            peak = Math.max(peak, Math.abs(v));
        }
        for (float v : intro) peak = Math.max(peak, Math.abs(v));
        for (float v : tail) peak = Math.max(peak, Math.abs(v));
        float k = (float) Math.min(0.14 / Math.sqrt(sum / loop.length), 0.9 / peak);
        for (float[] part : new float[][]{intro, loop, tail}) for (int i = 0; i < part.length; i++) part[i] *= k;
        return new float[][]{intro, loop, tail};
    }

    /** The bundled {@code <name>.wav}, {@code <name>-2.wav}… as mono float samples at {@link #RATE}, as recorded. */
    static float[][] loadRaw(String name) {
        List<float[]> out = new ArrayList<>();
        for (int i = 1; i <= 32; i++) {
            var url = BlockSounds.class.getResource("/io/blockdesigner/app/sounds/" + name + (i == 1 ? "" : "-" + i) + ".wav");
            if (url == null) break;
            try (var in = AudioSystem.getAudioInputStream(url)) {
                float[] mono = toMono(in);
                if (mono.length > 0) out.add(mono);
            } catch (Exception e) {
                // unreadable file: skip that variant
            }
        }
        return out.toArray(new float[0][]);
    }

    /** The bundled {@code <name>.wav}, {@code <name>-2.wav}… as mono float samples at {@link #RATE}, levelled. */
    static float[][] loadRecordings(String name) {
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

    // ---- painting ----------------------------------------------------------------------------------------------

    /** A brush stroke started: the intro plays, then the loop repeats until {@link #paintStop()}. */
    void paintStart(double volume) {
        if (volume <= 0 || line == null || paintLoop == null) return;
        synchronized (voices) {
            paintState = paintIntro.length > 0 ? 1 : 2;
            paintPos = 0;
            paintGain = (float) volume;
            paintFade = 1;
            voices.notifyAll();
        }
    }

    /** The stroke ended: the loop fades out as the end of the recording plays. */
    void paintStop() {
        synchronized (voices) {
            if (paintState == 0 || paintState == 3) return;
            paintState = 3;
            if (paintTail.length > 0) voices.add(new Voice(paintTail, paintGain, 1, new double[]{0}));
        }
    }

    // ---- UI ------------------------------------------------------------------------------------------------------

    /** A button, toggle or menu item was clicked: the same tick as the hotbar (its first slot's pitch). */
    void click(double volume) {
        tick(volume, 0);
    }

    /** A block was taken (from the palette, or picked in the world): the break sound, lower and quiet. */
    void pickUp(double volume) {
        ui(breaks, volume * 0.35, 0.85);
    }

    /** A block was put in the hotbar: the place sound, deepest. */
    void putDown(double volume) {
        ui(place, volume * 0.4, 0.72);
    }

    /** The held hotbar slot changed: the place sound, low and soft, rising a little with the slot number. */
    void tick(double volume, int slot) {
        ui(place, volume * 0.3, 0.8 + slot * 0.02);
    }

    /** One of the recordings (a different variant from last time), at {@code rate} (below 1 is lower and longer). */
    private void ui(float[][] set, double volume, double rate) {
        if (volume <= 0 || line == null || set.length == 0) return;
        synchronized (voices) {
            if (voices.size() >= 8) voices.removeFirst();
            voices.add(new Voice(set[random.nextInt(set.length)], (float) volume, rate, new double[]{0}));
            voices.notifyAll();
        }
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
                // The paint loop: intro once, then the loop over and over; on stop it fades as the tail plays.
                if (paintState != 0) {
                    float fadeStep = 1f / (RATE * PAINT_XFADE);
                    for (int k = 0; k < CHUNK && paintState != 0; k++) {
                        float v;
                        if (paintState == 1) {
                            v = paintIntro[paintPos++];
                            if (paintPos >= paintIntro.length) {
                                paintState = 2;
                                paintPos = 0;
                            }
                        } else {
                            v = paintLoop[paintPos++ % paintLoop.length];
                            paintPos %= paintLoop.length;
                        }
                        if (paintState == 3) {
                            paintFade -= fadeStep;
                            if (paintFade <= 0) {
                                paintState = 0;
                                break;
                            }
                        }
                        acc[k] += v * paintGain * paintFade;
                    }
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
