package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.BlockRegistry;
import io.blockdesigner.assets.CreativeOrder;
import io.blockdesigner.assets.Dir;
import io.blockdesigner.assets.TextureAtlas;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.core.model.BlockState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Searchable grid of blocks with three tabs (the game's own blocks, the blocks your layers use, and blocks from mods),
 * a fourth for mobs and other entities to place as stand-ins, plus an editor for the held block's properties (or the
 * held mob's: colour, profession, baby…).
 */
public final class BlockPalette extends VBox {
    private static final int TILE = 34;

    private final Workspace ws;
    private final TextField search = new TextField();
    private final TilePane tiles = new TilePane();
    /** Category tabs, drawn like the creative inventory's: a block icon per tab, its name as the tooltip and title. */
    private final FlowPane categories = new FlowPane(3, 3);
    private final Label categoryTitle = new Label("All blocks");
    private String categoryName = "All blocks", mobGroupTitle = "All mobs";
    /** Each tab's icon: a block id drawn from the assets, or (no assets / All / Recent) a Feather glyph. */
    private final List<Runnable> tabIconRefresh = new ArrayList<>();
    private final VBox selectedCard = new VBox(6);
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private final Map<String, StackPane> tileCache = new HashMap<>();
    private Predicate<BlockRegistry.BlockInfo> category = b -> true;
    /** Mobs tab: the group chips and the group shown (null: all). */
    private final FlowPane mobGroups = new FlowPane(3, 3);
    private String mobGroup;

    /** Which blocks the palette lists. */
    private enum Source {
        GAME("Game Blocks", "Every block in Minecraft itself"),
        SCHEMATIC("Schematic Blocks", "The blocks your layers use, most used first"),
        MODDED("Modded Blocks", "Blocks from the mods of the chosen launcher instance"),
        MOBS("Mobs", "Animals, villagers, monsters, armour stands and paintings to place in your build. They are saved and exported with it");

        final String label, tip;

        Source(String label, String tip) {
            this.label = label;
            this.tip = tip;
        }
    }

    private Source source = Source.GAME;
    private final HBox tabs = new HBox(2);
    private final Map<Source, ToggleButton> tabButtons = new java.util.EnumMap<>(Source.class);
    // Block id -> how many of it the layers hold (recounted a moment after edits, while that tab is showing)
    private final Map<String, Long> schematicCounts = new HashMap<>();
    private boolean schematicDirty = true;
    private final javafx.animation.PauseTransition schematicRefresh = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));

    public BlockPalette(Workspace ws) {
        this.ws = ws;
        getStyleClass().add("side-panel");
        setSpacing(8);

        Label title = new Label("Blocks");
        title.getStyleClass().add("panel-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("panel-header");

        search.setPromptText("Search blocks…  (" + Keybinds.keyOf(Keybinds.Action.SEARCH_BLOCKS) + " · e.g. oak stairs, create:shaft)");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((o, a, b) -> rebuild());
        HBox searchBox = new HBox(6, new FontIcon(Feather.SEARCH), search);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");
        HBox.setHgrow(search, Priority.ALWAYS);

        buildTabs();
        buildCategories();
        buildMobGroups();

        tiles.setPrefTileWidth(TILE);
        tiles.setPrefTileHeight(TILE);
        tiles.setHgap(4);
        tiles.setVgap(4);
        tiles.setPadding(new Insets(4));
        tiles.getStyleClass().add("block-tiles");
        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        selectedCard.getStyleClass().add("selected-block-card");

        categoryTitle.getStyleClass().add("palette-category-title");

        getChildren().addAll(header, tabs, searchBox, categories, mobGroups, categoryTitle, scroll, selectedCard);

        schematicRefresh.setOnFinished(e -> {
            if (source == Source.SCHEMATIC) rebuild();
            else updateTabCounts();
        });
        ws.scene().addListener(new io.blockdesigner.core.model.Scene.Listener() {
            @Override
            public void layerAdded(io.blockdesigner.core.model.Layer layer, int index) {
                schematicChanged();
            }

            @Override
            public void layerRemoved(io.blockdesigner.core.model.Layer layer) {
                schematicChanged();
            }

            @Override
            public void blocksChanged(io.blockdesigner.core.model.Layer layer, io.blockdesigner.core.model.Box localBox) {
                schematicChanged();
            }
        });

        ws.assetsProperty().addListener((o, a, b) -> {
            tileCache.clear();
            tabIconRefresh.forEach(Runnable::run);
            rebuild();
            updateSelectedCard();
        });
        ws.selectedBlockProperty().addListener((o, a, b) -> {
            if (b != null) {
                recent.remove(b.name());
                recent.addFirst(b.name());
                while (recent.size() > 24) recent.removeLast();
            }
            updateSelectedCard();
        });
        ws.heldEntityProperty().addListener((o, a, b) -> updateSelectedCard());
        rebuild();
        updateSelectedCard();
    }

    /**
     * The block under the mouse in the grid, or null. As in Minecraft's creative inventory, pressing a hotbar key over
     * a block puts it in that slot (MainWindow asks this before switching slots).
     */
    public BlockState hoveredBlock() {
        if (source == Source.MOBS) return null;
        for (javafx.scene.Node n : tiles.getChildren()) {
            if (n.isHover() && n.getUserData() instanceof BlockState st) return st;
        }
        return null;
    }

    /** Ctrl+F: jumps to the block search box. */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    private void buildTabs() {
        ToggleGroup group = new ToggleGroup();
        for (Source src : Source.values()) {
            ToggleButton t = new ToggleButton(src.label);
            t.getStyleClass().add("palette-tab");
            t.setToggleGroup(group);
            t.setTooltip(new Tooltip(src.tip));
            t.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setOnAction(e -> {
                if (!t.isSelected()) t.setSelected(true);
                source = src;
                rebuild();
            });
            tabButtons.put(src, t);
            tabs.getChildren().add(t);
        }
        tabButtons.get(source).setSelected(true);
        tabs.getStyleClass().add("palette-tabs");
    }

    private void schematicChanged() {
        schematicDirty = true;
        schematicRefresh.playFromStart();
    }

    /** Counts the blocks in every layer by id (states ignored). */
    private void recountSchematic() {
        if (!schematicDirty) return;
        schematicDirty = false;
        schematicCounts.clear();
        // Per-state counts come from one pass over each palette-indexed section, no callback per block.
        for (var l : ws.scene().layers()) {
            l.structure().stateCounts().forEach((st, n) -> schematicCounts.merge(st.name(), n, Long::sum));
        }
    }

    private void updateTabCounts() {
        BlockAssets assets = ws.assets();
        recountSchematic();
        long game = 0, modded = 0;
        if (assets != null) {
            for (BlockRegistry.BlockInfo b : assets.registry().all()) {
                if (b.namespace().equals("minecraft")) game++;
                else modded++;
            }
        }
        tabButtons.get(Source.GAME).getTooltip().setText(Source.GAME.tip + String.format(" (%,d)", game));
        tabButtons.get(Source.SCHEMATIC).getTooltip().setText(Source.SCHEMATIC.tip + String.format(" (%,d kinds)", schematicCounts.size()));
        tabButtons.get(Source.MODDED).getTooltip().setText(Source.MODDED.tip + String.format(" (%,d)", modded));
        tabButtons.get(Source.MOBS).getTooltip().setText(Source.MOBS.tip + String.format(" (%d kinds)", io.blockdesigner.core.model.EntityTypes.all().size()));
    }

    /** A creative-style tab: its name, the block its icon shows (null: the Feather glyph) and which blocks it holds. */
    private record Category(String name, String iconBlock, Feather glyph, Predicate<BlockRegistry.BlockInfo> test) {
    }

    private void buildCategories() {
        // Minecraft's own block tabs, with the icons the creative menu gives them.
        List<Category> cats = new ArrayList<>(List.of(
                new Category("All blocks", null, Feather.GRID, b -> true),
                new Category("Recently used", null, Feather.CLOCK, b -> recent.contains(b.id()))));
        String[] icons = {"minecraft:bricks", "minecraft:cyan_wool", "minecraft:grass_block", "minecraft:oak_sign", "minecraft:redstone"};
        for (CreativeOrder.Tab tab : CreativeOrder.Tab.values())
            cats.add(new Category(tab.label, icons[tab.ordinal()], null, b -> creativeTab(b) == tab));
        ToggleGroup group = new ToggleGroup();
        for (Category c : cats) {
            ToggleButton t = categoryTab(group, c.name(), () -> {
                if (c.iconBlock() == null || ws.assets() == null) return new FontIcon(c.glyph() != null ? c.glyph() : Feather.BOX);
                // Some tab icons are items, not blocks (redstone dust): those draw from their item texture.
                var info = ws.assets().registry().get(c.iconBlock());
                return iconView(BlockIcons.icon(ws.assets(), info.map(BlockRegistry.BlockInfo::defaultState).orElse(BlockState.of(c.iconBlock()))));
            });
            t.setOnAction(e -> {
                if (!t.isSelected()) t.setSelected(true);
                category = c.test();
                categoryName = c.name();
                rebuild();
            });
            if (c == cats.getFirst()) t.setSelected(true);
            categories.getChildren().add(t);
        }
    }

    /** A square tab holding just an icon, redrawn when the assets change; the name shows as its tooltip. */
    private ToggleButton categoryTab(ToggleGroup group, String name, java.util.function.Supplier<javafx.scene.Node> icon) {
        ToggleButton t = new ToggleButton(null, icon.get());
        t.getStyleClass().add("palette-category");
        t.setToggleGroup(group);
        t.setTooltip(new Tooltip(name));
        tabIconRefresh.add(() -> t.setGraphic(icon.get()));
        return t;
    }

    private static ImageView iconView(Image img) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(22);
        iv.setFitHeight(22);
        iv.setSmooth(false);
        return iv;
    }

    private void buildMobGroups() {
        ToggleGroup group = new ToggleGroup();
        List<String> names = new ArrayList<>(List.of("All"));
        names.addAll(io.blockdesigner.core.model.EntityTypes.groups());
        for (String name : names) {
            // Each group shows its first mob, like the creative tab showing one of its items.
            String iconId = name.equals("All") ? null : io.blockdesigner.core.model.EntityTypes.all().stream()
                    .filter(k -> k.group().equals(name)).map(io.blockdesigner.core.model.EntityTypes.Kind::id).findFirst().orElse(null);
            String title = name.equals("All") ? "All mobs" : name;
            ToggleButton t = categoryTab(group, title,
                    () -> iconId == null ? new FontIcon(Feather.GRID) : iconView(EntityIcons.icon(ws.assets(), iconId)));
            t.setOnAction(e -> {
                if (!t.isSelected()) t.setSelected(true);
                mobGroup = name.equals("All") ? null : name;
                mobGroupTitle = title;
                rebuild();
            });
            if (name.equals("All")) t.setSelected(true);
            mobGroups.getChildren().add(t);
        }
    }

    private final Map<String, CreativeOrder.Tab> tabOf = new HashMap<>();

    /** The creative tab a block is in; modded blocks (which the game's list doesn't have) are guessed from their name. */
    private CreativeOrder.Tab creativeTab(BlockRegistry.BlockInfo b) {
        return tabOf.computeIfAbsent(b.id(), id -> {
            CreativeOrder.Tab t = CreativeOrder.tab(id);
            if (t != null) return t;
            if (has(b, "redstone", "piston", "observer", "repeater", "comparator", "hopper", "dropper", "dispenser", "lever", "button",
                    "pressure_plate", "rail", "target", "tnt", "daylight", "note_block", "sculk_sensor", "tripwire")) return CreativeOrder.Tab.REDSTONE_BLOCKS;
            if (has(b, "crafting", "furnace", "smoker", "chest", "barrel", "anvil", "table", "loom", "stonecutter", "grindstone",
                    "brewing", "cauldron", "beacon", "bell", "lectern", "composter", "jukebox", "sign", "ladder", "scaffolding",
                    "flower_pot", "lantern", "torch", "lamp", "campfire", "chain", "head", "skull", "bed")) return CreativeOrder.Tab.FUNCTIONAL_BLOCKS;
            if (has(b, "wool", "concrete", "terracotta", "stained", "carpet", "candle", "banner", "shulker_box", "dyed")) return CreativeOrder.Tab.COLORED_BLOCKS;
            if (has(b, "leaves", "grass", "dirt", "sand", "gravel", "flower", "sapling", "moss", "mud", "clay", "snow", "ice", "coral",
                    "mushroom", "vine", "fern", "_ore", "crop", "bush", "root")) return CreativeOrder.Tab.NATURAL_BLOCKS;
            return CreativeOrder.Tab.BUILDING_BLOCKS;
        });
    }

    private static boolean has(BlockRegistry.BlockInfo b, String... words) {
        String p = b.path();
        for (String w : words) if (p.contains(w)) return true;
        return false;
    }

    private void rebuild() {
        BlockAssets assets = ws.assets();
        tiles.getChildren().clear();
        updateTabCounts();
        boolean mobs = source == Source.MOBS;
        categories.setVisible(!mobs);
        categories.setManaged(!mobs);
        mobGroups.setVisible(mobs);
        mobGroups.setManaged(mobs);
        categoryTitle.setText(mobs ? mobGroupTitle : categoryName);
        search.setPromptText(mobs ? "Search mobs…  (or type a modded id, e.g. alexsmobs:elephant)" : "Search blocks…  (" + Keybinds.keyOf(Keybinds.Action.SEARCH_BLOCKS) + " · e.g. oak stairs, create:shaft)");
        if (mobs) {
            rebuildMobs();
            return;
        }
        if (assets == null) return;
        String q = search.getText() == null ? "" : search.getText().strip();
        List<BlockRegistry.BlockInfo> blocks;
        if (source == Source.SCHEMATIC) {
            // Most used first; blocks the loaded assets don't know still get a (fallback) tile.
            String ql = q.toLowerCase(Locale.ROOT);
            blocks = new ArrayList<>();
            schematicCounts.entrySet().stream()
                    .sorted((a, b) -> a.getValue().equals(b.getValue()) ? a.getKey().compareTo(b.getKey()) : Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> {
                        BlockRegistry.BlockInfo info = assets.registry().get(e.getKey())
                                .orElseGet(() -> new BlockRegistry.BlockInfo(e.getKey(), Map.of(), BlockState.of(e.getKey())));
                        if (ql.isEmpty() || info.id().contains(ql) || info.displayName().toLowerCase(Locale.ROOT).contains(ql)) blocks.add(info);
                    });
        } else {
            boolean modded = source == Source.MODDED;
            blocks = new ArrayList<>(q.isEmpty() ? assets.registry().all() : assets.registry().search(q, 2000));
            blocks.removeIf(b -> b.namespace().equals("minecraft") == modded);
            // In the creative menu's order; blocks it doesn't list (and modded ones) follow by id.
            if (q.isEmpty()) blocks.sort((a, b) -> CreativeOrder.compare(a.id(), b.id()));
        }
        for (BlockRegistry.BlockInfo b : blocks) {
            if (!category.test(b)) continue;
            tiles.getChildren().add(tileCache.computeIfAbsent(b.id(), id -> tile(assets, b)));
        }
        if (tiles.getChildren().isEmpty()) {
            Label none = new Label(!q.isEmpty() ? "No blocks match \"" + q + "\" here"
                    : switch (source) {
                        case MOBS -> "";
                        case SCHEMATIC -> "No blocks in your layers yet. Import a schematic or start building.";
                        case MODDED -> "No modded blocks loaded. Choose a launcher instance with mods under Minecraft assets & mods (⚙ in the top bar).";
                        case GAME -> "No blocks in this category.";
                    });
            none.setWrapText(true);
            none.getStyleClass().add("placeholder-text");
            none.setMaxWidth(260);
            tiles.getChildren().add(none);
        }
    }

    /** The Mobs tab: every known entity type (by group), and a tile for any other id typed into the search. */
    private void rebuildMobs() {
        String q = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        for (var k : io.blockdesigner.core.model.EntityTypes.all()) {
            if (mobGroup != null && !mobGroup.equals(k.group())) continue;
            if (!q.isEmpty() && !k.id().contains(q.replace(' ', '_')) && !k.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            tiles.getChildren().add(tileCache.computeIfAbsent("entity|" + k.id(), x -> mobTile(k.id(), k.name())));
        }
        if (q.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && !io.blockdesigner.core.model.EntityTypes.known(q)) {
            // Modded mobs: any id works; it is drawn as a box until the game knows it.
            tiles.getChildren().add(mobTile(q, io.blockdesigner.core.model.EntityTypes.nameOf(q)));
        }
        if (tiles.getChildren().isEmpty()) {
            Label none = new Label("No mobs match \"" + q + "\". Type a full id (namespace:name) to place a modded one.");
            none.setWrapText(true);
            none.getStyleClass().add("placeholder-text");
            none.setMaxWidth(260);
            tiles.getChildren().add(none);
        }
    }

    private StackPane mobTile(String id, String name) {
        ImageView iv = new ImageView(EntityIcons.icon(ws.assets(), id));
        iv.setFitWidth(TILE - 6);
        iv.setFitHeight(TILE - 6);
        iv.setSmooth(false);
        StackPane p = new StackPane(iv);
        p.getStyleClass().add("block-tile");
        var k = io.blockdesigner.core.model.EntityTypes.kind(id);
        Tooltip.install(p, new Tooltip(name + "\n" + id + "\n" + k.group()
                + "\nClick to hold it, then right-click in Build mode to place it (it faces you)."));
        p.setOnMouseClicked(e -> {
            ws.holdEntity(id);
            ws.toolProperty().set(Workspace.ToolKind.BUILD);
        });
        return p;
    }

    private StackPane tile(BlockAssets assets, BlockRegistry.BlockInfo b) {
        ImageView iv = new ImageView(thumbnail(assets, b.defaultState()));
        iv.setFitWidth(TILE - 6);
        iv.setFitHeight(TILE - 6);
        iv.setSmooth(false);
        StackPane p = new StackPane(iv);
        p.getStyleClass().add("block-tile");
        // Hovered + a hotbar key puts this block in that slot (see hoveredBlock).
        p.setUserData(b.defaultState());
        Tooltip tip = new Tooltip(b.displayName() + "\n" + b.id());
        // In the Schematic tab the tooltip also says how many your layers use.
        tip.setOnShowing(e -> {
            Long n = source == Source.SCHEMATIC ? schematicCounts.get(b.id()) : null;
            tip.setText(b.displayName() + "\n" + b.id() + (n == null ? "" : String.format("\n%,d in your layers", n))
                    + "\nA hotbar key (" + Keybinds.keysOf(Keybinds.Action.HOTBAR_1) + "…" + Keybinds.keysOf(Keybinds.Action.HOTBAR_9) + ") puts it in that slot");
        });
        Tooltip.install(p, tip);
        p.setOnMouseClicked(e -> {
            ws.holdBlock(b.defaultState());
            ws.toolProperty().set(Workspace.ToolKind.BUILD);
        });
        // Drag onto the hotbar to keep it there.
        p.setOnDragDetected(e -> {
            javafx.scene.input.Dragboard db = p.startDragAndDrop(javafx.scene.input.TransferMode.COPY);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(Hotbar.DRAG_PREFIX + b.defaultState());
            db.setContent(content);
            db.setDragView(iv.getImage());
            e.consume();
        });
        return p;
    }

    Image thumbnail(BlockAssets assets, BlockState state) {
        return BlockIcons.icon(assets, state);
    }

    private void updateSelectedCard() {
        selectedCard.getChildren().clear();
        if (ws.heldEntityProperty().get() != null) {
            updateEntityCard(ws.heldEntityProperty().get());
            return;
        }
        BlockAssets assets = ws.assets();
        BlockState st = ws.selectedBlockProperty().get();
        if (assets == null || st == null) return;
        BlockRegistry.BlockInfo info = assets.registry().get(st.name()).orElse(null);
        ImageView iv = new ImageView(thumbnail(assets, st));
        iv.setFitWidth(36);
        iv.setFitHeight(36);
        iv.setSmooth(false);
        Label name = new Label(info != null ? info.displayName() : st.path());
        name.getStyleClass().add("selected-block-name");
        Label id = new Label(st.name());
        id.getStyleClass().add("layer-meta");
        HBox head = new HBox(10, iv, new VBox(2, name, id));
        head.setAlignment(Pos.CENTER_LEFT);
        selectedCard.getChildren().add(head);
        if (info == null || info.properties().isEmpty()) return;

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        int row = 0;
        BlockState complete = assets.registry().complete(st);
        for (var e : info.properties().entrySet()) {
            Label l = new Label(e.getKey());
            l.getStyleClass().add("prop-label");
            ComboBox<String> cb = new ComboBox<>();
            cb.getItems().setAll(e.getValue());
            cb.setValue(complete.get(e.getKey()));
            cb.getStyleClass().add("small");
            cb.setMaxWidth(Double.MAX_VALUE);
            String key = e.getKey();
            cb.setOnAction(ev -> {
                String v = cb.getValue();
                if (v != null) ws.selectedBlockProperty().set(assets.registry().complete(ws.selectedBlockProperty().get()).with(key, v.toLowerCase(Locale.ROOT)));
            });
            GridPane.setHgrow(cb, Priority.ALWAYS);
            grid.addRow(row++, l, cb);
        }
        selectedCard.getChildren().add(grid);
    }

    /** Mobs that grow up: Age below zero makes a baby (zombies and piglins use IsBaby). */
    private static final java.util.Set<String> AGEABLE = java.util.Set.of("pig", "cow", "sheep", "chicken", "mooshroom", "horse", "donkey",
            "mule", "goat", "rabbit", "llama", "trader_llama", "camel", "bee", "turtle", "villager", "wolf", "cat", "fox", "ocelot", "panda",
            "polar_bear", "armadillo", "sniffer", "axolotl", "strider", "frog");
    private static final java.util.Set<String> BABY_FLAG = java.util.Set.of("zombie", "husk", "drowned", "piglin");

    /** The held mob: its egg, name and the settings worth changing before placing it. */
    private void updateEntityCard(io.blockdesigner.core.nbt.CompoundTag nbt) {
        String id = nbt.getString("id");
        var kind = io.blockdesigner.core.model.EntityTypes.kind(id);
        String path = kind.id().startsWith("minecraft:") ? kind.id().substring(10) : "";
        ImageView iv = new ImageView(EntityIcons.icon(ws.assets(), kind.id()));
        iv.setFitWidth(36);
        iv.setFitHeight(36);
        iv.setSmooth(false);
        Label name = new Label(kind.name());
        name.getStyleClass().add("selected-block-name");
        Label idLabel = new Label(kind.id() + " · right-click to place");
        idLabel.getStyleClass().add("layer-meta");
        HBox head = new HBox(10, iv, new VBox(2, name, idLabel));
        head.setAlignment(Pos.CENTER_LEFT);
        selectedCard.getChildren().add(head);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        int[] row = {0};
        java.util.function.Consumer<java.util.function.Consumer<io.blockdesigner.core.nbt.CompoundTag>> edit = change -> {
            var c = ws.heldEntityProperty().get().copy();
            change.accept(c);
            ws.heldEntityProperty().set(c);
        };
        if (AGEABLE.contains(path) || BABY_FLAG.contains(path)) {
            javafx.scene.control.CheckBox baby = new javafx.scene.control.CheckBox("Baby");
            baby.setSelected(io.blockdesigner.core.model.EntityTypes.baby(nbt));
            baby.setOnAction(e -> edit.accept(c -> {
                if (BABY_FLAG.contains(path)) c.putBoolean("IsBaby", baby.isSelected());
                else c.putInt("Age", baby.isSelected() ? -24000 : 0);
            }));
            grid.add(baby, 1, row[0]++);
        }
        switch (path) {
            case "sheep" -> {
                ComboBox<String> color = combo(List.of(EntityIcons.DYES), EntityIcons.DYES[Math.floorMod(nbt.getByte("Color"), 16)]);
                color.setOnAction(e -> edit.accept(c -> c.putByte("Color", List.of(EntityIcons.DYES).indexOf(color.getValue()))));
                addRow(grid, row, "Wool", color);
            }
            case "villager" -> {
                var data = nbt.getCompound("VillagerData");
                ComboBox<String> prof = combo(EntityIcons.PROFESSIONS, strip(data.getString("profession"), "none"));
                ComboBox<String> biome = combo(EntityIcons.BIOMES, strip(data.getString("type"), "plains"));
                Runnable apply = () -> edit.accept(c -> c.put("VillagerData", new io.blockdesigner.core.nbt.CompoundTag()
                        .putString("type", "minecraft:" + biome.getValue()).putString("profession", "minecraft:" + prof.getValue())
                        .putInt("level", Math.max(1, data.getInt("level")))));
                prof.setOnAction(e -> apply.run());
                biome.setOnAction(e -> apply.run());
                addRow(grid, row, "Job", prof);
                addRow(grid, row, "Biome", biome);
            }
            case "painting" -> {
                ComboBox<String> variant = combo(io.blockdesigner.core.model.EntityTypes.paintingVariants(), io.blockdesigner.core.model.EntityTypes.paintingVariant(nbt));
                variant.setOnAction(e -> edit.accept(c -> {
                    c.putString("variant", "minecraft:" + variant.getValue());
                    c.remove("Motive");
                }));
                addRow(grid, row, "Picture", variant);
            }
            case "armor_stand" -> {
                for (String[] flag : new String[][]{{"ShowArms", "Arms"}, {"Small", "Small"}, {"NoBasePlate", "No base plate"}}) {
                    javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(flag[1]);
                    cb.setSelected(nbt.getBoolean(flag[0]));
                    cb.setOnAction(e -> edit.accept(c -> c.putBoolean(flag[0], cb.isSelected())));
                    grid.add(cb, 1, row[0]++);
                }
            }
            default -> {
            }
        }
        if (!grid.getChildren().isEmpty()) selectedCard.getChildren().add(grid);
    }

    private static ComboBox<String> combo(List<String> values, String value) {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().setAll(values);
        cb.setValue(value);
        cb.getStyleClass().add("small");
        cb.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(cb, Priority.ALWAYS);
        return cb;
    }

    private static void addRow(GridPane grid, int[] row, String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("prop-label");
        grid.addRow(row[0]++, l, control);
    }

    private static String strip(String id, String fallback) {
        String v = id.substring(id.indexOf(':') + 1);
        return v.isEmpty() ? fallback : v;
    }
}
