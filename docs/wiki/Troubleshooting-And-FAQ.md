# Troubleshooting and FAQ

## My plugin doesn't appear in the Plugins window

- The jar must be **directly** in the plugins folder (`%APPDATA%\BlockDesigner\plugins`, or `data\plugins` for the
  portable build), not in a subfolder, and end in `.jar`. Press **Reload** after copying.
- If it appears at the bottom as a **broken jar**, the reason is shown: no `blockdesigner-plugin.json` at the jar's
  root (check `src/main/resources`), invalid JSON, an `id` with characters other than `a-z 0-9 _ . -` (upper case
  counts), no `main`, or another jar with the same id (the first by file name wins; remove the old copy).

## It says "failed to start"

The error under it is the exception from loading or `enable`:

- `ClassNotFoundException: …` - `main` names a class that isn't in the jar (package typo, or the class is in a
  subproject that wasn't built into the jar).
- "… does not implement BlockDesignerPlugin" - the `main` class is the wrong one.
- `NoSuchMethodException: …<init>()` or `IllegalAccessException` - the main class has no public no-argument
  constructor (or the class itself isn't public).
- `NoClassDefFoundError` for a library - you compiled against a library that is neither in BlockDesigner nor bundled
  in your jar. Bundle it (BlockDesigner provides only its own API, `core`, JavaFX, Jackson and its other
  dependencies).
- `IllegalArgumentException: The command /x already exists` - pick another command name (see
  [BlockEdit commands](BlockEdit-Commands.md)).
- `IllegalArgumentException: … registered twice` or "… ids use a-z …" - fix the id.
- Anything else - your `enable` threw. The plugin's log and the Console have the line "Failed to enable: …".

## It says "needs a newer BlockDesigner"

The manifest's `api` is higher than the running BlockDesigner's `PluginApi.VERSION` (shown as "Plugin API N" in the
Plugins window). Update BlockDesigner, or lower `api` if you don't use the newer features.

## My plugin worked on an older BlockDesigner and breaks on a newer one

API levels only add things, so API calls keep working. But `core` classes (`BlockState`, `Structure`, `Layer`,
`SchematicFormat`, `WorldEdit.World`…) come with the API and are not versioned by the level; a change there can break
a plugin compiled against older jars. Rebuild against the newer jars and run your tests; the official plugins do this
after every BlockDesigner release. Anything outside `io.blockdesigner.plugin` and the `core` types it exposes (the
app's own classes, AtlantaFX internals) can change without notice; don't depend on it.

## It doesn't update itself

Go through [Automatic updates](Automatic-Updates.md). The usual causes:

- no `updates` in the manifest, or not an `https://github.com/owner/repo` link (the Plugins window row says "Updates
  by hand");
- the release is a draft or a pre-release, or the repository is private;
- the tag isn't newer than the installed `version` (tag `1.2`, installed `1.2.0` are equal);
- no `.jar` attached, or several jars and none named `<id>-…`;
- the new jar declares a higher `api` than the user's BlockDesigner;
- **Update plugins automatically** is off (Check for updates still works).

Run **Check for updates** by hand: its summary names the problem ("Not updated: …").

## It updates at every start

The jar's manifest `version` is lower than the release tag, so the installed version never catches up. Fill the
version from the build (`@VERSION@`) and tag with the same number.

## My panel is empty or throws on startup

Build JavaFX nodes in `PluginPanel.create`, not in `enable` or a constructor: the plugin is enabled before any panel
is shown. Only compile against JavaFX (`compileOnly`), don't bundle it.

## My changes can't be undone / undo breaks things

You changed `layer.structure()` directly. Use `ctx.editWorld`, `ctx.editor().edit(layer, label)`, a command's or
transform's `world()`, or a tool's stroke.

## The UI freezes while my plugin works

Everything except exporters and importers runs on the JavaFX thread. Move long work to your own thread and come
back with `ctx.runOnUiThread(...)`; stop the thread in `disable()`. Keep `enable` and transform previews quick.

## Where do my `System.out.println` lines go?

To the Console (bottom left), without your plugin's name; stderr shows as a warning. `ctx.log(...)` is better: it
goes to your plugin's log in the Plugins window and to the Console under your name. A log line containing "failed"
or starting with "Error" shows as an error.

## Where is my data after uninstalling?

Uninstall deletes only the jar. The data folder (`plugins\<id>`) and saved settings (`settings.json`,
`pluginOptions`) stay, so a reinstall picks them up.

## Can two plugins talk to each other?

Not through the API. Each plugin has its own class loader and can't see another plugin's classes. There is no
plugin-to-plugin service mechanism; they can only both react to the same scene events and edits.

## Why is the command class called `WorldEdit` when the app says BlockEdit?

The class predates the rename and is part of the API; see [BlockEdit commands](BlockEdit-Commands.md).

## Does my plugin have to live in its own repository?

For third-party plugins that's up to you. The official ones each have their own repository
(`BlockDesigner-<Name>`), release on their own schedule and are never built inside BlockDesigner's repository; only
the two examples used by BlockDesigner's tests live there.

## Unclear or not documented yet

- There is no documented way to declare a dependency on another plugin or on a maximum BlockDesigner version.
- Whether `PluginPanel.dock()` will ever place panels somewhere other than the plugin's tab is open (it is unused
  today).
