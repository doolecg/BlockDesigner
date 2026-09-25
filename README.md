# BlockDesigner

A Minecraft structure designer. Build block by block in a live 3D view with Minecraft-style controls, arrange schematics as layers, then export the result as a schematic or as a worldgen data pack. An AI assistant that builds from a description or a reference image is coming in a future release.

## Run

```
./gradlew :app:run
```

Requires JDK 25+. `gradle.properties` points Gradle at a local Temurin 26. On first launch, pick the Minecraft version whose textures and models to use. You can also pick a modded instance (Prism, CurseForge, Modrinth or the official launcher) so modded blocks, such as Create's, render too. Nothing from Minecraft is copied or shipped.

## Windows builds

- `./gradlew :app:portable` builds `dist/BlockDesigner/BlockDesigner.exe` and `dist/BlockDesigner-0.3.0-portable.zip`. Nothing needs installing, the Java runtime is bundled, and settings are kept in a `data` folder next to the exe.
- `./gradlew :app:installer` builds `dist/BlockDesigner-0.3.0.exe`, a per-user setup with Start menu and desktop shortcuts. It also makes `.bdproj` saves show the BlockDesigner icon and open in the app. It needs the WiX Toolset: unzip the [WiX 3.14 binaries](https://github.com/wixtoolset/wix3/releases) into `tools/wix3`, or have WiX on PATH.
- The version is `packageVersion` in `app/build.gradle.kts`. The icon is drawn by `packaging/make_icon.py` (needs Pillow); rerun it after changing the design.

## Features

- **Build like Minecraft:** creative flight, a hotbar, and break / place / pick-block controls with Minecraft's repeat timing.
  - **Placement:** blocks face the way Minecraft places them. Stairs and slabs go top or bottom, logs follow the clicked face, torches and signs go on walls, and doors and beds place both halves.
  - **Connections:** fences, walls, panes, redstone dust and rails connect to their neighbours as they do in game.
  - **Names:** blocks use Minecraft's own names, read from the game's and each mod's language files.
- **Tools:**
  - Select, with Select by type.
  - Build, with Replace and Shuffle modes.
  - Blender-style Move and Rotate gizmos.
  - Sculpting brushes (draw, erase, smooth, erode, fill, pinch, raise, lower, flatten, slope) and an Eraser.
- **WorldEdit:** set pos1 and pos2 with left and right click in Select mode, then press T for a chat-style command line with `/set`, `/replace`, `/walls`, `/copy`, `/paste`, `/stack`, `/smooth`, `/sphere` and more.
- **Layers:** every schematic, imported or new, is a layer.
  - Show / hide, lock, ghost, rename, reorder, duplicate and merge.
  - Move layers with the wheel or the Move gizmo, and turn them in quarter turns.
  - The slice view steps through Y levels.
- **Camera:** orbit and pan like Blender, a view cube, perspective or orthographic, numpad views, and creative flight with momentum.
- **Formats:**
  - Import and export Create / structure-block `.nbt`, Litematica `.litematic` (multi-region) and WorldEdit `.schem` (Sponge v2/v3).
  - Directional blocks stay correct through rotation and mirroring.
- **Worldgen export:** writes a data pack with a jigsaw structure, template pool, structure set and biome tag. It can save it as a zip or install it straight into a world; test it in-game with `/place structure <ns>:<name>`.
- **Modded blocks:** pick a Prism, CurseForge, Modrinth or official-launcher instance and its mods and resource packs render too.
- **Feel:** place and break sounds, break particles and smooth view transitions.
- **Undo and redo** cover every action.
- **Coming soon:**
  - **AI Assistant:** describe a build or show a reference image, and the assistant builds it block by block. It will work with Claude or any OpenAI-compatible server (OpenAI, Ollama, LM Studio). The code is in the `ai` module; it stays switched off (`AI_ASSISTANT` in `BlockDesignerApp`) until it has been tested end to end.
  - **Resource Tracker:** the materials a build needs, what you have gathered and what is left.

## Controls and keybinds

The same list is in the app: press **Alt+K** or **F1**, or click the ⌘ button in the viewport. Single-letter keys don't fire while you're typing in a text box, and while flying W A S D, Space, Shift and Ctrl always belong to flight.

### Modes and tools

| Key | Action |
|---|---|
| V | View mode: look around only |
| Q | Select mode |
| B | Build mode on / off (also while flying) |
| G / E | Move / Rotate tool (gizmos for the selected layers) |
| U / X | Brush / Eraser |
| Esc | Cancel a drag or placement, clear the selection, stop flying, then back to Select |

### Camera

| Key | Action |
|---|---|
| Middle-drag | Orbit around the point under the mouse |
| Shift+middle-drag | Pan |
| Alt+middle-drag | Swing to the next orthographic view in that direction |
| Wheel | Zoom |
| F | Frame the selected blocks, or else the active layer |
| Shift+F / Home | Frame everything |
| C | Creative flight on / off |
| P / O | Perspective / orthographic |
| Numpad 1 / 3 / 7 | Front / right / top (Ctrl: back / left / bottom) |
| Numpad 9 / 5 | Opposite side / toggle perspective and orthographic |
| Numpad 2 4 6 8 / . | Orbit 15° / frame the active layer |
| View cube | Click a face for that view (again for the opposite side), drag to orbit |

### Flying

| Key | Action |
|---|---|
| W A S D | Move (W and S follow where you look) |
| Space / Shift | Up / down |
| Ctrl | Sprint |
| Wheel | Next / previous hotbar slot |
| C / Esc | Stop flying |

### Build mode and hotbar

| Key | Action |
|---|---|
| Left-click | Break (hold to repeat) |
| Right-click | Place (hold to repeat), oriented like Minecraft |
| Middle-click | Pick the block into the hotbar |
| R | Replace mode: right-click swaps the aimed block and keeps its facing |
| Z | Shuffle mode: place random blocks from the hotbar |
| 1 – 9 | Hold a hotbar slot |
| Delete | Empty the held hotbar slot |
| Alt+C | Clear the hotbar |

### Brush and Eraser

| Key | Action |
|---|---|
| Drag | Paint with the brush mode (Eraser: remove blocks) |
| Right-drag | Smooth |
| Alt+1 … Alt+0 | Draw · Erase · Smooth · Erode · Fill · Pinch · Raise · Lower · Flatten · Slope |
| Shift+right-click | Brush settings: mode, size, strength, shape |
| - / = | Smaller / bigger brush (1–16) |
| , / . | Weaker / stronger brush (1–5) |
| Shift+drag / Ctrl+drag | Smooth / inverse mode (not while flying) |

### Select mode and WorldEdit

| Key | Action |
|---|---|
| Left-click / right-click | Set pos1 / pos2; the box's blocks become the selection |
| Shift+click / Ctrl+click | Add a block to / remove it from the selection |
| Drag | Marquee select |
| Shift+right-click or Menu key | Context menu |
| Alt+T | Select by type |
| Ctrl+A / Alt+A | Select every block of the active layer / deselect |
| Ctrl+J / Ctrl+R | Copy the selection to a new layer / fill it with the held block |
| Delete | Delete the selected blocks |
| Esc | Clear the region and selection |
| T or / | Command line, like Minecraft's chat (Tab completes, ↑ ↓ history) |

Commands take one slash and work on every visible, unlocked layer:

| Command | Action |
|---|---|
| `/pos1`, `/pos2` [x y z] | Set a corner (default: the block you aim at) |
| `/set <pattern>` | Fill the region, e.g. `stone`, `70%stone,30%andesite`, `hand` |
| `/replace [from] <to>` | Replace blocks |
| `/walls`, `/faces`, `/overlay`, `/center` `<pattern>` | Side walls, all faces, a layer on top, the middle |
| `/smooth [passes]`, `/naturalize` | Smooth the terrain · grass, dirt and stone by depth |
| `/hollow [thickness] [pattern]` | Hollow out shapes, keeping a shell |
| `/copy`, `/cut`, `/paste [-a] [-s]`, `/rotate`, `/flip` | Clipboard, relative to pos1 |
| `/move`, `/stack` `[n] [dir] [-a]` | Move or repeat the region's contents |
| `/expand`, `/contract`, `/shift` `<n> [dir]`, `/outset`, `/inset` | Resize or move the region |
| `/line`, `/sphere`, `/cyl`, `/pyramid` (`/h…` hollow) | Shapes at pos1 |
| `/count`, `/distr`, `/size`, `/sel` | Region info, clear the region |
| `/undo`, `/redo`, `/help` | Undo, redo, list commands |

### Move and Rotate tools

| Action | Result |
|---|---|
| Drag an arrow / square / the centre | Move along an axis / in a plane / freely |
| Drag a ring | Turn in 90° steps |
| Click a layer | Select it (Shift adds) |
| Esc / right-click | Cancel the drag |

### Layers

| Key | Action |
|---|---|
| Ctrl+wheel | Move along the hovered face's axis |
| Ctrl+Shift+wheel / Shift+wheel | Up / down · back / forward |
| Arrow keys, hold Tab | Move, bigger steps |
| Alt+wheel | Spin (over the top) or flip (over a side) |
| [ / ] | Make the layer below / above active |
| Ctrl+Shift+N / Ctrl+D / Ctrl+M | New / duplicate / merge |
| F2 | Rename the active layer |
| H / Alt+H / Shift+H | Hide or show / show every layer / ghost |
| L | Lock or unlock |
| Shift+Delete | Delete the selected layers |
| PgUp / PgDn, Insert | Slice view: step through Y levels, single level |
| Click / Enter, R, Esc | Placing an import: place, rotate, cancel |

### File, edit and view

| Key | Action |
|---|---|
| Ctrl+N / Ctrl+O / Ctrl+I | New / open / import |
| Ctrl+S / Ctrl+Shift+S | Save / save as |
| Ctrl+E / Ctrl+Shift+E | Export schematic / worldgen data pack |
| Ctrl+Z / Ctrl+Y (Ctrl+Shift+Z) | Undo / redo |
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
