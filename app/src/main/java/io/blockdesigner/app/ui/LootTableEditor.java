package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.worldgen.LootTables;
import io.blockdesigner.worldgen.LootTables.CustomTable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Makes or changes a custom loot table: how many rolls, and which items each roll can pick. */
final class LootTableEditor extends Dialog<LootTableEditor.Result> {
    private static final Pattern ITEM_FILE = Pattern.compile("assets/([a-z0-9_.-]+)/(?:items|models/item)/([a-z0-9_./-]+)\\.json");
    private static BlockAssets idsFor;
    private static List<String> itemIds = List.of();

    /** The saved table, or {@code table == null} when it was deleted. */
    record Result(CustomTable table) {
    }

    private record Row(TextField item, Spinner<Integer> weight, Spinner<Integer> min, Spinner<Integer> max, CheckBox enchant,
                       Label chance, Button remove, HBox count) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final GridPane grid = new GridPane();
    private final List<String> ids;

    LootTableEditor(Window owner, Workspace ws, CustomTable existing, List<String> takenNames) {
        initOwner(owner);
        setTitle(existing == null ? "New loot table" : "Edit loot table");
        setResizable(true);
        getDialogPane().getStylesheets().add(LootTableEditor.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        getDialogPane().getStyleClass().addAll("app-root", ws.darkProperty().get() ? "dark" : "light");
        ids = itemIds(ws.assets());

        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText("e.g. Pirate treasure");
        Spinner<Integer> minRolls = spinner(0, 64, existing == null ? 3 : existing.minRolls(), 72);
        Spinner<Integer> maxRolls = spinner(0, 64, existing == null ? 6 : existing.maxRolls(), 72);
        HBox rollsRow = new HBox(8, minRolls, new Label("to"), maxRolls, new Label("rolls"));
        rollsRow.setAlignment(Pos.CENTER_LEFT);
        GridPane top = new GridPane();
        top.setHgap(12);
        top.setVgap(8);
        top.addRow(0, DatapackDialog.label("Name"), name);
        top.addRow(1, DatapackDialog.label("Each fill makes"), rollsRow);
        top.add(DatapackDialog.hint("Each roll picks one of the items below; items with more weight come up more often. "
                + "A chest with 27 slots can hold at most 27 stacks."), 1, 2);

        grid.setHgap(8);
        grid.setVgap(6);
        javafx.scene.layout.ColumnConstraints itemCol = new javafx.scene.layout.ColumnConstraints(160, 240, Double.MAX_VALUE);
        itemCol.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().add(itemCol);
        Button add = new Button("Add item");
        add.setOnAction(e -> addRow(new LootTables.Item("", 1, 1, 1, false)).item().requestFocus());
        if (existing == null) {
            addRow(new LootTables.Item("minecraft:bread", 10, 1, 4, false));
            addRow(new LootTables.Item("minecraft:iron_ingot", 5, 1, 3, false));
        } else {
            existing.items().forEach(this::addRow);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(260);
        Label errors = DatapackDialog.hint("");
        errors.getStyleClass().setAll("plugin-error");
        errors.setWrapText(true);
        VBox body = new VBox(10, top, DatapackDialog.section("Items"), scroll, add, errors);
        body.setPadding(new Insets(4, 8, 4, 4));
        getDialogPane().setContent(body);
        getDialogPane().setPrefWidth(780);
        getDialogPane().setMinWidth(720);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType delete = new ButtonType("Delete table", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        if (existing != null) getDialogPane().getButtonTypes().add(delete);

        getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            CustomTable t = table(name.getText(), minRolls.getValue(), maxRolls.getValue());
            List<String> problems = new ArrayList<>(LootTables.validate(t));
            if (takenNames.contains(t.slug())) problems.add("There's already a loot table called '" + t.name().strip() + "'");
            if (!problems.isEmpty()) {
                errors.setText(String.join("\n", problems));
                e.consume();
            }
        });
        setResultConverter(b -> b == save ? new Result(table(name.getText(), minRolls.getValue(), maxRolls.getValue()))
                : b == delete ? new Result(null) : null);
    }

    private CustomTable table(String name, int minRolls, int maxRolls) {
        List<LootTables.Item> items = new ArrayList<>();
        for (Row r : rows) {
            if (r.item().getText().isBlank()) continue;
            items.add(new LootTables.Item(itemId(r.item().getText()), r.weight().getValue(), r.min().getValue(), r.max().getValue(),
                    r.enchant().isSelected()));
        }
        return new CustomTable(name.strip(), minRolls, maxRolls, items);
    }

    private static String itemId(String text) {
        String t = text.strip().toLowerCase(Locale.ROOT);
        return t.contains(":") ? t : "minecraft:" + t;
    }

    private Row addRow(LootTables.Item it) {
        TextField item = new TextField(it.item());
        item.setPromptText("item id, e.g. diamond");
        autocomplete(item);
        Spinner<Integer> weight = spinner(1, 1000, it.weight(), 96);
        Spinner<Integer> min = spinner(1, 64, it.min(), 76);
        Spinner<Integer> max = spinner(1, 64, it.max(), 76);
        HBox count = new HBox(4, min, new Label("–"), max);
        count.setAlignment(Pos.CENTER_LEFT);
        count.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        CheckBox enchant = new CheckBox("Enchant");
        enchant.setSelected(it.enchant());
        enchant.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        enchant.setTooltip(new javafx.scene.control.Tooltip("Give it a random enchantment (tools, weapons, armour and books)"));
        Label chance = new Label();
        chance.setMinWidth(52);
        Button remove = new Button("✕");
        remove.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        remove.setTooltip(new javafx.scene.control.Tooltip("Remove this item"));
        Row row = new Row(item, weight, min, max, enchant, chance, remove, count);
        remove.setOnAction(e -> {
            rows.remove(row);
            relayout();
        });
        weight.valueProperty().addListener((o, a, b) -> updateChances());
        rows.add(row);
        relayout();
        return row;
    }

    private void relayout() {
        grid.getChildren().clear();
        grid.addRow(0, head("Item"), head("Weight"), head("Count"), head(""), head("Chance"));
        int i = 1;
        for (Row r : rows) grid.addRow(i++, r.item(), r.weight(), r.count(), r.enchant(), r.chance(), r.remove());
        updateChances();
    }

    /** Each item's share of the picks. */
    private void updateChances() {
        int total = rows.stream().mapToInt(r -> r.weight().getValue()).sum();
        for (Row r : rows) {
            double pct = total == 0 ? 0 : 100.0 * r.weight().getValue() / total;
            r.chance().setText(pct >= 10 || pct == 0 ? Math.round(pct) + "%" : String.format(Locale.ROOT, "%.1f%%", pct));
        }
    }

    /** Suggests item ids from the loaded game and mods while typing. */
    private void autocomplete(TextField field) {
        ContextMenu pop = new ContextMenu();
        field.textProperty().addListener((o, a, b) -> {
            String q = b.strip().toLowerCase(Locale.ROOT);
            if (!field.isFocused() || q.isEmpty() || ids.isEmpty()) {
                pop.hide();
                return;
            }
            String bare = q.contains(":") ? q : ":" + q;
            List<String> hits = ids.stream().filter(id -> id.contains(q))
                    .sorted(Comparator.comparing((String id) -> !id.contains(bare)).thenComparing(String::length))
                    .limit(12).toList();
            if (hits.isEmpty() || hits.getFirst().equals(q) || hits.getFirst().equals("minecraft:" + q)) {
                pop.hide();
                return;
            }
            List<MenuItem> items = new ArrayList<>();
            for (String id : hits) {
                MenuItem mi = new MenuItem(id);
                mi.setOnAction(e -> {
                    field.setText(id);
                    field.positionCaret(id.length());
                    pop.hide();
                });
                items.add(mi);
            }
            pop.getItems().setAll(items);
            if (!pop.isShowing()) pop.show(field, Side.BOTTOM, 0, 0);
        });
        field.focusedProperty().addListener((o, a, b) -> {
            if (!b) pop.hide();
        });
    }

    /** Item ids (item models in 1.21.4+ {@code items/}, older {@code models/item/}) across the game and mods. */
    private static synchronized List<String> itemIds(BlockAssets assets) {
        if (assets == null) return List.of();
        if (assets != idsFor) {
            TreeSet<String> out = new TreeSet<>();
            for (String path : assets.assetStack().list("assets/")) {
                Matcher m = ITEM_FILE.matcher(path);
                if (m.matches() && !m.group(2).contains("/")) out.add(m.group(1) + ":" + m.group(2));
            }
            itemIds = List.copyOf(out);
            idsFor = assets;
        }
        return itemIds;
    }

    private static Spinner<Integer> spinner(int min, int max, int value, double width) {
        Spinner<Integer> s = new Spinner<>(min, max, value);
        s.setEditable(true);
        s.setPrefWidth(width);
        return s;
    }

    private static Label head(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }
}
