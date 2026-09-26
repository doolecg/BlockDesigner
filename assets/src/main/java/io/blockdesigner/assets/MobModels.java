package io.blockdesigner.assets;

import io.blockdesigner.assets.EntityModels.Cube;
import io.blockdesigner.assets.EntityModels.Mat;
import io.blockdesigner.assets.EntityModels.Part;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.core.model.EntityTypes;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Models for entities in a structure (mobs, armour stands, paintings, item frames), in the entity's own frame: the
 * origin is where it stands. Common mobs get Minecraft's own models (the cuboids, texture offsets and rest poses of
 * PigModel, CowModel, VillagerModel…) with the game's textures; everything else, and anything whose texture this
 * game version lacks, is drawn as its hitbox in the spawn egg's colour.
 */
final class MobModels {
    private MobModels() {
    }

    static final String WHITE = TextureAtlas.WHITE;
    private static final float PI = (float) Math.PI;
    private static final List<String> VILLAGER_TYPES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");
    private static final List<String> PROFESSIONS = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    private static final List<String> WOLVES = List.of("ashen", "black", "chestnut", "rusty", "snowy", "spotted", "striped", "woods");

    /** Entity textures to stitch into the atlas; names differ between versions, so every known spelling is listed. */
    static List<String> textures() {
        List<String> out = new ArrayList<>(List.of(
                "entity/pig/pig", "entity/pig/temperate_pig",
                "entity/cow/cow", "entity/cow/temperate_cow", "entity/cow/red_mooshroom", "entity/cow/brown_mooshroom",
                "entity/sheep/sheep", "entity/sheep/sheep_fur", "entity/sheep/sheep_wool",
                "entity/chicken", "entity/chicken/temperate_chicken",
                "entity/wolf/wolf", "entity/villager/villager", "entity/wandering_trader",
                "entity/zombie/zombie", "entity/zombie/husk", "entity/zombie/drowned",
                "entity/skeleton/skeleton", "entity/skeleton/stray", "entity/skeleton/stray_overlay", "entity/skeleton/wither_skeleton",
                "entity/creeper/creeper", "entity/armorstand/wood", "entity/iron_golem/iron_golem",
                "painting/back", "block/item_frame", "block/glow_item_frame"));
        for (String t : VILLAGER_TYPES) out.add("entity/villager/type/" + t);
        for (String p : PROFESSIONS) out.add("entity/villager/profession/" + p);
        for (String w : WOLVES) out.add("entity/wolf/wolf_" + w);
        return out.stream().map(t -> "minecraft:" + t).toList();
    }

    /** One textured pass over a model: the base skin, or an overlay (wool, a villager's biome clothes). */
    private record Skin(List<Part> parts, TextureAtlas.Sprite sprite, int texWidth, int tint) {
    }

    static List<BakedQuad> bake(StructureEntity e, TextureAtlas atlas) {
        EntityTypes.Kind k = EntityTypes.kind(e.id());
        String path = k.id().startsWith("minecraft:") ? k.id().substring(10) : "";
        CompoundTag nbt = e.nbt();
        List<BakedQuad> out = new ArrayList<>();
        if (path.equals("painting")) {
            painting(e, atlas, out);
            return out;
        }
        if (path.equals("item_frame") || path.equals("glow_item_frame")) {
            TextureAtlas.Sprite s = atlas.sprite("minecraft:block/" + path);
            box(e, s.missing() ? atlas.sprite(WHITE) : s, s.missing() ? k.color() : 0xFFFFFF, out);
            return out;
        }
        List<Skin> skins = new ArrayList<>();
        float scale = 1;
        switch (path) {
            case "pig" -> skin(skins, pig(), atlas, 64, -1, "entity/pig/pig", "entity/pig/temperate_pig");
            case "cow" -> skin(skins, cow(), atlas, 64, -1, "entity/cow/cow", "entity/cow/temperate_cow");
            case "mooshroom" -> skin(skins, cow(), atlas, 64, -1,
                    "entity/cow/" + ("brown".equals(nbt.getString("Type")) ? "brown" : "red") + "_mooshroom");
            case "sheep" -> {
                skin(skins, sheep(), atlas, 64, -1, "entity/sheep/sheep");
                if (!skins.isEmpty() && !nbt.getBoolean("Sheared")) {
                    skin(skins, sheepFur(), atlas, 64, WOOL[Math.floorMod(nbt.getByte("Color"), 16)], "entity/sheep/sheep_fur", "entity/sheep/sheep_wool");
                }
            }
            case "chicken" -> skin(skins, chicken(), atlas, 64, -1, "entity/chicken", "entity/chicken/temperate_chicken");
            case "wolf" -> {
                String v = nbt.getString("variant");
                v = v.substring(v.indexOf(':') + 1);
                skin(skins, wolf(), atlas, 64, -1, WOLVES.contains(v) ? "entity/wolf/wolf_" + v : "entity/wolf/wolf", "entity/wolf/wolf");
            }
            case "villager" -> {
                scale = 0.9375f;
                skin(skins, villager(), atlas, 64, -1, "entity/villager/villager");
                CompoundTag data = nbt.getCompound("VillagerData");
                String type = strip(data.getString("type")), prof = strip(data.getString("profession"));
                // Each layer sits a hair outside the last so they don't flicker against each other.
                if (!skins.isEmpty() && VILLAGER_TYPES.contains(type)) skin(skins, grown(villager(), 0.02f), atlas, 64, -1, "entity/villager/type/" + type);
                if (!skins.isEmpty() && PROFESSIONS.contains(prof)) skin(skins, grown(villager(), 0.04f), atlas, 64, -1, "entity/villager/profession/" + prof);
            }
            case "wandering_trader" -> {
                scale = 0.9375f;
                skin(skins, villager(), atlas, 64, -1, "entity/wandering_trader");
            }
            case "zombie", "drowned" -> skin(skins, humanoid(true), atlas, 64, -1, "entity/zombie/" + path);
            case "husk" -> {
                scale = 1.0625f;
                skin(skins, humanoid(true), atlas, 64, -1, "entity/zombie/husk");
            }
            case "skeleton" -> skin(skins, skeleton(0), atlas, 64, -1, "entity/skeleton/skeleton");
            case "stray" -> {
                skin(skins, skeleton(0), atlas, 64, -1, "entity/skeleton/stray");
                if (!skins.isEmpty()) skin(skins, skeleton(0.25f), atlas, 64, -1, "entity/skeleton/stray_overlay");
            }
            case "wither_skeleton" -> {
                scale = 1.2f;
                skin(skins, skeleton(0), atlas, 64, -1, "entity/skeleton/wither_skeleton");
            }
            case "creeper" -> skin(skins, creeper(), atlas, 64, -1, "entity/creeper/creeper");
            case "armor_stand" -> {
                if (nbt.getBoolean("Small")) scale = 0.5f;
                skin(skins, armorStand(nbt), atlas, 64, -1, "entity/armorstand/wood");
            }
            case "iron_golem" -> skin(skins, ironGolem(), atlas, 128, -1, "entity/iron_golem/iron_golem");
            default -> {
            }
        }
        if (skins.isEmpty()) {
            box(e, atlas.sprite(WHITE), k.color(), out);
            return out;
        }
        if (EntityTypes.baby(nbt)) scale *= 0.5f;
        // LivingEntityRenderer: turn to the body's yaw, flip into model space (y down), scale, and stand on the ground.
        Mat root = Mat.identity().rotateY(Math.toRadians(180 - e.yaw())).scale(-scale, -scale, scale).translate(0, -1.501, 0);
        for (Skin s : skins) {
            int th = Math.max(1, Math.round((float) s.texWidth * s.sprite.height() / s.sprite.width()));
            List<BakedQuad> quads = new ArrayList<>();
            for (Part p : s.parts) EntityModels.emit(p, root, s.texWidth, th, s.sprite, quads);
            for (BakedQuad q : quads) {
                out.add(s.tint == -1 ? q : new BakedQuad(q.pos(), q.uv(), q.normal(), q.face(), q.cull(), s.tint, q.layer(), q.shade(), q.sprite()));
            }
        }
        return out;
    }

    private static String strip(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Adds a skin with the first texture this version has; adds nothing when none exists. */
    private static void skin(List<Skin> skins, List<Part> parts, TextureAtlas atlas, int texWidth, int tint, String... textures) {
        for (String t : textures) {
            TextureAtlas.Sprite s = atlas.sprite("minecraft:" + t);
            if (!s.missing()) {
                skins.add(new Skin(parts, s, texWidth, tint));
                return;
            }
        }
    }

    /** Sheep wool colours (DyeColor.getTextureDiffuseColor), by the {@code Color} byte. */
    private static final int[] WOOL = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    // ---- Minecraft's model definitions (LayerDefinitions as of 1.21) -------------------------------------------

    private static List<Part> legs(Cube right, Cube left, float x, float y, float hindZ, float frontZ) {
        return List.of(Part.of(right).at(-x, y, hindZ), Part.of(left).at(x, y, hindZ), Part.of(right).at(-x, y, frontZ), Part.of(left).at(x, y, frontZ));
    }

    private static List<Part> with(List<Part> legs, Part... more) {
        List<Part> out = new ArrayList<>(List.of(more));
        out.addAll(legs);
        return out;
    }

    /** PigModel: QuadrupedModel with 6-pixel legs and a snout. */
    private static List<Part> pig() {
        Cube leg = Cube.of(0, 16, -2, 0, -2, 4, 6, 4);
        return with(legs(leg, leg, 3, 18, 7, -5),
                Part.of(Cube.of(0, 0, -4, -4, -8, 8, 8, 8), Cube.of(16, 16, -2, 0, -9, 4, 3, 1)).at(0, 12, -6),
                Part.of(Cube.of(28, 8, -5, -10, -7, 10, 16, 8)).at(0, 11, 2).rotated(PI / 2, 0, 0));
    }

    /** CowModel: head with horns, body with udder, mirrored left legs. */
    private static List<Part> cow() {
        Cube leg = Cube.of(0, 16, -2, 0, -2, 4, 12, 4), mirrored = new Cube(0, 16, -2, 0, -2, 4, 12, 4, 0, true);
        return with(legs(leg, mirrored, 4, 12, 7, -5),
                Part.of(Cube.of(0, 0, -4, -4, -6, 8, 8, 6), Cube.of(22, 0, -5, -5, -4, 1, 3, 1), Cube.of(22, 0, 4, -5, -4, 1, 3, 1)).at(0, 4, -8),
                Part.of(Cube.of(18, 4, -6, -10, -7, 12, 18, 10), Cube.of(52, 0, -2, 2, -8, 4, 6, 1)).at(0, 5, 2).rotated(PI / 2, 0, 0));
    }

    private static List<Part> sheep() {
        Cube leg = Cube.of(0, 16, -2, 0, -2, 4, 12, 4);
        return with(legs(leg, leg, 3, 12, 7, -5),
                Part.of(Cube.of(0, 0, -3, -4, -6, 6, 6, 8)).at(0, 6, -8),
                Part.of(Cube.of(28, 8, -4, -10, -7, 8, 16, 6)).at(0, 5, 2).rotated(PI / 2, 0, 0));
    }

    /** SheepFurModel: the wool, puffed out around the sheep. */
    private static List<Part> sheepFur() {
        Cube leg = new Cube(0, 16, -2, 0, -2, 4, 6, 4, 0.5f, false);
        return with(legs(leg, leg, 3, 12, 7, -5),
                Part.of(new Cube(0, 0, -3, -4, -4, 6, 6, 6, 0.6f, false)).at(0, 6, -8),
                Part.of(new Cube(28, 8, -4, -10, -7, 8, 16, 6, 1.75f, false)).at(0, 5, 2).rotated(PI / 2, 0, 0));
    }

    private static List<Part> chicken() {
        Cube leg = Cube.of(26, 0, -1, 0, -3, 3, 5, 3);
        return List.of(
                Part.of(Cube.of(0, 0, -2, -6, -2, 4, 6, 3)).at(0, 15, -4),
                Part.of(Cube.of(14, 0, -2, -4, -4, 4, 2, 2)).at(0, 15, -4),
                Part.of(Cube.of(14, 4, -1, -2, -3, 2, 2, 2)).at(0, 15, -4),
                Part.of(Cube.of(0, 9, -3, -4, -3, 6, 8, 6)).at(0, 16, 0).rotated(PI / 2, 0, 0),
                Part.of(leg).at(-2, 19, 1), Part.of(leg).at(1, 19, 1),
                Part.of(Cube.of(24, 13, 0, 0, -3, 1, 4, 6)).at(-4, 13, 0),
                Part.of(Cube.of(24, 13, -1, 0, -3, 1, 4, 6)).at(4, 13, 0));
    }

    private static List<Part> wolf() {
        Cube leg = Cube.of(0, 18, 0, 0, -1, 2, 8, 2);
        return List.of(
                Part.of(Cube.of(0, 0, -2, -3, -2, 6, 6, 4), Cube.of(16, 14, -2, -5, 0, 2, 2, 1), Cube.of(16, 14, 2, -5, 0, 2, 2, 1),
                        Cube.of(0, 10, -0.5f, -0.001f, -5, 3, 3, 4)).at(-1, 13.5f, -7),
                Part.of(Cube.of(18, 14, -3, -2, -3, 6, 9, 6)).at(0, 14, 2).rotated(PI / 2, 0, 0),
                Part.of(Cube.of(21, 0, -3, -3, -3, 8, 6, 7)).at(-1, 14, -3).rotated(PI / 2, 0, 0),
                Part.of(leg).at(-2.5f, 16, 7), Part.of(leg).at(0.5f, 16, 7), Part.of(leg).at(-2.5f, 16, -4), Part.of(leg).at(0.5f, 16, -4),
                Part.of(Cube.of(9, 18, 0, 0, -1, 2, 8, 2)).at(-1, 12, 8).rotated(PI / 5, 0, 0));
    }

    /** VillagerModel (also the wandering trader): robe, folded arms, the hat for professions that wear one. */
    private static List<Part> villager() {
        Part hat = Part.of(new Cube(32, 0, -4, -10, -4, 8, 10, 8, 0.51f, false))
                .with(Part.of(Cube.of(30, 47, -8, -8, -6, 16, 16, 1)).rotated(-PI / 2, 0, 0));
        Part head = Part.of(Cube.of(0, 0, -4, -10, -4, 8, 10, 8)).with(hat, Part.of(Cube.of(24, 0, -1, -1, -6, 2, 4, 2)).at(0, -2, 0));
        Part body = Part.of(Cube.of(16, 20, -4, 0, -3, 8, 12, 6)).with(Part.of(new Cube(0, 38, -4, 0, -3, 8, 20, 6, 0.5f, false)));
        Part arms = Part.of(Cube.of(44, 22, -8, 0, -2, 4, 8, 4), new Cube(44, 22, 4, 0, -2, 4, 8, 4, 0, true), Cube.of(40, 38, -4, 4, -2, 8, 4, 4))
                .at(0, 3, -1).rotated(-0.75f, 0, 0);
        return List.of(head, body, arms,
                Part.of(Cube.of(0, 22, -2, 0, -2, 4, 12, 4)).at(-2, 12, 0),
                Part.of(new Cube(0, 22, -2, 0, -2, 4, 12, 4, 0, true)).at(2, 12, 0));
    }

    /** HumanoidModel (zombies hold their arms out in front). */
    private static List<Part> humanoid(boolean armsOut) {
        float arm = armsOut ? -PI / 2.25f : 0;
        return List.of(
                Part.of(Cube.of(0, 0, -4, -8, -4, 8, 8, 8), new Cube(32, 0, -4, -8, -4, 8, 8, 8, 0.5f, false)),
                Part.of(Cube.of(16, 16, -4, 0, -2, 8, 12, 4)),
                Part.of(Cube.of(40, 16, -3, -2, -2, 4, 12, 4)).at(-5, 2, 0).rotated(arm, 0, 0),
                Part.of(new Cube(40, 16, -1, -2, -2, 4, 12, 4, 0, true)).at(5, 2, 0).rotated(arm, 0, 0),
                Part.of(Cube.of(0, 16, -2, 0, -2, 4, 12, 4)).at(-1.9f, 12, 0),
                Part.of(new Cube(0, 16, -2, 0, -2, 4, 12, 4, 0, true)).at(1.9f, 12, 0));
    }

    /** SkeletonModel: the humanoid with thin limbs; {@code grow} puffs it out for the stray's clothes. */
    private static List<Part> skeleton(float grow) {
        return List.of(
                Part.of(new Cube(0, 0, -4, -8, -4, 8, 8, 8, grow, false), new Cube(32, 0, -4, -8, -4, 8, 8, 8, grow + 0.5f, false)),
                Part.of(new Cube(16, 16, -4, 0, -2, 8, 12, 4, grow, false)),
                Part.of(new Cube(40, 16, -1, -2, -1, 2, 12, 2, grow, false)).at(-5, 2, 0),
                Part.of(new Cube(40, 16, -1, -2, -1, 2, 12, 2, grow, true)).at(5, 2, 0),
                Part.of(new Cube(0, 16, -1, 0, -1, 2, 12, 2, grow, false)).at(-2, 12, 0),
                Part.of(new Cube(0, 16, -1, 0, -1, 2, 12, 2, grow, true)).at(2, 12, 0));
    }

    private static List<Part> creeper() {
        Cube leg = Cube.of(0, 16, -2, 0, -2, 4, 6, 4);
        return List.of(
                Part.of(Cube.of(0, 0, -4, -8, -4, 8, 8, 8)).at(0, 6, 0),
                Part.of(Cube.of(16, 16, -4, 0, -2, 8, 12, 4)).at(0, 6, 0),
                Part.of(leg).at(-2, 18, 4), Part.of(leg).at(2, 18, 4), Part.of(leg).at(-2, 18, -4), Part.of(leg).at(2, 18, -4));
    }

    /** ArmorStandModel, posed from the stand's {@code Pose}; arms only when {@code ShowArms} is set. */
    private static List<Part> armorStand(CompoundTag nbt) {
        CompoundTag pose = nbt.getCompound("Pose");
        List<Part> out = new ArrayList<>();
        out.add(posed(Part.of(Cube.of(0, 0, -1, -7, -1, 2, 7, 2)).at(0, 1, 0), pose, "Head", 0, 0, 0));
        out.add(posed(Part.of(Cube.of(0, 26, -6, 0, -1.5f, 12, 3, 3), Cube.of(16, 0, -3, 3, -1, 2, 7, 2), Cube.of(48, 16, 1, 3, -1, 2, 7, 2),
                Cube.of(0, 48, -4, 10, -1, 8, 2, 2)), pose, "Body", 0, 0, 0));
        if (nbt.getBoolean("ShowArms")) {
            out.add(posed(Part.of(Cube.of(24, 0, -2, -2, -1, 2, 12, 2)).at(-5, 2, 0), pose, "RightArm", -15, 0, 10));
            out.add(posed(Part.of(new Cube(32, 16, 0, -2, -1, 2, 12, 2, 0, true)).at(5, 2, 0), pose, "LeftArm", -10, 0, -10));
        }
        out.add(posed(Part.of(Cube.of(8, 0, -1, 0, -1, 2, 11, 2)).at(-1.9f, 12, 0), pose, "RightLeg", 1, 0, 1));
        out.add(posed(Part.of(new Cube(40, 16, -1, 0, -1, 2, 11, 2, 0, true)).at(1.9f, 12, 0), pose, "LeftLeg", -1, 0, -1));
        if (!nbt.getBoolean("NoBasePlate")) out.add(Part.of(Cube.of(0, 32, -6, 11, -6, 12, 1, 12)).at(0, 12, 0));
        return out;
    }

    private static Part posed(Part p, CompoundTag pose, String key, float dx, float dy, float dz) {
        ListTag r = pose.getList(key);
        float x = dx, y = dy, z = dz;
        if (r.size() == 3) {
            x = (float) r.getDouble(0);
            y = (float) r.getDouble(1);
            z = (float) r.getDouble(2);
        }
        return p.rotated((float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z));
    }

    private static List<Part> ironGolem() {
        return List.of(
                Part.of(Cube.of(0, 0, -4, -12, -5.5f, 8, 10, 8), Cube.of(24, 0, -1, -5, -7.5f, 2, 4, 2)).at(0, -7, -2),
                Part.of(Cube.of(0, 40, -9, -2, -6, 18, 12, 11), new Cube(0, 70, -4.5f, 10, -3, 9, 5, 6, 0.5f, false)).at(0, -7, 0),
                Part.of(Cube.of(60, 21, -13, -2.5f, -3, 4, 30, 6)).at(0, -7, 0),
                Part.of(Cube.of(60, 58, 9, -2.5f, -3, 4, 30, 6)).at(0, -7, 0),
                Part.of(Cube.of(37, 0, -3.5f, -3, -3, 6, 16, 5)).at(-4, 11, 0),
                Part.of(new Cube(60, 0, -3.5f, -3, -3, 6, 16, 5, 0, true)).at(5, 11, 0));
    }

    /** The same parts with every cube grown a little (for overlay skins). */
    private static List<Part> grown(List<Part> parts, float by) {
        return parts.stream().map(p -> grow(p, by)).toList();
    }

    private static Part grow(Part p, float by) {
        List<Cube> cubes = p.cubes().stream().map(c -> new Cube(c.u(), c.v(), c.x(), c.y(), c.z(), c.w(), c.h(), c.d(), c.grow() + by, c.mirror())).toList();
        return new Part(cubes, p.px(), p.py(), p.pz(), p.xRot(), p.yRot(), p.zRot(), p.children().stream().map(c -> grow(c, by)).toList());
    }

    // ---- flat and boxy things ---------------------------------------------------------------------------------

    /** The entity's hitbox as a box: the stand-in for mobs without a model. */
    private static void box(StructureEntity e, TextureAtlas.Sprite sprite, int tint, List<BakedQuad> out) {
        double[] b = EntityTypes.box(e);
        float x0 = (float) (b[0] - e.x()), y0 = (float) (b[1] - e.y()), z0 = (float) (b[2] - e.z());
        float x1 = (float) (b[3] - e.x()), y1 = (float) (b[4] - e.y()), z1 = (float) (b[5] - e.z());
        cuboid(x0, y0, z0, x1, y1, z1, i -> sprite, i -> tint, out);
    }

    /**
     * A painting: the picture on the side facing out from the wall, the back texture everywhere else. Paintings with
     * no picture in this version (or none at all) get the back on every side.
     */
    private static void painting(StructureEntity e, TextureAtlas atlas, List<BakedQuad> out) {
        double[] b = EntityTypes.box(e);
        int facing = EntityTypes.facing(e.nbt());
        TextureAtlas.Sprite back = atlas.sprite("minecraft:painting/back"), front = atlas.sprite("minecraft:painting/" + EntityTypes.paintingVariant(e.nbt()));
        if (back.missing()) back = atlas.sprite(WHITE);
        if (front.missing()) front = back;
        Dir out2 = switch (facing) {
            case 2 -> Dir.NORTH;
            case 4 -> Dir.WEST;
            case 5 -> Dir.EAST;
            default -> Dir.SOUTH;
        };
        TextureAtlas.Sprite f = front, bk = back;
        cuboid((float) (b[0] - e.x()), (float) (b[1] - e.y()), (float) (b[2] - e.z()), (float) (b[3] - e.x()), (float) (b[4] - e.y()), (float) (b[5] - e.z()),
                d -> d == out2 ? f : bk, d -> 0xFFFFFF, out);
    }

    /**
     * Six faces of a box, each wound to face out. The texture's left edge is on the viewer's left and its top at the
     * top, as on a painting seen from the front.
     */
    private static void cuboid(float x0, float y0, float z0, float x1, float y1, float z1, java.util.function.Function<Dir, TextureAtlas.Sprite> sprite,
                               java.util.function.ToIntFunction<Dir> tint, List<BakedQuad> out) {
        // Corners in order: bottom-left, bottom-right, top-right, top-left, seen from outside.
        face(out, Dir.SOUTH, sprite, tint, new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1});
        face(out, Dir.NORTH, sprite, tint, new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0});
        face(out, Dir.EAST, sprite, tint, new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1});
        face(out, Dir.WEST, sprite, tint, new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0});
        face(out, Dir.UP, sprite, tint, new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0});
        face(out, Dir.DOWN, sprite, tint, new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1});
    }

    private static void face(List<BakedQuad> out, Dir d, java.util.function.Function<Dir, TextureAtlas.Sprite> sprites,
                             java.util.function.ToIntFunction<Dir> tints, float[] pos) {
        TextureAtlas.Sprite s = sprites.apply(d);
        // Texture (0,16) bottom-left … (16,0) top-left, in the sprite's 0..16 space.
        float[] uv = {s.u(0), s.v(16), s.u(16), s.v(16), s.u(16), s.v(0), s.u(0), s.v(0)};
        out.add(new BakedQuad(pos, uv, new float[]{d.dx, d.dy, d.dz}, d, null, tints.applyAsInt(d), s.layer(), true, s));
    }

    /** Whether an entity of this id gets a real model when its textures are present (the rest are boxes). */
    static final Set<String> MODELLED = Set.of("pig", "cow", "mooshroom", "sheep", "chicken", "wolf", "villager", "wandering_trader",
            "zombie", "drowned", "husk", "skeleton", "stray", "wither_skeleton", "creeper", "armor_stand", "iron_golem", "painting",
            "item_frame", "glow_item_frame");
}
