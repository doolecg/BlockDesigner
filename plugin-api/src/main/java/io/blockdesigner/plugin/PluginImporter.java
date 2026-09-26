package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Structure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Turns a file that isn't a schematic into blocks: an image into pixel art, a heightmap into terrain, another tool's
 * format. Its extensions join the Import window's file filter and drag and drop; the layers it returns are placed
 * like an imported schematic. For real schematic formats (that can also be exported) register a
 * {@link io.blockdesigner.core.formats.SchematicFormat} instead. Registered with
 * {@link PluginContext#registerImporter}. Since API 2.
 */
public interface PluginImporter {

    /** Stable id, unique within the plugin. */
    String id();

    /** Shown in the file filter, e.g. "Pixel art from an image". */
    String displayName();

    /** File extensions without the dot, lower case, e.g. {@code png}. */
    List<String> extensions();

    /**
     * Asked for before importing (in a small dialog), and remembered for next time; {@link Options#none()} imports
     * straight away.
     */
    default Options options() {
        return Options.none();
    }

    /**
     * Reads the file. Runs on a background thread: don't touch the UI or the scene.
     *
     * @return one or more layers, placed together; empty when the file holds nothing
     * @throws IOException with a message for the user when the file can't be read
     */
    List<ImportedLayer> importFile(Path file, OptionValues options, Progress progress, BlockCatalog blocks) throws IOException;

    /**
     * A layer to add.
     *
     * @param offset where the layer's origin goes relative to the other layers of the same import
     */
    record ImportedLayer(String name, Structure blocks, BlockPos offset) {
        public ImportedLayer {
            Objects.requireNonNull(blocks, "blocks");
            name = name == null || name.isBlank() ? "Imported" : name;
            offset = offset == null ? BlockPos.ORIGIN : offset;
        }
    }
}
