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
