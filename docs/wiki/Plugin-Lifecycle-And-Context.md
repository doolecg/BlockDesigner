# Plugin lifecycle and context

## The lifecycle

```java
public interface BlockDesignerPlugin {
    void enable(PluginContext context) throws Exception;   // once per start
    default void disable() { }                              // when turned off, reloaded, uninstalled or the app closes
}
```

1. **Construction.** BlockDesigner creates your `main` class with its public no-argument constructor, in the
   plugin's own class loader. Keep the constructor empty; you have no context yet.
2. **`enable(context)`**, on the JavaFX thread. Register everything here: formats, exporters, importers, actions,
   commands, transforms, panels, tools, object types, settings, event listeners. Don't build JavaFX nodes yet:
   panels make theirs in `create()` when first shown. An exception here marks the plugin FAILED and removes whatever
   it had registered.
3. **Running.** BlockDesigner calls your registered features (actions, commands, tools, events…) on the JavaFX
   thread, except exporters' `export` and importers' `importFile`, which run on a background thread.
4. **`disable()`**, on the JavaFX thread. Only needed for things you started yourself (threads, timers outside
   JavaFX, open files, network connections). Everything registered through the context is removed by BlockDesigner
   afterwards; your panels get `dispose()`.
5. **Unload.** The class loader is closed and the instance dropped. Turning the plugin on again starts over at step
   1 with a new class loader, so static fields do not survive a disable / enable, a Reload or an update.

Keep `enable` quick: plugins are enabled one after another while the main window opens. Start long work (loading
big data, network) on your own thread and come back with `ctx.runOnUiThread(...)`.

## Threads

- Every call into a plugin happens on the JavaFX application thread, except `PluginExporter.export` and
  `PluginImporter.importFile` (background thread) and `Progress.update` (safe from any thread).
- Touch the scene, the editor and JavaFX nodes only on the JavaFX thread. From your own threads, use
  `ctx.runOnUiThread(task)`; it runs the task at once when already on that thread.
- Scene events arrive on the JavaFX thread, at most one of each kind per frame.

## `PluginContext`

The context is your plugin's handle on BlockDesigner, valid from `enable` until the plugin is unloaded. Keep a
reference to it in your plugin if features need it later. The full list with API levels is in the
[API reference](https://github.com/doolecg/BlockDesigner/blob/main/docs/plugin-api-reference.md#plugincontext);
grouped by purpose:

| Purpose | Methods |
|---|---|
| About you | `info()` (your parsed manifest: id, name, version, author, description, main class, api, updates), `dataFolder()`, `log(message)` |
| Register | `registerFormat`, `registerExporter`, `registerAction`, `registerCommand` (API 1); `registerTransform`, `registerPanel`, `registerImporter`, `registerTool` (2); `registerObjectType` (3); `registerSettings` (4) |
| Listen | `on(eventType, listener)` returns a `Subscription` (API 2) |
| Read the project | `scene()`, `activeLayer()`, `selectedLayers()`, `targetVersion()`; `selection()` (API 2) |
| Edit the project | `editWorld(label, edit)`, `editor()`, `addLayer(name, blocks)` |
| Blocks and assets | `blocks()`, `assets()` (API 2) |
| Scene objects | `objects()` (API 3) |
| Settings | `settings()` (API 4) |
| Feedback | `status(message)`, `toast(message)` |
| Threads | `runOnUiThread(task)` |
| Hotbar and tools | `hotbar()`, `setHotbar(blocks)`, `blockIcon(block)`, `pickTool(id)`, `setToolOptions(id, change)` (API 5) |

A sketch that uses the common parts:

```java
public final class ExamplePlugin implements BlockDesignerPlugin {
    private PluginContext ctx;
    private ExecutorService worker;

    @Override
    public void enable(PluginContext ctx) {
        this.ctx = ctx;
        worker = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("example-worker").unstarted(r));
        ctx.on(SceneEvent.ProjectOpened.class, e -> ctx.log("Project: " + e.file().map(Path::toString).orElse("new")));
        ctx.registerAction(new PluginAction("Count blocks", "Counts the blocks in the active layer", this::count));
    }

    private void count() {
        Structure blocks = ctx.activeLayer().map(Layer::structure).orElse(null);
        if (blocks == null) { ctx.toast("No layer"); return; }
        long n = blocks.blockCount();
        ctx.status(n + " blocks in the active layer");
    }

    @Override
    public void disable() {
        worker.shutdownNow();      // the only thing BlockDesigner can't clean up for you
    }
}
```

## Editing rules

- Change blocks only through undoable paths: `ctx.editWorld(label, world -> …)`, `ctx.editor().edit(layer, label)`
  sessions, `ctx.addLayer(...)`, a command's `c.world()`, a transform's `c.world()` or a tool's stroke. Each is one
  undo step. Never mutate `layer.structure()` directly: those changes can't be undone.
- `editWorld` works in world coordinates across the visible, unlocked layers; blocks placed in empty space go into
  the active layer.

More in PLUGINS.md, [Reading and editing the scene](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md#reading-and-editing-the-scene)
and [Rules of the road](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md#rules-of-the-road).
