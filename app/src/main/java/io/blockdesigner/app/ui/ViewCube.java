package io.blockdesigner.app.ui;

import io.blockdesigner.render.Camera;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Blender-style view navigation in the viewport corner: a cube that turns with the camera. Click a face for that
 * view (click the face you're already looking at to flip to the opposite side), drag it to orbit. Below it are the
 * current view's name and a perspective / orthographic toggle.
 */
final class ViewCube extends VBox {

    /** The six axis views. Front looks north (-Z) with east to the right, as Blender's front view does. */
    enum View {
        FRONT("Front", 0, 0), BACK("Back", Math.PI, 0), RIGHT("Right", -Math.PI / 2, 0), LEFT("Left", Math.PI / 2, 0),
        TOP("Top", 0, Math.PI / 2), BOTTOM("Bottom", 0, -Math.PI / 2);

        final String label;
        final float yaw, pitch;

        View(String label, double yaw, double pitch) {
            this.label = label;
            this.yaw = (float) yaw;
            this.pitch = (float) pitch;
        }

        View opposite() {
            return switch (this) {
                case FRONT -> BACK;
                case BACK -> FRONT;
                case RIGHT -> LEFT;
                case LEFT -> RIGHT;
                case TOP -> BOTTOM;
                case BOTTOM -> TOP;
            };
        }

        /** The axis view the camera is at, or null for a free ("user") view. */
        static View of(Camera c) {
            for (View v : values()) {
                if (Math.abs(c.pitch() - v.pitch) > 1e-3) continue;
                // Looking straight up or down, the yaw only turns the picture; top and bottom need north up.
                double dy = Math.IEEEremainder(c.yaw() - v.yaw, 2 * Math.PI);
                if (Math.abs(dy) < 1e-3) return v;
            }
            return null;
        }
    }

    /**
     * A cube face: outward normal, and the screen right / up directions when looking straight at it, all world axes.
     */
    private record Face(View view, String label, Vector3f n, Vector3f right, Vector3f up) {
    }

    private static final Face[] FACES = {
            new Face(View.FRONT, "FRONT", new Vector3f(0, 0, 1), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0)),
            new Face(View.BACK, "BACK", new Vector3f(0, 0, -1), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0)),
            new Face(View.RIGHT, "RIGHT", new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0)),
            new Face(View.LEFT, "LEFT", new Vector3f(-1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0)),
            new Face(View.TOP, "TOP", new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1)),
            new Face(View.BOTTOM, "BOTTOM", new Vector3f(0, -1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)),
    };
    private static final double SIZE = 78, HALF = 20;
    private static final Color[] AXIS = {Color.web("#E5484D"), Color.web("#46C46E"), Color.web("#3E9BFF")};

    private final Canvas canvas = new Canvas(SIZE, SIZE);
    private final Label name = new Label();
    private final Button projection = new Button();
    private final Consumer<View> onPick;
    private final BiConsumer<Double, Double> onOrbit;
    private Camera camera;
    private View hot;
    private final List<double[]> polys = new ArrayList<>();
    private final List<View> polyViews = new ArrayList<>();
    private double lastX, lastY, dragged;

    ViewCube(Consumer<View> onPick, BiConsumer<Double, Double> onOrbit, Runnable onToggleProjection) {
        this.onPick = onPick;
        this.onOrbit = onOrbit;
        getStyleClass().add("view-cube");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(2);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setPickOnBounds(false);
        name.getStyleClass().add("view-cube-name");
        name.setMouseTransparent(true);
        projection.getStyleClass().addAll("flat", "small", "view-cube-toggle");
        projection.setFocusTraversable(false);
        projection.setTooltip(Keybinds.tooltip("Perspective / orthographic", "Switch the camera's projection (" + Keybinds.keyOf(Keybinds.Action.VIEW_ORTHO_TOGGLE) + " toggles)",
                Keybinds.Action.PERSPECTIVE, Keybinds.Action.ORTHOGRAPHIC));
        projection.setOnAction(e -> onToggleProjection.run());
        getChildren().addAll(canvas, name, projection);

        canvas.setOnMouseMoved(e -> {
            View h = faceAt(e.getX(), e.getY());
            if (h != hot) {
                hot = h;
                redraw();
            }
            e.consume();
        });
        canvas.setOnMouseExited(e -> {
            hot = null;
            redraw();
        });
        canvas.setOnMousePressed(e -> {
            lastX = e.getScreenX();
            lastY = e.getScreenY();
            dragged = 0;
            e.consume();
        });
        canvas.setOnMouseDragged(e -> {
            double dx = e.getScreenX() - lastX, dy = e.getScreenY() - lastY;
            lastX = e.getScreenX();
            lastY = e.getScreenY();
            dragged += Math.abs(dx) + Math.abs(dy);
            if (dragged > 3) onOrbit.accept(dx, dy);
            e.consume();
        });
        canvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY && dragged <= 3) {
                View v = faceAt(e.getX(), e.getY());
                if (v != null) onPick.accept(v);
            }
            e.consume();
        });
        // Keep clicks on the widget from reaching the viewport underneath.
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
    }

    /** Redraws for the camera's current angles and projection. */
    void update(Camera c) {
        camera = c;
        View v = View.of(c);
        name.setText((v == null ? "User" : v.label) + " · " + (c.orthoActive() ? "Ortho" : "Persp"));
        projection.setText(c.isOrtho() ? "Orthographic" : "Perspective");
        redraw();
    }

    /** World direction → widget screen offset (x right, y down) and depth toward the viewer. */
    private double[] toScreen(Vector3f w) {
        Vector3f r = camera.right(), u = camera.up(), f = camera.forward();
        return new double[]{w.dot(r), -w.dot(u), -w.dot(f)};
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setTransform(new Affine());
        g.clearRect(0, 0, SIZE, SIZE);
        polys.clear();
        polyViews.clear();
        if (camera == null) return;
        double cx = SIZE / 2, cy = SIZE / 2;

        // Soft disc behind the cube, like Blender's navigation gizmo backdrop.
        g.setFill(Color.gray(0.5, hot != null ? 0.22 : 0.12));
        g.fillOval(cx - SIZE / 2 + 2, cy - SIZE / 2 + 2, SIZE - 4, SIZE - 4);

        List<Face> visible = new ArrayList<>();
        for (Face f : FACES) if (toScreen(f.n)[2] > 1e-3) visible.add(f);
        visible.sort(Comparator.comparingDouble(f -> toScreen(f.n)[2]));
        View current = View.of(camera);
        for (Face f : visible) {
            double[] n = toScreen(f.n), r = toScreen(f.right), u = toScreen(f.up);
            double[] xs = new double[4], ys = new double[4];
            int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int k = 0; k < 4; k++) {
                xs[k] = cx + (n[0] + r[0] * corners[k][0] + u[0] * corners[k][1]) * HALF;
                ys[k] = cy + (n[1] + r[1] * corners[k][0] + u[1] * corners[k][1]) * HALF;
            }
            double facing = n[2];
            boolean lit = f.view == hot;
            Color base = lit ? Color.web("#FFD35A") : f.view == current ? Color.web("#7C9CFF") : Color.web("#5A6782");
            g.setFill(base.deriveColor(0, 1, 0.55 + 0.45 * facing, lit ? 0.95 : 0.85));
            g.fillPolygon(xs, ys, 4);
            g.setStroke(Color.gray(1, 0.55));
            g.setLineWidth(1);
            g.strokePolygon(xs, ys, 4);
            polys.add(new double[]{xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], xs[3], ys[3]});
            polyViews.add(f.view);

            // The label lies on the face: map face-local pixels (x right, y down) onto the projected face.
            if (facing > 0.25) {
                double fx = cx + n[0] * HALF, fy = cy + n[1] * HALF;
                g.setTransform(new Affine(r[0], -u[0], fx, r[1], -u[1], fy));
                g.setFill(Color.gray(1, Math.min(1, facing * 1.4)));
                g.setFont(Font.font(null, FontWeight.BOLD, f.label.length() > 5 ? 6.5 : 7.5));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(f.label, 0, 2.6);
                g.setTransform(new Affine());
            }
        }

        // Axis lines from the cube's west-bottom-north corner, coloured X red, Y green, Z blue, pointing positive.
        Vector3f corner = new Vector3f(-1, -1, -1);
        double[] c0 = toScreen(corner);
        Vector3f[] axes = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
        String[] names = {"X", "Y", "Z"};
        g.setFont(Font.font(null, FontWeight.BOLD, 8.5));
        g.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < 3; i++) {
            // As long as the cube's edge (corner to corner is 2 units).
            double[] e = toScreen(new Vector3f(corner).fma(2f, axes[i]));
            double x0 = cx + c0[0] * HALF, y0 = cy + c0[1] * HALF, x1 = cx + e[0] * HALF, y1 = cy + e[1] * HALF;
            g.setStroke(AXIS[i]);
            g.setLineWidth(2);
            g.strokeLine(x0, y0, x1, y1);
            g.setFill(AXIS[i]);
            g.fillText(names[i], x1 + (x1 - x0) * 0.08, y1 + (y1 - y0) * 0.08 + 3);
        }
    }

    private View faceAt(double x, double y) {
        // Front-most face first (the list is drawn back to front).
        for (int i = polys.size() - 1; i >= 0; i--) if (inside(polys.get(i), x, y)) return polyViews.get(i);
        return null;
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
