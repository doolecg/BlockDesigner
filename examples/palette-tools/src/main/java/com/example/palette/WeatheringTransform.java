package com.example.palette;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.TransformContext;

import java.util.Optional;

/**
 * Ages stone: a share of the blocks turns cracked or mossy (stone bricks, cobblestone, deepslate tiles, their stairs,
 * slabs and walls where the game has those variants). Blocks without such a variant are left alone.
 */
final class WeatheringTransform implements PluginTransform {

    @Override
    public String id() {
        return "weather";
    }

    @Override
    public String name() {
        return "Weathering";
    }

    @Override
    public String description() {
        return "Turns a share of the stone cracked or mossy, keeping stairs and slabs facing the same way.";
    }

    @Override
    public String icon() {
        // A brick wall with a crack running down it.
        return "M2 3 H14 V13 H2 Z M2 8 H14 M6 3 V8 M10 8 V13 M9 3 L8 6 L10 9 L8.5 13";
    }

    @Override
    public Options options() {
        return Options.builder()
                .decimal("amount", "Weathered", 0.35, 0, 1)
                .decimal("moss", "Of those, mossy", 0.5, 0, 1)
                .toggle("bottomUp", "More moss near the ground", true)
                .build();
    }

    @Override
    public boolean randomized() {
        return true;
    }

    @Override
    public void apply(TransformContext c) {
        double amount = c.options().decimal("amount"), moss = c.options().decimal("moss");
        boolean bottomUp = c.options().toggle("bottomUp");
        int minY = c.bounds().minY(), height = c.bounds().sizeY();
        for (BlockPos p : c.solidBlocks()) {
            // Draw both numbers for every block, used or not, so each block's fate depends only on the seed.
            double age = c.random().nextDouble(), kind = c.random().nextDouble();
            if (age >= amount) continue;
            double mossChance = moss;
            if (bottomUp && height > 1) mossChance = Math.min(1, moss * 2 * (1 - (p.y() - minY) / (double) (height - 1)));
            BlockState st = c.world().get(p);
            String first = kind < mossChance ? "mossy" : "cracked", second = first.equals("mossy") ? "cracked" : "mossy";
            Optional<BlockState> aged = c.blocks().variant(st, first).or(() -> c.blocks().variant(st, second));
            aged.ifPresent(v -> c.world().set(p, v));
        }
    }
}
