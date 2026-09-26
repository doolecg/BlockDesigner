package io.blockdesigner.app.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Tool glyphs Feather doesn't have, drawn in its style (2px rounded strokes on a 24px grid) so they sit with the other
 * tool icons. The stroke colour comes from CSS ({@code .tool-glyph}), so selected buttons turn them white.
 */
final class ToolIcons {
    private ToolIcons() {
    }

    /** A paint brush tilted like one in the hand: handle, metal ferrule, and bristles tapering to a point at the bottom left. */
    static Node brush(double size) {
        return glyph(size, 45,
                "M12 1 V8",
                "M9 8 H15 V11.5 H9 Z",
                "M9 11.5 C9 15.5 8.5 18 9.7 20.6 C10.2 21.7 11.1 23 12 23 C12.9 23 13.8 21.7 14.3 20.6 C15.5 18 15 15.5 15 11.5");
    }

    /** Build mode: a block drawn as a little cube, with a hammer coming down on it from the top left. */
    static Node build(double size) {
        return glyph(size, 0,
                // the cube: top face, then the two side faces
                "M13 12.5 L19 15.5 L13 18.5 L7 15.5 Z",
                "M7 15.5 V20.5 L13 23.5 L19 20.5 V15.5",
                "M13 18.5 V23.5",
                // the hammer: head across the handle, handle running up to the right
                "M4 6 L6.5 3.5 L11 8 L8.5 10.5 Z",
                "M8.75 5.75 L13.5 1");
    }

    /** An eraser:a tilted block with its rubber end split off, resting on a line. */
    static Node eraser(double size) {
        return glyph(size, 0,
                "M8.5 20.5 L3.5 15.5 L13.5 5.5 L20 12 L11.5 20.5 Z",
                "M8.5 10.5 L15 17",
                "M11.5 20.5 H20.5");
    }

    private static Node glyph(double size, double rotate, String... paths) {
        Group g = new Group();
        for (String d : paths) {
            SVGPath p = new SVGPath();
            p.setContent(d);
            p.setFill(null);
            p.setStrokeWidth(2);
            p.setStrokeLineCap(StrokeLineCap.ROUND);
            p.setStrokeLineJoin(StrokeLineJoin.ROUND);
            p.getStyleClass().add("tool-glyph");
            g.getChildren().add(p);
        }
        // A transparent 24px frame keeps the rotation about the grid's centre, whatever the strokes cover.
        javafx.scene.shape.Rectangle frame = new javafx.scene.shape.Rectangle(24, 24, javafx.scene.paint.Color.TRANSPARENT);
        g.getChildren().addFirst(frame);
        g.setRotate(rotate);
        // Feather's glyphs fill most of their box; these are drawn a little small, so they are scaled up to match.
        double k = size / 19;
        g.setScaleX(k);
        g.setScaleY(k);
        // Keep the icon's footprint the requested size, whatever the scaled strokes' bounds are.
        StackPane box = new StackPane(new Group(g));
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }
}
