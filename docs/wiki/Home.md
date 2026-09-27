# BlockDesigner plugin developer wiki

BlockDesigner is a Windows editor for Minecraft builds. A **plugin** is a `.jar` that BlockDesigner loads at startup
and that adds features to it: schematic formats, exporters and importers, menu actions, BlockEdit commands, tools,
transforms with a live preview, panels, objects in the scene and settings. This wiki is for people writing plugins.

It sits beside the existing plugin documents in the repository and links to them rather than repeating them:

- [PLUGINS.md](https://github.com/doolecg/BlockDesigner/blob/main/PLUGINS.md): the guide, with a worked example for every extension point.
- [docs/plugin-api-reference.md](https://github.com/doolecg/BlockDesigner/blob/main/docs/plugin-api-reference.md): every type in the API with its members.
- [docs/plugin-api-v2.md](https://github.com/doolecg/BlockDesigner/blob/main/docs/plugin-api-v2.md): the design record for API 2.

Where those and this wiki disagree, the code wins: `plugin-api/src/main/java/io/blockdesigner/plugin` (the API) and
`app/src/main/java/io/blockdesigner/app/plugins` (the loader and updater). The pages here were checked against the
code of BlockDesigner 0.4.22 (plugin API 5).

## Pages

**Start here**
- [What plugins can do](What-Plugins-Can-Do.md): the extension points and where each shows up in the app.
- [API levels](API-Levels.md): API 1 to 5, what each added and the first BlockDesigner version with it.
- [Setting up a plugin project](Setting-Up-A-Plugin-Project.md): the template, JDK 26, the vendored API jars, `compileOnly`.
- [The manifest](The-Manifest.md): `blockdesigner-plugin.json`, field by field.

**How it works**
- [How plugins are loaded](How-Plugins-Are-Loaded.md): the plugins folder, class loading, on and off, errors, logs and the Console.
- [Plugin lifecycle and context](Plugin-Lifecycle-And-Context.md): `enable`, `disable`, `PluginContext`, threads.
- [Registering features](Registering-Features.md): formats, exporters, importers, actions, tools, transforms, panels, scene objects, settings.
- [BlockEdit commands](BlockEdit-Commands.md): `/commands`, and why the Java class is still called `WorldEdit`.
- [Storing data and settings](Storing-Data-And-Settings.md): the data folder, settings in the plugin's tab, remembered options, data saved in the project.
- [Project file format](Project-File-Format.md): the `.bdproj` project file, the format shared with the game.

**Shipping**
- [Automatic updates](Automatic-Updates.md): exactly what a release needs for BlockDesigner to update the plugin by itself.
- [Releasing on GitHub](Releasing-On-GitHub.md): version, notes, tag, release and jar.
- [Testing plugins](Testing-Plugins.md): unit tests against the API jars, and loading through `PluginManager`.
- [Troubleshooting and FAQ](Troubleshooting-And-FAQ.md)

## Official plugins

Each lives in its own repository and is a good reference for the features it uses:

| Plugin | API | Shows how to |
|---|---|---|
| [Resource Tracker](https://github.com/doolecg/BlockDesigner-ResourceTracker) | 5 | a panel, a menu action, scene events, data kept per project in the data folder |
| [Palette Tools](https://github.com/doolecg/BlockDesigner-PaletteTools) | 5 | a tool on the selection, transforms, an exporter, an importer, a panel |
| [Reference Planes](https://github.com/doolecg/BlockDesigner-ReferencePlanes) | 4 | scene objects saved in the project, settings in the plugin's tab |
| [Terrain Generator](https://github.com/doolecg/BlockDesigner-TerrainGenerator) | 3 | a panel, a command, a tool, a scene object that holds its state in the project, a two-module build |
| [Pixel Art Generator](https://github.com/doolecg/BlockDesigner-PixelArtGenerator) | 5 | a large panel, a placing tool, an importer |

The two example plugins in the main repository, [`examples/hello-plugin`](https://github.com/doolecg/BlockDesigner/tree/main/examples/hello-plugin)
(API 1) and [`examples/palette-tools`](https://github.com/doolecg/BlockDesigner/tree/main/examples/palette-tools)
(API 2), are built inside BlockDesigner's own build because its tests load them. Start a real plugin from the
template on [Setting up a plugin project](Setting-Up-A-Plugin-Project.md) instead.
