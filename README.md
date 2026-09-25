# BlockDesigner

A Minecraft structure designer. Build block by block in a live 3D view with Minecraft-style controls, arrange schematics as layers, then export the result as a schematic or as a worldgen data pack. An AI assistant that builds from a description or a reference image is coming in a future release.

## Run

```
./gradlew :app:run
```

Requires JDK 25+. `gradle.properties` points Gradle at a local Temurin 26. On first launch, pick the Minecraft version whose textures and models to use. You can also pick a modded instance (Prism, CurseForge, Modrinth or the official launcher) so modded blocks, such as Create's, render too. Nothing from Minecraft is copied or shipped.

## Windows builds

- `./gradlew :app:portable` builds `dist/BlockDesigner/BlockDesigner.exe` and `dist/BlockDesigner-0.2.0-portable.zip`. Nothing needs installing, the Java runtime is bundled, and settings are kept in a `data` folder next to the exe.
- `./gradlew :app:installer` builds `dist/BlockDesigner-0.2.0.exe`, a per-user setup with Start menu and desktop shortcuts. It also makes `.bdproj` saves show the BlockDesigner icon and open in the app. It needs the WiX Toolset: unzip the [WiX 3.14 binaries](https://github.com/wixtoolset/wix3/releases) into `tools/wix3`, or have WiX on PATH.
- The icon is drawn by `packaging/make_icon.py` (needs Pillow). Rerun it after changing the design.

## Features

- **Formats**
  - Import and export Create / structure-block `.nbt`, Litematica `.litematic` (multi-region) and WorldEdit `.schem` (Sponge v2/v3).
  - Directional blocks (stairs, doors, rails, fences, signs) stay correct through rotation and mirroring.
- **Layers**
  - Every schematic, imported or new, is a layer, with visibility, lock and ghost toggles, rename, reorder, duplicate and merge.
  - Nudge the selected layers relative to the camera:
    - Ctrl+wheel over a layer: along the axis of the hovered face (wheel up pulls it out towards you); elsewhere left/right
    - Ctrl+Shift+wheel: up/down
    - Shift+wheel: back/forward
    - Arrow keys move left/right and back/forward
    - Hold Tab for 8-block steps
  - Alt+wheel over a layer turns it. Over the top it spins around the vertical axis. Over a side it flips a quarter turn around that side's horizontal edge: wheel up rolls the side you're looking at up to face the sky. Flipping rewrites the blocks; stairs, slabs and other blocks with no sideways form keep their old orientation.
  - Alt+K or F1 (or the ⌘ button) shows every keyboard and mouse shortcut; they are all listed under [Controls and keybinds](#controls-and-keybinds) below.
  - Hotbar: nine Minecraft-style slots, shown in Build mode, that record the blocks you middle-click and the ones you drag in from the block palette (drop on a slot to put it there). Press 1-9, click a slot, or use the wheel while flying to hold one. Alt+C clears it, and Delete in Build mode empties the held slot. Z toggles shuffle mode, where every placed block is a random pick from the filled slots (handy for textured walls and paths). R toggles Replace mode, where right-click swaps the aimed block for the held one. Fly speed is in the viewport settings.
  - Slice view: PgUp/PgDn step through Y levels. By default each level shows with everything below it; Insert switches to showing a single level.
  - Imports appear as a ghost that follows the cursor. Click to place, R to rotate, Esc to cancel.
- **Tools**
  - Start screen: new, open, import, recent projects, the Minecraft jar used for assets, and recommended schematic download sites. Turn it off with its checkbox; click the logo to open it again.
  - Flying (C) is creative mode: you can only break, place, pick and select blocks within 5 blocks, as in Minecraft.
  - Camera: middle-drag orbits around the point under the mouse, Shift+middle-drag pans, wheel zooms.
  - Modes: View (V) only looks around. Select (Q): left-click sets WorldEdit pos1 and right-click pos2, and the box's blocks are selected; Shift/Ctrl-click and marquee drags pick single blocks, Shift+right-click opens the menu, Esc clears. Build (B, which toggles back to the previous mode and also works while flying) works like Minecraft: left-click breaks, right-click places (hold to repeat), middle-click picks the block. Move (G) and Rotate (E) show Blender-style gizmos for the selected layers.
  - Undo and redo cover every action.
- **Coming soon**
  - AI Assistant: describe a build or show a reference image, and the assistant builds it block by block. It will work with Claude or any OpenAI-compatible server (OpenAI, Ollama, LM Studio). The code is in the `ai` module; it stays switched off (`AI_ASSISTANT` in `BlockDesignerApp`) until it has been tested end to end.
  - Resource Tracker: the materials a build needs, what you have gathered and what is left.
- **Worldgen export**
  - Writes a data pack with a jigsaw structure, template pool, structure set and biome tag, in the layout the chosen version expects.
  - Can save it as a zip or install it straight into a world. Test it in-game with `/place structure <ns>:<name>`.

## Controls and keybinds

The same list is in the app: press **Alt+K** or **F1**, or click the ⌘ button in the viewport. Single-letter keys don't fire while you're typing in a text box.

### Modes

| Key | Action |
|---|---|
| V | View mode: look around only |
| Q | Select mode |
| B | Build mode on / off (also while flying) |
| G | Move tool (gizmo for the selected layers) |
| E | Rotate tool (gizmo for the selected layers) |
| Esc | Cancel a drag or placement, clear the selection, stop flying, then back to Select |

### Camera

| Key | Action |
|---|---|
| Middle-drag | Orbit around the point under the mouse |
| Shift+middle-drag | Pan |
| Alt+middle-drag | Swing to the next orthographic view in that direction (left, right, top, bottom…) |
| Wheel | Zoom |
| F | Frame the selected blocks, or else the active layer |
| Shift+F / Home | Frame everything |
| C | Creative flight on / off |
| P / O | Perspective / orthographic |
| Numpad 1 / 3 / 7 | Front / right / top view (Ctrl: back / left / bottom) |
| Numpad 9 | Opposite side of the current view |
| Numpad 5 | Toggle perspective / orthographic |
| Numpad 2 / 4 / 6 / 8 | Orbit 15° down / left / right / up |
| Numpad . | Frame the active layer |
| View cube | Click a face for that view (click again for the opposite side), drag to orbit |

### Flying

| Key | Action |
|---|---|
| W A S D | Move (W and S follow where you look) |
| Space / Shift | Up / down |
| Ctrl | Sprint |
| Wheel | Next / previous hotbar slot |
| C / Esc | Stop flying |

Reach is 5 blocks, as in creative mode. Momentum (gliding to a stop) can be turned off in viewport settings.

### Build mode

| Key | Action |
|---|---|
| Left-click | Break (hold to repeat) |
| Right-click | Place (hold to repeat), oriented like Minecraft |
| Middle-click | Pick the block into the hotbar |
| R | Replace mode: right-click swaps the aimed block and keeps its facing (drag to paint) |
| Z | Shuffle mode: place random blocks from the hotbar |
| Delete | Empty the held hotbar slot |

### Hotbar

| Key | Action |
|---|---|
| 1 – 9 | Hold a slot |
| Middle-click | Record the block under the cursor |
| Drag from palette | Put a block in a slot |
| Alt+C | Clear the hotbar |

### Select mode

| Key | Action |
|---|---|
| Left-click | Set WorldEdit pos1 |
| Right-click | Set WorldEdit pos2 (the box's blocks become the selection) |
| Shift+click / Ctrl+click | Add a block to / remove it from the selection |
| Drag | Marquee select (Shift adds, Ctrl removes) |
| Shift+right-click or Menu key | Context menu: region fill, delete, replace, select by type, copy to layer, hide, lock |
| T | Select by type, with layer and property filters |
| Ctrl+A / Alt+A | Select every block of the active layer / deselect |
| Ctrl+J | Copy the selected blocks to a new layer |
| Ctrl+R | Fill the selected blocks with the held block |
| Delete | Delete the selected blocks |
| Esc | Clear the region and selection |

### WorldEdit commands

Press **/** (or the terminal button) to open the command bar. **Tab** completes, **↑ / ↓** walk the history, **Enter** runs, **Esc** closes. Commands edit the active layer, and each one is a single undo step.

| Command | Action |
|---|---|
| `//pos1`, `//pos2` [x y z] | Set a corner (default: the block you aim at) |
| `//sel` | Clear the region |
| `//set <pattern>` | Fill the region, e.g. `stone`, `70%stone,30%andesite`, `hand` |
| `//replace [from] <to>` | Replace blocks (just `<to>`: every non-air block) |
| `//walls`, `//faces`, `//overlay` `<pattern>` | Side walls, all six faces, a layer on top of each column |
| `//copy`, `//cut`, `//paste [-a] [-s]` | Clipboard, relative to pos1 (`-a` skips air, `-s` selects what was pasted) |
| `//rotate <90\|180\|270>`, `//flip [dir]` | Turn or mirror the clipboard |
| `//move [n] [dir] [-a]`, `//stack [count] [dir] [-a]` | Move or repeat the region's contents |
| `//expand <n> [dir]`, `//expand vert`, `//contract <n> [dir]`, `//shift <n> [dir]` | Resize or move the region |
| `//outset <n>`, `//inset <n>` | Grow or shrink every side |
| `//line <pattern> [thickness]` | A line from pos1 to pos2 |
| `//sphere`, `//hsphere` `<pattern> <radius>` | Solid / hollow sphere around pos1 |
| `//cyl`, `//hcyl` `<pattern> <radius> [height]` | Solid / hollow cylinder up from pos1 |
| `//pyramid`, `//hpyramid` `<pattern> <size>` | Solid / hollow pyramid on pos1 |
| `//count <mask>`, `//distr`, `//size` | Count blocks, what the region is made of, its size |
| `//undo`, `//redo`, `//help [command]` | Undo, redo, list commands |

Directions: `me` (where you look, the default), `up`, `down`, `north`, `south`, `east`, `west`, `forward`, `back`, `left`, `right`.

### Move and Rotate tools

| Action | Result |
|---|---|
| Drag an arrow | Move along that axis |
| Drag a square | Move in that plane |
| Drag the centre | Move freely in the view plane |
| Drag a ring | Turn about that axis in 90° steps |
| Click a layer | Select it (Shift adds) |
| Esc / right-click | Cancel the drag |

### Moving layers

| Key | Action |
|---|---|
| Ctrl+wheel | Along the hovered face's axis (left / right elsewhere) |
| Ctrl+Shift+wheel | Up / down |
| Shift+wheel | Back / forward |
| Arrow keys | Left / right, back / forward |
| Hold Tab | Bigger steps |
| Alt+wheel | Spin (over the top) or flip (over a side) |

### Layers

| Key | Action |
|---|---|
| [ / ] | Make the layer below / above active |
| Ctrl+Shift+N | New empty layer |
| Ctrl+D | Duplicate the selected layers |
| Ctrl+M | Merge the selected layers (or the active one down) |
| F2 | Rename the active layer |
| H / Alt+H | Hide or show the selected layers / show every layer |
| Shift+H | Ghost the selected layers |
| L | Lock or unlock the selected layers |
| Shift+Delete | Delete the selected layers |

### Placing an import

| Key | Action |
|---|---|
| Click / Enter | Place |
| R / Alt+wheel | Rotate |
| Esc | Cancel |

### Slice view

| Key | Action |
|---|---|
| PgUp / PgDn | Step through Y levels |
| Insert | Single level on / off |

### File, edit and view

| Key | Action |
|---|---|
| Ctrl+N | New project |
| Ctrl+O / Ctrl+I | Open / import |
| Ctrl+S / Ctrl+Shift+S | Save / save as |
| Ctrl+E / Ctrl+Shift+E | Export schematic / worldgen data pack |
| Ctrl+Z | Undo |
| Ctrl+Y / Ctrl+Shift+Z | Redo |
| Ctrl+F | Search blocks |
| N | Viewport settings |
| Alt+G | Ground grid on / off |
| Alt+K / F1 | Keyboard shortcuts |
| F11 | Full screen |

## Modules

| Module | What it does |
|---|---|
| `core` | NBT, block states, sparse structures, layers and scenes, undo, transforms, schematic formats, `.bdproj` projects |
| `assets` | Finds installs; loads blockstates, models and textures from game, mod and resource-pack jars; bakes models; builds the texture atlas |
| `render` | LWJGL/OpenGL offscreen renderer (MSAA, smooth AO, sorted translucency), meshing on worker threads, picking |
| `ai` | Provider interface, Claude and OpenAI-compatible providers, build tools, agent loop |
| `worldgen` | Data pack exporter |
| `app` | JavaFX UI (AtlantaFX theme) |

## Tests

```
./gradlew test
```

The integration tests in `assets` and `render` use your local Minecraft install and `testdata/` when they exist, and are skipped otherwise.
