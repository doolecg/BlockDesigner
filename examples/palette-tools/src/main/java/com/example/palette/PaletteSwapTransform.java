package com.example.palette;

import io.blockdesigner.core.blocks.BlockFamily;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.TransformContext;

import java.util.Optional;

/**
 * Swaps one material for another: oak → spruce turns oak planks, stairs, slabs, fences and doors into their spruce
 * counterparts, each keeping its facing, half and shape.
 */
final class PaletteSwapTransform implements PluginTransform {

    @Override
    public String id() {
        return "palette_swap";
    }

    @Override
    public String name() {
        return "Palette swap";
    }

    @Override
    public String description() {
        return "Replaces one material with another in every shape (planks, stairs, slabs, fences…), keeping their facing.";
    }

    @Override
    public String icon() {
        return "M2 5 H11 M8 2 L11 5 L8 8 M14 11 H5 M8 8 L5 11 L8 14";
    }

    @Override
    public Options options() {
        return Options.builder()
                .block("from", "Replace", BlockState.of("oak_planks"))
                .block("to", "With", BlockState.of("spruce_planks"))
                .toggle("family", "Every shape of the material (stairs, slabs, fences…)", true)
                .build();
    }

    @Override
    public void apply(TransformContext c) {
        BlockState from = c.options().block("from"), to = c.options().block("to");
        boolean wholeFamily = c.options().toggle("family");
        Optional<BlockFamily> fromFamily = wholeFamily ? c.blocks().family(from) : Optional.empty();
        Optional<BlockFamily> toFamily = wholeFamily ? c.blocks().family(to) : Optional.empty();
        for (BlockPos p : c.solidBlocks()) {
            BlockState st = c.world().get(p);
            if (st.name().equals(from.name())) {
                c.world().set(p, c.blocks().withId(st, to.name()));
            } else if (fromFamily.isPresent() && toFamily.isPresent() && fromFamily.get().contains(st.name())) {
                c.blocks().sameShape(st, toFamily.get()).ifPresent(v -> c.world().set(p, v));
            }
        }
    }
}
