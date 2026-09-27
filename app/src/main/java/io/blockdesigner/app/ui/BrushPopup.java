package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.core.edit.Sculpt;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.EnumMap;
import java.util.Map;

/**
 * Right-click in Brush mode: every brush setting at the cursor. Each mode shows its key; while the popup is open the
 * letter keys pick a mode, [ / ] (or - / =) change the size, , / . the strength, and Enter or Esc closes it.
 */
final class BrushPopup extends Popup {
    private final Settings settings;
    private final Runnable changed;
    /** Told when the user picks a mode (not when keys or other code change it). */
    private final java.util.function.Consumer<Sculpt.Mode> picked;
    private final Map<Sculpt.Mode, ToggleButton> modes = new EnumMap<>(Sculpt.Mode.class);
    private final Slider size = new Slider(1, BrushBar.MAX_SIZE, 1), strength = new Slider(1, 5, 2);
    private final Label sizeValue = new Label(), strengthValue = new Label();
    private final Slider noise = new Slider(0, 100, 0), noiseScale = new Slider(2, 32, 8);
    private final Label noiseValue = new Label(), noiseScaleValue = new Label();
    private final Map<Sculpt.Shape, ToggleButton> shapes = new EnumMap<>(Sculpt.Shape.class);
    private boolean syncing;
    /** The current key for each mode (Settings › Keybinds), and the labels and hint that show keys. */
    private java.util.function.Function<Keybinds.Action, String> keyText = a -> "";
    private final Map<Sculpt.Mode, Label> modeKeys = new EnumMap<>(Sculpt.Mode.class);
    private final Label sizeKeys = new Label(), strengthKeys = new Label(), hint = new Label();

    BrushPopup(Settings settings, Runnable changed, java.util.function.Consumer<Sculpt.Mode> picked) {
        this.settings = settings;
        this.changed = changed;
        this.picked = picked;
        setAutoHide(true);
        setHideOnEscape(true);

        Label title = new Label("Brush");
        title.getStyleClass().add("menu-title");

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        ToggleGroup group = new ToggleGroup();
        int i = 0;
        for (Sculpt.Mode m : Sculpt.Mode.values()) {
            ToggleButton b = new ToggleButton(m.label);
            int n = m.ordinal() + 1;
            Label key = new Label();
            key.getStyleClass().add("shortcut-key");
            modeKeys.put(m, key);
            b.setGraphic(key);
            b.setToggleGroup(group);
            b.getStyleClass().add("brush-mode");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setFocusTraversable(false);
            javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip();
            tip.setOnShowing(e -> {
                String k = keyText.apply(modeAction(m));
                tip.setText(m.description + "\n" + (k.isEmpty() ? "" : k + ", or ") + m.key + " while this is open");
            });
            b.setTooltip(tip);
            b.setOnAction(e -> select(m));
            modes.put(m, b);
            grid.add(b, i % 2, i / 2);
            GridPane.setHgrow(b, Priority.ALWAYS);
            i++;
        }

        ToggleGroup shapeGroup = new ToggleGroup();
        javafx.scene.layout.FlowPane shapeChips = new javafx.scene.layout.FlowPane(4, 4);
        for (Sculpt.Shape sh : Sculpt.Shape.values()) {
            ToggleButton t = new ToggleButton(sh.label);
            t.getStyleClass().addAll("chip", "small");
            t.setToggleGroup(shapeGroup);
            t.setFocusTraversable(false);
            t.setTooltip(new javafx.scene.control.Tooltip(sh.description));
            t.setOnAction(e -> {
                setShape(settings, sh);
                update();
            });
            shapes.put(sh, t);
            shapeChips.getChildren().add(t);
        }

        for (Slider s : new Slider[]{size, strength}) {
            s.setMajorTickUnit(1);
            s.setMinorTickCount(0);
            s.setSnapToTicks(true);
            s.setFocusTraversable(false);
            HBox.setHgrow(s, Priority.ALWAYS);
        }
        size.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            settings.brushSize = (int) Math.round(b.doubleValue());
            update();
        });
        strength.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            settings.brushStrength = (int) Math.round(b.doubleValue());
            update();
        });

        // Noise: soft, low-frequency bumps on the shape's edge, their size, and a new pattern.
        noise.setMajorTickUnit(10);
        noise.setMinorTickCount(0);
        noise.setSnapToTicks(true);
        noiseScale.setMajorTickUnit(1);
        noiseScale.setMinorTickCount(0);
        noiseScale.setSnapToTicks(true);
        for (Slider s : new Slider[]{noise, noiseScale}) {
            s.setFocusTraversable(false);
            HBox.setHgrow(s, Priority.ALWAYS);
        }
        noise.setTooltip(new javafx.scene.control.Tooltip("Roughens the brush's edge with soft noise, for natural-looking strokes (0: a clean shape)"));
        noiseScale.setTooltip(new javafx.scene.control.Tooltip("How big the noise's bumps are, in blocks"));
        noise.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            settings.brushNoise = (int) Math.round(b.doubleValue() / 10) * 10;
            update();
        });
        noiseScale.valueProperty().addListener((o, a, b) -> {
            if (syncing) return;
            settings.brushNoiseScale = (int) Math.round(b.doubleValue());
            update();
        });
        javafx.scene.control.Button reroll = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.REFRESH_CW));
        reroll.getStyleClass().addAll("flat", "small");
        reroll.setFocusTraversable(false);
        reroll.setTooltip(new javafx.scene.control.Tooltip("A new noise pattern"));
        reroll.setOnAction(e -> {
            settings.brushNoiseSeed = new java.util.Random().nextLong();
            update();
        });

        hint.getStyleClass().add("layer-meta");
        hint.setWrapText(true);

        VBox box = new VBox(8, title, grid,
                row("Size", size, sizeValue, sizeKeys),
                row("Strength", strength, strengthValue, strengthKeys),
                row("Shape", shapeChips, new Label(), new Label()),
                row("Noise", noise, noiseValue, new Label()),
                row("Bumps", new HBox(4, noiseScale, reroll), noiseScaleValue, new Label()),
                hint);
        box.getStyleClass().add("menu-panel");
        box.setPrefWidth(340);
        getContent().add(box);
        // Keys work whichever window has focus (see also key()).
        box.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (key(e)) e.consume();
        });
        setOnShown(e -> sync());
    }

    /** Shows the keys bound in Settings › Keybinds (brush modes, size, strength). */
    void setKeys(java.util.function.Function<Keybinds.Action, String> keyText) {
        this.keyText = keyText;
        showKeys();
    }

    private static Keybinds.Action modeAction(Sculpt.Mode m) {
        return Keybinds.Action.values()[Keybinds.Action.BRUSH_MODE_1.ordinal() + m.ordinal()];
    }

    private void showKeys() {
        modeKeys.forEach((m, l) -> l.setText(keyText.apply(modeAction(m))));
        sizeKeys.setText(pair(Keybinds.Action.BRUSH_SMALLER, Keybinds.Action.BRUSH_BIGGER));
        strengthKeys.setText(pair(Keybinds.Action.BRUSH_WEAKER, Keybinds.Action.BRUSH_STRONGER));
        String first = keyText.apply(Keybinds.Action.BRUSH_MODE_1), last = keyText.apply(Keybinds.Action.BRUSH_MODE_11);
        String modes = first.isEmpty() ? "The letter" : first + "…" + last + " (or the letter while this is open)";
        hint.setText(modes + " picks a mode · " + sizeKeys.getText() + " size · " + strengthKeys.getText() + " strength\n"
                + "Right-drag smooths · Shift smooths, Ctrl inverts while painting (not while flying)");
    }

    private String pair(Keybinds.Action a, Keybinds.Action b) {
        String x = keyText.apply(a), y = keyText.apply(b);
        return x.isEmpty() && y.isEmpty() ? "" : x + " / " + y;
    }

    private static Node row(String name, Node control, Label value, Label k) {
        Label l = new Label(name);
        l.setMinWidth(62);
        HBox.setHgrow(control, Priority.ALWAYS);
        HBox h = new HBox(8, l, control, value, k);
        h.setAlignment(Pos.CENTER_LEFT);
        value.setMinWidth(46);
        return h;
    }

    /** A key while the popup is open; returns whether it was used. */
    boolean key(KeyEvent e) {
        if (!isShowing() || e.isShortcutDown() || e.isAltDown()) return false;
        KeyCode c = e.getCode();
        switch (c) {
            case ENTER, ESCAPE -> hide();
            case MINUS, SUBTRACT -> step(-1, 0);
            case EQUALS, ADD -> step(1, 0);
            case COMMA -> step(0, -1);
            case PERIOD -> step(0, 1);
            default -> {
                if (!c.isLetterKey()) return false;
                char ch = c.getName().toUpperCase().charAt(0);
                for (Sculpt.Mode m : Sculpt.Mode.values()) {
                    if (m.key == ch) {
                        select(m);
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    private void select(Sculpt.Mode m) {
        // Picking Erase leaves the brush's own mode alone, so the eraser doesn't change what the brush does.
        if (m != Sculpt.Mode.ERASE || !eraser) settings.brushMode = m.name();
        update();
        picked.accept(m);
    }

    /** Whether the popup is showing for the eraser (its Erase mode lit, whatever the brush's mode is). */
    private boolean eraser;

    void setEraser(boolean eraser) {
        this.eraser = eraser;
        sync();
    }

    void step(int dSize, int dStrength) {
        settings.brushSize = Math.clamp(settings.brushSize + dSize, 1, BrushBar.MAX_SIZE);
        settings.brushStrength = Math.clamp(settings.brushStrength + dStrength, 1, 5);
        update();
    }

    private void update() {
        sync();
        changed.run();
    }

    /** Shows the current settings. */
    void sync() {
        showKeys();
        syncing = true;
        Sculpt.Mode mode = eraser ? Sculpt.Mode.ERASE : mode(settings);
        modes.forEach((m, b) -> b.setSelected(m == mode));
        size.setValue(settings.brushSize);
        strength.setValue(settings.brushStrength);
        int d = settings.brushSize * 2 - 1;
        sizeValue.setText(settings.brushSize == 1 ? "1" : d + "³");
        strengthValue.setText(String.valueOf(settings.brushStrength));
        settings.brushNoise = Math.clamp(settings.brushNoise, 0, 100);
        settings.brushNoiseScale = Math.clamp(settings.brushNoiseScale, 2, 32);
        noise.setValue(settings.brushNoise);
        noiseScale.setValue(settings.brushNoiseScale);
        noiseValue.setText(settings.brushNoise == 0 ? "Off" : settings.brushNoise + "%");
        noiseScaleValue.setText(settings.brushNoiseScale + " bl");
        noiseScale.setDisable(settings.brushNoise == 0 && shape(settings) != Sculpt.Shape.SPLATTER);
        Sculpt.Shape current = shape(settings);
        shapes.forEach((sh, t) -> t.setSelected(sh == current));
        syncing = false;
    }

    /** The brush shape, from settings that may predate shapes (then the old sphere / cube choice). */
    static Sculpt.Shape shape(Settings s) {
        if (s.brushShape != null) {
            try {
                return Sculpt.Shape.valueOf(s.brushShape);
            } catch (RuntimeException ignored) {
            }
        }
        return s.brushCube ? Sculpt.Shape.CUBE : Sculpt.Shape.SPHERE;
    }

    static void setShape(Settings s, Sculpt.Shape shape) {
        s.brushShape = shape.name();
        s.brushCube = shape == Sculpt.Shape.CUBE;
    }

    static Sculpt.Mode mode(Settings s) {
        try {
            return Sculpt.Mode.valueOf(s.brushMode);
        } catch (RuntimeException e) {
            return Sculpt.Mode.DRAW;
        }
    }
}
