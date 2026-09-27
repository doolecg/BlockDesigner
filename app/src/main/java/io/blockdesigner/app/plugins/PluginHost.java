package io.blockdesigner.app.plugins;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** What the app gives plugins; implemented by the main window so the plugin manager itself needs no UI. */
public interface PluginHost {
    Scene scene();

    SceneEditor editor();

    Optional<Layer> activeLayer();

    List<Layer> selectedLayers();

    McVersion targetVersion();

    void editWorld(String label, Consumer<WorldEdit.World> edit);

    Layer addLayer(String name, Structure blocks);

    void status(String message);

    void toast(String message);

    void runOnUiThread(Runnable task);

    /** Plugins were loaded, enabled or disabled: menus and the export window should refresh. */
    void pluginsChanged();

    // ---- API 2 ------------------------------------------------------------------------------------------------

    /** Runs a task on a later pulse of the UI thread (scene events are merged until then). */
    default void runLater(Runnable task) {
        runOnUiThread(task);
    }

    /** The loaded Minecraft assets, or null while none are. */
    default BlockAssets assets() {
        return null;
    }

    /** World box around the selected blocks, or the //pos1 //pos2 region; empty when neither exists. */
    default Optional<Box> selection() {
        return Optional.empty();
    }

    /** Opens a plugin transform's dialog (from {@code /transform <id>}). */
    default void openTransform(PluginManager.Transform transform) {
    }

    /** A plugin with tools is about to be disabled: put down its tool if it is the active one. */
    default void pluginUnloading(PluginManager.Plugin plugin) {
    }

    // ---- API 3 ------------------------------------------------------------------------------------------------

    /** The project's scene objects; null to let the plugin manager keep its own (tests). */
    default SceneObjectStore objects() {
        return null;
    }

    // ---- API 5 ------------------------------------------------------------------------------------------------

    /** The hotbar's slots, null for empty ones. */
    default List<io.blockdesigner.core.model.BlockState> hotbar() {
        return List.of();
    }

    /** Fills the hotbar from the left (null leaves a slot empty) and holds the first block. */
    default void setHotbar(List<io.blockdesigner.core.model.BlockState> blocks) {
    }

    /** A block's icon, or null while no assets are loaded. */
    default javafx.scene.image.Image blockIcon(io.blockdesigner.core.model.BlockState block) {
        return null;
    }

    /** Makes a plugin tool the active tool. */
    default void pickTool(PluginManager.Tool tool) {
    }

    /** A plugin changed a tool's remembered options: the active tool's options bar should follow. */
    default void toolOptionsChanged(PluginManager.Tool tool, io.blockdesigner.plugin.OptionValues values) {
    }

    // ---- API 6 ------------------------------------------------------------------------------------------------

    /** The resource packs layered on the assets, lowest priority first. */
    default List<java.nio.file.Path> resourcePacks() {
        return List.of();
    }

    /** Reloads the assets with these resource packs and keeps them in the settings. */
    default void useResourcePacks(List<java.nio.file.Path> packs) {
    }

    // ---- API 6 UI ---------------------------------------------------------------------------------------------

    /**
     * The app's options form for a plugin's panel, showing {@code initial} and calling {@code onChange} after each
     * user change; null where there is no UI (tests).
     */
    default io.blockdesigner.plugin.ui.OptionsForm optionsForm(PluginManager.Plugin plugin, io.blockdesigner.plugin.Options options,
                                                               io.blockdesigner.plugin.OptionValues initial,
                                                               Consumer<io.blockdesigner.plugin.OptionValues> onChange) {
        return null;
    }

    /** True while the app is dark. */
    default javafx.beans.property.ReadOnlyBooleanProperty dark() {
        return new javafx.beans.property.SimpleBooleanProperty(false);
    }

    /** The main window, for plugins' choosers and dialogs; null where there is none. */
    default javafx.stage.Window owner() {
        return null;
    }

    /** Gives a plugin's dialog the app's look (owner, stylesheets, font, dark or light). */
    default void styleDialog(javafx.scene.control.Dialog<?> dialog) {
    }

    /** Opens the plugin's tab at one of its panels (unknown id: the tab as it was). */
    default void showPanel(PluginManager.Plugin plugin, String panelId) {
    }

    /** A plugin changed the status dot of one of its pages. */
    default void panelStatusChanged(PluginManager.Plugin plugin) {
    }

    /** Opens the Settings window at the plugin's page (or its tab's Overview when it has no settings). */
    default void openSettings(PluginManager.Plugin plugin) {
    }

    /** A plugin changed its own settings: an open Settings page should redraw them. */
    default void pluginSettingsChanged(PluginManager.Plugin plugin) {
    }
}
