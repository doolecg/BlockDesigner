package io.blockdesigner.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BlockStateDefinition;
import io.blockdesigner.assets.model.ModelBaker;
import io.blockdesigner.assets.model.ModelLoader;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.version.McVersion;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything needed to draw blocks for one game version plus mods/resource packs: the block registry, the stitched
 * texture atlas and a cache of baked models per block state. Thread-safe for reads.
 */
public final class BlockAssets implements Closeable {
    private static final Pattern BLOCKSTATE_PATH = Pattern.compile("assets/([a-z0-9_.-]+)/blockstates/([a-z0-9_./-]+)\\.json");
    private static final Set<String> HIDDEN = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:moving_piston");

    private final McVersion version;
    private final AssetStack stack;
    private final Map<String, BlockStateDefinition> definitions;
    private final BlockRegistry registry;
    private final ModelLoader loader;
    private final TextureAtlas atlas;
    private final ModelBaker baker;
    private final Map<BlockState, BakedModel> cache = new ConcurrentHashMap<>();

    private BlockAssets(McVersion version, AssetStack stack, Map<String, BlockStateDefinition> definitions, BlockRegistry registry,
                        ModelLoader loader, TextureAtlas atlas) {
        this.version = version;
        this.stack = stack;
        this.definitions = definitions;
        this.registry = registry;
        this.loader = loader;
        this.atlas = atlas;
        this.baker = new ModelBaker(atlas, BlockTints.defaults());
    }

    /** Opens a game jar plus optional mod jars and resource packs (packs take priority over mods, mods over the game). */
    public static BlockAssets open(Path gameJar, List<Path> mods, List<Path> resourcePacks, Consumer<String> progress) throws IOException {
        List<AssetSource> sources = new ArrayList<>();
        for (Path p : resourcePacks) sources.add(AssetSource.open(p));
        for (Path p : mods) {
            try {
                sources.addAll(AssetSource.openWithNested(p));
            } catch (IOException e) {
                // A broken mod jar shouldn't stop the rest from loading.
                System.err.println("Skipping unreadable mod " + p.getFileName() + ": " + e.getMessage());
            }
        }
        sources.add(AssetSource.open(gameJar));
        McVersion version;
        try {
            version = McVersion.fromClientJar(gameJar);
        } catch (IOException e) {
            version = McVersion.latestKnown();
        }
        return load(version, new AssetStack(sources), progress);
    }

    public static BlockAssets load(McVersion version, AssetStack stack, Consumer<String> progress) {
        Consumer<String> log = progress == null ? s -> {
        } : progress;
        ObjectMapper json = new ObjectMapper();

        log.accept("Reading block states…");
        Map<String, BlockStateDefinition> defs = new LinkedHashMap<>();
        for (String path : stack.list("assets/")) {
            Matcher m = BLOCKSTATE_PATH.matcher(path);
            if (!m.matches()) continue;
            String id = m.group(1) + ":" + m.group(2);
            if (defs.containsKey(id)) continue;
            stack.read(path).ifPresent(bytes -> {
                try {
                    defs.put(id, BlockStateDefinition.parse(json.readTree(bytes)));
                } catch (IOException | RuntimeException ignored) {
                    // malformed blockstate file: skip block
                }
            });
        }

        log.accept("Reading block names…");
        Map<String, String> lang = readLang(stack, defs.keySet(), json);

        Map<String, BlockRegistry.BlockInfo> infos = new HashMap<>();
        defs.forEach((id, d) -> {
            if (HIDDEN.contains(id)) return;
            Map<String, List<String>> props = new LinkedHashMap<>();
            d.properties().forEach((k, v) -> props.put(k, List.copyOf(v)));
            infos.put(id, new BlockRegistry.BlockInfo(id, props, BlockState.of(id, d.defaultProperties()), langName(id, lang)));
        });

        log.accept("Resolving " + defs.size() + " block models…");
        ModelLoader loader = new ModelLoader(stack);
        Set<String> textures = new LinkedHashSet<>(FallbackModels.extraTextures());
        // Every banner pattern texture (vanilla, mods and resource packs), for patterned banners.
        for (String path : stack.list("assets/")) {
            Matcher m = BANNER_PATTERN.matcher(path);
            if (m.matches()) textures.add(m.group(1) + ":entity/banner/" + m.group(2));
            // Paintings (each variant's picture), and modded signs' board textures.
            Matcher pm = PAINTING.matcher(path);
            if (pm.matches()) textures.add(pm.group(1) + ":painting/" + pm.group(2));
            Matcher sm = SIGN_TEXTURE.matcher(path);
            if (sm.matches()) textures.add(sm.group(1) + ":entity/signs/" + sm.group(2));
            Matcher dm = POT_TEXTURE.matcher(path);
            if (dm.matches()) textures.add(dm.group(1) + ":entity/decorated_pot/" + dm.group(2));
        }
        for (BlockStateDefinition d : defs.values()) {
            for (String model : d.allModels()) {
                loader.resolve(model).ifPresent(rm -> {
                    rm.elements().forEach(e -> e.faces().values().forEach(f -> {
                        String t = rm.resolve(f.texture());
                        if (t != null) textures.add(t);
                    }));
                    rm.particle().ifPresent(textures::add);
                });
            }
        }

        log.accept("Stitching " + textures.size() + " textures…");
        TextureAtlas atlas = TextureAtlas.build(stack, textures);
        log.accept("Ready: " + infos.size() + " blocks, atlas " + atlas.width() + "×" + atlas.height());
        return new BlockAssets(version, stack, defs, new BlockRegistry(infos), loader, atlas);
    }

    /**
     * Blocks whose item is named differently from the block (their item is not a plain block item), so the palette shows
     * the name you know from the inventory.
     */
    private static final Map<String, String> ITEM_NAMES = Map.ofEntries(
            Map.entry("minecraft:redstone_wire", "item.minecraft.redstone"),
            Map.entry("minecraft:tripwire", "item.minecraft.string"),
            Map.entry("minecraft:cocoa", "item.minecraft.cocoa_beans"),
            Map.entry("minecraft:carrots", "item.minecraft.carrot"),
            Map.entry("minecraft:potatoes", "item.minecraft.potato"),
            Map.entry("minecraft:beetroots", "item.minecraft.beetroot_seeds"),
            Map.entry("minecraft:wheat", "item.minecraft.wheat_seeds"),
            Map.entry("minecraft:melon_stem", "item.minecraft.melon_seeds"),
            Map.entry("minecraft:pumpkin_stem", "item.minecraft.pumpkin_seeds"),
            Map.entry("minecraft:sweet_berry_bush", "item.minecraft.sweet_berries"),
            Map.entry("minecraft:cave_vines", "item.minecraft.glow_berries"),
            Map.entry("minecraft:torchflower_crop", "item.minecraft.torchflower_seeds"),
            Map.entry("minecraft:pitcher_crop", "item.minecraft.pitcher_pod"));

    private static String langName(String id, Map<String, String> lang) {
        String itemKey = ITEM_NAMES.get(id);
        if (itemKey != null && lang.containsKey(itemKey)) return lang.get(itemKey);
        int c = id.indexOf(':');
        String n = lang.get("block." + id.substring(0, c) + "." + id.substring(c + 1).replace('/', '.'));
        return n == null || n.isBlank() ? null : n;
    }

    /**
     * The English names from each namespace's {@code assets/<ns>/lang/en_us.json}, read through the asset stack so
     * resource packs and mods override them as they do in game.
     */
    private static Map<String, String> readLang(AssetStack stack, Set<String> ids, ObjectMapper json) {
        Map<String, String> out = new HashMap<>();
        Set<String> namespaces = new java.util.TreeSet<>();
        for (String id : ids) namespaces.add(id.substring(0, id.indexOf(':')));
        for (String ns : namespaces) {
            stack.read("assets/" + ns + "/lang/en_us.json").ifPresent(bytes -> {
                try {
                    json.readTree(bytes).properties().forEach(e -> {
                        if (e.getValue().isTextual()) out.putIfAbsent(e.getKey(), e.getValue().asText());
                    });
                } catch (IOException | RuntimeException ignored) {
                    // unreadable lang file: those blocks keep their tidied ids
                }
            });
        }
        return out;
    }

    public McVersion version() {
        return version;
    }

    public BlockRegistry registry() {
        return registry;
    }

    public TextureAtlas atlas() {
        return atlas;
    }

    public AssetStack assetStack() {
        return stack;
    }

    private static final Pattern BANNER_PATTERN = Pattern.compile("assets/([^/]+)/textures/entity/banner/([^/]+)\\.png");
    private static final Pattern PAINTING = Pattern.compile("assets/([^/]+)/textures/painting/([^/]+)\\.png");
    private static final Pattern POT_TEXTURE = Pattern.compile("assets/([^/]+)/textures/entity/decorated_pot/([^/]+)\\.png");
    private static final Pattern SIGN_TEXTURE = Pattern.compile("assets/([^/]+)/textures/entity/signs/((?:hanging/)?[^/]+)\\.png");
    private final Map<String, BakedModel> bannerCache = new ConcurrentHashMap<>();

    /** Whether a block's look depends on its block entity data (banner patterns, pot sherds), which meshing then keeps. */
    public static boolean usesBlockEntity(BlockState state) {
        String p = state.path();
        return p.endsWith("banner") || p.equals("decorated_pot");
    }

    /** Whether a block can be shown open (chests, trapped and ender chests, shulker boxes). */
    public static boolean opens(BlockState state) {
        String p = state.path();
        return p.equals("chest") || p.equals("trapped_chest") || p.equals("ender_chest") || p.endsWith("shulker_box");
    }

    /**
     * The model for a block drawn from its block entity data and, for chests and shulker boxes, how far open it is
     * (0..1). Falls back to the plain model when neither applies.
     */
    public BakedModel blockEntityModel(BlockState state, io.blockdesigner.core.nbt.CompoundTag blockEntity, float open) {
        if (open > 0 && opens(state)) {
            // A few steps are enough for the animation; they're cached like any other model.
            float step = Math.round(Math.min(1, open) * 20) / 20f;
            String key = state + "|open=" + step;
            BakedModel m = bannerCache.get(key);
            if (m != null) return m;
            return bannerCache.computeIfAbsent(key, k -> EntityModels.bake(state, atlas, step).map(b -> withShape(state, b)).orElseGet(() -> model(state)));
        }
        if (state.path().equals("decorated_pot")) {
            List<String> sherds = EntityModels.sherds(blockEntity);
            if (sherds.isEmpty()) return model(state);
            String key = state + "|" + sherds;
            BakedModel m = bannerCache.get(key);
            if (m != null) return m;
            return bannerCache.computeIfAbsent(key, k -> EntityModels.decoratedPot(state, sherds, atlas).map(b -> withShape(state, b)).orElseGet(() -> model(state)));
        }
        return bannerModel(state, blockEntity);
    }

    /**
     * A banner with its patterns (from its block entity data); the plain model when it has none. Patterned banners are
     * cached by their state and pattern list.
     */
    public BakedModel bannerModel(BlockState state, io.blockdesigner.core.nbt.CompoundTag blockEntity) {
        List<EntityModels.Pattern> patterns = EntityModels.patterns(blockEntity);
        if (patterns.isEmpty()) return model(state);
        String key = state + "|" + patterns;
        BakedModel m = bannerCache.get(key);
        if (m != null) return m;
        return bannerCache.computeIfAbsent(key, k -> EntityModels.banner(state, state.path(), atlas, patterns)
                .map(b -> withShape(state, b)).orElseGet(() -> model(state)));
    }

    /**
     * An entity's quads in its own frame (the origin is where it stands): Minecraft's model for common mobs, a painting
     * or frame, or a box the size of its hitbox for everything else.
     */
    public List<io.blockdesigner.assets.model.BakedQuad> entityQuads(io.blockdesigner.core.model.StructureEntity entity) {
        return MobModels.bake(entity, atlas);
    }

    /** Baked model for a state; never null (falls back to approximations or a missing-texture cube). */
    public BakedModel model(BlockState state) {
        if (state.isAir()) return BakedModel.EMPTY;
        BakedModel m = cache.get(state);
        if (m != null) return m;
        return cache.computeIfAbsent(state, st -> withShape(st, bakeUncached(st)));
    }

    /** Minecraft's own hitbox where the model's element bounds would be wrong (plants, torches, crops…). */
    private static BakedModel withShape(BlockState state, BakedModel m) {
        List<float[]> boxes = BlockShapes.shape(state, m);
        return boxes == null ? m : new BakedModel(m.quads(), m.opaqueFaces(), m.ambientOcclusion(), m.missing(), boxes);
    }

    private BakedModel bakeUncached(BlockState state) {
        BlockStateDefinition def = definitions.get(state.name());
        if (def == null) return FallbackModels.missing(state, baker);
        List<ModelBaker.Placed> parts = new ArrayList<>();
        Optional<String> particle = Optional.empty();
        for (BlockStateDefinition.ModelRef ref : def.select(registry.complete(state))) {
            Optional<ModelLoader.ResolvedModel> rm = loader.resolve(ref.model());
            if (rm.isEmpty()) continue;
            if (particle.isEmpty()) particle = rm.get().particle();
            if (!rm.get().elements().isEmpty()) parts.add(new ModelBaker.Placed(rm.get(), ref.x(), ref.y(), ref.uvlock()));
        }
        if (!parts.isEmpty()) return baker.bake(state, parts);
        return FallbackModels.bake(state, particle, baker).orElseGet(() -> FallbackModels.missing(state, baker));
    }

    /** Average RGB of the block's most visible texture (top face if present), for previews and colour matching. */
    public int averageColor(BlockState state) {
        BakedModel m = model(state);
        int best = 0x808080;
        for (var q : m.quads()) {
            best = multiply(q.sprite().averageRgb(), q.tint());
            if (q.face() == Dir.UP) return best;
        }
        return best;
    }

    private static int multiply(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return r << 16 | g << 8 | bl;
    }

    @Override
    public void close() throws IOException {
        stack.close();
    }
}
