package io.blockdesigner.app.ui;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.BlockPattern;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.ui.Form;
import io.blockdesigner.plugin.ui.Section;
import io.blockdesigner.plugin.ui.Theme;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
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
 * Draws the controls for a plugin's {@link Options} (the transform dialog, export cards, the import dialog, tool bars,
 * plugin settings pages and plugins' own panels) and reports each change as new {@link OptionValues}. Text-typed
 * values (blocks, patterns) only count once they parse; until then the field is marked and the last good value stays.
 *
 * <p>{@link Look#FULL} draws one section per {@link Options#groups group} (advanced groups collapsed), with help under
 * the controls; {@link Look#COMPACT} (the tool's options bar) is one flat form with the help as tooltips. Both use the
 * plugin UI kit's {@link Form}, so labels line up at 96–140 px and go above the controls when narrow.
 */
final class OptionsEditor extends VBox implements io.blockdesigner.plugin.ui.OptionsForm {
    enum Look { FULL, COMPACT }

    private final Look look;
    private OptionValues values;
    private final BlockCatalog blocks;
    private final Supplier<BlockState> held;
    private final Consumer<OptionValues> onChange;
    /** Whether each option is shown (its showWhen conditions hold) and each toggle is on, for the rows to follow. */
    private final Map<String, BooleanProperty> shown = new LinkedHashMap<>();
    private final Map<String, BooleanProperty> toggles = new LinkedHashMap<>();
    /** The sections of titled groups, to hide one whose options are all hidden. */
    private final Map<Section, List<String>> sections = new LinkedHashMap<>();
    /** Block icons for the block slots (null: the slots show short names), and the slots to redraw when they come. */
    private Function<BlockState, Image> icons;
    private final List<Runnable> slotRedraws = new ArrayList<>();
    /** Where the open advanced groups are remembered ("<store key>#<group title>"), or null. */
    private String expandKey;
    private List<String> expandedStore;
    private boolean building;

    /**
     * @param held     the block in hand, for the "use held block" buttons (may return null)
     * @param onChange called with the new values after every valid change
     */
    OptionsEditor(OptionValues initial, BlockCatalog blocks, Supplier<BlockState> held, Consumer<OptionValues> onChange) {
        this(Look.FULL, initial, blocks, held, onChange);
    }

    OptionsEditor(Look look, OptionValues initial, BlockCatalog blocks, Supplier<BlockState> held, Consumer<OptionValues> onChange) {
        this.look = look;
        this.values = initial;
        this.blocks = blocks;
        this.held = held;
        this.onChange = onChange;
        Theme.attach(this);
        getStyleClass().add(look == Look.FULL ? "options-editor" : "options-editor-compact");
        setSpacing(look == Look.FULL ? Theme.LG : Theme.SM);
        setFillWidth(true);
        setMinWidth(0);
        build();
    }

    /** Remembers which advanced groups the user opened, in {@code store} (the settings' list) under {@code storeKey}. */
    OptionsEditor rememberExpanded(String storeKey, List<String> store) {
        this.expandKey = storeKey;
        this.expandedStore = store;
        build();
        return this;
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

    // ---- OptionsForm ---------------------------------------------------------------------------------------------

    @Override
    public Node node() {
        return this;
    }

    @Override
    public OptionValues values() {
        return values;
    }

    @Override
    public void setValues(OptionValues v) {
        if (v == null || v.equals(values)) return;
        values = v;
        build();
    }

    @Override
    public void reset() {
        values = values.options().defaults();
        build();
        onChange.accept(values);
    }

    // ---- layout ----------------------------------------------------------------------------------------------------

    private void build() {
        building = true;
        try {
            getChildren().clear();
            shown.clear();
            toggles.clear();
            sections.clear();
            slotRedraws.clear();
            Options options = values.options();
            for (Options.Option o : options.all()) {
                shown.put(o.key(), new SimpleBooleanProperty(options.shown(o.key(), values)));
                if (o instanceof Options.ToggleOption) toggles.put(o.key(), new SimpleBooleanProperty(values.toggle(o.key())));
            }
            if (look == Look.COMPACT) {
                Form f = new Form();
                for (Options.Option o : options.all()) addRow(f, o);
                getChildren().add(f);
            } else {
                for (Options.Group g : options.groups()) {
                    Form f = new Form();
                    for (String key : g.keys()) addRow(f, options.get(key).orElseThrow());
                    if (g.title() == null) {
                        getChildren().add(f);
                        continue;
                    }
                    Section s = new Section(g.title(), f);
                    if (g.advanced()) {
                        String id = expandKey == null ? null : expandKey + "#" + g.title();
                        s.collapsible(id != null && expandedStore.contains(id));
                        if (id != null) s.expandedProperty().addListener((obs, was, open) -> {
                            expandedStore.remove(id);
                            if (open) expandedStore.add(id);
                        });
                    }
                    sections.put(s, g.keys());
                    getChildren().add(s);
                }
            }
            updateSections();
        } finally {
            building = false;
        }
    }

    private void addRow(Form f, Options.Option o) {
        Options options = values.options();
        String key = o.key();
        Node control = control(o);
        String help = options.help(key).orElse(null);
        Form.Row row = o instanceof Options.ToggleOption ? f.row(control) : f.row(o.label(), control);
        // Units go in the form's unit column (a 0..1 decimal without one reads as a percentage).
        String unit = options.unit(key).orElse(o instanceof Options.DecimalOption d && isFraction(d) ? "%" : null);
        if (unit != null) row.unit(unit);
        if (help != null) {
            if (look == Look.FULL) row.help(help);
            else {
                if (control instanceof Control c) c.setTooltip(new Tooltip(help));
                if (row.label() != null) row.label().setTooltip(new Tooltip(o.label() + "\n" + help));
            }
        }
        options.enabledWhen(key).ifPresent(t -> row.enabledWhen(toggles.get(t)));
        if (!options.conditions(key).isEmpty()) row.shownWhen(shown.get(key));
    }

    /** Hides a titled group whose options are all hidden by their conditions. */
    private void updateSections() {
        for (var e : sections.entrySet()) {
            boolean any = e.getValue().stream().anyMatch(k -> shown.get(k).get());
            e.getKey().setVisible(any);
            e.getKey().setManaged(any);
        }
    }

    private void set(String key, Object value) {
        if (building) return;
        OptionValues next = values.with(key, value);
        if (next.equals(values)) return;
        values = next;
        Options options = values.options();
        for (var e : shown.entrySet()) e.getValue().set(options.shown(e.getKey(), values));
        for (var e : toggles.entrySet()) e.getValue().set(values.toggle(e.getKey()));
        updateSections();
        onChange.accept(values);
    }

    // ---- controls --------------------------------------------------------------------------------------------------

    private Node control(Options.Option o) {
        String key = o.key();
        return switch (o) {
            case Options.IntegerOption i -> {
                Spinner<Integer> s = new Spinner<>(i.min(), i.max(), values.integer(key));
                s.setEditable(true);
                s.setPrefWidth(88);
                s.setMinWidth(0);
                s.valueProperty().addListener((obs, a, b) -> {
                    if (b != null) set(key, b);
                });
                // A typed number counts when the field loses focus, not only on Enter.
                s.getEditor().focusedProperty().addListener((obs, was, is) -> {
                    if (!is) commitSpinner(s);
                });
                yield s;
            }
            case Options.DecimalOption d -> decimal(key, d);
            case Options.ToggleOption t -> {
                CheckBox c = new CheckBox(t.label());
                c.setSelected(values.toggle(key));
                c.setWrapText(true);
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

    private static void commitSpinner(Spinner<Integer> s) {
        try {
            s.getValueFactory().setValue(s.getValueFactory().getConverter().fromString(s.getEditor().getText()));
        } catch (RuntimeException bad) {
            s.getEditor().setText(s.getValueFactory().getConverter().toString(s.getValue()));
        }
    }

    /** A slider with a small field showing the exact value (type one and press Enter); the row shows the unit. */
    private Node decimal(String key, Options.DecimalOption d) {
        String unit = values.options().unit(key).orElse(isFraction(d) ? "%" : null);
        boolean percent = "%".equals(unit);
        Slider s = new Slider(d.min(), d.max(), values.decimal(key));
        s.setMinWidth(0);
        s.setMaxWidth(Double.MAX_VALUE);
        TextField field = new TextField(format(values.decimal(key), d, percent));
        field.getStyleClass().add("bd-inline-field");
        field.setAccessibleText(d.label());
        Runnable commit = () -> {
            try {
                double v = Double.parseDouble(field.getText().replace("%", "").replace(',', '.').strip());
                if (percent) v /= 100;
                s.setValue(Math.clamp(v, d.min(), d.max()));
            } catch (NumberFormatException bad) {
                // not a number: show the value again
            }
            field.setText(format(s.getValue(), d, percent));
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) commit.run();
        });
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) field.setText(format(s.getValue(), d, percent));
        });
        s.valueProperty().addListener((obs, a, b) -> {
            if (!field.isFocused()) field.setText(format(b.doubleValue(), d, percent));
            set(key, b.doubleValue());
        });
        HBox.setHgrow(s, Priority.ALWAYS);
        HBox box = new HBox(8, s, field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        return box;
    }

    /** A 0..1 decimal without a unit reads best as a percentage (as it did before units existed). */
    private static boolean isFraction(Options.DecimalOption d) {
        return d.min() == 0 && d.max() == 1;
    }

    static String format(double v, Options.DecimalOption d, boolean percent) {
        if (percent) return Long.toString(Math.round(v * 100));
        double range = Math.abs(d.max() - d.min());
        return String.format(Locale.ROOT, range >= 20 ? "%.0f" : range >= 2 ? "%.1f" : "%.2f", v);
    }

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
        edit.setAccessibleText("Edit as text");
        edit.setOnAction(e -> {
            boolean show = !f.isVisible();
            f.setVisible(show);
            f.setManaged(show);
            if (show) f.requestFocus();
        });
        HBox.setHgrow(slots, Priority.ALWAYS);
        slots.setMinWidth(0);
        HBox top = new HBox(4, slots, edit);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setMinWidth(0);
        slotRedraws.add(redraw);
        redraw.run();
        VBox box = new VBox(4, top, f);
        box.setMinWidth(0);
        return box;
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
        f.setMinWidth(0);
        f.textProperty().addListener((obs, a, b) -> {
            try {
                set(key, b.isBlank() ? null : Path.of(b.strip()));
                f.getStyleClass().remove("error");
            } catch (RuntimeException e) {
                if (!f.getStyleClass().contains("error")) f.getStyleClass().add("error");
            }
        });
        Button browse = new Button("Browse…");
        browse.setMinWidth(Button.USE_PREF_SIZE);
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
        box.setMinWidth(0);
        return box;
    }
}
