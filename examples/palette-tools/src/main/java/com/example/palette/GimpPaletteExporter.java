package com.example.palette;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginExporter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the build's blocks as a GIMP / Krita / Inkscape palette (.gpl): one colour per kind of block, named after it.
 * Shows export options, a summary line on the card and progress while writing.
 */
final class GimpPaletteExporter implements PluginExporter {
    private final BlockCatalog blocks;

    GimpPaletteExporter(BlockCatalog blocks) {
        this.blocks = blocks;
    }

    @Override
    public String id() {
        return "gpl";
    }

    @Override
    public String displayName() {
        return "Colour palette";
    }

    @Override
    public String description() {
        return "The build's block colours for GIMP, Krita or Inkscape";
    }

    @Override
    public String extension() {
        return "gpl";
    }

    @Override
    public Options options() {
        return Options.builder()
                .integer("min", "Leave out blocks used fewer times than", 1, 1, 10_000)
                .choice("order", "Order", List.of("most used first", "by name"), "most used first")
                .build();
    }

    @Override
    public String summary(Structure merged) {
        return counts(merged, 1).size() + " kinds of block";
    }

    private static Map<String, Long> counts(Structure s, int min) {
        Map<String, Long> out = new TreeMap<>();
        s.stateCounts().forEach((st, n) -> out.merge(st.name(), n, Long::sum));
        out.values().removeIf(n -> n < min);
        return out;
    }

    @Override
    public void export(Request r) throws IOException {
        Map<String, Long> counts = counts(r.merged(), r.options().integer("min"));
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        if (r.options().choice("order").startsWith("most")) entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        StringBuilder sb = new StringBuilder("GIMP Palette\nName: " + r.name() + "\nColumns: 8\n#\n");
        int i = 0;
        for (var e : entries) {
            BlockState st = BlockState.of(e.getKey());
            int c = blocks.averageColor(st);
            sb.append(String.format("%3d %3d %3d\t%s (%d)%n", (c >> 16) & 255, (c >> 8) & 255, c & 255, blocks.displayName(st), e.getValue()));
            r.progress().update(++i / (double) entries.size(), "Colour " + i + " of " + entries.size());
        }
        Files.writeString(r.target(), sb);
    }
}
