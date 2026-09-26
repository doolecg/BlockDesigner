"""Draws the official plugins' logos in the style of the BlockDesigner icon (see make_icon.py).

Same isometric voxels, navy edges and three-tone faces, and each keeps the icon's orange voxel hovering as if it is
being placed:

  Palette Tools      the block repainted as a gradient (blue, violet, pink)
  Reference Planes   a picture on a plane, with a block standing in front of it
  Terrain Generator  voxel hills with water in the low ground
  Resource Tracker   stacks of blocks rising like a bar chart, one more on its way to the tallest
  any plugin         the icon's block with a plug (the orange voxel, prongs down) coming in to the gap

Run:  python packaging/make_plugin_icons.py   (needs Pillow)
Writes docs/images/logo.png in each plugin's repository next to this one (../BlockDesigner-<Name>), and
docs/images/logo-1024.png, the full-size drawing. The generic plugin logo goes to docs/images/plugin-logo.png here
(and packaging/plugin-icon-1024.png), for plugins without one of their own.
"""
import math
import os

from PIL import Image, ImageDraw

from make_icon import BLUE, EDGE, ORANGE, project

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPOS = os.path.dirname(ROOT)
BIG = 1024
LOGO = 160

# More face colours (top, left, right) in the icon's family: as light and as saturated as its blue and orange.
VIOLET = ((208, 184, 255), (160, 128, 250), (118, 88, 208))
PINK = ((255, 186, 222), (246, 128, 186), (204, 86, 146))
GREEN = ((178, 234, 156), (116, 196, 100), (74, 150, 64))
WATER = ((150, 214, 255), (96, 172, 240), (60, 128, 200))
PAPER = ((246, 247, 255), (226, 230, 250), (180, 188, 226))


class Box:
    """A box from (x, y, z) of size (w, h, d), with its three visible faces' colours and, optionally, a picture drawn
    on its +z (left) face: a function given a point mapper for that face."""

    def __init__(self, x, y, z, w, h, d, colours, picture=None):
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.colours, self.picture = colours, picture


def voxel(x, y, z, colours):
    return Box(x, y, z, 1, 1, 1, colours)


def draw_box(draw, b, s, ox, oy, edge_w):
    p = lambda a, c, e: project(a, c, e, s, ox, oy)
    x0, y0, z0, x1, y1, z1 = b.x, b.y, b.z, b.x + b.w, b.y + b.h, b.z + b.d
    top = [p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1)]
    left = [p(x0, y1, z1), p(x1, y1, z1), p(x1, y0, z1), p(x0, y0, z1)]
    right = [p(x1, y1, z0), p(x1, y1, z1), p(x1, y0, z1), p(x1, y0, z0)]
    for poly, col in ((top, b.colours[0]), (left, b.colours[1]), (right, b.colours[2])):
        draw.polygon(poly, fill=col + (255,))
    if b.picture:
        # Face coordinates u (0..1 along x) and v (0..1 up) on the +z face.
        b.picture(draw, lambda u, v: p(x0 + u * b.w, y0 + v * b.h, z1), edge_w)
    for poly in (top, left, right):
        draw.line(poly + [poly[0]], fill=EDGE, width=edge_w, joint="curve")


def render(boxes, size_px):
    """Draws the boxes (already in back-to-front order), fitted to the drawing's own bounds."""
    pts = [project(x, y, z, 1, 0, 0) for b in boxes
           for x in (b.x, b.x + b.w) for y in (b.y, b.y + b.h) for z in (b.z, b.z + b.d)]
    minx, maxx = min(q[0] for q in pts), max(q[0] for q in pts)
    miny, maxy = min(q[1] for q in pts), max(q[1] for q in pts)
    margin = 0.04 * size_px
    s = (size_px - 2 * margin) / max(maxx - minx, maxy - miny)
    ox = size_px / 2 - (minx + maxx) / 2 * s
    oy = size_px / 2 - (miny + maxy) / 2 * s
    img = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    edge = max(2, round(size_px / 90))
    for b in boxes:
        draw_box(d, b, s, ox, oy, edge)
    return img


def by_depth(boxes):
    """Back to front for boxes on a grid: the icon's own ordering."""
    return sorted(boxes, key=lambda b: (b.x + b.y + b.z, b.y))


# ---- the four logos ---------------------------------------------------------------------------------------------

def mix(a, b, t):
    return tuple(tuple(round(ca + (cb - ca) * t) for ca, cb in zip(fa, fb)) for fa, fb in zip(a, b))


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


def generic_plugin():
    """The icon's block with a plug coming in to the gap: the orange voxel, two prongs pointing down."""
    n, top = 3, 2
    cells = [voxel(x, y, z, BLUE) for x in range(n) for y in range(n) for z in range(n) if (x, y, z) != (top, top, top)]
    # High enough that the prongs show over the gap.
    lift = top + 1.9
    prongs = [Box(top + 0.18, lift - 0.8, top + 0.38, 0.24, 0.8, 0.24, ORANGE),
              Box(top + 0.58, lift - 0.8, top + 0.38, 0.24, 0.8, 0.24, ORANGE)]
    return by_depth(cells) + prongs + [voxel(top, lift, top, ORANGE)]


LOGOS = {
    "BlockDesigner-PaletteTools": palette_tools,
    "BlockDesigner-ReferencePlanes": reference_planes,
    "BlockDesigner-TerrainGenerator": terrain_generator,
    "BlockDesigner-ResourceTracker": resource_tracker,
}


def main():
    big = render(generic_plugin(), BIG)
    big.save(os.path.join(ROOT, "packaging", "plugin-icon-1024.png"))
    big.resize((LOGO, LOGO), Image.LANCZOS).save(os.path.join(ROOT, "docs", "images", "plugin-logo.png"))
    print("wrote", os.path.join(ROOT, "docs", "images", "plugin-logo.png"))
    for repo, scene in LOGOS.items():
        out = os.path.join(REPOS, repo, "docs", "images")
        if not os.path.isdir(os.path.join(REPOS, repo)):
            print("skipped", repo, "(no checkout next to this repository)")
            continue
        os.makedirs(out, exist_ok=True)
        big = render(scene(), BIG)
        big.save(os.path.join(out, "logo-1024.png"))
        big.resize((LOGO, LOGO), Image.LANCZOS).save(os.path.join(out, "logo.png"))
        print("wrote", os.path.join(out, "logo.png"))


if __name__ == "__main__":
    main()
