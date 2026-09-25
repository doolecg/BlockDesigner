package io.blockdesigner.core.formats;

import io.blockdesigner.core.nbt.NbtIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Entry point for reading and writing schematic files in any supported format. */
public final class Schematics {
    public static final SchematicFormat VANILLA = new VanillaStructureFormat();
    public static final SchematicFormat LITEMATICA = new LitematicaFormat();
    public static final SchematicFormat SPONGE = new SpongeSchematicFormat();
    private static final List<SchematicFormat> FORMATS = List.of(LITEMATICA, SPONGE, VANILLA);

    private Schematics() {
    }

    public static List<SchematicFormat> formats() {
        return FORMATS;
    }

    public static Optional<SchematicFormat> byExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        String ext = name.substring(dot + 1);
        return FORMATS.stream().filter(f -> f.extensions().contains(ext)).findFirst();
    }

    public static boolean isSupported(Path file) {
        return byExtension(file).isPresent();
    }

    /** Reads a schematic, detecting the format from its contents (falling back to the extension). */
    public static SchematicFile read(Path file) throws IOException {
        var root = NbtIO.read(file).tag();
        SchematicFormat format = FORMATS.stream().filter(f -> f.detect(root)).findFirst()
                .or(() -> byExtension(file))
                .orElseThrow(() -> new IOException("Unrecognised schematic format: " + file.getFileName()));
        SchematicFile sf = format.read(root);
        if (sf.name().isBlank()) {
            String fn = file.getFileName().toString();
            int dot = fn.lastIndexOf('.');
            sf = new SchematicFile(dot > 0 ? fn.substring(0, dot) : fn, sf.author(), sf.description(), sf.dataVersion(), sf.regions());
        }
        return sf;
    }

    public static void write(SchematicFile file, SchematicFormat format, WriteOptions options, Path target) throws IOException {
        SchematicFormat.Encoded enc = format.write(file, options);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        NbtIO.write(enc.root(), enc.rootName(), tmp, true);
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
