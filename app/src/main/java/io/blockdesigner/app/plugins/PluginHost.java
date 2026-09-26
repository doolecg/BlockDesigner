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
}
