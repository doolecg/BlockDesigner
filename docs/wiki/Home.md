# BlockDesigner user guide

BlockDesigner is a Windows editor for Minecraft builds. These pages show how to use it.

**Start here**
- [Getting started](Getting-Started): install, first launch, settings and updates.
- [Building like in Minecraft](Building-Like-In-Minecraft): movement, hotbar, place, break and pick, shapes, symmetry.
- [Controls and keybinds](Controls-And-Keybinds): every default key.

**Editing**
- [Brushes, selections and BlockEdit](Brushes-Selections-And-BlockEdit)
- [Layers](Layers)
- [Reference images](Reference-Images)

**Files and look**
- [Import and export](Import-And-Export): Litematica, WorldEdit, Create and worldgen data packs.
- [Camera, look and feel](Camera-Look-And-Feel)

**Plugins and help**
- [Using plugins](Using-Plugins)
- [User FAQ](User-FAQ)

---

# BlockDesigner plugin developer wiki

BlockDesigner is a Windows editor for Minecraft builds. A **plugin** is a `.jar` that BlockDesigner loads at startup
and that adds features to it: schematic formats, exporters and importers, menu actions, BlockEdit commands, tools,
transforms with a live preview, panels, objects in the scene and settings. This wiki is for people writing plugins.

It sits beside the existing plugin documents in the repository and links to them rather than repeating them:

- [PLUGINS.md](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md): the guide, with a worked example for every extension point.
- [docs/plugin-api-reference.md](https://github.com/doolecg/BlockDesigner/blob/main/docs/plugin-api-reference.md): every type in the API with its members.
- [docs/plugin-api-v2.md](https://github.com/doolecg/BlockDesigner/blob/main/docs/plugin-api-v2.md): the design record for API 2.

Where those and this wiki disagree, the code wins: `plugin-api/src/main/java/io/blockdesigner/plugin` (the API) and
`app/src/main/java/io/blockdesigner/app/plugins` (the loader and updater).

## Pages

**Start here**
- [What plugins can do](What-Plugins-Can-Do): the extension points and where each shows up in the app.
- [API levels](API-Levels): API 1 to 5, what each added and the first BlockDesigner version with it.
- [Setting up a plugin project](Setting-Up-A-Plugin-Project): the template, JDK 26, the vendored API jars, `compileOnly`.
- [The manifest](The-Manifest): `blockdesigner-plugin.json`, field by field.

**How it works**
- [How plugins are loaded](How-Plugins-Are-Loaded): the plugins folder, class loading, on and off, errors, logs and the Console.
- [Plugin lifecycle and context](Plugin-Lifecycle-And-Context): `enable`, `disable`, `PluginContext`, threads.
- [Registering features](Registering-Features): formats, exporters, importers, actions, tools, transforms, panels, scene objects, settings.
- [BlockEdit commands](BlockEdit-Commands): `/commands`, and why the Java class is still called `WorldEdit`.
- [Storing data and settings](Storing-Data-And-Settings): the data folder, settings in the plugin's tab, remembered options, data saved in the project.
- [Project file format](Project-File-Format): the `.bdproj` project file, the format shared with the game.

**Shipping**
- [Automatic updates](Automatic-Updates): exactly what a release needs for BlockDesigner to update the plugin by itself.
- [Releasing on GitHub](Releasing-On-GitHub): version, notes, tag, release and jar.
- [Testing plugins](Testing-Plugins): unit tests against the API jars, and loading through `PluginManager`.
- [Troubleshooting and FAQ](Troubleshooting-And-FAQ)

## Official plugins

Each lives in its own repository and is a good reference for the features it uses:

| Plugin | API | Shows how to |
|---|---|---|
| [BlockCompanion Plugin](https://github.com/doolecg/BlockDesigner-ResourceTracker) (formerly Resource Tracker) | 5 | a panel, a menu action, scene events, data kept per project in the data folder |
| [Palette Tools](https://github.com/doolecg/BlockDesigner-PaletteTools) | 5 | a tool on the selection, transforms, an exporter, an importer, a panel |
| [Terrain Generator](https://github.com/doolecg/BlockDesigner-TerrainGenerator) | 3 | a panel, a command, a tool, a scene object that holds its state in the project, a two-module build |
| [Pixel Art Generator](https://github.com/doolecg/BlockDesigner-PixelArtGenerator) | 5 | a large panel, a placing tool, an importer |

Reference Planes, once a plugin here too, is built into BlockDesigner now as its reference images; its code
([`app/.../refplanes`](https://github.com/doolecg/BlockDesigner/tree/main/app/src/main/java/io/blockdesigner/app/refplanes))
still uses the scene object interfaces, so it remains a scene object example. BlockDesigner no longer loads a plugin with
the id `reference-planes`.

The two example plugins in the main repository, [`examples/hello-plugin`](https://github.com/doolecg/BlockDesigner/tree/main/examples/hello-plugin)
(API 1) and [`examples/palette-tools`](https://github.com/doolecg/BlockDesigner/tree/main/examples/palette-tools)
(API 2), are built inside BlockDesigner's own build because its tests load them. Start a real plugin from the
template on [Setting up a plugin project](Setting-Up-A-Plugin-Project) instead.
