package io.blockdesigner.app.ui;

import io.blockdesigner.render.Camera;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Minecraft-style break particles: little chips in the broken block's colours that pop out, fall and fade. Simulated
 * in world space and drawn on an overlay, so they follow the camera like the real thing.
 */
final class BreakParticles extends Canvas {
    private static final int COUNT = 18;
    private static final float GRAVITY = 14, SIZE = 0.13f;

    private static final class Chip {
        final Vector3f pos, vel;
        final Color color;
        final float life;
        float age;

        Chip(Vector3f pos, Vector3f vel, Color color, float life) {
            this.pos = pos;
            this.vel = vel;
            this.color = color;
            this.life = life;
        }
    }

    private final List<Chip> chips = new ArrayList<>();
    private final Random random = new Random();

    BreakParticles() {
        setMouseTransparent(true);
        setManaged(false);
    }

    boolean active() {
        return !chips.isEmpty();
    }

    /** Bursts chips out of the block cell at {@code (x, y, z)}, coloured from its icon (grey without one). */
    void burst(int x, int y, int z, Image icon) {
        List<Color> palette = colours(icon);
        for (int i = 0; i < COUNT; i++) {
            Vector3f p = new Vector3f(x + 0.15f + random.nextFloat() * 0.7f, y + 0.15f + random.nextFloat() * 0.7f,
                    z + 0.15f + random.nextFloat() * 0.7f);
            Vector3f v = new Vector3f(p).sub(x + 0.5f, y + 0.5f, z + 0.5f).mul(3.2f).add(0, 1.6f + random.nextFloat() * 1.4f, 0);
            chips.add(new Chip(p, v, palette.get(random.nextInt(palette.size())), 0.45f + random.nextFloat() * 0.45f));
        }
    }

    /** Advances the simulation by {@code dt} seconds and redraws. */
    void step(double dt, Camera camera, double w, double h) {
        if (getWidth() != w) setWidth(w);
        if (getHeight() != h) setHeight(h);
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        if (chips.isEmpty() || w < 2 || h < 2) return;
        float fdt = (float) dt;
        for (var it = chips.iterator(); it.hasNext(); ) {
            Chip c = it.next();
            c.age += fdt;
            if (c.age >= c.life) {
                it.remove();
                continue;
            }
            c.vel.y -= GRAVITY * fdt;
            c.vel.mul((float) Math.pow(0.4, fdt));
            c.pos.fma(fdt, c.vel);
        }
        Matrix4f vp = camera.viewProjection((float) (w / h));
        Vector4f v = new Vector4f();
        for (Chip c : chips) {
            v.set(c.pos, 1).mul(vp);
            if (v.w <= 1e-4f) continue;
            double sx = (v.x / v.w * 0.5 + 0.5) * w, sy = (0.5 - v.y / v.w * 0.5) * h;
            double px = Math.clamp(SIZE / camera.unitsPerPixel(c.pos, (float) h), 1.5, 26);
            double fade = Math.min(1, (c.life - c.age) / (c.life * 0.35));
            g.setGlobalAlpha(fade);
            g.setFill(c.color);
            g.fillRect(sx - px / 2, sy - px / 2, px, px);
        }
        g.setGlobalAlpha(1);
    }

    /** A few opaque pixels from the icon, as the chip colours. */
    private List<Color> colours(Image icon) {
        List<Color> out = new ArrayList<>();
        PixelReader r = icon == null ? null : icon.getPixelReader();
        if (r != null) {
            int w = (int) icon.getWidth(), h = (int) icon.getHeight();
            for (int tries = 0; tries < 200 && out.size() < 12; tries++) {
                Color c = r.getColor(random.nextInt(w), random.nextInt(h));
                if (c.getOpacity() > 0.8) out.add(Color.color(c.getRed(), c.getGreen(), c.getBlue()));
            }
        }
        if (out.isEmpty()) out.add(Color.gray(0.55));
        return out;
    }
}
