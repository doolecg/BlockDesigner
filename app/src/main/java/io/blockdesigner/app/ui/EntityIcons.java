package io.blockdesigner.app.ui;

import io.blockdesigner.core.model.EntityTypes;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icons and one-line descriptions for entities: a spawn egg drawn in the type's colour with darker spots (the look of
 * Minecraft's eggs), so every mob, vanilla or modded, has a recognisable tile without rendering a model.
 */
final class EntityIcons {
    private EntityIcons() {
    }

    private static final int SIZE = 32;
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    static Image icon(String id) {
        return CACHE.computeIfAbsent(id, EntityIcons::draw);
    }

    /**
     * Where each modelled mob's face is on its texture: texture, texture width in model pixels, then the head's front
     * rectangle (x, y, w, h) — the north face of the head cube, where the model looks.
     */
    private record Face(String texture, int texWidth, int x, int y, int w, int h) {
    }

    private static final Map<String, List<Face>> FACES = Map.ofEntries(
            Map.entry("minecraft:pig", List.of(new Face("entity/pig/pig", 64, 8, 8, 8, 8), new Face("entity/pig/temperate_pig", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:cow", List.of(new Face("entity/cow/cow", 64, 6, 6, 8, 8), new Face("entity/cow/temperate_cow", 64, 6, 6, 8, 8))),
            Map.entry("minecraft:mooshroom", List.of(new Face("entity/cow/red_mooshroom", 64, 6, 6, 8, 8))),
            Map.entry("minecraft:sheep", List.of(new Face("entity/sheep/sheep", 64, 8, 8, 6, 6))),
            Map.entry("minecraft:chicken", List.of(new Face("entity/chicken", 64, 3, 3, 4, 6), new Face("entity/chicken/temperate_chicken", 64, 3, 3, 4, 6))),
            Map.entry("minecraft:wolf", List.of(new Face("entity/wolf/wolf", 64, 4, 4, 6, 6))),
            Map.entry("minecraft:villager", List.of(new Face("entity/villager/villager", 64, 8, 8, 8, 10))),
            Map.entry("minecraft:wandering_trader", List.of(new Face("entity/wandering_trader", 64, 8, 8, 8, 10))),
            Map.entry("minecraft:iron_golem", List.of(new Face("entity/iron_golem/iron_golem", 128, 8, 8, 8, 10))),
            Map.entry("minecraft:zombie", List.of(new Face("entity/zombie/zombie", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:husk", List.of(new Face("entity/zombie/husk", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:drowned", List.of(new Face("entity/zombie/drowned", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:skeleton", List.of(new Face("entity/skeleton/skeleton", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:stray", List.of(new Face("entity/skeleton/stray", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:wither_skeleton", List.of(new Face("entity/skeleton/wither_skeleton", 64, 8, 8, 8, 8))),
            Map.entry("minecraft:creeper", List.of(new Face("entity/creeper/creeper", 64, 8, 8, 8, 8))));
    private static final Map<String, Image> FACE_CACHE = new ConcurrentHashMap<>();
    private static io.blockdesigner.assets.BlockAssets faceAssets;

    /** The mob's face from its texture when the loaded game has it (as the palette tile), else its egg. */
    static Image icon(io.blockdesigner.assets.BlockAssets assets, String id) {
        if (assets == null) return icon(id);
        synchronized (FACE_CACHE) {
            if (faceAssets != assets) {
                FACE_CACHE.clear();
                faceAssets = assets;
            }
        }
        String full = EntityTypes.kind(id).id();
        Image face = FACE_CACHE.computeIfAbsent(full, k -> {
            for (Face f : FACES.getOrDefault(k, List.of())) {
                var sprite = assets.atlas().sprite("minecraft:" + f.texture());
                if (sprite.missing()) continue;
                java.awt.image.BufferedImage tex = assets.atlas().spriteImage(sprite);
                double scale = tex.getWidth() / (double) f.texWidth();
                int x0 = (int) Math.round(f.x() * scale), y0 = (int) Math.round(f.y() * scale);
                int w = Math.max(1, (int) Math.round(f.w() * scale)), h = Math.max(1, (int) Math.round(f.h() * scale));
                if (x0 + w > tex.getWidth() || y0 + h > tex.getHeight()) continue;
                // Square it up (villager faces are taller than wide), crisp nearest-neighbour pixels.
                int side = Math.max(w, h), out = 32;
                WritableImage img = new WritableImage(out, out);
                var pw = img.getPixelWriter();
                for (int y = 0; y < out; y++) {
                    for (int x = 0; x < out; x++) {
                        int sx = (int) ((x + 0.5) * side / out) - (side - w) / 2, sy = (int) ((y + 0.5) * side / out) - (side - h) / 2;
                        if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                        int argb = tex.getRGB(x0 + sx, y0 + sy);
                        if ((argb >>> 24) != 0) pw.setArgb(x, y, argb | 0xFF000000);
                    }
                }
                return img;
            }
            return icon(k);
        });
        return face;
    }

    private static Image draw(String id) {
        EntityTypes.Kind k = EntityTypes.kind(id);
        int base = k.color(), spot = shade(base, luminance(base) > 0.5 ? 0.55 : 1.8);
        WritableImage img = new WritableImage(SIZE, SIZE);
        var w = img.getPixelWriter();
        java.util.Random rnd = new java.util.Random(id.hashCode());
        List<int[]> spots = new ArrayList<>();
        for (int i = 0; i < 6; i++) spots.add(new int[]{8 + rnd.nextInt(16), 6 + rnd.nextInt(22), 2 + rnd.nextInt(3)});
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // An egg: narrower at the top. Pixel-art edges, like the game's items.
                double cy = 17, ry = 13.5, t = (y + 0.5 - cy) / ry;
                double rx = 10.5 * (t < 0 ? 1 + 0.18 * t : 1);
                double u = (x + 0.5 - 16) / rx;
                if (u * u + t * t > 1) continue;
                int c = base;
                for (int[] s : spots) if ((x - s[0]) * (x - s[0]) + (y - s[1]) * (y - s[1]) <= s[2] * s[2]) c = spot;
                // Light from the top left, a dark rim.
                double edge = u * u + t * t;
                if (edge > 0.82) c = shade(c, 0.62);
                else if (u < -0.2 && t < -0.25) c = shade(c, 1.22);
                w.setArgb(x, y, 0xFF000000 | c);
            }
        }
        return img;
    }

    private static double luminance(int rgb) {
        return (0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255)) / 255;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.clamp(((rgb >> 16) & 255) * f, 0, 255), g = (int) Math.clamp(((rgb >> 8) & 255) * f, 0, 255),
                b = (int) Math.clamp((rgb & 255) * f, 0, 255);
        return r << 16 | g << 8 | b;
    }

    static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black"};
    static final List<String> PROFESSIONS = List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    static final List<String> BIOMES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");

    /** What sets this one apart ("baby · pink wool", "farmer from the desert", "painting: Kebab (1×1)"), or empty. */
    static String describe(StructureEntity e) {
        CompoundTag nbt = e.nbt();
        String id = EntityTypes.kind(e.id()).id();
        List<String> parts = new ArrayList<>();
        if (EntityTypes.baby(nbt)) parts.add("baby");
        switch (id) {
            case "minecraft:sheep" -> parts.add(nbt.getBoolean("Sheared") ? "sheared" : BlockInfoHud.pretty(DYES[Math.floorMod(nbt.getByte("Color"), 16)]).toLowerCase() + " wool");
            case "minecraft:villager" -> {
                CompoundTag d = nbt.getCompound("VillagerData");
                String prof = strip(d.getString("profession")), type = strip(d.getString("type"));
                if (!prof.isEmpty() && !prof.equals("none")) parts.add(prof);
                if (!type.isEmpty()) parts.add("from the " + type);
            }
            case "minecraft:painting" -> {
                int[] size = EntityTypes.paintingSize(nbt);
                parts.add(BlockInfoHud.pretty(EntityTypes.paintingVariant(nbt)) + " (" + size[0] + "×" + size[1] + ")");
            }
            case "minecraft:armor_stand" -> {
                if (nbt.getBoolean("ShowArms")) parts.add("arms");
                if (nbt.getBoolean("Small")) parts.add("small");
                if (nbt.getBoolean("NoBasePlate")) parts.add("no base plate");
            }
            default -> {
            }
        }
        if (!EntityTypes.hanging(id)) parts.add("facing " + Math.round(e.yaw()) + "°");
        return String.join("  ·  ", parts);
    }

    private static String strip(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
