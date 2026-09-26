package io.blockdesigner.app.ui;

import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
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
 * Icons and one-line descriptions for entities: the mob's own model when the loaded game has one, else a spawn egg
 * drawn in the type's colour with darker spots (the look of Minecraft's eggs), so every mob, vanilla or modded, has a
 * recognisable tile.
 */
final class EntityIcons {
    private EntityIcons() {
    }

    private static final int SIZE = 32;
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    static Image icon(String id) {
        return CACHE.computeIfAbsent(id, EntityIcons::draw);
    }

    private static final Map<String, Image> MODEL_CACHE = new ConcurrentHashMap<>();
    private static io.blockdesigner.assets.BlockAssets modelAssets;

    /**
     * The mob's own model, drawn like an inventory block (from the front, three-quarters, lit from above) and fitted
     * to the tile; its egg when the loaded game has no model for it.
     */
    static Image icon(io.blockdesigner.assets.BlockAssets assets, String id) {
        if (assets == null) return icon(id);
        synchronized (MODEL_CACHE) {
            if (modelAssets != assets) {
                MODEL_CACHE.clear();
                modelAssets = assets;
            }
        }
        String full = EntityTypes.kind(id).id();
        return MODEL_CACHE.computeIfAbsent(full, k -> {
            int[] px = modelPixels(assets, k);
            if (px == null) return icon(k);
            WritableImage img = new WritableImage(BlockIcons.SIZE, BlockIcons.SIZE);
            img.getPixelWriter().setPixels(0, 0, BlockIcons.SIZE, BlockIcons.SIZE, javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, BlockIcons.SIZE);
            return img;
        });
    }

    /** {@link #icon(io.blockdesigner.assets.BlockAssets, String)}'s model view as ARGB pixels, or null when there's no model. */
    static int[] modelPixels(io.blockdesigner.assets.BlockAssets assets, String id) {
        String k = EntityTypes.kind(id).id();
        // Facing north, which the inventory view shows from the front (the dragon faces away from its rotation).
        List<BakedQuad> quads = assets.entityQuads(EntityTypes.create(k, 0, 0, 0, k.equals("minecraft:ender_dragon") ? 0 : 180));
        // One box is the placeholder: nothing to show but the egg.
        if (quads.size() <= 6) return null;
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BakedQuad q : quads) {
            for (int v = 0; v < 4; v++) {
                float[] p = {q.x(v), q.y(v), q.z(v)};
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], p[a]);
                    hi[a] = Math.max(hi[a], p[a]);
                }
            }
        }
        // Into the unit cube the block view expects, centred, keeping proportions.
        float size = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
        if (size <= 0) return null;
        List<BakedQuad> fitted = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            float[] pos = new float[12];
            for (int v = 0; v < 4; v++) {
                for (int a = 0; a < 3; a++) pos[v * 3 + a] = (q.pos()[v * 3 + a] - (lo[a] + hi[a]) / 2) / size + 0.5f;
            }
            fitted.add(new BakedQuad(pos, q.uv(), q.normal(), q.face(), q.cull(), q.tint(), q.layer(), q.shade(), q.sprite(), q.glow()));
        }
        BakedModel model = new BakedModel(fitted, new boolean[6], false, false);
        return BlockIcons.isometricPixels(assets.atlas(), model, new float[]{30, 225, 0, 0, 0, 0, 0.66f, 0.66f, 0.66f});
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
