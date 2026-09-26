package io.blockdesigner.plugin;

import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The plugin's handle on BlockDesigner, passed to {@link BlockDesignerPlugin#enable}. Use it from the JavaFX thread
 * (plugin callbacks already run there). Everything registered here is removed when the plugin is disabled.
 */
public interface PluginContext {

    PluginInfo info();

    /** A folder for the plugin's own files (created on first call). */
    Path dataFolder();

    /** Writes a line to the plugin log shown in the Plugins window. */
    void log(String message);

    // ---- extension points ------------------------------------------------------------------------------------

    /** Adds a schematic format to Import and Export. Its id must be unique across all formats. */
    void registerFormat(SchematicFormat format);

    /** Adds a card to the Export window. */
    void registerExporter(PluginExporter exporter);

    /** Adds an entry to the Plugins menu. */
    void registerAction(PluginAction action);

    /** Adds a command to the command bar. */
    void registerCommand(PluginCommand command);

    /**
     * Adds a transform: Plugins › Transform, the selection's right-click menu and {@code /transform <id>}.
     * Since API 2.
     */
    void registerTransform(PluginTransform transform);

    /** Adds a panel (a closable tab on the right). Since API 2. */
    void registerPanel(PluginPanel panel);

    /** Adds an importer for files that aren't schematics (Import window and drag and drop). Since API 2. */
    void registerImporter(PluginImporter importer);

    /** Adds a tool to the tool dock over the viewport. Since API 2. */
    void registerTool(PluginTool tool);

    /**
     * Adds a kind of scene object (see {@link SceneObject}); objects of it saved in the open project appear now. Since
     * API 3.
     */
    void registerObjectType(SceneObjectType type);

    /**
     * Adds settings to the plugin's own tab on the right. Every enabled plugin has that tab: it shows the plugin is
     * running and lists what it adds; these settings go at the top of it. BlockDesigner draws a control per option,
     * keeps the values between runs, and calls {@code onChange} on the JavaFX thread with the current values, once
     * straight away and again after every change (including "Reset to defaults"). Call it once. Since API 4.
     */
    void registerSettings(Options options, Consumer<OptionValues> onChange);

    /** The current values of the settings added with {@link #registerSettings}: defaults for none. Since API 4. */
    OptionValues settings();

    // ---- events (API 2) --------------------------------------------------------------------------------------

    /**
     * Calls {@code listener} on the JavaFX thread whenever an event of {@code type} happens, at most once per frame
     * per kind (bursts are merged, see {@link SceneEvent}). Pass {@code SceneEvent.class} to hear every kind.
     * Listeners are removed when the plugin is disabled; cancel the subscription to stop earlier. Since API 2.
     */
    <E extends SceneEvent> Subscription on(Class<E> type, Consumer<? super E> listener);

    // ---- the open project ------------------------------------------------------------------------------------

    /** The layers being edited. Read freely; change blocks and layers through {@link #editor()} so undo works. */
    Scene scene();

    /** Undoable editing: block sessions, adding/removing/modifying layers. */
    SceneEditor editor();

    Optional<Layer> activeLayer();

    /** Layers selected in the layer list (the active one when nothing else is). */
    List<Layer> selectedLayers();

    /** The Minecraft version exports target. */
    McVersion targetVersion();

    /** Block registry, families, variants and colours. Since API 2. */
    BlockCatalog blocks();

    /** Block models, the texture atlas and texture files of the loaded Minecraft version. Since API 2. */
    AssetAccess assets();

    /**
     * World-space box around the selected blocks, or the //pos1 //pos2 region when no blocks are selected; empty when
     * neither is. Since API 2.
     */
    Optional<Box> selection();

    /**
     * Edits blocks in world coordinates across the visible, unlocked layers, exactly like a command does, as one undo
     * step named {@code label}. Blocks placed in empty space go into the active layer.
     */
    void editWorld(String label, Consumer<WorldEdit.World> edit);

    /** Adds a new layer holding {@code blocks} (as one undo step) and makes it active. */
    Layer addLayer(String name, Structure blocks);

    /** The plugin's scene objects: add, list and select them, and store their data. Since API 3. */
    SceneObjects objects();

    // ---- feedback --------------------------------------------------------------------------------------------

    /** Sets the status bar text. */
    void status(String message);

    /** Shows a short message over the 3D view. */
    void toast(String message);

    /** Runs on the JavaFX thread (immediately when already on it). */
    void runOnUiThread(Runnable task);

    // ---- hotbar, icons and tools (API 5) ---------------------------------------------------------------------

    /** The hotbar's nine slots, left to right; air for an empty slot. Since API 5. */
    java.util.List<io.blockdesigner.core.model.BlockState> hotbar();

    /**
     * Fills the hotbar from the left with up to nine blocks (air leaves a slot empty), empties the rest and holds the
     * first block. Since API 5.
     */
    void setHotbar(java.util.List<io.blockdesigner.core.model.BlockState> blocks);

    /**
     * The block's icon as the block list and hotbar draw it; empty while no Minecraft assets are loaded. Call on the
     * JavaFX thread. Since API 5.
     */
    Optional<javafx.scene.image.Image> blockIcon(io.blockdesigner.core.model.BlockState block);

    /** Picks one of this plugin's tools by its id, as its key or button does. Since API 5. */
    void pickTool(String toolId);

    /**
     * Changes the remembered options of one of this plugin's tools (by its id); when it is the active tool, its
     * options bar and handler follow straight away. Since API 5.
     */
    void setToolOptions(String toolId, java.util.function.UnaryOperator<OptionValues> change);
}
