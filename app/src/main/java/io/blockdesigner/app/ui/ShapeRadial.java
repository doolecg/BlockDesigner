package io.blockdesigner.app.ui;

import io.blockdesigner.core.place.ShapeTool.Shape;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The build-shape wheel (hold Alt in Build mode): one sector per shape around the mouse. Moving the mouse towards a
 * sector highlights it and releasing Alt picks it; the middle keeps the current shape. Sectors can also be clicked.
 */
final class ShapeRadial extends Pane {
    private static final double OUTER = 176, INNER = 62, ICON_R = 124;

    private final Shape[] shapes = Shape.values();
    private final List<Path> sectors = new ArrayList<>();
    private final Group wheel = new Group();
    private final Label title = new Label(), sub = new Label();
    private final Consumer<Shape> onPick;
    private double cx, cy, px, py;
    private int hot = -1;
    private Shape current = Shape.SINGLE;

    ShapeRadial(Consumer<Shape> onPick) {
        this.onPick = onPick;
        getStyleClass().add("shape-radial");
        setPickOnBounds(false);
        setVisible(false);
        int n = shapes.length;
        double step = 360.0 / n;
        for (int i = 0; i < n; i++) {
            double mid = -90 + i * step;
            Path p = sector(mid - step / 2 + 0.8, mid + step / 2 - 0.8);
            p.getStyleClass().add("shape-radial-sector");
            int idx = i;
            p.setOnMouseEntered(e -> setHot(idx));
            p.setOnMouseClicked(e -> {
                e.consume();
                onPick.accept(shapes[idx]);
            });
            sectors.add(p);
            wheel.getChildren().add(p);
        }
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(-90 + i * step);
            Canvas icon = icon(shapes[i]);
            Label name = new Label(shortName(shapes[i]));
            name.getStyleClass().add("shape-radial-label");
            VBox v = new VBox(1, icon, name);
            v.setAlignment(Pos.CENTER);
            v.setMouseTransparent(true);
            v.setPrefWidth(84);
            v.setLayoutX(Math.cos(a) * ICON_R - 42);
            v.setLayoutY(Math.sin(a) * ICON_R - 28);
            wheel.getChildren().add(v);
        }
        Circle hub = new Circle(INNER - 4);
        hub.getStyleClass().add("shape-radial-hub");
        hub.setMouseTransparent(true);
        title.getStyleClass().add("shape-radial-title");
        sub.getStyleClass().add("shape-radial-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(104);
        sub.setAlignment(Pos.CENTER);
        sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox center = new VBox(2, title, sub);
        center.setAlignment(Pos.CENTER);
        center.setMouseTransparent(true);
        center.setPrefSize(112, 90);
        center.setLayoutX(-56);
        center.setLayoutY(-45);
        wheel.getChildren().addAll(hub, center);
        getChildren().add(wheel);
    }

    private static String shortName(Shape s) {
        return s == Shape.SINGLE ? "None" : s.label;
    }

    private static Path sector(double a0, double a1) {
        double r0 = Math.toRadians(a0), r1 = Math.toRadians(a1);
        Path p = new Path();
        p.getElements().add(new MoveTo(Math.cos(r0) * OUTER, Math.sin(r0) * OUTER));
        ArcTo outer = new ArcTo(OUTER, OUTER, 0, Math.cos(r1) * OUTER, Math.sin(r1) * OUTER, false, true);
        p.getElements().add(outer);
        p.getElements().add(new LineTo(Math.cos(r1) * INNER, Math.sin(r1) * INNER));
        p.getElements().add(new ArcTo(INNER, INNER, 0, Math.cos(r0) * INNER, Math.sin(r0) * INNER, false, false));
        p.getElements().add(new ClosePath());
        return p;
    }

    /** Opens centred on {@code (x, y)} in this pane's coordinates (kept inside the view). */
    void open(double x, double y, Shape currentShape) {
        current = currentShape;
        cx = Math.clamp(x, OUTER + 4, Math.max(OUTER + 4, getWidth() - OUTER - 4));
        cy = Math.clamp(y, OUTER + 4, Math.max(OUTER + 4, getHeight() - OUTER - 4));
        wheel.setLayoutX(cx);
        wheel.setLayoutY(cy);
        px = cx;
        py = cy;
        for (int i = 0; i < shapes.length; i++) sectors.get(i).pseudoClassStateChanged(CURRENT, shapes[i] == current);
        setHot(-1);
        setVisible(true);
        toFront();
    }

    void close() {
        setVisible(false);
        hot = -1;
    }

    boolean isOpen() {
        return isVisible();
    }

    /** Points at an absolute position (normal camera: the mouse). */
    void pointAt(double x, double y) {
        px = x;
        py = y;
        update();
    }

    /** Moves the pointer by a delta (flying: the mouse is held in place, so only movement counts). */
    void nudge(double dx, double dy) {
        double ox = px - cx + dx, oy = py - cy + dy, len = Math.hypot(ox, oy);
        // Keep the virtual pointer within the wheel so turning back works straight away.
        if (len > OUTER) {
            ox *= OUTER / len;
            oy *= OUTER / len;
        }
        px = cx + ox;
        py = cy + oy;
        update();
    }

    private void update() {
        double dx = px - cx, dy = py - cy;
        if (Math.hypot(dx, dy) < INNER * 0.6) {
            setHot(-1);
            return;
        }
        double deg = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        double step = 360.0 / shapes.length;
        setHot(Math.floorMod((int) Math.round(deg / step), shapes.length));
    }

    private void setHot(int i) {
        hot = i;
        for (int k = 0; k < sectors.size(); k++) sectors.get(k).pseudoClassStateChanged(HOT, k == i);
        Shape s = i < 0 ? current : shapes[i];
        title.setText(i < 0 ? "Keep " + shortName(current) : shortName(s));
        sub.setText(i < 0 ? "Release Alt to close" : s.description);
    }

    /** The highlighted shape, or null for "keep the current one". */
    Shape highlighted() {
        return hot < 0 ? null : shapes[hot];
    }

    private static final javafx.css.PseudoClass HOT = javafx.css.PseudoClass.getPseudoClass("hot");
    private static final javafx.css.PseudoClass CURRENT = javafx.css.PseudoClass.getPseudoClass("current");

    // ---- icons ----------------------------------------------------------------------------------------------

    /** A small isometric sketch of the shape. */
    static Canvas icon(Shape s) {
        Canvas c = new Canvas(34, 30);
        GraphicsContext g = c.getGraphicsContext2D();
        Color top = Color.web("#E9EDF5"), left = Color.web("#AEB8CC"), right = Color.web("#7F8BA3");
        switch (s) {
            case SINGLE -> cube(g, 17, 9, 7, top, left, right);
            case LINE -> {
                for (int i = 0; i < 4; i++) cube(g, 5 + i * 7, 15 - i * 3.5, 4, top, left, right);
            }
            case WALL -> {
                for (int y = 2; y >= 0; y--) for (int i = 0; i < 3; i++) cube(g, 9 + i * 7, 8 + y * 7 - i * 3.5, 4, top, left, right);
            }
            case FLOOR -> {
                for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) cube(g, 17 + (x - z) * 6, 8 + (x + z) * 3.2, 4, top, left, right);
            }
            case BOX -> cube(g, 17, 5, 11, top, left, right);
            case HOLLOW_BOX -> {
                cube(g, 17, 5, 11, top, left, right);
                g.setFill(Color.web("#3a4458"));
                g.fillRect(12, 15, 5, 6);
                g.fillRect(21, 13, 5, 6);
            }
            case WALLS -> {
                g.setFill(left);
                g.fillPolygon(new double[]{6, 17, 17, 6}, new double[]{9, 15, 26, 20}, 4);
                g.setFill(right);
                g.fillPolygon(new double[]{17, 28, 28, 17}, new double[]{15, 9, 20, 26}, 4);
                g.setStroke(top);
                g.setLineWidth(1.4);
                g.strokePolygon(new double[]{6, 17, 28, 17}, new double[]{9, 3, 9, 15}, 4);
            }
            case CIRCLE -> {
                g.setFill(top);
                g.fillOval(4, 9, 26, 14);
                g.setStroke(right);
                g.strokeOval(4, 9, 26, 14);
            }
            case RING -> {
                g.setStroke(top);
                g.setLineWidth(3.2);
                g.strokeOval(5, 9, 24, 13);
            }
            case CYLINDER -> {
                g.setFill(left);
                g.fillRect(6, 9, 22, 13);
                g.setFill(right);
                g.fillOval(6, 16, 22, 10);
                g.setFill(Color.web("#3a4458"));
                g.fillOval(6, 4, 22, 10);
                g.setStroke(top);
                g.setLineWidth(1.6);
                g.strokeOval(6, 4, 22, 10);
            }
            case SPHERE -> {
                g.setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.35, 0.3, 0.7, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                        new javafx.scene.paint.Stop(0, top), new javafx.scene.paint.Stop(1, right)));
                g.fillOval(5, 2, 25, 25);
            }
            case DOME -> {
                g.setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.35, 0.5, 0.8, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                        new javafx.scene.paint.Stop(0, top), new javafx.scene.paint.Stop(1, right)));
                g.fillArc(4, 5, 26, 36, 0, 180, javafx.scene.shape.ArcType.ROUND);
                g.setFill(left);
                g.fillRect(4, 22, 26, 2);
            }
            case PYRAMID -> {
                for (int i = 0; i < 4; i++) {
                    double w = 26 - i * 7;
                    g.setFill(i % 2 == 0 ? left : top);
                    g.fillRect(17 - w / 2, 23 - i * 6, w, 5);
                }
            }
        }
        return c;
    }

    /** An isometric cube with its top corner at (x, y) and edge length e. */
    private static void cube(GraphicsContext g, double x, double y, double e, Color top, Color left, Color right) {
        double h = e * 0.55;
        g.setFill(top);
        g.fillPolygon(new double[]{x, x + e, x, x - e}, new double[]{y, y + h, y + 2 * h, y + h}, 4);
        g.setFill(left);
        g.fillPolygon(new double[]{x - e, x, x, x - e}, new double[]{y + h, y + 2 * h, y + 2 * h + e, y + h + e}, 4);
        g.setFill(right);
        g.fillPolygon(new double[]{x, x + e, x + e, x}, new double[]{y + 2 * h, y + h, y + h + e, y + 2 * h + e}, 4);
    }
}
