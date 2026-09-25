package io.blockdesigner.core.formats;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Structure;

import java.util.List;

/**
 * Format-neutral schematic contents: one or more named regions (Litematica can hold several; other formats hold one)
 * plus descriptive metadata.
 */
public record SchematicFile(String name, String author, String description, int dataVersion, List<Region> regions) {

    /**
     * A region's blocks in its own coordinates (min corner at the origin) and where that origin sits relative to the
     * schematic origin.
     */
    public record Region(String name, Structure structure, BlockPos position) {
    }

    public SchematicFile {
        regions = List.copyOf(regions);
        name = name == null ? "" : name;
        author = author == null ? "" : author;
        description = description == null ? "" : description;
    }

    public static SchematicFile single(String name, Structure structure) {
        return new SchematicFile(name, structure.metadata().author, structure.metadata().description,
                structure.metadata().dataVersion, List.of(new Region(name, structure, BlockPos.ORIGIN)));
    }

    /** All regions pasted into one structure at their relative positions. */
    public Structure merged() {
        if (regions.size() == 1 && regions.getFirst().position().equals(BlockPos.ORIGIN)) return regions.getFirst().structure();
        Structure out = new Structure();
        for (Region r : regions) out.paste(r.structure(), r.position().x(), r.position().y(), r.position().z());
        out.metadata().name = name;
        out.metadata().author = author;
        out.metadata().description = description;
        out.metadata().dataVersion = dataVersion;
        return out;
    }

    public long totalBlocks() {
        return regions.stream().mapToLong(r -> r.structure().blockCount()).sum();
    }
}
