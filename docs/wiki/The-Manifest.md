# The manifest: `blockdesigner-plugin.json`

Every plugin jar has a `blockdesigner-plugin.json` at its root (in Gradle: `src/main/resources/blockdesigner-plugin.json`).
BlockDesigner reads it before loading any class (`PluginManager.readDescriptor`), for the Plugins window, the install
prompt, compatibility and updates. The file name is `PluginApi.DESCRIPTOR`.

A complete manifest, as the official plugins write it:

```json
{
  "id": "resource-tracker",
  "name": "Resource Tracker",
  "version": "@VERSION@",
  "author": "BlockDesigner",
  "description": "The materials a build needs, as the items you'd gather in survival ...",
  "updates": "https://github.com/doolecg/BlockDesigner-ResourceTracker",
  "main": "io.blockdesigner.resources.ResourceTrackerPlugin",
  "api": 5
}
```

`@VERSION@` is replaced with the Gradle project version when the jar is built (see
[Setting up a plugin project](Setting-Up-A-Plugin-Project.md)); in the jar it reads `"version": "1.0.1"`.

## Fields

| Field | Required | Read as | Meaning |
|---|---|---|---|
| `id` | **yes** | text | The plugin's identity. Must match `[a-z0-9_.-]+`, or the jar is rejected ("Plugin id '…' must use a-z, 0-9, _ . -"). Two jars with the same id can't both load: the second (in file-name order) is listed as broken, "Another jar already provides the plugin id '…'". The id also names the plugin's [data folder](Storing-Data-And-Settings.md), keys its saved options, tool keys and scene objects, identifies it for updates, and is what the updater expects the release jar to start with (`<id>-<version>.jar`). **Never change it** after the first release. |
| `main` | **yes** | text | Fully qualified name of the class implementing `BlockDesignerPlugin`. It needs a public no-argument constructor. Missing or blank: "The descriptor has no \"main\" class". A class that doesn't implement the interface fails when enabling. |
| `name` | no | text | Display name. Defaults to the id. |
| `version` | no | text | Shown in the Plugins window and the plugin's tab, and compared with release tags by the [updater](Automatic-Updates.md). Defaults to `""`, which the updater treats as `0` (so any release looks newer). Use `x.y.z`. |
| `author` | no | text | Shown in the Plugins window. Defaults to `""`. |
| `description` | no | text | Shown in the Plugins window, the install prompt and the plugin's tab. Defaults to `""`. |
| `api` | no | integer | The [API level](API-Levels.md) the plugin needs. Defaults to `1`. Higher than the app's `PluginApi.VERSION`: not loaded, shown as *needs a newer BlockDesigner*. |
| `updates` | no | text | Where releases are published, for [automatic updates](Automatic-Updates.md): a GitHub repository link `https://github.com/<owner>/<repo>` (a trailing `.git` or `/anything` is accepted). Missing, blank or not a GitHub link: the plugin is updated by hand, and the Plugins window says so. Read by BlockDesigner 0.4.16 and later; older versions ignore it. |

Other fields are ignored (the file is read as a JSON tree and only these keys are looked up), so adding your own
does no harm, but nothing reads them.

A jar without the file, or one that isn't a readable zip or JSON, doesn't appear as a plugin at all; it is listed
in the Plugins window as a broken jar with the reason ("Not a BlockDesigner plugin: no blockdesigner-plugin.json
inside", or the parser's message).

## Checklist

- `id` final, lower case, the same as `rootProject.name` so the jar is `<id>-<version>.jar`.
- `version` filled from the build (`@VERSION@`), never typed by hand in two places.
- `api` the lowest level whose features you use.
- `updates` set to the repository you publish releases in, if you want automatic updates.
- `main` points at the class that exists in the jar (the template's `ManifestTest` checks this).
