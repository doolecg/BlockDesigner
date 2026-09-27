package io.blockdesigner.plugin.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.util.Locale;

/**
 * The icons plugins can put on buttons, rows and empty states: Feather icons (MIT licence, see
 * {@code FEATHER-LICENSE.txt} beside this class), drawn as strokes in the text colour, the same set the app uses. The
 * icon follows its button: white on an accent button, muted in an empty state, a tone with {@code :danger} and so on.
 * Since API 6.
 */
public enum Icon {
    ADD("M12 5L12 19M5 12L19 12"),
    CHECK("M20 6L9 17L4 12"),
    CLOSE("M18 6L6 18M6 6L18 18"),
    COPY(rect(9, 9, 13, 13, 2), "M5 15H4A2 2 0 0 1 2 13V4A2 2 0 0 1 4 2H13A2 2 0 0 1 15 4V5"),
    DOWNLOAD("M21 15V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V15M7 10L12 15L17 10M12 15L12 3"),
    EDIT("M17 3A2.828 2.828 0 1 1 21 7L7.5 20.5L2 22L3.5 16.5L17 3Z"),
    EXTERNAL("M18 13V19A2 2 0 0 1 16 21H5A2 2 0 0 1 3 19V8A2 2 0 0 1 5 6H11M15 3L21 3L21 9M10 14L21 3"),
    FOLDER("M22 19A2 2 0 0 1 20 21H4A2 2 0 0 1 2 19V5A2 2 0 0 1 4 3H9L11 6H20A2 2 0 0 1 22 8Z"),
    IMAGE(rect(3, 3, 18, 18, 2), circle(8.5, 8.5, 1.5), "M21 15L16 10L5 21"),
    INFO(circle(12, 12, 10), "M12 16L12 12M12 8L12.01 8"),
    LINK("M10 13A5 5 0 0 0 17.54 13.54L20.54 10.54A5 5 0 0 0 13.47 3.47L11.75 5.18",
            "M14 11A5 5 0 0 0 6.46 10.46L3.46 13.46A5 5 0 0 0 10.53 20.53L12.24 18.82"),
    PAPERCLIP("M21.44 11.05L12.25 20.24A6 6 0 0 1 3.76 11.75L12.95 2.56A4 4 0 0 1 18.61 8.22L9.41 17.41A2 2 0 0 1 6.58 14.58L15.07 6.1"),
    PLAY("M5 3L19 12L5 21Z"),
    REFRESH("M23 4L23 10L17 10M1 20L1 14L7 14",
            "M3.51 9A9 9 0 0 1 18.36 5.64L23 10M1 14L5.64 18.36A9 9 0 0 0 20.49 15"),
    SAVE("M19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3H16L21 8V19A2 2 0 0 1 19 21Z", "M17 21L17 13L7 13L7 21M7 3L7 8L15 8"),
    SEARCH(circle(11, 11, 8), "M21 21L16.65 16.65"),
    SEND("M22 2L11 13M22 2L15 22L11 13L2 9Z"),
    SETTINGS(circle(12, 12, 3),
            "M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32"
                    + "A1.65 1.65 0 0 0 14.08 20.83V21A2 2 0 1 1 10.08 21V20.91A1.65 1.65 0 0 0 9 19.4"
                    + "A1.65 1.65 0 0 0 7.18 19.73L7.12 19.79A2 2 0 1 1 4.29 16.96L4.35 16.9A1.65 1.65 0 0 0 4.68 15.08"
                    + "A1.65 1.65 0 0 0 3.17 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.6 9"
                    + "A1.65 1.65 0 0 0 4.27 7.18L4.21 7.12A2 2 0 1 1 7.04 4.29L7.1 4.35A1.65 1.65 0 0 0 8.92 4.68H9"
                    + "A1.65 1.65 0 0 0 10 3.17V3A2 2 0 1 1 14 3V3.09A1.65 1.65 0 0 0 15 4.6"
                    + "A1.65 1.65 0 0 0 16.82 4.27L16.88 4.21A2 2 0 1 1 19.71 7.04L19.65 7.1A1.65 1.65 0 0 0 19.32 8.92V9"
                    + "A1.65 1.65 0 0 0 20.83 10H21A2 2 0 1 1 21 14H20.91A1.65 1.65 0 0 0 19.4 15Z"),
    SHUFFLE("M16 3L21 3L21 8M4 20L21 3M21 16L21 21L16 21M15 15L21 21M4 4L9 9"),
    STOP(rect(4, 4, 16, 16, 2)),
    TRASH("M3 6L5 6L21 6M19 6V20A2 2 0 0 1 17 22H7A2 2 0 0 1 5 20V6M8 6V4A2 2 0 0 1 10 2H14A2 2 0 0 1 16 4V6M10 11L10 17M14 11L14 17"),
    UNDO("M1 4L1 10L7 10M3.51 15A9 9 0 1 0 5.64 5.64L1 10"),
    WARNING("M10.29 3.86L1.82 18A2 2 0 0 0 3.53 21H20.47A2 2 0 0 0 22.18 18L13.71 3.86A2 2 0 0 0 10.29 3.86Z",
            "M12 9L12 13M12 17L12.01 17"),
    ERROR(circle(12, 12, 10), "M12 8L12 12M12 16L12.01 16"),
    CHEVRON_DOWN("M6 9L12 15L18 9"),
    CHEVRON_RIGHT("M9 18L15 12L9 6");

    private final String path;

    Icon(String... parts) {
        this.path = String.join(" ", parts);
    }

    /** The icon's SVG path data in a 24×24 box (Feather's grid), drawn as 2-unit round strokes. */
    public String path() {
        return path;
    }

    /** The icon at 16 px. */
    public Node node() {
        return node(16);
    }

    /**
     * The icon {@code size} px square, with the style class {@code bd-icon} (its stroke is {@code bd-icon-path}). Each
     * call makes a new node.
     */
    public Node node(double size) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.getStyleClass().add("bd-icon-path");
        p.setFill(Color.TRANSPARENT);
        p.setStroke(Color.GRAY);
        p.setStrokeWidth(2);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        // A transparent 24×24 box keeps every icon centred the same way, whatever part of the grid it uses.
        Rectangle box = new Rectangle(24, 24, Color.TRANSPARENT);
        box.setMouseTransparent(true);
        Group art = new Group(box, p);
        double k = size / 24;
        art.getTransforms().add(new Scale(k, k, 0, 0));
        StackPane holder = new StackPane(new Group(art));
        holder.getStyleClass().add("bd-icon");
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        holder.setMouseTransparent(true);
        return holder;
    }

    private static String circle(double cx, double cy, double r) {
        return String.format(Locale.ROOT, "M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ",
                n(cx - r), n(cy), n(r), n(r), n(cx + r), n(cy), n(r), n(r), n(cx - r), n(cy));
    }

    private static String rect(double x, double y, double w, double h, double r) {
        return String.format(Locale.ROOT, "M%s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sH%sA%s %s 0 0 1 %s %sV%sA%s %s 0 0 1 %s %sZ",
                n(x + r), n(y), n(x + w - r), n(r), n(r), n(x + w), n(y + r), n(y + h - r), n(r), n(r), n(x + w - r), n(y + h),
                n(x + r), n(r), n(r), n(x), n(y + h - r), n(y + r), n(r), n(r), n(x + r), n(y));
    }

    private static String n(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
