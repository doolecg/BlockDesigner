package io.blockdesigner.app.ui;

import atlantafx.base.controls.Popover;
import io.blockdesigner.app.Settings;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Blender-style viewport popover: view (field of view, clipping, fog), overlays and navigation settings. Changes apply
 * live and are saved when the popover closes.
 */
final class ViewportSettings {
    private ViewportSettings() {
    }

    static Popover popover(Settings s, Runnable changed, Runnable showShortcuts) {
        Popover p = SidePopover.create("Viewport", null);
        p.setContentNode(content(s, changed, showShortcuts, p));
        p.setOnHidden(e -> s.save());
        return p;
    }

    /** "Name (key)" with the key bound in Settings › Keybinds, or just the name when it has none. */
    private static String withKey(String name, Keybinds keys, Keybinds.Action a) {
        var k = keys.get(a);
        String t = Keybinds.text(k[0] != null ? k[0] : k[1]);
        return t.isEmpty() ? name : name + " (" + t + ")";
    }

    private static Node content(Settings s, Runnable changed, Runnable showShortcuts, Popover p) {
        Keybinds keys = new Keybinds(s);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(4);
        ColumnConstraints label = new ColumnConstraints();
        label.setHalignment(HPos.LEFT);
        ColumnConstraints control = new ColumnConstraints(170);
        control.setHgrow(Priority.ALWAYS);
        ColumnConstraints value = new ColumnConstraints(64);
        value.setHalignment(HPos.RIGHT);
        g.getColumnConstraints().addAll(label, control, value);
        Rows rows = new Rows(g, changed);

        rows.section("View");
        rows.slider("Field of view", 15, 120, () -> s.fovDeg, v -> s.fovDeg = v,
                v -> String.format("%.0f° · %.0fmm", v, 12 / Math.tan(Math.toRadians(v / 2))));
        rows.slider("Clip end", 256, 16000, () -> s.clipEnd, v -> s.clipEnd = v, v -> String.format("%.0f", v));
        rows.check("Fog", () -> s.fog, v -> s.fog = v);
        rows.slider("Fog start", 32, 4000, () -> s.fogDistance, v -> s.fogDistance = v, v -> String.format("%.0f", v));

        rows.section("Overlays");
        rows.check("Ground grid", () -> s.showGrid, v -> s.showGrid = v);
        rows.check("Layer outlines", () -> s.showOutlines, v -> s.showOutlines = v);
        rows.check("Block info", () -> s.showHud, v -> s.showHud = v);
        rows.check(withKey("Key hints", keys, Keybinds.Action.KEY_HINTS), () -> s.showKeyHints, v -> s.showKeyHints = v);

        rows.section("Navigation");
        rows.slider("Orbit sensitivity", 0.2, 3, () -> s.orbitSensitivity, v -> s.orbitSensitivity = v, v -> String.format("%.2f×", v));
        rows.slider("Zoom speed", 0.2, 3, () -> s.zoomSpeed, v -> s.zoomSpeed = v, v -> String.format("%.2f×", v));

        rows.section(withKey("Flying", keys, Keybinds.Action.FLY));
        rows.slider("Fly speed", 2, 80, () -> s.flySpeed, v -> s.flySpeed = v, v -> String.format("%.1f b/s", v));
        rows.slider("Look sensitivity", 0.2, 3, () -> s.lookSensitivity, v -> s.lookSensitivity = v, v -> String.format("%.2f×", v));
        rows.check("Momentum (glide to a stop)", () -> s.flyMomentum, v -> s.flyMomentum = v);

        rows.section("Editing");
        rows.slider(withKey("Fast nudge step", keys, Keybinds.Action.FAST_NUDGE), 2, 64, () -> s.fastNudgeStep, v -> s.fastNudgeStep = (int) Math.round(v), v -> String.format("%.0f", v));
        rows.slider("Place repeat", 50, 1000, () -> s.placeDelayMs, v -> s.placeDelayMs = (int) Math.round(v), v -> String.format("%.0f ms", v));
        rows.slider("Break repeat", 50, 1000, () -> s.breakDelayMs, v -> s.breakDelayMs = (int) Math.round(v), v -> String.format("%.0f ms", v));
        rows.slider("Brush speed", 2, 30, () -> s.brushRate, v -> s.brushRate = v, v -> String.format("%.0f / s", v));
        rows.check("Place / break sounds", () -> s.blockSounds, v -> s.blockSounds = v);
        rows.slider("Sound volume", 0, 1, () -> s.soundVolume, v -> s.soundVolume = v, v -> String.format("%.0f%%", v * 100));
        rows.check("Break particles", () -> s.breakParticles, v -> s.breakParticles = v);

        Button reset = new Button("Reset to defaults");
        reset.getStyleClass().add("flat");
        reset.setOnAction(e -> {
            s.resetViewport();
            changed.run();
            p.setContentNode(content(s, changed, showShortcuts, p));
        });
        Button shortcutsButton = new Button(withKey("Keyboard shortcuts", keys, Keybinds.Action.SHORTCUTS), new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.COMMAND));
        shortcutsButton.getStyleClass().add("flat");
        shortcutsButton.setOnAction(e -> showShortcuts.run());
        javafx.scene.layout.Region gap = new javafx.scene.layout.Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox footer = new HBox(shortcutsButton, gap, reset);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, g, footer);
        box.getStyleClass().add("viewport-settings");
        return box;
    }

    private static final class Rows {
        private final GridPane g;
        private final Runnable changed;
        private int row;

        Rows(GridPane g, Runnable changed) {
            this.g = g;
            this.changed = changed;
        }

        void section(String title) {
            Label l = new Label(title.toUpperCase());
            l.getStyleClass().add("viewport-settings-section");
            g.add(l, 0, row++, 3, 1);
        }

        void slider(String name, double min, double max, java.util.function.DoubleSupplier get, java.util.function.DoubleConsumer set, DoubleFunction<String> fmt) {
            Slider sl = new Slider(min, max, Math.clamp(get.getAsDouble(), min, max));
            Label value = new Label(fmt.apply(sl.getValue()));
            value.getStyleClass().add("viewport-settings-value");
            sl.valueProperty().addListener((o, a, b) -> {
                set.accept(b.doubleValue());
                value.setText(fmt.apply(b.doubleValue()));
                changed.run();
            });
            g.addRow(row++, new Label(name), sl, value);
        }

        void check(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(get.get());
            cb.selectedProperty().addListener((o, a, b) -> {
                set.accept(b);
                changed.run();
            });
            g.add(cb, 0, row++, 3, 1);
        }
    }
}
