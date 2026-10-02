# Getting started

BlockDesigner is a Windows app for planning Minecraft builds outside the game. This page covers installing it, the first launch, where your settings are kept and how updates work.

## Download

Get the latest version from the [releases page](https://github.com/doolecg/BlockDesigner/releases/latest). There are two downloads:

| File | Use it if... |
|---|---|
| **The installer (`.msi`)** | You want a normal install. It installs to `C:\Program Files\BlockDesigner`, adds Start menu and desktop shortcuts, and makes `.bdproj` projects open in BlockDesigner. Installing a newer version upgrades in place. |
| **The portable zip** (`BlockDesigner-<version>-portable.zip`) | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

Both include their own Java runtime, so there is nothing else to install.

## First launch

Pick the Minecraft version whose textures and models BlockDesigner should use. You can also pick a modded instance (Prism, CurseForge, Modrinth or the official launcher), so modded blocks such as Create's render too.

The textures come from your own Minecraft install. Nothing from Minecraft is shipped with the app.

Then try [building like in Minecraft](Building-Like-In-Minecraft). Press **F1** at any time to see your current keys.

## Where your settings are kept

- **Installed version:** `%APPDATA%\BlockDesigner`, never the install folder, so settings survive updates and reinstalls.
- **Portable version:** a `data` folder next to `BlockDesigner.exe`.

This holds your theme, keybinds, recent files and plugins. Each new version copies your settings into `%APPDATA%\BlockDesigner\backups` the first time it starts.

Under **Settings > General > Your settings** you can save a backup to a file, load one (for example on another PC) or reset everything to the defaults.

## Updates

When it opens, BlockDesigner checks GitHub for a newer release and shows its notes. **Install and restart** downloads it, checks it against the release's checksum and installs it. You can skip a version, or turn the check off under **Settings > General > Updates**.

## Windows warnings

Releases aren't code-signed yet. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting, even after a successful install or update, and SmartScreen may warn about it.

## Next

- [Building like in Minecraft](Building-Like-In-Minecraft)
- [Controls and keybinds](Controls-And-Keybinds)
- [User FAQ](User-FAQ)
