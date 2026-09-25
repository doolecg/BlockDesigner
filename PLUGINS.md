# Writing BlockDesigner plugins

A plugin is a `.jar` in BlockDesigner's `plugins` folder. To find the folder, open **Plugins (puzzle icon) > Manage plugins > Open folder**; by default it is `%APPDATA%\BlockDesigner\plugins`, and in the portable build it is `data\plugins`. BlockDesigner loads every jar in the folder at startup, each in its own class loader. You can turn plugins on and off, install, reload and uninstall them from that window.

A plugin can add:

| Extension point | Where it shows up |
|---|---|
| `registerFormat(SchematicFormat)` | Import (file filter, drag and drop) and a card in the Export window |
| `registerExporter(PluginExporter)` | A card in the Export window, for outputs that aren't schematics (reports, a mod's own files, folders) |
| `registerAction(PluginAction)` | An entry in the Plugins menu in the top bar |
| `registerCommand(PluginCommand)` | A `/command` in the command bar (T or /), with completion and `/help` |

Plugins can also read the scene and make undoable edits through `ctx.editor()`, `ctx.editWorld(...)` and `ctx.addLayer(...)`.

When a plugin is disabled, or fails while enabling, BlockDesigner removes everything it registered.

## Quick start

The repository has a complete example in [`examples/hello-plugin`](examples/hello-plugin). It adds a `/pillar` command, an "Add test platform" action, a CSV bill-of-materials exporter and a plain-text schematic format. To build it:

```
./gradlew :examples:hello-plugin:jar
```

Copy `examples/hello-plugin/build/libs/hello-plugin-*.jar` into the plugins folder, then press **Reload**.

### 1. Descriptor

Put `blockdesigner-plugin.json` at the root of the jar (in `src/main/resources`):

```json
{
  "id": "hello",
  "name": "Hello Plugin",
  "version": "1.0.0",
  "author": "You",
  "description": "What it adds",
  "main": "com.example.hello.HelloPlugin",
  "api": 1
}
```

- The `id` may only use `a-z 0-9 _ . -`.
- `main` must implement `BlockDesignerPlugin` and have a public no-argument constructor.
- `api` is the `PluginApi.VERSION` you built against. BlockDesigner refuses plugins that ask for a newer API than it has.

### 2. Dependencies

Compile against the plugin API (`plugin-api`, which pulls in `core`), and keep it `compileOnly`. The app provides both at runtime, so don't bundle them into your jar.

```kotlin
dependencies {
    compileOnly(files("libs/blockdesigner-plugin-api.jar", "libs/core.jar"))
}
```

Build the API jars with `./gradlew :plugin-api:jar :core:jar`. Target Java 25 or lower. Your own libraries can be shaded into the jar.

### 3. Entry point

```java
public final class HelloPlugin implements BlockDesignerPlugin {
    @Override
    public void enable(PluginContext ctx) {
        ctx.registerCommand(new PluginCommand("pillar", "/pillar <height> [block]", "Build a pillar", c -> {
            int h = Integer.parseInt(c.args().getFirst());
            BlockState block = c.args().size() > 1 ? c.blocks().resolve(c.args().get(1)) : BlockState.of("stone");
            BlockPos base = c.aim().orElseThrow(() -> new IllegalArgumentException("Aim at a block first"));
            for (int y = 1; y <= h; y++) c.world().set(base.add(0, y, 0), block);   // one undo step
            return "Built a pillar";
        }));
    }
}
```

## Rules of the road

- **Threads:** every call into a plugin (`enable`, actions, commands) runs on the JavaFX thread. `PluginExporter.export` runs on a background thread and gets its own copies of the layers. Do long work on your own thread and come back with `ctx.runOnUiThread(...)` before you touch the scene.
- **Undo:** change blocks through `ctx.editWorld(label, world -> ...)`, a command's `c.world()`, or `ctx.editor().edit(layer, label)` sessions. That way every change can be undone. Don't mutate `layer.structure()` directly.
- **Errors:** in a command, throw `IllegalArgumentException("friendly message")` to show an error in the command bar. Other exceptions are caught, logged in the Plugins window and shown as a toast.
- **Formats:** NBT formats implement `detect`, `read` and `write`. Any other storage returns `false` from `nbtBased()` and overrides `readFile` and `writeFile`, as `TextFormat` does in the example. Format ids must be unique across all plugins.
- **Your files:** `ctx.dataFolder()` is a folder that belongs to your plugin. `ctx.log(...)` writes to the log shown in the Plugins window.
- **Trust:** plugins run with the same access as BlockDesigner itself. Users are warned before they install one.
