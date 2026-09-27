# API levels

The plugin API has a single version number, `PluginApi.VERSION` in
[`plugin-api/src/main/java/io/blockdesigner/plugin/PluginApi.java`](https://github.com/doolecg/BlockDesigner/blob/main/plugin-api/src/main/java/io/blockdesigner/plugin/PluginApi.java).
It is **5** in BlockDesigner 0.4.22. A plugin declares the level it needs as `"api"` in its
[manifest](The-Manifest.md).

## The rule

- BlockDesigner **refuses** a plugin whose `api` is higher than its own `PluginApi.VERSION`. The plugin shows as
  *needs a newer BlockDesigner* in the Plugins window with the message "Needs plugin API N; this BlockDesigner has
  API M. Update BlockDesigner." (`PluginManager.discover`). It is not loaded at all, so it can't fail half-way.
- It **loads** every plugin with the same or a lower `api`. Levels only add things; API 1 plugins still run.
- So **declare the lowest level whose features you use**. That level decides the oldest BlockDesigner that can run
  your plugin, which is the "Needs BlockDesigner X or later" line in the official plugins' READMEs and notes.
- The automatic updater follows the same rule: a release whose jar declares a higher `api` than the running app is
  skipped ("needs a newer BlockDesigner") until the user updates BlockDesigner. See [Automatic updates](Automatic-Updates.md).

Nothing checks that you declared the right level. If you use an API 5 method but declare `"api": 3`, BlockDesigner
0.4.12 to 0.4.16 will load the plugin and it fails at the first call it doesn't have (`NoSuchMethodError`, or
`AbstractMethodError` for interface methods), usually in `enable`.

## What each level added

| API | First BlockDesigner | Added |
|---|---|---|
| **1** | 0.3.0 | Schematic formats (`registerFormat`), exporters (`registerExporter`), Plugins-menu actions (`registerAction`), BlockEdit commands (`registerCommand`), reading and editing the scene (`scene`, `editor`, `editWorld`, `addLayer`, `activeLayer`, `selectedLayers`, `targetVersion`), `dataFolder`, `log`, `status`, `toast`, `runOnUiThread`. |
| **2** | 0.4.4 | Declarative `Options` / `OptionValues` / `BlockPattern`; scene events (`on(...)`, `SceneEvent`, `Subscription`); the block catalog (`blocks()`); asset access (`assets()`); `selection()`; transforms, panels, tools and importers; options, summary and progress for exporters. |
| **3** | 0.4.12 | Scene objects: `registerObjectType`, `objects()`, `SceneObject`, `ObjectHandle`, `Pose`, `Drawing`, `ImageData`, `ViewInfo`. |
| **4** | 0.4.14 | Settings in the plugin's own tab: `registerSettings(options, onChange)` and `settings()`. Every plugin gets the tab whatever its level; only registering settings needs 4. |
| **5** | 0.4.17 | Tools that work on the selection (`PluginTool.selects()`, `ToolContext.selection()`, `previewTransform`, `applyTransform`), `ToolHandler.optionsChanged()`, options shown only for some choices (`Options.Builder.showWhen`), the hotbar (`hotbar()`, `setHotbar`), block icons (`blockIcon`), `pickTool` and `setToolOptions`. |
| **6** | 0.4.24 | Resource packs: `resourcePacks()` and `useResourcePacks(packs)`. The UI kit (`io.blockdesigner.plugin.ui`: `PanelScaffold`, `Section`, `Form`, `ActionBar`, `StatusBadge`, `Banner`, `EmptyState`, `ItemList`/`ItemRow`, `Segmented`, `Controls`, `Icon`, `Theme`, `Tone`) and `ui()` (`PluginUi`: the app's options form, remembered forms, dialogs, owner window, dark mode). `Options` groups, advanced groups, `help`, `unit`, `enabledWhen` and `option(...)`. `showPanel`, `setPanelStatus`, `openSettings`, `updateSettings`. Plugin settings move to the Settings window, and tool and action keys to Settings › Keybinds (app changes, for every level). |

The "Added" column follows the Javadoc of `PluginApi.VERSION`. The "First BlockDesigner" column comes from the main
repository's `RELEASE_NOTES.md` (0.3.0 introduced plugins; 0.4.4, 0.4.12, 0.4.14, 0.4.17 and 0.4.24 name API 2, 3, 4, 5 and 6).

Two app-side changes that are not API levels but matter to plugins:

- **0.4.16**: automatic plugin updates through the manifest's `"updates"` link. Older BlockDesigners ignore the field.
- **0.4.17**: every plugin gets one tab on the right; panels became pages of it instead of tabs of their own.
- **0.4.24**: the tab opens on its first page (the Overview is behind an info button), registered settings are on the
  plugin's page in the Settings window, tool and action keys are in Settings › Keybinds, and a file type that several
  importers take asks which one to use. Panels that aren't built with the UI kit's `PanelScaffold` keep their 10 px
  padding.

## Which jars to compile against

Compile against the API jars of the BlockDesigner version you test with (the official plugins use the newest
release), and declare the lowest `api` you need. Newer jars still contain everything older levels had, so building
against 0.4.22's jars with `"api": 3` is normal (Terrain Generator does it). Changes to `core` classes (`BlockState`,
`Structure`, `WorldEdit.World`…) are not versioned by the API level; see the FAQ on
[Troubleshooting and FAQ](Troubleshooting-And-FAQ.md#my-plugin-worked-on-an-older-blockdesigner-and-breaks-on-a-newer-one).
