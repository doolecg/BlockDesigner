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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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
    /** The label and control of each option, to hide the ones whose condition is off. */
    private final Map<String, List<Node>> rows = new LinkedHashMap<>();
    /** Block icons for the block slots (null: the slots show short names), and the slots to redraw when they come. */
    private Function<BlockState, Image> icons;
    private final List<Runnable> slotRedraws = new ArrayList<>();
    private List<String> shown;

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
        for (Options.Option o : initial.options().all()) {
            Node control = control(o);
            List<Node> nodes = new ArrayList<>();
            if (!(o instanceof Options.ToggleOption)) {
                Label l = new Label(o.label());
                l.getStyleClass().add("prop-label");
                nodes.add(l);
            }
            nodes.add(control);
            rows.put(o.key(), nodes);
        }
        updateShown();
    }

    /**
     * Lays out only the options whose {@link Options#shown conditions} hold with the current values. Hidden ones are
     * taken out of the grid rather than just hidden, so they leave no gaps. Rows that stay are only moved, never
     * taken out, so the control in use keeps the focus.
     */
    private void updateShown() {
        List<String> now = rows.keySet().stream().filter(k -> values.options().shown(k, values)).toList();
        if (now.equals(shown)) return;
        for (var e : rows.entrySet())
            if (!now.contains(e.getKey())) getChildren().removeAll(e.getValue());
        int row = 0, index = 0;
        for (String key : now) {
            List<Node> nodes = rows.get(key);
            for (int i = 0; i < nodes.size(); i++, index++) {
                Node n = nodes.get(i);
                // Inserted in place, so Tab still walks the options top to bottom.
                if (!getChildren().contains(n)) getChildren().add(index, n);
                // A toggle spans both columns; otherwise the label goes left and the control right.
                GridPane.setConstraints(n, nodes.size() == 1 ? 0 : i, row, nodes.size() == 1 ? 2 : 1, 1);
            }
            row++;
        }
        shown = now;
    }

    OptionValues values() {
        return values;
    }

    /** Draws the block options' slots with the workspace's block icons (once Minecraft's assets are loaded). */
    OptionsEditor icons(io.blockdesigner.app.Workspace ws) {
        return icons(st -> ws.assets() == null ? null : BlockIcons.icon(ws.assets(), st));
    }

    /** Draws the block options' slots with these icons (e.g. {@code s -> BlockIcons.icon(assets, s)}). */
    OptionsEditor icons(Function<BlockState, Image> icons) {
        this.icons = icons;
        slotRedraws.forEach(Runnable::run);
        return this;
    }

    private void set(String key, Object value) {
        OptionValues next = values.with(key, value);
        if (next.equals(values)) return;
        values = next;
        updateShown();
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
            case Options.BlockOption b -> blockSlots(key, values.block(key).toString(), false);
            case Options.BlockListOption b -> blockSlots(key, values.blockList(key).toString().replace("minecraft:", ""), true);
            case Options.FileOption f -> fileField(key, f);
        };
    }

    private static String format(double v, Options.DecimalOption d) {
        // Fractions of 0..1 read best as percentages.
        if (d.min() == 0 && d.max() == 1) return Math.round(v * 100) + "%";
        return String.format(Locale.ROOT, Math.abs(d.max() - d.min()) >= 20 ? "%.0f" : "%.2f", v);
    }

    /** A block (or weighted pattern) typed as text, with a button that takes the held block. */
    private static final double SLOT = 30;

    /**
     * A block, or a weighted mix of blocks, as small hotbar-style slots. Click a slot to put the held block in it (or
     * drop one from the block list), right-click to take it out of a mix, the wheel over it to change its share; the
     * + slot adds the held block. The pencil shows the same value as text (block states, exact weights). The text is
     * the one source of truth: the slots write into it and are redrawn from the value it parses to.
     */
    private Node blockSlots(String key, String text, boolean pattern) {
        TextField f = new TextField(text.startsWith("minecraft:") ? text.substring("minecraft:".length()) : text);
        f.setPromptText(pattern ? "e.g. 70%stone,30%andesite" : "e.g. stone_bricks");
        Tooltip tip = new Tooltip(pattern ? "Blocks with optional weights: 70%stone,30%andesite" : "A block, e.g. oak_stairs[facing=east]");
        f.setTooltip(tip);
        f.setVisible(false);
        f.setManaged(false);
        HBox slots = new HBox(3);
        slots.setAlignment(Pos.CENTER_LEFT);
        Runnable redraw = () -> drawSlots(slots, key, pattern, f);
        f.textProperty().addListener((obs, a, b) -> {
            try {
                set(key, pattern ? BlockPattern.parse(b, blocks) : blocks.resolve(b));
                f.getStyleClass().remove("error");
                f.setTooltip(tip);
            } catch (IllegalArgumentException e) {
                if (!f.getStyleClass().contains("error")) f.getStyleClass().add("error");
                f.setTooltip(new Tooltip(e.getMessage()));
            }
            redraw.run();
        });
        Button edit = new Button(null, new FontIcon(Feather.EDIT_2));
        edit.getStyleClass().add("flat");
        edit.setTooltip(new Tooltip("Edit as text"));
        edit.setOnAction(e -> {
            boolean show = !f.isVisible();
            f.setVisible(show);
            f.setManaged(show);
            if (show) f.requestFocus();
        });
        HBox.setHgrow(slots, Priority.ALWAYS);
        HBox top = new HBox(4, slots, edit);
        top.setAlignment(Pos.CENTER_LEFT);
        slotRedraws.add(redraw);
        redraw.run();
        return new VBox(4, top, f);
    }

    private void drawSlots(HBox slots, String key, boolean pattern, TextField f) {
        slots.getChildren().clear();
        if (!pattern) {
            slots.getChildren().add(slot(values.block(key), null, "Click: use the held block · or drop a block here", () -> {
                BlockState h = held.get();
                if (h != null && !h.isAir()) f.setText(id(h));
            }, dropped -> f.setText(id(dropped)), null, null));
            return;
        }
        List<BlockPattern.Entry> entries = values.blockList(key).entries();
        double total = entries.stream().mapToDouble(BlockPattern.Entry::weight).sum();
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            BlockPattern.Entry en = entries.get(i);
            String share = Math.round(en.weight() / total * 100) + "%";
            slots.getChildren().add(slot(en.block(), entries.size() > 1 ? share : null,
                    share + " of the mix · click: swap for the held block · right-click: remove · wheel: more or less",
                    () -> {
                        BlockState h = held.get();
                        if (h != null && !h.isAir()) f.setText(withEntry(entries, index, new BlockPattern.Entry(h, en.weight())));
                    },
                    dropped -> f.setText(withEntry(entries, index, new BlockPattern.Entry(dropped, en.weight()))),
                    entries.size() > 1 ? () -> f.setText(withEntry(entries, index, null)) : null,
                    up -> {
                        // Steps of about 5% of the mix, never below a weight of 1.
                        double step = Math.max(1, Math.round(total / 20));
                        double w = Math.max(1, en.weight() + (up ? step : -step));
                        f.setText(withEntry(entries, index, new BlockPattern.Entry(en.block(), w)));
                    }));
        }
        StackPane add = new StackPane(new FontIcon(Feather.PLUS));
        add.getStyleClass().addAll("hotbar-slot", "option-slot");
        add.setMinSize(SLOT, SLOT);
        add.setPrefSize(SLOT, SLOT);
        add.setMaxSize(SLOT, SLOT);
        Tooltip.install(add, new Tooltip("Add the held block to the mix · or drop a block here"));
        add.setOnMouseClicked(e -> {
            BlockState h = held.get();
            if (h != null && !h.isAir()) f.setText(withEntry(entries, entries.size(), new BlockPattern.Entry(h, 1)));
        });
        dropTarget(add, dropped -> f.setText(withEntry(entries, entries.size(), new BlockPattern.Entry(dropped, 1))));
        slots.getChildren().add(add);
    }

    /** One slot: the block's icon (or short name), its share of a mix in the corner, and what clicks do. */
    private StackPane slot(BlockState st, String badge, String hint, Runnable click, Consumer<BlockState> drop, Runnable remove,
                           Consumer<Boolean> wheel) {
        StackPane p = new StackPane();
        p.getStyleClass().addAll("hotbar-slot", "option-slot");
        p.setMinSize(SLOT, SLOT);
        p.setPrefSize(SLOT, SLOT);
        p.setMaxSize(SLOT, SLOT);
        Image icon = icons == null ? null : icons.apply(st);
        if (icon != null) {
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(SLOT - 8);
            iv.setFitHeight(SLOT - 8);
            iv.setSmooth(false);
            p.getChildren().add(iv);
        } else {
            Label l = new Label(st.path().length() > 5 ? st.path().substring(0, 5) : st.path());
            l.getStyleClass().add("hotbar-fallback");
            p.getChildren().add(l);
        }
        if (badge != null) {
            Label b = new Label(badge);
            b.getStyleClass().add("hotbar-number");
            StackPane.setAlignment(b, Pos.BOTTOM_RIGHT);
            p.getChildren().add(b);
        }
        Tooltip.install(p, new Tooltip(id(st) + "\n" + hint));
        p.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) click.run();
            else if (e.getButton() == MouseButton.SECONDARY && remove != null) remove.run();
        });
        if (wheel != null) p.setOnScroll(e -> {
            if (e.getDeltaY() != 0) wheel.accept(e.getDeltaY() > 0);
            e.consume();
        });
        dropTarget(p, drop);
        return p;
    }

    private static void dropTarget(StackPane p, Consumer<BlockState> drop) {
        p.setOnDragOver(e -> {
            if (Hotbar.dragged(e) != null) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        p.setOnDragDropped(e -> {
            BlockState st = Hotbar.dragged(e);
            if (st != null) drop.accept(st);
            e.setDropCompleted(st != null);
            e.consume();
        });
    }

    /** The mix as text with entry {@code index} replaced ({@code null} removes it; {@code index == size} appends). */
    private static String withEntry(List<BlockPattern.Entry> entries, int index, BlockPattern.Entry entry) {
        List<BlockPattern.Entry> next = new ArrayList<>(entries);
        if (index == next.size()) next.add(entry);
        else if (entry == null) next.remove(index);
        else next.set(index, entry);
        return new BlockPattern(next).toString().replace("minecraft:", "");
    }

    private static String id(BlockState st) {
        return st.toString().replace("minecraft:", "");
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
