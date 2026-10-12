"""The BlockDesigner icon (app window, installer and .bdproj saves), drawn by the voxel-icons skill:

    python <voxel-icons skill>/scripts/draw.py packaging/icon.py

An isometric block built from 3x3x3 voxels in the app's accent blue, with the top front corner voxel missing and an
orange voxel hovering above the gap, as if it is being placed. Sizes up to 32 px use a 2x2x2 block with thicker edges
so they stay readable. Writes the window / taskbar icons, the installer's .ico and packaging/icon-1024.png.
"""
from voxel import BLUE, ORANGE, by_depth, space, voxel


def block(n):
    top = n - 1
    cells = [voxel(x, y, z, BLUE) for x in range(n) for y in range(n) for z in range(n) if (x, y, z) != (top, top, top)]
    # The block being placed, hovering over the gap; the room kept around it is the whole block's height with it.
    return [space(0, 0, 0, n, n + 0.9, n)] + by_depth(cells) + [voxel(top, top + 0.9, top, ORANGE)]


ICONS_DIR = "../app/src/main/resources/io/blockdesigner/app/icons/"

ICONS = [
    {"name": "app",
     "drawing": lambda px: block(2 if px <= 32 else 3),
     "edge": lambda px: 55 if px <= 32 else 90,
     "png": {**{ICONS_DIR + f"icon-{s}.png": s for s in (16, 24, 32, 48, 64, 128, 256, 512)}, "icon-1024.png": 1024},
     "ico": "windows/blockdesigner.ico"},
]
