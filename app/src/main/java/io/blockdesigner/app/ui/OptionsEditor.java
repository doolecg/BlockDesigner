package io.blockdesigner.app.ui;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.BlockPattern;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Draws the controls for a plugin's {@link Options} (the transform dialog, and later export cards and tool bars) and
 * reports each change as new {@link OptionValues}. Text-typed values (blocks, patterns) only count once they parse;
 * until then the field is marked and the last good value stays.
 */
final class OptionsEditor extends GridPane {
    private OptionValues values;
    private final BlockCatalog blocks;
    private final Supplier<BlockState> held;
    private final Consumer<OptionValues> onChange;

    /**
     * @param held     the block in hand, for the "use held block" buttons (may return null)
     * @param onChange called with the new values after every valid change
     */
    OptionsEditor(OptionValues initial, BlockCatalog blocks, Supplier<BlockState> held, Consumer<OptionValues> onChange) {
        this.values = initial;
        this.blocks = blocks;
        this.held = held;
        this.onChange = onChange;
        setHgap(10);
        setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHalignment(HPos.LEFT);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        getColumnConstraints().addAll(labels, fields);
        int row = 0;
        for (Options.Option o : initial.options().all()) {
            Node control = control(o);
            if (o instanceof Options.ToggleOption) {
                add(control, 0, row, 2, 1);
            } else {
                Label l = new Label(o.label());
                l.getStyleClass().add("prop-label");
                add(l, 0, row);
                add(control, 1, row);
            }
            row++;
        }
    }

    OptionValues values() {
        return values;
    }

    private void set(String key, Object value) {
        OptionValues next = values.with(key, value);
        if (next.equals(values)) return;
        values = next;
        onChange.accept(values);
    }

    private Node control(Options.Option o) {
        String key = o.key();
        return switch (o) {
            case Options.IntegerOption i -> {
                Spinner<Integer> s = new Spinner<>(i.min(), i.max(), values.integer(key));
                s.setEditable(true);
                s.setMaxWidth(Double.MAX_VALUE);
                s.valueProperty().addListener((obs, a, b) -> {
                    if (b != null) set(key, b);
                });
                yield s;
            }
            case Options.DecimalOption d -> {
                Slider s = new Slider(d.min(), d.max(), values.decimal(key));
                Label v = new Label(format(values.decimal(key), d));
                v.setMinWidth(44);
                v.getStyleClass().add("prop-label");
                s.valueProperty().addListener((obs, a, b) -> {
                    v.setText(format(b.doubleValue(), d));
                    set(key, b.doubleValue());
                });
                HBox.setHgrow(s, Priority.ALWAYS);
                HBox box = new HBox(8, s, v);
                box.setAlignment(Pos.CENTER_LEFT);
                yield box;
            }
            case Options.ToggleOption t -> {
                CheckBox c = new CheckBox(t.label());
                c.setSelected(values.toggle(key));
                c.selectedProperty().addListener((obs, a, b) -> set(key, b));
                yield c;
            }
            case Options.ChoiceOption c -> {
                ComboBox<String> box = new ComboBox<>();
                box.getItems().setAll(c.values());
                box.setValue(values.choice(key));
                box.setMaxWidth(Double.MAX_VALUE);
                box.valueProperty().addListener((obs, a, b) -> {
                    if (b != null) set(key, b);
                });
                yield box;
            }
            case Options.TextOption t -> {
                TextField f = new TextField(values.text(key));
                f.textProperty().addListener((obs, a, b) -> set(key, b));
                yield f;
            }
            case Options.BlockOption b -> blockField(key, values.block(key).toString(), false);
            case Options.BlockListOption b -> blockField(key, values.blockList(key).toString().replace("minecraft:", ""), true);
            case Options.FileOption f -> fileField(key, f);
        };
    }

    private static String format(double v, Options.DecimalOption d) {
        // Fractions of 0..1 read best as percentages.
        if (d.min() == 0 && d.max() == 1) return Math.round(v * 100) + "%";
        return String.format(Locale.ROOT, Math.abs(d.max() - d.min()) >= 20 ? "%.0f" : "%.2f", v);
    }

    /** A block (or weighted pattern) typed as text, with a button that takes the held block. */
    private Node blockField(String key, String text, boolean pattern) {
        TextField f = new TextField(text.startsWith("minecraft:") ? text.substring("minecraft:".length()) : text);
        f.setPromptText(pattern ? "e.g. 70%stone,30%andesite" : "e.g. stone_bricks");
        Tooltip tip = new Tooltip(pattern ? "Blocks with optional weights: 70%stone,30%andesite" : "A block, e.g. oak_stairs[facing=east]");
        f.setTooltip(tip);
        f.textProperty().addListener((obs, a, b) -> {
            try {
                set(key, pattern ? BlockPattern.parse(b, blocks) : blocks.resolve(b));
                f.getStyleClass().remove("error");
                f.setTooltip(tip);
            } catch (IllegalArgumentException e) {
                if (!f.getStyleClass().contains("error")) f.getStyleClass().add("error");
                f.setTooltip(new Tooltip(e.getMessage()));
            }
        });
        Button hand = new Button(null, new FontIcon(pattern ? Feather.PLUS : Feather.CORNER_DOWN_LEFT));
        hand.getStyleClass().add("flat");
        hand.setTooltip(new Tooltip(pattern ? "Add the held block to the mix" : "Use the held block"));
        hand.setOnAction(e -> {
            BlockState h = held.get();
            if (h == null || h.isAir()) return;
            String id = h.toString().replace("minecraft:", "");
            f.setText(pattern && !f.getText().isBlank() ? f.getText() + "," + id : id);
        });
        HBox.setHgrow(f, Priority.ALWAYS);
        HBox box = new HBox(4, f, hand);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node fileField(String key, Options.FileOption o) {
        TextField f = new TextField(values.file(key).map(Path::toString).orElse(""));
        f.setPromptText("No file chosen");
        f.textProperty().addListener((obs, a, b) -> {
            try {
                set(key, b.isBlank() ? null : Path.of(b.strip()));
                f.getStyleClass().remove("error");
            } catch (RuntimeException e) {
                if (!f.getStyleClass().contains("error")) f.getStyleClass().add("error");
            }
        });
        Button browse = new Button("Browse…");
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(o.label());
            if (!o.extensions().isEmpty()) {
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(o.label(), o.extensions().stream().map(x -> "*." + x).toList()));
            }
            values.file(key).map(Path::getParent).map(Path::toFile).filter(File::isDirectory).ifPresent(fc::setInitialDirectory);
            File picked = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (picked != null) f.setText(picked.getAbsolutePath());
        });
        HBox.setHgrow(f, Priority.ALWAYS);
        HBox box = new HBox(4, f, browse);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
