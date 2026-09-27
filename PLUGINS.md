# Writing BlockDesigner plugins

A plugin is a `.jar` that BlockDesigner loads at startup, each in its own class loader. It can add schematic formats, exporters, importers, menu actions, `/commands`, transforms with a live preview, side panels and tools, and it can read and edit the open project with full undo.

This guide covers setting up a plugin project, the manifest, installing and testing, and every extension point with examples taken from the two example plugins in this repository. For a list of every type in the API see [docs/plugin-api-reference.md](docs/plugin-api-reference.md); for the design notes behind API 2 see [docs/plugin-api-v2.md](docs/plugin-api-v2.md). The [plugin developer wiki](docs/wiki/Home.md) covers the rest of a plugin's life: project setup, the manifest field by field, how plugins are loaded, automatic updates, releasing, testing and troubleshooting.

**Contents**

- [Getting started](#getting-started): [examples](#the-examples) · [project setup](#project-setup) · [manifest](#the-manifest-blockdesigner-pluginjson) · [entry point](#the-entry-point) · [install and test](#install-and-test)
- [API 1 extension points](#api-1-extension-points): [commands](#commands) · [actions](#menu-actions) · [exporters](#exporters) · [schematic formats](#schematic-formats) · [editing the scene](#reading-and-editing-the-scene)
- [API 2 extension points](#api-2-extension-points): [options](#options-parameters-without-writing-ui) · [transforms](#transforms) · [panels](#panels) · [tools](#tools) · [importers](#importers) · [richer exporters](#exporters-options-summary-progress) · [scene events](#scene-events) · [block catalog](#the-block-catalog) · [asset access](#asset-access) · [Bedrock NBT](#bedrock-nbt)
- [API 3 extension points](#api-3-extension-points): [scene objects](#scene-objects)
- [API 4 extension points](#api-4-extension-points): [settings](#settings-in-the-settings-window) · [API 5](#api-5-extension-points): [selection tools](#tools-that-work-on-the-selection) · [conditional options](#options-that-show-only-for-some-choices) · [hotbar and icons](#hotbar-icons-and-your-own-tools)
- [API 6 extension points](#api-6-extension-points): [resource packs](#resource-packs) · [the UI kit](#building-panels-with-the-ui-kit) · [options: groups, help, units](#options-groups-help-units-and-enabledwhen) · [settings](#settings-updatesettings-and-opensettings) · [keys](#keys) · [page status and navigation](#page-status-and-navigation) · [dialogs](#dialogs) · [remembered forms](#remembered-forms) · [theming](#theming)
- [API 7 extension points](#api-7-extension-points): [opening a file as the project](#opening-a-file-as-the-project)
- [Rules of the road](#rules-of-the-road) · [Limits and what's not supported yet](#limits-and-whats-not-supported-yet)

## What a plugin can add

| Extension point | API | Where it shows up |
|---|---|---|
| `registerCommand(PluginCommand)` | 1 | A `/command` in the command bar (T or /), with completion and `/help` |
| `registerAction(PluginAction)` | 1 | An entry in the Plugins menu in the top bar |
| `registerExporter(PluginExporter)` | 1 | A card in the Export window, for outputs that aren't schematics (reports, a mod's own files, folders) |
| `registerFormat(SchematicFormat)` | 1 | Import (file filter, drag and drop) and a card in the Export window |
| `registerTransform(PluginTransform)` | 2 | Plugins › Transform, the viewport's right-click menu and `/transform <id>`: a dialog with options, a live ghost preview and Apply as one undo step |
| `registerPanel(PluginPanel)` | 2 | A closable tab in the right-hand panel, with your own JavaFX content |
| `registerTool(PluginTool)` | 2 | A button in the tool dock over the viewport; mouse, wheel and keys go to your handler |
| `registerImporter(PluginImporter)` | 2 | Import (file filter, drag and drop) for files that aren't schematics, such as images |
| `registerObjectType(SceneObjectType)` | 3 | Things in the scene that aren't blocks (reference images, guides): rows in the Layers panel, drawn in the 3D view, moved by the Move / Rotate tools, a right-click menu, saved in the project |
| `registerSettings(Options, onChange)` | 4 | The plugin's page in the Settings window (from 0.4.24; the tab's Overview before), kept between runs |
| `ui()`, `showPanel`, `setPanelStatus`, `openSettings`, `updateSettings` | 6 | The UI kit and the app's forms and dialogs for your panels, a status dot on a page button, jumping to a page or to the plugin's settings |

API 2 also adds exporter options, summaries and progress; declarative `Options`; scene events (`ctx.on(...)`); the block catalog (`ctx.blocks()`); and asset access (`ctx.assets()`). API 3 adds scene objects (`ctx.objects()`).

When a plugin is disabled, or fails while enabling, BlockDesigner removes everything it registered.

**Every plugin gets its own tab** on the right, whatever API it declares, so users can see it's running, and from
0.4.17 it is the only tab a plugin gets. Its panels are the tab's pages, picked in a row of buttons along the top; the
tab opens on the page used last (the first one at first). At the end of that row are a gear (when the plugin has
settings: it opens the plugin's page in the Settings window) and an info button for the **Overview**. The Overview
lists everything the plugin registered, each with a button to use it (actions, tools, transforms, panels, plus its
commands, formats, importers, exporters and object types) and an **Open settings…** button, then at the bottom its
name, version, a Running (or Failed) badge and its description, with **Manage plugins…** and **Turn off**. A plugin
without panels shows just the Overview. Users can close the tab like any other.

## Getting started

### The examples

The repository has two complete plugins. Read them alongside this guide:

- [`examples/hello-plugin`](examples/hello-plugin) (API 1): a `/pillar` command, an "Add test platform" action, a CSV bill-of-materials exporter and a plain-text schematic format.
- [`examples/palette-tools`](examples/palette-tools) (API 2): Weathering, Palette swap and Gradient transforms, a Palette panel, a GIMP colour-palette exporter with options, a pixel-art importer and a Wall tool.
- [`plugins/reference-planes`](plugins/reference-planes) (API 3): Blender-style reference images as scene objects. This one is a real plugin, released on its own.

Build them from the repository root:

```
./gradlew :examples:hello-plugin:jar :examples:palette-tools:jar
```

The jars land in `examples/*/build/libs/`. Install them as described in [Install and test](#install-and-test).

### Project setup

Plugins compile against two jars: the plugin API and `core`, which the API exposes (block states, structures, layers, NBT, schematic formats). Build them from the repository:

```
./gradlew :plugin-api:jar :core:jar
```

This writes `plugin-api/build/libs/blockdesigner-plugin-api-<version>.jar` and `core/build/libs/core-<version>.jar`. Copy them into your project (for example into `libs/`) and depend on them as **`compileOnly`**: BlockDesigner provides both at runtime, so never bundle them into your jar. Your own libraries can be shaded into the jar.

A minimal `build.gradle.kts`:

```kotlin
plugins {
    java
}

dependencies {
    compileOnly(files("libs/blockdesigner-plugin-api-0.4.6.jar", "libs/core-0.4.6.jar"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25   // the API jars are Java 25 class files; target 25 or lower
}
```

You need JDK 25 or newer to compile against the API jars.

Panels are the only part of the API that uses JavaFX. If you make one, compile against JavaFX too, also `compileOnly` (BlockDesigner ships JavaFX 26). With the OpenJFX Gradle plugin, as [`examples/palette-tools/build.gradle.kts`](examples/palette-tools/build.gradle.kts) does:

```kotlin
plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "26.0.2"
    modules = listOf("javafx.controls")
    configuration = "compileOnly"
}
```

### The manifest: `blockdesigner-plugin.json`

Put `blockdesigner-plugin.json` at the root of the jar (in Gradle, `src/main/resources/blockdesigner-plugin.json`). This is `palette-tools`' manifest:

```json
{
  "id": "palette-tools",
  "name": "Palette Tools",
  "version": "1.0.0",
  "author": "BlockDesigner",
  "description": "Example plugin for API 2: weathering, palette swap and gradient transforms, a Palette panel, a colour palette exporter, a pixel art importer and a Wall tool.",
  "main": "com.example.palette.PaletteToolsPlugin",
  "api": 6
}
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Unique plugin id, using only `a-z 0-9 _ . -`. Two jars with the same id can't both load. |
| `main` | yes | Class implementing `BlockDesignerPlugin`, with a public no-argument constructor. |
| `name` | no | Display name (defaults to the id). |
| `version`, `author`, `description` | no | Shown in the Plugins window and the install prompt. |
| `api` | no | The plugin API version the plugin needs (defaults to `1`). |

**API versions.** The current API is **7** (`PluginApi.VERSION`, BlockDesigner 0.4.26).

- `"api": 1`: commands, menu actions, exporters, schematic formats and scene edits (`editWorld`, `editor()`, `addLayer`).
- `"api": 2`: everything in API 1 plus options, scene events, the block catalog, asset access, transforms, panels, tools, importers, and options / summary / progress for exporters.
- `"api": 3`: everything in API 2 plus scene objects (`registerObjectType`, `ctx.objects()`).
- `"api": 4`: plugin settings (`registerSettings`, `ctx.settings()`).
- `"api": 5`: tools that work on the selection, options shown for some choices (`showWhen`), the hotbar and block icons.
- `"api": 6`: resource packs, and the UI kit (`io.blockdesigner.plugin.ui`, `ctx.ui()`), option groups, help and units, page status and navigation, settings in the Settings window.
- `"api": 7`: opening a file as the project (`ctx.openFile`) and bringing the window forward (`ctx.ui().toFront()`).

Declare the lowest version whose features you use. BlockDesigner refuses plugins that ask for a newer API than it has (they show as *needs a newer BlockDesigner* in the Plugins window) and keeps loading older ones, so older plugins run unchanged on a newer BlockDesigner.

### Updates: `"updates"`

Add your plugin's GitHub repository to the manifest and BlockDesigner keeps it up to date by itself:

```json
"updates": "https://github.com/you/your-plugin"
```

At startup (and from **Check for updates** in the Plugins window), BlockDesigner reads that repository's latest
release. If its tag (`1.2.0` or `v1.2.0`) is newer than the installed `version`, it downloads the release's jar (the one
named `<id>-<version>.jar`, or its only jar), checks the download against the size and checksum GitHub publishes,
checks it's the same plugin id and an API this BlockDesigner has, and installs it in place, keeping it on or off as
it was. A release that needs a newer BlockDesigner is left alone until BlockDesigner is updated. Without `"updates"`
the plugin is updated by hand (Install… a newer jar). Users can turn automatic updates off in the Plugins window.

### The entry point

`main` implements `BlockDesignerPlugin`. Register everything in `enable`; `disable` is optional and only needed for resources you created yourself (threads, open files), because everything registered through the context is removed automatically.

```java
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
```

`PluginContext` also gives you `info()` (your manifest), `dataFolder()` (a folder of your own, created on first call), `log(...)` (the log shown in the Plugins window), `status(...)` (status bar), `toast(...)` (a short message over the 3D view) and `runOnUiThread(...)`.

### Install and test

BlockDesigner loads every `.jar` in its plugins folder at startup:

- installed version: `%APPDATA%\BlockDesigner\plugins`
- portable build: `data\plugins` next to `BlockDesigner.exe`

Open **Plugins (puzzle icon) › Manage plugins…** (also in Settings › General). From there you can:

- **Install…** a jar: BlockDesigner reads its manifest, asks for confirmation (plugins run with full access to the computer), copies it into the plugins folder, replacing an older copy of the same plugin id, and enables it.
- **Open folder** to copy jars in by hand, then **Reload** to rescan the folder. Reload is also the quickest way to test a rebuilt jar.
- Switch each plugin **on or off** with its check box (remembered between runs), or **uninstall** it (deletes the jar; if Windows still has it locked, delete it after a restart).
- See each plugin's state (*on* with what it registered, *off*, *failed to start*, *needs a newer BlockDesigner*), its error if it failed, and its **log**: your `ctx.log(...)` lines and any exceptions your callbacks threw.

## API 1 extension points

The examples in this section come from [`HelloPlugin.java`](examples/hello-plugin/src/main/java/com/example/hello/HelloPlugin.java) and [`TextFormat.java`](examples/hello-plugin/src/main/java/com/example/hello/TextFormat.java).

### Commands

A `PluginCommand` adds a BlockEdit command to the command bar (T or /), with its usage shown while typing and a line in `/help`. Names use `a-z 0-9 _`. The handler gets a `Context` with the arguments (`args`, with `-x` flags split out into `flags`), a `world` of every visible, unlocked layer merged in world coordinates, the `//pos1 //pos2` `region`, the aimed block (`aim`), the held block (`hand`) and a block resolver. Everything written to `c.world()` is one undo step. Throw `IllegalArgumentException` for a friendly error in the command bar; return the success message.

```java
ctx.registerCommand(new PluginCommand("pillar", "/pillar <height> [block]", "Build a pillar on the aimed block", c -> {
    if (c.args().isEmpty()) throw new IllegalArgumentException("Usage: /pillar <height> [block]");
    int height;
    try {
        height = Integer.parseInt(c.args().getFirst());
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Height must be a number");
    }
    BlockState block = c.args().size() > 1 ? c.blocks().resolve(c.args().get(1))
            : c.hand().orElse(BlockState.of("minecraft:stone_bricks"));
    BlockPos base = c.aim().orElseThrow(() -> new IllegalArgumentException("Aim at a block first"));
    for (int y = 1; y <= height; y++) c.world().set(base.add(0, y, 0), block);
    return "Built a " + height + "-block pillar of " + block.path();
}));
```

`c.requireRegion()` returns the region or fails with "Select a region first".

### Menu actions

A `PluginAction` is an entry in the Plugins menu: a label, a tooltip and a `Runnable` that runs on the JavaFX thread.

```java
ctx.registerAction(new PluginAction("Add test platform", "A 9×9 stone platform in a new layer", () -> {
    Structure s = new Structure();
    for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) s.set(x, 0, z, BlockState.of("minecraft:smooth_stone"));
    ctx.addLayer("Platform", s);   // one undo step; the new layer becomes active
    ctx.toast("Added a 9×9 platform");
}));
```

### Exporters

A `PluginExporter` is a card in the Export window for output that isn't a schematic format. Give it an `id` (unique within the plugin), a `displayName`, an optional `description`, and the file `extension` (or return true from `writesFolder()` to have the user pick a folder). `export(Request)` runs on a background thread and gets the export name, author, target Minecraft version, the chosen layers (`layers()`, bottom first) and the same layers merged into world coordinates (`merged()`), and the `target` path.

```java
ctx.registerExporter(new PluginExporter() {
    public String id() { return "bom"; }
    public String displayName() { return "Bill of materials"; }
    public String description() { return "Block counts as a spreadsheet"; }
    public String extension() { return "csv"; }

    public void export(Request r) throws IOException {
        Map<String, Long> counts = new TreeMap<>();
        r.merged().stateCounts().forEach((st, n) -> counts.merge(st.name(), n, Long::sum));
        StringBuilder sb = new StringBuilder("block,count,stacks\n");
        counts.forEach((block, n) -> sb.append(block).append(',').append(n).append(',')
                .append(String.format(Locale.ROOT, "%.1f", n / 64.0)).append('\n'));
        Files.writeString(r.target(), sb);
    }
});
```

API 2 adds options, a summary line and progress to exporters: see [below](#exporters-options-summary-progress).

### Schematic formats

A `SchematicFormat` (from `core`, `io.blockdesigner.core.formats`) shows up in both Import and Export. Format ids must be unique across all plugins.

- **NBT formats** implement `detect(CompoundTag root)`, `read(CompoundTag root)` and `write(SchematicFile, WriteOptions)`.
- **Other storage** returns `false` from `nbtBased()` and overrides `readFile(Path)` and `writeFile(SchematicFile, WriteOptions, Path)`.
- `canRead()` / `canWrite()` (both true by default) make a format import-only or export-only.

The example's text format writes one `x y z block_state` line per block:

```java
final class TextFormat implements SchematicFormat {
    public String id() { return "hello-text"; }
    public String displayName() { return "Block list (text)"; }
    public List<String> extensions() { return List.of("bdtxt"); }
    public boolean nbtBased() { return false; }

    public SchematicFile readFile(Path file) throws IOException {
        Structure s = new Structure();
        for (String line : Files.readAllLines(file)) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\s+", 4);
            s.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), BlockState.parse(p[3]));
        }
        return SchematicFile.single(file.getFileName().toString().replaceFirst("\\..*$", ""), s);
    }

    public void writeFile(SchematicFile file, WriteOptions options, Path target) throws IOException {
        // for each region: r.structure().forEachBlock((x, y, z, st) -> write x+ox, y+oy, z+oz and st)
    }
}
```

The full version, with line-numbered errors and region offsets, is in [`TextFormat.java`](examples/hello-plugin/src/main/java/com/example/hello/TextFormat.java).

### Reading and editing the scene

- **Read:** `ctx.scene()` (all layers), `ctx.activeLayer()`, `ctx.selectedLayers()`, `ctx.targetVersion()`; with API 2 also `ctx.selection()` (the box around the selected blocks, or the `//pos1 //pos2` region).
- **Edit in world coordinates**, like a command: `ctx.editWorld("Label", world -> world.set(pos, state))`. It works across the visible, unlocked layers; blocks placed in empty space go into the active layer. One undo step.
- **Edit one layer** in layer-local coordinates with a session (closing it commits one undo step):

  ```java
  try (var s = ctx.editor().edit(layer, "Paint floor")) {
      s.fill(box, BlockState.of("minecraft:oak_planks"));
  }
  ```

  `ctx.editor()` also adds, removes, reorders and modifies layers undoably.
- **Add a layer:** `ctx.addLayer(name, structure)`.

Never mutate `layer.structure()` directly: those changes can't be undone.

## API 2 extension points

Everything in this section needs `"api": 2` in the manifest. The examples come from [`examples/palette-tools`](examples/palette-tools/src/main/java/com/example/palette).

### Options: parameters without writing UI

Transforms, tools, exporters and importers describe their parameters with `Options`. BlockDesigner draws the controls (in the transform dialog, the tool's options bar, the export card and the import dialog), remembers the last values per feature in its settings, and hands them to you as `OptionValues`.

```java
Options.builder()
        .decimal("amount", "Weathered", 0.35, 0, 1)      // slider; a 0..1 range shows as a percentage
        .integer("height", "Height", 3, 1, 64)           // spinner
        .toggle("moss", "Moss as well as cracks", true)  // check box
        .block("with", "Replace with", BlockState.of("spruce_planks"))
        .blockList("fill", "Blocks", List.of(BlockState.of("stone"), BlockState.of("andesite")))  // weighted: 70%stone,30%andesite
        .choice("axis", "Along", List.of("up", "down", "east"), "up")
        .text("label", "Label", "")
        .file("image", "Image", List.of("png"))
        .build();
```

Read the values with the typed getters:

```java
double amount = values.decimal("amount");
BlockState st = values.blockList("fill").pick(random);   // a BlockPattern, drawn by weight
Optional<Path> image = values.file("image");
```

Keys use `A-Z a-z 0-9 _ . -`. Values are always valid: numbers are clamped to their range, and saved values that no longer fit (a changed range, a removed choice) fall back to the default. A getter with an unknown key or the wrong type throws `IllegalArgumentException`. `Options.none()` means no parameters.

`BlockPattern` is the value of a block list: `BlockPattern.parse("70%stone_bricks,30%mossy_stone_bricks", resolver)`, `pick(random)`, `blocks()`.

### Transforms

A `PluginTransform` changes existing blocks: weathering, a palette swap, a gradient. It appears under **Plugins › Transform**, in the viewport's right-click menu ("Transform selection" / "Transform <layer>") and as `/transform <id>` (`/transform` alone lists them). Choosing it opens a dialog with its options; while the user changes them, `apply` runs again in preview mode and the result shows as ghost blocks. **Apply** runs it once more with the same seed and options and writes the result as one undo step.

<p align="center"><img src="docs/images/plugin-transform-weathering.png" alt="The Weathering transform's dialog" width="360"></p>

From [`WeatheringTransform.java`](examples/palette-tools/src/main/java/com/example/palette/WeatheringTransform.java):

```java
final class WeatheringTransform implements PluginTransform {
    public String id() { return "weather"; }          // also /transform weather; a-z 0-9 _
    public String name() { return "Weathering"; }
    public String description() { return "Turns a share of the stone cracked or mossy, keeping stairs and slabs facing the same way."; }
    public String icon() { return "M2 3 H14 V13 H2 Z M2 8 H14 M6 3 V8 M10 8 V13 M9 3 L8 6 L10 9 L8.5 13"; }

    public Options options() {
        return Options.builder()
                .decimal("amount", "Weathered", 0.35, 0, 1)
                .decimal("moss", "Of those, mossy", 0.5, 0, 1)
                .toggle("bottomUp", "More moss near the ground", true)
                .build();
    }

    public boolean randomized() { return true; }      // adds a seed and a Reroll button

    public void apply(TransformContext c) {
        double amount = c.options().decimal("amount"), moss = c.options().decimal("moss");
        boolean bottomUp = c.options().toggle("bottomUp");
        int minY = c.bounds().minY(), height = c.bounds().sizeY();
        for (BlockPos p : c.solidBlocks()) {
            // Draw both numbers for every block, used or not, so each block's fate depends only on the seed.
            double age = c.random().nextDouble(), kind = c.random().nextDouble();
            if (age >= amount) continue;
            double mossChance = moss;
            if (bottomUp && height > 1) mossChance = Math.min(1, moss * 2 * (1 - (p.y() - minY) / (double) (height - 1)));
            BlockState st = c.world().get(p);
            String first = kind < mossChance ? "mossy" : "cracked", second = first.equals("mossy") ? "cracked" : "mossy";
            c.blocks().variant(st, first).or(() -> c.blocks().variant(st, second)).ifPresent(v -> c.world().set(p, v));
        }
    }
}
```

- **Scope** (`scope()`): `SELECTION` (the selected blocks, or the `//pos1 //pos2` region), `LAYER` (the active layer) or `SELECTION_OR_LAYER` (the default: the selection when there is one, otherwise the active layer).
- **Context:** `world()` (reads see the scene plus this run's writes), `bounds()`, `solidBlocks()`, `blockCount()`, `options()`, `random()`, `blocks()` and `preview()` (true while previewing).
- **Determinism:** use only `c.random()` for randomness and draw from it in a fixed order, so the preview and Apply match.
- **Preview:** writes go into an overlay and show as ghosts; cells the transform empties are outlined in red. Nothing in the project changes until Apply.
- **Errors:** throw `IllegalArgumentException` to show a message in the dialog.
- **Speed:** `apply` runs on the JavaFX thread once per preview; keep it to a few hundred milliseconds for a large selection.
- **Icon:** 16×16 SVG path data, drawn as 1.5px rounded strokes like the built-in tool icons.

### Panels

A `PluginPanel` is a page of the plugin's tab in the right-hand panel, with your own JavaFX content. Build nodes only in `create`, which runs the first time the page is shown; never in `enable` or a constructor, because the plugin is enabled before any panel is shown. Release timers and listeners in `dispose`.

From API 6, build the page with the UI kit's `PanelScaffold` (see [the UI kit](#building-panels-with-the-ui-kit)): it owns the padding and spacing, so the page lines up with BlockDesigner's own and every other plugin. A node that isn't a `PanelScaffold` gets the 10 px padding panels always had. Condensed from Palette Tools' Palette page, which lists the blocks of the selection with their icons and counts:

```java
final class PalettePanel implements PluginPanel {
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private PanelContext panel;
    private Label status;
    private boolean stale = true;

    public String id() { return "palette"; }
    public String title() { return "Palette"; }

    public Node create(PanelContext context) {
        this.panel = context;
        PluginContext ctx = context.plugin();
        subscriptions.add(ctx.on(SceneEvent.BlocksChanged.class, e -> changed()));
        subscriptions.add(ctx.on(SceneEvent.SelectionChanged.class, e -> changed()));
        context.onShown(() -> { if (stale) refresh(); });   // each time the page comes into view
        ItemList<Entry> list = new ItemList<Entry>(e -> ItemRow.of(e.name()).image(e.icon())
                        .trailing(Controls.caption(String.format("%,d", e.count()))))
                .empty(new EmptyState(Icon.INFO, "No blocks here.").hint("Build something, or select blocks to count them."));
        list.setItems(entries);
        status = Controls.caption("");
        refresh();
        return new PanelScaffold()
                .add(Controls.search("Filter blocks"))   // controls at the top
                .grow(list)                               // the list takes the free height
                .footer(status);                          // status at the bottom
    }

    private void changed() {
        stale = true;
        if (panel != null && panel.isShowing()) refresh();   // skip the work while hidden
    }

    private void refresh() {
        stale = false;
        // count blocks in panel.plugin().selection() or the visible layers, rebuild the rows...
        panel.setBadge("12");                                // a count next to the tab title
    }

    public void dispose() {
        subscriptions.forEach(Subscription::cancel);
        subscriptions.clear();
    }
}
```

`PanelContext` has `plugin()`, `isShowing()`, `onShown(Runnable)`, `setBadge(String)` (null or empty removes it) and `reveal()` (opens the panel and selects its tab). `icon()` takes 16×16 SVG path data like transforms; without one, the page gets a puzzle-piece glyph.

**Theming.** Style with the theme's looked-up colours instead of fixed ones (API 6 plugins: see [Theming](#theming) for the kit's classes, which do this for you):

| Colour | Use |
|---|---|
| `-color-fg-default`, `-color-fg-muted`, `-color-fg-subtle` | text, secondary text, hints |
| `-color-bg-default`, `-color-bg-subtle`, `-color-bg-inset` | backgrounds: panel, raised rows / cards, wells |
| `-color-border-default`, `-color-border-muted` | borders and separators |
| `-color-accent-fg`, `-color-accent-emphasis`, `-color-accent-subtle` | the theme's accent: links, selected items |
| `-color-danger-fg`, `-color-warning-fg`, `-color-success-fg` | errors, warnings, success |
| `-bd-accent`, `-bd-accent-soft` | BlockDesigner's own accent (selection outlines, selected cards) |

For example `label.setStyle("-fx-text-fill: -color-fg-muted;")`. Standard controls (buttons, lists, fields) are themed already, and AtlantaFX style classes such as `flat`, `accent`, `small` and `danger` work too.

### Tools

A `PluginTool` is a button in the tool dock over the viewport, next to Select, Build and the brushes. While it is the active tool, the left and right mouse buttons, mouse moves, the wheel (when `scroll` returns true) and plain keys (when `key` returns true) go to its `ToolHandler`; the middle button still orbits and pans. Its `options()` show in a bar above the hotbar.

Condensed from [`WallTool.java`](examples/palette-tools/src/main/java/com/example/palette/WallTool.java): drag out a straight wall, with ghosts while dragging, the wheel for the height and Esc to cancel.

```java
final class WallTool implements PluginTool {
    public String id() { return "wall"; }
    public String name() { return "Wall"; }
    public String description() { return "drag to build a straight wall · wheel sets the height · Esc cancels"; }
    public String defaultKey() { return "Shift+K"; }   // ignored if BlockDesigner already uses the key

    public Options options() {
        return Options.builder()
                .integer("height", "Height", 3, 1, 64)
                .blockList("blocks", "Blocks", BlockPattern.parse("70%stone_bricks,30%mossy_stone_bricks", BlockState::parse))
                .toggle("hand", "Use the held block instead", false)
                .build();
    }

    public ToolHandler activate(ToolContext ctx) {
        return new ToolHandler() {
            BlockPos start, end;

            public void press(ToolEvent e) {
                if (e.button() != ToolEvent.Button.PRIMARY) return;
                e.hit().ifPresent(h -> { start = end = h.adjacent(); ctx.preview().ghost(cells()); });
            }

            public void drag(ToolEvent e) {
                if (start == null) return;
                e.hit().ifPresent(h -> { end = h.adjacent(); ctx.preview().ghost(cells()); });
            }

            public void release(ToolEvent e) {
                if (start == null) return;
                Map<BlockPos, BlockState> wall = cells();
                start = null;
                ctx.preview().clear();
                try (ToolContext.Stroke s = ctx.beginStroke("Build wall")) {   // one undo step
                    wall.forEach((p, st) -> s.world().set(p, st));
                }
            }

            public boolean key(String key) {
                if (!key.equals("Esc") || start == null) return false;     // false: the key does what it normally does
                start = null;
                ctx.preview().clear();
                return true;
            }

            Map<BlockPos, BlockState> cells() { /* the wall's blocks from start to end, ctx.options().integer("height") high */ }
        };
    }
}
```

- **`ToolEvent`:** `hit()` (the block, the face, the empty `adjacent` cell in front of it and its layer; the ground grid counts as a hit with no layer), `button()`, the modifiers (`shift()`, `ctrl()`, `alt()`), and the mouse ray (`rayOrigin`, `rayDir`).
- **`ToolHandler`:** `hover`, `press`, `drag`, `release`, `scroll(e, delta)` (+1 / -1 per notch; return true when used, otherwise the wheel zooms), `key(String)` (e.g. `"R"`, `"Shift+R"`, `"Esc"`, `"Enter"`) and `deactivate`. Every method has a do-nothing default.
- **`ToolContext`:** `options()` (read them each time: they change while the tool is active), `hand()`, `blocks()`, `preview()` (`ghost(map)`, where air entries are outlined in red; `outline(box)`; `clear()`) and `beginStroke(label)`.
- **Strokes:** a `Stroke` sees every visible layer merged, like `editWorld`, and is one undo step however many events it spans. Closing it commits; `cancel()` puts every block back. A stroke still open when the tool is deactivated is committed.
- **Key:** `defaultKey()` uses JavaFX `KeyCombination` form (`"Shift+K"`, `"J"`). Users can change it in `settings.json` under `pluginToolKeys`, e.g. `"palette-tools/wall": "J"`.

### Importers

A `PluginImporter` turns a file that isn't a schematic into blocks: an image into pixel art, a heightmap into terrain. Its extensions join the Import window's file filter and drag and drop (a real schematic format wins over an importer for the same extension). If it has options, a small dialog asks for them first. `importFile` runs on a background thread; the layers it returns are placed like an imported schematic.

Condensed from [`PixelArtImporter.java`](examples/palette-tools/src/main/java/com/example/palette/PixelArtImporter.java):

```java
final class PixelArtImporter implements PluginImporter {
    public String id() { return "pixel_art"; }
    public String displayName() { return "Pixel art from an image"; }
    public List<String> extensions() { return List.of("png", "gif", "bmp"); }

    public Options options() {
        return Options.builder()
                .choice("material", "Blocks", List.of("concrete", "wool", "terracotta"), "concrete")
                .choice("facing", "Lay out", List.of("upright", "flat"), "upright")
                .integer("maxSize", "Largest side (pixels)", 128, 8, 512)
                .build();
    }

    public List<ImportedLayer> importFile(Path file, OptionValues options, Progress progress, BlockCatalog blocks) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) throw new IOException("Not an image BlockDesigner can read");   // shown to the user
        Structure s = new Structure();
        // for each pixel: pick the block whose blocks.averageColor(...) is closest, s.set(x, y, z, block),
        // and after each row: progress.update((y + 1) / (double) h, "Pixel art: row " + (y + 1) + " of " + h);
        String name = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        return List.of(new ImportedLayer(name, s, null));   // null offset = the origin
    }
}
```

`ImportedLayer(name, blocks, offset)`: return several to place them together, with `offset` giving each layer's origin relative to the others.

### Exporters: options, summary, progress

API 2 exporters can add `options()` (drawn on the export card and remembered), `summary(merged)` (an extra line on the card; it runs on the JavaFX thread when the chosen layers change, so keep it quick), and use `Request.options()`, `Request.progress()` (shown under the card) and `Request.assets()`. From [`GimpPaletteExporter.java`](examples/palette-tools/src/main/java/com/example/palette/GimpPaletteExporter.java):

```java
public Options options() {
    return Options.builder()
            .integer("min", "Leave out blocks used fewer times than", 1, 1, 10_000)
            .choice("order", "Order", List.of("most used first", "by name"), "most used first")
            .build();
}

public String summary(Structure merged) {
    return counts(merged, 1).size() + " kinds of block";
}

public void export(Request r) throws IOException {
    Map<String, Long> counts = counts(r.merged(), r.options().integer("min"));
    // ... sort by r.options().choice("order"), then for each colour:
    r.progress().update(++i / (double) entries.size(), "Colour " + i + " of " + entries.size());
}
```

`Progress.update(fraction, message)` takes 0 to 1 (negative when unknown) and is safe to call from any thread.

### Scene events

Listen for changes with `ctx.on(type, listener)`. It returns a `Subscription`; `cancel()` stops it early, and listeners are removed automatically when the plugin is disabled.

```java
Subscription s = ctx.on(SceneEvent.BlocksChanged.class, e -> refresh(e.dirty()));
ctx.on(SceneEvent.class, e -> { /* every kind */ });
```

Events arrive on the JavaFX thread, merged to at most one of each kind per frame, so a brush stroke is one `BlocksChanged` whose `dirty` box (world coordinates) covers everything it touched.

| Event | When |
|---|---|
| `BlocksChanged(layers, dirty)` | Blocks changed (edits, undo, redo, imports); layers bottom first |
| `LayersChanged()` | Layers were added, removed, reordered, renamed, moved, shown or hidden |
| `ActiveLayerChanged(layer)` | Another layer became active (empty when there are none) |
| `SelectionChanged(region)` | The block selection or the `//pos1 //pos2` region changed |
| `ProjectOpened(file)` | A project was opened, or a new one started (empty file) |

A listener that throws is logged against your plugin and doesn't affect others.

### The block catalog

`ctx.blocks()` (also in transform, tool and importer contexts) is a `BlockCatalog` over the loaded registry, including mods. Before a Minecraft version is loaded, the vanilla creative-menu blocks stand in.

- `resolve("oak_stairs[facing=east]")` completes a block state, or throws `IllegalArgumentException` for an unknown block. `exists(id)` checks an id.
- `family(state)` gives the block's `BlockFamily`: oak → planks, stairs, slab, fence, fence gate, door, trapdoor, button, pressure plate, signs, log, wood and stripped logs; stone bricks → bricks, stairs, slab, wall.
- `sameShape(oak_stairs, spruceFamily)` gives `spruce_stairs` with the same facing. The Gradient transform uses it to keep shapes: `c.blocks().family(pick).flatMap(f -> c.blocks().sameShape(st, f)).orElse(pick)`.
- `variant(state, "mossy")`, `variant(state, "cracked")`, `variant(state, "exposed")` and `withoutVariant(...)` swap materials, keeping shape and properties. The modifiers are `cracked`, `mossy`, `chiseled`, `polished`, `smooth`, `cut`, `exposed`, `weathered`, `oxidized` and `waxed` (`BlockFamilies.MODIFIERS`).
- `withId(state, "birch_stairs")` changes the block, keeping the properties the new one also has.
- `averageColor(state)` (0xAARRGGBB, opaque; from the top-face texture) and `displayName(state)`.

Families and variants are worked out from block ids and checked against the registry, so modded blocks named the vanilla way work too.

### Asset access

`ctx.assets()` (and `Request.assets()` in exporters) is an `AssetAccess` over the loaded game, mod and resource-pack assets. Everything is plain data (no AWT or JavaFX types) and safe to use on the export thread, for exporters that write meshes (OBJ, glTF) or images.

- `available()`: whether Minecraft's assets are loaded.
- `quads(state, side -> culled)`: the block model as textured quads in block space (positions, atlas uvs, face, cull side, tint, texture id). Pass `side -> false` for the whole model.
- `atlas()`: the block texture atlas as ARGB pixels and PNG bytes.
- `texture("minecraft:block/stone")`: a raw resource file's bytes (a texture id, or a full path such as `assets/minecraft/models/block/stone.json`).

### Bedrock NBT

`NbtIO.readLE` / `NbtIO.writeLE` in `core` read and write little-endian NBT, as used by Bedrock's `.mcstructure`, for a Bedrock format plugin.

## API 3 extension points

Everything in this section needs `"api": 3` in the manifest. The examples come from [`plugins/reference-planes`](plugins/reference-planes/src/main/java/io/blockdesigner/refplanes).

### Scene objects

A `SceneObject` is something in the scene that isn't blocks: a reference image, a guide, a marker. You write what makes your object different: how it draws, its right-click entries and its own state. BlockDesigner handles everything objects have in common:

- **Layers panel:** a row above the layers with your type's badge (`REFERENCE`), with the eye and lock buttons, click to select and double-click to rename.
- **3D view:** your drawing, with the object's `Pose` applied. A click selects the object, and a right-click opens its menu.
- **Move and Rotate tools:** they drive the pose (an object moves and turns freely, and Ctrl snaps to whole blocks and 15°). Delete removes the selected object.
- **Undo and projects:** every change can be undone, and objects are saved in the `.bdproj`. Objects whose plugin is off are kept in the project and come back when it is on again.

Register a `SceneObjectType` in `enable`. `create()` makes an empty object, which `load` fills when a project is opened. A type that lists `extensions()` also takes those files from Import and drag and drop, through `open(file, view)`:

```java
final class ReferenceType implements SceneObjectType {
    public String id() { return "reference"; }             // saved in projects: never change it
    public String name() { return "Reference image"; }
    public String badge() { return "REFERENCE"; }           // the tag in the Layers panel
    public SceneObject create() { return new ReferenceImage(ctx, this); }
    public List<String> extensions() { return List.of("png", "jpg", "jpeg", "gif", "bmp"); }

    public void open(Path file, ViewInfo view) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String blob = ctx.objects().storeBlob(bytes);        // kept in the project; save the key, not the bytes
        ReferenceImage ref = new ReferenceImage(ctx, this);
        // ... set it up from the picture
        ctx.objects().add(id(), "sketch", new Pose(view.target(), view.side().map(ViewInfo.Side::facing).orElse(new Vec3(0, 0, 0)),
                new Vec3(16, 16, 16)), ref);                // one undo step; the new object is selected
    }
}
```

The object draws itself in its own space, once per frame of the 3D view, and keeps its state small for undo:

```java
final class ReferenceImage implements SceneObject {
    public void draw(ViewInfo view, Drawing out) {
        if (!settings.visibleIn(view)) return;               // e.g. only when view.isOrthoSide(Side.FRONT)
        out.image(image(), settings.corners(), settings.uv(), settings.opacity(), settings.depth());
    }

    public String description(ObjectHandle self) { return "sketch.png · 1920×1080"; }   // under the name in the Layers panel
    public List<MenuItem> menu(ObjectHandle self) { return ReferenceMenu.items(self, this, type); }
    public byte[] save() { return settings.encode(); }       // your state only: BlockDesigner keeps the name and pose
    public void load(byte[] data) throws IOException { settings = ReferenceSettings.decode(data); }
    public Set<String> blobs() { return Set.of(settings.blob()); }                     // the blobs to save with it
}
```

- **`Drawing`:** `image(ImageData, corners, uv, opacity, depth)` draws a picture on a flat four-cornered patch, seen from both sides. UV runs 0..1 across the picture, with v from the top down, and outside 0..1 is see-through. `depth` is `BEHIND_BLOCKS` (a backdrop), `IN_SCENE` (blocks in front hide it) or `IN_FRONT` (over everything). `line(from, to, argb)` draws a line. The images are also what the mouse picks the object by.
- **`ImageData`:** width, height and ARGB pixels. BlockDesigner uploads each instance to the GPU once, so keep one instance per picture and don't change its pixels. Keep pictures to `ImageData.MAX_SIZE` (4096) a side.
- **`ViewInfo`:** whether the view is orthographic, which axis view it is (`side()`: FRONT looks north, RIGHT looks west, TOP looks down with north up), and the eye, look direction and orbit target. `Side.facing()` is the rotation that turns something drawn facing +Z towards that view, like Blender's "align to view".
- **`Pose`:** position, rotation (degrees, XYZ Euler as in Blender) and scale. A point of the object goes to `position + R·(scale∘local)`. It has helpers for `translated`, `rotatedAbout(axis, degrees, pivot)`, `scaledBy`, `toWorld`, `toLocal` and `axis`.
- **`ObjectHandle`** (BlockDesigner's side of your object): `name()`, `pose()`, `visible()`, `locked()` and their setters, each one undo step. `edit(label, change)` records a change to your own state: it runs `change`, and undo `load`s what `save()` returned before. `refresh()` redraws without an undo step (for a live slider preview). There are also `select()`, `selected()`, `remove()` and `exists()`.
- **`ctx.objects()`** (`SceneObjects`): your objects (`list()`), `add(...)`, `selected()`, the current `view()`, and blobs. `storeBlob(bytes)` returns a key that is the same for the same bytes, and `blob(key)` reads it back. Only blobs that some object lists in `blobs()` are saved.
- **Menus:** `menu(self)` returns JavaFX `MenuItem`s, as panels do. `CustomMenuItem` with `hideOnClick` false can hold fields and sliders. BlockDesigner adds Rename, Focus camera, Hide, Lock and Delete after them.
- **Errors:** an exception from `draw`, `load` or `menu` is logged against your plugin, and the object is skipped for that frame.

## API 4 extension points

### Settings in the Settings window

`ctx.registerSettings(options, onChange)` gives the plugin a page in BlockDesigner's Settings window, under
**Plugins** (from 0.4.24; before that they were at the top of the plugin's tab). They use the same `Options`
as transforms and tools, so BlockDesigner draws the controls (sliders, check boxes, choices, block pickers) and a
**Reset to defaults** button, and keeps the values between runs. `onChange` runs on the JavaFX thread with the current
values, once straight away and after every change; `ctx.settings()` reads them at any time. Register once, from
`enable`. Plugins that use it declare `"api": 4`.

```java
ctx.registerSettings(Options.builder()
        .decimal("height", "Height (blocks)", 16, 1, 512)
        .toggle("snap", "Snap to blocks", true)
        .build(), v -> height = v.decimal("height"));
```

## API 5 extension points

Plugins that use any of these declare `"api": 5` (BlockDesigner 0.4.17 and later).

### Tools that work on the selection

A tool whose `selects()` returns true leaves the left button to block selection, exactly as in Select mode, and gets
the right button, the wheel and keys. Its `ToolContext` can read the selection and run a transform on it:
`previewTransform(transform, options, seed)` shows the result as ghosts, `applyTransform(...)` writes it as one undo
step, both with the same seed so what was previewed is what gets applied. `ToolHandler.optionsChanged()` says when the
options bar changed, and `SceneEvent.SelectionChanged` when the selection did, so the preview can follow both.

### Options that show only for some choices

`showWhen(choiceKey, values...)` after an option shows it only while an earlier choice option has one of those values,
so a tool with a Mode choice shows just the options of the chosen mode:

```java
Options.builder()
        .choice("mode", "Mode", List.of("Swap", "Gradient"), "Swap")
        .block("from", "Replace", BlockState.of("oak_planks")).showWhen("mode", "Swap")
        .blockList("blocks", "Blocks", List.of(BlockState.of("stone"), BlockState.of("andesite"))).showWhen("mode", "Gradient")
        .build();
```

### Hotbar, icons and your own tools

`ctx.hotbar()` and `ctx.setHotbar(blocks)` read and fill the hotbar (up to nine blocks). `ctx.blockIcon(block)` gives
a block's icon for your panels. `ctx.pickTool(id)` picks one of your tools and `ctx.setToolOptions(id, v -> ...)`
changes its options, for example from a list of presets in a panel.

## API 6 extension points

Plugins that use these declare `"api": 6` (BlockDesigner 0.4.24 and later).

### Resource packs

`ctx.resourcePacks()` lists the resource packs layered on the Minecraft assets, lowest priority first (zip files or
folders). `ctx.useResourcePacks(packs)` reloads the assets with other packs on top of the same game jar and mods, and
keeps them as the user's choice (as if picked in Settings). Loading runs in the background; the view shows the new
textures when it is done, and missing files are skipped. Resource Tracker uses it to show blocks with the textures a
game uses:

```java
ctx.useResourcePacks(List.of(Path.of("C:/Users/me/AppData/Roaming/.minecraft/resourcepacks/Faithful.zip")));
```

A plugin that should also load on older BlockDesigners can call it by reflection and declare a lower `api`:
`PluginContext.class.getMethod("useResourcePacks", List.class)` throws `NoSuchMethodException` there.

### Building panels with the UI kit

The package `io.blockdesigner.plugin.ui` holds ready-made pieces in BlockDesigner's look. They are plain JavaFX classes
in the plugin API jar, and at runtime they come from the installed BlockDesigner, so a plugin's pages always match the
app. Each one attaches the kit's stylesheet itself, and owns its spacing: a page built from them has no pixel numbers.

| Piece | What it is |
|---|---|
| `PanelScaffold` | The page: a sticky `top(...)` (a scope row, a preview), the content column from `add(...)` (scrolls as one), or a `grow(node)` that takes the free height (a list, a chat), and a sticky `footer(...)` (a `Banner`, progress, the `ActionBar`). `empty(EmptyState)` with `showEmptyProperty()` replaces the content. Below 300 px it sets `:narrow` (`narrowProperty()`). |
| `Section` | A heading (sentence case) and its content; `actions(...)` puts icon buttons or links on the right of the heading, `badge(StatusBadge)` after the title, `collapsible(expanded)` folds it (one level only). |
| `Form` | Labels on the left (one width per form, 96–140 px), controls on the right; `row(label, control)` or a full-width `row(node)`, then `.help(text)`, `.unit("blocks")`, `.error(message)`, `.enabledWhen(...)`, `.shownWhen(...)`. Narrow forms put the labels above. Controls that fill the width share one column for their units. |
| `ActionBar` | Up to three buttons for the section or page (the accent one grows); `Controls.spacer()` pushes the rest right. Stacks the buttons when narrow. |
| `StatusBadge` | A dot and a word in a `Tone`: "Running", "Connected", "Failed". Never colour alone. |
| `Banner` | A message next to what it's about, with an optional action: `show(Tone.DANGER, "Couldn't read it", "Open another…", this::choose)`; hidden until shown. |
| `EmptyState` | An icon, one sentence, a hint and up to two buttons, for a list or page with nothing in it yet. |
| `ItemList` / `ItemRow` | A virtualised list whose rows fit the width: `ItemRow.of(title).image(icon)` or `.swatch(argb)`, `.meta(text, tone)`, `.below(node)`, up to three `.trailing(...)`. `visibleRows(min, max)` makes it as tall as its rows; `onOpen`, `onDelete`, `menu` add Enter / double-click, Delete and a right-click menu. Your own cell factory works too. |
| `Segmented` | A few mutually exclusive views side by side ("Picture / Regions / Blocks"). |
| `Controls` | `primary`, `button`, `danger`, `iconButton(Icon, tooltip, action)` (tooltip required), `toggle`, `busy(button, true)`, `hint`, `caption`, `pathCaption`, `link`, `search`, `blockIcon(icon, colour, size)`, `spacer`, `show(node, shown)`. |
| `Icon` | Feather icons (`ADD`, `CHECK`, `CLOSE`, `COPY`, `EDIT`, `FOLDER`, `IMAGE`, `INFO`, `LINK`, `PAPERCLIP`, `REFRESH`, `SAVE`, `SEARCH`, `SEND`, `SETTINGS`, `SHUFFLE`, `TRASH`, `UNDO`, `WARNING`…) drawn in the text colour: `Icon.REFRESH.node()`. |
| `Theme` | The spacing steps (`XS 4`, `SM 8`, `MD 12`, `LG 16`, `XL 24`), `NARROW`, `STYLESHEET`, and `root(node)` for a window of your own. |

Keep one layout order on every page: the page's controls at the top in the order they're used (most used first, related
fields together, advanced ones last, folded), and status and descriptions at the bottom: what it's doing, what went
wrong, what it is. Only set-once settings belong in the Settings window; options used while working stay on the page.

```java
Form form = new Form();
form.row("Count in", scopeBox);
Section count = new Section("Count", form)
        .actions(Controls.iconButton(Icon.REFRESH, "Count again", this::recount));
Banner banner = new Banner();
PanelScaffold page = new PanelScaffold()
        .add(count)
        .grow(new Section("Items", search).grow(list))
        .footer(banner, totals, new ActionBar(Controls.button("Copy list", null, this::copy),
                Controls.spacer(), Controls.danger("Reset…", "Forget what you gathered", this::reset)));
```

`ctx.ui().optionsForm(options, rememberAs, onChange)` gives the app's own form for some `Options` (the one in the
transform dialog and the Import window, with block slots, icons, groups, help and units) to put on a page.

### Options: groups, help, units and enabledWhen

API 6 adds metadata to `Options`, which every form that draws them shows (the Settings window, transform and Import
dialogs, the tool options bar, `ui().optionsForm`):

```java
Options.builder()
        .group("Shape")                                   // a heading over the options that follow
        .integer("width", "Width", 64, 4, 512).unit("blocks")
        .help("The picture is scaled to this many blocks across.")
        .toggle("hollow", "Hollow", false)
        .integer("wall", "Wall", 1, 1, 8).unit("blocks").enabledWhen("hollow")   // greyed out while Hollow is off
        .group("Background")
        .decimal("tolerance", "Tolerance", 0.08, 0, 0.4).unit("%")               // a fraction, shown as a percentage
        .advanced("Picture adjustments")                  // a group drawn folded
        .decimal("contrast", "Contrast", 1, 0, 2)
        .build();
```

`help`, `unit` and `enabledWhen` apply to the option added last; `unit` only to numbers, and `"%"` only to a decimal
between 0 and 1. `option(Option)` adds an option record as it is (to combine the options of several transforms; copy
their `help`, `unit` and `enabledWhen` too). Read them with `groups()`, `help(key)`, `unit(key)`, `enabledWhen(key)`
and `enabled(key, values)`. Advanced groups remember whether the user opened them.

### Settings: updateSettings and openSettings

A plugin's settings (`registerSettings`) are on its page in the Settings window, under **Plugins**, with a **Reset to
defaults** button. The tab's gear and the Overview's **Open settings…** button open that page; `ctx.openSettings()`
does too (to the Overview for a plugin without settings). `ctx.updateSettings(v -> v.with("mobs", true))` changes
them as if the user had: they are saved, `onChange` runs and an open Settings window redraws. Use it for a control on a
page that shows the same value, and to move settings a plugin used to keep in its own file over once.

### Keys

A tool's `defaultKey()` and every `PluginAction` can be given a key by the user in **Settings › Keybinds**, where each
plugin has its own group; actions have no key by default. A key BlockDesigner itself uses wins, and clashes show in red.

### Page status and navigation

`ctx.setPanelStatus(panelId, Tone.SUCCESS, "Connected to 2 games")` puts a coloured dot on a page's button, with the
text as its tooltip; `null` as the tone removes it. It works before the page was ever built, so a background job can
show how it stands. `ctx.showPanel(panelId)` opens the plugin's tab at one of its pages (reopening the tab if it was
closed), for a link from one page to another or from a scene object's menu.

### Dialogs

`ctx.ui()` gives dialogs in the app's look, owned by the main window, in the current theme:

- `confirm(title, message, confirmLabel, destructive)`: `destructive` draws the button red and keeps Cancel the
  default, so Enter doesn't delete;
- `askText(title, label, initial)` and `choose(title, label, choices, initial)`;
- `style(new Dialog<ButtonType>())` for a dialog of your own (add `bd-dialog` to its pane for the kit's padding);
- `owner()` for file choosers, `copyText(text, toast)`, `open(path)` (a file with its app, a folder in Explorer, off
  the UI thread), `darkProperty()`.

### Remembered forms

`ui().optionsForm(options, "panel", onChange)` keeps the form's values in BlockDesigner's settings between runs, under
the plugin's id. `"importer/<id>"` and `"exporter/<id>"` share the values of the plugin's importer or exporter with
that id, so a page and the Import window show the same settings. `null` keeps nothing: fill it with `setValues`.

### Theming

The kit uses only the theme's looked-up colours, so it follows all six themes, light and dark; so should your own
nodes. Use the kit's style classes rather than inline styles. These are stable: `bd-root`, `bd-scaffold`, `bd-top`,
`bd-content`, `bd-footer`, `bd-section`, `bd-section-header`, `bd-section-title`, `bd-hint`, `bd-caption`, `bd-form`,
`bd-form-label`, `bd-action-bar`, `bd-badge`, `bd-banner`, `bd-empty`, `bd-list`, `bd-row`, `bd-row-title`,
`bd-row-meta`, `bd-segmented`, `bd-segment`, `bd-block-slot`, `bd-icon`, `bd-card`, `bd-frame`, `bd-mono`,
`bd-search`, `bd-progress`, `bd-inline-field`, `bd-dialog`; and the pseudo-classes `:narrow`, `:error`, `:accent`,
`:success`, `:warning`, `:danger`, `:busy`. `Tone.apply(node, tone)` sets the tone pseudo-class on any node
(`NEUTRAL`, `ACCENT`, `SUCCESS`, `WARNING`, `DANGER`), for example a caption that turns green when something is done.

The kit's icons are from [Feather](https://feathericons.com) by Cole Bemis (MIT licence, included in the API jar as
`io/blockdesigner/plugin/ui/FEATHER-LICENSE.txt`).

## API 7 extension points

Plugins that use these declare `"api": 7` (BlockDesigner 0.4.26 and later).

### Opening a file as the project

`ctx.openFile(path, done)` opens a file the way File › Open does: a `.bdproj` is opened, and a schematic BlockDesigner
reads (`.schem`, `.litematic`, `.nbt`…) becomes a new, unsaved project named after the file. When the open project has
changes, the user is asked first ("Save changes to Castle?": Save, Don't save, Cancel). `done` is called once, on the
JavaFX thread, with an `OpenResult`: `OPENED` (after `SceneEvent.ProjectOpened`), `CANCELLED`, or `FAILED` with a short
`message()` ("Could not read Castle.litematic: …"). `ctx.ui().toFront()` brings the window forward, for a request that
came from outside BlockDesigner (Resource Tracker does both when a BlockCompanion game sends a build to edit):

```java
ctx.ui().toFront();
ctx.openFile(file, r -> {
    if (r.isOpened()) ctx.status("Opened " + file.getFileName());
    else if (r.status() == OpenResult.Status.FAILED) ctx.toast(r.message());
});
```

## Rules of the road

- **Threads:** every call into a plugin (`enable`, actions, commands, transforms, panels, tools, events, exporter `summary`) runs on the JavaFX thread. `PluginExporter.export` and `PluginImporter.importFile` run on a background thread; exporters get their own copies of the layers. Do long work on your own thread and come back with `ctx.runOnUiThread(...)` before you touch the scene.
- **Undo:** change blocks through `ctx.editWorld(...)`, a command's `c.world()`, a transform's `c.world()`, a tool's stroke, or `ctx.editor().edit(layer, label)` sessions. Don't mutate `layer.structure()` directly.
- **Errors:** in commands and transforms, throw `IllegalArgumentException("friendly message")` to show it to the user. Other exceptions are caught, logged in the Plugins window and shown as a toast.
- **Ids:** format ids must be unique across all plugins; exporter, importer, transform, panel and tool ids only within your plugin.
- **Your files:** `ctx.dataFolder()` belongs to your plugin. `ctx.log(...)` writes to the log shown in the Plugins window.
- **Trust:** plugins run with the same access as BlockDesigner itself. Users are warned before they install one.

## Limits and what's not supported yet

- Panels are pages of the plugin's tab on the right; `PluginPanel.Dock` is not used, so there is no left or bottom dock yet.
- Plugin tool and action keys are set in Settings › Keybinds; a default key BlockDesigner already uses is ignored.
- There is no secret (password) option kind: API keys and the like need a field of the plugin's own.
- Plugin tools don't receive input in flight mode.
- `/transform <id>` only opens the dialog; applying straight from the command line with options isn't supported.
- Preview ghosts are drawn translucent over the old blocks rather than replacing them in the view.
- Transforms change blocks only. `Stroke.cancel()` reverts blocks, not entities added through the stroke's world.
- `averageColor` averages the top-face texture, not a rendered inventory icon.
- `AssetAccess.quads` covers baked block models; block-entity models (chests, banners, signs) aren't included.
- There is no Bedrock `.mcstructure` format plugin yet, only the little-endian NBT it needs.
- Scene objects: the Scale tool doesn't drive them yet (use `Pose.scaledBy` from your own menu). They can't be reordered in the Layers panel or selected several at a time. The mouse picks them by their whole image patch, see-through parts included.

The design record, including how each piece is built and tested, is [docs/plugin-api-v2.md](docs/plugin-api-v2.md).
