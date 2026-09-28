# Registering features

Register everything from `enable(PluginContext ctx)`. Each section below has a short example; the worked examples
with every option are in [PLUGINS.md](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md), and every member
is listed in the [API reference](https://github.com/doolecg/BlockDesigner/blob/main/docs/plugin-api-reference.md).
Commands have [their own page](BlockEdit-Commands.md).

**Ids.** Format ids must be unique across all plugins (they share `Schematics`' list). Exporter, importer,
transform, panel and tool ids only need to be unique within your plugin; BlockDesigner keys them as
`<plugin id>/<id>`. Registering the same id twice throws `IllegalArgumentException`, which fails `enable`. Allowed
characters: transforms `a-z 0-9 _`; panels and tools `a-z 0-9 _ . -`. Keep ids stable: saved options and key binds
are stored under them.

## Menu actions (API 1)

An entry in the Plugins menu. The `Runnable` runs on the JavaFX thread; an exception is logged and shown as a toast.

```java
ctx.registerAction(new PluginAction("Add test platform", "A 9×9 stone platform in a new layer", () -> {
    Structure s = new Structure();
    for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) s.set(x, 0, z, BlockState.of("minecraft:smooth_stone"));
    ctx.addLayer("Platform", s);   // one undo step
}));
```

## Exporters (API 1, options from API 2)

A card in the Export window for output that isn't a schematic. `export` runs on a background thread with its own
copy of the layers.

```java
ctx.registerExporter(new PluginExporter() {
    public String id() { return "bom"; }
    public String displayName() { return "Bill of materials"; }
    public String extension() { return "csv"; }          // or writesFolder() -> true
    public Options options() { return Options.builder().integer("min", "Leave out fewer than", 1, 1, 10_000).build(); }
    public void export(Request r) throws IOException {
        StringBuilder sb = new StringBuilder("block,count\n");
        r.merged().stateCounts().forEach((st, n) -> {
            if (n >= r.options().integer("min")) sb.append(st.name()).append(',').append(n).append('\n');
        });
        Files.writeString(r.target(), sb);
        r.progress().update(1, "Done");
    }
});
```

## Schematic formats (API 1)

A `SchematicFormat` (from `core`, `io.blockdesigner.core.formats`) appears in both Import and Export. NBT formats
implement `detect`, `read` and `write`; other storage returns `false` from `nbtBased()` and overrides `readFile` and
`writeFile`. `canRead()` / `canWrite()` make it one-way. Example: `TextFormat` in
[`examples/hello-plugin`](https://github.com/doolecg/BlockDesigner/blob/main/examples/hello-plugin/src/main/java/com/example/hello/TextFormat.java).

```java
ctx.registerFormat(new TextFormat());   // id "hello-text", extension .bdtxt
```

## Importers (API 2)

Turn a file that isn't a schematic into layers. The extensions join Import's file filter and drag and drop (a real
format for the same extension wins). Options, if any, are asked for first; `importFile` runs on a background thread.

```java
ctx.registerImporter(new PluginImporter() {
    public String id() { return "heightmap"; }
    public String displayName() { return "Terrain from a heightmap"; }
    public List<String> extensions() { return List.of("png"); }
    public List<ImportedLayer> importFile(Path file, OptionValues o, Progress progress, BlockCatalog blocks) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) throw new IOException("Not an image");     // the message is shown to the user
        Structure s = new Structure();
        // ... fill s, calling progress.update(fraction, "Row y of h") now and then
        return List.of(new ImportedLayer("Heightmap", s, null));    // null offset = the origin
    }
});
```

## Transforms (API 2)

Change existing blocks with a previewed dialog: Plugins › Transform, the right-click menu, `/transform <id>`. `apply`
runs once per preview (writes show as ghosts) and once more on Apply (one undo step). Use only `c.random()` for
randomness so preview and Apply match; throw `IllegalArgumentException` for a message in the dialog.

```java
ctx.registerTransform(new PluginTransform() {
    public String id() { return "moss"; }                 // a-z 0-9 _
    public String name() { return "Mossify"; }
    public Options options() { return Options.builder().decimal("amount", "Mossy", 0.3, 0, 1).build(); }
    public boolean randomized() { return true; }          // seed field and Reroll button
    public void apply(TransformContext c) {
        for (BlockPos p : c.solidBlocks()) {
            if (c.random().nextDouble() >= c.options().decimal("amount")) continue;
            c.blocks().variant(c.world().get(p), "mossy").ifPresent(v -> c.world().set(p, v));
        }
    }
});
```

`scope()` picks `SELECTION`, `LAYER` or `SELECTION_OR_LAYER` (the default).

## Panels (API 2)

A page of the plugin's tab on the right with your own JavaFX content. Build nodes only in `create` (it runs the first
time the page is shown), refresh while visible, and release listeners in `dispose`.

```java
ctx.registerPanel(new PluginPanel() {
    private Subscription sub;
    public String id() { return "stats"; }
    public String title() { return "Stats"; }
    public Node create(PanelContext panel) {
        Label label = new Label();
        Runnable refresh = () -> label.setText(panel.plugin().scene().layers().size() + " layers");
        sub = panel.plugin().on(SceneEvent.LayersChanged.class, e -> { if (panel.isShowing()) refresh.run(); });
        panel.onShown(refresh);
        refresh.run();
        return new VBox(8, label);
    }
    public void dispose() { if (sub != null) sub.cancel(); }
});
```

Style with the theme's looked-up colours (`-color-fg-muted`, `-color-bg-subtle`, …) so light and dark themes work;
see PLUGINS.md, [Panels](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md#panels).

## Tools (API 2, selection tools API 5)

A tool in the tool dock. While active, mouse buttons, moves, the wheel (when `scroll` returns true) and keys (when
`key` returns true) go to its `ToolHandler`. Options show in the options bar; read them each time.

```java
ctx.registerTool(new PluginTool() {
    public String id() { return "pillar"; }
    public String name() { return "Pillar"; }
    public String defaultKey() { return "Shift+K"; }      // ignored if BlockDesigner already uses it
    public Options options() { return Options.builder().integer("height", "Height", 5, 1, 64).build(); }
    public ToolHandler activate(ToolContext t) {
        return new ToolHandler() {
            public void press(ToolEvent e) {
                if (e.button() != ToolEvent.Button.PRIMARY) return;
                e.hit().ifPresent(h -> {
                    BlockState block = t.hand().orElse(BlockState.of("minecraft:stone"));
                    try (ToolContext.Stroke s = t.beginStroke("Pillar")) {   // one undo step
                        for (int y = 0; y < t.options().integer("height"); y++) s.world().set(h.adjacent().add(0, y, 0), block);
                    }
                });
            }
        };
    }
});
```

With API 5 a tool can return `selects() == true` to leave the left button to block selection, read
`t.selection()`, and `previewTransform` / `applyTransform` a transform on it (Palette Tools' Palette tool does this).

## Scene objects (API 3)

Things in the scene that aren't blocks: a row in the Layers panel, drawn in the 3D view, moved by the gizmo tools,
saved in the project, undoable. You register a `SceneObjectType` and implement `SceneObject` (`draw`, `save`,
`load`, optionally `menu`, `description`, `blobs`).

```java
ctx.registerObjectType(new SceneObjectType() {
    public String id() { return "marker"; }               // saved in projects: never change it
    public String name() { return "Marker"; }
    public String badge() { return "MARKER"; }
    public SceneObject create() { return new Marker(); }  // filled by load() when a project opens
});
// adding one (one undo step, selected afterwards); Vec3 is io.blockdesigner.plugin.ToolEvent.Vec3:
ctx.objects().add("marker", "Spawn", new Pose(new Vec3(0, 64, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1)), new Marker());
```

`Marker.draw(view, out)` draws in the object's own space with `out.line(from, to, argb)` and
`out.image(...)`; `save()` returns only your own state (BlockDesigner keeps name and pose). Change your state through
`ObjectHandle.edit(label, change)` so it is undoable. Full example: BlockDesigner's built-in reference images
([`app/.../refplanes`](https://github.com/doolecg/BlockDesigner/tree/main/app/src/main/java/io/blockdesigner/app/refplanes),
once the Reference Planes plugin), and PLUGINS.md,
[Scene objects](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md#scene-objects).

## Settings (API 4)

The plugin's page in the Settings window (from 0.4.24; before that the top of the plugin's tab), with Reset to
defaults, kept between runs. `onChange` runs once straight away with the saved values and after every change.
Register once. Put only set-once settings there (defaults, connections, behaviour); options used while working
belong on the plugin's pages. `ctx.updateSettings(change)` changes them as if the user had, `ctx.openSettings()` opens
the page (API 6).

```java
ctx.registerSettings(Options.builder()
        .toggle("mobs", "Count mobs as spawn eggs", false)
        .choice("sort", "Sort by", List.of("most left", "most needed", "name"), "most left")
        .build(), v -> tracker.configure(v.toggle("mobs"), v.choice("sort")));
```

More on where the values live: [Storing data and settings](Storing-Data-And-Settings.md).

## Pages built with the UI kit (API 6)

From API 6 a panel's `create` returns a `PanelScaffold` from `io.blockdesigner.plugin.ui`, filled with `Section`s,
`Form`s, an `ItemList` and an `ActionBar`, so it looks like BlockDesigner's own pages without any pixel numbers.
Controls go at the top, status and descriptions at the bottom. `ctx.setPanelStatus(panelId, tone, text)` puts a dot on
the page's button (even before it is built), `ctx.showPanel(panelId)` opens it, and `ctx.ui()` gives dialogs and the
app's options form. See PLUGINS.md, [Building panels with the UI kit](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md#building-panels-with-the-ui-kit).

## Keys

Users give tools and Plugins-menu actions keys in Settings › Keybinds, in a group per plugin. `PluginTool.defaultKey()`
is the tool's key until they change it; actions have none by default.

## Options (API 2)

Transforms, tools, exporters, importers and settings describe their parameters with `Options`; BlockDesigner draws
the controls and hands you valid `OptionValues` (numbers clamped, stale saved values reset to defaults). Builders:
`decimal`, `integer`, `toggle`, `block`, `blockList`, `choice`, `text`, `file`, and (API 5) `showWhen(choiceKey,
values…)` after an option to show it only for some choices. Details:
PLUGINS.md, [Options](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md#options-parameters-without-writing-ui).

## Scene events (API 2)

```java
Subscription s = ctx.on(SceneEvent.BlocksChanged.class, e -> refresh(e.dirty()));
```

`BlocksChanged(layers, dirty)`, `LayersChanged()`, `ActiveLayerChanged(layer)`, `SelectionChanged(region)`,
`ProjectOpened(file)`; `SceneEvent.class` hears all. Merged to at most one of each kind per frame, on the JavaFX
thread; removed automatically when the plugin is unloaded.
