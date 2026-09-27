# Project file format (.bdproj)

A `.bdproj` file is a BlockDesigner project. It is also the format that moves builds between the app and the game:
BlockCompanion opens `.bdproj` files directly. Single schematics travel as `.schem` (Sponge Schematic v3). Litematica
`.litematic` and vanilla `.nbt` files can still be imported and exported, but nothing depends on them.

The code that reads and writes it is `core/src/main/java/io/blockdesigner/core/project/ProjectFile.java`.

## The container

A `.bdproj` is a zip file containing:

| Entry | What it holds |
|---|---|
| `project.json` | The project: name, Minecraft version and the list of layers. |
| `layers/<id>.schem` | One file per layer: its blocks, block entities (chest contents, signs…) and entities (mobs), as Sponge Schematic v3. Format 1 used `layers/<id>.litematic` instead. |
| anything else | Extra entries that other parts of the app and plugins store in the project. Readers must keep or ignore them. |

## project.json

```json
{
  "format": 2,
  "name": "Village",
  "targetVersion": "1.21.1",
  "dataVersion": 3955,
  "activeLayer": "5b0c…",
  "layers": [
    {
      "id": "5b0c…",
      "name": "House",
      "offset": [100, 64, -20],
      "rotation": 3,
      "mirror": "X",
      "visible": true,
      "locked": false,
      "ghost": false,
      "color": "#123456",
      "source": "litematic",
      "file": "layers/5b0c….schem",
      "origin": [-3, -2, -1]
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `format` | `2` for projects saved by BlockDesigner 0.4.23 and later, `1` before. A reader should refuse a format number it doesn't know. |
| `name` | The project's name. |
| `targetVersion`, `dataVersion` | The Minecraft version the project is for, and its data version. |
| `activeLayer` | The id of the layer that was active (optional). |
| `layers` | Layers, bottom first. |
| `id` | The layer's unique id. |
| `name` | The layer's name. |
| `offset` | Where the layer sits in the world, in blocks (x, y, z). |
| `rotation` | Quarter turns around the vertical axis, 0 to 3 (see below). |
| `mirror` | `NONE`, `X` (east and west swap) or `Z` (north and south swap). |
| `visible`, `locked`, `ghost` | The layer's flags in the app. |
| `color` | The layer's colour in the app, as `#RRGGBB`. |
| `source` | The format the layer was imported from, if any (optional). |
| `file` | The zip entry holding the layer's blocks. |
| `origin` | The layer's own coordinates of its lowest corner (x, y, z). The block file is stored from that corner, so this puts the blocks back where they were in the layer. |

## From a layer to the world

A block at position `p` in a layer's block file ends up in the world like this:

1. **Back to layer coordinates:** `local = p + origin`.
2. **Mirror,** around the layer's own 0, 0, 0: `X` makes x → −x; `Z` makes z → −z.
3. **Rotate** `rotation` times around the layer's own 0, 0, 0. Each quarter turn maps (x, z) to (−z, x), which is clockwise seen from above (east turns to south).
4. **Move:** add `offset`.

Blocks keep their own states (a stair's `facing`, a log's `axis`…) as stored, and BlockDesigner turns them to match the
layer's rotation and mirror when it shows or exports the layer. Entities (mobs) are turned the same way, around block
centres.

## The layer files

Each `layers/<id>.schem` is a standard Sponge Schematic v3 file (gzip-compressed NBT): a block palette with packed
block data, `BlockEntities` and `Entities`, and an `Offset` of 0, 0, 0. Any program that reads Sponge v3 can open a
layer on its own. Positions in it start at the layer's lowest corner, which `origin` records.

## Older projects

Format 1 projects (BlockDesigner 0.4.22 and earlier) store layers as `layers/<id>.litematic`, with the same
`project.json` fields. BlockDesigner still opens them, and saves them as format 2 from then on.
