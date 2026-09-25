package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.worldgen.LootTables;
import io.blockdesigner.worldgen.LootTables.Choice;
import io.blockdesigner.worldgen.LootTables.Container;
import io.blockdesigner.worldgen.LootTables.CustomTable;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data pack window's loot section: a loot table for each kind of container in the build, picked from
 * Minecraft's tables, tables found in the game and mods, a custom table, or any id.
 */
final class LootPanel extends VBox {
    private static final Pattern LOOT_FILE = Pattern.compile("data/([a-z0-9_.-]+)/loot_tables?/([a-z0-9_./-]+)\\.json");

    private final Workspace ws;
    private final ObservableValue<McVersion> version;
    private final Map<Container, Choice> choices = new EnumMap<>(Container.class);
    private final Map<Container, MenuButton> pickers = new EnumMap<>(Container.class);
    private final Map<Container, Button> editButtons = new EnumMap<>(Container.class);
    private final Map<Container, Label> names = new EnumMap<>(Container.class);
    private final Map<Container, Integer> counts;
    private List<String> discovered;

    LootPanel(Workspace ws, ObservableValue<McVersion> version, Map<Container, Integer> counts) {
        super(8);
        this.ws = ws;
        this.version = version;
        this.counts = counts;
        if (counts.isEmpty()) {
            getChildren().add(DatapackDialog.hint("No chests, barrels, shulker boxes, dispensers, hoppers, decorated pots or "
                    + "suspicious sand and gravel in the layers being exported. Place some and they'll be listed here."));
            return;
        }
        GridPane grid = DatapackDialog.form();
        int r = 0;
        if (counts.size() > 1) {
            MenuButton all = picker(c -> counts.keySet().forEach(k -> pick(k, c)), false);
            all.setText("Choose…");
            grid.addRow(r++, DatapackDialog.label("All containers"), all);
        }
        for (Container c : counts.keySet()) {
            Label name = DatapackDialog.label(c.label + "  ×" + counts.get(c));
            MenuButton mb = picker(ch -> pick(c, ch), c.brushable());
            Button edit = new Button("Edit…");
            edit.setVisible(false);
            edit.setOnAction(e -> {
                if (choices.get(c) instanceof Choice.Custom cu) editCustom(cu.table());
            });
            pickers.put(c, mb);
            editButtons.put(c, edit);
            names.put(c, name);
            grid.addRow(r++, name, mb, edit);
            pick(c, Choice.KEEP);
        }
        getChildren().addAll(grid, DatapackDialog.hint("Containers roll their loot the first time a player opens them (suspicious sand "
                + "and gravel when brushed), different for every copy of the structure. \"Keep as built\" leaves whatever you put in "
                + "them, including loot tables from imported structures. Custom tables are saved with your settings and written into the pack."));
        version.addListener((o, a, b) -> versionChanged());
        versionChanged();
    }

    /** The loot to export: only containers that change and that this version supports. */
    Map<Container, Choice> choices() {
        Map<Container, Choice> out = new EnumMap<>(Container.class);
        choices.forEach((c, ch) -> {
            if (!(ch instanceof Choice.Keep) && c.supports(version.getValue())) out.put(c, ch);
        });
        return out;
    }

    /** A container's choice for a saved preset, or null when it isn't in this build. */
    String encode(Container c) {
        return switch (choices.get(c)) {
            case null -> null;
            case Choice.Keep k -> "keep";
            case Choice.Empty e -> "empty";
            case Choice.Table t -> "table:" + t.id();
            case Choice.Custom cu -> "custom:" + cu.table().name();
        };
    }

    /** Restores a saved choice; a custom table deleted since then falls back to keeping the contents. */
    void decode(Container c, String v) {
        if (!pickers.containsKey(c) || v == null) return;
        Choice ch = Choice.KEEP;
        if (v.equals("empty")) ch = Choice.EMPTY;
        else if (v.startsWith("table:")) ch = new Choice.Table(v.substring(6));
        else if (v.startsWith("custom:")) {
            String n = v.substring(7);
            ch = ws.settings().lootTables.stream().filter(t -> t.name().equals(n)).findFirst().<Choice>map(Choice.Custom::new).orElse(Choice.KEEP);
        }
        pick(c, ch);
    }

    private void versionChanged() {
        McVersion v = version.getValue();
        for (Container c : pickers.keySet()) {
            boolean ok = c.supports(v);
            pickers.get(c).setDisable(!ok);
            names.get(c).setText(c.label + "  ×" + counts.get(c) + (ok ? "" : "  (not in " + v.id() + ")"));
        }
    }

    private void pick(Container c, Choice ch) {
        MenuButton mb = pickers.get(c);
        if (mb == null) return;
        choices.put(c, ch);
        mb.setText(describe(ch));
        mb.setTooltip(new Tooltip(ch instanceof Choice.Table t ? t.id() : ch instanceof Choice.Custom cu
                ? cu.table().items().size() + " kinds of item, " + cu.table().minRolls() + "–" + cu.table().maxRolls() + " rolls"
                : describe(ch)));
        editButtons.get(c).setVisible(ch instanceof Choice.Custom);
    }

    static String describe(Choice ch) {
        return switch (ch) {
            case Choice.Keep k -> "Keep as built";
            case Choice.Empty e -> "Empty";
            case Choice.Table t -> {
                LootTables.Preset p = LootTables.preset(t.id());
                yield p == null ? t.id() : p.group() + " · " + p.label();
            }
            case Choice.Custom cu -> "Custom · " + cu.table().name();
        };
    }

    /** A menu of every choice; rebuilt each time it opens so new custom tables and version changes show up. */
    private MenuButton picker(Consumer<Choice> onPick, boolean brushable) {
        MenuButton mb = new MenuButton();
        mb.setPrefWidth(300);
        mb.setAlignment(Pos.CENTER_LEFT);
        fill(mb, onPick, brushable);
        mb.setOnShowing(e -> fill(mb, onPick, brushable));
        return mb;
    }

    private void fill(MenuButton mb, Consumer<Choice> onPick, boolean brushable) {
        List<MenuItem> items = new ArrayList<>();
        items.add(item("Keep as built", () -> onPick.accept(Choice.KEEP)));
        items.add(item("Empty", () -> onPick.accept(Choice.EMPTY)));
        items.add(new SeparatorMenuItem());

        McVersion v = version.getValue();
        Map<String, Menu> groups = new LinkedHashMap<>();
        if (brushable) groups.put("Archaeology (brushing)", new Menu("Archaeology (brushing)"));
        for (LootTables.Preset p : LootTables.PRESETS) {
            if (!p.supports(v)) continue;
            MenuItem mi = item(p.label(), () -> onPick.accept(new Choice.Table(p.id())));
            mi.setUserData(p.id());
            groups.computeIfAbsent(p.group(), Menu::new).getItems().add(mi);
        }
        items.addAll(groups.values());

        // Tables the loaded game and mods ship that aren't in the list above.
        Map<String, Menu> byNamespace = new TreeMap<>();
        for (String id : discovered()) {
            if (LootTables.preset(id) != null) continue;
            String ns = id.substring(0, id.indexOf(':'));
            byNamespace.computeIfAbsent(ns, Menu::new).getItems().add(item(id.substring(ns.length() + 1), () -> onPick.accept(new Choice.Table(id))));
        }
        if (!byNamespace.isEmpty()) {
            Menu more = new Menu("From the game and mods");
            if (byNamespace.size() == 1) more.getItems().addAll(byNamespace.values().iterator().next().getItems());
            else more.getItems().addAll(byNamespace.values());
            items.add(more);
        }
        items.add(new SeparatorMenuItem());

        for (CustomTable t : ws.settings().lootTables) items.add(item("Custom · " + t.name(), () -> onPick.accept(new Choice.Custom(t))));
        items.add(item("New custom table…", () -> editCustom(null).ifPresent(t -> onPick.accept(new Choice.Custom(t)))));
        items.add(item("Loot table id…", () -> askId().ifPresent(id -> onPick.accept(new Choice.Table(id)))));
        mb.getItems().setAll(items);
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem mi = new MenuItem(text);
        mi.setOnAction(e -> action.run());
        return mi;
    }

    /** Chest and archaeology loot tables in the loaded game jar and mods (any mod table with "chest" in its path). */
    private List<String> discovered() {
        if (discovered != null) return discovered;
        BlockAssets assets = ws.assets();
        TreeSet<String> ids = new TreeSet<>();
        if (assets != null) {
            for (String path : assets.assetStack().list("data/")) {
                Matcher m = LOOT_FILE.matcher(path);
                if (!m.matches()) continue;
                String ns = m.group(1), p = m.group(2);
                if (p.startsWith("chests/") || p.startsWith("archaeology/") || (!ns.equals("minecraft") && p.contains("chest"))) ids.add(ns + ":" + p);
            }
        }
        return discovered = List.copyOf(ids);
    }

    private Optional<String> askId() {
        TextInputDialog d = new TextInputDialog();
        d.initOwner(getScene().getWindow());
        d.setTitle("Loot table id");
        d.setHeaderText("Use a loot table from Minecraft, a mod or another data pack");
        d.setContentText("Id, e.g. minecraft:chests/simple_dungeon");
        return d.showAndWait().map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .map(s -> s.contains(":") ? s : "minecraft:" + s);
    }

    /** Opens the editor for a new ({@code null}) or saved table, saves the result and updates every row using it. */
    private Optional<CustomTable> editCustom(CustomTable existing) {
        List<CustomTable> saved = ws.settings().lootTables;
        List<String> otherNames = saved.stream().filter(t -> t != existing).map(CustomTable::slug).toList();
        var result = new LootTableEditor(getScene().getWindow(), ws, existing, otherNames).showAndWait();
        if (result.isEmpty()) return Optional.empty();
        CustomTable now = result.get().table();
        int at = existing == null ? -1 : saved.indexOf(existing);
        if (now == null) {
            if (at >= 0) saved.remove(at);
        } else if (at >= 0) {
            saved.set(at, now);
        } else {
            saved.add(now);
        }
        ws.settings().save();
        if (existing != null) {
            for (Container c : new ArrayList<>(choices.keySet())) {
                if (choices.get(c) instanceof Choice.Custom cu && cu.table().equals(existing)) pick(c, now == null ? Choice.KEEP : new Choice.Custom(now));
            }
        }
        return Optional.ofNullable(now);
    }
}
