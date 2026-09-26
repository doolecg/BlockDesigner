package io.blockdesigner.app.plugins;

import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Without Minecraft's assets the catalog works from the vanilla creative-menu list. */
class AppBlockCatalogTest {
    private final AppBlockCatalog catalog = new AppBlockCatalog(() -> null);

    @Test
    void familiesFromTheVanillaList() {
        BlockFamily oak = catalog.family(BlockState.of("oak_stairs")).orElseThrow();
        assertThat(oak.get(BlockFamily.Shape.BLOCK)).contains("minecraft:oak_planks");
        assertThat(oak.get(BlockFamily.Shape.FENCE_GATE)).contains("minecraft:oak_fence_gate");
        BlockFamily spruce = catalog.family(BlockState.of("spruce_planks")).orElseThrow();
        BlockState stairs = BlockState.parse("oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
        assertThat(catalog.sameShape(stairs, spruce)).contains(stairs.withName("minecraft:spruce_stairs"));
    }

    @Test
    void variantsKeepProperties() {
        BlockState st = BlockState.parse("stone_brick_stairs[facing=north,half=bottom]");
        assertThat(catalog.variant(st, "mossy")).contains(st.withName("minecraft:mossy_stone_brick_stairs"));
        assertThat(catalog.variant(st, "cracked")).isEmpty();
        assertThat(catalog.variant(BlockState.of("stone_bricks"), "cracked")).contains(BlockState.of("cracked_stone_bricks"));
        assertThat(catalog.withoutVariant(BlockState.of("mossy_cobblestone"), "mossy")).contains(BlockState.of("cobblestone"));
    }

    @Test
    void withIdKeepsPropertiesOnlyForTheSameShapeWithoutARegistry() {
        BlockState st = BlockState.parse("oak_stairs[facing=east]");
        assertThat(catalog.withId(st, "birch_stairs")).isEqualTo(BlockState.parse("birch_stairs[facing=east]"));
        assertThat(catalog.withId(st, "stone")).isEqualTo(BlockState.of("stone"));
        assertThat(catalog.withId(st, "minecraft:oak_stairs")).isSameAs(st);
    }

    @Test
    void resolveAndNames() {
        assertThat(catalog.resolve("oak_log[axis=x]")).isEqualTo(BlockState.parse("oak_log[axis=x]"));
        assertThatThrownBy(() -> catalog.resolve(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.exists("mossy_cobblestone")).isTrue();
        assertThat(catalog.exists("minecraft:no_such_block")).isFalse();
        assertThat(catalog.displayName(BlockState.of("mossy_stone_bricks"))).isEqualTo("Mossy Stone Bricks");
        assertThat(catalog.averageColor(BlockState.of("stone")) >>> 24).isEqualTo(0xFF);
    }

    @Test
    void optionStoreRemembersValuesPerFeature() {
        Map<String, Map<String, String>> settings = new LinkedHashMap<>();
        OptionStore store = new OptionStore(settings);
        Options o = Options.builder().decimal("amount", "Amount", 0.3, 0, 1).block("with", "With", BlockState.of("stone")).build();
        String key = OptionStore.key("palette-tools", "transform", "weather");
        assertThat(store.load(key, o, catalog)).isEqualTo(o.defaults());
        OptionValues v = o.defaults().with("amount", 0.8).with("with", BlockState.parse("oak_stairs[facing=west]"));
        store.save(key, v);
        store.setNumber(key, "seed", 42);
        assertThat(settings).containsKey("palette-tools/transform/weather");
        // A fresh store over the same (saved and reloaded) settings map.
        OptionStore again = new OptionStore(new LinkedHashMap<>(settings));
        assertThat(again.load(key, o, catalog)).isEqualTo(v);
        assertThat(again.number(key, "seed", 0)).isEqualTo(42);
        assertThat(again.number("other", "seed", 7)).isEqualTo(7);
        // An option the plugin dropped is ignored, a new one gets its default.
        Options changed = Options.builder().decimal("amount", "Amount", 0.3, 0, 1).toggle("moss", "Moss", true).build();
        assertThat(again.load(key, changed, catalog)).isEqualTo(changed.defaults().with("amount", 0.8));
        assertThat(List.copyOf(settings.keySet())).containsExactly(key, key + "#");
    }
}
