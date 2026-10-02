# Releasing on GitHub

A release is what users download and what the [automatic updater](Automatic-Updates) installs. The official
plugins all release the same way; the steps below work for any plugin.

## Checklist

1. **Version.** Raise `version` in `build.gradle.kts` (the manifest takes it from there through `@VERSION@`). Patch
   for fixes or a rebuild against newer API jars, minor for new features.
2. **API level.** If you now use something from a newer [API level](API-Levels), raise `"api"` in the manifest and
   the "needs BlockDesigner X or later" line in your README and notes. Rebuilding against newer jars alone doesn't
   change the minimum.
3. **Release notes.** Add a section at the top of `RELEASE_NOTES.md` (see below).
4. **Build and test.** `./gradlew jar test`. Check `build/libs/<id>-<version>.jar` exists and that
   `blockdesigner-plugin.json` inside it has the new version.
5. **Commit and push** to `main`.
6. **Tag** with the plain version and push the tag: `git tag 1.2.0 && git push origin refs/tags/1.2.0`. (`v1.2.0`
   works for the updater too; the official plugins use plain tags.)
7. **Create the GitHub release** from that tag: title `<Name> <version>`, the notes section as the body, not a draft,
   not a pre-release, marked as latest. Attach **only** `build/libs/<id>-<version>.jar`.
8. **Check** the release page shows it as Latest with one `.jar`, then run **Check for updates** in BlockDesigner
   with the previous version installed: it should update.

## Release notes format

The official plugins keep `RELEASE_NOTES.md` newest first, sections separated by a line containing only `---`:

```markdown
# Resource Tracker 1.0.1

Kept up to date with BlockDesigner 0.4.22: built and tested against its plugin API. Nothing changes in how it works.

**Needs BlockDesigner 0.4.17 or later** (plugin API 5).

## Changed
- Built against the BlockDesigner 0.4.22 plugin API.

---
```

For a feature release: one or two sentences on what it's about, the **Needs** line, then `## New`, `## Changed`,
`## Fixed` with a short bold phrase leading each bullet.

## The official plugins' publishing script

The maintainer publishes with a local script, `publish_plugin.py` (kept with the maintainer's local tooling, not in
the public repository; it uses the GitHub REST API with git's stored credential, no `gh` needed). It enforces the
checklist above, which is a good model for your own tooling:

- reads `version` (a column-0 `version = "…"` line) from `build.gradle.kts` and `rootProject.name` from
  `settings.gradle.kts`;
- takes the notes above the first `---`, which must start with `# <Name> <version>`;
- requires `build/libs/<rootProject.name>-<version>.jar`, the tag pushed, and `main` pushed;
- creates the release (tag = version on `main`, title `<Name> <version>`, body = the notes, latest), uploads the jar,
  and reports what GitHub now lists as latest. `--dry-run` checks everything and changes nothing.

Because it uploads `<rootProject.name>-<version>.jar` and the updater looks for `<id>-…`, keep `rootProject.name`
equal to the manifest id.

## Two-module builds

If the plugin jar is built in a subproject (like Terrain Generator's `plugin/`), copy it into the root
`build/libs/` (Terrain Generator registers a root `jar` task for that) so release tooling and people find it in the
usual place, and give it the `<id>-<version>.jar` name with `base.archivesName = "<id>"`.

## After the release

- If your plugin is listed somewhere (BlockDesigner's README lists the official ones, with the minimum version),
  update the listing when the minimum changed.
- Users with automatic updates get the new version at their next start; others see it on the release page.
