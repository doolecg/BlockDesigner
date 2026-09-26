package io.blockdesigner.assets;

import io.blockdesigner.assets.EntityModels.Part;
import io.blockdesigner.core.model.EntityTypes;
import io.blockdesigner.core.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which of the game's model layers ({@link GameModels}) and textures each mob is drawn with: its variant from the
 * entity's NBT (a cat's coat, a horse's colour and markings, a llama's wool), parts the game hides until needed (chests
 * on llamas, an illager's separate arms), overlays (eyes, markings, clothes) and the renderer's own scaling. Texture
 * names changed between versions, so each lists every spelling: the first one the loaded game has is used.
 */
final class GameMobs {
    private GameMobs() {
    }

    /**
     * One textured pass over a layer's parts. {@code tint} is 0xRRGGBB, or -1 for none; {@code glow} passes are drawn
     * unlit at full brightness (eyes).
     */
    record Pass(List<Part> parts, TextureAtlas.Sprite sprite, int texWidth, int texHeight, int tint, boolean glow) {
    }

    /** How an entity's renderer places its model: most as a living entity (standing, turned, flipped). */
    enum Frame {
        LIVING, BOAT, MINECART, CRYSTAL, DRAGON
    }

    /**
     * A block drawn on a part of the model, as SnowGolemHeadLayer draws the carved pumpkin: at the part's pose, raised
     * 0.34375, turned round and shrunk to 0.625.
     */
    record BlockOn(String layer, String part, String block) {
    }

    /**
     * What to draw for an entity: its passes, the extra scale its renderer applies, how it places the model, and a
     * shift in model space (pixels, y down) its renderer adds.
     */
    record Look(List<Pass> passes, float scale, Frame frame, float[] offset, List<BlockOn> blocks) {
    }

    /** Collects a mob's passes while its recipe runs. */
    static final class Builder {
        final CompoundTag nbt;
        final TextureAtlas atlas;
        final boolean baby;
        final List<Pass> passes = new ArrayList<>();
        final Map<String, float[]> tweaks = new LinkedHashMap<>();
        final List<BlockOn> blocks = new ArrayList<>();
        float scale = 1;
        Frame frame = Frame.LIVING;
        float[] offset = {0, 0, 0};
        /** Whether the base pass found a baby model and texture (26.x); otherwise babies are the adult at half size. */
        boolean babyModel;
        /** The passes being added glow (see {@link #glow}). */
        private boolean glowing;

        Builder(CompoundTag nbt, TextureAtlas atlas) {
            this.nbt = nbt;
            this.atlas = atlas;
            this.baby = EntityTypes.baby(nbt);
        }

        /** A pass of {@code layer} (e.g. "fox#main") with the first of {@code textures} this version has. */
        Builder skin(String layer, int tint, Set<String> hidden, String... textures) {
            layer = "minecraft:" + layer;
            boolean base = passes.isEmpty();
            if (baby && (base || babyModel)) {
                String babyLayer = layer.replace("#", "_baby#");
                TextureAtlas.Sprite s = GameModels.has(babyLayer) ? first(babyNames(textures)) : null;
                if (s != null) {
                    if (base) babyModel = true;
                    add(babyLayer, s, tint, hidden);
                    return this;
                }
                if (!base) return this;
            }
            TextureAtlas.Sprite s = first(textures);
            if (s != null) add(layer, s, tint, hidden);
            return this;
        }

        Builder skin(String layer, String... textures) {
            return skin(layer, -1, Set.of(), textures);
        }

        /** A glowing pass: eyes and the like, drawn unlit at full brightness as the game's "eyes" render type. */
        Builder glow(String layer, String... textures) {
            glowing = true;
            skin(layer, textures);
            glowing = false;
            return this;
        }

        private void add(String layer, TextureAtlas.Sprite s, int tint, Set<String> hidden) {
            GameModels.layer(layer, hidden, tweaks).ifPresent(l -> {
                // A texture shaped unlike the model's was drawn for another version's model (1.21.1's 64x32 rabbit):
                // leave it to MobModels' older models.
                if (s.width() * l.texHeight() != s.height() * l.texWidth()) return;
                passes.add(new Pass(l.parts(), s, l.texWidth(), l.texHeight(), tint, glowing));
            });
        }

        private TextureAtlas.Sprite first(String... textures) {
            for (String t : textures) {
                TextureAtlas.Sprite s = atlas.sprite("minecraft:" + t);
                if (!s.missing()) return s;
            }
            return null;
        }

        Builder scale(float s) {
            scale *= s;
            return this;
        }

        /** Adds to a part's pose for the passes after this: {x, y, z (pixels), xRot, yRot, zRot (radians)}. */
        Builder pose(String part, float... add) {
            tweaks.put(part, add);
            return this;
        }

        Builder frame(Frame f) {
            frame = f;
            return this;
        }

        /** A block on a part (see {@link BlockOn}). */
        Builder block(String layer, String part, String block) {
            blocks.add(new BlockOn("minecraft:" + layer, part, block));
            return this;
        }

        /** A shift in model space, in pixels (y down), as a renderer's translate before drawing. */
        Builder offset(float x, float y, float z) {
            offset = new float[]{x, y, z};
            return this;
        }

        String variant(String key) {
            return variantOf(nbt.getString(key));
        }

        /** An id without its namespace ("minecraft:snow" → "snow"). */
        String variantOf(String id) {
            return id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        }
    }

    private static String[] babyNames(String[] textures) {
        String[] out = new String[textures.length];
        for (int i = 0; i < textures.length; i++) out[i] = textures[i] + "_baby";
        return out;
    }

    private interface Recipe {
        void look(Builder b);
    }

    private record Mob(Recipe recipe, List<String> textures) {
    }

    private static final Map<String, Mob> MOBS = new LinkedHashMap<>();

    private static void mob(String id, Recipe recipe, String... textures) {
        MOBS.put(id, new Mob(recipe, List.of(textures)));
    }

    /** "a/b_{x}" for each value: every variant's spelling, for the atlas. */
    private static String[] each(String pattern, List<String> values) {
        return values.stream().map(v -> pattern.replace("{}", v)).toArray(String[]::new);
    }

    private static String[] concat(String[]... parts) {
        List<String> out = new ArrayList<>();
        for (String[] p : parts) out.addAll(List.of(p));
        return out.toArray(String[]::new);
    }

    private static String pick(List<String> values, int i, String fallback) {
        return i >= 0 && i < values.size() ? values.get(i) : fallback;
    }

    /** DyeColor.getTextureDiffuseColor, by dye id. */
    private static final int[] DYE = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    private static final List<String> DYES = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static final float CRYSTAL_X = 0.6847192f, CRYSTAL_Y = -0.2526803f, CRYSTAL_Z = 0.6847192f;
    private static final Set<String> ILLAGER_HIDDEN = Set.of("hat", "left_arm", "right_arm");
    private static final Set<String> CHESTS = Set.of("left_chest", "right_chest");

    private static final List<String> CLIMATES = List.of("temperate", "warm", "cold");

    private static String climate(Builder b) {
        String v = b.variant("variant");
        return CLIMATES.contains(v) ? v : "temperate";
    }

    private static final List<String> CATS = List.of("tabby", "black", "red", "siamese", "british_shorthair", "calico", "persian",
            "ragdoll", "white", "jellie", "all_black");
    private static final List<String> AXOLOTLS = List.of("lucy", "wild", "gold", "cyan", "blue");
    private static final List<String> PARROTS = List.of("red_blue", "blue", "green", "yellow_blue", "grey");
    private static final List<String> LLAMAS = List.of("creamy", "white", "brown", "gray");
    private static final List<String> HORSES = List.of("white", "creamy", "chestnut", "brown", "black", "gray", "darkbrown");
    private static final List<String> MARKINGS = List.of("", "white", "whitefield", "whitedots", "blackdots");
    private static final List<String> RABBITS = List.of("brown", "white", "black", "white_splotched", "gold", "salt");
    private static final List<String> PANDAS = List.of("lazy", "worried", "playful", "brown", "weak", "aggressive");
    private static final List<String> FROGS = List.of("temperate", "warm", "cold");
    private static final List<String> VILLAGER_TYPES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");
    private static final List<String> PROFESSIONS = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "cherry", "dark_oak", "mangrove",
            "pale_oak", "bamboo");

    static {
        // ---- farm and wild animals
        // Pigs, cows and chickens got climate variants with their own models in 1.21.5; older versions have the
        // hand-made models in MobModels (these return nothing there, as their textures are named differently).
        mob("pig", b -> {
            String v = climate(b);
            b.skin(v.equals("cold") ? "cold_pig#main" : "pig#main", "entity/pig/pig_" + v);
        }, each("entity/pig/pig_{}", CLIMATES));
        mob("cow", b -> {
            String v = climate(b);
            b.skin(v.equals("temperate") ? "cow#main" : v + "_cow#main", "entity/cow/cow_" + v);
        }, each("entity/cow/cow_{}", CLIMATES));
        mob("chicken", b -> {
            String v = climate(b);
            b.skin(v.equals("cold") ? "cold_chicken#main" : "chicken#main", "entity/chicken/chicken_" + v);
        }, each("entity/chicken/chicken_{}", CLIMATES));
        mob("mooshroom", b -> b.skin("mooshroom#main", "entity/cow/mooshroom_" + ("brown".equals(b.variant("Type")) ? "brown" : "red")),
                "entity/cow/mooshroom_red", "entity/cow/mooshroom_brown");
        mob("allay", b -> b.skin("allay#main", "entity/allay/allay"), "entity/allay/allay");
        mob("armadillo", b -> b.skin("armadillo#main", -1, Set.of("cube"), "entity/armadillo/armadillo", "entity/armadillo"),
                "entity/armadillo/armadillo", "entity/armadillo");
        mob("axolotl", b -> {
            String v = pick(AXOLOTLS, b.nbt.getInt("Variant"), "lucy");
            b.skin("axolotl#main", "entity/axolotl/axolotl_" + v);
        }, each("entity/axolotl/axolotl_{}", AXOLOTLS));
        mob("bat", b -> b.skin("bat#main", "entity/bat/bat", "entity/bat"), "entity/bat/bat", "entity/bat");
        mob("bee", b -> b.skin("bee#main", "entity/bee/bee"), "entity/bee/bee");
        mob("camel", b -> b.skin("camel#main", "entity/camel/camel"), "entity/camel/camel");
        mob("camel_husk", b -> b.skin("camel#main", "entity/camel/camel_husk"), "entity/camel/camel_husk");
        mob("cat", b -> {
            String v = b.nbt.contains("variant") ? b.variant("variant") : pick(CATS, b.nbt.getInt("CatType"), "tabby");
            b.skin("cat#main", "entity/cat/cat_" + v, "entity/cat/" + v);
        }, concat(each("entity/cat/cat_{}", CATS), each("entity/cat/{}", CATS)));
        mob("ocelot", b -> b.skin("ocelot#main", "entity/cat/ocelot"), "entity/cat/ocelot");
        mob("fox", b -> {
            boolean snow = "snow".equals(b.variant("Type"));
            b.skin("fox#main", snow ? "entity/fox/fox_snow" : "entity/fox/fox", snow ? "entity/fox/snow_fox" : "entity/fox/fox");
        }, "entity/fox/fox", "entity/fox/fox_snow", "entity/fox/snow_fox");
        mob("frog", b -> {
            String v = b.nbt.contains("variant") ? b.variant("variant") : "temperate";
            b.skin("frog#main", -1, Set.of("croaking_body", "tongue"), "entity/frog/frog_" + v, "entity/frog/" + v + "_frog");
        }, concat(each("entity/frog/frog_{}", FROGS), each("entity/frog/{}_frog", FROGS)));
        mob("tadpole", b -> b.skin("tadpole#main", "entity/tadpole/tadpole"), "entity/tadpole/tadpole");
        mob("goat", b -> {
            Set<String> hidden = new LinkedHashSet<>();
            if (b.nbt.contains("HasLeftHorn") && !b.nbt.getBoolean("HasLeftHorn")) hidden.add("left_horn");
            if (b.nbt.contains("HasRightHorn") && !b.nbt.getBoolean("HasRightHorn")) hidden.add("right_horn");
            b.skin("goat#main", -1, hidden, "entity/goat/goat");
        }, "entity/goat/goat");
        mob("horse", b -> {
            int v = b.nbt.getInt("Variant");
            String colour = pick(HORSES, v & 0xFF, "white"), marks = pick(MARKINGS, (v >> 8) & 0xFF, "");
            b.skin("horse#main", "entity/horse/horse_" + colour);
            if (!marks.isEmpty()) b.skin("horse#main", "entity/horse/horse_markings_" + marks);
        }, concat(each("entity/horse/horse_{}", HORSES), each("entity/horse/horse_markings_{}", MARKINGS.subList(1, MARKINGS.size()))));
        mob("donkey", b -> b.skin("donkey#main", -1, b.nbt.getBoolean("ChestedHorse") ? Set.of() : CHESTS, "entity/horse/donkey"),
                "entity/horse/donkey");
        mob("mule", b -> b.skin("mule#main", -1, b.nbt.getBoolean("ChestedHorse") ? Set.of() : CHESTS, "entity/horse/mule"),
                "entity/horse/mule");
        mob("skeleton_horse", b -> b.skin("skeleton_horse#main", "entity/horse/horse_skeleton"), "entity/horse/horse_skeleton");
        mob("zombie_horse", b -> b.skin("zombie_horse#main", "entity/horse/horse_zombie"), "entity/horse/horse_zombie");
        Recipe llama = b -> {
            String v = pick(LLAMAS, b.nbt.getInt("Variant"), "creamy");
            String layer = b.nbt.getString("id").endsWith("trader_llama") ? "trader_llama#main" : "llama#main";
            b.skin(layer, -1, b.nbt.getBoolean("ChestedHorse") ? Set.of() : CHESTS, "entity/llama/llama_" + v, "entity/llama/" + v);
            if (layer.startsWith("trader")) b.skin("llama#decor", "entity/equipment/llama_body/trader_llama", "entity/llama/decor/trader_llama");
        };
        String[] llamaTextures = concat(each("entity/llama/llama_{}", LLAMAS), each("entity/llama/{}", LLAMAS),
                new String[]{"entity/equipment/llama_body/trader_llama", "entity/llama/decor/trader_llama"});
        mob("llama", llama, llamaTextures);
        mob("trader_llama", llama, llamaTextures);
        mob("panda", b -> {
            String gene = b.nbt.contains("MainGene") ? b.variant("MainGene") : "normal";
            if (!PANDAS.contains(gene)) b.skin("panda#main", "entity/panda/panda");
            else b.skin("panda#main", "entity/panda/panda_" + gene, "entity/panda/" + gene + "_panda");
        }, concat(new String[]{"entity/panda/panda"}, each("entity/panda/panda_{}", PANDAS), each("entity/panda/{}_panda", PANDAS)));
        mob("parrot", b -> b.skin("parrot#main", "entity/parrot/parrot_" + pick(PARROTS, b.nbt.getInt("Variant"), "red_blue")),
                each("entity/parrot/parrot_{}", PARROTS));
        mob("polar_bear", b -> b.skin("polar_bear#main", "entity/bear/polarbear"), "entity/bear/polarbear");
        mob("rabbit", b -> {
            int t = b.nbt.getInt("RabbitType");
            String v = t == 99 ? "caerbannog" : pick(RABBITS, t, "brown");
            b.skin("rabbit#main", "entity/rabbit/rabbit_" + v, "entity/rabbit/" + v);
        }, concat(each("entity/rabbit/rabbit_{}", RABBITS), each("entity/rabbit/{}", RABBITS),
                new String[]{"entity/rabbit/rabbit_caerbannog", "entity/rabbit/caerbannog"}));
        mob("sniffer", b -> b.skin("sniffer#main", "entity/sniffer/sniffer"), "entity/sniffer/sniffer");
        mob("strider", b -> b.skin("strider#main", "entity/strider/strider"), "entity/strider/strider");
        mob("turtle", b -> b.skin("turtle#main", -1, b.nbt.getBoolean("HasEgg") ? Set.of() : Set.of("egg_belly"),
                "entity/turtle/turtle", "entity/turtle/big_sea_turtle"), "entity/turtle/turtle", "entity/turtle/big_sea_turtle");
        mob("happy_ghast", b -> b.skin("happy_ghast#main", "entity/ghast/happy_ghast"), "entity/ghast/happy_ghast");
        mob("copper_golem", b -> {
            b.skin("copper_golem#main", "entity/copper_golem/copper_golem");
            // Its eyes texture goes over the main model (the #eyes layer is empty).
            b.glow("copper_golem#main", "entity/copper_golem/copper_golem_eyes");
        }, "entity/copper_golem/copper_golem", "entity/copper_golem/copper_golem_eyes");
        mob("snow_golem", b -> {
            b.skin("snow_golem#main", "entity/snow_golem/snow_golem", "entity/snow_golem");
            // Its carved pumpkin, until it's sheared off.
            if (!b.nbt.contains("Pumpkin") || b.nbt.getBoolean("Pumpkin")) b.block("snow_golem#main", "head", "minecraft:carved_pumpkin[facing=north]");
        }, "entity/snow_golem/snow_golem", "entity/snow_golem");

        // ---- water
        mob("cod", b -> b.skin("cod#main", "entity/fish/cod"), "entity/fish/cod");
        mob("salmon", b -> {
            String type = b.variant("type");
            b.skin(type.equals("small") ? "salmon_small#main" : type.equals("large") ? "salmon_large#main" : "salmon#main", "entity/fish/salmon");
        }, "entity/fish/salmon");
        mob("pufferfish", b -> {
            int puff = b.nbt.getInt("PuffState");
            b.skin(puff >= 2 ? "pufferfish_big#main" : puff == 1 ? "pufferfish_medium#main" : "pufferfish_small#main", "entity/fish/pufferfish");
        }, "entity/fish/pufferfish");
        mob("tropical_fish", b -> {
            // Variant packs the shape, the pattern and the two colours into one int.
            int v = b.nbt.getInt("Variant");
            boolean large = (v & 0xFF) == 1;
            int pattern = (v >> 8) & 0xFF, base = (v >> 16) & 0xFF, second = (v >> 24) & 0xFF;
            String shape = large ? "b" : "a", layer = large ? "tropical_fish_large" : "tropical_fish_small";
            b.skin(layer + "#main", DYE[base & 15], Set.of(), "entity/fish/tropical_" + shape);
            b.skin(layer + "#pattern", DYE[second & 15], Set.of(), "entity/fish/tropical_" + shape + "_pattern_" + (Math.min(pattern, 5) + 1));
        }, concat(new String[]{"entity/fish/tropical_a", "entity/fish/tropical_b"},
                each("entity/fish/tropical_a_pattern_{}", List.of("1", "2", "3", "4", "5", "6")),
                each("entity/fish/tropical_b_pattern_{}", List.of("1", "2", "3", "4", "5", "6"))));
        mob("squid", b -> b.skin("squid#main", "entity/squid/squid"), "entity/squid/squid");
        mob("glow_squid", b -> b.skin("glow_squid#main", "entity/squid/glow_squid"), "entity/squid/glow_squid");
        mob("dolphin", b -> b.skin("dolphin#main", "entity/dolphin/dolphin", "entity/dolphin"), "entity/dolphin/dolphin", "entity/dolphin");
        mob("guardian", b -> b.skin("guardian#main", "entity/guardian/guardian", "entity/guardian"), "entity/guardian/guardian", "entity/guardian");
        mob("elder_guardian", b -> b.skin("elder_guardian#main", "entity/guardian/guardian_elder", "entity/guardian_elder"),
                "entity/guardian/guardian_elder", "entity/guardian_elder");
        mob("nautilus", b -> b.skin("nautilus#main", "entity/nautilus/nautilus"), "entity/nautilus/nautilus");
        mob("zombie_nautilus", b -> b.skin("zombie_nautilus#main", "entity/nautilus/zombie_nautilus"), "entity/nautilus/zombie_nautilus");

        // ---- monsters
        mob("blaze", b -> b.skin("blaze#main", "entity/blaze/blaze", "entity/blaze"), "entity/blaze/blaze", "entity/blaze");
        mob("breeze", b -> {
            b.skin("breeze#main", "entity/breeze/breeze");
            b.glow("breeze#eyes", "entity/breeze/breeze_eyes");
        }, "entity/breeze/breeze", "entity/breeze/breeze_eyes");
        mob("bogged", b -> {
            b.skin("bogged#main", "entity/skeleton/bogged");
            b.skin("bogged#outer", "entity/skeleton/bogged_overlay");
        }, "entity/skeleton/bogged", "entity/skeleton/bogged_overlay");
        mob("parched", b -> b.skin("parched#main", "entity/skeleton/parched"), "entity/skeleton/parched");
        mob("cave_spider", b -> {
            b.skin("cave_spider#main", "entity/spider/cave_spider");
            b.glow("cave_spider#main", "entity/spider/spider_eyes", "entity/spider_eyes");
        }, "entity/spider/cave_spider", "entity/spider/spider_eyes", "entity/spider_eyes");
        mob("spider", b -> {
            b.skin("spider#main", "entity/spider/spider");
            b.glow("spider#main", "entity/spider/spider_eyes", "entity/spider_eyes");
        }, "entity/spider/spider", "entity/spider/spider_eyes", "entity/spider_eyes");
        mob("creaking", b -> b.skin("creaking#main", "entity/creaking/creaking"), "entity/creaking/creaking");
        mob("enderman", b -> {
            b.skin("enderman#main", "entity/enderman/enderman");
            b.glow("enderman#main", "entity/enderman/enderman_eyes");
        }, "entity/enderman/enderman", "entity/enderman/enderman_eyes");
        mob("endermite", b -> b.skin("endermite#main", "entity/endermite/endermite", "entity/endermite"),
                "entity/endermite/endermite", "entity/endermite");
        mob("silverfish", b -> b.skin("silverfish#main", "entity/silverfish/silverfish", "entity/silverfish"),
                "entity/silverfish/silverfish", "entity/silverfish");
        mob("ghast", b -> b.skin("ghast#main", "entity/ghast/ghast"), "entity/ghast/ghast");
        mob("hoglin", b -> b.skin("hoglin#main", "entity/hoglin/hoglin"), "entity/hoglin/hoglin");
        mob("zoglin", b -> b.skin("zoglin#main", "entity/hoglin/zoglin"), "entity/hoglin/zoglin");
        mob("piglin", b -> b.skin("piglin#main", "entity/piglin/piglin"), "entity/piglin/piglin");
        mob("piglin_brute", b -> b.skin("piglin_brute#main", "entity/piglin/piglin_brute"), "entity/piglin/piglin_brute");
        mob("zombified_piglin", b -> b.skin("zombified_piglin#main", "entity/piglin/zombified_piglin"), "entity/piglin/zombified_piglin");
        for (String illager : List.of("pillager", "vindicator", "evoker", "illusioner")) {
            mob(illager, b -> b.skin(illager + "#main", -1, ILLAGER_HIDDEN, "entity/illager/" + illager), "entity/illager/" + illager);
        }
        mob("ravager", b -> b.skin("ravager#main", "entity/illager/ravager"), "entity/illager/ravager");
        mob("vex", b -> b.skin("vex#main", "entity/illager/vex"), "entity/illager/vex");
        mob("witch", b -> b.skin("witch#main", "entity/witch/witch", "entity/witch"), "entity/witch/witch", "entity/witch");
        mob("phantom", b -> {
            b.skin("phantom#main", "entity/phantom/phantom", "entity/phantom");
            b.glow("phantom#main", "entity/phantom/phantom_eyes", "entity/phantom_eyes");
            b.scale(1 + 0.15f * b.nbt.getInt("size"));
            // PhantomRenderer.scale: translate(0, 1.3125, 0.1875) blocks, after the flip.
            b.offset(0, 21, 3);
        }, "entity/phantom/phantom", "entity/phantom", "entity/phantom/phantom_eyes", "entity/phantom_eyes");
        mob("shulker", b -> {
            int c = b.nbt.contains("Color") ? b.nbt.getByte("Color") : 16;
            String colour = c >= 0 && c < 16 ? "_" + DYES.get(c) : "";
            b.skin("shulker#main", "entity/shulker/shulker" + colour, "entity/shulker/shulker");
        }, concat(new String[]{"entity/shulker/shulker"}, each("entity/shulker/shulker_{}", DYES)));
        Recipe slime = b -> {
            boolean magma = b.nbt.getString("id").endsWith("magma_cube");
            if (magma) b.skin("magma_cube#main", "entity/slime/magmacube");
            else {
                b.skin("slime#main", "entity/slime/slime");
                b.skin("slime#outer", "entity/slime/slime");
            }
            b.scale(EntityTypes.slimeSize(b.nbt));
        };
        mob("slime", slime, "entity/slime/slime");
        mob("magma_cube", slime, "entity/slime/magmacube");
        mob("warden", b -> {
            b.skin("warden#main", "entity/warden/warden");
            // Its glowing patches and heart (which the game pulses; shown here at full glow).
            b.glow("warden#bioluminescent", "entity/warden/warden_bioluminescent_layer");
            b.glow("warden#heart", "entity/warden/warden_heart");
        }, "entity/warden/warden", "entity/warden/warden_bioluminescent_layer", "entity/warden/warden_heart");
        // Its neck and tail as EnderDragonModel.setupAnim lays them out for a dragon hovering in place (the #posed
        // layer, which packaging/mob_models.py makes by running setupAnim).
        mob("ender_dragon", b -> {
            b.frame(Frame.DRAGON);
            b.skin("ender_dragon#posed", "entity/enderdragon/dragon");
            b.glow("ender_dragon#posed", "entity/enderdragon/dragon_eyes");
        }, "entity/enderdragon/dragon", "entity/enderdragon/dragon_eyes");
        mob("wither", b -> {
            b.skin("wither#main", "entity/wither/wither");
            b.scale(2);
        }, "entity/wither/wither");
        mob("zombie_villager", b -> {
            b.skin("zombie_villager#main", "entity/zombie_villager/zombie_villager");
            CompoundTag data = b.nbt.getCompound("VillagerData");
            String type = b.variantOf(data.getString("type")), prof = b.variantOf(data.getString("profession"));
            if (VILLAGER_TYPES.contains(type)) b.skin("zombie_villager#main", "entity/zombie_villager/type/" + type);
            if (PROFESSIONS.contains(prof)) b.skin("zombie_villager#main", "entity/zombie_villager/profession/" + prof);
        }, concat(new String[]{"entity/zombie_villager/zombie_villager"}, each("entity/zombie_villager/type/{}", VILLAGER_TYPES),
                each("entity/zombie_villager/profession/{}", PROFESSIONS)));
        mob("giant", b -> b.skin("giant#main", "entity/zombie/zombie"), "entity/zombie/zombie");

        // ---- things
        mob("end_crystal", b -> b.frame(Frame.CRYSTAL)
                // At rest: the glass bobbed to EndCrystalRenderer.getY(0) (-1.1 blocks, halved), each shell turned 60 degrees
                // about the diagonal (EndCrystalModel.setupAnim), as ZYX angles.
                .pose("outer_glass", 0, -8.8f, 0, CRYSTAL_X, CRYSTAL_Y, CRYSTAL_Z).pose("inner_glass", 0, 0, 0, CRYSTAL_X, CRYSTAL_Y, CRYSTAL_Z)
                .pose("cube", 0, 0, 0, CRYSTAL_X, CRYSTAL_Y, CRYSTAL_Z).skin("end_crystal#main", -1, b.nbt.contains("ShowBottom") && !b.nbt.getBoolean("ShowBottom") ? Set.of("base") : Set.of(),
                "entity/end_crystal/end_crystal"), "entity/end_crystal/end_crystal");
        Recipe cart = b -> {
            String id = b.variantOf(b.nbt.getString("id"));
            b.frame(Frame.MINECART).skin((GameModels.has("minecraft:" + id + "#main") ? id : "minecart") + "#main", "entity/minecart/minecart", "entity/minecart");
        };
        for (String cartId : List.of("minecart", "chest_minecart", "furnace_minecart", "hopper_minecart", "tnt_minecart", "spawner_minecart",
                "command_block_minecart")) {
            mob(cartId, cart, "entity/minecart/minecart", "entity/minecart");
        }
        for (String wood : WOODS) {
            String boat = wood.equals("bamboo") ? "bamboo_raft" : wood + "_boat", chest = wood.equals("bamboo") ? "bamboo_chest_raft" : wood + "_chest_boat";
            mob(boat, b -> b.frame(Frame.BOAT).skin("boat/" + wood + "#main", "entity/boat/" + wood), "entity/boat/" + wood);
            mob(chest, b -> b.frame(Frame.BOAT).skin("chest_boat/" + wood + "#main", "entity/chest_boat/" + wood), "entity/chest_boat/" + wood);
        }
    }

    /** Every texture a mob here might use (all spellings, baby versions too), for the atlas. */
    static List<String> textures() {
        Set<String> out = new LinkedHashSet<>();
        for (Mob m : MOBS.values()) {
            for (String t : m.textures()) {
                out.add("minecraft:" + t);
                out.add("minecraft:" + t + "_baby");
            }
        }
        return List.copyOf(out);
    }

    static boolean has(String path) {
        return MOBS.containsKey(path);
    }

    /** How to draw a mob (by id path), or null when this game version has none of its textures. */
    static Look look(String path, CompoundTag nbt, TextureAtlas atlas) {
        Mob m = MOBS.get(path);
        if (m == null) return null;
        Builder b = new Builder(nbt, atlas);
        m.recipe().look(b);
        if (b.passes.isEmpty()) return null;
        return new Look(List.copyOf(b.passes), b.baby && !b.babyModel ? b.scale * 0.5f : b.scale, b.frame, b.offset, List.copyOf(b.blocks));
    }
}
