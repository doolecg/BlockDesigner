package io.blockdesigner.core.formats;

import io.blockdesigner.core.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A schematic file format. The built-in ones are gzipped NBT and implement {@link #detect}, {@link #read} and
 * {@link #write}; a plugin format stored some other way returns false from {@link #nbtBased()} and overrides
 * {@link #readFile} / {@link #writeFile} instead.
 */
public interface SchematicFormat {
    /** Stable identifier, e.g. {@code litematica}. */
    String id();

    String displayName();

    /** File extensions without the dot; the first is used when saving. */
    List<String> extensions();

    /** True if {@code root} looks like this format (used when extensions are ambiguous). */
    default boolean detect(CompoundTag root) {
        return false;
    }

    default SchematicFile read(CompoundTag root) throws IOException {
        throw new IOException(displayName() + " is not an NBT format");
    }

    /** Serialises the regions; returns the root compound plus its root-tag name. */
    default Encoded write(SchematicFile file, WriteOptions options) {
        throw new UnsupportedOperationException(displayName() + " is not an NBT format");
    }

    /** Whether files are gzipped NBT handled through {@link #read} / {@link #write}. */
    default boolean nbtBased() {
        return true;
    }

    /** Reads a whole file; only called when {@link #nbtBased()} is false. */
    default SchematicFile readFile(Path file) throws IOException {
        throw new IOException(displayName() + " cannot be read");
    }

    /** Writes a whole file; only called when {@link #nbtBased()} is false. */
    default void writeFile(SchematicFile file, WriteOptions options, Path target) throws IOException {
        throw new IOException(displayName() + " cannot be written");
    }

    /** Whether this format can save (some plugin formats are import-only). */
    default boolean canWrite() {
        return true;
    }

    /** Whether this format can load files. */
    default boolean canRead() {
        return true;
    }

    record Encoded(String rootName, CompoundTag root) {
    }
}
