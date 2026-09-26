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
     */
    public static final int VERSION = 3;

    /** Name of the descriptor file at the root of a plugin jar. */
    public static final String DESCRIPTOR = "blockdesigner-plugin.json";

    private PluginApi() {
    }
}
