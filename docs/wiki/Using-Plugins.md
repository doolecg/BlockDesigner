# Using plugins

Plugins are `.jar` files that add schematic formats, exporters, importers, menu actions, `/commands`, transforms with a live preview, side panels and tools.

Plugins run with the same access as BlockDesigner itself, so only install ones you trust.

## The Plugins window

Open it from the Plugins (puzzle icon) menu with **Manage plugins...**. Each plugin is a card with its status, a settings button, **Turn on** / **Turn off**, uninstall and its log. You can install, enable or disable, reload and uninstall plugins there.

## Installing a plugin

- Pick it under **Suggested to install**. It lists the BlockDesigner team's plugins you don't have yet, each with an **Install** button that downloads its newest release.
- Or download the plugin's `.jar` from its releases page and drop it on the drop box in the window. You can also click the drop box to pick the file.

## Official plugins

Each lives in its own repository and is released separately from BlockDesigner.

| Plugin | What it does | Needs |
|---|---|---|
| [BlockCompanion Plugin](https://github.com/doolecg/BlockDesigner-ResourceTracker) (formerly Resource Tracker; installed with BlockDesigner) | Links BlockDesigner to the BlockCompanion Minecraft mod: send projects into the game and edit builds from it. Also the materials a build needs as items (stacks and shulker boxes), what you have gathered and what is left, saved with each project | 0.4.24 |
| [Palette Tools](https://github.com/doolecg/BlockDesigner-PaletteTools) | Weathering, palette swap and gradient transforms, a Palette panel, a colour palette exporter, a pixel art importer and a Wall tool | 0.4.24 |

"Needs" is the BlockDesigner version the plugin requires.

BlockDesigner installs the BlockCompanion Plugin, switched on, the first time it starts. If you uninstall or switch it off, it stays that way.

## Using a plugin

- Each running plugin has a tab on the right with its pages. The info button on the tab shows what it adds.
- A plugin's settings are on its page under **Plugins** in **Settings**.
- Its tools and Plugins-menu actions can be given keys in **Settings > Keybinds**.
- When two plugins can import the same kind of file, Import asks which one to use.

## Updates

**Check for updates** in the Plugins window updates every plugin.

## Reference Planes

Reference Planes is no longer a plugin. It is built in as [reference images](Reference-Images).

## Writing a plugin

See the [plugin developer wiki](Home#blockdesigner-plugin-developer-wiki), starting with [What plugins can do](What-Plugins-Can-Do) and [Setting up a plugin project](Setting-Up-A-Plugin-Project).
