package io.blockdesigner.assets;

import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.ModelBaker;
import io.blockdesigner.assets.model.ModelBaker.BoxSpec;
import io.blockdesigner.assets.model.RenderLayer;
import io.blockdesigner.core.model.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Approximate shapes for blocks the game draws with code instead of JSON models (chests, beds, signs, banners, heads,
 * fluids...). They use the block's particle texture, or a colour-matched wool texture.
 */
final class FallbackModels {
    static final Set<String> COLORS = Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private FallbackModels() {
    }

    /** Extra textures fallbacks may need, so they get stitched into the atlas. */
    static List<String> extraTextures() {
        List<String> out = new java.util.ArrayList<>(List.of("minecraft:block/water_still", "minecraft:block/lava_still",
                "minecraft:block/oak_planks", "minecraft:block/structure_void", "minecraft:block/barrier"));
        for (String c : COLORS) out.add("minecraft:block/" + c + "_wool");
        out.addAll(EntityModels.textures());
        out.addAll(MobModels.textures());
        return out;
    }

    static Optional<BakedModel> bake(BlockState state, Optional<String> particle, ModelBaker baker) {
        String path = state.path();
        String tex = particle.orElse(null);
        int yRot = facingRotation(state.get("facing"));

        if (path.equals("water") || path.equals("bubble_column")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(0, 0, 0, 16, 14, 16, "minecraft:block/water_still", 0, RenderLayer.TRANSLUCENT)), 0, false));
        }
        if (path.equals("lava")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(0, 0, 0, 16, 14, 16, "minecraft:block/lava_still", -1, RenderLayer.SOLID)), 0, false));
        }
        if (path.equals("structure_void")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(5, 5, 5, 11, 11, 11, "minecraft:block/structure_void", -1, RenderLayer.CUTOUT)), 0, false));
        }
        // Chests, beds, signs and heads: their real models, built like the game's renderers (the boxes below are
        // only for blocks without one, such as modded signs).
        Optional<BakedModel> real = EntityModels.bake(state, baker.atlas());
        if (real.isPresent()) return real;
        if (path.endsWith("chest")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(1, 0, 1, 15, 14, 15, tex, -1, null)), yRot, false));
        }
        if (path.endsWith("_bed")) {
            String color = path.substring(0, path.length() - 4);
            String wool = COLORS.contains(color) ? "minecraft:block/" + color + "_wool" : tex;
            return Optional.of(baker.bakeBoxes(state, List.of(box(0, 3, 0, 16, 9, 16, wool, -1, null),
                    box(0, 0, 0, 3, 3, 3, "minecraft:block/oak_planks", -1, null), box(13, 0, 0, 16, 3, 3, "minecraft:block/oak_planks", -1, null),
                    box(0, 0, 13, 3, 3, 16, "minecraft:block/oak_planks", -1, null), box(13, 0, 13, 16, 3, 16, "minecraft:block/oak_planks", -1, null)), yRot, false));
        }
        if (path.endsWith("_wall_hanging_sign") || path.endsWith("_hanging_sign")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(1, 0, 7, 15, 10, 9, tex, -1, null)), yRot, false));
        }
        if (path.endsWith("_wall_sign")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(0, 4.5f, 14, 16, 12.5f, 16, tex, -1, null)), yRot, false));
        }
        if (path.endsWith("_sign")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(7.25f, 0, 7.25f, 8.75f, 9, 8.75f, tex, -1, null),
                    box(0, 9, 7.25f, 16, 17, 8.75f, tex, -1, null)), rotation16(state.get("rotation")), false));
        }
        if (path.endsWith("_wall_banner") || path.endsWith("_banner")) {
            boolean wall = path.endsWith("_wall_banner");
            String color = path.substring(0, path.length() - (wall ? 12 : 7));
            String wool = COLORS.contains(color) ? "minecraft:block/" + color + "_wool" : tex;
            if (wall) return Optional.of(baker.bakeBoxes(state, List.of(box(1, -14, 14, 15, 14, 16, wool, -1, null)), yRot, false));
            return Optional.of(baker.bakeBoxes(state, List.of(box(7, 0, 7, 9, 28, 9, "minecraft:block/oak_planks", -1, null),
                    box(1, 4, 6, 15, 28, 7, wool, -1, null)), rotation16(state.get("rotation")), false));
        }
        if (path.endsWith("_wall_head") || path.endsWith("_wall_skull")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(4, 4, 8, 12, 12, 16, tex, -1, null)), yRot, false));
        }
        if (path.endsWith("_head") || path.endsWith("_skull")) {
            return Optional.of(baker.bakeBoxes(state, List.of(box(4, 0, 4, 12, 8, 12, tex, -1, null)), 0, false));
        }
        if (path.contains("shulker_box") || path.equals("decorated_pot") || path.equals("conduit") || path.equals("copper_golem_statue")) {
            float inset = path.contains("shulker_box") ? 0 : 3;
            return Optional.of(baker.bakeBoxes(state, List.of(box(inset, 0, inset, 16 - inset, 16 - inset / 2, 16 - inset, tex, -1, null)), 0, false));
        }
        if (tex != null) {
            // Something with only a particle texture: show a slightly inset cube so it's visible and clickable.
            return Optional.of(baker.bakeBoxes(state, List.of(box(1, 0, 1, 15, 15, 15, tex, -1, null)), 0, false));
        }
        return Optional.empty();
    }

    /** Missing-model placeholder: a full checkered cube. */
    static BakedModel missing(BlockState state, ModelBaker baker) {
        return baker.bakeBoxes(state, List.of(box(0, 0, 0, 16, 16, 16, TextureAtlas.MISSING, -1, RenderLayer.SOLID)), 0, true);
    }

    private static BoxSpec box(float x0, float y0, float z0, float x1, float y1, float z1, String tex, int tint, RenderLayer layer) {
        return new BoxSpec(new float[]{x0, y0, z0}, new float[]{x1, y1, z1}, tex, tint, layer);
    }

    /** Model y-rotation for a facing, with north as the unrotated direction. */
    static int facingRotation(String facing) {
        if (facing == null) return 0;
        return switch (facing) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    /** Standing signs/banners: rotation 0 faces south; snap to the nearest quarter turn. */
    static int rotation16(String rotation) {
        int r;
        try {
            r = rotation == null ? 0 : Integer.parseInt(rotation);
        } catch (NumberFormatException e) {
            r = 0;
        }
        int quarter = Math.round(r / 4f) & 3; // 0 south, 1 west, 2 north, 3 east
        return switch (quarter) {
            case 0 -> 180;
            case 1 -> 270;
            case 2 -> 0;
            default -> 90;
        };
    }
}
