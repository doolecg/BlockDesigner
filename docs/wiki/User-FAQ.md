# User FAQ

## Where do the textures come from?

From your own Minecraft install. Nothing from Minecraft is shipped with BlockDesigner. On first launch, pick the Minecraft version to use. To get modded blocks such as Create's, pick a modded instance (Prism, CurseForge, Modrinth or the official launcher).

## Windows won't start BlockDesigner, or SmartScreen warns about it

Releases aren't code-signed yet. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting, even after a successful install or update. SmartScreen may also warn about it.

## Where are my settings? Will an update lose them?

Installed version: `%APPDATA%\BlockDesigner`. Portable version: the `data` folder next to `BlockDesigner.exe`. Updates and reinstalls don't touch them, and each new version copies them to `%APPDATA%\BlockDesigner\backups` the first time it starts.

## How do I back up my settings or move them to another PC?

**Settings > General > Your settings** saves a backup to a file, loads one, or resets everything to the defaults.

## How do I stop the update check?

Turn it off under **Settings > General > Updates**. You can also skip one version when it offers an update.

## How do I change a key?

**Settings > Keybinds** (Ctrl+,). Each action can have a second key, and clashes are flagged. Press **F1** to see your current keys. See [Controls and keybinds](Controls-And-Keybinds).

## A single-letter key does nothing

Those keys don't fire while you're typing in a text box. While flying, W A S D, Space, Shift and Ctrl belong to flight.

## Where did Reference Planes go?

It is built in as [reference images](Reference-Images). If you have the old plugin, BlockDesigner stops loading it, renames the jar to end in `.retired` and tells you once. Your settings, key and the pictures in your projects are kept.

## Where did Resource Tracker go?

It is now the BlockCompanion Plugin. See [Using plugins](Using-Plugins).

## How do I test a worldgen data pack?

Install it into a world and run `/place structure <namespace>:<name>`. See [Import and export](Import-And-Export).

## Something else

Open an issue on the [GitHub repository](https://github.com/doolecg/BlockDesigner/issues). If the problem is with a plugin, use that plugin's repository.
