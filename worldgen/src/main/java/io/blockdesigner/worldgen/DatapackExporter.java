package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;
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
import java.util.Locale;
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

    /**
     * How the ground around the structure is reshaped when it generates (Minecraft's {@code terrain_adaptation}).
     * Anything but NONE also reserves a 12-block margin around the structure for the blending.
     */
    public enum TerrainAdaptation {
        NONE("No blending", "Placed exactly as built. The ground isn't touched, so on a slope parts can hang in the air or cut "
                + "into the hill.", "Floating islands, underground rooms, builds that already have their own base"),
        BEARD_THIN("Soft blend", "Raises the ground under the footprint and clears hills above it, then feathers the edges into "
                + "the surroundings over a few blocks. The build sits naturally on uneven ground.", "Villages, pillager outposts"),
        BEARD_BOX("Solid base", "Like soft blend, but fills a solid block of ground under the whole footprint with straight "
                + "sides, so nothing overhangs even on steep slopes.", "Ancient cities; heavy builds on rough ground"),
        BURY("Buried", "Pulls the surrounding ground up over and around the structure, half hiding it. Combine with a negative "
                + "height to sink ruins you dig up.", "Trail ruins"),
        ENCAPSULATE("Encased", "Wraps the structure's box in solid ground on every side, filling caves and air pockets around "
                + "it, so it reads as carved into rock.", "Trial chambers; underground rooms and vaults");

        public final String label, description, usedBy;

        TerrainAdaptation(String label, String description, String usedBy) {
            this.label = label;
            this.description = description;
            this.usedBy = usedBy;
        }
    }

    /** When in world generation the structure is placed (Minecraft's generation step). */
    public enum Step {
        RAW_GENERATION("Raw generation", "Before anything else, even carvers and lakes"),
        LAKES("Lakes", "With lava and water lakes"),
        LOCAL_MODIFICATIONS("Local modifications", "With geodes and icebergs"),
        UNDERGROUND_STRUCTURES("Underground structures", "With mineshafts and dungeons; caves can still cut through it later"),
        SURFACE_STRUCTURES("Surface structures", "With villages, temples and outposts (usual choice)"),
        STRONGHOLDS("Strongholds", "With strongholds"),
        UNDERGROUND_ORES("Underground ores", "With ore veins"),
        UNDERGROUND_DECORATION("Underground decoration", "With fossils and cave features"),
        FLUID_SPRINGS("Fluid springs", "With water and lava springs"),
        VEGETAL_DECORATION("Vegetal decoration", "With trees and plants, which can then grow into it"),
        TOP_LAYER_MODIFICATION("Top layer", "Last of all, after snow and ice");

        public final String label, description;

        Step(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /**
     * Reads a biome tag (e.g. {@code minecraft:is_ocean}) as its raw entries: biome ids and nested {@code #tags}.
     * Empty when the tag isn't known. Tags from several packs are already merged.
     */
    @FunctionalInterface
    public interface BiomeTags {
        java.util.Optional<List<String>> entries(String tagId);
    }

    /** Biomes that are water: oceans, rivers, beaches and swamps (for "stay away from water"). */
    public static final List<String> WATER_BIOMES = List.of("#minecraft:is_ocean", "#minecraft:is_river", "#minecraft:is_beach",
            "minecraft:stony_shore", "minecraft:swamp", "minecraft:mangrove_swamp");

    /**
     * The biomes to spawn in: the chosen biomes and tags with the excluded ones taken away. Tags are expanded into
     * single biomes (a tag can't subtract), so this needs {@link BiomeTags}; with no exclusions the chosen entries
     * are returned as they are.
     *
     * @throws IllegalArgumentException when a tag can't be read or nothing is left
     */
    public static List<String> resolveBiomes(List<String> include, List<String> exclude, BiomeTags tags) {
        if (exclude.isEmpty()) return include;
        if (tags == null) throw new IllegalArgumentException("Load a Minecraft version first: excluding biomes needs the game's biome lists");
        java.util.LinkedHashSet<String> in = new java.util.LinkedHashSet<>(), out = new java.util.LinkedHashSet<>();
        for (String e : include) expand(e, tags, in, new java.util.HashSet<>());
        for (String e : exclude) expand(e, tags, out, new java.util.HashSet<>());
        in.removeAll(out);
        if (in.isEmpty()) throw new IllegalArgumentException("Every chosen biome is excluded; nothing is left to spawn in");
        return List.copyOf(in);
    }

    private static void expand(String entry, BiomeTags tags, java.util.Set<String> into, java.util.Set<String> seen) {
        String e = entry.strip();
        if (e.isEmpty()) return;
        if (!e.startsWith("#")) {
            into.add(e.contains(":") ? e : "minecraft:" + e);
            return;
        }
        String id = e.substring(1);
        if (!id.contains(":")) id = "minecraft:" + id;
        if (!seen.add(id)) return;
        List<String> entries = tags.entries(id).orElseThrow(() -> new IllegalArgumentException(
                "Can't read the biome tag #" + entry.replace("#", "") + " from the game or mods"));
        for (String sub : entries) expand(sub, tags, into, seen);
    }

    /** Where the structure's start sits vertically. */
    public enum HeightMode {
        SURFACE("On the surface", "On top of the ground (world surface heightmap), raised or sunk by the offset"),
        OCEAN_FLOOR("On the sea floor", "On the ground under any water (ocean floor heightmap); on land same as the surface"),
        FIXED("At a fixed height", "At the Y level given by the offset, whatever the terrain; good for sky islands and fixed-depth vaults"),
        RANGE("Random height range", "A random Y between the two levels for each one, like dungeons and underground ruins");

        public final String label, description;

        HeightMode(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** How structures are scattered inside their grid cells ({@code spread_type}). */
    public enum Spread {
        LINEAR("Random", "Anywhere in each cell; neighbours can end up close together"),
        TRIANGULAR("Even", "Biased towards the middle of each cell, so they are spaced more evenly");

        public final String label, description;

        Spread(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /**
     * Finer control over placement, blending and look.
     *
     * @param height          how the start height is chosen
     * @param minY            lowest Y for {@link HeightMode#RANGE}
     * @param maxY            highest Y for {@link HeightMode#RANGE}
     * @param foundationDepth blocks of foundation added under the build's bottom layer (0 = none); the build is sunk by
     *                        the same amount so its floor stays where it was
     * @param foundationBlock the foundation block, or {@code match} to continue each bottom block downwards
     * @param integrity       share of blocks kept (1 = intact, lower = a ruin with blocks randomly missing)
     * @param mossiness       share of stone that turns mossy or cracked (0 = new)
     * @param spread          scatter within grid cells
     * @param avoidSet        a structure set to keep away from (e.g. {@code minecraft:villages}), or null
     * @param avoidChunks     how many chunks away from {@code avoidSet} (1–16)
     * @param excludeBiomes   biomes and #tags it must never spawn in (a blacklist, taken away from the chosen biomes)
     * @param noWaterlogging  blocks placed into water stay dry instead of becoming waterlogged (1.21+)
     * @param biomeTags       reads biome tags from the game and mods; needed to take exclusions away from tags
     */
    public record Advanced(HeightMode height, int minY, int maxY, int foundationDepth, String foundationBlock,
                           double integrity, double mossiness, Spread spread, String avoidSet, int avoidChunks,
                           List<String> excludeBiomes, boolean noWaterlogging, BiomeTags biomeTags) {

        public Advanced {
            excludeBiomes = excludeBiomes == null ? List.of() : List.copyOf(excludeBiomes);
        }

        public Advanced(HeightMode height, int minY, int maxY, int foundationDepth, String foundationBlock,
                        double integrity, double mossiness, Spread spread, String avoidSet, int avoidChunks) {
            this(height, minY, maxY, foundationDepth, foundationBlock, integrity, mossiness, spread, avoidSet, avoidChunks, List.of(), false, null);
        }

        public static Advanced defaults() {
            return new Advanced(HeightMode.SURFACE, -40, 20, 0, "match", 1.0, 0.0, Spread.LINEAR, null, 4);
        }

        public Advanced withBiomes(List<String> exclude, boolean dry, BiomeTags tags) {
            return new Advanced(height, minY, maxY, foundationDepth, foundationBlock, integrity, mossiness, spread, avoidSet, avoidChunks,
                    exclude, dry, tags);
        }

        boolean weathered() {
            return integrity < 0.999 || mossiness > 0.001;
        }
    }

    /** One template in the start pool; several pieces become random variants chosen by weight. */
    public record Piece(String name, Structure structure, int weight) {
    }

    /**
     * @param yOffset      shift applied to the template relative to the surface (negative sinks it into the ground)
     * @param includeAir   write air so the structure carves terrain inside its bounds; otherwise terrain shows through
     * @param followSurface place on the world surface heightmap (vs. an absolute Y given by {@code yOffset})
     * @param loot          loot table per kind of container; kinds left out keep what they were built with
     */
    public record Options(String namespace, String name, String description, McVersion version, List<String> biomes,
                          Step step, TerrainAdaptation terrainAdaptation, boolean followSurface, int yOffset,
                          int spacing, int separation, int salt, double frequency, boolean includeAir, Advanced advanced,
                          Map<LootTables.Container, LootTables.Choice> loot) {

        public Options {
            if (advanced == null) advanced = Advanced.defaults();
            loot = loot == null ? Map.of() : Map.copyOf(loot);
        }

        public Options(String namespace, String name, String description, McVersion version, List<String> biomes,
                       Step step, TerrainAdaptation terrainAdaptation, boolean followSurface, int yOffset,
                       int spacing, int separation, int salt, double frequency, boolean includeAir, Advanced advanced) {
            this(namespace, name, description, version, biomes, step, terrainAdaptation, followSurface, yOffset, spacing, separation,
                    salt, frequency, includeAir, advanced, Map.of());
        }

        public Options(String namespace, String name, String description, McVersion version, List<String> biomes,
                       Step step, TerrainAdaptation terrainAdaptation, boolean followSurface, int yOffset,
                       int spacing, int separation, int salt, double frequency, boolean includeAir) {
            this(namespace, name, description, version, biomes, step, terrainAdaptation, followSurface, yOffset, spacing, separation,
                    salt, frequency, includeAir, Advanced.defaults());
        }

        public Options withAdvanced(Advanced a) {
            return new Options(namespace, name, description, version, biomes, step, terrainAdaptation, followSurface, yOffset,
                    spacing, separation, salt, frequency, includeAir, a, loot);
        }

        public Options withLoot(Map<LootTables.Container, LootTables.Choice> l) {
            return new Options(namespace, name, description, version, biomes, step, terrainAdaptation, followSurface, yOffset,
                    spacing, separation, salt, frequency, includeAir, advanced, l);
        }

        /** The height mode actually used (an old-style "don't follow the surface" means a fixed height). */
        HeightMode heightMode() {
            return advanced.height() == HeightMode.SURFACE && !followSurface ? HeightMode.FIXED : advanced.height();
        }

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
        if (!o.biomes().isEmpty() && !o.advanced().excludeBiomes().isEmpty()) {
            try {
                resolveBiomes(o.biomes(), o.advanced().excludeBiomes(), o.advanced().biomeTags());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }
        if (pieces.isEmpty()) errors.add("Nothing to export");
        Advanced a = o.advanced();
        if (a.integrity() <= 0 || a.integrity() > 1) errors.add("Integrity must be above 0% and at most 100%");
        if (a.mossiness() < 0 || a.mossiness() > 1) errors.add("Mossiness must be 0–100%");
        if (a.foundationDepth() < 0 || a.foundationDepth() > 64) errors.add("Foundation depth must be 0–64");
        if (a.foundationDepth() > 0 && !"match".equals(a.foundationBlock())) {
            try {
                BlockState.parse(a.foundationBlock());
            } catch (IllegalArgumentException e) {
                errors.add("Foundation block: " + e.getMessage());
            }
        }
        if (o.heightMode() == HeightMode.RANGE && a.minY() > a.maxY()) errors.add("The lowest height must not be above the highest");
        errors.addAll(LootTables.validate(o.loot(), o.version()));
        if (a.avoidSet() != null && (a.avoidChunks() < 1 || a.avoidChunks() > 16)) errors.add("Keep-away distance must be 1–16 chunks");
        for (Piece p : pieces) {
            if (p.structure().blockCount() == 0) errors.add("'" + p.name() + "' is empty");
            if (!ID_PART.matcher(p.name()).matches()) errors.add("Piece name '" + p.name() + "' may only use a-z, 0-9, _ . -");
            p.structure().bounds().ifPresent(b -> {
                if (Math.max(b.sizeX(), b.sizeZ()) > 2 * maxDistance(o)) {
                    errors.add("'" + p.name() + "' is " + Math.max(b.sizeX(), b.sizeZ()) + " blocks wide; worldgen supports up to "
                            + 2 * maxDistance(o) + " for this version and terrain setting (" + o.terrainAdaptation().name().toLowerCase(Locale.ROOT) + ")");
                }
            });
        }
        return errors;
    }

    /**
     * The structure's {@code max_distance_from_center}. Minecraft rejects the pack unless this plus the terrain
     * adaptation padding (12 blocks for anything but {@code none}) stays within 128, so beard/bury structures get 116.
     */
    static int maxDistance(Options o) {
        int limit = o.version().dataVersion() >= 3955 ? 128 : 80;
        int padding = o.terrainAdaptation() == TerrainAdaptation.NONE ? 0 : 12;
        return Math.min(limit, 128 - padding);
    }

    /** All files of the pack, path → bytes (for writing to a zip or folder, and for tests). */
    public static Map<String, byte[]> build(Options o, List<Piece> pieces) throws IOException {
        List<String> errors = validate(o, pieces);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        Map<String, byte[]> files = new LinkedHashMap<>();
        packMeta(o, files);
        String processors = processorList(o, files);
        for (Piece p : pieces) template(o, p.name(), withFoundation(p.structure(), o.advanced()), files);

        // Template pool: each piece is a weighted alternative.
        List<PoolEntry> start = new ArrayList<>();
        for (Piece p : pieces) start.add(new PoolEntry(p.name(), p.weight(), "rigid", processors));
        pool(o, "start", start, files);

        structure(o, 1, o.advanced().foundationDepth(), files);
        commonFiles(o, files);
        return files;
    }

    // ---- village-style (multi-piece jigsaw) structures --------------------------------------------------------

    /** What a layer is in a village: the centre it grows from, a street piece, or a building along the streets. */
    public enum Role { START, STREET, BUILDING }

    /** A horizontal side of a piece (its entrance, or a jigsaw's facing). */
    public enum Side {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx, dz;

        Side(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    /**
     * One template of a village.
     *
     * @param entrance for buildings, the side facing the street; null finds the door (or uses the south side)
     */
    public record VillagePiece(String name, Structure structure, Role role, int weight, Side entrance) {
    }

    /**
     * Settings for village-style generation: a start piece with streets growing from it and buildings along them,
     * joined by jigsaw blocks the exporter adds for you (pieces that already contain jigsaw blocks are left alone).
     *
     * @param depth           how many pieces away from the start the village can grow (jigsaw "size", 1–7)
     * @param streetBlock     block for generated streets, plaza and doorsteps, e.g. {@code minecraft:dirt_path}
     * @param generateStreets add generated straight streets and crossroads to the street pool
     * @param streetWidth     width of generated streets (odd, 3–7)
     */
    public record Village(int depth, String streetBlock, boolean generateStreets, int streetWidth) {
        public static Village defaults() {
            return new Village(6, "minecraft:dirt_path", true, 3);
        }
    }

    /** Largest jigsaw {@code size}: 7 up to 1.20.1, 20 from 1.20.2. */
    public static int maxVillageSize(McVersion v) {
        return v.dataVersion() >= 3578 ? 20 : 7;
    }

    public static List<String> validateVillage(Options o, Village v, List<VillagePiece> pieces) {
        List<Piece> asPieces = pieces.stream().map(p -> new Piece(p.name(), p.structure(), p.weight())).toList();
        List<String> errors = new ArrayList<>(validate(o, asPieces.isEmpty() && v.generateStreets() ? List.of() : asPieces));
        errors.remove("Nothing to export");
        if (pieces.stream().noneMatch(p -> p.role() == Role.BUILDING)) errors.add("Mark at least one layer as a building");
        if (!v.generateStreets() && pieces.stream().noneMatch(p -> p.role() == Role.STREET)) {
            errors.add("Add street layers, or let the exporter generate streets");
        }
        int maxSize = maxVillageSize(o.version());
        if (v.depth() < 1 || v.depth() > maxSize) errors.add("Village size must be 1–" + maxSize + " for this version");
        if (v.streetWidth() < 1 || v.streetWidth() > 9 || v.streetWidth() % 2 == 0) errors.add("Street width must be odd, 1–9");
        try {
            BlockState.parse(v.streetBlock());
        } catch (IllegalArgumentException e) {
            errors.add("Street block: " + e.getMessage());
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (VillagePiece p : pieces) if (!names.add(p.name())) errors.add("Two pieces are called '" + p.name() + "'");
        return errors;
    }

    /**
     * A village-style pack: pools {@code start}, {@code streets} and {@code houses}. Streets follow the terrain
     * ({@code terrain_matching}); the start and buildings keep their shape ({@code rigid}) and sit at street level.
     */
    public static Map<String, byte[]> buildVillage(Options o, Village v, List<VillagePiece> pieces) throws IOException {
        List<String> errors = validateVillage(o, v, pieces);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        McVersion ver = o.version();
        String ns = o.namespace(), base = ns + ":" + o.name() + "/";
        String streetName = ns + ":street", entrance = ns + ":building_entrance";
        String streetsPool = base + "streets", housesPool = base + "houses";
        BlockState path = BlockState.parse(v.streetBlock());
        Jigsaw toStreets = new Jigsaw(streetName, streetName, streetsPool);
        Jigsaw toHouses = new Jigsaw(ns + ":street_side", entrance, housesPool);
        Jigsaw houseDoor = new Jigsaw(entrance, "minecraft:empty", "minecraft:empty");

        Map<String, byte[]> files = new LinkedHashMap<>();
        packMeta(o, files);
        String processors = processorList(o, files);
        int foundation = o.advanced().foundationDepth();
        List<PoolEntry> start = new ArrayList<>(), streets = new ArrayList<>(), houses = new ArrayList<>();

        for (VillagePiece p : pieces) {
            // Buildings and the centre get the foundation (streets follow the terrain instead).
            Structure s = p.role() == Role.STREET ? p.structure().copy() : withFoundation(p.structure(), o.advanced());
            s.normalizeToOrigin();
            boolean own = hasJigsaw(s);
            switch (p.role()) {
                case START -> {
                    // Street connectors at the original floor, above the foundation.
                    if (!own) for (Side side : Side.values()) edgeJigsaw(s, side, foundation, toStreets.named(ns + ":start"), path, ver);
                    start.add(new PoolEntry(p.name(), p.weight(), "rigid", processors));
                }
                case STREET -> {
                    if (!own) {
                        // A street layer runs along its longer axis: ends continue the street, the long sides take houses.
                        Box b = s.bounds().orElseThrow();
                        boolean alongX = b.sizeX() >= b.sizeZ();
                        for (Side side : alongX ? List.of(Side.WEST, Side.EAST) : List.of(Side.NORTH, Side.SOUTH)) edgeJigsaw(s, side, 0, toStreets, path, ver);
                        for (Side side : alongX ? List.of(Side.NORTH, Side.SOUTH) : List.of(Side.WEST, Side.EAST)) edgeJigsaw(s, side, 0, toHouses, path, ver);
                    }
                    streets.add(new PoolEntry(p.name(), p.weight(), "terrain_matching", EMPTY));
                }
                case BUILDING -> {
                    if (!own) s = withEntrance(s, p.entrance(), houseDoor, path, ver);
                    houses.add(new PoolEntry(p.name(), p.weight(), "rigid", processors));
                }
            }
            template(o, p.name(), s, files);
        }
        boolean userStart = !start.isEmpty();
        if (!userStart) {
            template(o, "plaza", plaza(v, path, toStreets.named(ns + ":start"), ver), files);
            start.add(new PoolEntry("plaza", 1, "terrain_matching", EMPTY));
        }
        if (v.generateStreets()) {
            int w = v.streetWidth();
            template(o, "street_long", street(15, w, path, toStreets, toHouses, ver), files);
            template(o, "street_short", street(9, w, path, toStreets, toHouses, ver), files);
            template(o, "crossroad", crossroad(w, path, toStreets, ver), files);
            streets.add(new PoolEntry("street_long", 4, "terrain_matching", EMPTY));
            streets.add(new PoolEntry("street_short", 3, "terrain_matching", EMPTY));
            streets.add(new PoolEntry("crossroad", 2, "terrain_matching", EMPTY));
        }
        pool(o, "start", start, files);
        pool(o, "streets", streets, files);
        pool(o, "houses", houses, files);
        structure(o, v.depth(), userStart ? foundation : 0, files);
        commonFiles(o, files);
        return files;
    }

    public static void writeVillageZip(Options o, Village v, List<VillagePiece> pieces, Path zip) throws IOException {
        writeZip(buildVillage(o, v, pieces), zip);
    }

    /** Jigsaw connection data: this jigsaw's name, the name it connects to, and the pool the connected piece comes from. */
    record Jigsaw(String name, String target, String pool) {
        Jigsaw named(String n) {
            return new Jigsaw(n, target, pool);
        }
    }

    private static boolean hasJigsaw(Structure s) {
        return s.usedStates().stream().anyMatch(st -> st.name().equals("minecraft:jigsaw"));
    }

    /**
     * Puts a jigsaw facing out of {@code side} in the middle of that edge at height {@code y}. It turns into the block
     * that was there when the piece generates (or {@code fallback} for an empty cell, e.g. a path doorstep).
     */
    static void edgeJigsaw(Structure s, Side side, int y, Jigsaw j, BlockState fallback, McVersion v) {
        Box b = s.bounds().orElse(new Box(0, 0, 0, 0, 0, 0));
        int x = switch (side) {
            case WEST -> b.minX();
            case EAST -> b.maxX();
            default -> (b.minX() + b.maxX()) / 2;
        };
        int z = switch (side) {
            case NORTH -> b.minZ();
            case SOUTH -> b.maxZ();
            default -> (b.minZ() + b.maxZ()) / 2;
        };
        placeJigsaw(s, x, y, z, side, j, fallback, v);
    }

    /** Sets a jigsaw block (and its block entity) at a position, remembering the replaced block as its final state. */
    static void placeJigsaw(Structure s, int x, int y, int z, Side facing, Jigsaw j, BlockState fallback, McVersion v) {
        BlockState here = s.get(x, y, z);
        BlockState finalState = here.isAir() || here.name().equals("minecraft:jigsaw") ? fallback : here;
        BlockPos pos = new BlockPos(x, y, z);
        s.set(pos, BlockState.of("minecraft:jigsaw", Map.of("orientation", facing.name().toLowerCase(Locale.ROOT) + "_up")));
        CompoundTag be = new CompoundTag()
                .putString("id", "minecraft:jigsaw")
                .putString("name", j.name())
                .putString("target", j.target())
                .putString("pool", j.pool())
                .putString("final_state", finalState.toString())
                .putString("joint", "aligned");
        // 1.20.3 added placement ordering; older versions reject nothing but ignore it, so only write it where it exists.
        if (v.dataVersion() >= 3698) {
            be.putInt("placement_priority", 0);
            be.putInt("selection_priority", 0);
        }
        s.setBlockEntity(pos, be);
    }

    /**
     * Adds a building's entrance jigsaw on its street side, under the door when there is one: the house then stands
     * with its door facing the street, its floor at street level. A door on the bottom layer gets a doorstep row
     * underneath so the jigsaw doesn't replace it.
     */
    static Structure withEntrance(Structure s, Side wanted, Jigsaw j, BlockState path, McVersion v) {
        Box b = s.bounds().orElseThrow();
        BlockPos door = null;
        Side side = wanted;
        int best = Integer.MAX_VALUE;
        for (BlockState st : s.usedStates()) {
            if (!st.name().endsWith("_door") || !"lower".equals(st.get("half"))) continue;
            int[] found = findDoor(s, st, b, wanted);
            if (found != null && found[3] < best) {
                best = found[3];
                door = new BlockPos(found[0], found[1], found[2]);
                if (wanted == null) side = Side.values()[found[4]];
            }
        }
        if (side == null) side = Side.SOUTH;
        int y = door == null ? b.minY() : door.y() - 1;
        if (y < b.minY()) {
            Structure lifted = new Structure();
            lifted.paste(s, 0, 1, 0);
            lifted.metadata().dataVersion = s.metadata().dataVersion;
            s = lifted;
            b = s.bounds().orElseThrow();
            door = door.add(0, 1, 0);
            y = door.y() - 1;
        }
        int x = switch (side) {
            case WEST -> b.minX();
            case EAST -> b.maxX();
            default -> door != null ? door.x() : (b.minX() + b.maxX()) / 2;
        };
        int z = switch (side) {
            case NORTH -> b.minZ();
            case SOUTH -> b.maxZ();
            default -> door != null ? door.z() : (b.minZ() + b.maxZ()) / 2;
        };
        placeJigsaw(s, x, y, z, side, j, path, v);
        return s;
    }

    /** The door (of this state) nearest an edge, or the wanted edge: {x, y, z, distance, side ordinal}. */
    private static int[] findDoor(Structure s, BlockState door, Box b, Side wanted) {
        int[][] best = {null};
        s.forEachBlock((x, y, z, st) -> {
            if (st != door) return;
            int[] d = {z - b.minZ(), b.maxX() - x, b.maxZ() - z, x - b.minX()}; // N E S W
            for (Side side : Side.values()) {
                if (wanted != null && side != wanted) continue;
                int dist = d[side.ordinal()] * 1000 + (y - b.minY());
                if (best[0] == null || dist < best[0][3]) best[0] = new int[]{x, y, z, dist, side.ordinal()};
            }
        });
        return best[0];
    }

    /** A straight street along X: path blocks, street jigsaws at both ends, house jigsaws along both sides. */
    static Structure street(int length, int width, BlockState path, Jigsaw toStreets, Jigsaw toHouses, McVersion v) {
        Structure s = new Structure();
        for (int x = 0; x < length; x++) for (int z = 0; z < width; z++) s.set(x, 0, z, path);
        int mid = width / 2;
        placeJigsaw(s, 0, 0, mid, Side.WEST, toStreets, path, v);
        placeJigsaw(s, length - 1, 0, mid, Side.EAST, toStreets, path, v);
        // A house slot every 6 blocks, away from the ends.
        for (int x = 3; x < length - 3; x += 6) {
            placeJigsaw(s, x, 0, 0, Side.NORTH, toHouses, path, v);
            placeJigsaw(s, x, 0, width - 1, Side.SOUTH, toHouses, path, v);
        }
        return s;
    }

    /** A square junction with a street jigsaw on every side. */
    static Structure crossroad(int width, BlockState path, Jigsaw toStreets, McVersion v) {
        Structure s = new Structure();
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) s.set(x, 0, z, path);
        for (Side side : Side.values()) edgeJigsaw(s, side, 0, toStreets, path, v);
        return s;
    }

    /** The default start when no layer is marked as one: a small square the streets leave from. */
    static Structure plaza(Village v, BlockState path, Jigsaw toStreets, McVersion ver) {
        int size = v.streetWidth() + 4;
        Structure s = new Structure();
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) s.set(x, 0, z, path);
        for (Side side : Side.values()) edgeJigsaw(s, side, 0, toStreets, path, ver);
        return s;
    }

    // ---- pack pieces shared by both layouts ------------------------------------------------------------------

    private static final String EMPTY = "minecraft:empty";

    private record PoolEntry(String piece, int weight, String projection, String processors) {
    }

    /**
     * Writes the weathering processor list (ruin: random missing blocks; age: mossy and cracked stone) and returns its
     * id, or {@code minecraft:empty} when the structure stays pristine.
     */
    private static String processorList(Options o, Map<String, byte[]> files) throws IOException {
        Advanced a = o.advanced();
        if (!a.weathered()) return EMPTY;
        ObjectNode list = JSON.createObjectNode();
        ArrayNode ps = list.putArray("processors");
        if (a.integrity() < 0.999) ps.addObject().put("processor_type", "minecraft:block_rot").put("integrity", round(a.integrity()));
        if (a.mossiness() > 0.001) ps.addObject().put("processor_type", "minecraft:block_age").put("mossiness", round(a.mossiness()));
        files.put("data/" + o.namespace() + "/worldgen/processor_list/" + o.name() + ".json", JSON.writeValueAsBytes(list));
        return o.namespace() + ":" + o.name();
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /**
     * Adds {@code depth} blocks of foundation under every block of the bottom layer, so the build stands on solid
     * footings instead of floating over dips in the terrain.
     */
    static Structure withFoundation(Structure src, Advanced a) {
        Structure s = src.copy();
        int depth = a.foundationDepth();
        if (depth <= 0 || s.blockCount() == 0) return s;
        Box b = s.bounds().orElseThrow();
        int y0 = b.minY();
        BlockState fixed = "match".equals(a.foundationBlock()) ? null : BlockState.parse(a.foundationBlock());
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                BlockState bottom = src.get(x, y0, z);
                if (bottom.isAir()) continue;
                BlockState f = fixed != null ? fixed : footing(bottom);
                for (int d = 1; d <= depth; d++) s.set(x, y0 - d, z, f);
            }
        }
        return s;
    }

    /** What to continue a bottom block down with: itself for plain blocks, dirt under grass and paths, else cobblestone. */
    private static BlockState footing(BlockState bottom) {
        String p = bottom.path();
        if (p.equals("grass_block") || p.equals("dirt_path") || p.equals("farmland") || p.equals("podzol") || p.equals("mycelium")) {
            return BlockState.of("minecraft:dirt");
        }
        boolean partial = p.contains("slab") || p.contains("stairs") || p.contains("carpet") || p.contains("pressure_plate")
                || p.contains("door") || p.contains("fence") || p.contains("wall") || p.contains("pane") || p.contains("torch")
                || p.contains("rail") || p.contains("button") || p.contains("sign") || p.contains("flower") || p.contains("sapling");
        if (!partial && bottom.properties().isEmpty()) return bottom;
        if (!partial && bottom.has("axis")) return bottom;
        return BlockState.of("minecraft:cobblestone");
    }

    private static void packMeta(Options o, Map<String, byte[]> files) throws IOException {
        McVersion v = o.version();
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
    }

    /** A structure template (vanilla .nbt) at {@code <ns>:<name>/<piece>}. */
    private static void template(Options o, String piece, Structure s, Map<String, byte[]> files) throws IOException {
        McVersion v = o.version();
        WriteOptions wo = WriteOptions.defaults(v).withIncludeAir(o.includeAir());
        var enc = Schematics.VANILLA.write(SchematicFile.single(piece, LootTables.apply(s, o)), wo);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.write(enc.root(), enc.rootName(), out, true);
        files.put("data/" + o.namespace() + "/" + v.structureFolder() + "/" + o.name() + "/" + piece + ".nbt", out.toByteArray());
    }

    private static void pool(Options o, String poolName, List<PoolEntry> entries, Map<String, byte[]> files) throws IOException {
        ObjectNode pool = JSON.createObjectNode();
        ArrayNode elements = pool.putArray("elements");
        for (PoolEntry p : entries) {
            ObjectNode e = elements.addObject();
            ObjectNode el = e.putObject("element");
            el.put("element_type", "minecraft:single_pool_element");
            el.put("location", o.namespace() + ":" + o.name() + "/" + p.piece());
            el.put("processors", p.processors());
            el.put("projection", p.projection());
            e.put("weight", Math.max(1, p.weight()));
        }
        pool.put("fallback", "minecraft:empty");
        files.put("data/" + o.namespace() + "/worldgen/template_pool/" + o.name() + "/" + poolName + ".json", JSON.writeValueAsBytes(pool));
    }

    /** @param sink extra blocks to lower the start by (its foundation), so the original floor stays at the chosen height */
    private static void structure(Options o, int size, int sink, Map<String, byte[]> files) throws IOException {
        String ns = o.namespace();
        ObjectNode st = JSON.createObjectNode();
        st.put("type", "minecraft:jigsaw");
        st.put("biomes", "#" + ns + ":has_structure/" + o.name());
        st.put("step", o.step().name().toLowerCase(Locale.ROOT));
        st.put("terrain_adaptation", o.terrainAdaptation().name().toLowerCase(Locale.ROOT));
        st.putObject("spawn_overrides");
        st.put("start_pool", ns + ":" + o.name() + "/start");
        st.put("size", size);
        switch (o.heightMode()) {
            case RANGE -> {
                ObjectNode h = st.putObject("start_height");
                h.put("type", "minecraft:uniform");
                h.putObject("min_inclusive").put("absolute", o.advanced().minY() - sink);
                h.putObject("max_inclusive").put("absolute", o.advanced().maxY() - sink);
            }
            case FIXED -> st.putObject("start_height").put("absolute", o.yOffset() - sink);
            case SURFACE -> {
                st.putObject("start_height").put("absolute", o.yOffset() - sink);
                st.put("project_start_to_heightmap", "WORLD_SURFACE_WG");
            }
            case OCEAN_FLOOR -> {
                st.putObject("start_height").put("absolute", o.yOffset() - sink);
                st.put("project_start_to_heightmap", "OCEAN_FLOOR_WG");
            }
        }
        st.put("max_distance_from_center", maxDistance(o));
        st.put("use_expansion_hack", false);
        // 1.21 lets jigsaw structures keep blocks dry when they generate into water.
        if (o.advanced().noWaterlogging() && o.version().dataVersion() >= 3953) st.put("liquid_settings", "ignore_waterlogging");
        files.put("data/" + ns + "/worldgen/structure/" + o.name() + ".json", JSON.writeValueAsBytes(st));
    }

    /** Structure set (placement), biome tag and custom loot tables. */
    private static void commonFiles(Options o, Map<String, byte[]> files) throws IOException {
        String ns = o.namespace(), id = ns + ":" + o.name();
        LootTables.write(o, files);
        ObjectNode set = JSON.createObjectNode();
        ArrayNode structures = set.putArray("structures");
        structures.addObject().put("structure", id).put("weight", 1);
        ObjectNode placement = set.putObject("placement");
        placement.put("type", "minecraft:random_spread");
        placement.put("spacing", o.spacing());
        placement.put("separation", o.separation());
        placement.put("salt", o.salt());
        if (o.frequency() < 1) placement.put("frequency", round(o.frequency()));
        if (o.advanced().spread() == Spread.TRIANGULAR) placement.put("spread_type", "triangular");
        if (o.advanced().avoidSet() != null) {
            placement.putObject("exclusion_zone").put("other_set", o.advanced().avoidSet()).put("chunk_count", o.advanced().avoidChunks());
        }
        files.put("data/" + ns + "/worldgen/structure_set/" + o.name() + ".json", JSON.writeValueAsBytes(set));

        ObjectNode tag = JSON.createObjectNode();
        ArrayNode values = tag.putArray("values");
        List<String> biomes = resolveBiomes(o.biomes(), o.advanced().excludeBiomes(), o.advanced().biomeTags());
        for (String b : biomes) {
            // Expanded lists name single biomes; modded ones are optional so the pack still loads without that mod.
            if (b.startsWith("#") || b.startsWith("minecraft:")) values.add(b);
            else values.addObject().put("id", b).put("required", false);
        }
        files.put("data/" + ns + "/tags/worldgen/biome/has_structure/" + o.name() + ".json", JSON.writeValueAsBytes(tag));
    }

    private static com.fasterxml.jackson.databind.JsonNode format(McVersion v) {
        if (v.dataPackMinor() == 0) return JSON.getNodeFactory().numberNode(v.dataPackMajor());
        ArrayNode a = JSON.createArrayNode();
        a.add(v.dataPackMajor()).add(v.dataPackMinor());
        return a;
    }

    /** Writes the pack as a zip (for the world's datapacks folder or to share). */
    public static void writeZip(Options o, List<Piece> pieces, Path zip) throws IOException {
        writeZip(build(o, pieces), zip);
    }

    private static void writeZip(Map<String, byte[]> files, Path zip) throws IOException {
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
