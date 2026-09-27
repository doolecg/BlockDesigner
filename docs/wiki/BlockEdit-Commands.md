# BlockEdit commands

**BlockEdit** is BlockDesigner's command editing: a cuboid region between `//pos1` and `//pos2`, a clipboard, and
familiar commands such as `/set`, `/replace`, `/walls`, `/copy`, `/paste`, `/stack` and `/sphere`. Users type them in
the command bar (T or `/` opens it, like Minecraft's chat) or in the line at the bottom of the Console. A plugin can
add its own commands, which get completion, their usage shown while typing, and a line in `/help`.

## The name: BlockEdit, and a class called `WorldEdit`

The feature used to be called WorldEdit in the app and was renamed **BlockEdit** in user-facing text (0.4.22). The
Java class that implements it kept its old name, `io.blockdesigner.core.worldedit.WorldEdit`, and so did its nested
types, because they are part of the plugin API: `PluginContext.editWorld(String, Consumer<WorldEdit.World>)`,
`PluginCommand.Context.world()` (a `WorldEdit.World`), `TransformContext.world()` and `ToolContext.Stroke.world()` all
use `WorldEdit.World`. Renaming the class would break every compiled plugin, so:

- in **code**, it is `WorldEdit` and `WorldEdit.World` (import `io.blockdesigner.core.worldedit.WorldEdit`);
- in **text users see** (your command descriptions, notes, READMEs), call it BlockEdit.

Don't confuse either with "WorldEdit (.schem)", the name of the Sponge schematic *file format* in Import and Export,
which is about the WorldEdit mod's files and keeps its name.

## Registering a command (API 1)

```java
ctx.registerCommand(new PluginCommand("pillar", "/pillar <height> [block]", "Build a pillar on the aimed block", c -> {
    if (c.args().isEmpty()) throw new IllegalArgumentException("Usage: /pillar <height> [block]");
    int height;
    try {
        height = Integer.parseInt(c.args().getFirst());
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Height must be a number");
    }
    BlockState block = c.args().size() > 1 ? c.blocks().resolve(c.args().get(1))
            : c.hand().orElse(BlockState.of("minecraft:stone_bricks"));
    BlockPos base = c.aim().orElseThrow(() -> new IllegalArgumentException("Aim at a block first"));
    for (int y = 1; y <= height; y++) c.world().set(base.add(0, y, 0), block);
    return "Built a " + height + "-block pillar of " + block.path();
}));
```

- **Name**: without the slash, `a-z 0-9 _` only (the `PluginCommand` constructor throws otherwise). **Usage**
  defaults to `/name`. The description gets your plugin's name appended in `/help`.
- **Context** (`PluginCommand.Context`): `args()` (words after the name, flags removed), `flags()` (`-x` style),
  `world()` (every visible, unlocked layer merged, in world coordinates), `region()` (the `//pos1 //pos2` box),
  `requireRegion()` (the region, or "Select a region first"), `aim()` (the aimed block), `hand()` (the held block),
  `blocks()` (resolves text like `oak_stairs[facing=east]`, "Unknown block: …" otherwise).
- **Undo**: everything written to `c.world()` is one undo step.
- **Result**: return the message to show; `null` or blank shows "/name done.".
- **Errors**: throw `IllegalArgumentException("friendly message")` and the message is shown as an error. Any other
  exception is logged against your plugin ("/name failed: …") and shown as "<Plugin> failed: <message>".
- **Name clashes**: if the name (or an alias) already exists, built-in or from another plugin, the `WorldEdit.register`
  call behind `registerCommand` throws "The command /name already exists". Uncaught, that fails your `enable`, so pick
  distinctive names, or catch the exception and carry on without the command.
- Commands are removed when the plugin is turned off.

## `/transform`

While any plugin has a [transform](Registering-Features.md#transforms-api-2), BlockDesigner adds a `/transform <id>`
command (`/transform` alone lists them) that opens the transform's dialog. If a plugin already registered its own
`/transform`, BlockDesigner leaves it alone and the transforms stay reachable from the menus.

## Editing outside a command

`ctx.editWorld("Label", world -> world.set(pos, state))` gives the same merged world view as a command, as one undo
step, from an action, a panel button or anywhere else on the JavaFX thread.
