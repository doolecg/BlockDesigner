# Automatic updates

From BlockDesigner 0.4.16, a plugin can update itself from its GitHub releases. This page describes exactly what the
app does, from
[`PluginUpdater.java`](https://github.com/doolecg/BlockDesigner/blob/main/app/src/main/java/io/blockdesigner/app/plugins/PluginUpdater.java)
and `MainWindow.updatePlugins`, so you can make releases it accepts.

## What your plugin and release must have

1. **An `updates` link in the manifest** pointing at a GitHub repository:
   ```json
   "updates": "https://github.com/you/BlockDesigner-YourPlugin"
   ```
   It must match `https://github.com/<owner>/<repo>` (owner and repo made of `A-Z a-z 0-9 _ . -`); a trailing
   `.git` or any `/path` after the repo is accepted and ignored. `http://`, other hosts, or no link at all mean
   "updated by hand", and the Plugins window and the plugin's tab say so ("Updates by hand: it doesn't link a release
   source" / "…its release source isn't a GitHub repository"). With a valid link they say "Updates automatically from
   github.com/<owner>/<repo>".

2. **A published latest release** in that repository. BlockDesigner asks GitHub for
   `GET https://api.github.com/repos/<owner>/<repo>/releases/latest`, which only returns a published release that is
   not a draft and not a pre-release (GitHub's "Latest" badge). No such release: "github.com/<owner>/<repo> has no
   releases". The request is anonymous (no token), so the repository must be public.

3. **A tag that is the version.** The release's `tag_name`, with one leading `v` or `V` removed, is the version. It is
   compared with the installed plugin's manifest `version` number by number (`1.10.0` > `1.9.0`); anything after a
   `-` or `+` is ignored, so `1.2.0-beta` counts as `1.2.0`. Only a **newer** version is installed. An installed
   plugin with no `version` counts as `0`. The official plugins use plain tags: `1.0.2`.

4. **The jar attached to the release**, as a release asset whose name ends in `.jar`:
   - if the release has exactly one `.jar`, that one is used, whatever its name;
   - if it has several, one must be named `<plugin id>-…` (starting with the manifest id and a dash, case ignored),
     conventionally `<id>-<version>.jar`; otherwise the update fails with "its <v> release has several jars and none
     is named <id>-<version>.jar". If several start with `<id>-` (say a `-sources.jar` too), the last one GitHub
     lists wins, so **attach only the plugin jar**;
   - no `.jar` at all: "its <v> release has no .jar".

5. **Inside the downloaded jar**, the manifest must have the **same `id`** (otherwise "the download is a different
   plugin") and an `api` this BlockDesigner has (otherwise it is skipped with "<Name> <v> needs a newer
   BlockDesigner", and tried again at the next check).

6. **The jar's manifest `version` must equal the tag.** BlockDesigner doesn't check this, but if the jar says an
   older version than the tag, the installed version stays older than the latest release, and the plugin is
   downloaded and reinstalled at every start. Fill the version from the build (`"version": "@VERSION@"`, see
   [Setting up a plugin project](Setting-Up-A-Plugin-Project.md)) and tag with the same number.

In short: **manifest `updates` = your public GitHub repo; release tag = `x.y.z` = manifest version; exactly one asset,
`<id>-x.y.z.jar`; not a draft or pre-release.** The [release checklist](Releasing-On-GitHub.md) produces exactly that.

## What happens during an update

For each installed plugin whose manifest has a valid `updates` link (on, off or failed alike), on a background
thread, one after another:

1. Ask GitHub for the latest release and pick the jar as above.
2. Download it into `%TEMP%\BlockDesigner-update\plugins\<id>\`, checking its size against the release and its
   SHA-256 against the `digest` GitHub publishes for the asset (when GitHub provides one). A mismatch installs nothing.
3. Read its manifest; check the id and the API level.
4. On the JavaFX thread, install it like **Install…** does: unload the old plugin, delete its jar if the file name
   differs, copy the new jar into the plugins folder under the asset's file name, and enable it. If the plugin was
   **off** before, it is switched off again straight away. The plugin's log gets "Updated from <old> to <new>
   (<release page>)".

Your plugin's `disable()` runs on the old version, and `enable()` on the new one, in the same session: no restart.
Data in the [data folder](Storing-Data-And-Settings.md) and saved settings are kept.

## When it checks

- **At every start**, once, right after the plugins are loaded, if **Update plugins automatically** is on (it is on
  by default; setting `autoUpdatePlugins` in `settings.json`). The startup check is quiet: it only speaks up when
  something was updated (a toast "Updated <Name> <v>" and a status-bar summary). Failures at startup are not shown.
- **On demand**: **Plugins › Manage plugins… › Check for updates** runs the same check and shows a one-line summary
  ("Every plugin is up to date.", "Updated …", "Not updated: …") next to the button and in the status bar.
- There is no periodic check while BlockDesigner is running.

## How users switch it off

**Plugins (puzzle icon) › Manage plugins…** and untick **Update plugins automatically** (tooltip: "At startup,
plugins that link their release source update to their newest release"). It is saved at once. Check for updates
still works by hand. There is no per-plugin switch: a user who wants to keep one plugin at an old version has to
turn automatic updates off altogether (or install a jar without an `updates` link).

## Things to know

- Every plugin is one anonymous GitHub API request per check; GitHub limits anonymous requests per IP address (at
  the time of writing, 60 an hour). That is plenty for startup checks, but many restarts in an hour with many
  plugins can hit it; the check then fails quietly until the limit resets.
- A release that raises the plugin's `api` level reaches only users whose BlockDesigner has that level; the others
  keep the previous version until they update BlockDesigner (and are told so when they check by hand).
- Pulling a bad release: publish a fixed, **higher** version. Deleting the release makes the previous one "latest"
  again, but installed copies never go back to a lower version by themselves.
- Pre-releases are never offered, which makes them a safe way to share test builds.
- Installing a plugin by hand (Install…) always works, with or without an `updates` link.
