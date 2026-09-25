package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.version.McVersion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loot for the containers of a generated structure: which loot table each kind of container rolls when a player
 * first opens it (or brushes it), chosen from Minecraft's own tables or a custom table written into the pack.
 */
public final class LootTables {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int V1_20_3 = 3698, V1_21 = 3953;

    private LootTables() {
    }

    /** Blocks that can carry a loot table, grouped the way they're offered in the export window. */
    public enum Container {
        CHEST("Chests", "minecraft:chest", 0),
        TRAPPED_CHEST("Trapped chests", "minecraft:trapped_chest", 0),
        BARREL("Barrels", "minecraft:barrel", 0),
        SHULKER_BOX("Shulker boxes", "minecraft:shulker_box", 0),
        DISPENSER("Dispensers", "minecraft:dispenser", 0),
        DROPPER("Droppers", "minecraft:dropper", 0),
        HOPPER("Hoppers", "minecraft:hopper", 0),
        DECORATED_POT("Decorated pots", "minecraft:decorated_pot", V1_20_3),
        SUSPICIOUS_SAND("Suspicious sand", "minecraft:brushable_block", 0),
        SUSPICIOUS_GRAVEL("Suspicious gravel", "minecraft:brushable_block", 0);

        public final String label;
        /** Block entity id written when the block has no data yet. */
        final String blockEntity;
        /** Oldest data version where this block takes a loot table. */
        final int since;

        Container(String label, String blockEntity, int since) {
            this.label = label;
            this.blockEntity = blockEntity;
            this.since = since;
        }

        /** Rolled when brushed rather than opened (archaeology tables). */
        public boolean brushable() {
            return this == SUSPICIOUS_SAND || this == SUSPICIOUS_GRAVEL;
        }

        public boolean supports(McVersion v) {
            return v.dataVersion() >= since;
        }

        /** The container kind of a block, or null when it can't hold loot. */
        public static Container of(BlockState st) {
            if (!st.namespace().equals("minecraft")) return null;
            String p = st.path();
            return switch (p) {
                case "chest" -> CHEST;
                case "trapped_chest" -> TRAPPED_CHEST;
                case "barrel" -> BARREL;
                case "dispenser" -> DISPENSER;
                case "dropper" -> DROPPER;
                case "hopper" -> HOPPER;
                case "decorated_pot" -> DECORATED_POT;
                case "suspicious_sand" -> SUSPICIOUS_SAND;
                case "suspicious_gravel" -> SUSPICIOUS_GRAVEL;
                default -> p.endsWith("shulker_box") ? SHULKER_BOX : p.endsWith("copper_chest") ? CHEST : null;
            };
        }
    }

    /** One of Minecraft's own loot tables, with a readable name and the menu group it's listed under. */
    public record Preset(String id, String label, String group, int since) {
        public boolean supports(McVersion v) {
            return v.dataVersion() >= since;
        }
    }

    public static final List<Preset> PRESETS = List.of(
            p("chests/simple_dungeon", "Dungeon", "Dungeons & ruins"),
            p("chests/abandoned_mineshaft", "Mineshaft", "Dungeons & ruins"),
            p("chests/stronghold_corridor", "Stronghold corridor", "Dungeons & ruins"),
            p("chests/stronghold_crossing", "Stronghold crossing", "Dungeons & ruins"),
            p("chests/stronghold_library", "Stronghold library", "Dungeons & ruins"),
            p("chests/ancient_city", "Ancient city", "Dungeons & ruins"),
            p("chests/ancient_city_ice_box", "Ancient city ice box", "Dungeons & ruins"),
            p("chests/ruined_portal", "Ruined portal", "Dungeons & ruins"),
            p("chests/woodland_mansion", "Woodland mansion", "Dungeons & ruins"),
            p("chests/pillager_outpost", "Pillager outpost", "Dungeons & ruins"),
            p("chests/igloo_chest", "Igloo", "Dungeons & ruins"),
            p("chests/spawn_bonus_chest", "Bonus chest", "Dungeons & ruins"),

            p("chests/desert_pyramid", "Desert pyramid", "Temples"),
            p("chests/jungle_temple", "Jungle temple", "Temples"),
            p("chests/jungle_temple_dispenser", "Jungle temple dispenser (arrows)", "Temples"),

            p("chests/village/village_plains_house", "Plains house", "Villages"),
            p("chests/village/village_desert_house", "Desert house", "Villages"),
            p("chests/village/village_savanna_house", "Savanna house", "Villages"),
            p("chests/village/village_snowy_house", "Snowy house", "Villages"),
            p("chests/village/village_taiga_house", "Taiga house", "Villages"),
            p("chests/village/village_armorer", "Armorer", "Villages"),
            p("chests/village/village_butcher", "Butcher", "Villages"),
            p("chests/village/village_cartographer", "Cartographer", "Villages"),
            p("chests/village/village_fisher", "Fisher", "Villages"),
            p("chests/village/village_fletcher", "Fletcher", "Villages"),
            p("chests/village/village_mason", "Mason", "Villages"),
            p("chests/village/village_shepherd", "Shepherd", "Villages"),
            p("chests/village/village_tannery", "Tannery", "Villages"),
            p("chests/village/village_temple", "Temple (cleric)", "Villages"),
            p("chests/village/village_toolsmith", "Toolsmith", "Villages"),
            p("chests/village/village_weaponsmith", "Weaponsmith", "Villages"),

            p("chests/shipwreck_supply", "Shipwreck supplies", "Ocean"),
            p("chests/shipwreck_map", "Shipwreck map", "Ocean"),
            p("chests/shipwreck_treasure", "Shipwreck treasure", "Ocean"),
            p("chests/buried_treasure", "Buried treasure", "Ocean"),
            p("chests/underwater_ruin_small", "Ocean ruin (small)", "Ocean"),
            p("chests/underwater_ruin_big", "Ocean ruin (big)", "Ocean"),

            p("chests/nether_bridge", "Nether fortress", "Nether & End"),
            p("chests/bastion_treasure", "Bastion treasure", "Nether & End"),
            p("chests/bastion_other", "Bastion", "Nether & End"),
            p("chests/bastion_bridge", "Bastion bridge", "Nether & End"),
            p("chests/bastion_hoglin_stable", "Bastion hoglin stable", "Nether & End"),
            p("chests/end_city_treasure", "End city", "Nether & End"),

            new Preset("minecraft:chests/trial_chambers/supply", "Supplies", "Trial chambers", V1_21),
            new Preset("minecraft:chests/trial_chambers/corridor", "Corridor", "Trial chambers", V1_21),
            new Preset("minecraft:chests/trial_chambers/entrance", "Entrance", "Trial chambers", V1_21),
            new Preset("minecraft:chests/trial_chambers/intersection", "Intersection", "Trial chambers", V1_21),
            new Preset("minecraft:chests/trial_chambers/intersection_barrel", "Intersection barrel", "Trial chambers", V1_21),
            new Preset("minecraft:chests/trial_chambers/reward", "Vault reward", "Trial chambers", V1_21),
            new Preset("minecraft:chests/trial_chambers/reward_ominous", "Ominous vault reward", "Trial chambers", V1_21),

            p("archaeology/desert_pyramid", "Desert pyramid", "Archaeology (brushing)"),
            p("archaeology/desert_well", "Desert well", "Archaeology (brushing)"),
            p("archaeology/ocean_ruin_warm", "Warm ocean ruin", "Archaeology (brushing)"),
            p("archaeology/ocean_ruin_cold", "Cold ocean ruin", "Archaeology (brushing)"),
            p("archaeology/trail_ruins_common", "Trail ruins (common)", "Archaeology (brushing)"),
            p("archaeology/trail_ruins_rare", "Trail ruins (rare)", "Archaeology (brushing)"));

    private static Preset p(String path, String label, String group) {
        return new Preset("minecraft:" + path, label, group, 0);
    }

    /** The preset with this id, or null. */
    public static Preset preset(String id) {
        return PRESETS.stream().filter(pr -> pr.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * One kind of item in a custom table.
     *
     * @param weight  how likely this item is picked on each roll, relative to the others
     * @param min     fewest of it per pick
     * @param max     most of it per pick
     * @param enchant give it a random enchantment (tools, weapons, armour and books)
     */
    public record Item(String item, int weight, int min, int max, boolean enchant) {
    }

    /**
     * A loot table you make yourself: each time the container is filled, a random number of rolls between
     * {@code minRolls} and {@code maxRolls} each pick one item by weight.
     */
    public record CustomTable(String name, int minRolls, int maxRolls, List<Item> items) {
        public CustomTable {
            items = items == null ? List.of() : List.copyOf(items);
        }

        /** File name inside the pack. */
        public String slug() {
            String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
            return s.isEmpty() ? "loot" : s;
        }
    }

    /** What a kind of container gets. */
    public sealed interface Choice {
        /** Leave the containers as built: their items, or any loot table they already have. */
        record Keep() implements Choice {
        }

        /** Generate empty. */
        record Empty() implements Choice {
        }

        /** An existing table: Minecraft's, a mod's or another data pack's, by id. */
        record Table(String id) implements Choice {
        }

        /** A custom table, written into this pack. */
        record Custom(CustomTable table) implements Choice {
        }

        Choice KEEP = new Keep(), EMPTY = new Empty();
    }

    /** Checks the loot settings; problems in plain language. */
    public static List<String> validate(Map<Container, Choice> loot, McVersion v) {
        List<String> errors = new ArrayList<>();
        for (var e : loot.entrySet()) {
            String what = e.getKey().label;
            if (e.getValue() instanceof Choice.Table t && !ID.matcher(t.id()).matches()) {
                errors.add(what + ": '" + t.id() + "' isn't a loot table id (like minecraft:chests/simple_dungeon)");
            }
            if (e.getValue() instanceof Choice.Table t && preset(t.id()) != null && !preset(t.id()).supports(v)) {
                errors.add(what + ": " + preset(t.id()).label() + " loot needs a newer Minecraft version");
            }
            if (e.getValue() instanceof Choice.Custom c) errors.addAll(validate(c.table()).stream().map(m -> what + ": " + m).toList());
        }
        return errors;
    }

    public static List<String> validate(CustomTable t) {
        List<String> errors = new ArrayList<>();
        String n = "loot table '" + t.name() + "'";
        if (t.name() == null || t.name().isBlank()) errors.add("A custom loot table needs a name");
        if (t.items().isEmpty()) errors.add(n + " has no items");
        if (t.minRolls() < 0 || t.maxRolls() < t.minRolls() || t.maxRolls() > 64) errors.add(n + ": rolls must be 0–64, lowest first");
        for (Item i : t.items()) {
            if (i.item() == null || !ID.matcher(i.item()).matches()) errors.add(n + ": '" + i.item() + "' isn't an item id");
            if (i.weight() < 1) errors.add(n + ": " + i.item() + " needs a weight of at least 1");
            if (i.min() < 1 || i.max() < i.min() || i.max() > 64) errors.add(n + ": " + i.item() + " count must be 1–64, lowest first");
        }
        return errors;
    }

    /** The id a custom table gets inside the pack: {@code <ns>:<structure>/<table>}. */
    static String customId(DatapackExporter.Options o, CustomTable t) {
        return o.namespace() + ":" + o.name() + "/" + t.slug();
    }

    /** Writes each custom table in use once. */
    static void write(DatapackExporter.Options o, Map<String, byte[]> files) throws java.io.IOException {
        Map<String, CustomTable> tables = new LinkedHashMap<>();
        Map<String, Boolean> onlyBrushed = new LinkedHashMap<>();
        for (var e : o.loot().entrySet()) {
            if (!(e.getValue() instanceof Choice.Custom c)) continue;
            tables.put(c.table().slug(), c.table());
            onlyBrushed.merge(c.table().slug(), e.getKey().brushable(), Boolean::logicalAnd);
        }
        for (CustomTable t : tables.values()) {
            String path = "data/" + o.namespace() + "/" + o.version().lootTableFolder() + "/" + o.name() + "/" + t.slug() + ".json";
            files.put(path, JSON.writeValueAsBytes(json(t, onlyBrushed.get(t.slug()) ? "minecraft:archaeology" : "minecraft:chest")));
        }
    }

    static ObjectNode json(CustomTable t, String type) {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", type);
        ObjectNode pool = root.putArray("pools").addObject();
        if (t.minRolls() == t.maxRolls()) pool.put("rolls", t.minRolls());
        else pool.putObject("rolls").put("type", "minecraft:uniform").put("min", t.minRolls()).put("max", t.maxRolls());
        pool.put("bonus_rolls", 0);
        ArrayNode entries = pool.putArray("entries");
        for (Item i : t.items()) {
            ObjectNode e = entries.addObject().put("type", "minecraft:item").put("name", i.item()).put("weight", i.weight());
            ArrayNode fns = JSON.createArrayNode();
            if (i.min() != 1 || i.max() != 1) {
                ObjectNode sc = fns.addObject().put("function", "minecraft:set_count");
                if (i.min() == i.max()) sc.put("count", i.min());
                else sc.putObject("count").put("type", "minecraft:uniform").put("min", i.min()).put("max", i.max());
            }
            if (i.enchant()) fns.addObject().put("function", "minecraft:enchant_randomly");
            if (!fns.isEmpty()) e.set("functions", fns);
        }
        return root;
    }

    /** How many of each container kind a structure holds. */
    public static Map<Container, Integer> count(Structure s) {
        Map<Container, Integer> out = new EnumMap<>(Container.class);
        s.forEachBlock((x, y, z, st) -> {
            Container c = Container.of(st);
            if (c != null) out.merge(c, 1, Integer::sum);
        });
        return out;
    }

    /** A copy of the structure with the chosen loot put into its containers (or the structure itself when nothing changes). */
    static Structure apply(Structure src, DatapackExporter.Options o) {
        Map<Container, Choice> loot = o.loot();
        if (loot.values().stream().allMatch(c -> c instanceof Choice.Keep)) return src;
        Structure s = src.copy();
        List<BlockPos> targets = new ArrayList<>();
        List<Container> kinds = new ArrayList<>();
        s.forEachBlock((x, y, z, st) -> {
            Container c = Container.of(st);
            if (c != null && c.supports(o.version()) && !(loot.getOrDefault(c, Choice.KEEP) instanceof Choice.Keep)) {
                targets.add(new BlockPos(x, y, z));
                kinds.add(c);
            }
        });
        for (int i = 0; i < targets.size(); i++) {
            BlockPos pos = targets.get(i);
            Container c = kinds.get(i);
            CompoundTag be = s.blockEntity(pos);
            be = be == null ? new CompoundTag().putString("id", c.blockEntity) : be.copy();
            // Contents that would otherwise sit alongside (or instead of) the rolled loot.
            be.remove("Items");
            be.remove("item");
            be.remove("LootTable");
            be.remove("LootTableSeed");
            switch (loot.get(c)) {
                case Choice.Table t -> be.putString("LootTable", t.id());
                case Choice.Custom cu -> be.putString("LootTable", customId(o, cu.table()));
                default -> {
                }
            }
            s.setBlockEntity(pos, be);
        }
        return s;
    }
}
