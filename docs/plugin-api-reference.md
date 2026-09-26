# Plugin API reference

Every public type in `io.blockdesigner.plugin` (the `plugin-api` module, [`plugin-api/src/main/java/io/blockdesigner/plugin`](../plugin-api/src/main/java/io/blockdesigner/plugin)), with its members. The Javadoc in the sources has the details; [PLUGINS.md](../PLUGINS.md) is the guide with worked examples, and [plugin-api-v2.md](plugin-api-v2.md) is the design record.

**API** is the `"api"` version a plugin must declare in `blockdesigner-plugin.json` to use the type. Types from `core` that the API uses (`BlockState`, `BlockPos`, `Box`, `Structure`, `Layer`, `Scene`, `SceneEditor`, `WorldEdit.World`, `SchematicFormat`, `BlockFamily`, `McVersion`) are in the `core` jar.

## Overview

| Type | Kind | API | What it is |
|---|---|---|---|
| [`PluginApi`](#pluginapi) | class | 1 | The API version (`VERSION = 3`) and the manifest file name |
| [`BlockDesignerPlugin`](#blockdesignerplugin) | interface | 1 | A plugin's entry point |
| [`PluginInfo`](#plugininfo) | record | 1 | What the manifest says about a plugin |
| [`PluginContext`](#plugincontext) | interface | 1 (parts 2) | The plugin's handle on BlockDesigner: registration, the scene, edits, feedback |
| [`PluginCommand`](#plugincommand) | record | 1 | A `/command` for the command bar |
| [`PluginAction`](#pluginaction) | record | 1 | An entry in the Plugins menu |
| [`PluginExporter`](#pluginexporter) | interface | 1 (parts 2) | A card in the Export window for non-schematic output |
| [`Options`](#options) | class | 2 | Declarative parameters BlockDesigner draws controls for |
| [`OptionValues`](#optionvalues) | class | 2 | The values chosen for some `Options` |
| [`BlockPattern`](#blockpattern) | record | 2 | A weighted mix of blocks (`70%stone,30%andesite`) |
| [`Progress`](#progress) | interface | 2 | Progress reporting for long jobs |
| [`SceneEvent`](#sceneevent) | sealed interface | 2 | Changes in the open project, delivered to listeners |
| [`Subscription`](#subscription) | interface | 2 | A listener handle; cancel to stop listening |
| [`BlockCatalog`](#blockcatalog) | interface | 2 | The block registry, families, variants, colours and names |
| [`AssetAccess`](#assetaccess) | interface | 2 | Block models as quads, the texture atlas and raw asset files |
| [`PluginTransform`](#plugintransform) | interface | 2 | An operation on existing blocks with a previewed dialog |
| [`TransformContext`](#transformcontext) | interface | 2 | What a transform sees while it runs |
| [`PluginPanel`](#pluginpanel) | interface | 2 | A tab of the plugin's own in the right-hand panel (JavaFX) |
| [`PanelContext`](#panelcontext) | interface | 2 | A panel's handle on its tab |
| [`PluginTool`](#plugintool) | interface | 2 | A tool in the tool dock over the viewport |
| [`ToolHandler`](#toolhandler) | interface | 2 | Receives an active tool's mouse, wheel and key input |
| [`ToolEvent`](#toolevent) | record | 2 | Mouse input for a tool handler |
| [`ToolContext`](#toolcontext) | interface | 2 | What an active tool can use: options, preview, strokes |
| [`PluginImporter`](#pluginimporter) | interface | 2 | Turns a non-schematic file into layers |
| [`SceneObjectType`](#sceneobjecttype) | interface | 3 | A kind of scene object (reference image, guide): makes them, from files too |
| [`SceneObject`](#sceneobject) | interface | 3 | Something in the scene that isn't blocks: draws itself, has a right-click menu and its own state |
| [`ObjectHandle`](#objecthandle) | interface | 3 | BlockDesigner's side of one object: name, pose, visibility, lock, undoable edits |
| [`SceneObjects`](#sceneobjects) | interface | 3 | The plugin's objects in the project, the view, and blobs |
| [`Pose`](#pose) | record | 3 | Position, rotation and scale of an object |
| [`Drawing`](#drawing) | interface | 3 | What an object draws: images on patches, lines |
| [`ImageData`](#imagedata) | class | 3 | ARGB pixels for `Drawing.image` |
| [`ViewInfo`](#viewinfo) | record | 3 | How the 3D view looks at the scene: ortho, axis view, eye, target |

## Core

### PluginApi

`public final class PluginApi`

| Member | Description |
|---|---|
| `static final int VERSION = 3` | The API version this BlockDesigner provides. Plugins declaring a newer `api` are not loaded. |
| `static final String DESCRIPTOR = "blockdesigner-plugin.json"` | Name of the manifest at the root of a plugin jar. |

### BlockDesignerPlugin

`public interface BlockDesignerPlugin` — the class named by `"main"` in the manifest; needs a public no-argument constructor.

| Member | Description |
|---|---|
| `void enable(PluginContext context) throws Exception` | Called once after loading (or re-enabling). Register everything here; don't build JavaFX nodes yet. |
| `default void disable()` | Called when the plugin is disabled or the app closes. Registrations are removed automatically; release anything else. |

### PluginInfo

`public record PluginInfo(String id, String name, String version, String author, String description, String mainClass, int api)` — the parsed manifest. `name` defaults to `id`; `version`, `author` and `description` default to `""`.

### PluginContext

`public interface PluginContext` — passed to `enable`. Use from the JavaFX thread.

| Member | API | Description |
|---|---|---|
| `PluginInfo info()` | 1 | This plugin's manifest. |
| `Path dataFolder()` | 1 | A folder for the plugin's own files (created on first call). |
| `void log(String message)` | 1 | Writes a line to the plugin log in the Plugins window. |
| `void registerFormat(SchematicFormat format)` | 1 | Adds a schematic format to Import and Export (id unique across all formats). |
| `void registerExporter(PluginExporter exporter)` | 1 | Adds a card to the Export window. |
| `void registerAction(PluginAction action)` | 1 | Adds an entry to the Plugins menu. |
| `void registerCommand(PluginCommand command)` | 1 | Adds a command to the command bar. |
| `void registerTransform(PluginTransform transform)` | 2 | Adds a transform (Plugins › Transform, right-click menu, `/transform <id>`). |
| `void registerPanel(PluginPanel panel)` | 2 | Adds a panel (a closable tab on the right). |
| `void registerImporter(PluginImporter importer)` | 2 | Adds an importer (Import window, drag and drop). |
| `void registerTool(PluginTool tool)` | 2 | Adds a tool to the tool dock. |
| `void registerObjectType(SceneObjectType type)` | 3 | Adds a kind of scene object; objects of it in the open project appear. |
| `<E extends SceneEvent> Subscription on(Class<E> type, Consumer<? super E> listener)` | 2 | Listens for scene events, at most once per frame per kind; `SceneEvent.class` hears every kind. |
| `Scene scene()` | 1 | The layers being edited (read freely; edit through `editor()`). |
| `SceneEditor editor()` | 1 | Undoable editing: block sessions, adding / removing / modifying layers. |
| `Optional<Layer> activeLayer()` | 1 | The active layer. |
| `List<Layer> selectedLayers()` | 1 | Layers selected in the layer list (the active one when nothing else is). |
| `McVersion targetVersion()` | 1 | The Minecraft version exports target. |
| `BlockCatalog blocks()` | 2 | Block registry, families, variants and colours. |
| `AssetAccess assets()` | 2 | Block models, the texture atlas and texture files. |
| `Optional<Box> selection()` | 2 | World-space box around the selected blocks, or the `//pos1 //pos2` region; empty when neither. |
| `void editWorld(String label, Consumer<WorldEdit.World> edit)` | 1 | Edits blocks in world coordinates across visible, unlocked layers as one undo step; new blocks go into the active layer. |
| `Layer addLayer(String name, Structure blocks)` | 1 | Adds a layer (one undo step) and makes it active. |
| `SceneObjects objects()` | 3 | The plugin's scene objects. |
| `void status(String message)` | 1 | Sets the status bar text. |
| `void toast(String message)` | 1 | Shows a short message over the 3D view. |
| `void runOnUiThread(Runnable task)` | 1 | Runs on the JavaFX thread (immediately when already on it). |

## API 1 extension points

### PluginCommand

`public record PluginCommand(String name, String usage, String description, Handler handler)` — `name` without the slash, `a-z 0-9 _` only (otherwise `IllegalArgumentException`); `usage` defaults to `/name`.

| Nested type | Description |
|---|---|
| `interface Handler { String run(Context context) throws Exception; }` | Runs the command and returns the message shown; throw `IllegalArgumentException` for a friendly error. Writes to `context.world()` are one undo step. |
| `record Context(List<String> args, List<String> flags, WorldEdit.World world, Optional<Box> region, Optional<BlockPos> aim, Optional<BlockState> hand, BlockResolver blocks)` | What a command sees: words after the name (flags removed), `-x` flags, the merged visible unlocked layers, the `//pos` region, the aimed block, the held block and a resolver. `Box requireRegion()` returns the region or throws "Select a region first". |
| `interface BlockResolver { BlockState resolve(String text); }` | Turns text like `oak_stairs[facing=east]` into a block state; throws `IllegalArgumentException` when unknown. |

### PluginAction

`public record PluginAction(String label, String description, Runnable action)` — a Plugins-menu entry: menu text, tooltip, and the action run on the JavaFX thread. `label` and `action` are required.

### PluginExporter

`public interface PluginExporter` — an Export window card for output that isn't a schematic format.

| Member | API | Description |
|---|---|---|
| `String id()` | 1 | Stable id, unique within the plugin. |
| `String displayName()` | 1 | Card title. |
| `default String description()` | 1 | One line under the name (default `""`). |
| `String extension()` | 1 | Extension written, without the dot; `null` when `writesFolder()`. |
| `default boolean writesFolder()` | 1 | True to have the user pick a folder as the target. |
| `default Options options()` | 2 | Parameters shown on the card and remembered. |
| `default String summary(Structure merged)` | 2 | An extra summary line on the card, or `null`; runs on the JavaFX thread when the chosen layers change. |
| `void export(Request request) throws IOException` | 1 | Writes the output on a background thread. |

`record Request(String name, String author, McVersion version, Structure merged, List<Layer> layers, Path target, OptionValues options, Progress progress, AssetAccess assets)` — the export name, author, target version, the chosen layers merged in world coordinates, the layers themselves (bottom first, read-only by convention), the target file or folder, and (API 2) the chosen options, a progress sink and asset access. A six-argument constructor (without the API 2 components) is kept for API 1.

## Shared API 2 pieces

### Options

`public final class Options` — immutable, ordered parameters.

| Member | Description |
|---|---|
| `static Options none()` | No parameters: the feature runs straight away. |
| `static Builder builder()` | Starts a builder. Keys must be unique and use `A-Z a-z 0-9 _ . -`. |
| `List<Option> all()` | Every option, in display order. |
| `Optional<Option> get(String key)` | One option by key. |
| `boolean isEmpty()` | Whether there are no options. |
| `OptionValues defaults()` | Values with every option at its default. |

`Builder` methods (each returns the builder; finish with `Options build()`):

| Method | Control |
|---|---|
| `integer(String key, String label, int defaultValue, int min, int max)` | Spinner |
| `decimal(String key, String label, double defaultValue, double min, double max)` | Slider (a 0..1 range shows as a percentage) |
| `toggle(String key, String label, boolean defaultValue)` | Check box |
| `block(String key, String label, BlockState defaultValue)` | Block field |
| `blockList(String key, String label, List<BlockState> defaultBlocks)` / `blockList(String key, String label, BlockPattern defaultValue)` | Weighted block pattern field |
| `choice(String key, String label, List<String> values, String defaultValue)` | Drop-down |
| `text(String key, String label, String defaultValue)` | Text field |
| `file(String key, String label, List<String> extensions)` | File field with Browse (extensions without the dot; empty for any) |

`sealed interface Option { String key(); String label(); }` with the records `IntegerOption`, `DecimalOption`, `ToggleOption`, `BlockOption`, `BlockListOption`, `ChoiceOption`, `TextOption` and `FileOption`, one per builder method.

### OptionValues

`public final class OptionValues` — immutable values for some `Options`. Every option has a value (its default until changed), except a file nobody picked.

| Member | Description |
|---|---|
| `static OptionValues defaults(Options options)` | Every option at its default. |
| `Options options()` | The options these values are for. |
| `int integer(String key)`, `double decimal(String key)`, `boolean toggle(String key)` | Typed getters. |
| `BlockState block(String key)`, `BlockPattern blockList(String key)` | Block getters. |
| `String choice(String key)`, `String text(String key)` | Text getters. |
| `Optional<Path> file(String key)` | The picked file, if any. |
| `Object get(String key)` | The raw value. |
| `OptionValues with(String key, Object value)` | A changed copy; numbers are clamped, choices must be valid. |
| `Map<String, String> toStrings()` / `static OptionValues fromStrings(Options, Map<String, String>, PluginCommand.BlockResolver)` | Text form for saving, and reading it back (invalid values fall back to defaults). |

Typed getters throw `IllegalArgumentException` for an unknown key or the wrong type.

### BlockPattern

`public record BlockPattern(List<Entry> entries)` — a weighted mix of blocks, the value of a block-list option. `record Entry(BlockState block, double weight)` (weight must be positive).

| Member | Description |
|---|---|
| `static BlockPattern of(BlockState... blocks)` / `of(List<BlockState> blocks)` | Equal parts of each block. |
| `static BlockPattern parse(String text, PluginCommand.BlockResolver resolve)` | Parses `70%stone,30%andesite` (a missing weight counts as 1). |
| `BlockState pick(RandomGenerator random)` | A block drawn by weight. |
| `List<BlockState> blocks()` | The blocks, in order. |
| `String toString()` | The text form; round-trips through `parse`. |

### Progress

`@FunctionalInterface public interface Progress` — `void update(double fraction, String message)`: `fraction` 0 to 1 (negative when unknown), `message` null keeps the last one. Safe from any thread. `Progress.NONE` ignores updates.

### SceneEvent

`public sealed interface SceneEvent` — delivered on the JavaFX thread, at most one of each kind per frame.

| Record | Description |
|---|---|
| `BlocksChanged(List<Layer> layers, Box dirty)` | Blocks changed (edits, undo, redo, imports); layers bottom first, `dirty` in world coordinates. |
| `LayersChanged()` | Layers were added, removed, reordered, renamed, moved, shown or hidden. |
| `ActiveLayerChanged(Optional<Layer> layer)` | Another layer became active (empty when there are none). |
| `SelectionChanged(Optional<Box> region)` | The block selection or `//pos` region changed; world-space box, or empty. |
| `ProjectOpened(Optional<Path> file)` | A project was opened, or a new one started (empty file). |

### Subscription

`@FunctionalInterface public interface Subscription extends AutoCloseable` — `void cancel()` stops the listener (safe to call twice); `close()` cancels. Listeners are removed automatically when the plugin is disabled.

### BlockCatalog

`public interface BlockCatalog extends PluginCommand.BlockResolver` — the loaded registry (vanilla creative-menu blocks before assets load).

| Member | Description |
|---|---|
| `BlockState resolve(String text)` | Completes a block state from text; throws `IllegalArgumentException` when malformed or unknown. |
| `boolean exists(String blockId)` | Whether a block id exists in the registry. |
| `Optional<BlockFamily> family(BlockState block)` | The block's family (planks, stairs, slab, fence, door…), if it has several shapes. |
| `Optional<BlockState> sameShape(BlockState block, BlockFamily other)` | The same shape in another family, keeping properties. |
| `Optional<BlockState> variant(BlockState block, String modifier)` | A material variant (`mossy`, `cracked`, `exposed`…), keeping shape and properties. |
| `Optional<BlockState> withoutVariant(BlockState block, String modifier)` | The reverse of `variant`. |
| `BlockState withId(BlockState block, String newId)` | Another block, copying the properties it also has. |
| `int averageColor(BlockState block)` | Average texture colour, `0xAARRGGBB`, opaque (top face when it has one). |
| `String displayName(BlockState block)` | The block's display name. |

### AssetAccess

`public interface AssetAccess` — plain-data access to the loaded game, mod and resource-pack assets; safe on background threads. `AssetAccess.NONE` returns nothing.

| Member | Description |
|---|---|
| `boolean available()` | Whether Minecraft's assets are loaded. |
| `List<Quad> quads(BlockState block, Predicate<BlockPlacement.Dir> culled)` | The block model as quads, leaving out faces whose cull side is covered (`side -> false` for all). |
| `Optional<Atlas> atlas()` | The block texture atlas. |
| `Optional<byte[]> texture(String resourceId)` | A raw resource file by id (`minecraft:block/stone`) or full path. |

`record Quad(float[] positions, float[] uvs, BlockPlacement.Dir face, Optional<BlockPlacement.Dir> cull, int tint, String texture)` — one face in block space: 4 × xyz, 4 × uv in the atlas, facing side, cull side, tint (`0xFFFFFF` for none), texture id.
`record Atlas(int width, int height, int[] argb, byte[] png)` — pixels row by row and the same image as PNG bytes.

## API 2 extension points

### PluginTransform

`public interface PluginTransform`

| Member | Description |
|---|---|
| `String id()` | Stable id, unique within the plugin; also the word after `/transform` (`a-z 0-9 _`). |
| `String name()` | Menu and dialog title. |
| `default String description()` | One line under the title and as tooltip. |
| `default String icon()` | 16×16 SVG path data, stroked like the tool icons; `null` for none. |
| `default Scope scope()` | `SELECTION`, `LAYER` or `SELECTION_OR_LAYER` (default). |
| `default Options options()` | Parameters shown in the dialog. |
| `default boolean randomized()` | True to show a seed and Reroll button. |
| `void apply(TransformContext context) throws Exception` | Changes the blocks; runs per preview and once on Apply. Throw `IllegalArgumentException` for a message in the dialog. |

### TransformContext

`public interface TransformContext`

| Member | Description |
|---|---|
| `WorldEdit.World world()` | Blocks in world coordinates; reads see the scene plus this run's writes. |
| `Box bounds()` | World-space box around the blocks being transformed. |
| `Iterable<BlockPos> solidBlocks()` / `int blockCount()` | The non-air blocks being transformed and how many. |
| `OptionValues options()` | The values chosen in the dialog. |
| `Random random()` | Seeded from the dialog's seed, so preview and Apply match. |
| `BlockCatalog blocks()` | Block registry, families and variants. |
| `boolean preview()` | True while previewing, false when applying. |

### PluginPanel

`public interface PluginPanel` — uses JavaFX (`javafx.scene.Node`).

| Member | Description |
|---|---|
| `String id()` | Stable id, unique within the plugin (remembers whether the panel is open). |
| `String title()` | Tab title. |
| `default String icon()` | 16×16 SVG path data; `null` for a puzzle-piece glyph. |
| `default Dock dock()` | `RIGHT` (default), `LEFT` or `BOTTOM`; only `RIGHT` is laid out today, the others fall back to it. |
| `Node create(PanelContext context)` | Builds the content once, the first time the panel is shown. |
| `default void dispose()` | The panel is being removed: stop timers and listeners. |

### PanelContext

`public interface PanelContext`

| Member | Description |
|---|---|
| `PluginContext plugin()` | The plugin's context. |
| `boolean isShowing()` | Whether the panel is open and its tab selected. |
| `void onShown(Runnable action)` | Runs each time the panel comes into view. |
| `void setBadge(String text)` | Text next to the tab title; `null` or empty removes it. |
| `void reveal()` | Opens the panel if closed and selects its tab. |

### PluginTool

`public interface PluginTool`

| Member | Description |
|---|---|
| `String id()` | Stable id, unique within the plugin. |
| `String name()` | Tooltip title and toast. |
| `default String description()` | One tooltip line: what the buttons do. |
| `default String icon()` | 16×16 SVG path data; `null` for a default. |
| `default String defaultKey()` | JavaFX `KeyCombination` text (`"Shift+K"`), or `null`; ignored when the key is already bound. |
| `default Options options()` | Parameters shown in the tool's options bar. |
| `ToolHandler activate(ToolContext context)` | The tool was picked: return the handler for its input. |

### ToolHandler

`public interface ToolHandler` — every method runs on the JavaFX thread and has a do-nothing default.

| Member | Description |
|---|---|
| `void hover(ToolEvent e)` | Mouse moved with no button held. |
| `void press(ToolEvent e)` / `void drag(ToolEvent e)` / `void release(ToolEvent e)` | Left or right button down, moved while held, up. |
| `boolean scroll(ToolEvent e, double delta)` | Wheel turned (+1 / -1 per notch); return true when used, else the view zooms. |
| `boolean key(String key)` | A key outside text fields (`"R"`, `"Shift+R"`, `"Esc"`, `"Enter"`); return true when used. |
| `void deactivate()` | Another tool was picked or the plugin is being disabled. |

### ToolEvent

`public record ToolEvent(Optional<Hit> hit, Button button, Set<Modifier> modifiers, Vec3 rayOrigin, Vec3 rayDir)` — with `shift()`, `ctrl()` and `alt()`.

| Nested type | Description |
|---|---|
| `enum Button { NONE, PRIMARY, SECONDARY, MIDDLE }` | The button pressed or released (`NONE` for hover, scroll and drag without one). |
| `enum Modifier { SHIFT, CTRL, ALT }` | Modifier keys held. |
| `record Vec3(double x, double y, double z)` | A point or direction in world space. |
| `record Hit(BlockPos block, BlockPlacement.Dir face, BlockPos adjacent, Optional<Layer> layer)` | The block hit (for the ground, the cell below the grid), the face, the empty cell in front of it, and its layer (empty for the ground). |

### ToolContext

`public interface ToolContext` — valid until `ToolHandler.deactivate()`.

| Member | Description |
|---|---|
| `PluginContext plugin()` | The plugin's context. |
| `OptionValues options()` | Current values of the options bar (read them each time). |
| `Optional<BlockState> hand()` | The block in the selected hotbar slot. |
| `BlockCatalog blocks()` | The block catalog. |
| `Preview preview()` | Ghost blocks and outlines. |
| `Stroke beginStroke(String label)` | Starts an edit that becomes one undo step when committed. |

`interface Preview`: `void ghost(Map<BlockPos, BlockState> blocks)` (air entries outlined in red; replaces earlier ghosts), `void outline(Box box)` (`null` removes it), `void clear()`.
`interface Stroke extends AutoCloseable`: `WorldEdit.World world()` (world coordinates; reads see the stroke's own writes), `void commit()`, `void cancel()` (puts every changed block back), `close()` commits.

### PluginImporter

`public interface PluginImporter`

| Member | Description |
|---|---|
| `String id()` | Stable id, unique within the plugin. |
| `String displayName()` | Shown in the file filter. |
| `List<String> extensions()` | Extensions without the dot, lower case. |
| `default Options options()` | Asked for in a small dialog before importing, and remembered. |
| `List<ImportedLayer> importFile(Path file, OptionValues options, Progress progress, BlockCatalog blocks) throws IOException` | Reads the file on a background thread; an `IOException` message is shown to the user. |

`record ImportedLayer(String name, Structure blocks, BlockPos offset)` — a layer to add; `name` defaults to "Imported", `offset` (the layer's origin relative to the others of the same import) defaults to the origin.

## API 3 extension points

### SceneObjectType

`public interface SceneObjectType` — registered with `PluginContext.registerObjectType`.

| Member | Description |
|---|---|
| `String id()` | Stable id, unique within the plugin, saved in projects (`a-z 0-9 _ . -`). |
| `String name()` | What one is called ("Reference image"); also the choice when a file could be imported several ways. |
| `String badge()` | The tag on its rows in the Layers panel ("REFERENCE"). |
| `SceneObject create()` | A new, empty object (`load` follows when it comes from a project). |
| `default List<String> extensions()` | Files `open` turns into objects (Import, drag and drop). |
| `default void open(Path file, ViewInfo view) throws IOException` | Makes an object from such a file, usually with `objects().add(...)`; an `IOException` message is shown. |

### SceneObject

`public interface SceneObject` — a plugin's object. Every call runs on the JavaFX thread.

| Member | Description |
|---|---|
| `void draw(ViewInfo view, Drawing out)` | Draws the object in its own space, every frame it is visible; draw nothing to hide it in this view. |
| `default String description(ObjectHandle self)` | The line under its name in the Layers panel. |
| `default List<MenuItem> menu(ObjectHandle self)` | Its own right-click entries (JavaFX); BlockDesigner adds rename, focus, hide, lock and delete. |
| `byte[] save()` | Its own state (not the name, pose or flags), for projects and undo. |
| `void load(byte[] data) throws IOException` | Puts back a saved state. |
| `default Set<String> blobs()` | Keys of the blobs it uses, saved with the project. |

### ObjectHandle

`public interface ObjectHandle` — BlockDesigner's side of one object. The setters are one undo step each.

| Member | Description |
|---|---|
| `String id()`, `String type()`, `SceneObject object()` | Its id in the project, its type id and the plugin's object. |
| `boolean exists()` | Still in the scene (false once deleted, or while its plugin is off). |
| `String name()`, `void setName(String)` | Its name in the Layers panel. |
| `Pose pose()`, `void setPose(Pose pose, String label)` | Where it is; the Move and Rotate tools change it too. |
| `boolean visible()`, `void setVisible(boolean)` | Shown or hidden. |
| `boolean locked()`, `void setLocked(boolean)` | A locked object is drawn but can't be picked or moved in the view. |
| `void edit(String label, Runnable change)` | Changes the plugin's own state as one undo step (undo loads what `save()` returned before). |
| `void refresh()` | Redraws without an undo step. |
| `boolean selected()`, `void select()`, `void remove()` | Selection (the gizmo tools work on the selected object) and deleting (one undo step). |

### SceneObjects

`public interface SceneObjects` — from `PluginContext.objects()`.

| Member | Description |
|---|---|
| `List<ObjectHandle> list()` | The plugin's objects, top first. |
| `ObjectHandle add(String type, String name, Pose pose, SceneObject object)` | Adds one of the plugin's types, as one undo step, and selects it. |
| `Optional<ObjectHandle> selected()` | The selected object, if it is the plugin's. |
| `ViewInfo view()` | How the 3D view looks now. |
| `String storeBlob(byte[] data)` | Keeps data (an image file) with the project; the key is the same for the same bytes. |
| `Optional<byte[]> blob(String key)` | Data stored under a key. |

### Pose

`public record Pose(Vec3 position, Vec3 rotation, Vec3 scale)` — `Vec3` is `ToolEvent.Vec3`. Rotation is in degrees, XYZ Euler (`R = Rz·Ry·Rx`); a point goes to `position + R·(scale∘local)`.

| Member | Description |
|---|---|
| `static Pose IDENTITY`, `static Pose at(x, y, z)` | At the origin (or a point), unturned, scale 1. |
| `withPosition`, `withRotation`, `withScale` | Copies with one part changed. |
| `Pose translated(dx, dy, dz)` | Moved. |
| `Pose rotatedAbout(int axis, double degrees, Vec3 pivot)` | Turned about world axis 0 / 1 / 2 through a pivot. |
| `Pose scaledBy(fx, fy, fz)` | Scaled along its own axes. |
| `Vec3 toWorld(...)`, `Vec3 toLocal(Vec3)` | Points between its own space and the world. |
| `Vec3 axis(int)`, `double[] rotationMatrix()` | Its turned axes; the rotation as a row-major 3×3 matrix. |

### Drawing

`public interface Drawing` — valid only during `SceneObject.draw`; coordinates are in the object's own space.

| Member | Description |
|---|---|
| `void image(ImageData image, Vec3[] corners, double[] uv, double opacity, Depth depth)` | A picture on four corners (bottom-left, bottom-right, top-right, top-left), seen from both sides; `uv` is u v per corner, v from the top, outside 0..1 see-through. |
| `void line(Vec3 from, Vec3 to, int argb)` | A line. |
| `enum Depth { BEHIND_BLOCKS, IN_SCENE, IN_FRONT }` | A backdrop blocks always cover; hidden by blocks in front; over everything. |

### ImageData

`public final class ImageData(int width, int height, int[] argb)` — ARGB pixels (not premultiplied), top row first, wrapped, not copied. Uploaded once per instance: keep one per picture and never change its pixels. `width()`, `height()`, `argb()`, `aspect()`; `MAX_SIZE = 4096`.

### ViewInfo

`public record ViewInfo(boolean ortho, Optional<Side> side, Vec3 eye, Vec3 forward, Vec3 target)` — `side` is the axis view the camera is at (empty for a free view); `isOrthoSide(Side)` checks for one orthographic axis view. `enum Side { FRONT, BACK, RIGHT, LEFT, TOP, BOTTOM }`, with `facing()`: the rotation that turns something drawn facing +Z towards that view, upright.
