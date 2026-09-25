package io.blockdesigner.core.worldedit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.place.BlockPlacement.Dir;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.transform.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * WorldEdit-style editing: a cuboid region between pos1 and pos2, a clipboard, and the familiar commands
 * ({@code /set}, {@code /replace}, {@code /walls}, {@code /copy}, {@code /paste}, {@code /stack}, {@code /sphere}…).
 * The session state lives here; the caller supplies the world to read and write and the player's aim and look.
 */
public final class WorldEdit {
    /** Largest region or shape a single command may touch. */
    public static final long MAX_VOLUME = 8_000_000;

    public interface World {
        BlockState get(BlockPos p);

        void set(BlockPos p, BlockState s);
    }

    /**
     * @param look     the direction the player looks (for "me" and the default direction)
     * @param aim      the block the player aims at, or null
     * @param hand     the held block, or null
     * @param resolve  parses a block name or state ("stone", "oak_stairs[facing=east]"), or null when unknown
     */
    public record Context(World world, Dir look, BlockPos aim, BlockState hand, Function<String, BlockState> resolve) {
    }

    public enum Special { NONE, UNDO, REDO }

    /**
     * @param changed   blocks set (for the undo step and the message)
     * @param region    the region moved or changed shape (the view redraws it and the selection follows)
     */
    public record Result(boolean ok, String message, int changed, boolean region, Special special) {
        public static Result ok(String msg, int changed) {
            return new Result(true, msg, changed, false, Special.NONE);
        }

        public static Result region(String msg) {
            return new Result(true, msg, 0, true, Special.NONE);
        }

        public static Result error(String msg) {
            return new Result(false, msg, 0, false, Special.NONE);
        }
    }

    /** A command for help and autocomplete. */
    public record Command(String name, String usage, String description) {
    }

    public static final List<Command> COMMANDS = List.of(
            new Command("pos1", "/pos1 [x y z]", "Set the first corner (default: the block you aim at)"),
            new Command("pos2", "/pos2 [x y z]", "Set the second corner"),
            new Command("sel", "/sel", "Clear the region"),
            new Command("size", "/size", "Size and volume of the region"),
            new Command("count", "/count <mask>", "Count blocks in the region (e.g. /count stone,dirt)"),
            new Command("distr", "/distr", "What the region is made of"),
            new Command("set", "/set <pattern>", "Fill the region (e.g. /set stone, /set 70%stone,30%andesite, /set hand)"),
            new Command("replace", "/replace [from] <to>", "Replace blocks (just <to>: every non-air block)"),
            new Command("walls", "/walls <pattern>", "The four side walls of the region"),
            new Command("faces", "/faces <pattern>", "All six faces of the region (alias /outline)"),
            new Command("overlay", "/overlay <pattern>", "A layer on top of the highest block of each column"),
            new Command("move", "/move [n] [dir] [-a]", "Move the contents (-a: don't move air), the region follows"),
            new Command("stack", "/stack [count] [dir] [-a]", "Repeat the contents next to itself"),
            new Command("copy", "/copy", "Copy the region, relative to pos1"),
            new Command("cut", "/cut", "Copy, then clear the region"),
            new Command("paste", "/paste [-a] [-s]", "Paste at pos1 (-a: skip air, -s: select what was pasted)"),
            new Command("rotate", "/rotate <90|180|270>", "Turn the clipboard clockwise (seen from above)"),
            new Command("flip", "/flip [dir]", "Mirror the clipboard along a direction (default: where you look)"),
            new Command("expand", "/expand <n> [dir] | /expand vert", "Grow the region that way"),
            new Command("contract", "/contract <n> [dir]", "Pull that side of the region in"),
            new Command("shift", "/shift <n> [dir]", "Move the region (not its blocks)"),
            new Command("outset", "/outset <n>", "Grow every side"),
            new Command("inset", "/inset <n>", "Shrink every side"),
            new Command("line", "/line <pattern> [thickness]", "A line from pos1 to pos2"),
            new Command("sphere", "/sphere <pattern> <radius>", "Solid sphere around pos1 (/hsphere: hollow)"),
            new Command("hsphere", "/hsphere <pattern> <radius>", "Hollow sphere around pos1"),
            new Command("cyl", "/cyl <pattern> <radius> [height]", "Solid cylinder up from pos1 (/hcyl: hollow)"),
            new Command("hcyl", "/hcyl <pattern> <radius> [height]", "Hollow cylinder up from pos1"),
            new Command("pyramid", "/pyramid <pattern> <size>", "Solid pyramid on pos1 (/hpyramid: hollow)"),
            new Command("hpyramid", "/hpyramid <pattern> <size>", "Hollow pyramid on pos1"),
            new Command("smooth", "/smooth [passes]", "Smooth the terrain's surface in the region (heightmap)"),
            new Command("naturalize", "/naturalize", "Grass on top, three dirt, then stone (only stone / dirt / grass change)"),
            new Command("hollow", "/hollow [thickness] [pattern]", "Hollow out solid shapes, keeping a shell (default 1)"),
            new Command("center", "/center <pattern>", "Mark the middle of the region (1–2 blocks per axis)"),
            new Command("undo", "/undo", "Undo the last change"),
            new Command("redo", "/redo", "Redo"),
            new Command("help", "/help [command]", "List the commands"));

    /** A command added from outside the editor (by a plugin). */
    @FunctionalInterface
    public interface Extension {
        /**
         * @param args   arguments after the command name, flags removed
         * @param flags  single-letter flags given as {@code -x}, lower case
         * @param region the selected region, or null when no corner is set
         */
        Result run(List<String> args, List<String> flags, Context c, Box region);
    }

    private static final Map<String, Extension> EXTENSIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<Command> EXTRA_COMMANDS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Adds a command (name without the slash). Names of built-in commands and their aliases can't be taken. */
    public static void register(Command command, Extension handler) {
        String n = command.name().toLowerCase(Locale.ROOT);
        if (ALIASES.containsKey(n) || COMMANDS.stream().anyMatch(c -> c.name().equals(n)) || EXTENSIONS.containsKey(n)) {
            throw new IllegalArgumentException("The command /" + n + " already exists");
        }
        EXTENSIONS.put(n, handler);
        EXTRA_COMMANDS.add(command);
    }

    public static void unregister(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        EXTENSIONS.remove(n);
        EXTRA_COMMANDS.removeIf(c -> c.name().equals(n));
    }

    /** Built-in commands followed by plugin commands, for help and completion. */
    public static List<Command> commands() {
        if (EXTRA_COMMANDS.isEmpty()) return COMMANDS;
        List<Command> all = new ArrayList<>(COMMANDS);
        all.addAll(EXTRA_COMMANDS);
        return all;
    }

    private static final Map<String, String> ALIASES = Map.of("outline", "faces", "desel", "sel", "deselect", "sel",
            "hpos1", "pos1", "hpos2", "pos2", "cls", "sel", "?", "help");

    private BlockPos pos1, pos2;
    private Map<BlockPos, BlockState> clipboard;
    private final Random random;

    public WorldEdit() {
        this(new Random());
    }

    public WorldEdit(Random random) {
        this.random = random;
    }

    public BlockPos pos1() {
        return pos1;
    }

    public BlockPos pos2() {
        return pos2;
    }

    public void setPos1(BlockPos p) {
        pos1 = p;
    }

    public void setPos2(BlockPos p) {
        pos2 = p;
    }

    public void clear() {
        pos1 = pos2 = null;
    }

    /** The region, from one or both corners, or null when neither is set. */
    public Box region() {
        if (pos1 == null && pos2 == null) return null;
        BlockPos a = pos1 != null ? pos1 : pos2, b = pos2 != null ? pos2 : pos1;
        return Box.of(a, b);
    }

    public boolean hasClipboard() {
        return clipboard != null;
    }

    // ---- running commands ------------------------------------------------------------------------------------

    public Result run(String line, Context c) {
        String s = line.strip();
        while (s.startsWith("/")) s = s.substring(1);
        if (s.isEmpty()) return Result.error("Type a command, e.g. /set stone (/help lists them)");
        List<String> args = new ArrayList<>(List.of(s.split("\\s+")));
        String name = args.removeFirst().toLowerCase(Locale.ROOT);
        name = ALIASES.getOrDefault(name, name);
        List<String> flags = new ArrayList<>();
        args.removeIf(a -> {
            if (a.length() == 2 && a.charAt(0) == '-' && Character.isLetter(a.charAt(1))) {
                flags.add(a.substring(1).toLowerCase(Locale.ROOT));
                return true;
            }
            return false;
        });
        try {
            return switch (name) {
                case "pos1", "pos2" -> pos(name.equals("pos1"), args, c);
                case "sel" -> {
                    clear();
                    yield Result.region("Selection cleared.");
                }
                case "size" -> size();
                case "count" -> count(args, c);
                case "distr" -> distr(c);
                case "set" -> set(args, c);
                case "replace" -> replace(args, c);
                case "walls" -> shell(args, c, true);
                case "faces" -> shell(args, c, false);
                case "overlay" -> overlay(args, c);
                case "move" -> move(args, flags, c);
                case "stack" -> stack(args, flags, c);
                case "copy" -> copy(c, false);
                case "cut" -> copy(c, true);
                case "paste" -> paste(flags, c);
                case "rotate" -> rotate(args);
                case "flip" -> flip(args, c);
                case "expand", "contract", "shift" -> resize(name, args, c);
                case "outset", "inset" -> outset(name.equals("outset"), args);
                case "line" -> line(args, c);
                case "smooth" -> smoothRegion(args, c);
                case "naturalize" -> naturalize(c);
                case "hollow" -> hollow(args, c);
                case "center", "centre" -> center(args, c);
                case "sphere", "hsphere" -> sphere(args, c, name.startsWith("h"));
                case "cyl", "hcyl" -> cyl(args, c, name.startsWith("h"));
                case "pyramid", "hpyramid" -> pyramid(args, c, name.startsWith("h"));
                case "undo" -> new Result(true, "Undone.", 0, false, Special.UNDO);
                case "redo" -> new Result(true, "Redone.", 0, false, Special.REDO);
                case "help" -> help(args);
                default -> {
                    Extension ext = EXTENSIONS.get(name);
                    yield ext != null ? ext.run(args, flags, c, region()) : Result.error("Unknown command /" + name + " · /help lists them");
                }
            };
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private Result help(List<String> args) {
        if (!args.isEmpty()) {
            String n = ALIASES.getOrDefault(args.getFirst().replace("/", ""), args.getFirst().replace("/", ""));
            for (Command cmd : commands()) if (cmd.name().equals(n)) return Result.ok(cmd.usage() + " · " + cmd.description(), 0);
            return Result.error("No command /" + n);
        }
        StringBuilder sb = new StringBuilder("Commands:");
        for (Command cmd : commands()) sb.append(" /").append(cmd.name());
        return Result.ok(sb.toString(), 0);
    }

    private Box needRegion() {
        if (pos1 == null || pos2 == null) {
            throw new IllegalArgumentException(pos1 == null && pos2 == null
                    ? "Make a region first: left-click pos1, right-click pos2 (Select mode)" : "Set both corners: left-click pos1, right-click pos2");
        }
        Box b = region();
        if (b.volume() > MAX_VOLUME) throw new IllegalArgumentException(String.format("Region too big (%,d blocks, limit %,d)", b.volume(), MAX_VOLUME));
        return b;
    }

    private BlockPos needPos1() {
        if (pos1 == null) throw new IllegalArgumentException("Set pos1 first (left-click in Select mode)");
        return pos1;
    }

    private Result pos(boolean first, List<String> args, Context c) {
        BlockPos p;
        if (args.size() == 3 || args.size() == 1 && args.getFirst().split(",").length == 3) {
            String[] xyz = args.size() == 3 ? args.toArray(new String[0]) : args.getFirst().split(",");
            p = new BlockPos(integer(xyz[0]), integer(xyz[1]), integer(xyz[2]));
        } else if (c.aim() != null) {
            p = c.aim();
        } else {
            return Result.error("Aim at a block (or give x y z)");
        }
        if (first) pos1 = p;
        else pos2 = p;
        Box b = region();
        return Result.region((first ? "First" : "Second") + " position set to " + p + (pos1 != null && pos2 != null
                ? String.format(" (%,d blocks).", b.volume()) : "."));
    }

    private Result size() {
        Box b = needRegion();
        return Result.ok(String.format("Region %d×%d×%d = %,d blocks · pos1 %s · pos2 %s", b.sizeX(), b.sizeY(), b.sizeZ(), b.volume(), pos1, pos2), 0);
    }

    private Result count(List<String> args, Context c) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /count <mask>");
        Box b = needRegion();
        Predicate<BlockState> mask = mask(String.join(" ", args), c);
        long n = 0;
        for (BlockPos p : cells(b)) if (mask.test(c.world().get(p))) n++;
        return Result.ok(String.format("Counted: %,d", n), 0);
    }

    private Result distr(Context c) {
        Box b = needRegion();
        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        for (BlockPos p : cells(b)) {
            BlockState s = c.world().get(p);
            if (s.isAir()) continue;
            counts.merge(s.name(), 1L, Long::sum);
            total++;
        }
        if (total == 0) return Result.ok("The region is empty.", 0);
        StringBuilder sb = new StringBuilder(String.format("%,d blocks:", total));
        long t = total;
        counts.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(8).forEach(e ->
                sb.append(String.format("  %s %.1f%%", e.getKey().replace("minecraft:", ""), 100.0 * e.getValue() / t)));
        if (counts.size() > 8) sb.append("  …");
        return Result.ok(sb.toString(), 0);
    }

    private Result set(List<String> args, Context c) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /set <pattern>");
        Box b = needRegion();
        Pattern pat = pattern(String.join(" ", args), c);
        int n = 0;
        for (BlockPos p : cells(b)) n += put(c, p, pat.next());
        return done(n);
    }

    private Result replace(List<String> args, Context c) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /replace [from] <to>");
        Box b = needRegion();
        Predicate<BlockState> from = args.size() >= 2 ? mask(args.getFirst(), c) : s -> !s.isAir();
        Pattern to = pattern(args.getLast(), c);
        int n = 0;
        for (BlockPos p : cells(b)) if (from.test(c.world().get(p))) n += put(c, p, to.next());
        return done(n);
    }

    private Result shell(List<String> args, Context c, boolean wallsOnly) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /" + (wallsOnly ? "walls" : "faces") + " <pattern>");
        Box b = needRegion();
        Pattern pat = pattern(String.join(" ", args), c);
        int n = 0;
        for (BlockPos p : cells(b)) {
            boolean side = p.x() == b.minX() || p.x() == b.maxX() || p.z() == b.minZ() || p.z() == b.maxZ();
            boolean cap = p.y() == b.minY() || p.y() == b.maxY();
            if (side || !wallsOnly && cap) n += put(c, p, pat.next());
        }
        return done(n);
    }

    private Result overlay(List<String> args, Context c) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /overlay <pattern>");
        Box b = needRegion();
        Pattern pat = pattern(String.join(" ", args), c);
        int n = 0;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.maxY(); y >= b.minY(); y--) {
                    if (c.world().get(new BlockPos(x, y, z)).isAir()) continue;
                    BlockPos above = new BlockPos(x, y + 1, z);
                    if (c.world().get(above).isAir()) n += put(c, above, pat.next());
                    break;
                }
            }
        }
        return done(n);
    }

    private Result move(List<String> args, List<String> flags, Context c) {
        Box b = needRegion();
        int dist = args.isEmpty() ? 1 : integer(args.getFirst());
        Dir d = direction(args.size() >= 2 ? args.get(1) : null, c);
        boolean skipAir = flags.contains("a");
        Map<BlockPos, BlockState> contents = read(b, c);
        int n = 0;
        for (BlockPos p : contents.keySet()) n += put(c, p, BlockState.AIR);
        for (var e : contents.entrySet()) {
            if (skipAir && e.getValue().isAir()) continue;
            n += put(c, e.getKey().add(d.x * dist, d.y * dist, d.z * dist), e.getValue());
        }
        pos1 = pos1.add(d.x * dist, d.y * dist, d.z * dist);
        pos2 = pos2.add(d.x * dist, d.y * dist, d.z * dist);
        return new Result(true, String.format("Moved %d %s · %,d blocks changed.", dist, d.id(), n), n, true, Special.NONE);
    }

    private Result stack(List<String> args, List<String> flags, Context c) {
        Box b = needRegion();
        int count = args.isEmpty() ? 1 : integer(args.getFirst());
        if (count < 1 || count > 256) throw new IllegalArgumentException("Stack count must be 1–256");
        Dir d = direction(args.size() >= 2 ? args.get(1) : null, c);
        int step = d.x != 0 ? b.sizeX() : d.y != 0 ? b.sizeY() : b.sizeZ();
        if ((long) count * b.volume() > MAX_VOLUME) throw new IllegalArgumentException("That stack is too big");
        boolean skipAir = flags.contains("a");
        Map<BlockPos, BlockState> contents = read(b, c);
        int n = 0;
        for (int i = 1; i <= count; i++) {
            int o = step * i;
            for (var e : contents.entrySet()) {
                if (skipAir && e.getValue().isAir()) continue;
                n += put(c, e.getKey().add(d.x * o, d.y * o, d.z * o), e.getValue());
            }
        }
        return done(n);
    }

    private Result copy(Context c, boolean cut) {
        Box b = needRegion();
        Map<BlockPos, BlockState> cb = new LinkedHashMap<>();
        int n = 0;
        for (BlockPos p : cells(b)) cb.put(p.subtract(pos1), c.world().get(p));
        if (cut) for (BlockPos p : cells(b)) n += put(c, p, BlockState.AIR);
        clipboard = cb;
        return cut ? new Result(true, String.format("%,d blocks cut.", b.volume()), n, false, Special.NONE)
                : Result.ok(String.format("%,d blocks copied (relative to pos1). Set pos1 somewhere and /paste.", b.volume()), 0);
    }

    private Result paste(List<String> flags, Context c) {
        if (clipboard == null) throw new IllegalArgumentException("The clipboard is empty: /copy first");
        BlockPos at = needPos1();
        boolean skipAir = flags.contains("a");
        int n = 0;
        Box pasted = null;
        for (var e : clipboard.entrySet()) {
            BlockPos p = at.add(e.getKey());
            pasted = pasted == null ? Box.of(p, p) : pasted.union(Box.of(p, p));
            if (skipAir && e.getValue().isAir()) continue;
            n += put(c, p, e.getValue());
        }
        boolean select = flags.contains("s") && pasted != null;
        if (select) {
            pos1 = pasted.min();
            pos2 = pasted.max();
        }
        return new Result(true, String.format("Pasted · %,d blocks changed.", n), n, select, Special.NONE);
    }

    private Result rotate(List<String> args) {
        if (clipboard == null) throw new IllegalArgumentException("The clipboard is empty: /copy first");
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /rotate <90|180|270>");
        int deg = integer(args.getFirst());
        if (deg % 90 != 0) throw new IllegalArgumentException("Rotate by 90, 180 or 270");
        clipboard = transformed(Transform.rotation(Math.floorMod(deg / 90, 4)));
        return Result.ok("Clipboard rotated " + deg + "°.", 0);
    }

    private Result flip(List<String> args, Context c) {
        if (clipboard == null) throw new IllegalArgumentException("The clipboard is empty: /copy first");
        Dir d = direction(args.isEmpty() ? null : args.getFirst(), c);
        if (d.y != 0) {
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            for (var e : clipboard.entrySet()) {
                BlockState s = e.getValue();
                if ("top".equals(s.get("half"))) s = s.with("half", "bottom");
                else if ("bottom".equals(s.get("half"))) s = s.with("half", "top");
                if ("top".equals(s.get("type"))) s = s.with("type", "bottom");
                else if ("bottom".equals(s.get("type"))) s = s.with("type", "top");
                if ("up".equals(s.get("facing"))) s = s.with("facing", "down");
                else if ("down".equals(s.get("facing"))) s = s.with("facing", "up");
                BlockPos p = e.getKey();
                out.put(new BlockPos(p.x(), -p.y(), p.z()), s);
            }
            clipboard = out;
        } else {
            clipboard = transformed(new Transform(0, d.x != 0 ? Transform.Mirror.X : Transform.Mirror.Z));
        }
        return Result.ok("Clipboard flipped " + d.id() + ".", 0);
    }

    private Map<BlockPos, BlockState> transformed(Transform t) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        BlockTransformer bt = BlockTransformer.defaults();
        Map<BlockState, BlockState> cache = new HashMap<>();
        for (var e : clipboard.entrySet()) out.put(t.apply(e.getKey()), cache.computeIfAbsent(e.getValue(), s -> bt.apply(s, t)));
        return out;
    }

    private Result resize(String op, List<String> args, Context c) {
        if (pos1 == null || pos2 == null) needRegion();
        if (op.equals("expand") && !args.isEmpty() && args.getFirst().toLowerCase(Locale.ROOT).startsWith("vert")) {
            int lo = -64, hi = 319;
            if (pos1.y() <= pos2.y()) {
                pos1 = new BlockPos(pos1.x(), lo, pos1.z());
                pos2 = new BlockPos(pos2.x(), hi, pos2.z());
            } else {
                pos2 = new BlockPos(pos2.x(), lo, pos2.z());
                pos1 = new BlockPos(pos1.x(), hi, pos1.z());
            }
            return Result.region("Region expanded from Y " + lo + " to " + hi + ".");
        }
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /" + op + " <n> [dir]");
        int n = integer(args.getFirst());
        Dir d = direction(args.size() >= 2 ? args.get(1) : null, c);
        if (op.equals("shift")) {
            pos1 = pos1.add(d.x * n, d.y * n, d.z * n);
            pos2 = pos2.add(d.x * n, d.y * n, d.z * n);
            return Result.region("Region shifted " + n + " " + d.id() + ".");
        }
        // The corner lying furthest that way moves: out for expand, back in for contract.
        int axis = d.x != 0 ? 0 : d.y != 0 ? 1 : 2, sign = d.x + d.y + d.z;
        boolean firstIsFar = sign * (coord(pos1, axis) - coord(pos2, axis)) >= 0;
        int delta = op.equals("expand") ? n * sign : -n * sign;
        if (op.equals("contract") && Math.abs(coord(pos1, axis) - coord(pos2, axis)) < n) {
            throw new IllegalArgumentException("The region is not that big");
        }
        if (firstIsFar) pos1 = withCoord(pos1, axis, coord(pos1, axis) + delta);
        else pos2 = withCoord(pos2, axis, coord(pos2, axis) + delta);
        Box b = region();
        return Result.region(String.format("Region %s %d %s · now %d×%d×%d.", op.equals("expand") ? "expanded" : "contracted", n, d.id(), b.sizeX(), b.sizeY(), b.sizeZ()));
    }

    private Result outset(boolean out, List<String> args) {
        Box b = needRegion();
        int n = args.isEmpty() ? 1 : integer(args.getFirst());
        int k = out ? n : -n;
        if (!out && (b.sizeX() <= 2 * n || b.sizeY() <= 2 * n || b.sizeZ() <= 2 * n)) throw new IllegalArgumentException("The region is not that big");
        pos1 = new BlockPos(b.minX() - k, b.minY() - k, b.minZ() - k);
        pos2 = new BlockPos(b.maxX() + k, b.maxY() + k, b.maxZ() + k);
        return Result.region("Region " + (out ? "outset" : "inset") + " by " + n + ".");
    }

    /** WorldEdit's /smooth: blurs the heightmap of the region's columns and raises / lowers them to match. */
    private Result smoothRegion(List<String> args, Context c) {
        Box b = needRegion();
        int passes = args.isEmpty() ? 1 : integer(args.getFirst());
        if (passes < 1 || passes > 50) throw new IllegalArgumentException("Passes must be 1–50");
        int sx = b.sizeX(), sz = b.sizeZ();
        double[] h = new double[sx * sz];
        for (int x = 0; x < sx; x++)
            for (int z = 0; z < sz; z++) {
                h[x * sz + z] = Double.NaN;
                for (int y = b.maxY(); y >= b.minY(); y--) {
                    if (!c.world().get(new BlockPos(b.minX() + x, y, b.minZ() + z)).isAir()) {
                        h[x * sz + z] = y;
                        break;
                    }
                }
            }
        double[] cur = h.clone();
        for (int p = 0; p < passes; p++) {
            double[] nxt = cur.clone();
            for (int x = 0; x < sx; x++)
                for (int z = 0; z < sz; z++) {
                    if (Double.isNaN(cur[x * sz + z])) continue;
                    double t = 0, wsum = 0;
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            int xx = x + dx, zz = z + dz;
                            if (xx < 0 || zz < 0 || xx >= sx || zz >= sz || Double.isNaN(cur[xx * sz + zz])) continue;
                            double wgt = (2 - Math.abs(dx)) * (2 - Math.abs(dz));
                            t += cur[xx * sz + zz] * wgt;
                            wsum += wgt;
                        }
                    nxt[x * sz + z] = t / wsum;
                }
            cur = nxt;
        }
        int n = 0;
        for (int x = 0; x < sx; x++)
            for (int z = 0; z < sz; z++) {
                if (Double.isNaN(h[x * sz + z])) continue;
                int from = (int) h[x * sz + z], to = Math.clamp(Math.round(cur[x * sz + z]), b.minY(), b.maxY());
                if (to == from) continue;
                int cx = b.minX() + x, cz = b.minZ() + z;
                BlockState top = c.world().get(new BlockPos(cx, from, cz)), below = c.world().get(new BlockPos(cx, from - 1, cz));
                BlockState fill = below.isAir() ? top : below;
                if (to > from) for (int y = from; y < to; y++) n += put(c, new BlockPos(cx, y, cz), fill);
                else for (int y = from; y > to; y--) n += put(c, new BlockPos(cx, y, cz), BlockState.AIR);
                n += put(c, new BlockPos(cx, to, cz), top);
            }
        return done(n);
    }

    private static final java.util.Set<String> NATURAL = java.util.Set.of("minecraft:stone", "minecraft:dirt", "minecraft:grass_block",
            "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt");

    /** WorldEdit's /naturalize: by depth below each column's surface, grass, three dirt, then stone. */
    private Result naturalize(Context c) {
        Box b = needRegion();
        BlockState grass = BlockState.of("grass_block").withProperties(Map.of("snowy", "false")), dirt = BlockState.of("dirt"), stone = BlockState.of("stone");
        int n = 0;
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                int depth = -1;
                for (int y = b.maxY(); y >= b.minY(); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = c.world().get(p);
                    if (s.isAir()) {
                        depth = -1;
                        continue;
                    }
                    depth++;
                    if (!NATURAL.contains(s.name())) continue;
                    n += put(c, p, depth == 0 ? grass : depth <= 3 ? dirt : stone);
                }
            }
        return done(n);
    }

    /** WorldEdit's /hollow: keeps a shell of {@code thickness} around solid shapes and clears (or fills) the inside. */
    private Result hollow(List<String> args, Context c) {
        Box b = needRegion();
        int thick = args.isEmpty() ? 1 : integer(args.getFirst());
        if (thick < 1 || thick > 32) throw new IllegalArgumentException("Thickness must be 1–32");
        Pattern inside = args.size() >= 2 ? pattern(args.get(1), c) : () -> BlockState.AIR;
        // Distance (in face steps) from air or the region's outside, by breadth-first search.
        Map<BlockPos, Integer> dist = new HashMap<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        for (BlockPos p : cells(b)) {
            if (c.world().get(p).isAir()) continue;
            boolean edge = false;
            for (int[] f : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos q = p.add(f[0], f[1], f[2]);
                if (!b.contains(q.x(), q.y(), q.z()) || c.world().get(q).isAir()) {
                    edge = true;
                    break;
                }
            }
            if (edge) {
                dist.put(p, 1);
                queue.add(p);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int d = dist.get(p);
            for (int[] f : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos q = p.add(f[0], f[1], f[2]);
                if (!b.contains(q.x(), q.y(), q.z()) || dist.containsKey(q) || c.world().get(q).isAir()) continue;
                dist.put(q, d + 1);
                queue.add(q);
            }
        }
        int n = 0;
        for (var e : dist.entrySet()) if (e.getValue() > thick) n += put(c, e.getKey(), inside.next());
        return done(n);
    }

    /** WorldEdit's /center: the middle block of the region (two per axis when that side is even). */
    private Result center(List<String> args, Context c) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /center <pattern>");
        Box b = needRegion();
        Pattern pat = pattern(String.join(" ", args), c);
        int n = 0;
        for (int x = (b.minX() + b.maxX()) / 2; x <= (b.minX() + b.maxX() + 1) / 2; x++)
            for (int y = (b.minY() + b.maxY()) / 2; y <= (b.minY() + b.maxY() + 1) / 2; y++)
                for (int z = (b.minZ() + b.maxZ()) / 2; z <= (b.minZ() + b.maxZ() + 1) / 2; z++) n += put(c, new BlockPos(x, y, z), pat.next());
        return done(n);
    }

    private Result line(List<String> args, Context c) {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /line <pattern> [thickness]");
        if (pos1 == null || pos2 == null) needRegion();
        Pattern pat = pattern(args.getFirst(), c);
        int thick = args.size() >= 2 ? integer(args.get(1)) : 0;
        java.util.Set<BlockPos> cells = new java.util.LinkedHashSet<>();
        int dx = pos2.x() - pos1.x(), dy = pos2.y() - pos1.y(), dz = pos2.z() - pos1.z();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            BlockPos p = new BlockPos((int) Math.round(pos1.x() + dx * t), (int) Math.round(pos1.y() + dy * t), (int) Math.round(pos1.z() + dz * t));
            for (int x = -thick; x <= thick; x++)
                for (int y = -thick; y <= thick; y++)
                    for (int z = -thick; z <= thick; z++)
                        if (x * x + y * y + z * z <= thick * thick) cells.add(p.add(x, y, z));
        }
        int n = 0;
        for (BlockPos p : cells) n += put(c, p, pat.next());
        return done(n);
    }

    private Result sphere(List<String> args, Context c, boolean hollow) {
        if (args.size() < 2) throw new IllegalArgumentException("Usage: /" + (hollow ? "h" : "") + "sphere <pattern> <radius>");
        BlockPos o = needPos1();
        Pattern pat = pattern(args.getFirst(), c);
        double r = number(args.get(1));
        if (r < 0.5 || r > 150) throw new IllegalArgumentException("Radius must be 0.5–150");
        int ri = (int) Math.ceil(r);
        int n = 0;
        for (int x = -ri; x <= ri; x++)
            for (int y = -ri; y <= ri; y++)
                for (int z = -ri; z <= ri; z++) {
                    if (!inSphere(x, y, z, r)) continue;
                    // Hollow keeps only cells with a neighbour outside the sphere.
                    if (hollow && inSphere(x + 1, y, z, r) && inSphere(x - 1, y, z, r) && inSphere(x, y + 1, z, r)
                            && inSphere(x, y - 1, z, r) && inSphere(x, y, z + 1, r) && inSphere(x, y, z - 1, r)) continue;
                    n += put(c, o.add(x, y, z), pat.next());
                }
        return done(n);
    }

    private static boolean inSphere(int x, int y, int z, double r) {
        return x * x + y * y + z * z <= (r + 0.5) * (r + 0.5) - 0.25;
    }

    private Result cyl(List<String> args, Context c, boolean hollow) {
        if (args.size() < 2) throw new IllegalArgumentException("Usage: /" + (hollow ? "h" : "") + "cyl <pattern> <radius> [height]");
        BlockPos o = needPos1();
        Pattern pat = pattern(args.getFirst(), c);
        double r = number(args.get(1));
        int h = args.size() >= 3 ? integer(args.get(2)) : 1;
        if (r < 0.5 || r > 150 || h < 1 || h > 384) throw new IllegalArgumentException("Radius 0.5–150, height 1–384");
        int ri = (int) Math.ceil(r);
        int n = 0;
        for (int x = -ri; x <= ri; x++)
            for (int z = -ri; z <= ri; z++) {
                if (!inSphere(x, 0, z, r)) continue;
                if (hollow && inSphere(x + 1, 0, z, r) && inSphere(x - 1, 0, z, r) && inSphere(x, 0, z + 1, r) && inSphere(x, 0, z - 1, r)) continue;
                for (int y = 0; y < h; y++) n += put(c, o.add(x, y, z), pat.next());
            }
        return done(n);
    }

    private Result pyramid(List<String> args, Context c, boolean hollow) {
        if (args.size() < 2) throw new IllegalArgumentException("Usage: /" + (hollow ? "h" : "") + "pyramid <pattern> <size>");
        BlockPos o = needPos1();
        Pattern pat = pattern(args.getFirst(), c);
        int size = integer(args.get(1));
        if (size < 1 || size > 150) throw new IllegalArgumentException("Size must be 1–150");
        int n = 0;
        for (int y = 0; y < size; y++) {
            int half = size - 1 - y;
            for (int x = -half; x <= half; x++)
                for (int z = -half; z <= half; z++) {
                    boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                    if (hollow && !edge && y != 0) continue;
                    n += put(c, o.add(x, y, z), pat.next());
                }
        }
        return done(n);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static Result done(int n) {
        return Result.ok(String.format("Operation completed (%,d block%s affected).", n, n == 1 ? "" : "s"), n);
    }

    private static int put(Context c, BlockPos p, BlockState s) {
        if (c.world().get(p) == s) return 0;
        c.world().set(p, s);
        return 1;
    }

    private static Iterable<BlockPos> cells(Box b) {
        return () -> new java.util.Iterator<>() {
            int x = b.minX(), y = b.minY(), z = b.minZ();
            boolean more = true;

            public boolean hasNext() {
                return more;
            }

            public BlockPos next() {
                BlockPos p = new BlockPos(x, y, z);
                if (++x > b.maxX()) {
                    x = b.minX();
                    if (++z > b.maxZ()) {
                        z = b.minZ();
                        if (++y > b.maxY()) more = false;
                    }
                }
                return p;
            }
        };
    }

    private static Map<BlockPos, BlockState> read(Box b, Context c) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (BlockPos p : cells(b)) out.put(p, c.world().get(p));
        return out;
    }

    private static int coord(BlockPos p, int axis) {
        return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
    }

    private static BlockPos withCoord(BlockPos p, int axis, int v) {
        return axis == 0 ? new BlockPos(v, p.y(), p.z()) : axis == 1 ? new BlockPos(p.x(), v, p.z()) : new BlockPos(p.x(), p.y(), v);
    }

    private static int integer(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a whole number");
        }
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a number");
        }
    }

    /** me / (none) = where you look; up, down, north…; forward, back, left, right relative to the look. */
    static Dir direction(String s, Context c) {
        Dir look = c.look() != null ? c.look() : Dir.NORTH;
        if (s == null || s.equalsIgnoreCase("me")) return look;
        Dir flat = look.horizontal() ? look : Dir.NORTH;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "u", "up" -> Dir.UP;
            case "d", "down" -> Dir.DOWN;
            case "n", "north" -> Dir.NORTH;
            case "s", "south" -> Dir.SOUTH;
            case "e", "east" -> Dir.EAST;
            case "w", "west" -> Dir.WEST;
            case "f", "forward" -> look;
            case "b", "back" -> look.opposite();
            case "l", "left" -> flat.counterClockWise();
            case "r", "right" -> flat.clockWise();
            default -> throw new IllegalArgumentException("Unknown direction '" + s + "' (up, down, north, east, south, west, me, left, right…)");
        };
    }

    /** A block source: a single block, or a weighted mix like {@code 70%stone,30%andesite}. */
    interface Pattern {
        BlockState next();
    }

    Pattern pattern(String text, Context c) {
        List<BlockState> blocks = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String part : splitTop(text)) {
            double w = 1;
            String b = part.strip();
            int pct = b.indexOf('%');
            if (pct > 0) {
                w = number(b.substring(0, pct));
                b = b.substring(pct + 1);
            }
            blocks.add(block(b, c));
            weights.add(w);
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("No block given");
        if (blocks.size() == 1) {
            BlockState only = blocks.getFirst();
            return () -> only;
        }
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        return () -> {
            double r = random.nextDouble() * total;
            for (int i = 0; i < blocks.size(); i++) {
                r -= weights.get(i);
                if (r <= 0) return blocks.get(i);
            }
            return blocks.getLast();
        };
    }

    /** Blocks to match: a comma list; a name alone matches any state of it, [props] must all match; ! negates. */
    Predicate<BlockState> mask(String text, Context c) {
        String t = text.strip();
        boolean not = t.startsWith("!");
        if (not) t = t.substring(1);
        List<Predicate<BlockState>> any = new ArrayList<>();
        for (String part : splitTop(t)) {
            String p = part.strip();
            if (p.equals("*") || p.equalsIgnoreCase("#existing")) {
                any.add(s -> !s.isAir());
                continue;
            }
            BlockState want = block(p, c);
            if (want.isAir()) {
                any.add(BlockState::isAir);
            } else if (p.contains("[")) {
                any.add(s -> s.name().equals(want.name()) && s.properties().entrySet().containsAll(BlockState.parse(p.contains(":") ? p : "minecraft:" + p).properties().entrySet()));
            } else {
                any.add(s -> s.name().equals(want.name()));
            }
        }
        Predicate<BlockState> m = s -> any.stream().anyMatch(x -> x.test(s));
        return not ? m.negate() : m;
    }

    private static BlockState block(String name, Context c) {
        String n = name.strip();
        if (n.equalsIgnoreCase("hand")) {
            if (c.hand() == null) throw new IllegalArgumentException("Your hand is empty");
            return c.hand();
        }
        if (n.equalsIgnoreCase("air") || n.equals("0")) return BlockState.AIR;
        BlockState s = c.resolve() != null ? c.resolve().apply(n) : null;
        if (s == null) throw new IllegalArgumentException("Unknown block '" + n + "'");
        return s;
    }

    /** Splits on commas that are not inside [ ]. */
    private static List<String> splitTop(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') depth--;
            else if (ch == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start));
        out.removeIf(String::isBlank);
        return out;
    }
}
