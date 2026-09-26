# Writing BlockDesigner plugins

A plugin is a `.jar` in BlockDesigner's `plugins` folder. To find the folder, open **Plugins (puzzle icon) > Manage plugins > Open folder**; by default it is `%APPDATA%\BlockDesigner\plugins`, and in the portable build it is `data\plugins`. BlockDesigner loads every jar in the folder at startup, each in its own class loader. You can turn plugins on and off, install, reload and uninstall them from that window.

A plugin can add:

| Extension point | Where it shows up |
|---|---|
| `registerFormat(SchematicFormat)` | Import (file filter, drag and drop) and a card in the Export window |
| `registerExporter(PluginExporter)` | A card in the Export window, for outputs that aren't schematics (reports, a mod's own files, folders) |
| `registerAction(PluginAction)` | An entry in the Plugins menu in the top bar |
| `registerCommand(PluginCommand)` | A `/command` in the command bar (T or /), with completion and `/help` |
| `registerTransform(PluginTransform)` *(API 2)* | Plugins › Transform, the viewport's right-click menu and `/transform <id>`: a dialog with options, a live ghost preview and Apply as one undo step |
| `registerPanel(PluginPanel)` *(API 2)* | A closable tab in the right-hand panel, with your own JavaFX content |
| `registerTool(PluginTool)` *(API 2)* | A button in the tool dock over the viewport; mouse, wheel and keys go to your handler |
| `registerImporter(PluginImporter)` *(API 2)* | Import (file filter, drag and drop) for files that aren't schematics, such as images |

Plugins can also read the scene and make undoable edits through `ctx.editor()`, `ctx.editWorld(...)` and `ctx.addLayer(...)`. With API 2 they can also listen for changes (`ctx.on(...)`), look up blocks, families and colours (`ctx.blocks()`), read block models and textures (`ctx.assets()`), and ask the user for parameters without writing any UI (`Options`).

When a plugin is disabled, or fails while enabling, BlockDesigner removes everything it registered.

## Quick start

The repository has two complete examples:

- [`examples/hello-plugin`](examples/hello-plugin) (API 1): a `/pillar` command, an "Add test platform" action, a CSV bill-of-materials exporter and a plain-text schematic format.
- [`examples/palette-tools`](examples/palette-tools) (API 2): Weathering, Palette swap and Gradient transforms, a Palette panel, a colour palette exporter with options, a pixel art importer and a Wall tool.

To build them:

```
./gradlew :examples:hello-plugin:jar :examples:palette-tools:jar
```

Copy the jars from `examples/*/build/libs/` into the plugins folder, then press **Reload**.

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
- `api` is the plugin API version you need: `1` if you only use formats, exporters, actions and commands, `2` for anything marked *API 2* here. BlockDesigner refuses plugins that ask for a newer API than it has (they show as *incompatible* in the Plugins window), and keeps loading older ones.

### 2. Dependencies

Compile against the plugin API (`plugin-api`, which pulls in `core`), and keep it `compileOnly`. The app provides both at runtime, so don't bundle them into your jar.

```kotlin
dependencies {
    compileOnly(files("libs/blockdesigner-plugin-api.jar", "libs/core.jar"))
}
```

Build the API jars with `./gradlew :plugin-api:jar :core:jar`. Target Java 25 or lower. Your own libraries can be shaded into the jar.

Panels are the only part of the API that uses JavaFX. If you make one, compile against JavaFX too (`compileOnly`, version 26); `examples/palette-tools/build.gradle.kts` shows how with the OpenJFX Gradle plugin. The app provides it at runtime.

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

## API 2

### Options: parameters without UI

Transforms, tools, exporters and importers describe their parameters with `Options`, and BlockDesigner draws the controls (in the transform dialog, the tool's options bar, the export card, the import dialog), remembers the last values per feature in its settings, and hands them to you as `OptionValues`:

```java
Options.builder()
        .decimal("amount", "Weathered", 0.35, 0, 1)      // slider; 0..1 shows as a percentage
        .integer("height", "Height", 3, 1, 64)           // spinner
        .toggle("moss", "Moss as well as cracks", true)  // check box
        .block("with", "Replace with", BlockState.of("spruce_planks"))
        .blockList("fill", "Blocks", List.of(BlockState.of("stone"), BlockState.of("andesite")))  // weighted: 70%stone,30%andesite
        .choice("axis", "Along", List.of("up", "down", "east"), "up")
        .text("label", "Label", "")
        .file("image", "Image", List.of("png"))
        .build();

double amount = values.decimal("amount");
BlockState st = values.blockList("fill").pick(random);
Optional<Path> image = values.file("image");
```

Values are always valid: numbers are clamped to their range, and saved values that no longer fit (a changed range, a removed choice) fall back to the default.

### Scene events

```java
Subscription s = ctx.on(SceneEvent.BlocksChanged.class, e -> refresh(e.dirty()));
ctx.on(SceneEvent.class, e -> { /* every kind */ });
```

Events arrive on the JavaFX thread, merged to at most one of each kind per frame, so a brush stroke is one `BlocksChanged` whose `dirty` box (world coordinates) covers everything it touched. The kinds are `BlocksChanged(layers, dirty)`, `LayersChanged()`, `ActiveLayerChanged(layer)`, `SelectionChanged(region)` and `ProjectOpened(file)`. Listeners go away with the plugin; `cancel()` stops one earlier.

### Blocks: `ctx.blocks()`

`BlockCatalog` knows the loaded registry (vanilla creative-menu blocks before a Minecraft jar is loaded):

- `resolve("oak_stairs[facing=east]")` completes a block state or throws `IllegalArgumentException` for unknown blocks.
- `family(state)` gives the block's `BlockFamily`: oak → planks, stairs, slab, fence, fence gate, door, trapdoor, button, pressure plate, signs, log, wood and stripped logs; stone bricks → bricks, stairs, slab, wall. `sameShape(oak_stairs, spruceFamily)` gives `spruce_stairs` with the same facing.
- `variant(state, "mossy")`, `variant(state, "cracked")`, `variant(state, "exposed")`… (see `BlockFamilies.MODIFIERS`) and `withoutVariant(...)` swap materials, keeping shape and properties.
- `withId(state, "birch_stairs")` changes the block, keeping the properties the new one also has.
- `averageColor(state)` (0xAARRGGBB) and `displayName(state)`.

Families and variants are worked out from block ids and checked against the registry, so modded blocks named the vanilla way work too.

### Transforms

```java
ctx.registerTransform(new PluginTransform() {
    public String id() { return "weather"; }                 // also /transform weather
    public String name() { return "Weathering"; }
    public Options options() { return Options.builder().decimal("amount", "Weathered", 0.35, 0, 1).build(); }
    public boolean randomized() { return true; }             // adds a seed and a Reroll button
    public void apply(TransformContext c) {
        for (BlockPos p : c.solidBlocks()) {
            if (c.random().nextDouble() >= c.options().decimal("amount")) continue;
            c.blocks().variant(c.world().get(p), "cracked").ifPresent(v -> c.world().set(p, v));
        }
    }
});
```

`scope()` picks what it works on: the selection (selected blocks, or the //pos1 //pos2 region), the active layer, or the selection when there is one and the layer otherwise (the default). While the dialog is open `apply` runs again after each change with `c.preview()` true; its writes go into an overlay and show as ghost blocks (cells it empties are outlined in red). Apply runs it once more with the same seed and options and writes the result as one undo step, so use only `c.random()` for randomness. Throw `IllegalArgumentException` to show a message in the dialog.

### Panels

```java
ctx.registerPanel(new PluginPanel() {
    public String id() { return "palette"; }
    public String title() { return "Palette"; }
    public Node create(PanelContext panel) {           // once, when first shown
        panel.onShown(this::refresh);                  // each time it comes into view
        panel.setBadge("12");                          // a count next to the title
        return new VBox(...);
    }
    public void dispose() { /* stop timers, cancel subscriptions */ }
});
```

Panels open as tabs on the right; users can close them (they stay closed next time) and reopen them from the bar on the window's right edge. `dock()` LEFT and BOTTOM currently fall back to the right. Build nodes only in `create`, never in `enable` or a constructor: the plugin is enabled before any panel is shown. Check `panel.isShowing()` before expensive refreshes.

To follow every theme (light, dark and the colour themes), style with the theme's looked-up colours instead of fixed ones:

| Colour | Use |
|---|---|
| `-color-fg-default`, `-color-fg-muted`, `-color-fg-subtle` | text, secondary text, hints |
| `-color-bg-default`, `-color-bg-subtle`, `-color-bg-inset` | backgrounds: panel, raised rows/cards, wells |
| `-color-border-default`, `-color-border-muted` | borders and separators |
| `-color-accent-fg`, `-color-accent-emphasis`, `-color-accent-subtle` | the theme's accent: links, selected items |
| `-color-danger-fg`, `-color-warning-fg`, `-color-success-fg` | errors, warnings, success |
| `-bd-accent`, `-bd-accent-soft` | BlockDesigner's own accent (selection outlines, selected cards) |

For example `label.setStyle("-fx-text-fill: -color-fg-muted;")`. Standard controls (buttons, lists, fields) are themed already; AtlantaFX style classes such as `flat`, `accent`, `small` and `danger` work too.

### Tools

```java
ctx.registerTool(new PluginTool() {
    public String id() { return "wall"; }
    public String name() { return "Wall"; }
    public String defaultKey() { return "Shift+K"; }         // ignored if BlockDesigner already uses the key
    public Options options() { ... }                           // shown in a bar over the hotbar
    public ToolHandler activate(ToolContext t) {
        return new ToolHandler() {
            BlockPos start;
            public void press(ToolEvent e) { e.hit().ifPresent(h -> start = h.adjacent()); }
            public void drag(ToolEvent e) { t.preview().ghost(cells(e)); }
            public void release(ToolEvent e) {
                t.preview().clear();
                try (ToolContext.Stroke s = t.beginStroke("Build wall")) { cells(e).forEach(s.world()::set); }
            }
            public boolean key(String key) { return key.equals("Esc") && cancel(); }
        };
    }
});
```

While the tool is active the left and right buttons, mouse moves, the wheel (when `scroll` returns true) and plain keys (when `key` returns true) go to the handler; the middle button still orbits and pans, and in flight mode the normal flight controls apply. A `ToolEvent` has what the mouse points at (`hit`: the block, the face, the cell in front of it and its layer, or the ground), the button, the modifier keys and the mouse ray. A `Stroke` is one undo step, however many events it spans; `cancel()` puts everything back. A plugin tool's key can be changed in `settings.json` (`pluginToolKeys`, e.g. `"palette-tools/wall": "J"`); it isn't in Settings › Keybinds yet.

### Exporters: options, summary, progress, assets

`PluginExporter` gained `options()` (drawn on the export card), `summary(merged)` (an extra line on the card, e.g. "12 map items") and, in `Request`, the chosen `options()`, a `progress()` to report to (shown under the card) and `assets()`.

`AssetAccess` (also `ctx.assets()`) gives block models as textured quads (`quads(state, culledSide -> …)`), the texture atlas (`atlas()`: pixels and PNG bytes) and raw resource files (`texture("minecraft:block/stone")`), all as plain data safe to use on the export thread. For meshes (OBJ, glTF) and images.

### Importers

```java
ctx.registerImporter(new PluginImporter() {
    public String id() { return "pixel_art"; }
    public String displayName() { return "Pixel art from an image"; }
    public List<String> extensions() { return List.of("png"); }
    public List<ImportedLayer> importFile(Path file, OptionValues o, Progress p, BlockCatalog blocks) throws IOException { ... }
});
```

The extensions join the Import window's filter and drag and drop (a real schematic format wins over an importer for the same extension). With options, a small dialog asks for them first. `importFile` runs on a background thread; the layers it returns are placed like an imported schematic.

### Bedrock NBT

`NbtIO.readLE` / `NbtIO.writeLE` in core read and write little-endian NBT (Bedrock's `.mcstructure`), for a Bedrock format plugin.

## Rules of the road

- **Threads:** every call into a plugin (`enable`, actions, commands, transforms, panels, tools, events) runs on the JavaFX thread. `PluginExporter.export` and `PluginImporter.importFile` run on a background thread; exporters get their own copies of the layers. Do long work on your own thread and come back with `ctx.runOnUiThread(...)` before you touch the scene.
- **Undo:** change blocks through `ctx.editWorld(label, world -> ...)`, a command's `c.world()`, a transform's `c.world()`, a tool's stroke, or `ctx.editor().edit(layer, label)` sessions. That way every change can be undone. Don't mutate `layer.structure()` directly.
- **Errors:** in a command, throw `IllegalArgumentException("friendly message")` to show an error in the command bar. Other exceptions are caught, logged in the Plugins window and shown as a toast.
- **Formats:** NBT formats implement `detect`, `read` and `write`. Any other storage returns `false` from `nbtBased()` and overrides `readFile` and `writeFile`, as `TextFormat` does in the example. Format ids must be unique across all plugins.
- **Your files:** `ctx.dataFolder()` is a folder that belongs to your plugin. `ctx.log(...)` writes to the log shown in the Plugins window.
- **Trust:** plugins run with the same access as BlockDesigner itself. Users are warned before they install one.
