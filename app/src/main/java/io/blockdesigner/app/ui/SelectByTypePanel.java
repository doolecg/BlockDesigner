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
 * Select mode's "By type" panel: tick the block types (or exact states) you want, then either select them or replace
 * them with another block. The list shows what the chosen layers actually contain, with counts; where to look and
 * property filters sit under "More options".
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
        REPLACE("New selection"), ADD("Add to selection"), REMOVE("Remove from selection");

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

    /**
     * Replace the ticked blocks with {@code target} (air removes them); with {@code keepProperties}, each keeps its
     * facing, half, waterlogging and any other property the new block also has.
     */
    public record Replace(Query query, Set<String> keys, BlockState target, boolean keepProperties) {
        /** The state a found (world-facing) block becomes. */
        public BlockState result(BlockState found, BlockAssets assets) {
            if (!keepProperties || target.isAir()) return target;
            var info = assets == null ? null : assets.registry().get(target.name()).orElse(null);
            BlockState out = assets == null ? target : assets.registry().complete(target);
            for (var e : found.properties().entrySet()) {
                boolean valid = info == null ? out.get(e.getKey()) != null
                        : info.properties().getOrDefault(e.getKey(), List.of()).contains(e.getValue());
                if (valid) out = out.with(e.getKey(), e.getValue());
            }
            return out;
        }
    }

    private final BlockAssets assets;
    private final Function<Query, Map<String, Long>> counter;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> shown = new FilteredList<>(rows);
    private final Map<String, Image> icons = new HashMap<>();
    private final Label summary = new Label();
    private final BooleanProperty anyChecked = new SimpleBooleanProperty();
    private Query query;

    /** What the panel's button does: select the ticked blocks, or replace them. */
    private final BooleanProperty replacing = new SimpleBooleanProperty();
    private BlockState target;
    private final ImageView targetIcon = new ImageView();
    private final Label targetName = new Label();

    /**
     * @param counter   counts the matching blocks per key for a query
     * @param preselect the block to start with ticked (world-facing), or null
     * @param held      the block in hand, offered as the replacement (or null)
     */
    public SelectByTypePanel(BlockAssets assets, Scope defaultScope, boolean hasSelection, boolean sliceActive,
                             BlockState preselect, BlockState held, Function<Query, Map<String, Long>> counter,
                             java.util.function.Consumer<Options> onSelect, java.util.function.Consumer<Replace> onReplace,
                             Runnable onClose) {
        this.assets = assets;
        this.counter = counter;
        getStyleClass().add("by-type");

        // 1. What to do: two big pills.
        ToggleGroup actionGroup = new ToggleGroup();
        javafx.scene.control.ToggleButton selectAction = new javafx.scene.control.ToggleButton("Select", new FontIcon(Feather.MOUSE_POINTER));
        javafx.scene.control.ToggleButton replaceAction = new javafx.scene.control.ToggleButton("Replace", new FontIcon(Feather.REFRESH_CW));
        selectAction.getStyleClass().add("left-pill");
        replaceAction.getStyleClass().add("right-pill");
        for (var b : List.of(selectAction, replaceAction)) {
            b.setToggleGroup(actionGroup);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        selectAction.setSelected(true);
        actionGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
            else replacing.set(b == replaceAction);
        });
        HBox action = new HBox(0, selectAction, replaceAction);

        Label step1 = step("1", "Tick the blocks to find");

        // Scope and filters (under More options)
        ComboBox<Scope> scope = new ComboBox<>();
        scope.getItems().setAll(Scope.values());
        if (!hasSelection) scope.getItems().remove(Scope.SELECTION);
        scope.setValue(scope.getItems().contains(defaultScope) ? defaultScope : Scope.VISIBLE);
        scope.setMaxWidth(Double.MAX_VALUE);

        ToggleGroup matchGroup = new ToggleGroup();
        RadioButton byType = new RadioButton("Any facing / state");
        RadioButton byState = new RadioButton("Exact state only");
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
        list.setPrefHeight(260);
        list.setPlaceholder(new Label("None of these blocks here. Try another place to look under More options."));
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
        g.setVgap(8);
        g.addRow(0, new Label("Look in"), scope);
        g.addRow(1, new Label("Match"), match);
        g.addRow(2, new Label("Properties"), props);
        g.add(propsHint, 1, 3);
        g.add(slice, 1, 4);
        GridPane.setHgrow(scope, Priority.ALWAYS);
        javafx.scene.control.TitledPane more = new javafx.scene.control.TitledPane(null, g);
        more.setExpanded(false);
        more.setAnimated(false);
        Label scopeNote = new Label();
        scopeNote.getStyleClass().add("layer-meta");
        Runnable moreTitle = () -> scopeNote.setText("Looking in: " + scope.getValue().label.toLowerCase(Locale.ROOT));
        moreTitle.run();
        more.setText("More options");
        HBox scopeRow = new HBox(scopeNote);

        // 2a. Select: how it combines with what is selected.
        ToggleGroup modeGroup = new ToggleGroup();
        HBox modes = new HBox(0);
        Mode[] allModes = Mode.values();
        for (int i = 0; i < allModes.length; i++) {
            javafx.scene.control.ToggleButton b = new javafx.scene.control.ToggleButton(allModes[i].label);
            b.setUserData(allModes[i]);
            b.setToggleGroup(modeGroup);
            b.getStyleClass().addAll("small", i == 0 ? "left-pill" : i == allModes.length - 1 ? "right-pill" : "center-pill");
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            if (i == 0) b.setSelected(true);
            modes.getChildren().add(b);
        }
        modeGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
        });
        VBox selectStep = new VBox(6, step("2", "Then"), modes);

        // 2b. Replace: the block to put in their place.
        targetIcon.setFitWidth(28);
        targetIcon.setFitHeight(28);
        targetIcon.setSmooth(false);
        targetName.getStyleClass().add("layer-name");
        HBox targetCard = new HBox(8, targetIcon, targetName);
        targetCard.setAlignment(Pos.CENTER_LEFT);
        targetCard.getStyleClass().add("by-type-target");
        TextField targetSearch = new TextField();
        targetSearch.setPromptText("Search a block to replace with…");
        targetSearch.getStyleClass().add("search-field");
        HBox targetSearchBox = new HBox(6, new FontIcon(Feather.SEARCH), targetSearch);
        targetSearchBox.setAlignment(Pos.CENTER_LEFT);
        targetSearchBox.getStyleClass().add("search-box");
        HBox.setHgrow(targetSearch, Priority.ALWAYS);
        ListView<BlockState> results = new ListView<>();
        results.setPrefHeight(132);
        results.setCellFactory(v -> new ListCell<>() {
            private final ImageView iv = new ImageView();

            {
                iv.setFitWidth(20);
                iv.setFitHeight(20);
                iv.setSmooth(false);
            }

            @Override
            protected void updateItem(BlockState st, boolean empty) {
                super.updateItem(st, empty);
                if (st == null || empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                iv.setImage(st.isAir() ? null : icon(st));
                setGraphic(st.isAir() ? new FontIcon(Feather.TRASH_2) : iv);
                setText(st.isAir() ? "Air (remove them)" : BlockInfoHud.name(assets, st) + "   " + st.name());
            }
        });
        results.setVisible(false);
        results.setManaged(false);
        targetSearch.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.strip();
            List<BlockState> found = new java.util.ArrayList<>();
            if (!q.isEmpty() && assets != null) {
                if ("air".startsWith(q.toLowerCase(Locale.ROOT)) || q.toLowerCase(Locale.ROOT).startsWith("remove")) found.add(BlockState.AIR);
                for (var info : assets.registry().search(q, 60)) found.add(info.defaultState());
            }
            results.getItems().setAll(found);
            results.setVisible(!found.isEmpty());
            results.setManaged(!found.isEmpty());
        });
        results.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) return;
            setTarget(b);
            javafx.application.Platform.runLater(() -> {
                targetSearch.clear();
                results.getSelectionModel().clearSelection();
            });
        });
        targetSearch.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DOWN && !results.getItems().isEmpty()) {
                results.requestFocus();
                results.getSelectionModel().selectFirst();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !results.getItems().isEmpty()) {
                setTarget(results.getItems().getFirst());
                targetSearch.clear();
                e.consume();
            }
        });
        Button useHeld = new Button("Held block", new FontIcon(Feather.PACKAGE));
        useHeld.getStyleClass().addAll("small", "flat");
        useHeld.setDisable(held == null);
        useHeld.setOnAction(e -> setTarget(held));
        Button useAir = new Button("Air", new FontIcon(Feather.TRASH_2));
        useAir.getStyleClass().addAll("small", "flat");
        useAir.setTooltip(new javafx.scene.control.Tooltip("Remove the ticked blocks"));
        useAir.setOnAction(e -> setTarget(BlockState.AIR));
        Region tgrow = new Region();
        HBox.setHgrow(tgrow, Priority.ALWAYS);
        HBox targetRow = new HBox(6, targetCard, tgrow, useHeld, useAir);
        targetRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox keep = new CheckBox("Keep facing, half and other matching properties");
        keep.setSelected(true);
        keep.setTooltip(new javafx.scene.control.Tooltip("Oak stairs facing east become stone brick stairs facing east, and so on"));
        VBox replaceStep = new VBox(6, step("2", "Replace them with"), targetRow, targetSearchBox, results, keep);
        setTarget(held);

        selectStep.visibleProperty().bind(replacing.not());
        selectStep.managedProperty().bind(replacing.not());
        replaceStep.visibleProperty().bind(replacing);
        replaceStep.managedProperty().bind(replacing);

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> onClose.run());
        ok.getStyleClass().add("accent");
        ok.setDefaultButton(true);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox buttons = new HBox(8, grow, cancel, ok);

        setSpacing(10);
        setPadding(new Insets(2, 2, 2, 2));
        setPrefWidth(500);
        getChildren().addAll(action, step1, searchBox, listBar, list, scopeRow, more, selectStep, replaceStep, buttons);

        Runnable refresh = () -> {
            Map<String, Set<String>> filter = parseProperties(props.getText());
            propsHint.setText(filter.isEmpty() ? "Blank matches every block" : "Blocks without these properties are skipped");
            moreTitle.run();
            refresh(new Query(scope.getValue(), byState.isSelected(), filter, slice.isSelected()));
        };
        scope.valueProperty().addListener((o, a, b) -> refresh.run());
        matchGroup.selectedToggleProperty().addListener((o, a, b) -> refresh.run());
        props.textProperty().addListener((o, a, b) -> refresh.run());
        slice.selectedProperty().addListener((o, a, b) -> refresh.run());
        replacing.addListener((o, a, b) -> updateSummary());

        refresh.run();
        if (preselect != null) {
            for (Row r : rows) if (r.state.name().equals(preselect.name())) r.checked.set(true);
        }
        updateSummary();

        ok.setOnAction(e -> {
            if (replacing.get()) {
                if (target == null) return;
                onReplace.accept(new Replace(query, checkedKeys(), target, keep.isSelected()));
            } else {
                onSelect.accept(new Options(query, checkedKeys(), (Mode) modeGroup.getSelectedToggle().getUserData()));
            }
            onClose.run();
        });
        sceneProperty().addListener((o, a, b) -> {
            if (b != null) javafx.application.Platform.runLater(search::requestFocus);
        });
    }

    private final Button ok = new Button();

    /** A numbered step heading. */
    private static Label step(String n, String text) {
        Label num = new Label(n);
        num.getStyleClass().add("by-type-step-num");
        Label l = new Label(text, num);
        l.getStyleClass().add("by-type-step");
        return l;
    }

    private void setTarget(BlockState st) {
        target = st;
        if (st == null) {
            targetIcon.setImage(null);
            targetName.setText("Pick a block below, or hold one");
        } else if (st.isAir()) {
            targetIcon.setImage(null);
            targetName.setText("Air (removes them)");
        } else {
            targetIcon.setImage(icon(st));
            targetName.setText(BlockInfoHud.name(assets, st));
        }
        updateSummary();
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
        String count = String.format("%,d block%s", blocks, blocks == 1 ? "" : "s");
        ok.setText(types == 0 ? (replacing.get() ? "Replace" : "Select")
                : replacing.get() ? (target != null && target.isAir() ? "Remove " : "Replace ") + count : "Select " + count);
        ok.setDisable(types == 0 || (replacing.get() && target == null));
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
