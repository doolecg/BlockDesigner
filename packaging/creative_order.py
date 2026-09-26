"""Writes the creative menu's block order (assets/.../creative_order.txt) from a Minecraft client jar.

Reads the bytecode of net.minecraft.world.item.CreativeModeTabs (unobfuscated since 26.1) with javap, and lists
the items each block tab adds, in order. Colour and copper collections are expanded by name. The app only uses
the order: ids that aren't blocks (or don't exist in the loaded version) are skipped when it reads the file.

    python packaging/creative_order.py <minecraft-26.x-client.jar> [javap]
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

TABS = [("building_blocks", "OAK_LOG"), ("colored_blocks", "WHITE_WOOL"), ("natural_blocks", "GRASS_BLOCK"),
        ("functional_blocks", None), ("redstone_blocks", "REDSTONE")]
COLORS = ["white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan",
          "light_blue", "blue", "purple", "magenta", "pink"]
STATES = ["", "exposed_", "weathered_", "oxidized_"]

ITEM = re.compile(r"getstatic .*// Field net/minecraft/world/item/Items\.([A-Z0-9_]+):L([^;]+);")
METHOD = re.compile(r"^  (?:private |public )?static .* ([\w$]+)\(")


def copper(name, waxed):
    base = name.lower()
    out = []
    for i, st in enumerate(STATES):
        if base == "copper_block" and i > 0:
            n = st + "copper"
        else:
            n = st + base
        out.append(("waxed_" if waxed else "") + n)
    return out


def items(lines, families):
    out = []
    for line in lines:
        if "copperBlockFamilies" in line and "invokestatic" in line:
            # Called twice: the unwaxed families, then the waxed ones.
            waxed = sum(1 for x in out if x == "#copper") > 0
            out.append("#copper")
            for f in families:
                out.extend(copper(f, waxed))
            continue
        m = ITEM.search(line)
        if not m:
            continue
        name, kind = m.groups()
        if kind.endswith("ColorCollection"):
            suffix = name.lower().removeprefix("dyed_")
            out.extend(c + "_" + suffix for c in COLORS)
        elif kind.endswith("WeatheringCopperCollection"):
            out.extend(copper(name, False) + copper(name, True))
        else:
            out.append(name.lower())
    return [x for x in out if x != "#copper"]


def main():
    jar = sys.argv[1]
    javap = sys.argv[2] if len(sys.argv) > 2 else "javap"
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(jar) as z:
            z.extract("net/minecraft/world/item/CreativeModeTabs.class", tmp)
        code = subprocess.run([javap, "-c", "-p", os.path.join(tmp, "net/minecraft/world/item/CreativeModeTabs.class")],
                              capture_output=True, text=True, check=True).stdout.splitlines()
    methods, cur = {}, None
    for line in code:
        m = METHOD.match(line)
        if m:
            cur = m.group(1)
            methods.setdefault(cur, [])
        elif cur:
            methods[cur].append(line)
    families = [m.group(1) for m in map(ITEM.search, methods["copperBlockFamilies"]) if m]
    tabs = {n: items(ls, families) for n, ls in methods.items() if n.startswith("lambda$bootstrap$")}

    def first(n):
        return next((i for i in tabs[n] if i), None)

    used, out = set(), ["# Minecraft's creative menu order for its block tabs, from " + os.path.basename(jar),
                        "# (made by packaging/creative_order.py; the app skips ids that aren't blocks)"]
    for tab, lead in TABS:
        if lead is None:
            # Functional blocks: the tab holding the crafting table.
            name = next(n for n, it in tabs.items() if "crafting_table" in it and n not in used)
        else:
            name = next(n for n, it in tabs.items() if it and it[0] == lead.lower() and n not in used)
        used.add(name)
        out.append("[" + tab + "]")
        seen = set()
        for i in tabs[name]:
            if i not in seen:
                seen.add(i)
                out.append(i)
    dest = os.path.join(os.path.dirname(__file__), "..", "assets", "src", "main", "resources", "io", "blockdesigner",
                        "assets", "creative_order.txt")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print("wrote", os.path.normpath(dest), sum(1 for l in out if not l.startswith(("#", "["))), "ids")


if __name__ == "__main__":
    main()
