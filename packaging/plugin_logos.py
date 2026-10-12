"""The official plugins' logos, in the style of the BlockDesigner icon (see icon.py), drawn by the voxel-icons skill:

    python <voxel-icons skill>/scripts/draw.py packaging/plugin_logos.py [--only NAME] [--preview DIR]

Same isometric voxels, navy edges and three-tone faces, and each keeps the icon's orange voxel hovering as if it is
being placed:

  PaletteTools        the block repainted as a gradient (blue, violet, pink)
  ReferencePlanes     a picture on a plane, with a block standing in front of it
  TerrainGenerator    voxel hills with water in the low ground
  ResourceTracker     stacks of blocks rising like a bar chart, one more on its way to the tallest
  PixelArtGenerator   a pixelated picture on a plane, its pixels stepping out of it as blocks
  plugin              the icon's block with a plug (the orange voxel, prongs down) coming in to the gap

Each plugin's logo goes to docs/images/logo.png (160 px) and logo-1024.png in its repository next to this one
(../BlockDesigner-<Name>), skipped when that checkout is missing. The generic plugin logo goes to
docs/images/plugin-logo.png here (and packaging/plugin-icon-1024.png), for plugins without one of their own.
"""
import math

from voxel import BLUE, EDGE, GREEN, ORANGE, PAPER, PINK, VIOLET, WATER, Box, by_depth, mix, voxel


def ramp(t):
    """Blue, through violet, to pink, for t from 0 to 1."""
    return mix(BLUE, VIOLET, t * 2) if t < 0.5 else mix(VIOLET, PINK, (t - 0.5) * 2)


def palette_tools():
    """The icon's block, repainted as a gradient from blue at the back to pink at the front."""
    n, top = 3, 2
    cells = [voxel(x, y, z, ramp((x + z + (2 - y)) / 6)) for x in range(n) for y in range(n) for z in range(n)
             if (x, y, z) != (top, top, top)]
    return by_depth(cells) + [voxel(top, top + 0.9, top, ORANGE)]


def reference_planes():
    """A picture (hills under a sun) on a thin plane, and a block standing in front of it."""

    def picture(draw, at, edge_w):
        # Two hills, then the sun.
        hills = [(0.0, 0.0), (0.0, 0.28), (0.22, 0.55), (0.42, 0.34), (0.66, 0.68), (1.0, 0.3), (1.0, 0.0)]
        draw.polygon([at(u, v) for u, v in hills], fill=BLUE[1] + (255,))
        draw.line([at(u, v) for u, v in hills[1:-1]], fill=EDGE, width=max(1, edge_w // 2), joint="curve")
        sun = [at(0.24 + 0.1 * math.cos(a), 0.8 + 0.1 * math.sin(a) * 1.0) for a in [i * math.pi / 12 for i in range(24)]]
        draw.polygon(sun, fill=ORANGE[1] + (255,))

    plane = Box(0, 0, 0, 3.8, 3.0, 0.22, PAPER, picture)
    block = [voxel(x, y, z, BLUE) for x in (2.8, 3.8) for y in (0, 1) for z in (1.6, 2.6)]
    return [plane] + by_depth(block) + [voxel(3.8, 2.9, 2.6, ORANGE)]


def terrain_generator():
    """Voxel hills: grass on the heights, water in the low ground."""
    heights = [[3, 2, 2, 2],
               [3, 3, 1, 1],
               [2, 1, 1, 0],
               [2, 1, 0, 0]]
    cells = []
    for x in range(4):
        for z in range(4):
            h = heights[z][x]
            if h == 0:
                cells.append(Box(x, 0, z, 1, 0.75, 1, WATER))
            for y in range(h):
                cells.append(voxel(x, y, z, GREEN))
    return by_depth(cells) + [voxel(0, 3.9, 0, ORANGE)]


def resource_tracker():
    """Stacks of blocks rising like a bar chart, with one more arriving on the tallest."""
    heights = [1, 2, 3]
    # Bars apart, so they read as a chart rather than a staircase.
    xs = [0, 1.6, 3.2]
    cells = [voxel(xs[i], y, 0, BLUE) for i, h in enumerate(heights) for y in range(h)]
    return by_depth(cells) + [voxel(xs[2], 3.9, 0, ORANGE)]


def pixel_art_generator():
    """A pixelated picture on a plane, its pixels stepping out of it as blocks."""
    # Rows top to bottom: sky, sun, hills. Each cell one colour, like the builder's block grid.
    grid = [[BLUE, BLUE, ORANGE, BLUE],
            [VIOLET, BLUE, BLUE, VIOLET],
            [GREEN, GREEN, VIOLET, GREEN],
            [GREEN, GREEN, GREEN, GREEN]]
    n = len(grid)

    def picture(draw, at, edge_w):
        for r, row in enumerate(grid):
            for c, col in enumerate(row):
                u0, u1 = c / n, (c + 1) / n
                v0, v1 = 1 - (r + 1) / n, 1 - r / n
                draw.polygon([at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1)], fill=col[1] + (255,))

    plane = Box(0, 0, 0, 3.2, 3.2, 0.22, PAPER, picture)
    # Beside it, the picture's colours built as blocks: hills below, sky above, the sun arriving on top.
    blocks = [voxel(4.2, 0, 1, GREEN), voxel(5.2, 0, 1, GREEN), voxel(5.2, 1, 1, VIOLET), voxel(4.2, 1, 1, BLUE)]
    return [plane] + by_depth(blocks) + [voxel(5.2, 2.9, 1, ORANGE)]


def generic_plugin():
    """The icon's block with a plug coming in to the gap: the orange voxel, two prongs pointing down."""
    n, top = 3, 2
    cells = [voxel(x, y, z, BLUE) for x in range(n) for y in range(n) for z in range(n) if (x, y, z) != (top, top, top)]
    # High enough that the prongs show over the gap.
    lift = top + 1.9
    prongs = [Box(top + 0.18, lift - 0.8, top + 0.38, 0.24, 0.8, 0.24, ORANGE),
              Box(top + 0.58, lift - 0.8, top + 0.38, 0.24, 0.8, 0.24, ORANGE)]
    return by_depth(cells) + prongs + [voxel(top, lift, top, ORANGE)]


def plugin_logo(name, drawing):
    repo = f"../../BlockDesigner-{name}"
    return {"name": name, "drawing": drawing(), "requires": repo, "from_big": True,
            "png": {f"{repo}/docs/images/logo.png": 160, f"{repo}/docs/images/logo-1024.png": 1024}}


ICONS = [
    {"name": "plugin", "drawing": generic_plugin(), "from_big": True,
     "png": {"../docs/images/plugin-logo.png": 160, "plugin-icon-1024.png": 1024}},
    plugin_logo("PaletteTools", palette_tools),
    plugin_logo("ReferencePlanes", reference_planes),
    plugin_logo("TerrainGenerator", terrain_generator),
    plugin_logo("ResourceTracker", resource_tracker),
    plugin_logo("PixelArtGenerator", pixel_art_generator),
]
