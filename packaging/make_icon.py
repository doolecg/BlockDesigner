"""Draws the BlockDesigner icon (app window, installer and .bdproj saves).

An isometric block built from 3x3x3 voxels in the app's accent blue, with the top front corner voxel missing and an
orange voxel hovering above the gap, as if it is being placed. Rendered large and downscaled for every size.

Run:  python packaging/make_icon.py   (needs Pillow)
Writes:
  app/src/main/resources/io/blockdesigner/app/icons/icon-<size>.png   window / taskbar icons
  packaging/windows/blockdesigner.ico                                  installer + .bdproj file icon
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BIG = 1024
N = 3  # voxels per side

# Face colours: top, left (+z), right (+x). Accent #7c9cff family.
BLUE = ((178, 196, 255), (124, 156, 255), (84, 108, 214))
ORANGE = ((255, 206, 128), (255, 159, 46), (214, 118, 20))
EDGE = (28, 38, 84, 255)


def project(x, y, z, s, ox, oy):
    """Isometric: +x goes right-down, +z goes left-down, +y goes up."""
    c, k = math.cos(math.radians(30)), math.sin(math.radians(30))
    return ox + (x - z) * c * s, oy + (x + z) * k * s - y * s


def voxel(draw, x, y, z, colours, s, ox, oy, edge_w):
    p = lambda a, b, c: project(a, b, c, s, ox, oy)
    top = [p(x, y + 1, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)]
    left = [p(x, y + 1, z + 1), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x, y, z + 1)]
    right = [p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1), p(x + 1, y, z)]
    for poly, col in ((top, colours[0]), (left, colours[1]), (right, colours[2])):
        draw.polygon(poly, fill=col + (255,))
        draw.line(poly + [poly[0]], fill=EDGE, width=edge_w, joint="curve")


def render(size_px, n):
    """Draws at size_px with n voxels per side, fitted to the drawing's own bounds."""
    # Projected extent with unit scale: cube corners plus the floating voxel's top.
    pts = [project(x, y, z, 1, 0, 0) for x in (0, n) for y in (0, n + 0.9) for z in (0, n)]
    minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
    miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
    margin = 0.04 * size_px
    s = (size_px - 2 * margin) / max(maxx - minx, maxy - miny)
    ox = size_px / 2 - (minx + maxx) / 2 * s
    oy = size_px / 2 - (miny + maxy) / 2 * s

    img = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    edge = max(2, round(size_px / (90 if n > 2 else 55)))
    top = n - 1
    cells = [(x, y, z) for x in range(n) for y in range(n) for z in range(n) if (x, y, z) != (top, top, top)]
    cells.sort(key=lambda c: (c[0] + c[1] + c[2], c[1]))  # back to front
    for x, y, z in cells:
        voxel(d, x, y, z, BLUE, s, ox, oy, edge)
    # The block being placed, hovering over the gap.
    voxel(d, top, top + 0.9, top, ORANGE, s, ox, oy, edge)
    return img


def sized(target):
    """Supersampled 4x then downscaled; tiny sizes use a simpler 2x2x2 block so they stay readable."""
    n = 2 if target <= 32 else N
    return render(target * 4 if target < 256 else BIG, n).resize((target, target), Image.LANCZOS)


def main():
    big = render(BIG, N)
    icons = os.path.join(ROOT, "app", "src", "main", "resources", "io", "blockdesigner", "app", "icons")
    os.makedirs(icons, exist_ok=True)
    sizes = [16, 24, 32, 48, 64, 128, 256, 512]
    for sz in sizes:
        sized(sz).save(os.path.join(icons, f"icon-{sz}.png"))
    win = os.path.join(ROOT, "packaging", "windows")
    os.makedirs(win, exist_ok=True)
    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    frames = [sized(sz) for sz in ico_sizes]
    frames[-1].save(os.path.join(win, "blockdesigner.ico"), sizes=[(sz, sz) for sz in ico_sizes], append_images=frames[:-1])
    big.save(os.path.join(ROOT, "packaging", "icon-1024.png"))
    print("icons written")


if __name__ == "__main__":
    main()
