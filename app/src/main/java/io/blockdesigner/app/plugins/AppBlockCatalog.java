package io.blockdesigner.app.plugins;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.BlockRegistry;
import io.blockdesigner.assets.CreativeOrder;
import io.blockdesigner.core.blocks.BlockFamilies;
import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.BlockCatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * The {@link BlockCatalog} plugins get: the loaded assets' registry when there is one, otherwise the vanilla blocks of
 * the creative menu, so families and variants already work before a Minecraft jar is chosen (and in tests).
 */
public final class AppBlockCatalog implements BlockCatalog {
    private final Supplier<BlockAssets> assets;

    /** @param assets the currently loaded assets, or null while none are */
    public AppBlockCatalog(Supplier<BlockAssets> assets) {
        this.assets = assets;
    }

    private BlockRegistry registry() {
        BlockAssets a = assets.get();
        return a == null ? null : a.registry();
    }

    private BlockFamilies families() {
        return new BlockFamilies(this::exists);
    }

    @Override
    public BlockState resolve(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("No block given");
        String t = text.strip();
        BlockState st;
        try {
            st = BlockState.parse(t.contains(":") ? t : "minecraft:" + t);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a block: " + text);
        }
        BlockRegistry r = registry();
        if (r == null) return st;
        if (!r.contains(st.name())) throw new IllegalArgumentException("Unknown block: " + text);
        return r.complete(st);
    }

    @Override
    public boolean exists(String blockId) {
        String id = BlockState.normalizeId(blockId);
        BlockRegistry r = registry();
        return r != null ? r.contains(id) : CreativeOrder.index(id) >= 0;
    }

    @Override
    public Optional<BlockFamily> family(BlockState block) {
        return families().family(block.name());
    }

    @Override
    public Optional<BlockState> sameShape(BlockState block, BlockFamily other) {
        return families().sameShape(block.name(), other).map(id -> withId(block, id));
    }

    @Override
    public Optional<BlockState> variant(BlockState block, String modifier) {
        return families().withModifier(block.name(), modifier).map(id -> withId(block, id));
    }

    @Override
    public Optional<BlockState> withoutVariant(BlockState block, String modifier) {
        return families().withoutModifier(block.name(), modifier).map(id -> withId(block, id));
    }

    @Override
    public BlockState withId(BlockState block, String newId) {
        String id = BlockState.normalizeId(newId);
        if (id.equals(block.name())) return block;
        BlockRegistry r = registry();
        var info = r == null ? null : r.get(id).orElse(null);
        if (info == null) {
            // Without the registry we can't know the new block's properties: keep them all when it is the same shape
            // (stairs to stairs), none when the shape changes.
            boolean sameShape = BlockFamilies.shapeOf(block.name()) == BlockFamilies.shapeOf(id);
            return sameShape ? BlockState.of(id, block.properties()) : BlockState.of(id);
        }
        Map<String, String> props = new TreeMap<>(info.defaultState().properties());
        block.properties().forEach((k, v) -> {
            List<String> allowed = info.properties().get(k);
            if (allowed != null && allowed.contains(v)) props.put(k, v);
        });
        return BlockState.of(id, props);
    }

    @Override
    public int averageColor(BlockState block) {
        BlockAssets a = assets.get();
        if (a == null) return 0xFF808080;
        try {
            return 0xFF000000 | a.averageColor(block);
        } catch (RuntimeException e) {
            return 0xFF808080;
        }
    }

    @Override
    public String displayName(BlockState block) {
        BlockRegistry r = registry();
        return r != null ? r.displayName(block.name()) : BlockRegistry.pretty(block.path());
    }
}
