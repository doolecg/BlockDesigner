package io.blockdesigner.core.blocks;

import io.blockdesigner.core.blocks.BlockFamily.Shape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Works out block families and material variants from block ids, the way Minecraft names them: {@code <wood>_stairs},
 * {@code <stone>_brick_slab} next to {@code <stone>_bricks}, {@code stripped_<wood>_log}, {@code cracked_} and
 * {@code mossy_} prefixes, copper's {@code exposed_} / {@code waxed_} stages. There is no hand-written table: every
 * guess is checked against the ids that exist (the loaded registry), so modded blocks named the same way work too.
 */
public final class BlockFamilies {
    /** Prefixes that make a variant of a material, e.g. {@code mossy_cobblestone}, {@code cracked_stone_bricks}. */
    public static final List<String> MODIFIERS = List.of("cracked", "mossy", "chiseled", "polished", "smooth", "cut",
            "exposed", "weathered", "oxidized", "waxed");
    private static final Set<String> COPPER_STAGES = Set.of("exposed", "weathered", "oxidized");

    /** Suffix of each shape, longest first where one ends with another ({@code _wall_sign} before {@code _sign}). */
    private static final List<Map.Entry<String, Shape>> SUFFIXES = List.of(
            Map.entry("_wall_hanging_sign", Shape.WALL_HANGING_SIGN),
            Map.entry("_hanging_sign", Shape.HANGING_SIGN),
            Map.entry("_wall_sign", Shape.WALL_SIGN),
            Map.entry("_sign", Shape.SIGN),
            Map.entry("_pressure_plate", Shape.PRESSURE_PLATE),
            Map.entry("_fence_gate", Shape.FENCE_GATE),
            Map.entry("_fence", Shape.FENCE),
            Map.entry("_trapdoor", Shape.TRAPDOOR),
            Map.entry("_door", Shape.DOOR),
            Map.entry("_button", Shape.BUTTON),
            Map.entry("_stairs", Shape.STAIRS),
            Map.entry("_slab", Shape.SLAB),
            Map.entry("_wall", Shape.WALL),
            Map.entry("_log", Shape.LOG),
            Map.entry("_stem", Shape.LOG),
            Map.entry("_wood", Shape.WOOD),
            Map.entry("_hyphae", Shape.WOOD));

    private final Predicate<String> exists;

    /** @param exists whether a block id (with namespace, e.g. {@code minecraft:oak_stairs}) is a real block */
    public BlockFamilies(Predicate<String> exists) {
        this.exists = exists;
    }

    /** The family {@code blockId} belongs to, or empty for a block that comes in one shape only. */
    public Optional<BlockFamily> family(String blockId) {
        String ns = namespace(blockId), path = path(blockId);
        boolean stripped = path.startsWith("stripped_");
        for (var e : SUFFIXES) {
            String suffix = e.getKey();
            if (!path.endsWith(suffix) || path.length() == suffix.length()) continue;
            String stem = path.substring(0, path.length() - suffix.length());
            if (stripped && (e.getValue() == Shape.LOG || e.getValue() == Shape.WOOD)) stem = stem.substring("stripped_".length());
            BlockFamily f = build(ns, stem, null);
            return f.members().size() > 1 && f.contains(ns + ":" + path) ? Optional.of(f) : Optional.empty();
        }
        // A full block: its family's stem is the id without _planks / the plural s / _block, or the id itself.
        List<String> stems = new ArrayList<>();
        if (path.endsWith("_planks")) stems.add(path.substring(0, path.length() - "_planks".length()));
        if (path.endsWith("bricks") || path.endsWith("tiles")) stems.add(path.substring(0, path.length() - 1));
        if (path.endsWith("_block")) stems.add(path.substring(0, path.length() - "_block".length()));
        stems.add(path);
        for (String stem : stems) {
            BlockFamily f = build(ns, stem, ns + ":" + path);
            if (f.members().size() > 1) return Optional.of(f);
        }
        return Optional.empty();
    }

    /** Which shape a block is, judged by its id alone ({@link Shape#BLOCK} when it has no shape suffix). */
    public static Shape shapeOf(String blockId) {
        String path = path(blockId);
        for (var e : SUFFIXES) {
            if (path.endsWith(e.getKey()) && path.length() > e.getKey().length()) {
                boolean stripped = path.startsWith("stripped_");
                if (stripped && e.getValue() == Shape.LOG) return Shape.STRIPPED_LOG;
                if (stripped && e.getValue() == Shape.WOOD) return Shape.STRIPPED_WOOD;
                return e.getValue();
            }
        }
        return Shape.BLOCK;
    }

    /** The block of the same shape as {@code blockId} in another family (oak_stairs → spruce_stairs). */
    public Optional<String> sameShape(String blockId, BlockFamily other) {
        return family(blockId).flatMap(f -> f.shapeOf(normalize(blockId))).flatMap(other::get);
    }

    /**
     * The {@code modifier} variant of a block, keeping its shape: {@code cobblestone} + mossy → {@code mossy_cobblestone},
     * {@code stone_brick_stairs} + mossy → {@code mossy_stone_brick_stairs}, {@code copper_block} + exposed →
     * {@code exposed_copper}, {@code waxed_cut_copper} + weathered → {@code waxed_weathered_cut_copper}. Empty when
     * that variant doesn't exist (there are no cracked stone brick stairs). A block that already is the variant is
     * returned as it is.
     */
    public Optional<String> withModifier(String blockId, String modifier) {
        String ns = namespace(blockId), path = path(blockId), m = modifier + "_";
        if (path.startsWith(m) || path.startsWith("waxed_" + m)) return Optional.of(ns + ":" + path);
        List<String> candidates = new ArrayList<>();
        if (COPPER_STAGES.contains(modifier) && path.startsWith("waxed_")) candidates.add("waxed_" + m + path.substring("waxed_".length()));
        candidates.add(m + path);
        // Copper's full block drops "_block" once it has a stage: copper_block → exposed_copper.
        if (path.endsWith("_block")) candidates.add(m + path.substring(0, path.length() - "_block".length()));
        for (String c : candidates) if (exists.test(ns + ":" + c)) return Optional.of(ns + ":" + c);
        return Optional.empty();
    }

    /** The reverse of {@link #withModifier}: {@code mossy_cobblestone} without mossy → {@code cobblestone}. */
    public Optional<String> withoutModifier(String blockId, String modifier) {
        String ns = namespace(blockId), path = path(blockId), m = modifier + "_";
        String rest;
        if (path.startsWith(m)) rest = path.substring(m.length());
        else if (path.startsWith("waxed_" + m)) rest = "waxed_" + path.substring(("waxed_" + m).length());
        else return Optional.empty();
        if (exists.test(ns + ":" + rest)) return Optional.of(ns + ":" + rest);
        if (exists.test(ns + ":" + rest + "_block")) return Optional.of(ns + ":" + rest + "_block");
        return Optional.empty();
    }

    /** The modifiers a block has a variant for, e.g. [cracked, mossy, chiseled] for {@code stone_bricks}. */
    public List<String> modifiers(String blockId) {
        List<String> out = new ArrayList<>();
        String id = normalize(blockId);
        for (String m : MODIFIERS) {
            Optional<String> v = withModifier(id, m);
            if (v.isPresent() && !v.get().equals(id)) out.add(m);
        }
        return out;
    }

    private BlockFamily build(String ns, String stem, String block) {
        Map<Shape, String> members = new EnumMap<>(Shape.class);
        if (block != null) members.put(Shape.BLOCK, block);
        else {
            List<String> full = new ArrayList<>(List.of(stem + "_planks"));
            if (stem.endsWith("brick") || stem.endsWith("tile")) full.add(stem + "s");
            full.add(stem + "_block");
            full.add(stem);
            for (String c : full) {
                if (exists.test(ns + ":" + c)) {
                    members.put(Shape.BLOCK, ns + ":" + c);
                    break;
                }
            }
        }
        for (var e : SUFFIXES) {
            Shape shape = e.getValue();
            String id = ns + ":" + stem + e.getKey();
            if (!members.containsKey(shape) && exists.test(id)) members.put(shape, id);
            if (shape == Shape.LOG || shape == Shape.WOOD) {
                Shape strippedShape = shape == Shape.LOG ? Shape.STRIPPED_LOG : Shape.STRIPPED_WOOD;
                String stripped = ns + ":stripped_" + stem + e.getKey();
                if (!members.containsKey(strippedShape) && exists.test(stripped)) members.put(strippedShape, stripped);
            }
        }
        return new BlockFamily(ns + ":" + stem, members);
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static String namespace(String id) {
        int i = id.indexOf(':');
        return i < 0 ? "minecraft" : id.substring(0, i);
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
