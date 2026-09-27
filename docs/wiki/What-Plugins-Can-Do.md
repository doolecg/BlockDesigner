# What plugins can do

A plugin registers features through the `PluginContext` it is handed in `enable`. Each `register…` call adds one
thing to the app; when the plugin is turned off or fails, BlockDesigner removes all of it again.

| You register | API | Where it shows up |
|---|---|---|
| `registerFormat(SchematicFormat)` | 1 | Import (file filter, drag and drop) and a card in the Export window |
| `registerExporter(PluginExporter)` | 1 | A card in the Export window, for output that isn't a schematic (reports, a mod's files, folders) |
| `registerAction(PluginAction)` | 1 | An entry in the Plugins menu (puzzle icon in the top bar) |
| `registerCommand(PluginCommand)` | 1 | A BlockEdit `/command` in the command bar (T or /) and the Console, with completion and `/help` |
| `registerTransform(PluginTransform)` | 2 | Plugins › Transform, the viewport's right-click menu and `/transform <id>`: a dialog with options and a live ghost preview |
| `registerPanel(PluginPanel)` | 2 | A page of the plugin's tab on the right, with your own JavaFX content |
| `registerTool(PluginTool)` | 2 | A tool in the tool dock over the viewport; mouse, wheel and keys go to your handler |
| `registerImporter(PluginImporter)` | 2 | Import (file filter, drag and drop) for files that aren't schematics, such as images |
| `registerObjectType(SceneObjectType)` | 3 | Objects in the scene that aren't blocks: rows in the Layers panel, drawn in the 3D view, moved by the Move / Rotate / Scale tools, saved in the project |
| `registerSettings(Options, onChange)` | 4 | Settings at the top of the plugin's tab, kept between runs |

Besides registering, a plugin can:

- **read the project**: `scene()`, `activeLayer()`, `selectedLayers()`, `targetVersion()`, and (API 2) `selection()`;
- **edit it with undo**: `editWorld(label, world -> …)`, `editor()` sessions, `addLayer(name, blocks)`;
- **listen for changes** (API 2): `on(SceneEvent.BlocksChanged.class, e -> …)` and the other scene events;
- **look up blocks** (API 2): `blocks()` for the registry, block families, variants, colours and names;
- **read game assets** (API 2): `assets()` for block models as quads, the texture atlas and raw texture files;
- **talk to the user**: `status(…)`, `toast(…)`, `log(…)` (the Plugins window and the Console);
- **use the hotbar and its own tools** (API 5): `hotbar()`, `setHotbar(…)`, `blockIcon(…)`, `pickTool(…)`, `setToolOptions(…)`;
- **keep files** in its own `dataFolder()`.

Every plugin also gets **its own tab** on the right, whatever API it declares. Its Overview page shows the plugin's
name, version, status (Running or Failed), description, how it updates, its settings (API 4) and everything it
registered, each with a button to use it, plus a Turn off button. The plugin's panels are further pages of that tab.

Short examples of each feature are on [Registering features](Registering-Features.md) and
[BlockEdit commands](BlockEdit-Commands.md); the full worked examples are in
[PLUGINS.md](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md).

## What plugins can't do (yet)

These come from the "Limits" section of PLUGINS.md and the code:

- Panels only go in the plugin's tab on the right. `PluginPanel.dock()` exists in the API but is not used.
- There is no secret (password) option kind; API keys need a field of the plugin's own on one of its pages.
- Plugin tools get no input in flight mode.
- `/transform <id>` opens the transform's dialog; it can't apply with options straight from the command line.
- Transforms change blocks only.
- Scene objects can't be reordered in the Layers panel or selected several at a time. (PLUGINS.md also says the
  Scale tool doesn't drive them; that is out of date: `ViewportPane` and `ObjectDrag` scale objects with the Scale
  tool.)
- There is no way to list every block id in the registry through `BlockCatalog` (you can check one with `exists(id)`).
- There is no sandbox: a plugin runs with the same rights as BlockDesigner. Users are warned before installing one.
