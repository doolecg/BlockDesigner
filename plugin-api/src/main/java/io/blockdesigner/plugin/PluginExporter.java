package io.blockdesigner.plugin;

import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * An export target shown as a card in the Export window, for outputs that aren't a schematic format (a bill of
 * materials, a mod's own file, a folder of files…). For plain schematic formats register a
 * {@link io.blockdesigner.core.formats.SchematicFormat} instead; it then also shows up in Import.
 */
public interface PluginExporter {

    /** Stable id, unique within the plugin. */
    String id();

    String displayName();

    /** One line under the name on the card. */
    default String description() {
        return "";
    }

    /** Extension of the file written, without the dot; {@code null} when {@link #writesFolder()}. */
    String extension();

    /** When true the user picks a folder and {@link Request#target()} is that folder. */
    default boolean writesFolder() {
        return false;
    }

    /** Writes the output. Runs on a background thread; don't touch the UI from here. */
    void export(Request request) throws IOException;

    /**
     * @param name    the export name the user typed
     * @param merged  the chosen layers merged into world coordinates (layer transforms applied)
     * @param layers  the chosen layers themselves, bottom first (read-only by convention)
     * @param target  the file (or folder) to write
     */
    record Request(String name, String author, McVersion version, Structure merged, List<Layer> layers, Path target) {
    }
}
