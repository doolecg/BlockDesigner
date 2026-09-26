package com.example.palette;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.plugin.BlockPattern;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginTool;
import io.blockdesigner.plugin.ToolContext;
import io.blockdesigner.plugin.ToolEvent;
import io.blockdesigner.plugin.ToolHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Drag out a straight wall: press on a block, drag along the ground and release. The wall shows as ghosts while
 * dragging; the wheel changes its height, Esc cancels. Built from a block mix (70% stone bricks, 30% mossy by default)
 * or the held block, as one undo step.
 */
final class WallTool implements PluginTool {

    @Override
    public String id() {
        return "wall";
    }

    @Override
    public String name() {
        return "Wall";
    }

    @Override
    public String description() {
        return "drag to build a straight wall · wheel sets the height · Esc cancels";
    }

    @Override
    public String icon() {
        return "M2 13 H14 V7 H2 Z M2 10 H14 M5 7 V10 M9 7 V10 M12 7 V10 M7 10 V13 M11 10 V13 M4 10 V13 M4 5 V3 M8 5 V3 M12 5 V3";
    }

    @Override
    public String defaultKey() {
        return "Shift+K";
    }

    @Override
    public Options options() {
        return Options.builder()
                .integer("height", "Height", 3, 1, 64)
                .blockList("blocks", "Blocks", BlockPattern.parse("70%stone_bricks,30%mossy_stone_bricks", BlockState::parse))
                .toggle("hand", "Use the held block instead", false)
                .build();
    }

    @Override
    public ToolHandler activate(ToolContext ctx) {
        return new ToolHandler() {
            private BlockPos start, end;
            private int extraHeight;

            @Override
            public void press(ToolEvent e) {
                if (e.button() != ToolEvent.Button.PRIMARY) return;
                e.hit().ifPresent(h -> {
                    start = end = h.adjacent();
                    extraHeight = 0;
                    show();
                });
            }

            @Override
            public void drag(ToolEvent e) {
                if (start == null) return;
                e.hit().ifPresent(h -> {
                    end = h.adjacent();
                    show();
                });
            }

            @Override
            public void release(ToolEvent e) {
                if (start == null) return;
                Map<BlockPos, BlockState> wall = cells();
                start = null;
                ctx.preview().clear();
                try (ToolContext.Stroke s = ctx.beginStroke("Build wall")) {
                    wall.forEach((p, st) -> s.world().set(p, st));
                }
            }

            @Override
            public boolean scroll(ToolEvent e, double delta) {
                if (start == null) return false;
                extraHeight = Math.max(1 - height0(), extraHeight + (int) Math.signum(delta));
                show();
                return true;
            }

            @Override
            public boolean key(String key) {
                if (!key.equals("Esc") || start == null) return false;
                start = null;
                ctx.preview().clear();
                return true;
            }

            @Override
            public void deactivate() {
                start = null;
            }

            private int height0() {
                return ctx.options().integer("height");
            }

            private void show() {
                Map<BlockPos, BlockState> wall = cells();
                ctx.preview().ghost(wall);
                ctx.preview().outline(Box.of(start, end.add(0, height0() + extraHeight - 1, 0)));
            }

            /** A one-block-thick wall along the longer horizontal axis from start to end. */
            private Map<BlockPos, BlockState> cells() {
                int dx = end.x() - start.x(), dz = end.z() - start.z();
                boolean alongX = Math.abs(dx) >= Math.abs(dz);
                int len = Math.abs(alongX ? dx : dz), step = Integer.signum(alongX ? dx : dz);
                int height = height0() + extraHeight;
                BlockPattern pattern = ctx.options().blockList("blocks");
                boolean hand = ctx.options().toggle("hand") && ctx.hand().isPresent();
                // Seeded by the start, so the preview and the built wall pick the same blocks.
                Random random = new Random(start.pack());
                Map<BlockPos, BlockState> out = new LinkedHashMap<>();
                for (int i = 0; i <= len; i++) {
                    for (int y = 0; y < height; y++) {
                        BlockPos p = alongX ? start.add(i * step, y, 0) : start.add(0, y, i * step);
                        out.put(p, hand ? ctx.hand().get() : pattern.pick(random));
                    }
                }
                return out;
            }
        };
    }
}
