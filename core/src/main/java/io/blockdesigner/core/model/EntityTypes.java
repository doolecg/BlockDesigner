package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.FloatTag;
import io.blockdesigner.core.nbt.ListTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the editor knows about entity types: a display name, Minecraft's hitbox size (for picking and the placeholder
 * box drawn when there is no model), the spawn egg colour and a palette group. Unknown and modded ids still work, with
 * a name made from the id and a player-sized box.
 */
public final class EntityTypes {
    private EntityTypes() {
    }

    /**
     * @param width  hitbox width and depth in blocks
     * @param height hitbox height in blocks
     * @param color  0xRRGGBB for the placeholder box (the spawn egg's base colour)
     * @param group  palette section
     * @param living a mob (gets PersistenceRequired so it stays where it was put)
     */
    public record Kind(String id, String name, float width, float height, int color, String group, boolean living) {
    }

    public static final String FARM = "Farm animals", VILLAGE = "Villagers and golems", WILD = "Wild animals", WATER = "Water",
            HOSTILE = "Monsters", DECOR = "Decoration and vehicles";

    private static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    private static void add(String path, String name, double w, double h, int color, String group) {
        add(path, name, w, h, color, group, true);
    }

    private static void add(String path, String name, double w, double h, int color, String group, boolean living) {
        String id = "minecraft:" + path;
        KINDS.put(id, new Kind(id, name, (float) w, (float) h, color, group, living));
    }

    static {
        add("pig", "Pig", 0.9, 0.9, 0xF0A5A2, FARM);
        add("cow", "Cow", 0.9, 1.4, 0x443626, FARM);
        add("sheep", "Sheep", 0.9, 1.3, 0xE7E7E7, FARM);
        add("chicken", "Chicken", 0.4, 0.7, 0xA1A1A1, FARM);
        add("mooshroom", "Mooshroom", 0.9, 1.4, 0xA00F10, FARM);
        add("horse", "Horse", 1.3964844, 1.6, 0xC09E7D, FARM);
        add("donkey", "Donkey", 1.3964844, 1.5, 0x534539, FARM);
        add("mule", "Mule", 1.3964844, 1.6, 0x1B0200, FARM);
        add("goat", "Goat", 0.9, 1.3, 0xA5947C, FARM);
        add("rabbit", "Rabbit", 0.4, 0.5, 0x995F40, FARM);
        add("llama", "Llama", 0.9, 1.87, 0xC09E7D, FARM);
        add("camel", "Camel", 1.7, 2.375, 0xFCC369, FARM);
        add("bee", "Bee", 0.7, 0.6, 0xEDC343, FARM);
        add("turtle", "Turtle", 1.2, 0.4, 0xE7E7E7, FARM);

        add("villager", "Villager", 0.6, 1.95, 0x563C33, VILLAGE);
        add("wandering_trader", "Wandering Trader", 0.6, 1.95, 0x456296, VILLAGE);
        add("trader_llama", "Trader Llama", 0.9, 1.87, 0xEAA430, VILLAGE);
        add("iron_golem", "Iron Golem", 1.4, 2.7, 0xDBCDC1, VILLAGE);
        add("snow_golem", "Snow Golem", 0.7, 1.9, 0xD9F2F2, VILLAGE);
        add("allay", "Allay", 0.35, 0.6, 0x00DAFF, VILLAGE);
        add("cat", "Cat", 0.6, 0.7, 0xEFC88E, VILLAGE);
        add("wolf", "Wolf", 0.6, 0.85, 0xD7D3D3, VILLAGE);
        add("parrot", "Parrot", 0.5, 0.9, 0x0DA70B, VILLAGE);

        add("fox", "Fox", 0.6, 0.7, 0xD5B69F, WILD);
        add("ocelot", "Ocelot", 0.6, 0.7, 0xEFDE7D, WILD);
        add("panda", "Panda", 1.3, 1.25, 0xE7E7E7, WILD);
        add("polar_bear", "Polar Bear", 1.4, 1.4, 0xEEEEDE, WILD);
        add("frog", "Frog", 0.5, 0.5, 0xD07444, WILD);
        add("armadillo", "Armadillo", 0.7, 0.65, 0xAD716D, WILD);
        add("sniffer", "Sniffer", 1.9, 1.75, 0x871E09, WILD);
        add("bat", "Bat", 0.5, 0.9, 0x4C3E30, WILD);
        add("strider", "Strider", 0.9, 1.7, 0x9C3436, WILD);

        add("axolotl", "Axolotl", 0.75, 0.42, 0xFBC1E3, WATER);
        add("cod", "Cod", 0.5, 0.3, 0xC1A76A, WATER);
        add("salmon", "Salmon", 0.7, 0.4, 0xA00F10, WATER);
        add("tropical_fish", "Tropical Fish", 0.5, 0.4, 0xEF6915, WATER);
        add("pufferfish", "Pufferfish", 0.7, 0.7, 0xF6B201, WATER);
        add("squid", "Squid", 0.8, 0.8, 0x223B4D, WATER);
        add("glow_squid", "Glow Squid", 0.8, 0.8, 0x095656, WATER);
        add("dolphin", "Dolphin", 0.9, 0.6, 0x223B4D, WATER);

        add("zombie", "Zombie", 0.6, 1.95, 0x00AFAF, HOSTILE);
        add("husk", "Husk", 0.6, 1.95, 0x797061, HOSTILE);
        add("drowned", "Drowned", 0.6, 1.95, 0x8FF1D7, HOSTILE);
        add("skeleton", "Skeleton", 0.6, 1.99, 0xC1C1C1, HOSTILE);
        add("stray", "Stray", 0.6, 1.99, 0x617677, HOSTILE);
        add("wither_skeleton", "Wither Skeleton", 0.7, 2.4, 0x141414, HOSTILE);
        add("creeper", "Creeper", 0.6, 1.7, 0x0DA70B, HOSTILE);
        add("spider", "Spider", 1.4, 0.9, 0x342D27, HOSTILE);
        add("enderman", "Enderman", 0.6, 2.9, 0x161616, HOSTILE);
        add("witch", "Witch", 0.6, 1.95, 0x340000, HOSTILE);
        add("pillager", "Pillager", 0.6, 1.95, 0x532F36, HOSTILE);
        add("vindicator", "Vindicator", 0.6, 1.95, 0x959B9B, HOSTILE);
        add("slime", "Slime", 1.04, 1.04, 0x51A03E, HOSTILE);
        add("blaze", "Blaze", 0.6, 1.8, 0xF6B201, HOSTILE);
        add("piglin", "Piglin", 0.6, 1.95, 0x995F40, HOSTILE);
        add("guardian", "Guardian", 0.85, 0.85, 0x5A8272, HOSTILE);

        add("armor_stand", "Armor Stand", 0.5, 1.975, 0xB2946A, DECOR, false);
        add("item_frame", "Item Frame", 0.75, 0.75, 0xA07E4F, DECOR, false);
        add("glow_item_frame", "Glow Item Frame", 0.75, 0.75, 0x7FC8B8, DECOR, false);
        add("painting", "Painting", 1, 1, 0xB08453, DECOR, false);
        add("minecart", "Minecart", 0.98, 0.7, 0x8B8B8B, DECOR, false);
        add("chest_minecart", "Minecart with Chest", 0.98, 0.7, 0xA0772F, DECOR, false);
        add("oak_boat", "Oak Boat", 1.375, 0.5625, 0xA2834F, DECOR, false);
        add("end_crystal", "End Crystal", 2, 2, 0xCB85CB, DECOR, false);
    }

    /** Every known type, in palette order. */
    public static List<Kind> all() {
        return List.copyOf(KINDS.values());
    }

    public static List<String> groups() {
        return List.of(FARM, VILLAGE, WILD, WATER, HOSTILE, DECOR);
    }

    /** The type's details; unknown (modded) ids get a player-sized grey box and a name made from the id. */
    public static Kind kind(String id) {
        if (id == null || id.isBlank()) id = "minecraft:pig";
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        Kind k = KINDS.get(full);
        if (k != null) return k;
        if (full.endsWith("_boat") || full.endsWith("_raft")) return new Kind(full, nameOf(full), 1.375f, 0.5625f, 0xA2834F, DECOR, false);
        if (full.endsWith("minecart")) return new Kind(full, nameOf(full), 0.98f, 0.7f, 0x8B8B8B, DECOR, false);
        return new Kind(full, nameOf(full), 0.6f, 1.8f, 0x9AA3B5, WILD, true);
    }

    public static boolean known(String id) {
        return KINDS.containsKey(id);
    }

    /** "minecraft:wandering_trader" → "Wandering Trader". */
    public static String nameOf(String id) {
        String p = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : p.split("[_/]")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** The name to show for an entity: its custom name when it has a plain one, otherwise the type's. */
    public static String displayName(StructureEntity e) {
        String custom = e.nbt().getString("CustomName");
        if (!custom.isEmpty()) {
            // Text components are stored as JSON ("{\"text\":\"Bob\"}") or, in newer versions, a plain string.
            String t = custom.replaceAll("^\\{\"text\":\"(.*)\"}$", "$1").replaceAll("^\"(.*)\"$", "$1");
            if (!t.isBlank() && !t.startsWith("{")) return t;
        }
        return kind(e.id()).name();
    }

    /**
     * A fresh entity of type {@code id} standing at {@code (x, y, z)}, facing {@code yaw} degrees (Minecraft's: 0 south,
     * 90 west). Mobs get {@code PersistenceRequired} so they don't despawn once the structure is in a world.
     */
    public static StructureEntity create(String id, double x, double y, double z, float yaw) {
        Kind k = kind(id);
        CompoundTag nbt = new CompoundTag().putString("id", k.id());
        nbt.put("Rotation", ListTag.of(new FloatTag(yaw), new FloatTag(0)));
        if (k.living()) nbt.putBoolean("PersistenceRequired", true);
        if (k.id().equals("minecraft:villager")) {
            nbt.put("VillagerData", new CompoundTag().putString("type", "minecraft:plains").putString("profession", "minecraft:none").putInt("level", 1));
        }
        return new StructureEntity(x, y, z, nbt);
    }

    /**
     * An axis-aligned box {minX, minY, minZ, maxX, maxY, maxZ} around the entity (local coordinates), for picking and
     * the placeholder. Hanging entities (frames, paintings) are thin plates against their wall.
     */
    public static double[] box(StructureEntity e) {
        Kind k = kind(e.id());
        String path = k.id().substring(k.id().indexOf(':') + 1);
        if (path.equals("item_frame") || path.equals("glow_item_frame") || path.equals("painting")) {
            int facing = facing(e.nbt());
            double w = 0.75, h = 0.75;
            if (path.equals("painting")) {
                int[] size = paintingSize(e.nbt());
                w = size[0];
                h = size[1];
            }
            double t = 1 / 16.0;
            // Frames and paintings on a wall stand up; frames can also lie on a floor or ceiling.
            return switch (facing) {
                case 0, 1 -> new double[]{e.x() - w / 2, e.y() - t / 2, e.z() - h / 2, e.x() + w / 2, e.y() + t / 2, e.z() + h / 2};
                case 2, 3 -> new double[]{e.x() - w / 2, e.y() - h / 2, e.z() - t / 2, e.x() + w / 2, e.y() + h / 2, e.z() + t / 2};
                default -> new double[]{e.x() - t / 2, e.y() - h / 2, e.z() - w / 2, e.x() + t / 2, e.y() + h / 2, e.z() + w / 2};
            };
        }
        double scale = baby(e.nbt()) ? 0.5 : 1;
        double hw = k.width() * scale / 2, h = k.height() * scale;
        if (path.equals("armor_stand") && e.nbt().getBoolean("Small")) {
            hw /= 2;
            h /= 2;
        }
        return new double[]{e.x() - hw, e.y(), e.z() - hw, e.x() + hw, e.y() + h, e.z() + hw};
    }

    /** A baby mob (Age below zero, or IsBaby for zombies and piglins). */
    public static boolean baby(CompoundTag nbt) {
        return nbt.getInt("Age") < 0 || nbt.getBoolean("IsBaby");
    }

    /**
     * Which way a hanging entity faces (out from its wall), as Minecraft's 3D data value: 0 down, 1 up, 2 north, 3 south,
     * 4 west, 5 east. Item frames store that value in {@code Facing}; paintings store the horizontal (2D) value, 0 south,
     * 1 west, 2 north, 3 east, in {@code facing} (older versions: {@code Facing}).
     */
    public static int facing(CompoundTag nbt) {
        boolean painting = nbt.getString("id").endsWith(":painting") || nbt.getString("id").equals("painting");
        String key = nbt.contains("facing") ? "facing" : "Facing";
        if (!nbt.contains(key)) return 3;
        int v = nbt.getByte(key);
        return painting ? FROM_2D[Math.floorMod(v, 4)] : Math.clamp(v, 0, 5);
    }

    private static final int[] FROM_2D = {3, 4, 2, 5}, TO_2D = {-1, -1, 2, 0, 1, 3};
    private static final int[][] FACING_VEC = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    /** Stores a facing (3D value) the way this entity's type keeps it. */
    public static void setFacing(CompoundTag nbt, int facing3d) {
        boolean painting = nbt.getString("id").endsWith(":painting");
        if (painting) {
            nbt.remove("Facing");
            nbt.putByte("facing", Math.max(0, TO_2D[facing3d]));
        } else {
            nbt.remove("facing");
            nbt.putByte("Facing", facing3d);
        }
    }

    /** The unit vector {x, y, z} of a 3D facing value. */
    public static int[] facingVector(int facing3d) {
        return FACING_VEC[Math.clamp(facing3d, 0, 5)].clone();
    }

    public static int facingOf(int x, int y, int z) {
        for (int i = 0; i < 6; i++) if (FACING_VEC[i][0] == x && FACING_VEC[i][1] == y && FACING_VEC[i][2] == z) return i;
        return 3;
    }

    /** Whether the entity hangs on a wall (frames, paintings), so its facing, not its yaw, says which way it points. */
    public static boolean hanging(String id) {
        return id.endsWith(":painting") || id.endsWith("item_frame") || id.endsWith(":leash_knot");
    }

    /**
     * The entity turned by {@code t} (about block centres, as layers turn) and then moved by {@code (dx, dy, dz)}: its
     * position, yaw, a hanging entity's facing and the block it hangs from ({@code TileX/Y/Z}) all follow.
     */
    public static StructureEntity transform(StructureEntity e, io.blockdesigner.core.transform.Transform t, double dx, double dy, double dz) {
        double[] p = t.apply(e.x(), e.y(), e.z());
        CompoundTag nbt = e.nbt().copy();
        if (nbt.getList("Rotation").size() == 2) {
            ListTag rot = nbt.getList("Rotation");
            nbt.put("Rotation", ListTag.of(new FloatTag(t.applyYaw((float) rot.getDouble(0))), rot.get(1)));
        }
        if (hanging(e.id()) && (nbt.contains("Facing") || nbt.contains("facing"))) {
            int f = facing(nbt);
            int[] v = facingVector(f);
            BlockPos turned = t.apply(v[0], v[1], v[2]);
            setFacing(nbt, facingOf(turned.x(), turned.y(), turned.z()));
        }
        if (nbt.contains("TileX")) {
            BlockPos tile = t.apply(nbt.getInt("TileX"), nbt.getInt("TileY"), nbt.getInt("TileZ"));
            nbt.putInt("TileX", tile.x() + (int) Math.round(dx)).putInt("TileY", tile.y() + (int) Math.round(dy)).putInt("TileZ", tile.z() + (int) Math.round(dz));
        }
        return new StructureEntity(p[0] + dx, p[1] + dy, p[2] + dz, nbt);
    }

    private static final Map<String, int[]> PAINTINGS = Map.ofEntries(
            Map.entry("kebab", new int[]{1, 1}), Map.entry("aztec", new int[]{1, 1}), Map.entry("alban", new int[]{1, 1}),
            Map.entry("aztec2", new int[]{1, 1}), Map.entry("bomb", new int[]{1, 1}), Map.entry("plant", new int[]{1, 1}),
            Map.entry("wasteland", new int[]{1, 1}), Map.entry("meditative", new int[]{1, 1}),
            Map.entry("pool", new int[]{2, 1}), Map.entry("courbet", new int[]{2, 1}), Map.entry("sea", new int[]{2, 1}),
            Map.entry("sunset", new int[]{2, 1}), Map.entry("creebet", new int[]{2, 1}),
            Map.entry("wanderer", new int[]{1, 2}), Map.entry("graham", new int[]{1, 2}), Map.entry("prairie_ride", new int[]{1, 2}),
            Map.entry("match", new int[]{2, 2}), Map.entry("bust", new int[]{2, 2}), Map.entry("stage", new int[]{2, 2}),
            Map.entry("void", new int[]{2, 2}), Map.entry("skull_and_roses", new int[]{2, 2}), Map.entry("wither", new int[]{2, 2}),
            Map.entry("baroque", new int[]{2, 2}), Map.entry("humble", new int[]{2, 2}), Map.entry("earth", new int[]{2, 2}),
            Map.entry("wind", new int[]{2, 2}), Map.entry("water", new int[]{2, 2}), Map.entry("fire", new int[]{2, 2}),
            Map.entry("fighters", new int[]{4, 2}), Map.entry("changing", new int[]{4, 2}), Map.entry("finding", new int[]{4, 2}),
            Map.entry("lowmist", new int[]{4, 2}), Map.entry("passage", new int[]{4, 2}),
            Map.entry("skeleton", new int[]{4, 3}), Map.entry("donkey_kong", new int[]{4, 3}),
            Map.entry("pointer", new int[]{4, 4}), Map.entry("pigscene", new int[]{4, 4}), Map.entry("burning_skull", new int[]{4, 4}),
            Map.entry("orb", new int[]{4, 4}), Map.entry("unpacked", new int[]{4, 4}),
            Map.entry("bouquet", new int[]{3, 3}), Map.entry("cavebird", new int[]{3, 3}), Map.entry("cotan", new int[]{3, 3}),
            Map.entry("endboss", new int[]{3, 3}), Map.entry("fern", new int[]{3, 3}), Map.entry("owlemons", new int[]{3, 3}),
            Map.entry("sunflowers", new int[]{3, 3}), Map.entry("tides", new int[]{3, 3}),
            Map.entry("backyard", new int[]{3, 4}), Map.entry("pond", new int[]{3, 4}));

    /** A painting's variant id without namespace ("kebab"), from 1.21's {@code variant} or the older {@code Motive}. */
    public static String paintingVariant(CompoundTag nbt) {
        String v = nbt.getString("variant");
        if (v.isEmpty()) v = nbt.getString("Motive");
        if (v.isEmpty()) return "kebab";
        return v.substring(v.indexOf(':') + 1);
    }

    /** Vanilla painting variants, smallest first (Alt+scroll steps through them). */
    public static List<String> paintingVariants() {
        List<String> out = new java.util.ArrayList<>(PAINTINGS.keySet());
        out.sort(java.util.Comparator.comparingInt((String v) -> PAINTINGS.get(v)[0] * PAINTINGS.get(v)[1]).thenComparing(v -> v));
        return out;
    }

    /**
     * Where a hanging entity's centre is for the block it hangs in ({@code tile}, the cell in front of the wall) and the
     * way it faces: pressed against the wall, and for paintings of even width or height shifted half a block (left as
     * seen from the front, and up), as Minecraft's Painting.calculateBoundingBox does.
     */
    public static double[] hangingPosition(int tileX, int tileY, int tileZ, int facing3d, CompoundTag nbt) {
        int[] f = facingVector(facing3d);
        double x = tileX + 0.5 - f[0] * 0.46875, y = tileY + 0.5 - f[1] * 0.46875, z = tileZ + 0.5 - f[2] * 0.46875;
        if (nbt.getString("id").endsWith(":painting") && f[1] == 0) {
            int[] size = paintingSize(nbt);
            // Counter-clockwise of the facing (south → east).
            double cx = f[2], cz = -f[0];
            if (size[0] % 2 == 0) {
                x += cx * 0.5;
                z += cz * 0.5;
            }
            if (size[1] % 2 == 0) y += 0.5;
        }
        return new double[]{x, y, z};
    }

    /** Width and height in blocks of a painting (1×1 when the variant is unknown). */
    public static int[] paintingSize(CompoundTag nbt) {
        return PAINTINGS.getOrDefault(paintingVariant(nbt), new int[]{1, 1});
    }
}
