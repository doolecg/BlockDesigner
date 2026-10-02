# Storing data and settings

A plugin has four places to keep things, depending on what the data belongs to.

| What | Where | Kept by | Removed when |
|---|---|---|---|
| Your own files (caches, per-project side data, downloads) | `ctx.dataFolder()` | you | never by BlockDesigner (not on uninstall either) |
| User settings, on your page in the Settings window (API 4) | `settings.json`, `pluginOptions` | BlockDesigner | Settings › reset everything |
| A page's remembered form (`ui().optionsForm(..., "panel", ...)`, API 6) | `settings.json`, `pluginOptions` | BlockDesigner | as above |
| Last options of your transforms, tools, exporters, importers (API 2) | `settings.json`, `pluginOptions` | BlockDesigner | as above |
| State that belongs to a build | the project (`.bdproj`) through scene objects (API 3) | BlockDesigner | when the object is deleted |

## The data folder

`ctx.dataFolder()` returns `<plugins folder>\<plugin id>` (for an installed BlockDesigner
`%APPDATA%\BlockDesigner\plugins\<id>`, for the portable build `data\plugins\<id>`), creating it on first call. If it
can't be created, "Can't create data folder: …" is logged and the path is still returned. The folder belongs to your
plugin; write whatever you need there. Only `*.jar` files directly in the plugins folder are scanned, so your
subfolder is never mistaken for a plugin.

Per-project data without scene objects: the BlockCompanion Plugin keeps what the user has gathered for each project in
`dataFolder()/projects/<key of the project's absolute path>.json` and follows the open project with
`SceneEvent.ProjectOpened` (see `Gathered.java` in its repository). The trade-off is that the data stays on this PC
and doesn't travel with the project file.

Jackson (`com.fasterxml.jackson.databind`, 2.20) is available at runtime for JSON; declare it `compileOnly`.

## Settings in the Settings window (API 4)

```java
ctx.registerSettings(Options.builder()
        .decimal("height", "Height (blocks)", 16, 1, 512)
        .toggle("snap", "Snap to blocks", true)
        .build(), v -> { height = v.decimal("height"); snap = v.toggle("snap"); });
```

- BlockDesigner draws the controls on the plugin's page in the Settings window (under Plugins), with **Reset to
  defaults**; from API 6 with the options' groups, help and units. (Before 0.4.24 they were at the top of the tab.)
- `ctx.updateSettings(v -> v.with("mobs", true))` (API 6) changes them as if the user had: saved, `onChange` called,
  an open Settings window redrawn. Use it to move settings you kept in your own file over once.
- The values are saved in the app's `settings.json`, in the `pluginOptions` map under the key
  `<plugin id>/settings/tab`, as text (`OptionValues.toStrings()`).
- On `registerSettings` the saved values are loaded (missing or no longer valid ones fall back to the defaults) and
  `onChange` is called straight away; after that on every change, on the JavaFX thread. `ctx.settings()` reads the
  current values at any time (defaults of no options before you register).
- Register once, from `enable`: a second call throws "Settings are registered once".
- Declare `"api": 4` or higher.

Change a setting's key, type or range with care: a saved value that no longer fits falls back to the default, which
the user may notice as a reset.

## Remembered options (API 2)

You don't store the options of transforms, tools, exporters and importers yourself: BlockDesigner remembers the last
values per feature in `pluginOptions` under keys like `palette-tools/transform/weather` or `<id>/tool/<tool id>`
(`OptionStore.key(pluginId, kind, id)`), and hands them back next time. A transform's last seed is kept beside them.
Your tool can change its own remembered options with `ctx.setToolOptions(toolId, v -> v.with("height", 8))` (API 5).

Plugin tool key binds are in `settings.json` under `pluginToolKeys`, keyed `<plugin id>/<tool id>`, e.g.
`"palette-tools/wall": "J"`, and action keys under `pluginActionKeys`, keyed `<plugin id>/<action label>`; users set
both in Settings › Keybinds (`""` means no key).

A page's form from `ctx.ui().optionsForm(options, rememberAs, onChange)` (API 6) is kept under
`<plugin id>/<rememberAs>`. `"importer/<id>"` and `"exporter/<id>"` are the keys of the plugin's importer and exporter
options, so a page and the Import window share one set of values (Pixel Art Generator does this).

## State saved in the project (API 3)

When state belongs to the build (a reference image, a generator's settings for this terrain), put it in a
[scene object](Registering-Features#scene-objects-api-3):

- `SceneObject.save()` returns your state as bytes; `load(bytes)` puts it back when the project opens and on undo /
  redo. BlockDesigner keeps the object's name, pose, visibility and lock itself.
- Big data (image files) goes into blobs: `ctx.objects().storeBlob(bytes)` returns a key (the same key for the same
  bytes) to keep in your state, `blob(key)` reads it back, and `blobs()` lists the keys an object uses so they are
  saved with the project.
- Change your state through `ObjectHandle.edit(label, change)` so the change is one undo step.
- When your plugin is off or not installed, its objects stay in the project untouched and come back when it is on.

Terrain Generator keeps all its settings in a "Terrain preview" object this way, so they save with the project and
every change can be undone.

## Not recommended

- Static fields as storage: they are lost on every disable / enable, Reload or update (a new class loader).
- Writing into BlockDesigner's own `settings.json` or next to the app: use the data folder.
- Writing into the plugins folder itself, outside your data folder: a stray `.jar` there would be loaded as a plugin.
