package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.core.edit.Sculpt;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/**
 * The Paint brush / Eraser options bar, along the top of the viewport like a sculpting or painting app's tool header
 * (Blender, Photoshop): the tool as a coloured mode pill, then labelled controls for the mode, size, strength and
 * shape, each showing its value, and a button for the full settings. Keys for each are in the tooltips. It stays one
 * compact row: the sliders give way before any text does.
 */
final class BrushBar extends HBox {
    static final int MAX_SIZE = 16;

    private final Settings settings;
    private final Runnable changed;
    private final Label title = new Label();
    private final MenuButton mode = new MenuButton();
    private final Node modeGroup;
    private final Slider size = new Slider(1, MAX_SIZE, 1), strength = new Slider(1, 5, 2);
    private final Label sizeValue = new Label(), strengthValue = new Label();
    private final ToggleButton sphere = new ToggleButton("Sphere"), cube = new ToggleButton("Cube");
    private Consumer<Sculpt.Mode> pickMode = m -> {
    };
    private boolean eraser, syncing;

    /** @param openPopup opens the full brush settings (also Shift+right-click in the viewport) */
    BrushBar(Settings settings, Runnable changed, Runnable openPopup) {
        this.settings = settings;
        this.changed = changed;
        getStyleClass().addAll("brush-bar", "brush-options");
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);
        setMaxHeight(USE_PREF_SIZE);

        // The tool itself, as the viewport's mode pill (it stands in for the mode badge while this bar shows).
        title.getStyleClass().addAll("mode-badge", "mode-edit");
        title.setMinWidth(USE_PREF_SIZE);

        // Mode: every sculpt mode with its key; the eraser has only one, so the group hides for it.
        mode.getStyleClass().addAll("small", "brush-mode-button");
        mode.setFocusTraversable(false);
        for (Sculpt.Mode m : Sculpt.Mode.values()) {
            if (m == Sculpt.Mode.ERASE) continue;
            MenuItem item = new MenuItem(m.label);
            item.setUserData(m);
            item.setOnAction(e -> pickMode.accept(m));
            mode.getItems().add(item);
        }
        mode.setOnShowing(e -> {
            // Show each mode's current key (they can be rebound).
            for (MenuItem item : mode.getItems()) {
                Sculpt.Mode m = (Sculpt.Mode) item.getUserData();
                String k = Keybinds.keyOf(Keybinds.Action.values()[Keybinds.Action.BRUSH_MODE_1.ordinal() + m.ordinal()]);
                item.setText(m.label + (k.isEmpty() ? "" : "   " + k));
            }
        });
        mode.setTooltip(Keybinds.tooltip("Brush mode", "What each stroke does: draw, smooth, raise, flatten…",
                Keybinds.Action.BRUSH_MODE_1));
        mode.setMinWidth(USE_PREF_SIZE);
        modeGroup = group("Mode", mode);

        // Size: − slider + and the brush's size in blocks.
        Button minus = new Button("−"), plus = new Button("+");
        for (Button b : new Button[]{minus, plus}) {
            b.getStyleClass().addAll("flat", "small", "brush-step");
            b.setFocusTraversable(false);
        }
        minus.setTooltip(Keybinds.tooltip("Smaller brush", null, Keybinds.Action.BRUSH_SMALLER));
        plus.setTooltip(Keybinds.tooltip("Bigger brush", null, Keybinds.Action.BRUSH_BIGGER));
        minus.setOnAction(e -> step(-1));
        plus.setOnAction(e -> step(1));
        slider(size, 110);
        size.setTooltip(Keybinds.tooltip("Brush size", "How far the brush reaches",
                Keybinds.Action.BRUSH_SMALLER, Keybinds.Action.BRUSH_BIGGER));
        size.valueProperty().addListener((o, a, b) -> {
            int v = (int) Math.round(b.doubleValue());
            if (syncing || v == settings.brushSize) return;
            settings.brushSize = v;
            refresh();
            changed.run();
        });
        sizeValue.getStyleClass().add("brush-value");
        sizeValue.setMinWidth(USE_PREF_SIZE);
        Node sizeGroup = group("Size", new HBox(2, minus, size, plus, sizeValue));

        // Strength 1–5.
        slider(strength, 70);
        strength.setTooltip(Keybinds.tooltip("Strength", "How strongly each stamp changes the terrain",
                Keybinds.Action.BRUSH_WEAKER, Keybinds.Action.BRUSH_STRONGER));
        strength.valueProperty().addListener((o, a, b) -> {
            int v = (int) Math.round(b.doubleValue());
            if (syncing || v == settings.brushStrength) return;
            settings.brushStrength = v;
            refresh();
            changed.run();
        });
        strengthValue.getStyleClass().add("brush-value");
        strengthValue.setMinWidth(USE_PREF_SIZE);
        Node strengthGroup = group("Strength", new HBox(6, strength, strengthValue));

        // Shape: two segments.
        ToggleGroup shapes = new ToggleGroup();
        sphere.getStyleClass().add("left-pill");
        cube.getStyleClass().add("right-pill");
        for (ToggleButton t : new ToggleButton[]{sphere, cube}) {
            t.getStyleClass().add("small");
            t.setToggleGroup(shapes);
            t.setFocusTraversable(false);
        }
        sphere.setOnAction(e -> setShape(false));
        cube.setOnAction(e -> setShape(true));
        Node shapeGroup = group("Shape", new HBox(0, sphere, cube));

        Button more = new Button(null, new FontIcon(Feather.SLIDERS));
        more.getStyleClass().addAll("flat", "small");
        more.setFocusTraversable(false);
        more.setTooltip(Keybinds.tooltip("All brush settings", "Every mode with its letter key (also Shift+right-click in the viewport)"));
        more.setOnAction(e -> openPopup.run());

        getChildren().addAll(title, modeGroup, divider(), sizeGroup, strengthGroup, divider(), shapeGroup, more);
        // Clicks stay on the bar instead of painting the viewport underneath.
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
        refresh();
    }

    /** Told when a mode is picked from the Mode menu. */
    void setOnPickMode(Consumer<Sculpt.Mode> pick) {
        pickMode = pick;
    }

    private static void slider(Slider s, double width) {
        s.setMinWidth(40);
        s.setMaxWidth(width * 1.5);
        HBox.setHgrow(s, javafx.scene.layout.Priority.SOMETIMES);
        s.setMajorTickUnit(1);
        s.setMinorTickCount(0);
        s.setSnapToTicks(true);
        s.setPrefWidth(width);
        s.setFocusTraversable(false);
    }

    /** A labelled group: a small caption, then its controls, on one line. */
    private static Node group(String caption, Node controls) {
        Label l = new Label(caption.toUpperCase(java.util.Locale.ROOT));
        l.getStyleClass().add("brush-group-label");
        l.setMinWidth(USE_PREF_SIZE);
        HBox g = new HBox(6, l, controls);
        g.setAlignment(Pos.CENTER_LEFT);
        if (controls instanceof HBox h) {
            h.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(h, javafx.scene.layout.Priority.SOMETIMES);
        }
        HBox.setHgrow(g, javafx.scene.layout.Priority.SOMETIMES);
        return g;
    }

    private static Node divider() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.getStyleClass().add("brush-divider");
        return s;
    }

    void show(boolean eraser) {
        this.eraser = eraser;
        title.setText(eraser ? "ERASER" : "BRUSH");
        title.setGraphic(eraser ? ToolIcons.eraser(14) : ToolIcons.brush(14));
        title.setStyle("-bd-mode: " + (eraser ? "#E5484D" : "#3E9BFF") + ";");
        modeGroup.setVisible(!eraser);
        modeGroup.setManaged(!eraser);
        refresh();
    }

    /** Re-reads the settings (after the popup or a key changed them). */
    void sync() {
        refresh();
    }

    /** One size smaller or bigger (the size keys). */
    void step(int d) {
        settings.brushSize = Math.clamp(settings.brushSize + d, 1, MAX_SIZE);
        refresh();
        changed.run();
    }

    private void setShape(boolean isCube) {
        settings.brushCube = isCube;
        refresh();
        changed.run();
    }

    private void refresh() {
        syncing = true;
        settings.brushSize = Math.clamp(settings.brushSize, 1, MAX_SIZE);
        settings.brushStrength = Math.clamp(settings.brushStrength, 1, 5);
        size.setValue(settings.brushSize);
        strength.setValue(settings.brushStrength);
        sphere.setSelected(!settings.brushCube);
        cube.setSelected(settings.brushCube);
        int d = settings.brushSize * 2 - 1;
        sizeValue.setText(settings.brushSize == 1 ? "1 block" : d + "×" + d + "×" + d);
        strengthValue.setText(Integer.toString(settings.brushStrength));
        mode.setText(BrushPopup.mode(settings).label);
        syncing = false;
    }
}
