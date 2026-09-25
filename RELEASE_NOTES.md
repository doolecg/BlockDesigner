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
