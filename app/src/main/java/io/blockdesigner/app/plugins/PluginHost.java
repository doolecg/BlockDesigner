package io.blockdesigner.app.plugins;

import io.blockdesigner.core.edit.SceneEditor;
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
}
