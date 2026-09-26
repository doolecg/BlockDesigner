package com.example.palette;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.TransformContext;

import java.util.List;

/**
 * Repaints the blocks with a list of blocks from one end to the other (dark stone at the bottom of a wall fading into
 * light stone at the top), with a blend so the bands don't show hard lines. Keeps each block's shape: a stair becomes a
 * stair of the new material when there is one.
 */
final class GradientTransform implements PluginTransform {

    @Override
    public String id() {
        return "gradient";
    }

    @Override
    public String name() {
        return "Gradient";
    }

    @Override
    public String description() {
        return "Fades from the first block to the last along an axis, with a blend between bands.";
    }

    @Override
    public String icon() {
        return "M2 2 H14 V14 H2 Z M2 6 H14 M2 10 H14 M5 10 L5 14 M9 6 V10 M11 10 V14";
    }

    @Override
    public Options options() {
        return Options.builder()
                .blockList("blocks", "Blocks, first to last", List.of(BlockState.of("deepslate_bricks"), BlockState.of("stone_bricks"),
                        BlockState.of("andesite"), BlockState.of("diorite")))
                .choice("axis", "Along", List.of("up", "down", "east", "west", "south", "north"), "up")
                .decimal("blend", "Blend", 0.5, 0, 1)
                .build();
    }

    @Override
    public boolean randomized() {
        return true;
    }

    @Override
    public void apply(TransformContext c) {
        List<BlockState> blocks = c.options().blockList("blocks").blocks();
        String axis = c.options().choice("axis");
        double blend = c.options().decimal("blend");
        Box b = c.bounds();
        for (BlockPos p : c.solidBlocks()) {
            double t = switch (axis) {
                case "up" -> fraction(p.y(), b.minY(), b.maxY());
                case "down" -> 1 - fraction(p.y(), b.minY(), b.maxY());
                case "east" -> fraction(p.x(), b.minX(), b.maxX());
                case "west" -> 1 - fraction(p.x(), b.minX(), b.maxX());
                case "south" -> fraction(p.z(), b.minZ(), b.maxZ());
                default -> 1 - fraction(p.z(), b.minZ(), b.maxZ());
            };
            // Jitter by up to half a band either way, so neighbouring bands mix.
            double band = t * blocks.size() + (c.random().nextDouble() - 0.5) * blend;
            BlockState pick = blocks.get(Math.clamp((int) Math.floor(band), 0, blocks.size() - 1));
            BlockState st = c.world().get(p);
            // Keep the shape: a stair stays a stair of the new material if the game has one, else it becomes the full block.
            BlockState out = c.blocks().family(pick).flatMap(f -> c.blocks().sameShape(st, f)).orElse(pick);
            c.world().set(p, out);
        }
    }

    private static double fraction(int v, int min, int max) {
        return max == min ? 0.5 : (v - min) / (double) (max - min + 1) + 0.5 / (max - min + 1);
    }
}
