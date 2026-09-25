package com.example.hello;

import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A human-readable schematic: one {@code x y z block_state} line per block. Not NBT, so it overrides
 * {@link #readFile} / {@link #writeFile} and returns false from {@link #nbtBased()}.
 */
final class TextFormat implements SchematicFormat {

    @Override
    public String id() {
        return "hello-text";
    }

    @Override
    public String displayName() {
        return "Block list (text)";
    }

    @Override
    public List<String> extensions() {
        return List.of("bdtxt");
    }

    @Override
    public boolean nbtBased() {
        return false;
    }

    @Override
    public SchematicFile readFile(Path file) throws IOException {
        Structure s = new Structure();
        int n = 0;
        for (String line : Files.readAllLines(file)) {
            n++;
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\s+", 4);
            if (p.length < 4) throw new IOException("Line " + n + ": expected 'x y z block'");
            try {
                s.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), BlockState.parse(p[3]));
            } catch (IllegalArgumentException e) {
                throw new IOException("Line " + n + ": " + e.getMessage());
            }
        }
        String name = file.getFileName().toString().replaceFirst("\\..*$", "");
        return SchematicFile.single(name, s);
    }

    @Override
    public void writeFile(SchematicFile file, WriteOptions options, Path target) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(target)) {
            w.write("# " + file.name() + " · x y z block\n");
            for (SchematicFile.Region r : file.regions()) {
                BlockPos o = r.position();
                r.structure().forEachBlock((x, y, z, st) -> {
                    try {
                        w.write((x + o.x()) + " " + (y + o.y()) + " " + (z + o.z()) + " " + st + "\n");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
