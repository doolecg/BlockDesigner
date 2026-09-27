# How plugins are loaded

Everything on this page is in
[`app/src/main/java/io/blockdesigner/app/plugins/PluginManager.java`](https://github.com/doolecg/BlockDesigner/blob/main/app/src/main/java/io/blockdesigner/app/plugins/PluginManager.java)
unless it says otherwise.

## The plugins folder

`<settings folder>\plugins`, where the settings folder is (`Settings.dir()`):

- installed BlockDesigner: `%APPDATA%\BlockDesigner`, so plugins are in `%APPDATA%\BlockDesigner\plugins`;
- portable build: `data\plugins` next to `BlockDesigner.exe` (it starts with `-Dblockdesigner.dataDir` pointing at `data`);
- anything else started with `-Dblockdesigner.dataDir=<dir>`: `<dir>\plugins`.

The folder is created if it doesn't exist. **Plugins › Manage plugins… › Open folder** opens it.

## Startup

When the main window is shown, `loadAll()` runs on the JavaFX thread:

1. Every `*.jar` directly in the folder (not in subfolders) is listed and sorted by file name.
2. For each jar the [manifest](The-Manifest.md) is read. A jar with no manifest, a broken zip or JSON, a bad `id` or
   no `main` goes to the **broken jars** list with the reason. A second jar with an id already seen is broken too.
3. A plugin whose `api` is higher than `PluginApi.VERSION` is marked **INCOMPATIBLE** and never loaded.
4. Every other plugin is **enabled**, unless the user switched it off before (its id is in `disabledPlugins` in
   `settings.json`), in which case it is **DISABLED** and none of its classes are loaded.
5. If any plugin failed, the status bar says "N plugin(s) failed to load · see Plugins > Manage plugins".
6. If automatic plugin updates are on, the [update check](Automatic-Updates.md) starts in the background.

## Enabling: class loading

For each plugin BlockDesigner:

1. creates a `URLClassLoader` named `plugin-<id>` over the plugin's jar, whose **parent is the app's class loader**;
2. loads the `main` class through it and checks it implements `BlockDesignerPlugin`;
3. creates an instance with the public no-argument constructor;
4. creates the plugin's `PluginContext` and calls `enable(context)`;
5. marks it **ENABLED** and logs "Enabled · 1 panel · 1 action" (a summary of what it registered).

What that means for you:

- Each plugin has its own class loader, so two plugins can contain classes with the same names without clashing.
- Class lookups go to the parent first (standard Java delegation). Everything on BlockDesigner's class path is
  visible to the plugin: the plugin API, `core`, JavaFX, Jackson (2.20) and, technically, the app's own classes. Only
  `io.blockdesigner.plugin` and the `core` types it uses are API; anything else can change in any release.
- If you bundle a library BlockDesigner also has, BlockDesigner's copy is the one used.
- Plugins can't see each other's classes.

If anything in steps 1 to 4 throws (including an exception from your `enable`), the plugin is marked **FAILED**, its
error is kept as "ExceptionClass: message" (the cause, for exceptions from the constructor), "Failed to enable: …"
is logged, and everything it registered before the failure is removed again.

## States

| State | Plugins window says | Meaning |
|---|---|---|
| ENABLED | on · what it registered | Loaded and running. |
| DISABLED | off | Switched off by the user (remembered in `settings.json`), or not started yet. |
| FAILED | failed to start | Its class couldn't be loaded or `enable` threw; the error shows under it and in its log. |
| INCOMPATIBLE | needs a newer BlockDesigner | Its `api` is newer than this BlockDesigner's. Its check box is disabled. |

## Turning a plugin off, reloading, uninstalling

- **Off** (its check box in the Plugins window, or Turn off in its tab): BlockDesigner calls the plugin's `disable()`,
  then removes everything it registered (formats, commands, exporters, importers, actions, transforms, tools,
  settings, event listeners), calls `dispose()` on its panels, parks its scene objects (they stay in the project and
  come back when it is on again), and closes its class loader. The id goes into `disabledPlugins`.
- **On** again: a fresh class loader, a fresh instance, a fresh context, `enable` again. Static state from the
  previous run is gone.
- **Reload** (Plugins window): unloads every plugin and runs the whole startup scan again. This is the quickest way
  to try a rebuilt jar you copied into the folder.
- **Install…**: reads the manifest, asks for confirmation (showing author and description and a warning that plugins
  run with full access), unloads an installed plugin with the same id, deletes its jar if the file name differs,
  copies the new jar into the folder and enables it (even if the old one was off; the automatic updater keeps it off
  instead). "Installed, but it failed to start" shows the error.
- **Uninstall**: unloads the plugin and deletes its jar. Its [data folder](Storing-Data-And-Settings.md) and saved
  options are **not** deleted. PLUGINS.md notes that if Windows still has the jar locked, it can be deleted after a
  restart.
- **App shutdown**: every plugin is unloaded (so `disable()` runs).

## Errors while running

A plugin can't take BlockDesigner down by throwing:

- Menu actions, commands, settings callbacks, event listeners, scene object drawing and menus are called inside a
  try/catch. The exception is logged against the plugin ("'Label' failed: …", "/name failed: …", "Event listener
  failed: …", "Scene object failed: …"). Failing actions, settings callbacks and scene objects are also shown as a
  toast "✖ <Plugin>: message"; a failing command shows its error in the command bar; a failing event listener is
  only logged.
- In commands and transforms, an `IllegalArgumentException` is the friendly way to report bad input: its message is
  shown to the user as an error without being logged as a failure.
- An exception from `disable()` is logged ("Error while disabling: …"); unloading continues.
- Registering something invalid throws from the `register…` call (a duplicate id, an id with the wrong characters, a
  `/command` name that already exists). Uncaught, that fails `enable`, and the plugin is FAILED.

## Logs and the Console

Each plugin has a log: its own `ctx.log(...)` lines plus the lines BlockDesigner writes about it (enabled, failures,
updates). The Plugins window shows the last 200 lines with a time stamp.

Since the Console (bottom left of the main window) arrived, every plugin log line also goes to it (`ConsoleLog`),
with the plugin's name as the source. Lines that contain "failed" or start with "Error" are shown at ERROR level,
everything else at INFO. So `ctx.log("Download failed: timeout")` shows as an error in the Console, and
`ctx.log("Ready")` as information. Anything a plugin prints to `System.out` / `System.err` also reaches the Console
(the Console captures both streams, stderr at WARN level), but without the plugin's name, so prefer `ctx.log`.
