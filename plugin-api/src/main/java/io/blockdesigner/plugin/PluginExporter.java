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

    /**
     * Parameters shown on the export card (a scale, a palette, a toggle…); the chosen values arrive in
     * {@link Request#options()} and are remembered for next time. Since API 2.
     */
    default Options options() {
        return Options.none();
    }

    /**
     * An extra line for the card's summary about what would be written, e.g. "12 map items · 2 × 3 maps", or null for
     * none. Runs on the JavaFX thread whenever the choice of layers changes; keep it quick. Since API 2.
     *
     * @param merged the chosen layers merged into world coordinates
     */
    default String summary(Structure merged) {
        return null;
    }

    /** Writes the output. Runs on a background thread; don't touch the UI from here. */
    void export(Request request) throws IOException;

    /**
     * @param name     the export name the user typed
     * @param merged   the chosen layers merged into world coordinates (layer transforms applied)
     * @param layers   the chosen layers themselves, bottom first (read-only by convention)
     * @param target   the file (or folder) to write
     * @param options  the values chosen for {@link #options()} (since API 2)
     * @param progress where to report how far the export has got; shown under the card (since API 2)
     * @param assets   block models, the texture atlas and texture files, for mesh and image exports (since API 2)
     */
    record Request(String name, String author, McVersion version, Structure merged, List<Layer> layers, Path target,
                   OptionValues options, Progress progress, AssetAccess assets) {

        /** A request without options, progress reporting or assets, as in API 1. */
        public Request(String name, String author, McVersion version, Structure merged, List<Layer> layers, Path target) {
            this(name, author, version, merged, layers, target, Options.none().defaults(), Progress.NONE, AssetAccess.NONE);
        }

        public Request {
            options = options == null ? Options.none().defaults() : options;
            progress = progress == null ? Progress.NONE : progress;
            assets = assets == null ? AssetAccess.NONE : assets;
        }
    }
}
