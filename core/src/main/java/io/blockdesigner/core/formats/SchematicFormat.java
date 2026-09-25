package io.blockdesigner.core.formats;

import io.blockdesigner.core.nbt.CompoundTag;

import java.io.IOException;
import java.util.List;

/** A schematic file format backed by an NBT root compound. */
public interface SchematicFormat {
    /** Stable identifier, e.g. {@code litematica}. */
    String id();

    String displayName();

    /** File extensions without the dot; the first is used when saving. */
    List<String> extensions();

    /** True if {@code root} looks like this format (used when extensions are ambiguous). */
    boolean detect(CompoundTag root);

    SchematicFile read(CompoundTag root) throws IOException;

    /** Serialises the regions; returns the root compound plus its root-tag name. */
    Encoded write(SchematicFile file, WriteOptions options);

    record Encoded(String rootName, CompoundTag root) {
    }
}
