# BlockDesigner

An AI-assisted Minecraft structure designer. Describe a build or show a reference image, and it appears block by block in a live 3D view. You refine it by chatting or by editing it yourself, then export it as a schematic or as a worldgen data pack.

## Run

```
./gradlew :app:run
```

Requires JDK 25+. `gradle.properties` points Gradle at a local Temurin 26. On first launch, pick the Minecraft version whose textures and models to use. You can also pick a modded instance (Prism, CurseForge, Modrinth or the official launcher) so modded blocks, such as Create's, render too. Nothing from Minecraft is copied or shipped.

## Features

- **Formats**
  - Import and export Create / structure-block `.nbt`, Litematica `.litematic` (multi-region) and WorldEdit `.schem` (Sponge v2/v3).
  - Directional blocks (stairs, doors, rails, fences, signs) stay correct through rotation and mirroring.
- **Layers**
  - Every schematic, imported or new, is a layer, with visibility, lock and ghost toggles, rename, reorder, duplicate and merge.
  - Nudge the selected layers relative to the camera:
    - Ctrl+wheel: up/down
    - Alt+wheel: left/right
    - Shift+wheel: back/forward
    - Arrow keys and PgUp/PgDn do the same
    - Hold Tab for 8-block steps
  - Imports appear as a ghost that follows the cursor. Click to place, R to rotate, Esc to cancel.
- **Tools**
  - Select (Q), move (M), place (B), erase (E), paint (P), box fill (X; Shift sets the height, Ctrl clears) and pick (I).
  - Undo and redo cover every action.
- **Assistant**
  - Uses Claude (default model: Claude Opus 5) or any OpenAI-compatible server (OpenAI, Ollama, LM Studio).
  - Builds with dedicated tools: fill, set_blocks, sphere, cylinder, roof, copy/rotate, replace, layers, and render_view so it can look at its own work.
  - Each assistant turn is one undo step and adds a snapshot to the iteration timeline.
- **Worldgen export**
  - Writes a data pack with a jigsaw structure, template pool, structure set and biome tag, in the layout the chosen version expects.
  - Can save it as a zip or install it straight into a world. Test it in-game with `/place structure <ns>:<name>`.

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
