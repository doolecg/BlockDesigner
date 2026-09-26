# BlockDesigner 0.4.14

Every plugin gets its own tab on the right, so you can see it's running, change its settings and use what it adds.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.14.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.14.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.14-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## New
- **A tab for every plugin.** Each running plugin has its own tab on the right. It shows the plugin's version and a green **Running** badge (or why it failed), its settings, and everything it adds: buttons for its actions, tools, transforms and panels, and its commands, file formats and objects. It also has **Manage plugins…** and **Turn off** buttons. Close it like any other tab.
- **Plugin settings (plugin API 4).** Plugins can put settings in their tab. BlockDesigner draws the controls, has a **Reset to defaults** button and keeps the values between runs. [Reference Planes 1.1.0](https://github.com/doolecg/BlockDesigner-ReferencePlanes) uses this for how new pictures start out.

---

# BlockDesigner 0.4.13

A Reach slider for creative flight, a longer default reach, and links to the plugins in their new homes.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.13.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.13.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.13-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## New
- **Reach slider** in Viewport settings › Flying. It sets how far away you can break and place blocks while flying, from 3 to 64 blocks.

## Changed
- **Longer reach while flying:** 7 blocks by default, a couple more than before.
- **Plugins have their own homes.** [Reference Planes](https://github.com/doolecg/BlockDesigner-ReferencePlanes), [Palette Tools](https://github.com/doolecg/BlockDesigner-PaletteTools) and [Hello Plugin](https://github.com/doolecg/BlockDesigner-HelloPlugin) each have their own repository and releases, linked from the README.

---

# BlockDesigner 0.4.12

Shapes place blocks facing the right way and can break too, and plugins can now add their own objects to the scene, which the new Reference Planes plugin uses.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.12.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.12.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.12-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## New
- **Break with shapes.** With a shape chosen in Build mode, left-drag outlines it in red and breaks every block in it when you let go (as in Effortless Building). One undo puts it back.
- **Plugin objects in the scene (plugin API 3).** Plugins can add their own objects, such as pictures, with a row in Layers, a right-click menu, saving in the project, and Move, Rotate and Scale. The **Reference Planes** plugin, released separately, uses this for reference images.

## Changed
- **Shapes place blocks the right way round.** Every block in a line, wall, floor or other shape is turned as a click on the face you started from would turn it: stairs, slabs, torches and the rest. Logs, pillars and chains follow the direction of a line.

---

# BlockDesigner 0.4.11

The wheel zooms again in Build mode, shapes end on the side of the block you point at, and notifications move to the bottom left.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.11.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.11.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.11-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## Changed
- **Build mode wheel.** The mouse wheel zooms again, and Alt+wheel now picks the hotbar slot. Scrolling with Alt held doesn't open the shape wheel. Shift+wheel still turns the layer.
- **Shapes follow the block side at both ends.** While dragging out a line, floor or wall, pointing at a block ends the shape on the side you aim at, just where a single block would go, as long as that spot lies on the shape's line or plane. A line into a wall now stops at the wall's face.
- **Notifications** now pop up at the bottom left of the viewport.
- **Creative flight in Build mode** shows two pills at the top left: Build mode and Creative.

---

# BlockDesigner 0.4.10

A Scale tool next to Move and Rotate, a Minecraft-style wheel in Build mode, moving and turning by the side of a layer's box, and a tidier viewport.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.10.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.10.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.10-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## New
- **Scale tool (S).** It sits with Move and Rotate. Drag a square handle to stretch the selected layers (or selected blocks) along one axis, or drag the centre to scale them evenly. Blocks are resized in whole blocks, keeping their look. Right-click or Esc cancels, and one undo puts it back.
- **Block size slider** in the Blocks panel header, to make the block icons smaller or bigger.
- **Build mode wheel, as in Minecraft.** The mouse wheel picks the hotbar slot, Alt+wheel zooms, and Shift+wheel turns the layer under the mouse.
- **A pill per active feature.** Next to the mode badge, Replace, Shuffle, the shape, Symmetry and Flying each get their own pill.

## Changed
- **Moving and turning go by the layer's bounding box.** Ctrl+wheel moves, and Alt+wheel (Shift+wheel in Build mode) turns, by the side of the layer's box under the mouse, never by the camera angle. With nothing under the mouse, nothing moves.
- **The block info box** now sits at the top centre of the viewport.
- **Notifications** now pop up at the bottom right, just under the key hints.
- **View cube:** the X, Y and Z lines are now as long as the cube's edges.
- **New defaults:** dark mode, and sounds at 5% volume. Your saved settings are kept.

---

# BlockDesigner 0.4.9

A small fix to update prompts: BlockDesigner only asks you to update when there's a newer version.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.9.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.9.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.9-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. A copy installed in AppData moves to Program Files with this update, because it installs with the `.msi`. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## Fixed
- **No update prompt when you're up to date.** In 0.4.8, a copy in AppData was asked to move to Program Files even on the latest version. Now the move just happens as part of the next real update, and the update window says so.

---

# BlockDesigner 0.4.8

A fix for updating from 0.4.6 and earlier. Those versions couldn't install 0.4.7 by themselves, and sent you to this page instead. Now they update as usual, and afterwards BlockDesigner offers the move to `C:\Program Files`.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.8.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner` (Windows asks for admin). Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.8.exe** | Only for updating from 0.4.6 or earlier: their updater installs it for you. For new installs, use the `.msi`. |
| **BlockDesigner-0.4.8-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand: download the `.msi` above.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## Fixed
- **Updating from 0.4.6 and earlier works again.** Releases include the classic setup `.exe` again, which those versions' updater installs in place.

## New
- **Move to Program Files:** a copy installed in AppData (by 0.4.6 or earlier, or by their updater) now offers **Move BlockDesigner to Program Files** in the update window. It closes, installs for all users with the `.msi` (Windows asks for admin), and opens the new copy. That copy then removes the one in AppData. Your settings are kept.

---

# BlockDesigner 0.4.7

BlockDesigner now installs into `C:\Program Files`. Your settings are backed up automatically and can be saved, loaded or reset. The tools are on the number keys. You can always see which mode you're in, and the brush has a proper options bar.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.7.msi** | You want a normal install. It installs for all users into `C:\Program Files\BlockDesigner`, and Windows asks for admin. |
| **BlockDesigner-0.4.7-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

**Updating from 0.4.6 or earlier:** install this one by hand. Earlier versions installed per-user into AppData, and their updater can't make the move, so it points you to this page instead. Run the `.msi`. The first time 0.4.7 starts, it removes the old AppData copy, so you don't end up with two. Your settings are kept. From 0.4.7 on, updates install themselves again, with a Windows admin prompt.

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting.

## New
- **Installs to Program Files:** the installer is now an `.msi` that installs for all users into `C:\Program Files\BlockDesigner`, and you can still pick another folder. Settings, plugins and caches stay in `%APPDATA%\BlockDesigner` and your temp folder, never in the install folder.
- **Your settings, safe across updates:**
  - The first start of each new version copies your settings into `%APPDATA%\BlockDesigner\backups`, keeping the last ten.
  - Settings › General › Your settings has **Save a backup…**, **Load a backup…** (for example on another PC) and **Reset to defaults…**. A reset keeps your Minecraft version, mods and recent files.
  - Settings are saved safely, so a crash mid-save can't corrupt them. A settings file that can't be read is set aside, not overwritten.
- **Mode badge:** the top-left of the 3D view always shows the mode you're in, for example **BUILD MODE** (with Replace, Shuffle, the shape, or Creative flight), **BRUSH · Smooth**, ERASER, SELECT, MOVE or VIEW. The edit modes also get a thin coloured frame round the view: Build green, Brush blue, Eraser red.
- **Brush options bar:** the paint brush and eraser have a bar along the top of the view, like a sculpting app's tool header. It has the tool's coloured pill, a Mode menu (with each mode's key), labelled Size and Strength sliders with their values, Sphere / Cube, and a button for every setting.

## Changed
- **New default tool keys:** the toolbar's tools are on the number keys, in toolbar order.

  | Tool | Key |
  |---|---|
  | View | 1 |
  | Select | 2 |
  | Build | 3 |
  | Move | G |
  | Rotate | R |
  | Brush | 4 |
  | Eraser | 5 |

  In Build mode, 1–9 still pick hotbar slots, as in Minecraft; leave Build mode with Esc. Over a block in the palette, 1–9 fill that hotbar slot in any mode. While you're placing an import, R still turns it. You can change any key in Settings › Keybinds.
- **The default theme is Claude.** This only affects new installs and settings resets.
- **Key hints no longer overlap the hotbar:** they sit above it.

## Fixed
- **The Build-mode tip** said to press the Build key to leave. That key now picks a hotbar slot in Build mode, so the tip says Esc.

## Docs
- A new README, a full plugin guide (`PLUGINS.md`) and an API reference (`docs/plugin-api-reference.md`).

---

# BlockDesigner 0.4.6

Softer interface sounds: clicks, the hotbar, and picking up or putting down blocks now use the place and break sounds, played lower and quieter, so everything sounds like one set.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.6.exe** | You want a normal install. Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.6-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand (see [0.4.2](https://github.com/doolecg/BlockDesigner/releases/tag/0.4.2)).

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## Changed
- **The interface sounds are quieter, lower versions of the place and break sounds.** They replace the high-pitched ones from 0.4.5:
  - **buttons, toggles, menu items and the hotbar:** the place sound, low and soft, rising a little from hotbar slot 1 to 9;
  - **picking up a block** (from the palette, or middle-click): the break sound, lower and quiet;
  - **a block landing in the hotbar:** the place sound at its deepest.
  
  Building itself sounds as before. Viewport settings › Editing › UI sounds still turns them off.

---

# BlockDesigner 0.4.5

Sounds: painting with the brush has its own looping sound, and the interface has soft sounds for clicks, picking up blocks and the hotbar. Clicking a button over the 3D view no longer also breaks, places or paints behind it.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.5.exe** | You want a normal install. Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.5-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and later update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand (see [0.4.2](https://github.com/doolecg/BlockDesigner/releases/tag/0.4.2)).

**Windows Smart App Control:** this build still isn't code-signed. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting after an install or update.

## New
- **Painting sound:** the paint brush plays a brushing sound while you paint. It loops for as long as you hold the button, and its ending plays when you let go. It replaces the thud the brush used to make on every dab. The eraser keeps its break sounds.
- **UI sounds**, in the spirit of the Extra Sounds mod:
  - buttons, toggles and menu items play a soft tick, the same one as changing hotbar slots;
  - taking a block from the palette or picking one in the world plays a pick-up pop;
  - a block landing in the hotbar plays a put-down pop.
  
  Turn them off under Viewport settings › Editing › UI sounds. The place / break switch is now "Build sounds (place, break, paint)", and both share the volume slider.

## Fixed
- **The update window shows the release notes formatted**, with headings, lists, tables, bold and links, instead of raw Markdown text.
- **Clicking the interface over the 3D view no longer acts on the world behind it.** This covers the viewport buttons, the hotbar, the brush bar, the view cube and the command line. Before, a click there could also break, place or paint the block behind the button.

---

# BlockDesigner 0.4.4

Plugin API v2: plugins can now add transforms with a live preview, side panels, their own tools, importers and richer exporters. Also in this release: every key label now shows your keybinds, and you can fill hotbar slots straight from the palette.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.4.exe** | You want a normal install. Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.4-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 and 0.4.3 update to this by themselves. 0.4.0 and 0.4.1 need a one-time install by hand (see [0.4.2](https://github.com/doolecg/BlockDesigner/releases/tag/0.4.2)).

**Windows Smart App Control:** this build isn't code-signed yet. On PCs with Smart App Control switched on, Windows may block BlockDesigner from starting, even after a successful install or update. The build is ready to sign as soon as a certificate is in place.

## New
- **Plugin API v2.** Plugins that declare `"api": 2` can add:
  - **Transforms** with an options dialog and a live preview as ghosts in the view. They have a seed and a Reroll button, and apply as one undo step. They appear under Plugins › Transform, in the viewport's right-click menu and as `/transform <id>`.
  - **Panels** as closable tabs on the right.
  - **Tools** in the tool dock, with options above the hotbar. Each stroke is one undo step.
  - **Importers** for Import and drag and drop, such as images turned into pixel art.
  - **Richer exporters**, with options, progress and access to block models and textures.
  - **Supporting pieces:** declarative options (remembered per plugin), scene events, and a block catalog that knows block families (oak → stairs, slabs, fences…) and cracked or mossy variants.

  API 1 plugins keep working unchanged. There is a new example plugin, `examples/palette-tools`, with Weathering, Palette swap and Gradient transforms, a Palette panel, a colour-palette exporter, a pixel-art importer and a Wall tool. See `PLUGINS.md` and `docs/plugin-api-v2.md`.
- **Fill hotbar slots from the palette:** hover a block in the palette and press a hotbar key (1–9) to put it in that slot, like Minecraft's creative inventory.
- **Tooltips show the key and what the button does**, on every button that has a key.

## Changed
- **Every key the app shows comes from Settings › Keybinds:** button tooltips, menu hints, the Shortcuts list (F1), status messages and the hotbar's Replace / Shuffle labels. A few of these still showed old keys, such as "Shuffle (Z)" and "R places again".
- **Arrow keys are written out** as Left / Right / Up / Down in key labels.

## For builders of BlockDesigner
- **Code signing step:** the packaging tasks can now sign the app, its native DLLs (JavaFX, LWJGL, JNA) and the installer. See "Code signing" in the README; `./gradlew :app:fetchSigntool` downloads `signtool.exe`.

---

# BlockDesigner 0.4.3

Every keyboard key can now be changed. Settings › Keybinds covers the viewport too: flying, camera views, nudging, the hotbar, brush modes and more.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.3.exe** | You want a normal install. Installing over an earlier version upgrades it in place. |
| **BlockDesigner-0.4.3-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.2 updates to this by itself. On 0.4.0 or 0.4.1, install it by hand once: their updater can't install updates (see [0.4.2](https://github.com/doolecg/BlockDesigner/releases/tag/0.4.2)).

## Changed
- **Every menu looks the same:** right-click menus, dropdowns (Export, plugins, loot pickers, combo boxes), the side panels (viewport settings, shortcuts, select or replace by type) and the brush and symmetry popups now share one look. They have the same background, border, rounded corners, shadow, padding, title style and item highlight, in every theme, light and dark. The brush and symmetry popups had lost their border and shadow, and those are back. The side panels no longer have an arrow, and their titles sit on the left like the others.
- **Menus show your keys:** the brush popup, the symmetry popup and the viewport settings list the keys set in Settings › Keybinds, not the original ones.
- **F is Focus again:** it frames the selected blocks, or else the active layer. It had been Shift+F since 0.4.1.
- **The Eraser moves to Y.**

## New
- **More keys in Settings › Keybinds**, in these groups (the defaults are the keys from before):
  - **Flying:** forward, back, left, right, up, down, sprint, and flight on / off.
  - **Camera & views:** the numpad views (front, back, left, right, top, bottom, opposite), perspective / orthographic, orbiting in 15° steps, and framing everything or the active layer.
  - **Moving layers & placing:** the arrow-key nudges, bigger steps (Tab), turning and placing an import, and the slice view's level up / down and single level.
  - **Building:** the shape wheel key, symmetry and its centre, Delete, and Esc.
  - **Hotbar:** each of the nine slots.
  - **Brush:** each of the ten brush modes.
- **Held keys:** actions you hold (flying, bigger steps, the shape wheel) take a single key, and Shift, Ctrl or Alt on its own works for them. You could fly down with Ctrl and sprint with Shift, for example.
- **Recording a key:** click it again, or click anywhere else, to cancel. Esc can now be bound like any other key.
- **Clash warnings:** the flying keys don't count as clashing with other actions, because they only act while you fly. W is both the Move tool and fly forward.
- **The key hints and flying tips** show your own keys.

---

# BlockDesigner 0.4.2

A fix for updating. In 0.4.0 and 0.4.1, **Install and restart** could download the update, close BlockDesigner, and then never install it or open again. That happened when the install folder's path had a space in it, as it does for any Windows user name with a space. The setup then waited on an error box that stayed hidden.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.2.exe** | You want a normal install. Installing over 0.4.0 or 0.4.1 upgrades it in place. |
| **BlockDesigner-0.4.2-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

**If you are on 0.4.0 or 0.4.1, install this one by hand:** download the `.exe` above and run it. BlockDesigner updates itself from 0.4.2 on. Everything new in 0.4.1 is listed in the [0.4.1 release](https://github.com/doolecg/BlockDesigner/releases/tag/0.4.1).

## Fixed
- **Updating works** when the install folder's path has spaces in it. The setup gets the folder in a form it accepts.
- **The setup's progress shows** instead of running hidden. If it ever stops responding, BlockDesigner gives up on it after 15 minutes and opens again anyway, rather than staying closed.
- **Trying again after a failed update works.** Each attempt downloads into its own folder, so a file left open by an earlier attempt can't block it.
- **Update errors are readable sentences** instead of a bare file path.

---

# BlockDesigner 0.4.1

A polish release. The block palette now looks and sorts like Minecraft's creative menu, shapes grow out of whichever face you start them on, and Select by type can replace blocks as well.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.1.exe** | You want a normal install. Installing over 0.4.0 upgrades it in place. |
| **BlockDesigner-0.4.1-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. |

0.4.0 offers this update by itself when it opens.

## New
- **Replace by type:** the Select by type panel (Alt+T) is now "Select or replace by type".
  - Tick the blocks to find, then choose **Select** or **Replace**.
  - To replace, pick the new block by searching, use the held block, or pick **Air** to remove them. Facing, half and other properties the new block shares are kept, so oak stairs become stone brick stairs facing the same way. It is one undo step.
  - Where to look and the property filters are under **More options**.
  - Shift+right-click a block in Select mode for **Replace all … with the held block**.
- **Move and rotate selected blocks:** with blocks selected, the Move (W) and Rotate (E) tools move or turn just those blocks inside their layer, instead of the whole layer. They keep their facing and data (chest contents, sign text), replace what they land on, and the whole drag is one undo step. Right-click or Esc during the drag cancels it.
- **Move the selection to another layer:** **Ctrl+Shift+J** moves the selected blocks into a new layer (Ctrl+J still copies). Shift+right-click › **Move to active layer** moves them into the active layer. Either way they stay where they are in the world.
- **Keybinds editor:** Settings › **Keybinds** lists every keyboard shortcut by group, with a search. Click a key and press a new one; each action can have a second key. Keys used by two actions show in red, and each action (or everything) can go back to its default. The key hints in the corner show your keys.
- **New default keys** (all changeable in Settings › Keybinds):

  | Action | Was | Now |
  |---|---|---|
  | Build mode | B | **G** |
  | Move tool | G | **W** |
  | Paint brush | U | **B** |
  | Eraser | X | **F** |
  | Shuffle mode | Z | **Shift+Z** |
  | Replace mode | R | **Shift+X** |
  | Clear the hotbar | Alt+C | **Shift+C** |
  | Frame the selection or active layer | F | **Shift+F** (Home still frames everything) |
- **Settings opens on General** instead of Appearance.
- **Key hints:** the bottom-right corner shows the keys for what you are doing now, as keycaps and a mouse with the button lit. They change with the tool, flying, dragging a shape and placing an import. Turn them off or on with **Shift+F1**, or under Overlays in the viewport settings.
- **The version** is shown on the welcome screen.
- **The layout is remembered:** the window's size, position and maximised state, the widths of the side panels, and the split between layers and palette come back as you left them.

## Changed
- **Shapes grow from the face you start on.** Start a sphere, box, cylinder, dome or other shape on the underside of a block and it hangs down. Start it on a side and it grows sideways out of the wall, with the scroll wheel setting its depth. Floor, Wall and Line work as before.
- **Block icons look like Minecraft's:** each block is drawn from its item model with the inventory's angle, size and lighting. Stairs face the right way, and fences, walls and buttons use their inventory models.
- **The palette is laid out like the creative menu:** icon tabs for Building, Colored, Natural, Functional and Redstone blocks (plus All and Recently used), with blocks in the creative menu's order. Modded blocks are sorted into the tab that fits their name.
- **Build mode's icon** is a block with a hammer.
- **The view cube is smaller**, so it covers less of the viewport.

---

# BlockDesigner 0.4.0

This release adds mobs: place pigs, villagers, iron golems, armour stands, paintings and more as stand-ins in your builds, drawn with Minecraft's own models and saved and exported with the schematic, as Litematica does. It also clears most of 0.3.0's known issues. From this version on, BlockDesigner updates itself.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.4.0.exe** | You want a normal install: Start menu entry, optional desktop shortcut, and `.bdproj` projects that open with a double-click. Installs for your user only, so no admin is needed. Installing over 0.3.0 upgrades it in place. |
| **BlockDesigner-0.4.0-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. Settings stay in a `data` folder next to the exe. To keep your 0.3.0 settings, copy its `data` folder across. |

The requirements haven't changed: 64-bit Windows 10 or 11, OpenGL 3.3, and Minecraft: Java Edition installed.

0.3.0 can't update itself, so install 0.4.0 by hand this once. After that, BlockDesigner offers each new version when it opens.

## New

### Mobs and other entities
- **A Mobs tab in the palette:** farm animals, villagers and golems, wild animals, water mobs, monsters, and decoration and vehicles. Each tile shows the mob's face from the game's textures.
  - Click one to hold it, then **right-click in Build mode** to place it on the aimed block, facing you. It stands at the exact height, so slabs and carpets work.
  - Before placing, set what makes it yours under the grid: wool colour, a villager's job and home biome, baby, a painting's picture, and an armour stand's arms, size and base plate.
  - Type any id into the search (`alexsmobs:grizzly_bear`) to place a modded mob. It is drawn as a box the size of the mob.
- **Minecraft's models** for pigs, cows, mooshrooms, sheep (with dyed wool), chickens, wolves, villagers (with biome clothes and job outfits), wandering traders, iron golems, zombies, husks, drowned, skeletons, strays, wither skeletons, creepers and armour stands. Paintings show their picture and item frames hang on their wall. Other mobs are boxes the size of their hitbox in their spawn egg's colour.
- **Editing:**
  - **Left-click** removes a mob in Build mode.
  - **Middle-click** holds one just like it.
  - **Alt+scroll** over a mob turns it 22.5°. Over a painting it steps through the pictures that fit.
  - In **Select mode**, click (Shift adds, Ctrl toggles) or drag a box to select mobs. **Arrow keys** move them and **Delete** removes them. Everything undoes.
- **Hovering** a mob shows its name, job, colour and facing in the info box.
- **Everywhere else:** mobs move and turn with their layer, show through ghosts and the slice view, are kept in projects, and are exported to Litematica, WorldEdit and structure files (and worldgen data packs). Paintings and frames keep facing their wall when a layer is turned. Mobs get `PersistenceRequired` so they don't despawn once pasted in.
- **WorldEdit:** `/copy -e`, `/cut -e`, `/move -e` and `/stack -e` take mobs and other entities along.

### Blocks
- **Fix block shapes** rejoins fences, walls, panes, iron bars, redstone dust, rails and stair corners from their neighbours. Use it on schematics saved by tools that leave fences unjoined or dust as dots. It is in the layer list's menu, in Select mode's Shift+right-click menu (for the selected blocks) and as `/fixshapes` (for the region). Rail junctions only change when that joins them to more rails.
- **Decorated pots** have their real model, with the patterns of the sherds they were made with.
- **Modded signs** show their board texture when the mod has one.
- **Chests and shulker boxes open:** click one in View mode (V) to open or shut it with the game's lid animation. Double chests open both halves.

### Tools
- **New brush and eraser icons** in the tool dock: a paint brush and an eraser.
- **New project button** at the top left, next to Open and Save (same as Ctrl+N).

### Updates
- **BlockDesigner updates itself.** When it opens, it checks GitHub for a newer release and shows that release's notes. Choose **Install and restart** and it downloads the update, checks the file against the release's checksum, closes, installs and opens again. The setup build runs the new installer in the same folder. The portable build replaces its files and keeps your `data` folder. If your project has unsaved changes, it asks to save it first.
- Choose **Skip this version** to stop it asking about that release, or turn the check off in **Settings > General > Updates**. You can also click **Check for updates now** there.

## Fixes
- **Across layers:** placing, breaking, Replace, the brushes, the eraser and WorldEdit now see every visible layer. Fences, walls, panes, redstone, rails and stair corners join blocks in other layers, and the sculpt brushes shape terrain spread over several layers. A cell taken in any layer counts as taken. Locked layers are read (things join them) but never changed.
- **Deleting or replacing a selection** updates the fences, walls, redstone and stairs next to it, in every layer.
- **Eraser:** choosing a mode in the brush popup while erasing now switches to the Brush in that mode. The popup shows Erase lit while the Eraser is in hand.
- **Saved with the project:** the block selection, selected mobs and the WorldEdit region.
- **Banner patterns** (and any other block data, such as chest contents and sign text) survive `/copy` and `/paste`, `/rotate`, `/flip`, `/move` and `/stack`.
- **Merge down** keeps the upper layer's mobs.

## Known issues
- **Security warning:** the installer and exe still aren't code-signed, so Windows SmartScreen may warn on first run. Click **More info → Run anyway**.
- **Mob models:** horses, cats, llamas, foxes and the other mobs without a model are drawn as coloured boxes. Babies are the grown-up model at half size. Villager job levels and armour or held items aren't shown.
- **Block shapes:** imported schematics keep their fence, wall, redstone and rail shapes as saved until you place or break next to them, or use **Fix block shapes**.
- **Loot tables** only apply to worldgen data packs. Schematic exports keep containers exactly as built.

## Coming soon
- **Resource Tracker:** the materials a build needs, what you've gathered and what's left.

---

# BlockDesigner 0.3.0

This release adds sculpting brushes (smooth, erode, raise, flatten and more), the Eraser, build shapes and symmetry. WorldEdit now works like Minecraft's chat: press T, type `/set stone`, and it replaces what's already there across all your layers.

Worldgen data packs gain village layouts, loot tables for chests and other containers, and presets you can save. There is also a new Export window, six themes, plugins, and real models for chests, beds, signs, heads and banners. The unfinished AI Assistant has been removed.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.3.0.exe** | You want a normal install: Start menu entry, optional desktop shortcut, and `.bdproj` projects that open with a double-click. Installs for your user only, so no admin is needed. |
| **BlockDesigner-0.3.0-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. Settings stay in a `data` folder next to the exe. |

The requirements haven't changed: 64-bit Windows 10 or 11, OpenGL 3.3, and Minecraft: Java Edition installed.

## Changed controls
- **T opens the command line**, like Minecraft's chat. **Select by type** moved from T to **Alt+T**; the filter button still opens it.
- **Commands take one slash:** `/set stone` (`//set` still works).

## New

### Export window
- **A new Export window (Ctrl+E):**
  - Pick a card on the left: Litematica, WorldEdit, Create / Structure, Worldgen data pack, or anything a plugin adds.
  - Before you export, it shows the layer count, blocks, size and number of block types.
  - **Save to** lists each Minecraft instance's `schematics` folder (or `config/worldedit/schematics` for WorldEdit), so an export lands where the mod looks for it.
  - It remembers the last card, options and folder, and warns before replacing a file.
  - Exports are written in the background.
- **Format icons** for Create (brass cog), Litematica (hologram cube), WorldEdit (wand axe), worldgen (grass block) and plugins (puzzle piece). They appear in the Export menu and window, on imported layers in the layer list (hover for the format) and in the start screen's recent files.

### Build shapes (Effortless Building style)
- **Hold Alt in Build mode** to open the shape wheel, point at a shape and let go:
  - Line, Wall, Floor
  - Box, Room (hollow box), Walls
  - Circle, Ring, Cylinder
  - Sphere, Dome, Pyramid
  - **None**, for normal single-block placing
- **Right-drag** to draw the shape out from the aimed block with an outline preview. Release to place it as one undo step.
  - Lines follow the axis closest to the mouse, walls stand across your view, and everything else lies on the start block's level.
  - **Mouse wheel** while dragging sets the height of boxes, rooms, walls and cylinders.
  - **Esc** or a left-click cancels.
- Shapes fill only empty cells, or everything in Replace mode (R). They use the held block, or a random hotbar block per cell in Shuffle mode (Z). Fences and walls join up.
- This works with the normal camera and while flying. Alt+key shortcuts, Alt+wheel layer moves and Alt+middle-drag still work: the wheel only opens when Alt is held on its own.

### Symmetry (Effortless Building style mirror)
- **M** in Build mode opens the Symmetry panel (or use the new mirror button on the right of the 3D view):
  - **Mirror** across X (red plane), Y (green) and/or Z (blue).
  - **Radial:** 2–16 copies around a vertical axis.
  - **Centre** on a block's middle (odd-width builds) or on the edge between blocks (even-width). **Shift+M** puts the centre on the aimed block.
- **Applies to** shapes, single-block placing and breaking. Each copy's stairs, doors, slabs and logs are turned to match; you can switch that off.
- **Previews:** the planes and spokes are drawn in the view. Faint outlines show where the aimed block's copies go, and a shape being dragged shows every mirrored copy.

### Themes and Settings
- **Settings window** (the gear in the top bar, or **Ctrl+,**):
  - **Appearance:** choose a theme and a mode (Dark, Light or Match Windows).
  - **General:** your author name, the start screen, Minecraft assets, plugins, and the settings folder.
- **Six themes:**
  - **Claude:** professional warmth, with ivory paper, warm slate and terracotta.
  - **Blue:** the classic look.
  - **Green**, **Red** and **Orange**.
  - **Zen:** calm stone and sage, lower contrast for long sessions.
- **Each theme covers everything:** panels, menus, tooltips, dialogs, the accent on buttons and layer outlines, and the 3D view's sky and grid. Changes apply instantly.
- The moon button still flips between dark and light. **Minecraft assets** moved from the gear to the version badge (click it) and to Settings > General.

### Village-style worldgen
- The data pack window has a **Village (jigsaw)** layout.
  - Mark each layer as the **centre**, a **street** or a **building**, give it a weight, and optionally pick a building's entrance side.
  - BlockDesigner adds the jigsaw blocks. A building's entrance goes under its door (or on the side you picked), so houses stand with their door on the street. Streets connect at their ends and take houses along their sides.
  - It can also generate streets and crossroads in a block you choose (dirt path, gravel, cobblestone…). With no centre layer, it adds a small square.
  - Streets follow the terrain; the centre and buildings keep their shape.
  - Layers that already contain jigsaw blocks are used as they are.

### Worldgen presets
- **Built-in presets** at the top of the data pack window fill in placement, blending and weathering for a common kind of structure:
  - House on the surface (like villages)
  - Big build on rough ground
  - **On flat ground, never on hills:** plains and meadows, with hills and mountains excluded, a solid base and a 2-block foundation, so it never hangs off a slope
  - Half-buried ruin (like trail ruins)
  - Underground vault (like trial chambers)
  - Sunken wreck on the sea floor
  - Floating sky island
- **Save your own:** **Save…** keeps every setting except the pack's name and version, including biomes, exclusions and loot. Saved presets are listed under the built-in ones as **Saved · name** and are available in every project. **Delete** removes one.

### Loot in generated structures
- **Give each kind of container a loot table:** chests (copper chests too), trapped chests, barrels, shulker boxes, dispensers, droppers, hoppers and decorated pots (1.20.3+). Suspicious sand and gravel roll their loot when brushed.
- The window lists the containers in your layers with a count, and **All containers** sets them all at once.
- **Minecraft's own tables**, grouped:
  - Dungeons and ruins: dungeon, mineshaft, stronghold, ancient city, ruined portal, woodland mansion, pillager outpost, igloo, bonus chest
  - Temples: desert pyramid, jungle temple (and its arrow dispenser)
  - Villages: the five house styles and every job site
  - Ocean: shipwrecks, buried treasure, ocean ruins
  - Nether and End: fortress, bastion, end city
  - Trial chambers (1.21+): supplies, corridors, vault rewards
  - Archaeology: desert pyramid and well, ocean ruins, trail ruins
- **From the game and mods:** chest tables found in the loaded game and your mods are listed too.
- **Custom tables:** name it, set how many rolls each fill makes (e.g. 2–5), then add items with a weight, a count range and an optional random enchantment. Item ids autocomplete from the game and mods, and each item's chance is shown as you change weights. Custom tables are saved with your settings, can be edited or deleted later, and are written into the pack.
- **Keep as built** (the default) leaves containers as you filled them, including loot tables in imported structures. **Empty** clears them. **Loot table id…** takes any table from a mod or another data pack.
- Loot is rolled the first time a container is opened, so every copy of the structure gets different loot.

### More worldgen control
- **Blending with the terrain** is now a set of cards, each with a cross-section sketch (the ground before and after), a plain explanation and the vanilla structures that use it:
  - **Soft blend** (`beard_thin`)
  - **Solid base** (`beard_box`)
  - **Buried** (`bury`)
  - **Encased** (`encapsulate`)
  - **No blending** (`none`)
- **Foundations:** extend the bottom layer 1–32 blocks down so the build stands on footings instead of floating over dips. The footings can match each bottom block or use a block you pick. The build is lowered by the same amount, so its floor stays put.
- **Height:** on the surface, on the sea floor, at a fixed Y (sky islands), or at a random Y between two levels (underground ruins).
- **Generation step:** choose when it generates, with a note on what else runs at that step.
- **How often:**
  - A spacing summary in blocks.
  - **Spread:** random or even.
  - **Chance:** 5–100%, to make it rarer without changing the grid.
  - **Keep away from** villages, outposts, monuments and other vanilla structures, by 1–16 chunks.
- **Weathering:**
  - **Integrity:** for ruins, blocks go missing at random.
  - **Age:** mossy and cracked stone.
- **Biome blacklist ("Never in"):**
  - Tick groups to exclude: oceans, rivers, beaches and shores, swamps, hills and mountains, mountain peaks, snowy and icy, mushroom fields, deep dark.
  - Type any other biomes or #tags to exclude.
  - Biome tags are expanded into single biomes from your loaded game and mods, then the exclusions are taken away. Modded biomes stay optional, so the pack still loads without that mod.
  - The window shows how many biomes are left, and which ones.
- **Stay away from water:** one click excludes oceans, rivers, beaches and swamps. It also turns on **Keep blocks dry**, so blocks placed into water aren't waterlogged (1.21+).
- **Bigger villages:** village size goes up to 20 on 1.20.2 and newer (7 before).

### Plugins
- **Plugins** made by other people can add schematic formats (to Import and Export), export cards, menu entries and `/commands`.
- They are managed from the new puzzle button in the top bar: turn them on and off, install, reload, uninstall, and see errors and logs.
- Guide: `PLUGINS.md`. Example: `examples/hello-plugin`.

### Faster
- **Layer bounds are tracked as you edit** instead of being rescanned. Each rescan used to take about 8 ms on a 3-million-block build, and it happened several times per frame per layer.
- **Meshing:** sections are copied in bulk (about 3× faster) and each block's model is looked up once per section, not per face and vertex. Fully buried blocks are skipped.
- **Frustum culling:** sections outside the view aren't drawn.
- **Duplicated layers share one GPU mesh (instancing):** copies aren't re-meshed or re-uploaded until one of them is edited.
- **Less work while editing:**
  - Exports flatten layers without creating objects per block.
  - Big commands skip layers they don't touch.
  - The layer list and status bar refresh once per frame instead of on every change.

### Brushes
- **Brush (U)**, with ten modes picked with **Alt+1 … Alt+0**. Alt isn't a flight key, so they work while flying too.

  | Key | Mode | What it does |
  |---|---|---|
  | Alt+1 | Draw | Adds blocks with the held block (a random hotbar pick per block in shuffle mode) |
  | Alt+2 | Erase | Removes blocks |
  | Alt+3 | Smooth | Rounds things off. On top of the ground it smooths the heightmap (hills round off, pits fill in, grass stays on top); on a side face it rounds shapes in 3D |
  | Alt+4 | Erode | Wears away exposed corners and edges |
  | Alt+5 | Fill | Plugs pits and crevices |
  | Alt+6 | Pinch | Pulls material in to sharpen ridges (Ctrl: pushes out) |
  | Alt+7 | Raise | Builds a hill with a soft falloff |
  | Alt+8 | Lower | Sinks the ground the same way |
  | Alt+9 | Flatten | Levels to the height you click and closes holes |
  | Alt+0 | Slope | A ramp rising from where the stroke started (Ctrl: cuts down) |

- **Right-drag smooths**, in any mode and while flying.
- **Shift+right-click** (or the brush bar's mode button) opens the brush settings: modes, size, strength and shape.
- **Size, strength and shape:** `-`/`=` change the size (1–16) and `,`/`.` the strength (1–5). The shape can be a sphere or a cube.
- **While painting** (not flying): Shift smooths and Ctrl inverts the mode (Draw↔Erase, Raise↔Lower, Erode↔Fill).
- **Strokes are smooth:**
  - The brush aims at the surface as it was when the stroke began, so it glides along it instead of catching on its own new blocks.
  - Fast drags are filled in along the path.
  - Sculpt modes keep working while you hold still, like an airbrush.
  - The pace is set by **Brush speed** in viewport settings (default 8 dabs a second).
- **Brushes never build into the camera:** they stop short of it instead of growing past it.
- **The outline under the cursor** is green for adding, red for removing and blue for reshaping. Terrain brushes show a ring on the ground.
- Each stroke is one undo step, and fences, walls, redstone, rails and stairs reconnect as you paint.

### Eraser
- **Eraser (X):** drag to remove blocks from every visible, unlocked layer, sized and shaped like the brush.

### WorldEdit
- **The command line works like Minecraft's chat:**
  - T (or `/`) opens it bottom-left with `/` already typed.
  - Recent commands and their results show above the input.
  - Tab completes commands and block names, and matching blocks are listed while you type one.
- **Commands work on what you see:** every visible, unlocked layer, merged. `/set` replaces the blocks already there, whichever layer they're in; empty cells go into the active layer.
- **Connections:** fences, walls, redstone, rails and stairs now join up with what commands build.
- **A drag selection sets pos1 and pos2** to the corners of the box around what you selected, so `/set`, `/copy`, `/stack` and the rest work on it straight away. Shift- and Ctrl-clicking to add or remove blocks updates the corners too.
- **New commands:**
  - `/smooth [passes]` smooths the terrain surface in the region.
  - `/naturalize` puts grass on top, then three dirt, then stone.
  - `/hollow [thickness] [pattern]` hollows out shapes, keeping a shell.
  - `/center <pattern>` marks the middle of the region.

### Chests, beds, signs, heads, banners and shulker boxes look real
- **They're drawn with their real models,** built the way Minecraft's own renderers build them, using the game's textures:
  - Chests with the lid seam and latch; double chests join with the latch across the middle. Trapped and ender chests too.
  - Beds with pillow, blanket and legs, in all 16 colours.
  - Standing and wall signs, and hanging signs with their chains (and the bar on walls), for every wood type.
  - Skeleton, wither skeleton, zombie, creeper, player (Steve), piglin (with ears) and dragon heads.
  - Banners (standing and on walls) in all 16 colours, with their patterns from the schematic layered on top. Both the current pattern format and the pre-1.20.5 short codes are read, and pattern textures from mods and resource packs work too.
  - Shulker boxes (undyed and all 16 colours) turned to face the way they're placed.
- **Heads now face you when placed.** Before, they faced away.
- Modded signs and decorated pots still use simple shapes.

### Hitboxes follow the model
- **Blocks smaller than a full block use Minecraft's own hitboxes:**
  - Flowers, grass, ferns, saplings, mushrooms, fungi, crops (which grow with age), stems, sugar cane and cactus.
  - Coral and coral fans, amethyst buds (turned the way they point), carpets, pressure plates, rails, potted plants and torches.
  - Pointed dripstone (by thickness), big dripleaf (its leaf follows the tilt), bamboo and cocoa (three sizes).
  - Signs (standing, wall and hanging), heads and skulls, beds (mattress and legs), chests (single and double) and banners.
- Other non-full blocks (slabs, stairs, fences, walls, lanterns…) use their model's shape. Modded plants drawn as a cross get the grass shape.
- Aiming at the empty part of a block reaches whatever is behind it, as in Minecraft.
- The hover outline is drawn around the real shape too.
- Placement uses the exact point you click, so slabs and stairs pick top or bottom more reliably.

### New shortcuts

| Area | Keys |
|---|---|
| Tools | U brush · X eraser |
| Brush | Alt+1…0 modes · right-drag smooth · Shift+right-click settings · - / = size · , / . strength · Shift+drag smooth · Ctrl+drag invert |
| Commands | T or / opens the command line · Tab completes · ↑ ↓ history |
| Selecting | Alt+T select by type |

## Removed
- **The AI Assistant** is gone: its tab, the chat, and the Claude and OpenAI-compatible (local model) providers. It never left "Coming soon". The side panel now holds only the Resource Tracker tab. API keys saved by earlier versions are ignored, and dropped from the settings file the next time it saves.

## Fixes
- **Worldgen packs made for 1.21 and newer no longer stop the world from loading.** They set `max_distance_from_center` to 128. With a terrain setting like `beard_thin`, Minecraft adds 12 blocks, and the total must stay at or under 128, so the world failed with "Horizontal structure size including terrain adaptation must not exceed 128". Structures that fit the ground now use 116.

## Known issues
- **Security warning:** the installer and exe still aren't code-signed, so Windows SmartScreen may warn on first run. Click **More info → Run anyway**.
- **Existing blocks:** fences, walls, redstone and rails in imported schematics keep the shapes they were saved with until you place or break next to them. Deleting or replacing a selection doesn't update neighbours.
- **Across layers:** connections, stair corners and the sculpt brushes only look at blocks in the same layer.
- **Eraser tool:** picking a mode in the brush popup has no effect there; switch to the Brush (U) first.
- **Block selections and the WorldEdit region** aren't saved in projects yet.
- **Special blocks:** decorated pots and modded signs still show as simple shapes. Chests and shulker boxes don't animate open.
- **Banner patterns** show when a schematic has them, but copying banners with `/copy` and `/paste` drops their patterns.
- **Loot tables** only apply to worldgen data packs. Schematic exports keep containers exactly as built.

## Coming soon
- **Resource Tracker:** the materials a build needs, what you've gathered and what's left.

---

# BlockDesigner 0.2.0

Blocks now go down the way Minecraft places them, with the game's own names. This release also adds WorldEdit-style commands, Blender-style Move and Rotate gizmos, a view cube with an orthographic camera, and sounds and particles.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.2.0.exe** | You want a normal install: Start menu entry, optional desktop shortcut, and `.bdproj` projects that open with a double-click. Installs for your user only, so no admin is needed. |
| **BlockDesigner-0.2.0-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. Settings stay in a `data` folder next to the exe. |

The requirements are the same as 0.1.0: 64-bit Windows 10 or 11, OpenGL 3.3, and Minecraft: Java Edition installed.

## Changed controls
- **Shuffle mode** moved from R to **Z**. **R** now toggles Replace mode, and it still rotates an import ghost while you place one.
- **Select mode:** left-click sets **pos1** and right-click sets **pos2** (see WorldEdit below). Shift-click and Ctrl-click add or remove single blocks, and dragging still draws a marquee.
- **The right-click menu** in Select mode moved to **Shift+right-click** (or the Menu key).
- **F** frames the selected blocks when there are any, and otherwise the active layer.

## New

### Placing like Minecraft
- **Blocks face the way Minecraft places them.**
  - Stairs and slabs go top or bottom depending on where you click, and stairs join into corners.
  - Logs follow the face you click.
  - Torches, signs, banners and heads switch to their wall form on sides.
  - Doors and beds place both halves.
  - Buttons, levers, trapdoors, ladders, pistons, observers, hoppers, furnaces, chests and repeaters all face the right way.
  - Modded blocks get a matching rule from their properties.
- **Connections:**
  - Fences, walls, glass panes and iron bars join only their own kind, fence gates side-on, and solid blocks. They never reach into air.
  - Walls go tall under blocks, and straight runs of wall have no post.
- **Redstone:**
  - A lone dust is a cross. Dust joins other dust (up and down steps too), torches, levers, buttons, repeaters (front and back only) and observers (back only).
  - Rails join each other, climb slopes, and plain rails curve at corners.
- **Neighbours update** when you place or break next to them.
- **Replace mode (R):** right-click swaps the block you aim at for the held block and keeps its facing and shape. It swaps both halves of doors and beds. Hold and drag to paint.
- **Minecraft's block names:** names come from the game's language files (and each mod's own), e.g. "Block of Redstone" and "Redstone Dust".

### WorldEdit
- In Select mode, left-click sets pos1 and right-click sets pos2. The box between them is drawn and its blocks become the selection. Esc clears it.
- **Press `/`** (or the terminal button) for a command bar. It shows each command's usage as you type; Tab completes and ↑/↓ recalls earlier commands.
- **Commands:** `//set`, `//replace`, `//walls`, `//faces`, `//overlay`, `//copy`, `//cut`, `//paste`, `//rotate`, `//flip`, `//move`, `//stack`, `//expand`, `//contract`, `//shift`, `//outset`, `//inset`, `//line`, `//sphere`, `//cyl`, `//pyramid` (with hollow versions), `//count`, `//distr`, `//size`, `//undo` and `//redo`.
- **Mixes and directions:** blocks can be mixed (`//set 70%stone,30%andesite`) or taken from your hand (`//set hand`). Directions accept `up`, `north`, `left` or `me`.
- **Where commands work:** they edit the active layer, and each command is one undo step. `//paste` and the shapes work from pos1.

### Move and Rotate gizmos
- **Move (G):** Blender-style handles. Drag an arrow to move along an axis, a square to move in a plane, or the centre to move freely. Moves snap to whole blocks.
- **Rotate (E):** drag a ring to turn the selected layers in 90° steps around their shared centre.
- Each drag is one undo step, and Esc or right-click cancels it.

### Camera and view
- **View cube:** click a face for that view, click it again for the opposite side, or drag the cube to orbit.
- **Orthographic camera:** P for perspective and O for orthographic, or the button under the cube. Axis views switch to ortho automatically, as in Blender.
- **Numpad views:**
  - 1, 3 and 7 for front, right and top; hold Ctrl for back, left and bottom.
  - 9 for the opposite side, and 5 to switch perspective and ortho.
  - 2, 4, 6 and 8 orbit, and `.` frames the active layer.
- **Alt+middle-drag** swings to the next ortho view in the direction you drag.
- **Flight momentum:** flying glides to a stop instead of halting. You can turn it off in viewport settings.

### Selecting
- **Select by type (T, or the filter button):** pick block types or exact states to select. You can filter by layer, properties (`half=top`, `facing=north|south`) and slice level, and replace, add to or remove from the selection.

### Feel
- **Sounds:** a place thud and a break thud, with a slightly different pitch on every other click. Volume and on/off are in viewport settings.
- **Break particles:** chips in the block's colours pop out and fall.
- **Hotbar:** Shuffle (Z) and Replace (R) toggles are always shown at the end of the bar, lit when on. Delete in Build mode empties the held slot.

### Block palette
- **Tabs:** Game Blocks, Schematic Blocks (what your layers use, most used first, with counts) and Modded Blocks.

### New shortcuts

| Area | Keys |
|---|---|
| File | Ctrl+N new project · Ctrl+Shift+E worldgen data pack · Ctrl+F block search |
| Layers | `[` / `]` layer below / above · Ctrl+Shift+N new layer · Ctrl+D duplicate · Ctrl+M merge · F2 rename · Shift+Delete delete |
| Layer toggles | H hide/show · Alt+H show all · Shift+H ghost · L lock |
| Selection | Ctrl+A select all in active layer · Alt+A deselect · Ctrl+J copy to new layer · Ctrl+R fill with held block |
| View | N viewport settings · Alt+G grid · Home frame everything · F1 shortcuts · F11 full screen |

Everything is listed in the shortcuts panel (Alt+K or F1).

### Interface
- **Side popovers:** viewport settings, shortcuts and Select by type all open beside their own icon.
- **The shortcuts list** scrolls with the mouse wheel while it's open.
- **App icon:** it now appears on the start screen, in the top bar, on recent `.bdproj` files and in every dialog's title bar.

## Fixes
- **Fences and walls** no longer show arms reaching into air, and new fences start unconnected.
- **Sounds** start straight away, including the first click after the app opens.

## Known issues
- **Security warning:** the installer and exe still aren't code-signed, so Windows SmartScreen may warn on first run. Click **More info → Run anyway**.
- **Existing blocks:** fences, walls, redstone and rails in imported schematics keep the shapes they were saved with until you place or break next to them. Deleting or replacing a selection, and WorldEdit commands, don't update neighbours.
- **Across layers:** connections and stair corners only look at blocks in the same layer.
- **Block selections and the WorldEdit region** aren't saved in projects yet.
- **Special blocks:** chests, beds and other blocks Minecraft draws with a special renderer still show as simple boxes.

---

# BlockDesigner 0.1.0

The first release of BlockDesigner, a Minecraft structure designer for Windows. Build block by block in a live 3D view with Minecraft-style controls, then export the result as a schematic or a worldgen data pack.

## Downloads

| File | Use it if… |
|---|---|
| **BlockDesigner-0.1.0.exe** | You want a normal install: Start menu entry, optional desktop shortcut, and `.bdproj` projects that open with a double-click. Installs for your user only, so no admin is needed. |
| **BlockDesigner-0.1.0-portable.zip** | You don't want to install anything. Unzip it anywhere and run `BlockDesigner.exe`. Settings stay in a `data` folder next to the exe. |

Both include their own Java runtime, so you don't need Java installed.

**Requirements:** 64-bit Windows 10 or 11, a graphics card with OpenGL 3.3, and Minecraft: Java Edition installed. BlockDesigner loads block models and textures from your own game files and doesn't include any Minecraft assets.

## Highlights

### Build like in Minecraft
- **Three modes:** View (V) for looking around, Select (Q) for choosing blocks, and Build (B) for placing and breaking.
  - **Build:** left-click breaks, right-click places, and middle-click picks the block. Hold a button to repeat.
  - **Select:** never edits. Click a block or drag a marquee to select, then right-click for delete, replace, copy to a new layer, select all, hide or lock.
- **Creative flight (C):** WASD to fly the way you're looking, Space and Shift to go up and down, and Ctrl to sprint. Your reach is 5 blocks, as in creative mode.
- **Hotbar:** nine slots that fill up with the blocks you middle-click or drag in from the block palette. Switch with 1–9 or the mouse wheel, and clear it with Alt+C.
- **Shuffle mode (R):** every block you place is picked at random from your hotbar, which is handy for textured walls, floors and paths.
- **Block palette:** icons look like Minecraft's inventory, so slabs, stairs, fences and walls show in 3D and flowers, torches and doors show as flat items.

### Layers
- Every schematic you import or create is a layer, with show/hide, lock, ghost, rename, reorder, duplicate and merge.
- **Moving layers:** Ctrl+scroll moves along the face under your mouse, Ctrl+Shift+scroll moves up and down, and Shift+scroll moves back and forward. Hold Tab for bigger steps.
- **Turning layers:** Alt+scroll over the top spins a layer. Over a side, it flips the layer a quarter turn onto that side.
- **Slice view:** PgUp and PgDn step through Y levels, and Insert switches between one level and everything up to that level.
- **Imports** follow your cursor as a ghost until you click to place them. R rotates the ghost.

### Viewport
- Middle-drag orbits around the point under the mouse, Shift+middle-drag pans, and the wheel zooms.
- Viewport settings (the sliders button) cover field of view, clip distance, fog, overlays, orbit and zoom speed, fly speed and look sensitivity.
- Alt+K, or the ⌘ button, shows every keyboard and mouse shortcut.

### Formats
- Import and export Create or structure-block `.nbt`, Litematica `.litematic` (including multi-region) and WorldEdit `.schem` (Sponge v2/v3).
- Stairs, doors, rails, fences and signs keep facing the right way when you rotate or mirror.
- **Worldgen export:** writes a data pack with a jigsaw structure, template pool, structure set and biome tag. Save it as a zip or install it straight into a world, then test in-game with `/place structure`.

### Modded blocks
Pick a Prism, CurseForge, Modrinth or official-launcher instance, and blocks from its mods and resource packs render too. For example, Create builds show their real models.

### Other
- **Start screen:** new project, open, recent projects, the Minecraft jar in use, and links to schematic sites. You can turn it off, and clicking the logo opens it again.
- **Side panel tabs** can be closed, then reopened from the bar on the right edge.
- **Undo and redo** cover every action.

## Coming soon
- **AI Assistant:** describe a build or attach a reference image, and an assistant builds it block by block. You'll be able to refine it by chatting. It will work with Claude or any OpenAI-compatible server (OpenAI, Ollama, LM Studio).
- **Resource Tracker:** the materials a build needs, what you've gathered and what's left.

## Known issues
- **Security warning:** the installer and exe aren't code-signed yet, so Windows SmartScreen may warn on first run. Click **More info → Run anyway**.
- **Block selections** aren't saved in projects yet.
- **Special blocks:** chests, beds and other blocks Minecraft draws with a special renderer show as simple boxes.
