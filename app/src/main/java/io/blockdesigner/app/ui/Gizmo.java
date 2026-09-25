package io.blockdesigner.app.ui;

import io.blockdesigner.render.Camera;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Blender-style transform handles drawn over the viewport: Move shows axis arrows, plane squares and a free-move centre,
 * Rotate shows a ring per axis. Drawn in screen space at a constant size, so they stay easy to grab at any zoom. This
 * class only draws and hit-tests; {@link ViewportPane} does the dragging.
 */
final class Gizmo extends Canvas {
    enum Mode { MOVE, ROTATE }

    enum Kind { AXIS, PLANE, FREE, RING }

    /** A grabbable part; {@code axis} is 0/1/2 for X/Y/Z (a plane's normal, a ring's rotation axis). */
    record Handle(Kind kind, int axis) {
    }

    static final Vector3f[] AXES = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
    private static final Color[] COLORS = {Color.web("#E5484D"), Color.web("#46C46E"), Color.web("#3E9BFF")};
    private static final Color HOT = Color.web("#FFD35A");
    private static final double SIZE_PX = 110, GRAB_PX = 9, RING_SEGMENTS = 72;
    private static final String[] NAMES = {"X", "Y", "Z"};

    private Mode mode;
    private Vector3f pivot;
    private Matrix4f vp;
    private Vector3f eye;
    private float size;
    private double w, h;
    // Screen geometry from the last draw, for hit tests: axis base/tip, plane quads, ring polylines.
    private final double[][] axisSeg = new double[3][];
    private final double[][] planeQuad = new double[3][];
    private final double[][] ring = new double[3][];
    private double[] center;

    Gizmo() {
        setMouseTransparent(true);
        setManaged(false);
    }

    static String axisName(int axis) {
        return NAMES[axis];
    }

    /** World length of the handles (constant on screen). */
    float size() {
        return size;
    }

    /** Screen position of the pivot, or null when hidden. */
    double[] center() {
        return center;
    }

    /** Redraws; {@code mode} or {@code pivot} null hides the gizmo. */
    void update(Mode mode, Vector3f pivot, Camera camera, double width, double height, Handle hot, Handle active) {
        this.mode = mode;
        this.pivot = pivot;
        w = width;
        h = height;
        if (getWidth() != width) setWidth(width);
        if (getHeight() != height) setHeight(height);
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        center = null;
        for (int i = 0; i < 3; i++) axisSeg[i] = planeQuad[i] = ring[i] = null;
        if (mode == null || pivot == null || width < 2 || height < 2) return;

        vp = camera.viewProjection((float) (width / height));
        eye = camera.viewEye();
        if (!camera.orthoActive() && new Vector3f(pivot).sub(eye).dot(camera.forward()) < 0.3f) return;
        size = (float) (SIZE_PX * camera.unitsPerPixel(pivot, (float) height));
        center = project(pivot);
        if (center == null) return;

        g.setLineCap(StrokeLineCap.ROUND);
        if (mode == Mode.MOVE) drawMove(g, hot, active);
        else drawRotate(g, hot, active);
    }

    private void drawMove(GraphicsContext g, Handle hot, Handle active) {
        // Planes first so the arrows sit on top.
        for (int n = 0; n < 3; n++) {
            int u = (n + 1) % 3, v = (n + 2) % 3;
            float a = 0.26f * size, b = 0.46f * size;
            double[] q = new double[8];
            float[][] corners = {{a, a}, {b, a}, {b, b}, {a, b}};
            boolean ok = true;
            for (int k = 0; k < 4; k++) {
                double[] s = project(new Vector3f(pivot).fma(corners[k][0], AXES[u]).fma(corners[k][1], AXES[v]));
                if (s == null) {
                    ok = false;
                    break;
                }
                q[k * 2] = s[0];
                q[k * 2 + 1] = s[1];
            }
            if (!ok || Math.abs(area(q)) < 60) continue;
            planeQuad[n] = q;
            boolean lit = is(hot, Kind.PLANE, n) || is(active, Kind.PLANE, n);
            Color c = lit ? HOT : COLORS[n];
            g.setFill(c.deriveColor(0, 1, 1, lit ? 0.55 : 0.28));
            g.setStroke(c);
            g.setLineWidth(1.5);
            double[] xs = {q[0], q[2], q[4], q[6]}, ys = {q[1], q[3], q[5], q[7]};
            g.fillPolygon(xs, ys, 4);
            g.strokePolygon(xs, ys, 4);
        }
        for (int i = 0; i < 3; i++) {
            double[] base = project(new Vector3f(pivot).fma(0.2f * size, AXES[i]));
            double[] tip = project(new Vector3f(pivot).fma(size, AXES[i]));
            if (base == null || tip == null) continue;
            double dx = tip[0] - center[0], dy = tip[1] - center[1], len = Math.hypot(dx, dy);
            // Looking straight down an axis: its arrow collapses, so hide it (as Blender does).
            if (len < 18) continue;
            axisSeg[i] = new double[]{base[0], base[1], tip[0], tip[1]};
            boolean lit = is(hot, Kind.AXIS, i) || is(active, Kind.AXIS, i);
            Color c = lit ? HOT : COLORS[i];
            g.setStroke(c);
            g.setLineWidth(lit ? 4 : 3);
            g.strokeLine(base[0], base[1], tip[0], tip[1]);
            double ux = dx / len, uy = dy / len, px = -uy, py = ux;
            g.setFill(c);
            g.fillPolygon(new double[]{tip[0] + ux * 12, tip[0] - ux * 2 + px * 6, tip[0] - ux * 2 - px * 6},
                    new double[]{tip[1] + uy * 12, tip[1] - uy * 2 + py * 6, tip[1] - uy * 2 - py * 6}, 3);
            g.setFont(javafx.scene.text.Font.font(null, javafx.scene.text.FontWeight.BOLD, 11));
            g.fillText(NAMES[i], tip[0] + ux * 16 - 4, tip[1] + uy * 16 + 4);
        }
        boolean lit = is(hot, Kind.FREE, 0) || is(active, Kind.FREE, 0);
        g.setStroke(lit ? HOT : Color.gray(1, 0.9));
        g.setLineWidth(lit ? 3 : 2);
        g.strokeOval(center[0] - 8, center[1] - 8, 16, 16);
        if (lit) {
            g.setFill(HOT.deriveColor(0, 1, 1, 0.4));
            g.fillOval(center[0] - 8, center[1] - 8, 16, 16);
        }
    }

    private void drawRotate(GraphicsContext g, Handle hot, Handle active) {
        Vector3f toEye = new Vector3f(eye).sub(pivot);
        float r = 0.85f * size;
        for (int i = 0; i < 3; i++) {
            Vector3f u = AXES[(i + 1) % 3], v = AXES[(i + 2) % 3];
            int n = (int) RING_SEGMENTS;
            double[] pts = new double[(n + 1) * 2];
            boolean[] front = new boolean[n + 1];
            boolean ok = true;
            for (int k = 0; k <= n; k++) {
                double t = 2 * Math.PI * k / n;
                Vector3f off = new Vector3f(u).mul((float) Math.cos(t) * r).fma((float) Math.sin(t) * r, v);
                double[] s = project(new Vector3f(pivot).add(off));
                if (s == null) {
                    ok = false;
                    break;
                }
                pts[k * 2] = s[0];
                pts[k * 2 + 1] = s[1];
                front[k] = off.dot(toEye) >= -0.05f * r * toEye.length();
            }
            if (!ok) continue;
            ring[i] = pts;
            boolean lit = is(hot, Kind.RING, i) || is(active, Kind.RING, i);
            Color c = lit ? HOT : COLORS[i];
            for (int k = 0; k < n; k++) {
                // The far half of each ring is dimmed so it reads as a sphere.
                boolean f = front[k] && front[k + 1];
                g.setStroke(f || lit ? c : c.deriveColor(0, 1, 1, 0.3));
                g.setLineWidth(lit ? 4.5 : f ? 3 : 2);
                g.strokeLine(pts[k * 2], pts[k * 2 + 1], pts[k * 2 + 2], pts[k * 2 + 3]);
            }
        }
        g.setFill(Color.gray(1, 0.85));
        g.fillOval(center[0] - 3, center[1] - 3, 6, 6);
    }

    /** The handle under a screen point, or null. */
    Handle hit(double x, double y) {
        if (center == null || mode == null) return null;
        if (mode == Mode.MOVE) {
            if (Math.hypot(x - center[0], y - center[1]) <= 11) return new Handle(Kind.FREE, 0);
            int best = -1;
            double bestD = GRAB_PX;
            for (int i = 0; i < 3; i++) {
                double[] s = axisSeg[i];
                if (s == null) continue;
                // Reach a little past the tip so the arrowhead counts.
                double dx = s[2] - s[0], dy = s[3] - s[1], len = Math.hypot(dx, dy);
                double d = segDist(x, y, s[0], s[1], s[2] + dx / len * 12, s[3] + dy / len * 12);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best >= 0) return new Handle(Kind.AXIS, best);
            for (int n = 0; n < 3; n++) if (planeQuad[n] != null && inside(planeQuad[n], x, y)) return new Handle(Kind.PLANE, n);
            return null;
        }
        int best = -1;
        double bestD = GRAB_PX;
        for (int i = 0; i < 3; i++) {
            double[] p = ring[i];
            if (p == null) continue;
            for (int k = 0; k + 3 < p.length; k += 2) {
                double d = segDist(x, y, p[k], p[k + 1], p[k + 2], p[k + 3]);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
        }
        return best >= 0 ? new Handle(Kind.RING, best) : null;
    }

    private double[] project(Vector3f p) {
        Vector4f v = new Vector4f(p, 1).mul(vp);
        if (v.w <= 1e-4f) return null;
        return new double[]{(v.x / v.w * 0.5 + 0.5) * w, (0.5 - v.y / v.w * 0.5) * h};
    }

    private static boolean is(Handle h, Kind k, int axis) {
        return h != null && h.kind() == k && h.axis() == axis;
    }

    private static double segDist(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.clamp(((px - x0) * dx + (py - y0) * dy) / l2, 0, 1);
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    private static double area(double[] q) {
        double a = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            a += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1];
        }
        return a / 2;
    }

    private static boolean inside(double[] q, double x, double y) {
        boolean in = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            double xi = q[i * 2], yi = q[i * 2 + 1], xj = q[j * 2], yj = q[j * 2 + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }
}
