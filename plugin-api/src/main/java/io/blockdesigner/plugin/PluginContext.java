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

    /**
     * Adds a panel: a page of the plugin's own tab on the right, picked in the row of pages along its top (the tab
     * opens on the first one). Build it with {@code io.blockdesigner.plugin.ui.PanelScaffold} to match the app.
     * Since API 2.
     */
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
     * Adds the plugin's settings. They are shown on the plugin's own page in the Settings window (API 6; before that,
     * at the top of the Overview of the plugin's tab), with a "Reset to defaults" button; the Overview links to them.
     * BlockDesigner draws a control per option (with the options' groups, help and units), keeps the values between
     * runs, and calls {@code onChange} on the JavaFX thread with the current values, once straight away and again
     * after every change (including "Reset to defaults" and {@link #updateSettings}). Call it once. Since API 4.
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

    // ---- resource packs (API 6) ------------------------------------------------------------------------------

    /**
     * The resource packs layered on the Minecraft assets (game jar and mods), lowest priority first: zip files or
     * folders. Since API 6.
     */
    List<Path> resourcePacks();

    /**
     * Reloads the Minecraft assets with these resource packs (lowest priority first) on top of the game jar and mods,
     * and keeps them as the user's choice. Loading happens in the background; the view shows the new textures when it
     * is done. Missing files are skipped. Since API 6.
     */
    void useResourcePacks(List<Path> packs);

    // ---- plugin UI (API 6) -----------------------------------------------------------------------------------

    /**
     * The app's help for the plugin's own panels and dialogs: its options form, dark or light, the main window, and
     * dialogs in the app's look. The components themselves are in {@code io.blockdesigner.plugin.ui}. Since API 6.
     */
    io.blockdesigner.plugin.ui.PluginUi ui();

    /**
     * Opens the plugin's tab at one of its panels, by the panel's id (reopening the tab if the user closed it); an
     * unknown id opens the tab as it was. Since API 6.
     */
    void showPanel(String panelId);

    /**
     * A status dot on a panel's page button, coloured by {@code tone}, with {@code text} as its tooltip ("Connected
     * to 2 games"); a null tone removes it. Works before the panel is first shown. Since API 6.
     */
    void setPanelStatus(String panelId, io.blockdesigner.plugin.ui.Tone tone, String text);

    /**
     * Opens the Settings window at this plugin's page (for a plugin without {@link #registerSettings settings}, its
     * tab's Overview). Since API 6.
     */
    void openSettings();

    /**
     * Changes the {@link #registerSettings registered settings} as if the user had: the new values are kept,
     * {@code onChange} runs and an open Settings page redraws. For a setting also offered on a panel (a provider
     * picker, say), or to move settings the plugin used to keep itself. Since API 6.
     */
    void updateSettings(java.util.function.UnaryOperator<OptionValues> change);

    // ---- opening files (API 7) -------------------------------------------------------------------------------

    /**
     * Opens a file as the project, as File › Open does: a {@code .bdproj} is opened; a schematic BlockDesigner reads
     * ({@code .schem}, {@code .litematic}, {@code .nbt}…) becomes a new, unsaved project named after the file, its
     * blocks where the file has them. When the open project has changes, the user is first asked whether to save
     * them (Save, Don't save, Cancel). Reading happens in the background; {@code done} is called once, on the JavaFX
     * thread, with how it went ({@link SceneEvent.ProjectOpened} is sent before it on success). Since API 7.
     */
    void openFile(Path file, Consumer<OpenResult> done);
}
