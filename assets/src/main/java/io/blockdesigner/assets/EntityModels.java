package io.blockdesigner.assets;

import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Real models for the blocks Minecraft draws with code instead of JSON models: chests, beds, signs (standing, wall,
 * hanging) and mob heads. The cuboids, texture offsets, part poses and render transforms follow the game's own model
 * and renderer code; the textures are the game's entity textures (entity/chest/normal.png, entity/bed/red.png…).
 * Cubes are built exactly like Minecraft's ModelPart.Cube (same corner order and UV layout), so the textures line up.
 */
final class EntityModels {
    private EntityModels() {
    }

    static final Set<String> WOODS = Set.of("oak", "spruce", "birch", "acacia", "jungle", "dark_oak", "mangrove", "cherry", "bamboo",
            "crimson", "warped", "pale_oak");

    /** Entity textures to stitch into the atlas. */
    static List<String> textures() {
        List<String> out = new ArrayList<>();
        for (String c : List.of("normal", "normal_left", "normal_right", "trapped", "trapped_left", "trapped_right", "ender")) out.add("minecraft:entity/chest/" + c);
        for (String c : FallbackModels.COLORS) out.add("minecraft:entity/bed/" + c);
        for (String w : WOODS) {
            out.add("minecraft:entity/signs/" + w);
            out.add("minecraft:entity/signs/hanging/" + w);
        }
        out.add("minecraft:entity/shulker/shulker");
        for (String c : FallbackModels.COLORS) out.add("minecraft:entity/shulker/shulker_" + c);
        out.add("minecraft:entity/banner_base");
        out.add("minecraft:entity/banner/base");
        out.addAll(List.of("minecraft:entity/skeleton/skeleton", "minecraft:entity/skeleton/wither_skeleton", "minecraft:entity/zombie/zombie",
                "minecraft:entity/creeper/creeper", "minecraft:entity/player/wide/steve", "minecraft:entity/piglin/piglin",
                "minecraft:entity/enderdragon/dragon"));
        return out;
    }

    // ---- model parts, like Minecraft's ModelPart ---------------------------------------------------------------

    /** One cuboid: texture offset, origin and size in pixels, inflation, mirrored UVs. */
    record Cube(int u, int v, float x, float y, float z, float w, float h, float d, float grow, boolean mirror) {
        static Cube of(int u, int v, float x, float y, float z, float w, float h, float d) {
            return new Cube(u, v, x, y, z, w, h, d, 0, false);
        }
    }

    /** A part: its cubes, pose (pivot in pixels, rotations in radians) and children. */
    record Part(List<Cube> cubes, float px, float py, float pz, float xRot, float yRot, float zRot, List<Part> children) {
        static Part of(Cube... cubes) {
            return new Part(List.of(cubes), 0, 0, 0, 0, 0, 0, List.of());
        }

        Part at(float x, float y, float z) {
            return new Part(cubes, x, y, z, xRot, yRot, zRot, children);
        }

        Part rotated(float x, float y, float z) {
            return new Part(cubes, px, py, pz, x, y, z, children);
        }

        Part with(Part... kids) {
            return new Part(cubes, px, py, pz, xRot, yRot, zRot, List.of(kids));
        }
    }

    static Optional<BakedModel> bake(BlockState state, TextureAtlas atlas) {
        return bake(state, atlas, 0);
    }

    /**
     * @param open how far a chest's lid or a shulker box's shell is open, 0 (shut) to 1, as the game animates it when
     *             a player uses one
     */
    static Optional<BakedModel> bake(BlockState state, TextureAtlas atlas, float open) {
        String p = state.path();
        String ns = state.name().substring(0, state.name().indexOf(':'));
        Mat root;
        List<Part> parts;
        String tex;
        int tw = 64, th = 64;
        if (p.equals("chest") || p.equals("trapped_chest") || p.equals("ender_chest")) {
            String type = String.valueOf(state.get("type"));
            String base = p.equals("ender_chest") ? "ender" : p.equals("trapped_chest") ? "trapped" : "normal";
            boolean left = type.equals("left") && !base.equals("ender"), right = type.equals("right") && !base.equals("ender");
            tex = "minecraft:entity/chest/" + base + (left ? "_left" : right ? "_right" : "");
            parts = chest(left, right);
            if (open > 0) {
                // ChestRenderer: the lid and lock swing up about the back edge, eased like the game's lid.
                float a = -(1 - (1 - open) * (1 - open) * (1 - open)) * (float) Math.PI / 2;
                parts = List.of(parts.get(0), parts.get(1).rotated(a, 0, 0), parts.get(2).rotated(a, 0, 0));
            }
            root = Mat.identity().translate(0.5, 0.5, 0.5).rotateY(Math.toRadians(-yRot(state.get("facing")))).translate(-0.5, -0.5, -0.5);
        } else if (p.endsWith("_bed")) {
            String color = p.substring(0, p.length() - 4);
            if (!FallbackModels.COLORS.contains(color)) return Optional.empty();
            tex = "minecraft:entity/bed/" + color;
            parts = bed("head".equals(state.get("part")));
            root = Mat.identity().translate(0, 0.5625, 0).rotateX(Math.PI / 2).translate(0.5, 0.5, 0.5)
                    .rotateZ(Math.toRadians(180 + yRot(state.get("facing")))).translate(-0.5, -0.5, -0.5);
        } else if (p.endsWith("_hanging_sign")) {
            boolean wall = p.endsWith("_wall_hanging_sign");
            String wood = p.substring(0, p.length() - (wall ? "_wall_hanging_sign" : "_hanging_sign").length());
            // Modded woods keep their board texture in their own namespace (biomesoplenty:entity/signs/hanging/fir).
            tex = ns + ":entity/signs/hanging/" + wood;
            th = 32;
            boolean attached = "true".equals(state.get("attached"));
            parts = hangingSign(wall, !wall && attached);
            double yRot = wall ? -yRot(state.get("facing")) : -rotation(state) * 22.5;
            root = Mat.identity().translate(0.5, 0.9375, 0.5).rotateY(Math.toRadians(yRot)).translate(0, -0.3125, 0).scale(1, -1, -1);
        } else if (p.endsWith("_sign")) {
            boolean wall = p.endsWith("_wall_sign");
            String wood = p.substring(0, p.length() - (wall ? "_wall_sign" : "_sign").length());
            tex = ns + ":entity/signs/" + wood;
            th = 32;
            parts = sign(!wall);
            double yRot = wall ? -yRot(state.get("facing")) : -rotation(state) * 22.5;
            double f = 2 / 3.0;
            root = Mat.identity().translate(0.5, 0.75 * f, 0.5).rotateY(Math.toRadians(yRot));
            if (wall) root = root.translate(0, -0.3125, -0.4375);
            root = root.scale(f, -f, -f);
        } else if (p.equals("shulker_box") || p.endsWith("_shulker_box")) {
            String color = p.equals("shulker_box") ? null : p.substring(0, p.length() - "_shulker_box".length());
            if (color != null && !FallbackModels.COLORS.contains(color)) return Optional.empty();
            tex = "minecraft:entity/shulker/shulker" + (color == null ? "" : "_" + color);
            // ShulkerModel's base and lid (closed), ShulkerBoxRenderer's transform: turned to face the box's facing.
            parts = List.of(Part.of(Cube.of(0, 28, -8, -8, -8, 16, 8, 16)).at(0, 24, 0), Part.of(Cube.of(0, 0, -8, -16, -8, 16, 12, 16)).at(0, 24, 0));
            if (open > 0) {
                // ShulkerModel's lid rises half a block and turns three quarters of a circle as it opens.
                parts = List.of(parts.get(0), parts.get(1).at(0, 24 - open * 8, 0).rotated(0, (float) Math.toRadians(270 * open), 0));
            }
            root = Mat.identity().translate(0.5, 0.5, 0.5).scale(0.9995, 0.9995, 0.9995);
            root = switch (String.valueOf(state.get("facing"))) {
                case "down" -> root.rotateX(Math.PI);
                case "north" -> root.rotateX(Math.PI / 2).rotateZ(Math.PI);
                case "south" -> root.rotateX(Math.PI / 2);
                case "west" -> root.rotateX(Math.PI / 2).rotateZ(Math.PI / 2);
                case "east" -> root.rotateX(Math.PI / 2).rotateZ(-Math.PI / 2);
                default -> root;
            };
            root = root.scale(1, -1, -1).translate(0, -1, 0);
        } else if (p.endsWith("_banner")) {
            return banner(state, p, atlas, List.of());
        } else if ((p.endsWith("_head") || p.endsWith("_skull")) && !p.equals("piston_head")) {
            boolean wall = p.endsWith("_wall_head") || p.endsWith("_wall_skull");
            String mob = p.replace("_wall_head", "").replace("_wall_skull", "").replace("_head", "").replace("_skull", "");
            double deg;
            if (wall) {
                String f = String.valueOf(state.get("facing"));
                int sx = f.equals("east") ? 1 : f.equals("west") ? -1 : 0, sz = f.equals("south") ? 1 : f.equals("north") ? -1 : 0;
                root = Mat.identity().translate(0.5 - sx * 0.25, 0.25, 0.5 - sz * 0.25);
                deg = yRot(opposite(f));
            } else {
                root = Mat.identity().translate(0.5, 0, 0.5);
                deg = rotation(state) * 22.5;
            }
            root = root.scale(-1, -1, 1);
            float yaw = (float) Math.toRadians(deg);
            switch (mob) {
                case "skeleton", "wither_skeleton", "creeper" -> {
                    tex = mob.equals("creeper") ? "minecraft:entity/creeper/creeper" : "minecraft:entity/skeleton/" + mob;
                    th = 32;
                    parts = List.of(Part.of(Cube.of(0, 0, -4, -8, -4, 8, 8, 8)).rotated(0, yaw, 0));
                }
                case "zombie", "player" -> {
                    tex = mob.equals("zombie") ? "minecraft:entity/zombie/zombie" : "minecraft:entity/player/wide/steve";
                    parts = List.of(Part.of(Cube.of(0, 0, -4, -8, -4, 8, 8, 8), new Cube(32, 0, -4, -8, -4, 8, 8, 8, 0.25f, false)).rotated(0, yaw, 0));
                }
                case "piglin" -> {
                    tex = "minecraft:entity/piglin/piglin";
                    parts = List.of(piglinHead().rotated(0, yaw, 0));
                }
                case "dragon" -> {
                    tex = "minecraft:entity/enderdragon/dragon";
                    tw = th = 256;
                    root = root.translate(0, -0.374375, 0).scale(0.75, 0.75, 0.75);
                    parts = List.of(dragonHead().rotated(0, yaw, 0));
                }
                default -> {
                    return Optional.empty();
                }
            }
        } else if (p.equals("decorated_pot")) {
            return decoratedPot(state, List.of(), atlas);
        } else {
            return Optional.empty();
        }
        TextureAtlas.Sprite sprite = atlas.sprite(tex);
        if (sprite.missing()) return Optional.empty();
        List<BakedQuad> quads = new ArrayList<>();
        for (Part part : parts) emit(part, root, tw, th, sprite, quads);
        return Optional.of(new BakedModel(List.copyOf(quads), new boolean[6], false, false));
    }

    /** Minecraft's dye colours (DyeColor's texture colour), for banner cloth. */
    private static final java.util.Map<String, Integer> DYES = java.util.Map.ofEntries(
            java.util.Map.entry("white", 0xF9FFFE), java.util.Map.entry("orange", 0xF9801D), java.util.Map.entry("magenta", 0xC74EBD),
            java.util.Map.entry("light_blue", 0x3AB3DA), java.util.Map.entry("yellow", 0xFED83D), java.util.Map.entry("lime", 0x80C71F),
            java.util.Map.entry("pink", 0xF38BAA), java.util.Map.entry("gray", 0x474F52), java.util.Map.entry("light_gray", 0x9D9D97),
            java.util.Map.entry("cyan", 0x169C9C), java.util.Map.entry("purple", 0x8932B8), java.util.Map.entry("blue", 0x3C44AA),
            java.util.Map.entry("brown", 0x835432), java.util.Map.entry("green", 0x5E7C16), java.util.Map.entry("red", 0xB02E26),
            java.util.Map.entry("black", 0x1D1D21));

    /**
     * BannerRenderer: pole (standing only), crossbar and cloth from banner_base, then the cloth again with the "base"
     * pattern tinted in the banner's colour, as the game draws it. Wall banners hang from the block, into the one below.
     */
    static Optional<BakedModel> banner(BlockState state, String p, TextureAtlas atlas, List<Pattern> patterns) {
        boolean wall = p.endsWith("_wall_banner");
        String color = p.substring(0, p.length() - (wall ? "_wall_banner" : "_banner").length());
        Integer dye = DYES.get(color);
        TextureAtlas.Sprite base = atlas.sprite("minecraft:entity/banner_base"), cloth = atlas.sprite("minecraft:entity/banner/base");
        if (dye == null || base.missing() || cloth.missing()) return Optional.empty();
        Mat root;
        if (wall) {
            root = Mat.identity().translate(0.5, -1 / 6.0, 0.5).rotateY(Math.toRadians(-yRot(state.get("facing")))).translate(0, -0.3125, -0.4375);
        } else {
            root = Mat.identity().translate(0.5, 0.5, 0.5).rotateY(Math.toRadians(-rotation(state) * 22.5));
        }
        root = root.scale(2 / 3.0, -2 / 3.0, -2 / 3.0);
        List<BakedQuad> quads = new ArrayList<>();
        if (!wall) emit(Part.of(Cube.of(44, 0, -1, -30, -1, 2, 42, 2)), root, 64, 64, base, quads);
        emit(Part.of(Cube.of(0, 42, -10, -32, -1, 20, 2, 2)), root, 64, 64, base, quads);
        Part flag = Part.of(Cube.of(0, 0, -10, 0, -2, 20, 40, 1)).at(0, -32, 0);
        emit(flag, root, 64, 64, base, quads);
        // The coloured layer sits a hair outside the cloth so it draws on top of it.
        List<BakedQuad> tinted = new ArrayList<>();
        emit(Part.of(new Cube(0, 0, -10, 0, -2, 20, 40, 1, 0.02f, false)).at(0, -32, 0), root, 64, 64, cloth, tinted);
        for (BakedQuad q : tinted) {
            quads.add(new BakedQuad(q.pos(), q.uv(), q.normal(), q.face(), q.cull(), dye, cloth.layer(), q.shade(), cloth));
        }
        // Then each pattern, in order, tinted in its colour, each a hair further out than the one below it.
        for (int i = 0; i < patterns.size(); i++) {
            Pattern pat = patterns.get(i);
            TextureAtlas.Sprite sprite = atlas.sprite(pat.texture());
            Integer c = DYES.get(pat.color());
            if (sprite.missing() || c == null) continue;
            List<BakedQuad> layer = new ArrayList<>();
            emit(Part.of(new Cube(0, 0, -10, 0, -2, 20, 40, 1, 0.02f * (i + 2), false)).at(0, -32, 0), root, 64, 64, sprite, layer);
            for (BakedQuad q : layer) quads.add(new BakedQuad(q.pos(), q.uv(), q.normal(), q.face(), q.cull(), c, sprite.layer(), q.shade(), sprite));
        }
        return Optional.of(new BakedModel(List.copyOf(quads), new boolean[6], false, false));
    }

    /** One banner pattern layer: its texture id (entity/banner/…) and dye colour name. */
    record Pattern(String texture, String color) {
    }

    private static final String[] DYE_IDS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    /** The short codes banners used before 1.20.5 ("Patterns": [{Pattern: "bs", Color: 14}]). */
    private static final java.util.Map<String, String> OLD_CODES = java.util.Map.ofEntries(
            java.util.Map.entry("b", "base"), java.util.Map.entry("bs", "stripe_bottom"), java.util.Map.entry("ts", "stripe_top"),
            java.util.Map.entry("ls", "stripe_left"), java.util.Map.entry("rs", "stripe_right"), java.util.Map.entry("cs", "stripe_center"),
            java.util.Map.entry("ms", "stripe_middle"), java.util.Map.entry("drs", "stripe_downright"), java.util.Map.entry("dls", "stripe_downleft"),
            java.util.Map.entry("ss", "small_stripes"), java.util.Map.entry("cr", "cross"), java.util.Map.entry("sc", "straight_cross"),
            java.util.Map.entry("ld", "diagonal_left"), java.util.Map.entry("rud", "diagonal_right"), java.util.Map.entry("lud", "diagonal_up_left"),
            java.util.Map.entry("rd", "diagonal_up_right"), java.util.Map.entry("vh", "half_vertical"), java.util.Map.entry("vhr", "half_vertical_right"),
            java.util.Map.entry("hh", "half_horizontal"), java.util.Map.entry("hhb", "half_horizontal_bottom"), java.util.Map.entry("bl", "square_bottom_left"),
            java.util.Map.entry("br", "square_bottom_right"), java.util.Map.entry("tl", "square_top_left"), java.util.Map.entry("tr", "square_top_right"),
            java.util.Map.entry("bt", "triangle_bottom"), java.util.Map.entry("tt", "triangle_top"), java.util.Map.entry("bts", "triangles_bottom"),
            java.util.Map.entry("tts", "triangles_top"), java.util.Map.entry("mc", "circle"), java.util.Map.entry("mr", "rhombus"),
            java.util.Map.entry("bo", "border"), java.util.Map.entry("cbo", "curly_border"), java.util.Map.entry("bri", "bricks"),
            java.util.Map.entry("gra", "gradient"), java.util.Map.entry("gru", "gradient_up"), java.util.Map.entry("cre", "creeper"),
            java.util.Map.entry("sku", "skull"), java.util.Map.entry("flo", "flower"), java.util.Map.entry("moj", "mojang"),
            java.util.Map.entry("glb", "globe"), java.util.Map.entry("pig", "piglin"), java.util.Map.entry("flw", "flow"),
            java.util.Map.entry("gus", "guster"));

    /**
     * A banner block entity's patterns, in order. Reads the 1.20.5+ form ({@code patterns: [{pattern: "minecraft:stripe_bottom",
     * color: "red"}]}) and the older one ({@code Patterns: [{Pattern: "bs", Color: 14}]}).
     */
    static List<Pattern> patterns(io.blockdesigner.core.nbt.CompoundTag nbt) {
        List<Pattern> out = new ArrayList<>();
        if (nbt == null) return out;
        if (nbt.contains("patterns")) {
            for (io.blockdesigner.core.nbt.Tag t : nbt.getList("patterns")) {
                if (!(t instanceof io.blockdesigner.core.nbt.CompoundTag c)) continue;
                String id = c.getString("pattern");
                if (id.isEmpty()) continue;
                int colon = id.indexOf(':');
                String ns = colon < 0 ? "minecraft" : id.substring(0, colon), path = id.substring(colon + 1);
                String color = c.getString("color");
                out.add(new Pattern(ns + ":entity/banner/" + path, color.isEmpty() ? "white" : color));
            }
        } else if (nbt.contains("Patterns")) {
            for (io.blockdesigner.core.nbt.Tag t : nbt.getList("Patterns")) {
                if (!(t instanceof io.blockdesigner.core.nbt.CompoundTag c)) continue;
                String name = OLD_CODES.get(c.getString("Pattern"));
                int color = c.getInt("Color");
                if (name == null || color < 0 || color >= DYE_IDS.length) continue;
                out.add(new Pattern("minecraft:entity/banner/" + name, DYE_IDS[color]));
            }
        }
        return out;
    }

    // ---- Minecraft's model definitions -------------------------------------------------------------------------

    /** ChestRenderer's single / double halves (the double halves meet at the seam, the lock split between them). */
    private static List<Part> chest(boolean left, boolean right) {
        if (left) {
            return List.of(Part.of(Cube.of(0, 19, 0, 0, 1, 15, 10, 14)), Part.of(Cube.of(0, 0, 0, 0, 0, 15, 5, 14)).at(0, 9, 1),
                    Part.of(Cube.of(0, 0, 0, -2, 14, 1, 4, 1)).at(0, 8, 0));
        }
        if (right) {
            return List.of(Part.of(Cube.of(0, 19, 1, 0, 1, 15, 10, 14)), Part.of(Cube.of(0, 0, 1, 0, 0, 15, 5, 14)).at(0, 9, 1),
                    Part.of(Cube.of(0, 0, 15, -2, 14, 1, 4, 1)).at(0, 8, 0));
        }
        return List.of(Part.of(Cube.of(0, 19, 1, 0, 1, 14, 10, 14)), Part.of(Cube.of(0, 0, 1, 0, 0, 14, 5, 14)).at(0, 9, 1),
                Part.of(Cube.of(0, 0, 7, -2, 14, 2, 4, 1)).at(0, 8, 0));
    }

    /** BedRenderer's head and foot halves (the mattress and two legs each). */
    private static List<Part> bed(boolean head) {
        float pi = (float) Math.PI;
        if (head) {
            return List.of(Part.of(Cube.of(0, 0, 0, 0, 0, 16, 16, 6)),
                    Part.of(Cube.of(50, 6, 0, 6, 0, 3, 3, 3)).rotated(pi / 2, 0, pi / 2),
                    Part.of(Cube.of(50, 18, -16, 6, 0, 3, 3, 3)).rotated(pi / 2, 0, pi));
        }
        return List.of(Part.of(Cube.of(0, 22, 0, 0, 0, 16, 16, 6)),
                Part.of(Cube.of(50, 0, 0, 6, -16, 3, 3, 3)).rotated(pi / 2, 0, 0),
                Part.of(Cube.of(50, 12, -16, 6, -16, 3, 3, 3)).rotated(pi / 2, 0, 3 * pi / 2));
    }

    /** SignRenderer: the board, and the stick for standing signs. */
    private static List<Part> sign(boolean standing) {
        Part board = Part.of(Cube.of(0, 0, -12, -14, -1, 24, 12, 2));
        return standing ? List.of(board, Part.of(Cube.of(0, 14, -1, -2, -1, 2, 14, 2))) : List.of(board);
    }

    /** HangingSignRenderer: the board, the plank on walls, and the chains. */
    private static List<Part> hangingSign(boolean wall, boolean vChains) {
        List<Part> out = new ArrayList<>();
        out.add(Part.of(Cube.of(0, 12, -7, 0, -1, 14, 10, 2)));
        if (wall) out.add(Part.of(Cube.of(0, 0, -8, -6, -2, 16, 2, 4)));
        if (vChains) {
            out.add(Part.of(Cube.of(14, 6, -6, -6, 0, 12, 6, 0)));
        } else {
            float q = (float) Math.PI / 4;
            out.add(Part.of(Cube.of(0, 6, -1.5f, 0, 0, 3, 6, 0)).at(-5, -6, 0).rotated(0, -q, 0));
            out.add(Part.of(Cube.of(6, 6, -1.5f, 0, 0, 3, 6, 0)).at(-5, -6, 0).rotated(0, q, 0));
            out.add(Part.of(Cube.of(0, 6, -1.5f, 0, 0, 3, 6, 0)).at(5, -6, 0).rotated(0, -q, 0));
            out.add(Part.of(Cube.of(6, 6, -1.5f, 0, 0, 3, 6, 0)).at(5, -6, 0).rotated(0, q, 0));
        }
        return out;
    }

    /** PiglinHeadModel: head, snout, tusks and the two ears. */
    private static Part piglinHead() {
        Part leftEar = Part.of(Cube.of(51, 6, 0, 0, -2, 1, 5, 4)).at(4.5f, -6, 0).rotated(0, 0, (float) -Math.PI / 6);
        Part rightEar = Part.of(Cube.of(39, 6, -1, 0, -2, 1, 5, 4)).at(-4.5f, -6, 0).rotated(0, 0, (float) Math.PI / 6);
        return Part.of(Cube.of(0, 0, -5, -8, -4, 10, 8, 8), Cube.of(31, 1, -2, -4, -5, 4, 4, 1), Cube.of(2, 4, 2, -2, -5, 1, 2, 1),
                Cube.of(2, 0, -3, -2, -5, 1, 2, 1)).with(leftEar, rightEar);
    }

    /** DragonHeadModel: snout, head, scales, nostrils and jaw. */
    private static Part dragonHead() {
        float f = -16;
        Part jaw = Part.of(Cube.of(176, 65, -6, 0, -16, 12, 4, 16)).at(0, 4, -8 + f);
        return Part.of(Cube.of(176, 44, -6, -1, -8 + f, 12, 5, 16), Cube.of(112, 30, -8, -8, 6 + f, 16, 16, 16),
                new Cube(0, 0, -5, -12, 12 + f, 2, 4, 6, 0, true), new Cube(112, 0, -5, -3, -6 + f, 2, 2, 4, 0, true),
                Cube.of(0, 0, 3, -12, 12 + f, 2, 4, 6), Cube.of(112, 0, 3, -3, -6 + f, 2, 2, 4)).with(jaw);
    }

    /** Pottery sherd item ids of a decorated pot (back, left, right, front), from its block entity; empty when plain. */
    static List<String> sherds(io.blockdesigner.core.nbt.CompoundTag nbt) {
        List<String> out = new ArrayList<>();
        if (nbt == null) return out;
        for (io.blockdesigner.core.nbt.Tag t : nbt.getList("sherds")) {
            if (t instanceof io.blockdesigner.core.nbt.StringTag st) out.add(st.value());
        }
        return out;
    }

    /**
     * DecoratedPotRenderer: the neck, top and bottom from decorated_pot_base, and four sides, each the plain side or a
     * sherd's pattern. {@code sherds} is back, left, right, front (bricks and missing entries are plain).
     */
    static Optional<BakedModel> decoratedPot(BlockState state, List<String> sherds, TextureAtlas atlas) {
        TextureAtlas.Sprite base = atlas.sprite("minecraft:entity/decorated_pot/decorated_pot_base");
        TextureAtlas.Sprite plain = atlas.sprite("minecraft:entity/decorated_pot/decorated_pot_side");
        if (base.missing() || plain.missing()) return Optional.empty();
        Mat root = Mat.identity().translate(0.5, 0, 0.5).rotateY(Math.toRadians(180 - yRot(state.get("facing")))).translate(-0.5, 0, -0.5);
        List<BakedQuad> quads = new ArrayList<>();
        float pi = (float) Math.PI;
        emit(Part.of(new Cube(0, 0, 4, 17, 4, 8, 3, 8, 0.2f, false), new Cube(0, 5, 5, 20, 5, 6, 1, 6, -0.1f, false)).at(0, 37, 16).rotated(pi, 0, 0),
                root, 32, 32, base, quads);
        emit(Part.of(Cube.of(-14, 13, 0, 0, 0, 14, 0, 14)).at(1, 16, 1), root, 32, 32, base, quads);
        emit(Part.of(Cube.of(-14, 13, 0, 0, 0, 14, 0, 14)).at(1, 0, 1), root, 32, 32, base, quads);
        Part[] sides = {Part.of(Cube.of(1, 0, 0, 0, 0, 14, 16, 0)).at(15, 16, 1).rotated(0, 0, pi),
                Part.of(Cube.of(1, 0, 0, 0, 0, 14, 16, 0)).at(1, 16, 1).rotated(0, -pi / 2, pi),
                Part.of(Cube.of(1, 0, 0, 0, 0, 14, 16, 0)).at(15, 16, 15).rotated(0, pi / 2, pi),
                Part.of(Cube.of(1, 0, 0, 0, 0, 14, 16, 0)).at(1, 16, 15).rotated(pi, 0, 0)};
        for (int i = 0; i < 4; i++) {
            TextureAtlas.Sprite side = plain;
            String sherd = i < sherds.size() ? sherds.get(i) : "";
            if (sherd.endsWith("_pottery_sherd")) {
                int c = sherd.indexOf(':');
                String sns = c < 0 ? "minecraft" : sherd.substring(0, c), name = sherd.substring(c + 1, sherd.length() - "_sherd".length());
                TextureAtlas.Sprite pattern = atlas.sprite(sns + ":entity/decorated_pot/" + name + "_pattern");
                if (!pattern.missing()) side = pattern;
            }
            northFace(sides[i], root, 16, 16, side, quads);
        }
        return Optional.of(new BakedModel(List.copyOf(quads), new boolean[6], false, false));
    }

    /** As {@link #emit}, drawing only each cube's north face (a ModelPart built with {@code EnumSet.of(NORTH)}). */
    private static void northFace(Part part, Mat parent, int tw, int th, TextureAtlas.Sprite sprite, List<BakedQuad> out) {
        Mat m = parent.translate(part.px / 16, part.py / 16, part.pz / 16);
        if (part.xRot != 0 || part.yRot != 0 || part.zRot != 0) m = m.rotateZ(part.zRot).rotateY(part.yRot).rotateX(part.xRot);
        for (Cube c : part.cubes) {
            float x0 = c.x, y0 = c.y, z0 = c.z, x1 = c.x + c.w, y1 = c.y + c.h;
            float[] v7 = {x0, y0, z0}, v = {x1, y0, z0}, v1 = {x1, y1, z0}, v2 = {x0, y1, z0};
            float u5 = c.u + c.d, u6 = c.u + c.d + c.w, v11 = c.v + c.d, v12 = c.v + c.d + c.h;
            // The side is a flat plate: without a thickness to tell inside from out, the game's winding is kept.
            face(out, m, sprite, tw, th, false, new float[]{(x0 + x1) / 2, (y0 + y1) / 2, z0}, new float[][]{v, v7, v2, v1}, u5, v11, u6, v12, c.w * c.h);
        }
    }

    // ---- building quads ------------------------------------------------------------------------------------------

    static void emit(Part part, Mat parent, int tw, int th, TextureAtlas.Sprite sprite, List<BakedQuad> out) {
        Mat m = parent.translate(part.px / 16, part.py / 16, part.pz / 16);
        if (part.xRot != 0 || part.yRot != 0 || part.zRot != 0) m = m.rotateZ(part.zRot).rotateY(part.yRot).rotateX(part.xRot);
        for (Cube c : part.cubes) cube(c, m, tw, th, sprite, out);
        for (Part child : part.children) emit(child, m, tw, th, sprite, out);
    }

    /** Minecraft's ModelPart.Cube: eight corners and six faces with its UV layout, then the pose applied. */
    private static void cube(Cube c, Mat m, int tw, int th, TextureAtlas.Sprite sprite, List<BakedQuad> out) {
        float x0 = c.x - c.grow, y0 = c.y - c.grow, z0 = c.z - c.grow;
        float x1 = c.x + c.w + c.grow, y1 = c.y + c.h + c.grow, z1 = c.z + c.d + c.grow;
        if (c.mirror) {
            float t = x1;
            x1 = x0;
            x0 = t;
        }
        float[] v7 = {x0, y0, z0}, v = {x1, y0, z0}, v1 = {x1, y1, z0}, v2 = {x0, y1, z0};
        float[] v3 = {x0, y0, z1}, v4 = {x1, y0, z1}, v5 = {x1, y1, z1}, v6 = {x0, y1, z1};
        float u = c.u, w = c.w, h = c.h, d = c.d, tv = c.v;
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
        float f10 = tv, f11 = tv + d, f12 = tv + d + h;
        float[] center = {(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2};
        face(out, m, sprite, tw, th, c.mirror, center, new float[][]{v4, v3, v7, v}, f5, f10, f6, f11, w * d);  // down
        face(out, m, sprite, tw, th, c.mirror, center, new float[][]{v1, v2, v6, v5}, f6, f11, f7, f10, w * d); // up
        face(out, m, sprite, tw, th, c.mirror, center, new float[][]{v7, v3, v6, v2}, f4, f11, f5, f12, d * h); // west
        face(out, m, sprite, tw, th, c.mirror, center, new float[][]{v, v7, v2, v1}, f5, f11, f6, f12, w * h);  // north
        face(out, m, sprite, tw, th, c.mirror, center, new float[][]{v4, v, v1, v5}, f6, f11, f8, f12, d * h);  // east
        face(out, m, sprite, tw, th, c.mirror, center, new float[][]{v3, v4, v5, v6}, f8, f11, f9, f12, w * h); // south
    }

    /** One polygon: Minecraft's corner-to-UV mapping, transformed, wound to face outwards. */
    private static void face(List<BakedQuad> out, Mat m, TextureAtlas.Sprite sprite, int tw, int th, boolean mirror, float[] center,
                             float[][] corners, float u1, float v1, float u2, float v2, float area) {
        if (area <= 0) return;
        float[][] uv = {{u2, v1}, {u1, v1}, {u1, v2}, {u2, v2}};
        float[][] c = corners.clone();
        if (mirror) {
            c = new float[][]{corners[3], corners[2], corners[1], corners[0]};
            uv = new float[][]{uv[3], uv[2], uv[1], uv[0]};
        }
        double[][] p = new double[4][];
        for (int i = 0; i < 4; i++) p[i] = m.point(c[i][0] / 16, c[i][1] / 16, c[i][2] / 16);
        // Outward: from the cube's centre towards this face's centre.
        double[] cc = m.point(center[0] / 16, center[1] / 16, center[2] / 16);
        double[] fc = {(p[0][0] + p[1][0] + p[2][0] + p[3][0]) / 4, (p[0][1] + p[1][1] + p[2][1] + p[3][1]) / 4, (p[0][2] + p[1][2] + p[2][2] + p[3][2]) / 4};
        double[] outward = {fc[0] - cc[0], fc[1] - cc[1], fc[2] - cc[2]};
        double[] n = cross(sub(p[1], p[0]), sub(p[2], p[0]));
        if (dot(n, n) < 1e-14) n = cross(sub(p[2], p[0]), sub(p[3], p[0]));
        int[] order = {0, 1, 2, 3};
        // A flat cube (zero depth) has no inside: keep Minecraft's winding. Otherwise turn it to face outwards.
        if (dot(outward, outward) > 1e-12 && dot(n, outward) < 0) {
            order = new int[]{3, 2, 1, 0};
            n = new double[]{-n[0], -n[1], -n[2]};
        }
        double len = Math.sqrt(dot(n, n));
        float[] normal = len < 1e-12 ? new float[]{0, 1, 0} : new float[]{(float) (n[0] / len), (float) (n[1] / len), (float) (n[2] / len)};
        float[] pos = new float[12], tex = new float[8];
        for (int i = 0; i < 4; i++) {
            int k = order[i];
            pos[i * 3] = (float) p[k][0];
            pos[i * 3 + 1] = (float) p[k][1];
            pos[i * 3 + 2] = (float) p[k][2];
            tex[i * 2] = sprite.u(uv[k][0] / tw * 16);
            tex[i * 2 + 1] = sprite.v(uv[k][1] / th * 16);
        }
        out.add(new BakedQuad(pos, tex, normal, Dir.nearest(normal[0], normal[1], normal[2]), null, -1, sprite.layer(), true, sprite));
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Minecraft's Direction.toYRot: south 0, west 90, north 180, east 270. */
    private static double yRot(String facing) {
        return switch (String.valueOf(facing)) {
            case "west" -> 90;
            case "north" -> 180;
            case "east" -> 270;
            default -> 0;
        };
    }

    private static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    private static int rotation(BlockState s) {
        try {
            return Integer.parseInt(String.valueOf(s.get("rotation")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A small 4×4 transform (row-major), composed like Minecraft's PoseStack: each call applies inside the last. */
    record Mat(double[] a) {
        static Mat identity() {
            return new Mat(new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        }

        Mat mul(double[] b) {
            double[] r = new double[16];
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 4; j++) {
                    double s = 0;
                    for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
                    r[i * 4 + j] = s;
                }
            return new Mat(r);
        }

        Mat translate(double x, double y, double z) {
            return mul(new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1});
        }

        Mat scale(double x, double y, double z) {
            return mul(new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1});
        }

        Mat rotateX(double t) {
            double c = Math.cos(t), s = Math.sin(t);
            return mul(new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1});
        }

        Mat rotateY(double t) {
            double c = Math.cos(t), s = Math.sin(t);
            return mul(new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1});
        }

        Mat rotateZ(double t) {
            double c = Math.cos(t), s = Math.sin(t);
            return mul(new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        }

        double[] point(double x, double y, double z) {
            return new double[]{a[0] * x + a[1] * y + a[2] * z + a[3], a[4] * x + a[5] * y + a[6] * z + a[7], a[8] * x + a[9] * y + a[10] * z + a[11]};
        }
    }
}
