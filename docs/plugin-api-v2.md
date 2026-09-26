# Plugin API 2

Design notes for the second version of BlockDesigner's plugin API: what it adds, how it is built, the decisions
behind it and what is still open. The user-facing guide is [PLUGINS.md](../PLUGINS.md), every public type is listed
in [plugin-api-reference.md](plugin-api-reference.md), and the reference plugin is
[`examples/palette-tools`](../examples/palette-tools). Where this record and PLUGINS.md differ, PLUGINS.md describes
the current behaviour.

## Goals

- Let plugins do the things users keep asking for (weathering, palette swaps, gradients, pixel art, custom tools,
  live stats panels) without reaching into app internals.
- Simple plugins need no JavaFX: parameters are declared, BlockDesigner draws them.
- Every edit stays undoable and previews never touch the project.
- API 1 plugins keep working unchanged.

## Versioning

`PluginApi.VERSION` is now 2. A plugin declares the version it needs with `"api"` in `blockdesigner-plugin.json`;
BlockDesigner refuses plugins asking for a newer one (state *incompatible*, with a message) and loads older ones. All
additions are new interfaces, new default methods, or new methods on `PluginContext` (which only the app implements),
so API 1 jars need no rebuild. `PluginExporter.Request` grew three components; its old six-argument constructor is
kept.

## Shared pieces

### Options and OptionValues

`Options` is an immutable, ordered list of typed parameters built with `Options.builder()`:
`integer`, `decimal`, `toggle`, `block`, `blockList` (a weighted `BlockPattern`, WorldEdit's `70%stone,30%andesite`
syntax), `choice`, `text`, `file`. `Options.none()` means "run straight away".

`OptionValues` is immutable. Typed getters (`integer`, `decimal`, `toggle`, `block`, `blockList`, `choice`, `text`,
`file` → `Optional<Path>`) throw a clear `IllegalArgumentException` for an unknown key or the wrong type. `with(key,
value)` clamps numbers to their range and rejects unknown choices, so values are always valid.

Persistence: `toStrings()` / `fromStrings()` give a text form; anything unreadable or out of date falls back to the
default, so saved values survive plugin updates. The app keeps them in `settings.json` under `pluginOptions`, keyed
`"<plugin>/<kind>/<id>"` (`OptionStore`), plus a side map for extra numbers such as a transform's last seed.

The app renders options with `OptionsEditor` (sliders show 0..1 ranges as percentages; block fields accept typed
states and have a "use held block" button; pattern fields validate as you type). It is used by the transform dialog,
the tool options bar, the export card and the import dialog.

### Scene events

`ctx.on(Class<E extends SceneEvent>, Consumer)` returns a `Subscription` (also `AutoCloseable`). The sealed
`SceneEvent` has `BlocksChanged(layers, dirty)`, `LayersChanged()`, `ActiveLayerChanged(Optional<Layer>)`,
`SelectionChanged(Optional<Box>)` and `ProjectOpened(Optional<Path>)`.

`SceneEventBus` listens to the `Scene`, gathers changes and delivers them once on the next UI pulse
(`Platform.runLater`): at most one event per kind per frame, in a fixed order (project, layers, active layer, blocks,
selection). The dirty box is converted to world space and merged; layers are sorted bottom first and dropped if gone.
Nothing is queued while nobody listens. The selection box is measured only when the event is delivered. A listener
that throws is logged against its plugin and doesn't affect others. Listeners are removed when their plugin unloads.

Selection changes come from the viewport's `selectionChanged()` / `regionChanged()` and, because many code paths edit
the selection directly, from a per-frame check of the selected-block count and the //pos region.

### BlockCatalog and block families

`BlockCatalog` (`ctx.blocks()`, and in transform, tool and importer contexts): `resolve`, `exists`, `family`,
`sameShape`, `variant` / `withoutVariant`, `withId`, `averageColor`, `displayName`.

There was no family knowledge in core, so `core/blocks/BlockFamilies` derives it from ids, checked against a
`Predicate<String>` of known ids (the registry; the vanilla creative-menu list before assets load):

- Shape suffixes, longest first: `_wall_hanging_sign`, `_hanging_sign`, `_wall_sign`, `_sign`, `_pressure_plate`,
  `_fence_gate`, `_fence`, `_trapdoor`, `_door`, `_button`, `_stairs`, `_slab`, `_wall`, `_log`/`_stem`,
  `_wood`/`_hyphae`, with `stripped_` giving the stripped shapes.
- The full block of a stem is the first that exists of `<stem>_planks`, `<stem>s` (for `brick`/`tile` stems),
  `<stem>_block`, `<stem>` — so `stone_brick_stairs` ↔ `stone_bricks`, `quartz_stairs` ↔ `quartz_block`,
  `oak_door` ↔ `oak_planks`.
- A block in a single shape has no family (`dirt`, `quartz_bricks`).
- Variants are prefixes (`cracked`, `mossy`, `chiseled`, `polished`, `smooth`, `cut`, `exposed`, `weathered`,
  `oxidized`, `waxed`), with copper's quirks: `waxed_` stays first and `copper_block` + exposed is `exposed_copper`.

`withId` copies the properties the new block also has, using the registry's allowed values; without a registry it
keeps all properties for the same shape and none otherwise. `averageColor` is the average of the block's top-face
texture (`BlockAssets.averageColor`), opaque, grey without assets.

## Transforms

`PluginTransform`: `id`, `name`, `description`, `icon` (16×16 SVG path, stroked like the tool icons), `scope`
(`SELECTION`, `LAYER`, `SELECTION_OR_LAYER`), `options`, `randomized`, `apply(TransformContext)`.
`TransformContext`: `world()`, `bounds()`, `solidBlocks()`, `blockCount()`, `options()`, `random()`
(`java.util.Random`, seeded), `blocks()`, `preview()`.

Where they show: Plugins menu › Transform (there is no Edit menu in the current UI), the viewport's right-click menu
("Transform selection" / "Transform <layer>"), and `/transform` (lists) / `/transform <id>` (opens the dialog; the
command registers itself while any plugin has transforms).

How it runs (`TransformRunner`, UI-free): the transform writes into an overlay over the real blocks; the result is
the set of cells whose state differs (`Changes`). Targets (`TransformTargets`): a selection is the selected non-air
blocks in visible, unlocked layers, or the non-air cells of the //pos region; a layer target is the active layer's
blocks, and its edits stay in that layer. The dialog (`TransformDialog`, non-modal so the view can be orbited)
re-runs the preview 160 ms after each change and shows ghosts through a second `SceneRenderer` on a private preview
`Scene` (so previews never reach the layer list, undo, saves or plugin events); cells that become air get red
outlines, and the target's bounds a white one. Apply runs the transform again with the same seed and options,
`preview()` false, and writes the changes as one undo step. Shapes are not reconnected afterwards, so what was
previewed is exactly what is applied. The last options and seed are remembered, even on Cancel.

## Panels

`PluginPanel` (`id`, `title`, `icon`, `dock`, `create(PanelContext)`, `dispose`) and `PanelContext` (`plugin`,
`isShowing`, `onShown`, `setBadge`, `reveal`). The only JavaFX in the API; `plugin-api` has JavaFX as `compileOnly`.

Panels become tabs in MainWindow's right-hand `sideTabs`, next to the Resource Tracker, using the same closable-tab
logic: closed ones wait in the bar on the right edge, and the panel folds away when every tab is closed. Content is
created on first show; a failing `create` shows an error in the tab and is logged. Closed plugin panels are remembered
(`closedPluginPanels`). Tabs follow plugins being enabled and disabled; `dispose` runs on unload. PLUGINS.md lists the
AtlantaFX looked-up colours to use.

## Tools

`PluginTool` (`id`, `name`, `description`, `icon`, `defaultKey`, `options`, `activate(ToolContext)`),
`ToolHandler` (`hover`, `press`, `drag`, `release`, `scroll` → used?, `key` → used?, `deactivate`),
`ToolEvent(Optional<Hit> hit, Button, Set<Modifier>, Vec3 rayOrigin, Vec3 rayDir)` with
`Hit(block, face, adjacent, Optional<Layer>)` (the ground grid counts as a hit with no layer),
`ToolContext` (`plugin`, `options`, `hand`, `blocks`, `preview`, `beginStroke`), `Preview` (`ghost`, `outline`,
`clear`) and `Stroke` (`world`, `commit`, `cancel`; closing commits).

In the app: a `PLUGIN` tool kind; plugin tool buttons below the built-in ones in `ToolDock`; `PluginToolSession`
wraps the handler (every call guarded, failures reported to the plugin log and a toast). The viewport forwards left
and right button press / drag / release, mouse moves, the wheel (only when `scroll` returns true) and plain keys
(through the main window's key filter, before key binds) while a plugin tool is active and nothing is being placed;
the middle button keeps orbiting. A stroke is an undo group plus a `LayeredEdit` (same semantics as `editWorld`:
merged visible layers, writes where blocks are, new blocks into the active layer, shapes reconnected on commit);
writes are flushed after each event so they show live; `cancel` rolls the blocks back. Deactivation commits open
strokes and clears the preview. The options bar sits above the hotbar. A plugin being disabled puts its active tool
down first.

## Exporters, importers, assets

- `PluginExporter`: `options()` (drawn on the export card and remembered), `summary(merged)` (an extra card line;
  skipped above 2M blocks because it needs the layers merged), and `Request.options()`, `progress()`, `assets()`.
  Progress updates the indicator and a status line under the card.
- `PluginImporter` (`id`, `displayName`, `extensions`, `options`, `importFile(Path, OptionValues, Progress,
  BlockCatalog)` → `List<ImportedLayer(name, blocks, offset)>`): extensions join the Import filter and drag and drop
  (a schematic format for the same extension wins); options are asked for in a small dialog; reading happens in the
  background; the layers are placed like an imported schematic.
- `AssetAccess` (`ctx.assets()`, `Request.assets()`): `available`, `quads(state, culled)` (model quads as plain
  arrays with face, cull side, tint and texture id), `atlas()` (ARGB pixels plus PNG bytes from a small built-in
  encoder, no AWT), `texture(resourceId)` (raw files from the asset stack).
- `NbtIO.readLE` / `writeLE` in core: little-endian NBT for Bedrock `.mcstructure`.

## Example plugin: palette-tools

API 2, exercising every new extension point: Weathering (cracked / mossy, more moss low down, randomized), Palette
swap (whole family, keeping facing), Gradient (block list along an axis with blend), the Palette panel (block counts
of the selection or the visible layers, colour swatches, badge, refreshes on events only while showing), a GIMP
palette exporter (options, summary, progress), a pixel-art importer, and a Wall tool (drag with ghost preview, wheel
for height, Esc cancels, one undo step). `hello-plugin` stays on API 1 to prove compatibility.

## Tests

- core: `BlockFamiliesTest` (families, shapes, variants, copper), `NbtTest` (little-endian round trip and byte order).
- plugin-api: `OptionsTest` (defaults, typed getters, clamping, persistence round trip and fallbacks, patterns).
- app: `SceneEventBusTest` (coalescing, world-space dirty boxes, ordering, filtering, cancel/removal, errors),
  `AppBlockCatalogTest` (catalog without assets, `OptionStore`), `TransformTargetsTest` (preview changes nothing,
  apply equals preview, one undo step, locked layers, turned layers, scopes), `PluginToolSessionTest` (Wall tool:
  preview, stroke as one undo step, wheel and Esc), `AppAssetAccessTest` (PNG encoder), and `PluginManagerTest`
  loading both example jars (API 1 and 2 side by side, transforms, `/transform`, events until disable, exporter
  options/progress, importer, refusing a newer API).
- `PluginUiSnapshotsIT` (with `UI_SNAPSHOTS=1`) renders the transform dialogs, the panel and the plugin export card.

## Open items

- `PluginPanel.Dock.LEFT` and `BOTTOM` fall back to the right-hand tabs; there is no left or bottom dock yet.
- Plugin tool keys aren't in Settings › Keybinds (the editor is built on a fixed enum); they can be changed in
  `settings.json` (`pluginToolKeys`). A default key already used by BlockDesigner is ignored.
- Plugin tools don't receive input in flight mode.
- `/transform <id>` only opens the dialog; applying straight from the command line with options is not done.
- Preview ghosts are drawn translucent over the old blocks rather than replacing them in the view.
- Transforms change blocks only (entities are read-only through their world); `Stroke.cancel` reverts blocks, not
  entities added through the stroke's world.
- `averageColor` averages the top-face texture, not a rendered inventory icon.
- `AssetAccess.quads` covers baked block models; block-entity models (chests, banners, signs) are not included.
- No Bedrock `.mcstructure` format plugin yet; only the little-endian NBT it needs.
- Selection events rely partly on a per-frame count/region check, so a selection swapped for another of exactly the
  same size and region through an unhooked path would not be reported.
