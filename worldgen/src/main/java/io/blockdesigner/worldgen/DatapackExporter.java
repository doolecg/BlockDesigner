package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.NbtIO;
import io.blockdesigner.core.version.McVersion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a data pack that makes a structure generate naturally: the structure template(s), a jigsaw structure
 * definition, a template pool, a structure set (placement) and a biome tag. Layout and pack format follow the
 * target {@link McVersion}.
 */
public final class DatapackExporter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern ID_PART = Pattern.compile("[a-z0-9_.-]+");

    public enum TerrainAdaptation { NONE, BEARD_THIN, BEARD_BOX, BURY, ENCAPSULATE }

    public enum Step {
        RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES, STRONGHOLDS,
        UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, VEGETAL_DECORATION, TOP_LAYER_MODIFICATION
    }

    /** One template in the start pool; several pieces become random variants chosen by weight. */
    public record Piece(String name, Structure structure, int weight) {
    }

    /**
     * @param yOffset      shift applied to the template relative to the surface (negative sinks it into the ground)
     * @param includeAir   write air so the structure carves terrain inside its bounds; otherwise terrain shows through
     * @param followSurface place on the world surface heightmap (vs. an absolute Y given by {@code yOffset})
     */
    public record Options(String namespace, String name, String description, McVersion version, List<String> biomes,
                          Step step, TerrainAdaptation terrainAdaptation, boolean followSurface, int yOffset,
                          int spacing, int separation, int salt, double frequency, boolean includeAir) {

        public static Options defaults(String namespace, String name, McVersion version) {
            return new Options(namespace, name, "Structures made with BlockDesigner", version,
                    List.of("#minecraft:is_overworld"), Step.SURFACE_STRUCTURES, TerrainAdaptation.BEARD_THIN, true, 0,
                    34, 8, Math.abs((namespace + ":" + name).hashCode()) % 1_000_000_000, 1.0, false);
        }
    }

    private DatapackExporter() {
    }

    /** Checks options; returns problems in plain language (empty when valid). */
    public static List<String> validate(Options o, List<Piece> pieces) {
        List<String> errors = new ArrayList<>();
        if (!ID_PART.matcher(o.namespace()).matches()) errors.add("Namespace may only use a-z, 0-9, _ . -");
        if (o.namespace().equals("minecraft")) errors.add("Use your own namespace rather than 'minecraft'");
        if (!ID_PART.matcher(o.name()).matches()) errors.add("Structure name may only use a-z, 0-9, _ . -");
        if (o.spacing() < 1 || o.spacing() > 4096) errors.add("Spacing must be between 1 and 4096 chunks");
        if (o.separation() < 0 || o.separation() >= o.spacing()) errors.add("Separation must be smaller than spacing");
        if (o.frequency() <= 0 || o.frequency() > 1) errors.add("Frequency must be in (0, 1]");
        if (o.biomes().isEmpty()) errors.add("Pick at least one biome or biome tag");
        if (pieces.isEmpty()) errors.add("Nothing to export");
        for (Piece p : pieces) {
            if (p.structure().blockCount() == 0) errors.add("'" + p.name() + "' is empty");
            if (!ID_PART.matcher(p.name()).matches()) errors.add("Piece name '" + p.name() + "' may only use a-z, 0-9, _ . -");
            p.structure().bounds().ifPresent(b -> {
                if (Math.max(b.sizeX(), b.sizeZ()) > 2 * maxDistance(o.version())) {
                    errors.add("'" + p.name() + "' is " + Math.max(b.sizeX(), b.sizeZ()) + " blocks wide; worldgen supports up to "
                            + 2 * maxDistance(o.version()) + " for this version");
                }
            });
        }
        return errors;
    }

    private static int maxDistance(McVersion v) {
        return v.dataVersion() >= 3955 ? 128 : 80;
    }

    /** All files of the pack, path → bytes (for writing to a zip or folder, and for tests). */
    public static Map<String, byte[]> build(Options o, List<Piece> pieces) throws IOException {
        List<String> errors = validate(o, pieces);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        McVersion v = o.version();
        String ns = o.namespace(), id = ns + ":" + o.name();
        Map<String, byte[]> files = new LinkedHashMap<>();

        // pack.mcmeta
        ObjectNode meta = JSON.createObjectNode();
        ObjectNode pack = meta.putObject("pack");
        pack.put("description", o.description());
        if (v.usesMinMaxPackFormat()) {
            pack.set("min_format", format(v));
            pack.set("max_format", format(v));
        } else {
            pack.put("pack_format", v.dataPackMajor());
        }
        files.put("pack.mcmeta", JSON.writeValueAsBytes(meta));

        // Structure templates (vanilla .nbt)
        SchematicFormat nbt = Schematics.VANILLA;
        WriteOptions wo = WriteOptions.defaults(v).withIncludeAir(o.includeAir());
        for (Piece p : pieces) {
            var enc = nbt.write(SchematicFile.single(p.name(), p.structure()), wo);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIO.write(enc.root(), enc.rootName(), out, true);
            files.put("data/" + ns + "/" + v.structureFolder() + "/" + o.name() + "/" + p.name() + ".nbt", out.toByteArray());
        }

        // Template pool: each piece is a weighted alternative.
        ObjectNode pool = JSON.createObjectNode();
        ArrayNode elements = pool.putArray("elements");
        for (Piece p : pieces) {
            ObjectNode e = elements.addObject();
            ObjectNode el = e.putObject("element");
            el.put("element_type", "minecraft:single_pool_element");
            el.put("location", ns + ":" + o.name() + "/" + p.name());
            el.put("processors", "minecraft:empty");
            el.put("projection", "rigid");
            e.put("weight", Math.max(1, p.weight()));
        }
        pool.put("fallback", "minecraft:empty");
        files.put("data/" + ns + "/worldgen/template_pool/" + o.name() + "/start.json", JSON.writeValueAsBytes(pool));

        // Structure
        ObjectNode st = JSON.createObjectNode();
        st.put("type", "minecraft:jigsaw");
        st.put("biomes", "#" + ns + ":has_structure/" + o.name());
        st.put("step", o.step().name().toLowerCase());
        st.put("terrain_adaptation", o.terrainAdaptation().name().toLowerCase());
        st.putObject("spawn_overrides");
        st.put("start_pool", ns + ":" + o.name() + "/start");
        st.put("size", 1);
        st.putObject("start_height").put("absolute", o.yOffset());
        if (o.followSurface()) st.put("project_start_to_heightmap", "WORLD_SURFACE_WG");
        st.put("max_distance_from_center", maxDistance(v));
        st.put("use_expansion_hack", false);
        files.put("data/" + ns + "/worldgen/structure/" + o.name() + ".json", JSON.writeValueAsBytes(st));

        // Structure set (placement)
        ObjectNode set = JSON.createObjectNode();
        ArrayNode structures = set.putArray("structures");
        structures.addObject().put("structure", id).put("weight", 1);
        ObjectNode placement = set.putObject("placement");
        placement.put("type", "minecraft:random_spread");
        placement.put("spacing", o.spacing());
        placement.put("separation", o.separation());
        placement.put("salt", o.salt());
        if (o.frequency() < 1) placement.put("frequency", o.frequency());
        files.put("data/" + ns + "/worldgen/structure_set/" + o.name() + ".json", JSON.writeValueAsBytes(set));

        // Biome tag
        ObjectNode tag = JSON.createObjectNode();
        ArrayNode values = tag.putArray("values");
        o.biomes().forEach(values::add);
        files.put("data/" + ns + "/tags/worldgen/biome/has_structure/" + o.name() + ".json", JSON.writeValueAsBytes(tag));
        return files;
    }

    private static com.fasterxml.jackson.databind.JsonNode format(McVersion v) {
        if (v.dataPackMinor() == 0) return JSON.getNodeFactory().numberNode(v.dataPackMajor());
        ArrayNode a = JSON.createArrayNode();
        a.add(v.dataPackMajor()).add(v.dataPackMinor());
        return a;
    }

    /** Writes the pack as a zip (for the world's datapacks folder or to share). */
    public static void writeZip(Options o, List<Piece> pieces, Path zip) throws IOException {
        Map<String, byte[]> files = build(o, pieces);
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream z = new ZipOutputStream(os)) {
            for (var e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Writes the pack as a folder (e.g. directly into saves/&lt;world&gt;/datapacks). */
    public static void writeFolder(Options o, List<Piece> pieces, Path dir) throws IOException {
        for (var e : build(o, pieces).entrySet()) {
            Path f = dir.resolve(e.getKey());
            Files.createDirectories(f.getParent() == null ? dir : f.getParent());
            Files.write(f, e.getValue());
        }
    }

    /** In-game commands to test the result. */
    public static String testCommands(Options o) {
        String id = o.namespace() + ":" + o.name();
        return "/place structure " + id + "\n/locate structure " + id;
    }
}
