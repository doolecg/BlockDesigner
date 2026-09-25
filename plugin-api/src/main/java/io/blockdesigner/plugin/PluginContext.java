package io.blockdesigner.plugin;

import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.formats.SchematicFormat;
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

    /**
     * Edits blocks in world coordinates across the visible, unlocked layers, exactly like a command does, as one undo
     * step named {@code label}. Blocks placed in empty space go into the active layer.
     */
    void editWorld(String label, Consumer<WorldEdit.World> edit);

    /** Adds a new layer holding {@code blocks} (as one undo step) and makes it active. */
    Layer addLayer(String name, Structure blocks);

    // ---- feedback --------------------------------------------------------------------------------------------

    /** Sets the status bar text. */
    void status(String message);

    /** Shows a short message over the 3D view. */
    void toast(String message);

    /** Runs on the JavaFX thread (immediately when already on it). */
    void runOnUiThread(Runnable task);
}
