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
    private static final List<SchematicFormat> BUILT_IN = List.of(LITEMATICA, SPONGE, VANILLA);
    /** Built-in formats first, then any registered by plugins. */
    private static final List<SchematicFormat> FORMATS = new java.util.concurrent.CopyOnWriteArrayList<>(BUILT_IN);

    private Schematics() {
    }

    /** Every readable/writable format: the built-in ones plus plugin formats. */
    public static List<SchematicFormat> formats() {
        return java.util.Collections.unmodifiableList(FORMATS);
    }

    public static boolean isBuiltIn(SchematicFormat f) {
        return BUILT_IN.contains(f);
    }

    /** Adds a format (from a plugin). Its id must not clash with an existing one. */
    public static void register(SchematicFormat format) {
        for (SchematicFormat f : FORMATS) {
            if (f.id().equals(format.id())) throw new IllegalArgumentException("A format with id '" + format.id() + "' is already registered");
        }
        FORMATS.add(format);
    }

    /** Removes a plugin format again; built-in formats stay. */
    public static void unregister(SchematicFormat format) {
        if (!BUILT_IN.contains(format)) FORMATS.remove(format);
    }

    public static Optional<SchematicFormat> byId(String id) {
        return FORMATS.stream().filter(f -> f.id().equals(id)).findFirst();
    }

    /** Every extension any format reads, without dots. */
    public static List<String> allExtensions() {
        return FORMATS.stream().flatMap(f -> f.extensions().stream()).distinct().toList();
    }

    public static Optional<SchematicFormat> byExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        String ext = name.substring(dot + 1);
        return FORMATS.stream().filter(f -> f.extensions().contains(ext)).findFirst();
    }

    public static boolean isSupported(Path file) {
        return byExtension(file).filter(SchematicFormat::canRead).isPresent();
    }

    /** Reads a schematic, detecting the format from its contents (falling back to the extension). */
    public static SchematicFile read(Path file) throws IOException {
        return readDetailed(file).file();
    }

    /** The format of an NBT file: by content first (formats share extensions), then by extension. */
    public static SchematicFormat detect(Path file, io.blockdesigner.core.nbt.CompoundTag root) throws IOException {
        for (SchematicFormat f : FORMATS) {
            if (!f.nbtBased() || !f.canRead()) continue;
            try {
                if (f.detect(root)) return f;
            } catch (RuntimeException e) {
                // a plugin's detector failed on this file: try the others
            }
        }
        return byExtension(file).orElseThrow(() -> new IOException("Unrecognised schematic format: " + file.getFileName()));
    }

    /** Reads a schematic and reports which format it was, e.g. to label the imported layer. */
    public static Read readDetailed(Path file) throws IOException {
        SchematicFormat format;
        SchematicFile sf;
        Optional<SchematicFormat> byExt = byExtension(file);
        if (byExt.isPresent() && !byExt.get().nbtBased()) {
            format = byExt.get();
            sf = format.readFile(file);
        } else {
            var root = NbtIO.read(file).tag();
            format = detect(file, root);
            sf = format.read(root);
        }
        if (sf.name().isBlank()) {
            String fn = file.getFileName().toString();
            int dot = fn.lastIndexOf('.');
            sf = new SchematicFile(dot > 0 ? fn.substring(0, dot) : fn, sf.author(), sf.description(), sf.dataVersion(), sf.regions());
        }
        return new Read(sf, format);
    }

    public record Read(SchematicFile file, SchematicFormat format) {
    }

    public static void write(SchematicFile file, SchematicFormat format, WriteOptions options, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        if (format.nbtBased()) {
            SchematicFormat.Encoded enc = format.write(file, options);
            NbtIO.write(enc.root(), enc.rootName(), tmp, true);
        } else {
            format.writeFile(file, options, tmp);
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
