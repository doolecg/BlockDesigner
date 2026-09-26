package com.example.palette;

import io.blockdesigner.plugin.BlockDesignerPlugin;
import io.blockdesigner.plugin.PluginContext;

/**
 * Registers one of each API 2 extension: transforms, a panel, an exporter with options, an importer and a tool.
 * Everything is removed again automatically when the plugin is disabled.
 */
public final class PaletteToolsPlugin implements BlockDesignerPlugin {

    @Override
    public void enable(PluginContext ctx) {
        ctx.registerTransform(new WeatheringTransform());
        ctx.registerTransform(new PaletteSwapTransform());
        ctx.registerTransform(new GradientTransform());
        ctx.registerPanel(new PalettePanel());
        ctx.registerExporter(new GimpPaletteExporter(ctx.blocks()));
        ctx.registerImporter(new PixelArtImporter());
        ctx.registerTool(new WallTool());
    }
}
