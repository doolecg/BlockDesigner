package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

/**
 * The Paint brush / Eraser options above the hotbar: brush shape (sphere or cube) and size. {@code -} / {@code =}
 * change the size from the keyboard.
 */
final class BrushBar extends HBox {
    static final int MAX_SIZE = 16;

    private final Settings settings;
    private final Label title = new Label();
    private final Label value = new Label();
    private final Slider size = new Slider(1, MAX_SIZE, 1);
    private final ToggleButton sphere = new ToggleButton("Sphere"), cube = new ToggleButton("Cube");
    private final Runnable changed;
    private final Button mode = new Button();
    private final Label strength = new Label();
    private boolean eraser;

    /** @param openPopup opens the full brush settings (the mode button, like right-click in the viewport) */
    BrushBar(Settings settings, Runnable changed, Runnable openPopup) {
        this.settings = settings;
        this.changed = changed;
        mode.getStyleClass().addAll("small", "brush-mode-button");
        mode.setFocusTraversable(false);
        mode.setTooltip(new Tooltip("Brush mode and settings (Shift+right-click in the viewport) · Alt+1…0 pick a mode"));
        mode.setOnAction(e -> openPopup.run());
        strength.getStyleClass().add("brush-value");
        strength.setTooltip(new Tooltip("Strength (, / .)"));
        getStyleClass().add("brush-bar");
        setSpacing(8);
        setAlignment(Pos.CENTER);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        title.getStyleClass().add("brush-title");

        ToggleGroup shapes = new ToggleGroup();
        for (ToggleButton t : new ToggleButton[]{sphere, cube}) {
            t.getStyleClass().addAll("chip", "small");
            t.setToggleGroup(shapes);
            t.setFocusTraversable(false);
        }
        sphere.setOnAction(e -> setShape(false));
        cube.setOnAction(e -> setShape(true));

        Button minus = new Button("−"), plus = new Button("+");
        for (Button b : new Button[]{minus, plus}) {
            b.getStyleClass().addAll("flat", "small");
            b.setFocusTraversable(false);
        }
        minus.setTooltip(new Tooltip("Smaller (-)"));
        plus.setTooltip(new Tooltip("Bigger (=)"));
        minus.setOnAction(e -> step(-1));
        plus.setOnAction(e -> step(1));
        size.setMajorTickUnit(1);
        size.setMinorTickCount(0);
        size.setSnapToTicks(true);
        size.setPrefWidth(130);
        size.setFocusTraversable(false);
        size.valueProperty().addListener((o, a, b) -> {
            int v = (int) Math.round(b.doubleValue());
            if (v != settings.brushSize) {
                settings.brushSize = v;
                refresh();
                changed.run();
            }
        });
        value.getStyleClass().add("brush-value");
        getChildren().addAll(title, mode, sphere, cube, minus, size, plus, value, strength);
        // Clicks stay on the bar instead of painting the viewport underneath.
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
        refresh();
    }

    void show(boolean eraser) {
        this.eraser = eraser;
        title.setText(eraser ? "Eraser" : "Brush");
        refresh();
    }

    /** Re-reads the settings (after the popup or a key changed them). */
    void sync() {
        refresh();
    }

    /** {@code -} / {@code =}: one size smaller or bigger. */
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
        settings.brushSize = Math.clamp(settings.brushSize, 1, MAX_SIZE);
        size.setValue(settings.brushSize);
        sphere.setSelected(!settings.brushCube);
        cube.setSelected(settings.brushCube);
        int d = settings.brushSize * 2 - 1;
        value.setText(settings.brushSize == 1 ? "1 block" : d + "×" + d + "×" + d);
        mode.setText((eraser ? "Erase" : BrushPopup.mode(settings).label) + " ▾");
        strength.setText("Strength " + settings.brushStrength);
        mode.setVisible(true);
    }
}
