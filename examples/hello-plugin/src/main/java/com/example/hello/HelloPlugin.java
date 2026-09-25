package com.example.hello;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.plugin.BlockDesignerPlugin;
import io.blockdesigner.plugin.PluginAction;
import io.blockdesigner.plugin.PluginCommand;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginExporter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/** Shows each extension point once. */
public final class HelloPlugin implements BlockDesignerPlugin {

    @Override
    public void enable(PluginContext ctx) {
        // 1. A command: /pillar <height> [block] builds a pillar on the aimed block, as one undo step.
        ctx.registerCommand(new PluginCommand("pillar", "/pillar <height> [block]", "Build a pillar on the aimed block", c -> {
            if (c.args().isEmpty()) throw new IllegalArgumentException("Usage: /pillar <height> [block]");
            int height;
            try {
                height = Integer.parseInt(c.args().getFirst());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Height must be a number");
            }
            if (height < 1 || height > 256) throw new IllegalArgumentException("Height must be 1–256");
            BlockState block = c.args().size() > 1 ? c.blocks().resolve(c.args().get(1))
                    : c.hand().orElse(BlockState.of("minecraft:stone_bricks"));
            BlockPos base = c.aim().orElseThrow(() -> new IllegalArgumentException("Aim at a block first"));
            for (int y = 1; y <= height; y++) c.world().set(base.add(0, y, 0), block);
            return "Built a " + height + "-block pillar of " + block.path();
        }));

        // 2. A menu action: a new layer with a 9×9 stone platform.
        ctx.registerAction(new PluginAction("Add test platform", "A 9×9 stone platform in a new layer", () -> {
            Structure s = new Structure();
            for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) s.set(x, 0, z, BlockState.of("minecraft:smooth_stone"));
            ctx.addLayer("Platform", s);
            ctx.toast("Added a 9×9 platform");
        }));

        // 3. An exporter: a CSV bill of materials (how many of each block).
        ctx.registerExporter(new PluginExporter() {
            @Override
            public String id() {
                return "bom";
            }

            @Override
            public String displayName() {
                return "Bill of materials";
            }

            @Override
            public String description() {
                return "Block counts as a spreadsheet";
            }

            @Override
            public String extension() {
                return "csv";
            }

            @Override
            public void export(Request r) throws IOException {
                Map<String, Long> counts = new TreeMap<>();
                r.merged().stateCounts().forEach((st, n) -> counts.merge(st.name(), n, Long::sum));
                StringBuilder sb = new StringBuilder("block,count,stacks\n");
                counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                        .forEach(e -> sb.append(e.getKey()).append(',').append(e.getValue()).append(',')
                                .append(String.format(java.util.Locale.ROOT, "%.1f", e.getValue() / 64.0)).append('\n'));
                Files.writeString(r.target(), sb);
            }
        });

        // 4. A schematic format that isn't NBT: shows up in Import and Export.
        ctx.registerFormat(new TextFormat());
        ctx.log("Hello from " + ctx.info().name());
    }
}
