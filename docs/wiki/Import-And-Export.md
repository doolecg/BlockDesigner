# Import and export

## Formats

BlockDesigner imports and exports:

| Format | File |
|---|---|
| Create / structure block | `.nbt` |
| Litematica (multi-region) | `.litematic` |
| WorldEdit (Sponge v2 / v3) | `.schem` |

Directional blocks stay correct through rotation and mirroring. Projects are saved as `.bdproj`.

## Importing

Press **Ctrl+I** or use **Import**. The imported schematic becomes a layer and follows the mouse: **click** or **Enter** places it, **R** rotates it and **Esc** cancels. See [Layers](Layers).

Pictures imported this way become [reference images](Reference-Images). When two plugins can import the same kind of file, Import asks which one to use.

## Exporting a schematic

Press **Ctrl+E** for the Export window. It has a card per format showing what will be exported: layers, blocks, size and block types.

It can save straight into a detected instance's `schematics` or `config/worldedit/schematics` folder, and it remembers your choices.

## Worldgen data pack

Press **Ctrl+Shift+E** to make a build generate in new chunks. There are two kinds:

- A **single structure**, with random variants.
- A **village-style** layout with jigsaw pieces. Mark layers as the centre, streets or buildings. BlockDesigner adds the jigsaw blocks and can generate streets.

Built-in and saved presets set placement, biomes and blending. Containers can get loot tables.

Save the pack as a zip or install it straight into a world. To test it in the game, run:

```
/place structure <namespace>:<name>
```

## Keys

| Key | Action |
|---|---|
| Ctrl+N / Ctrl+O / Ctrl+I | New / open / import |
| Ctrl+S / Ctrl+Shift+S | Save / save as |
| Ctrl+E / Ctrl+Shift+E | Export schematic / worldgen data pack |
