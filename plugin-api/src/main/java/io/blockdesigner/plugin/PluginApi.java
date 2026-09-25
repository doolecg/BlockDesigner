package io.blockdesigner.plugin;

/** Version of the plugin API. Plugins declaring a newer {@code api} than this are not loaded. */
public final class PluginApi {
    /** 1: formats, exporters, actions, commands, world edits. */
    public static final int VERSION = 1;

    /** Name of the descriptor file at the root of a plugin jar. */
    public static final String DESCRIPTOR = "blockdesigner-plugin.json";

    private PluginApi() {
    }
}
