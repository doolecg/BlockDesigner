package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.BlockRegistry;
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

/** Searchable grid of every block (vanilla and mods), plus an editor for the selected block's properties. */
public final class BlockPalette extends VBox {
    private static final int TILE = 34;

    private final Workspace ws;
    private final TextField search = new TextField();
    private final TilePane tiles = new TilePane();
    private final FlowPane categories = new FlowPane(4, 4);
    private final VBox selectedCard = new VBox(6);
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private final Map<String, StackPane> tileCache = new HashMap<>();
    private Predicate<BlockRegistry.BlockInfo> category = b -> true;

    public BlockPalette(Workspace ws) {
        this.ws = ws;
        getStyleClass().add("side-panel");
        setSpacing(8);

        Label title = new Label("Blocks");
        title.getStyleClass().add("panel-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("panel-header");

        search.setPromptText("Search blocks…  (e.g. oak stairs, create:shaft)");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((o, a, b) -> rebuild());
        HBox searchBox = new HBox(6, new FontIcon(Feather.SEARCH), search);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");
        HBox.setHgrow(search, Priority.ALWAYS);

        buildCategories();

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

        getChildren().addAll(header, searchBox, categories, scroll, selectedCard);

        ws.assetsProperty().addListener((o, a, b) -> {
            tileCache.clear();
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
        rebuild();
        updateSelectedCard();
    }

    private void buildCategories() {
        Map<String, Predicate<BlockRegistry.BlockInfo>> cats = new LinkedHashMap<>();
        cats.put("All", b -> true);
        cats.put("Recent", b -> recent.contains(b.id()));
        cats.put("Stone", b -> has(b, "stone", "brick", "deepslate", "andesite", "diorite", "granite", "tuff", "basalt", "blackstone", "cobble", "sandstone", "prismarine", "quartz", "purpur", "end_stone", "calcite"));
        cats.put("Wood", b -> has(b, "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped", "planks", "log", "wood", "pale_oak"));
        cats.put("Shapes", b -> has(b, "stairs", "slab", "wall", "fence", "pane", "door", "trapdoor"));
        cats.put("Colour", b -> has(b, "wool", "concrete", "terracotta", "glass", "carpet", "candle", "banner", "bed"));
        cats.put("Nature", b -> has(b, "leaves", "grass", "dirt", "sand", "gravel", "flower", "sapling", "moss", "mud", "clay", "snow", "ice", "coral", "mushroom", "vine", "fern", "tulip", "rose", "daisy", "kelp"));
        cats.put("Light", b -> has(b, "lantern", "torch", "lamp", "glowstone", "sea_lantern", "froglight", "candle", "shroomlight", "end_rod", "campfire"));
        cats.put("Redstone", b -> has(b, "redstone", "piston", "observer", "repeater", "comparator", "hopper", "dropper", "dispenser", "lever", "button", "pressure_plate", "rail", "target", "tnt", "crafter"));
        cats.put("Mods", b -> !b.namespace().equals("minecraft"));
        ToggleGroup group = new ToggleGroup();
        cats.forEach((name, pred) -> {
            ToggleButton t = new ToggleButton(name);
            t.getStyleClass().addAll("chip", "small");
            t.setToggleGroup(group);
            t.setOnAction(e -> {
                if (!t.isSelected()) t.setSelected(true);
                category = pred;
                rebuild();
            });
            if (name.equals("All")) t.setSelected(true);
            categories.getChildren().add(t);
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
        if (assets == null) return;
        String q = search.getText() == null ? "" : search.getText().strip();
        List<BlockRegistry.BlockInfo> blocks = q.isEmpty()
                ? new ArrayList<>(assets.registry().all())
                : assets.registry().search(q, 2000);
        if (q.isEmpty()) blocks.sort((a, b) -> {
            int na = a.namespace().equals("minecraft") ? 0 : 1, nb = b.namespace().equals("minecraft") ? 0 : 1;
            return na != nb ? Integer.compare(na, nb) : a.path().compareTo(b.path());
        });
        for (BlockRegistry.BlockInfo b : blocks) {
            if (!category.test(b)) continue;
            tiles.getChildren().add(tileCache.computeIfAbsent(b.id(), id -> tile(assets, b)));
        }
    }

    private StackPane tile(BlockAssets assets, BlockRegistry.BlockInfo b) {
        ImageView iv = new ImageView(thumbnail(assets, b.defaultState()));
        iv.setFitWidth(TILE - 6);
        iv.setFitHeight(TILE - 6);
        iv.setSmooth(false);
        StackPane p = new StackPane(iv);
        p.getStyleClass().add("block-tile");
        Tooltip.install(p, new Tooltip(b.displayName() + "\n" + b.id()));
        p.setOnMouseClicked(e -> {
            ws.selectedBlockProperty().set(b.defaultState());
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
}
