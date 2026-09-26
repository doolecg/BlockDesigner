package io.blockdesigner.app.ui;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.util.List;

/**
 * The key hints in the viewport's bottom-right corner, like a game's control prompts: a keycap (or a mouse with the
 * button lit) beside what it does right now. The rows follow what you are doing (the tool, flying, dragging a shape,
 * placing an import). Shift+F1 or the viewport settings turn it off.
 */
final class KeyHints extends VBox {
    /** One hint: the keys (e.g. "Ctrl", "J"; "LMB", "RMB", "MMB" and "Wheel" draw a mouse) and what they do. */
    record Hint(List<String> keys, String action) {
        static Hint of(String action, String... keys) {
            return new Hint(List.of(keys), action);
        }
    }

    private List<Hint> shown = List.of();

    KeyHints() {
        getStyleClass().add("key-hints");
        setSpacing(4);
        setMouseTransparent(true);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setFillWidth(false);
        setAlignment(Pos.BOTTOM_RIGHT);
    }

    /** Shows these hints (rebuilt only when they change). */
    void show(List<Hint> hints) {
        if (hints.equals(shown)) return;
        shown = hints;
        getChildren().clear();
        for (Hint h : hints) {
            HBox keys = new HBox(3);
            keys.setAlignment(Pos.CENTER_RIGHT);
            for (int i = 0; i < h.keys().size(); i++) {
                if (i > 0) {
                    Label plus = new Label("+");
                    plus.getStyleClass().add("key-hint-plus");
                    keys.getChildren().add(plus);
                }
                keys.getChildren().add(cap(h.keys().get(i)));
            }
            Label text = new Label(h.action());
            text.getStyleClass().add("key-hint-text");
            HBox row = new HBox(8, text, keys);
            row.setAlignment(Pos.CENTER_RIGHT);
            row.getStyleClass().add("key-hint-row");
            getChildren().add(row);
        }
    }

    private static Node cap(String key) {
        return switch (key) {
            case "LMB" -> mouse(-1);
            case "RMB" -> mouse(1);
            case "MMB", "Wheel" -> mouse(0);
            default -> {
                Label l = new Label(key);
                l.getStyleClass().add("keycap");
                yield l;
            }
        };
    }

    /** A little mouse with its left (-1), right (1) or middle (0) button lit. */
    private static Node mouse(int button) {
        Rectangle body = new Rectangle(0, 0, 12, 17);
        body.setArcWidth(10);
        body.setArcHeight(10);
        body.getStyleClass().add("key-mouse");
        SVGPath split = new SVGPath();
        split.setContent("M0 7 H12 M6 0 V7");
        split.getStyleClass().add("key-mouse");
        SVGPath lit = new SVGPath();
        lit.setContent(switch (button) {
            case -1 -> "M6 0.5 H5 C2.5 0.5 0.5 2.5 0.5 5 V7 H6 Z";
            case 1 -> "M6 0.5 H7 C9.5 0.5 11.5 2.5 11.5 5 V7 H6 Z";
            default -> "M4.5 1.5 H7.5 V6.5 H4.5 Z";
        });
        lit.getStyleClass().add("key-mouse-lit");
        Group g = new Group(body, lit, split);
        HBox box = new HBox(g);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("keycap-mouse");
        return box;
    }
}
