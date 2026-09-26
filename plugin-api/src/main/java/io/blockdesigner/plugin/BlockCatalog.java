package io.blockdesigner.plugin;

import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockState;

import java.util.Optional;

/**
 * What BlockDesigner knows about blocks: the loaded Minecraft version's registry (plus mods), block families and
 * variants, and colours. Get it from {@link PluginContext#blocks()}; transforms get it from their context too.
 *
 * <p>Families are worked out from block ids ({@code oak_planks}, {@code oak_stairs}, {@code stone_bricks},
 * {@code stone_brick_wall}, {@code mossy_} and {@code cracked_} variants…) and checked against the registry, so they
 * cover modded blocks named the vanilla way. Before Minecraft's assets are loaded, the vanilla creative-menu blocks
 * stand in for the registry.
 */
public interface BlockCatalog extends PluginCommand.BlockResolver {

    /**
     * Turns user text like {@code stone}, {@code minecraft:oak_stairs[facing=east]} or {@code create:andesite_casing}
     * into a complete block state (missing properties get the block's defaults).
     *
     * @throws IllegalArgumentException when the text is malformed or the block doesn't exist
     */
    @Override
    BlockState resolve(String text);

    /** Whether a block id (e.g. {@code minecraft:mossy_cobblestone}) exists in the loaded registry. */
    boolean exists(String blockId);

    /** The family a block belongs to (oak_stairs → oak: planks, stairs, slab, fence, door…), if it comes in several shapes. */
    Optional<BlockFamily> family(BlockState block);

    /**
     * The block of the same shape in another family, keeping its properties: oak_stairs[facing=east] with the spruce
     * family is spruce_stairs[facing=east]. Empty when the other family doesn't have that shape.
     */
    Optional<BlockState> sameShape(BlockState block, BlockFamily other);

    /**
     * A material variant of the block, keeping its shape and properties: stone_brick_stairs + {@code "mossy"} →
     * mossy_stone_brick_stairs, stone_bricks + {@code "cracked"} → cracked_stone_bricks, cut_copper +
     * {@code "exposed"} → exposed_cut_copper. See {@link io.blockdesigner.core.blocks.BlockFamilies#MODIFIERS}.
     * Empty when the game has no such block. A block that already is the variant is returned unchanged.
     */
    Optional<BlockState> variant(BlockState block, String modifier);

    /** The reverse of {@link #variant}: mossy_cobblestone without {@code "mossy"} is cobblestone. */
    Optional<BlockState> withoutVariant(BlockState block, String modifier);

    /**
     * {@code block} turned into {@code newId}, copying the properties the new block also has (facing, half,
     * waterlogged…); the rest get the new block's defaults.
     */
    BlockState withId(BlockState block, String newId);

    /** Average colour (0xAARRGGBB, opaque) of the block's texture (its top face when it has one), for pixel art and maps. */
    int averageColor(BlockState block);

    /** The block's display name, e.g. "Mossy Stone Brick Stairs". */
    String displayName(BlockState block);
}
