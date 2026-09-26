package io.blockdesigner.plugin;

/**
 * Entry point of a BlockDesigner plugin.
 *
 * <p>A plugin is a jar in the {@code plugins} folder (next to BlockDesigner's settings, see the Plugins window) that
 * contains a {@code blockdesigner-plugin.json} descriptor at its root:
 *
 * <pre>{@code
 * {
 *   "id": "hello",                        // a-z, 0-9, _ . -
 *   "name": "Hello Plugin",
 *   "version": "1.0.0",
 *   "author": "You",
 *   "description": "What it adds",
 *   "main": "com.example.HelloPlugin",     // implements BlockDesignerPlugin, public no-arg constructor
 *   "api": 2                               // the lowest PluginApi.VERSION whose features it uses (1, 2 or 3)
 * }
 * }</pre>
 *
 * <p>Every call into the plugin happens on the JavaFX application thread. Long work should run on your own thread
 * and come back with {@link PluginContext#runOnUiThread(Runnable)} before touching the scene.
 */
public interface BlockDesignerPlugin {

    /**
     * Called once after the plugin is loaded (or re-enabled). Register formats, exporters, actions, commands,
     * transforms, panels, tools, importers and scene object types here. Don't build JavaFX nodes yet: panels make
     * theirs when first shown.
     */
    void enable(PluginContext context) throws Exception;

    /**
     * Called when the plugin is disabled or the app closes. Everything registered through the context is removed
     * automatically; release anything else here (threads, files).
     */
    default void disable() {
    }
}
