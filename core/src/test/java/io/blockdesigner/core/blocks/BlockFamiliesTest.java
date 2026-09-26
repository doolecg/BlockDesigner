package io.blockdesigner.core.blocks;

import io.blockdesigner.core.blocks.BlockFamily.Shape;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BlockFamiliesTest {
    /** A slice of the vanilla registry, enough to cover each naming pattern. */
    private static final Set<String> IDS = Stream.of(
            "oak_planks", "oak_stairs", "oak_slab", "oak_fence", "oak_fence_gate", "oak_door", "oak_trapdoor", "oak_button",
            "oak_pressure_plate", "oak_sign", "oak_wall_sign", "oak_hanging_sign", "oak_wall_hanging_sign", "oak_log", "oak_wood",
            "stripped_oak_log", "stripped_oak_wood",
            "spruce_planks", "spruce_stairs", "spruce_slab", "spruce_fence", "spruce_log",
            "crimson_planks", "crimson_stairs", "crimson_stem", "crimson_hyphae", "stripped_crimson_stem",
            "stone", "stone_stairs", "stone_slab", "stone_button", "stone_pressure_plate", "smooth_stone", "smooth_stone_slab",
            "stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall", "cracked_stone_bricks", "chiseled_stone_bricks",
            "mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall",
            "cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall",
            "mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall",
            "bricks", "brick_stairs", "brick_slab", "brick_wall",
            "nether_bricks", "nether_brick_stairs", "nether_brick_slab", "nether_brick_wall", "nether_brick_fence", "cracked_nether_bricks",
            "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall", "cracked_deepslate_tiles",
            "quartz_block", "quartz_stairs", "quartz_slab", "quartz_bricks",
            "copper_block", "exposed_copper", "cut_copper", "cut_copper_stairs", "exposed_cut_copper", "exposed_cut_copper_stairs",
            "waxed_cut_copper", "waxed_weathered_cut_copper",
            "iron_block", "iron_door", "iron_trapdoor",
            "dirt", "mushroom_stem", "heavy_weighted_pressure_plate").map(s -> "minecraft:" + s).collect(Collectors.toSet());

    private final BlockFamilies families = new BlockFamilies(IDS::contains);

    @Test
    void woodFamilyHasEveryShape() {
        BlockFamily oak = families.family("minecraft:oak_stairs").orElseThrow();
        assertThat(oak.id()).isEqualTo("minecraft:oak");
        assertThat(oak.get(Shape.BLOCK)).contains("minecraft:oak_planks");
        assertThat(oak.get(Shape.WALL_SIGN)).contains("minecraft:oak_wall_sign");
        assertThat(oak.get(Shape.STRIPPED_LOG)).contains("minecraft:stripped_oak_log");
        assertThat(oak.members()).hasSize(Shape.values().length - 1); // every shape but a wall
        assertThat(families.family("minecraft:oak_planks")).contains(oak);
        assertThat(families.family("minecraft:stripped_oak_wood")).contains(oak);
    }

    @Test
    void netherWoodUsesStemAndHyphae() {
        BlockFamily crimson = families.family("minecraft:crimson_hyphae").orElseThrow();
        assertThat(crimson.get(Shape.LOG)).contains("minecraft:crimson_stem");
        assertThat(crimson.get(Shape.STRIPPED_LOG)).contains("minecraft:stripped_crimson_stem");
        assertThat(crimson.get(Shape.BLOCK)).contains("minecraft:crimson_planks");
    }

    @Test
    void brickAndTileFamiliesUseThePluralBlock() {
        BlockFamily sb = families.family("minecraft:stone_bricks").orElseThrow();
        assertThat(sb.id()).isEqualTo("minecraft:stone_brick");
        assertThat(sb.get(Shape.STAIRS)).contains("minecraft:stone_brick_stairs");
        assertThat(families.family("minecraft:stone_brick_wall").orElseThrow().get(Shape.BLOCK)).contains("minecraft:stone_bricks");
        assertThat(families.family("minecraft:bricks").orElseThrow().get(Shape.SLAB)).contains("minecraft:brick_slab");
        assertThat(families.family("minecraft:nether_brick_fence").orElseThrow().get(Shape.BLOCK)).contains("minecraft:nether_bricks");
        assertThat(families.family("minecraft:deepslate_tiles").orElseThrow().get(Shape.WALL)).contains("minecraft:deepslate_tile_wall");
        // Stone and stone bricks are different materials.
        assertThat(families.family("minecraft:stone").orElseThrow().members().values()).doesNotContain("minecraft:stone_brick_stairs");
    }

    @Test
    void blockSuffixAndPlainBlocks() {
        assertThat(families.family("minecraft:quartz_block").orElseThrow().get(Shape.STAIRS)).contains("minecraft:quartz_stairs");
        assertThat(families.family("minecraft:iron_door").orElseThrow().get(Shape.BLOCK)).contains("minecraft:iron_block");
        assertThat(families.family("minecraft:smooth_stone_slab").orElseThrow().get(Shape.BLOCK)).contains("minecraft:smooth_stone");
        assertThat(families.family("minecraft:dirt")).isEmpty();
        assertThat(families.family("minecraft:mushroom_stem")).isEmpty();
        assertThat(families.family("minecraft:heavy_weighted_pressure_plate")).isEmpty();
        assertThat(families.family("minecraft:quartz_bricks")).isEmpty();
    }

    @Test
    void sameShapeInAnotherFamily() {
        BlockFamily spruce = families.family("minecraft:spruce_planks").orElseThrow();
        assertThat(families.sameShape("minecraft:oak_stairs", spruce)).contains("minecraft:spruce_stairs");
        assertThat(families.sameShape("minecraft:oak_door", spruce)).isEmpty();
        assertThat(families.sameShape("oak_fence", spruce)).contains("minecraft:spruce_fence");
    }

    @Test
    void modifiersAddAndRemoveVariants() {
        assertThat(families.withModifier("minecraft:cobblestone", "mossy")).contains("minecraft:mossy_cobblestone");
        assertThat(families.withModifier("minecraft:stone_brick_stairs", "mossy")).contains("minecraft:mossy_stone_brick_stairs");
        assertThat(families.withModifier("minecraft:stone_brick_stairs", "cracked")).isEmpty();
        assertThat(families.withModifier("minecraft:stone_bricks", "cracked")).contains("minecraft:cracked_stone_bricks");
        assertThat(families.withModifier("minecraft:mossy_cobblestone", "mossy")).contains("minecraft:mossy_cobblestone");
        assertThat(families.withModifier("minecraft:copper_block", "exposed")).contains("minecraft:exposed_copper");
        assertThat(families.withModifier("minecraft:waxed_cut_copper", "weathered")).contains("minecraft:waxed_weathered_cut_copper");
        assertThat(families.withoutModifier("minecraft:mossy_stone_brick_wall", "mossy")).contains("minecraft:stone_brick_wall");
        assertThat(families.withoutModifier("minecraft:exposed_copper", "exposed")).contains("minecraft:copper_block");
        assertThat(families.withoutModifier("minecraft:stone", "mossy")).isEmpty();
        assertThat(families.modifiers("minecraft:stone_bricks")).containsExactly("cracked", "mossy", "chiseled");
    }

    @Test
    void shapeFromIdAlone() {
        assertThat(BlockFamilies.shapeOf("minecraft:birch_wall_hanging_sign")).isEqualTo(Shape.WALL_HANGING_SIGN);
        assertThat(BlockFamilies.shapeOf("minecraft:stripped_birch_log")).isEqualTo(Shape.STRIPPED_LOG);
        assertThat(BlockFamilies.shapeOf("minecraft:andesite_wall")).isEqualTo(Shape.WALL);
        assertThat(BlockFamilies.shapeOf("minecraft:glass")).isEqualTo(Shape.BLOCK);
    }
}
