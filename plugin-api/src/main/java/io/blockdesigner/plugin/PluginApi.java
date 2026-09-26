package io.blockdesigner.plugin;

/** Version of the plugin API. Plugins declaring a newer {@code api} than this are not loaded. */
public final class PluginApi {
    /**
     * 1: formats, exporters, actions, commands, world edits.
     * <br>2: options, scene events, the block catalog, asset access, transforms, panels, tools, importers, and
     * options / summary / progress for exporters. Plugins that use any of these declare {@code "api": 2}, so older
     * BlockDesigners refuse them with a clear message instead of failing half-way. API 1 plugins keep working.
     * <br>3: scene objects (images, guides and markers that aren't blocks, listed in the Layers panel, moved by the
     * Move / Rotate / Scale tools and saved with the project). API 1 and 2 plugins keep working.
     * <br>4: settings in the plugin's own tab ({@link PluginContext#registerSettings}). Every plugin gets that tab,
     * whatever API it declares; only plugins that register settings need {@code "api": 4}.
     * <br>5: tools can read the selection and preview and apply transforms on it
     * ({@link ToolContext#selection}, {@link ToolContext#previewTransform}, {@link ToolContext#applyTransform}), hear
     * about option changes ({@link ToolHandler#optionsChanged}), and options can show only for some values of a
     * choice ({@link Options.Builder#showWhen}); a tool can leave the left button to block selection
     * ({@link PluginTool#selects}); plugins can read and fill the hotbar, draw block icons, and pick and set up their
     * own tools ({@link PluginContext#hotbar}, {@link PluginContext#blockIcon}, {@link PluginContext#setToolOptions}).
     */
    public static final int VERSION = 5;

    /** Name of the descriptor file at the root of a plugin jar. */
    public static final String DESCRIPTOR = "blockdesigner-plugin.json";

    private PluginApi() {
    }
}
