package io.blockdesigner.app.ui;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.model.BlockState;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Select mode's "Select by type": pick which block types (or exact states) to select, where to look for them, and
 * optional property filters. The block list shows what the chosen scope actually contains, with counts.
 */
public final class SelectByTypePanel extends VBox {

    public enum Scope {
        ACTIVE("Active layer"), SELECTED("Selected layers"), VISIBLE("All visible layers"), SELECTION("Within the current selection");

        final String label;

        Scope(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Mode {
        REPLACE("Replace the selection"), ADD("Add to the selection"), REMOVE("Remove from the selection");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * What to count: blocks in {@code scope} whose world-facing state has every property in {@code properties}
     * (values may list alternatives, {@code a|b}), keyed by block id or, when {@code exact}, the full state.
     */
    public record Query(Scope scope, boolean exact, Map<String, Set<String>> properties, boolean slice) {
        /** The key a state is listed under. */
        public String key(BlockState s) {
            return exact ? s.toString() : s.name();
        }

        public boolean matches(BlockState s) {
            for (var e : properties.entrySet()) {
                String v = s.get(e.getKey());
                if (v == null || !e.getValue().contains(v)) return false;
            }
            return true;
        }
    }

    public record Options(Query query, Set<String> keys, Mode mode) {
    }

    private final BlockAssets assets;
    private final Function<Query, Map<String, Long>> counter;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> shown = new FilteredList<>(rows);
    private final Map<String, Image> icons = new HashMap<>();
    private final Label summary = new Label();
    private final BooleanProperty anyChecked = new SimpleBooleanProperty();
    private Query query;

    /**
     * @param counter  counts the matching blocks per key for a query
     * @param preselect the block to start with checked (world-facing), or null
     */
    public SelectByTypePanel(BlockAssets assets, Scope defaultScope, boolean hasSelection, boolean sliceActive,
                             BlockState preselect, Function<Query, Map<String, Long>> counter,
                             java.util.function.Consumer<Options> onSelect, Runnable onClose) {
        this.assets = assets;
        this.counter = counter;

        ComboBox<Scope> scope = new ComboBox<>();
        scope.getItems().setAll(Scope.values());
        if (!hasSelection) scope.getItems().remove(Scope.SELECTION);
        scope.setValue(scope.getItems().contains(defaultScope) ? defaultScope : Scope.VISIBLE);
        scope.setMaxWidth(Double.MAX_VALUE);

        ToggleGroup matchGroup = new ToggleGroup();
        RadioButton byType = new RadioButton("Block type (any facing / state)");
        RadioButton byState = new RadioButton("Exact state");
        byType.setToggleGroup(matchGroup);
        byState.setToggleGroup(matchGroup);
        byType.setSelected(true);
        HBox match = new HBox(14, byType, byState);

        TextField props = new TextField();
        props.setPromptText("e.g. half=top, facing=north|south");
        Label propsHint = new Label();
        propsHint.getStyleClass().add("layer-meta");

        CheckBox slice = new CheckBox("Only the levels shown by the slice view");
        slice.setSelected(sliceActive);
        slice.setVisible(sliceActive);
        slice.setManaged(sliceActive);

        ComboBox<Mode> mode = new ComboBox<>();
        mode.getItems().setAll(Mode.values());
        mode.setValue(Mode.REPLACE);
        mode.setMaxWidth(Double.MAX_VALUE);

        TextField search = new TextField();
        search.setPromptText("Filter the list…  (e.g. stairs, create:)");
        search.getStyleClass().add("search-field");
        HBox searchBox = new HBox(6, new FontIcon(Feather.SEARCH), search);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.strip().toLowerCase(Locale.ROOT);
            shown.setPredicate(r -> q.isEmpty() || r.key.contains(q) || r.title.toLowerCase(Locale.ROOT).contains(q));
        });

        Button all = new Button("All");
        Button none = new Button("None");
        Button invert = new Button("Invert");
        for (Button b : List.of(all, none, invert)) b.getStyleClass().addAll("small", "flat");
        all.setOnAction(e -> shown.forEach(r -> r.checked.set(true)));
        none.setOnAction(e -> shown.forEach(r -> r.checked.set(false)));
        invert.setOnAction(e -> shown.forEach(r -> r.checked.set(!r.checked.get())));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        summary.getStyleClass().add("layer-meta");
        HBox listBar = new HBox(4, all, none, invert, spacer, summary);
        listBar.setAlignment(Pos.CENTER_LEFT);

        ListView<Row> list = new ListView<>(shown);
        list.setCellFactory(v -> new RowCell());
        list.setPrefHeight(300);
        list.setOnKeyPressed(e -> {
            Row r = list.getSelectionModel().getSelectedItem();
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE && r != null) {
                r.checked.set(!r.checked.get());
                e.consume();
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, new Label("Look in"), scope);
        g.addRow(1, new Label("Match by"), match);
        g.addRow(2, new Label("Properties"), props);
        g.add(propsHint, 1, 3);
        g.add(slice, 1, 4);
        g.addRow(5, new Label("Result"), mode);
        GridPane.setHgrow(scope, Priority.ALWAYS);

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> onClose.run());
        Button ok = new Button("Select");
        ok.getStyleClass().add("accent");
        ok.setDefaultButton(true);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox buttons = new HBox(8, grow, cancel, ok);

        setSpacing(10);
        setPadding(new Insets(2, 2, 2, 2));
        setPrefWidth(500);
        getChildren().addAll(g, searchBox, listBar, list, buttons);

        Runnable refresh = () -> {
            Map<String, Set<String>> filter = parseProperties(props.getText());
            propsHint.setText(filter.isEmpty() ? "Blank matches every block" : "Blocks without these properties are skipped");
            refresh(new Query(scope.getValue(), byState.isSelected(), filter, slice.isSelected()));
        };
        scope.valueProperty().addListener((o, a, b) -> refresh.run());
        matchGroup.selectedToggleProperty().addListener((o, a, b) -> refresh.run());
        props.textProperty().addListener((o, a, b) -> refresh.run());
        slice.selectedProperty().addListener((o, a, b) -> refresh.run());

        refresh.run();
        if (preselect != null) {
            for (Row r : rows) if (r.state.name().equals(preselect.name())) r.checked.set(true);
        }
        updateSummary();

        ok.disableProperty().bind(anyChecked.not());
        ok.setOnAction(e -> {
            onSelect.accept(new Options(query, checkedKeys(), mode.getValue()));
            onClose.run();
        });
        sceneProperty().addListener((o, a, b) -> {
            if (b != null) javafx.application.Platform.runLater(search::requestFocus);
        });
    }

    /** Rebuilds the list for a new query, keeping what was ticked (a ticked type ticks all its states and back). */
    private void refresh(Query q) {
        Set<String> checkedTypes = new HashSet<>(), checkedStates = new HashSet<>();
        for (Row r : rows) {
            if (!r.checked.get()) continue;
            checkedTypes.add(r.state.name());
            if (query != null && query.exact()) checkedStates.add(r.key);
        }
        boolean wasExact = query != null && query.exact();
        query = q;
        Map<String, Long> counts = counter.apply(q);
        List<Row> fresh = new java.util.ArrayList<>();
        counts.forEach((key, n) -> {
            Row r = new Row(key, q.exact() ? BlockState.parse(key) : BlockState.of(key), n);
            boolean on = q.exact() && wasExact ? checkedStates.contains(key) : checkedTypes.contains(r.state.name());
            r.checked.set(on);
            r.checked.addListener((o, a, b) -> updateSummary());
            fresh.add(r);
        });
        fresh.sort((a, b) -> a.count != b.count ? Long.compare(b.count, a.count) : a.key.compareTo(b.key));
        rows.setAll(fresh);
        updateSummary();
    }

    private Set<String> checkedKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (Row r : rows) if (r.checked.get()) out.add(r.key);
        return out;
    }

    private void updateSummary() {
        long blocks = 0, types = 0;
        for (Row r : rows) {
            if (!r.checked.get()) continue;
            blocks += r.count;
            types++;
        }
        anyChecked.set(types > 0);
        summary.setText(rows.isEmpty() ? "No matching blocks"
                : String.format("%,d block%s in %d of %d %s", blocks, blocks == 1 ? "" : "s", types, rows.size(),
                query.exact() ? "states" : "types"));
    }

    /** {@code key=value, key=a|b}; malformed parts are ignored. */
    static Map<String, Set<String>> parseProperties(String text) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (text == null) return out;
        for (String part : text.split("[,;\\s]+")) {
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) continue;
            Set<String> values = new LinkedHashSet<>();
            for (String v : part.substring(eq + 1).split("\\|")) if (!v.isBlank()) values.add(v.strip().toLowerCase(Locale.ROOT));
            if (!values.isEmpty()) out.put(part.substring(0, eq).strip().toLowerCase(Locale.ROOT), values);
        }
        return out;
    }

    private Image icon(BlockState s) {
        if (assets == null) return null;
        return icons.computeIfAbsent(s.toString(), k -> {
            BlockState shown = s.properties().isEmpty()
                    ? assets.registry().get(s.name()).map(i -> i.defaultState()).orElse(s) : s;
            return BlockIcons.icon(assets, shown);
        });
    }

    private final class Row {
        final String key;
        final BlockState state;
        final long count;
        final String title;
        final BooleanProperty checked = new SimpleBooleanProperty();

        Row(String key, BlockState state, long count) {
            this.key = key;
            this.state = state;
            this.count = count;
            this.title = BlockInfoHud.name(assets, state);
        }
    }

    private final class RowCell extends ListCell<Row> {
        private final CheckBox box = new CheckBox();
        private final ImageView iv = new ImageView();
        private final Label name = new Label(), meta = new Label(), count = new Label();
        private final HBox root;
        private Row bound;

        RowCell() {
            iv.setFitWidth(24);
            iv.setFitHeight(24);
            iv.setSmooth(false);
            name.getStyleClass().add("layer-name");
            meta.getStyleClass().add("layer-meta");
            count.getStyleClass().add("layer-meta");
            VBox text = new VBox(0, name, meta);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            root = new HBox(8, box, iv, text, spacer, count);
            root.setAlignment(Pos.CENTER_LEFT);
            // Clicking anywhere on the row (the box included) toggles it.
            box.setMouseTransparent(true);
            root.setOnMouseClicked(e -> {
                if (bound != null) bound.checked.set(!bound.checked.get());
            });
        }

        @Override
        protected void updateItem(Row r, boolean empty) {
            super.updateItem(r, empty);
            if (bound != null) box.selectedProperty().unbindBidirectional(bound.checked);
            bound = empty ? null : r;
            if (r == null || empty) {
                setGraphic(null);
                return;
            }
            box.selectedProperty().bindBidirectional(r.checked);
            iv.setImage(icon(r.state));
            name.setText(r.title);
            String props = r.state.properties().isEmpty() ? "" : r.state.toString().substring(r.state.name().length());
            meta.setText(r.state.name() + props);
            count.setText(String.format("%,d", r.count));
            setGraphic(root);
        }
    }
}
