package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.place.BlockPlacement;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Read access to the loaded Minecraft assets (game jar, mods and resource packs, in the game's priority order): block
 * models as textured quads, the texture atlas and raw texture files. For exporters that write meshes (OBJ, glTF) or
 * images. Everything here is plain data (no AWT or JavaFX types) and safe to use from an exporter's background
 * thread. Get it from {@link PluginContext#assets()} or {@link PluginExporter.Request#assets()}. Since API 2.
 */
public interface AssetAccess {

    /** No assets: every lookup comes back empty. */
    AssetAccess NONE = new AssetAccess() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public List<Quad> quads(BlockState block, Predicate<BlockPlacement.Dir> culled) {
            return List.of();
        }

        @Override
        public Optional<Atlas> atlas() {
            return Optional.empty();
        }

        @Override
        public Optional<byte[]> texture(String resourceId) {
            return Optional.empty();
        }
    };

    /**
     * One textured face of a block model, in block space (0..1 on each axis).
     *
     * @param positions 4 vertices × x, y, z, counter-clockwise seen from the front
     * @param uvs       4 vertices × u, v in the {@link #atlas() atlas} (0..1, v down)
     * @param face      the side the face looks towards (nearest axis)
     * @param cull      the neighbour that hides this face when it is a full block; empty for faces never hidden
     * @param tint      RGB multiplier for grass, leaves, water… ({@code 0xFFFFFF} for none)
     * @param texture   the texture's id, e.g. {@code minecraft:block/oak_planks}
     */
    record Quad(float[] positions, float[] uvs, BlockPlacement.Dir face, Optional<BlockPlacement.Dir> cull, int tint, String texture) {
    }

    /**
     * Every texture used by blocks, packed into one image.
     *
     * @param argb pixels, row by row, {@code 0xAARRGGBB}
     * @param png  the same image as PNG file bytes
     */
    record Atlas(int width, int height, int[] argb, byte[] png) {
    }

    /** Whether Minecraft's assets are loaded (the user picked a game jar). */
    boolean available();

    /**
     * The block's model as quads, leaving out faces whose {@code cull} side {@code culled} says is covered (pass
     * {@code side -> false} for the whole model).
     */
    List<Quad> quads(BlockState block, Predicate<BlockPlacement.Dir> culled);

    /** The block texture atlas the quads' uvs point into; empty while no assets are loaded. */
    Optional<Atlas> atlas();

    /**
     * A raw resource file's bytes by resource id, e.g. {@code minecraft:block/stone} (a texture, read from
     * {@code assets/minecraft/textures/block/stone.png}) or a full path such as
     * {@code assets/minecraft/models/block/stone.json}.
     */
    Optional<byte[]> texture(String resourceId);
}
