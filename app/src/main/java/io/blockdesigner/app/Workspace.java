package io.blockdesigner.app;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.version.McVersion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;

/**
 * Shared, observable application state: the scene being edited, loaded assets, the selected block and tool.
 * Touch only from the JavaFX thread.
 */
public final class Workspace {
    /**
     * Viewport modes: look around only, select and slide layers, build Minecraft-style (left break, right place), or
     * move / rotate the selected layers with Blender-style gizmos, paint and erase blocks with a brush, or use a tool a
     * plugin added (which one is up to the viewport).
     */
    public enum ToolKind { VIEW, SELECT, BUILD, MOVE, ROTATE, BRUSH, ERASER, PLUGIN }

    public static final int HOTBAR_SIZE = 9;

    private final Settings settings;
    private final Scene scene = new Scene();
    private final SceneEditor editor = new SceneEditor(scene);
    private final ObjectProperty<BlockAssets> assets = new SimpleObjectProperty<>();
    private final ObjectProperty<McVersion> targetVersion = new SimpleObjectProperty<>(McVersion.latestKnown());
    private final ObjectProperty<BlockState> selectedBlock = new SimpleObjectProperty<>(BlockState.of("stone_bricks"));
    private final ObjectProperty<ToolKind> tool = new SimpleObjectProperty<>(ToolKind.SELECT);
    /** Nine hotbar slots (null = empty): a record of picked and dragged-in blocks. */
    private final ObservableList<BlockState> hotbar = FXCollections.observableArrayList();
    /** The held slot, or -1 when the selected block did not come from the hotbar. */
    private final javafx.beans.property.IntegerProperty hotbarSlot = new javafx.beans.property.SimpleIntegerProperty(-1);
    /** Shuffle mode (Z): each placed block is a random pick from the filled hotbar slots. */
    private final BooleanProperty shuffle = new SimpleBooleanProperty();
    /**
     * The mob (or painting, armour stand…) held for placing, as its entity data with {@code id}; null when holding a
     * block. Choosing a block lets go of it.
     */
    private final ObjectProperty<io.blockdesigner.core.nbt.CompoundTag> heldEntity = new SimpleObjectProperty<>();

    {
        // No hotbar slot is lit while a mob is in hand.
        heldEntity.addListener((o, a, b) -> {
            if (b != null) hotbarSlot.set(-1);
            else if (selectedBlock.get() != null) hotbarSlot.set(hotbar.indexOf(selectedBlock.get()));
        });
    }
    private final BooleanProperty replace = new SimpleBooleanProperty();
    private final java.util.Random random = new java.util.Random();
    private ToolKind beforeBuild = ToolKind.SELECT;
    private final ObjectProperty<Layer> activeLayer = new SimpleObjectProperty<>();
    /** Layers selected in the layer list (for multi-layer moves); always includes the active layer when non-empty. */
    private final ObservableList<Layer> selectedLayers = FXCollections.observableArrayList();
    private final ObservableList<Layer> layers = FXCollections.observableArrayList();
    private final BooleanProperty dark = new SimpleBooleanProperty(true);
    /** Colour theme id (see AppTheme) and how dark/light is picked (DARK, LIGHT, SYSTEM). */
    private final StringProperty theme = new SimpleStringProperty("BLUE");
    private final StringProperty themeMode = new SimpleStringProperty("DARK");
    private final StringProperty projectName = new SimpleStringProperty("Untitled");
    private final ObjectProperty<Path> projectFile = new SimpleObjectProperty<>();
    private final StringProperty status = new SimpleStringProperty("");

    public Workspace(Settings settings) {
        this.settings = settings;
        dark.set(settings.darkTheme);
        theme.set(settings.theme == null ? "BLUE" : settings.theme);
        themeMode.set(settings.themeMode != null ? settings.themeMode : settings.darkTheme ? "DARK" : "LIGHT");
        theme.addListener((o, a, b) -> settings.theme = b);
        themeMode.addListener((o, a, b) -> settings.themeMode = b);
        initHotbar();
        scene.addListener(new Scene.Listener() {
            @Override
            public void layerAdded(Layer layer, int index) {
                layers.setAll(scene.layers());
            }

            @Override
            public void layerRemoved(Layer layer) {
                layers.setAll(scene.layers());
                selectedLayers.remove(layer);
            }

            @Override
            public void layersReordered() {
                layers.setAll(scene.layers());
            }

            @Override
            public void activeLayerChanged(Layer layer) {
                activeLayer.set(layer);
            }
        });
    }

    public Settings settings() {
        return settings;
    }

    public Scene scene() {
        return scene;
    }

    public SceneEditor editor() {
        return editor;
    }

    public ObjectProperty<BlockAssets> assetsProperty() {
        return assets;
    }

    public BlockAssets assets() {
        return assets.get();
    }

    public ObjectProperty<McVersion> targetVersionProperty() {
        return targetVersion;
    }

    public ObjectProperty<BlockState> selectedBlockProperty() {
        return selectedBlock;
    }

    private void initHotbar() {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            BlockState st = null;
            String saved = i < settings.hotbar.size() ? settings.hotbar.get(i) : null;
            if (saved != null && !saved.isBlank()) {
                try {
                    st = BlockState.parse(saved);
                } catch (RuntimeException ignored) {
                    // unreadable entry: leave the slot empty
                }
            }
            hotbar.add(st);
        }
        // The highlighted slot follows the selected block wherever it was chosen.
        selectedBlock.addListener((o, a, b) -> {
            hotbarSlot.set(b == null ? -1 : hotbar.indexOf(b));
            if (b != null) heldEntity.set(null);
        });
        hotbar.addListener((javafx.collections.ListChangeListener<BlockState>) c -> {
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            for (BlockState st : hotbar) out.add(st == null ? "" : st.toString());
            settings.hotbar = out;
        });
    }

    public ObservableList<BlockState> hotbar() {
        return hotbar;
    }

    public javafx.beans.property.ReadOnlyIntegerProperty hotbarSlotProperty() {
        return hotbarSlot;
    }

    /** Holds the block in slot {@code i}; empty slots are ignored. */
    public void selectHotbarSlot(int i) {
        if (i < 0 || i >= HOTBAR_SIZE || hotbar.get(i) == null) return;
        selectedBlock.set(hotbar.get(i));
        hotbarSlot.set(i);
        heldEntity.set(null);
    }

    /** See {@link #heldEntity}. */
    public ObjectProperty<io.blockdesigner.core.nbt.CompoundTag> heldEntityProperty() {
        return heldEntity;
    }

    /** Holds an entity type for placing in Build mode (right-click), with its default data. */
    public void holdEntity(String id) {
        heldEntity.set(io.blockdesigner.core.model.EntityTypes.create(id, 0, 0, 0, 0).nbt());
    }

    /** Lets go of a held mob so the selected block is in hand again (a block picked in the palette). */
    public void holdBlock(BlockState state) {
        heldEntity.set(null);
        selectedBlock.set(state);
    }

    /**
     * Delete in Build mode: empties the held hotbar slot. As in Minecraft the slot stays selected with an empty hand,
     * so nothing is placed until another block is chosen. Returns the removed block, or null if the slot was empty.
     */
    public BlockState clearHeldSlot() {
        int i = hotbarSlot.get();
        if (i < 0 || hotbar.get(i) == null) return null;
        BlockState removed = hotbar.get(i);
        hotbar.set(i, null);
        selectedBlock.set(null);
        hotbarSlot.set(i);
        return removed;
    }

    /** Minecraft's scroll-to-change-slot over the filled slots: {@code step} +1 moves right, -1 left, wrapping. */
    public void scrollHotbar(int step) {
        int start = hotbarSlot.get() < 0 ? (step > 0 ? -1 : 0) : hotbarSlot.get();
        for (int n = 1; n <= HOTBAR_SIZE; n++) {
            int i = Math.floorMod(start + step * n, HOTBAR_SIZE);
            if (hotbar.get(i) != null) {
                selectHotbarSlot(i);
                return;
            }
        }
    }

    /**
     * Records a block (middle-click pick): jumps to its slot if it is already there, otherwise fills the first empty
     * slot, or the held one when the bar is full; then holds it.
     */
    public void recordInHotbar(BlockState state) {
        int i = hotbar.indexOf(state);
        if (i < 0) i = hotbar.indexOf(null);
        if (i < 0) i = Math.max(0, hotbarSlot.get());
        putInHotbar(i, state);
    }

    /** Puts a block in a specific slot (dragged from the palette) and holds it. */
    public void putInHotbar(int i, BlockState state) {
        int existing = hotbar.indexOf(state);
        if (existing >= 0 && existing != i) hotbar.set(existing, null);
        hotbar.set(i, state);
        selectHotbarSlot(i);
    }

    public BooleanProperty shuffleProperty() {
        return shuffle;
    }

    /** Build mode's Replace (R): right-click swaps the aimed block for the held one instead of placing next to it. */
    public BooleanProperty replaceProperty() {
        return replace;
    }

    /** The block to place next: a random filled hotbar slot in shuffle mode, otherwise the held block. */
    public BlockState blockToPlace() {
        if (shuffle.get()) {
            java.util.List<BlockState> filled = hotbar.stream().filter(java.util.Objects::nonNull).toList();
            if (!filled.isEmpty()) return filled.get(random.nextInt(filled.size()));
        }
        return selectedBlock.get();
    }

    /** Shift+C (by default): empties every slot (the held block stays selected). */
    public void clearHotbar() {
        for (int i = 0; i < HOTBAR_SIZE; i++) hotbar.set(i, null);
        hotbarSlot.set(-1);
    }

    /** B: switches Build on, or back off to the mode used before it (works while flying too). */
    public void toggleBuild() {
        ToolKind now = tool.get();
        if (now == ToolKind.BUILD) {
            tool.set(beforeBuild);
        } else {
            beforeBuild = now;
            tool.set(ToolKind.BUILD);
        }
    }

    public ObjectProperty<ToolKind> toolProperty() {
        return tool;
    }

    public ObjectProperty<Layer> activeLayerProperty() {
        return activeLayer;
    }

    public ObservableList<Layer> selectedLayers() {
        return selectedLayers;
    }

    /** Scene layers, bottom first (mirrors {@link Scene#layers()}). */
    public ObservableList<Layer> layers() {
        return layers;
    }

    /** Whether the UI is currently dark (resolved from the theme mode). */
    public BooleanProperty darkProperty() {
        return dark;
    }

    public StringProperty themeProperty() {
        return theme;
    }

    public StringProperty themeModeProperty() {
        return themeMode;
    }

    public StringProperty projectNameProperty() {
        return projectName;
    }

    public ObjectProperty<Path> projectFileProperty() {
        return projectFile;
    }

    public StringProperty statusProperty() {
        return status;
    }

    /** Layers that a nudge should move: the multi-selection, or just the active layer. */
    public java.util.List<Layer> nudgeTargets() {
        if (selectedLayers.size() > 1) return java.util.List.copyOf(selectedLayers);
        Layer a = activeLayer.get();
        return a == null ? java.util.List.of() : java.util.List.of(a);
    }
}
