package io.blockdesigner.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.blockdesigner.ai.provider.AiProvider.ToolSpec;
import io.blockdesigner.assets.BlockRegistry;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.transform.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

import static io.blockdesigner.ai.tools.Schema.array;
import static io.blockdesigner.ai.tools.Schema.bool;
import static io.blockdesigner.ai.tools.Schema.enumOf;
import static io.blockdesigner.ai.tools.Schema.integer;
import static io.blockdesigner.ai.tools.Schema.object;
import static io.blockdesigner.ai.tools.Schema.pos;
import static io.blockdesigner.ai.tools.Schema.str;

/**
 * The tools an AI model uses to inspect and build. All coordinates are world coordinates (Y up, north = -Z,
 * east = +X); edits go into the active layer, converted to its local space. Each call is one undoable edit; the
 * agent groups a whole turn into a single undo step.
 */
public final class BuildTools {
    /** Largest number of blocks one call may touch. */
    public static final int MAX_VOLUME = 262_144;

    /** Result of a tool call. {@code image} completes with PNG bytes for render_view, otherwise null. */
    public record Outcome(String text, CompletableFuture<byte[]> image, boolean error) {
        static Outcome ok(String text) {
            return new Outcome(text, null, false);
        }

        static Outcome fail(String text) {
            return new Outcome(text, null, true);
        }
    }

    /** Thrown for invalid arguments; becomes an error result the model can react to. */
    static final class ToolError extends RuntimeException {
        ToolError(String msg) {
            super(msg);
        }
    }

    private final BuildContext ctx;
    private final BlockTransformer transformer = BlockTransformer.defaults();

    public BuildTools(BuildContext ctx) {
        this.ctx = ctx;
    }

    // ---- tool definitions ---------------------------------------------------------------------------------

    public static List<ToolSpec> specs() {
        List<ToolSpec> t = new ArrayList<>();
        JsonNode block = str("Block state, e.g. \"minecraft:oak_planks\", \"spruce_stairs[facing=east,half=bottom]\", \"create:andesite_casing\". Namespace defaults to minecraft; omitted properties use defaults.");
        t.add(new ToolSpec("get_build_info",
                "Summary of the scene: layers (id, name, block count, world bounds, which is active), the most used blocks in the active layer, and the target Minecraft version. Call this first.",
                object().build()));
        t.add(new ToolSpec("get_layer_slice",
                "ASCII top-down map of one horizontal slice (single Y level) of the visible layers, with a legend. Rows run north (top) to south; columns west to east. Region is clipped to 64x64.",
                object().req("y", integer("World Y level"))
                        .opt("from", pos("Optional min corner of the area")).opt("to", pos("Optional max corner of the area")).build()));
        t.add(new ToolSpec("get_region",
                "List every non-air block in a box (world coordinates), one per line as 'x y z block'. Limited to 1500 blocks.",
                object().req("from", pos("Corner")).req("to", pos("Opposite corner")).build()));
        t.add(new ToolSpec("search_blocks",
                "Find block ids by name, including modded blocks (e.g. 'andesite', 'create:shaft', 'glass pane'). Only blocks returned here or known vanilla ids will render.",
                object().req("query", str("Search text")).opt("limit", integer("Max results (default 20)")).build()));
        t.add(new ToolSpec("get_block_properties",
                "Properties a block supports and their allowed values (e.g. facing, half, shape, axis, waterlogged).",
                object().req("block", str("Block id")).build()));
        t.add(new ToolSpec("set_blocks",
                "Place individual blocks. Use for details (doors, torches, windows, decorations). Use \"minecraft:air\" to remove.",
                object().req("blocks", array("Blocks to place",
                        object().req("pos", pos("Position")).req("block", block).build())).build()));
        t.add(new ToolSpec("fill",
                "Fill a box between two corners (inclusive). mode: solid (default), hollow (shell only, interior cleared to air), outline (shell only, interior untouched), walls (four vertical sides only, no floor/ceiling). Optionally only replace air.",
                object().req("from", pos("Corner")).req("to", pos("Opposite corner")).req("block", block)
                        .opt("mode", enumOf("Fill mode", "solid", "hollow", "outline", "walls"))
                        .opt("only_air", bool("Only place where there is currently air")).build()));
        t.add(new ToolSpec("line",
                "Straight line of blocks between two points (3D Bresenham). Good for beams, fences, supports.",
                object().req("from", pos("Start")).req("to", pos("End")).req("block", block).build()));
        t.add(new ToolSpec("sphere",
                "Sphere or dome. part: full, top (dome), bottom (bowl).",
                object().req("center", pos("Centre block")).req("radius", integer("Radius in blocks (1-48)")).req("block", block)
                        .opt("hollow", bool("Shell only")).opt("part", enumOf("Which part", "full", "top", "bottom")).build()));
        t.add(new ToolSpec("cylinder",
                "Cylinder standing on a base centre, extending along an axis (default y = vertical). Good for towers, silos, pillars.",
                object().req("base", pos("Centre of the bottom disc")).req("radius", integer("Radius (1-48)")).req("height", integer("Length along the axis"))
                        .req("block", block).opt("hollow", bool("Wall only")).opt("axis", enumOf("Axis", "x", "y", "z")).build()));
        t.add(new ToolSpec("roof",
                "Build a roof over a rectangular footprint from stair blocks. style: gable (two slopes; ridge along 'ridge_axis'), hip (slopes on all four sides), flat (one layer of slabs). The roof starts at from.y and rises one block per step.",
                object().req("from", pos("Footprint corner; y = roof base height")).req("to", pos("Opposite footprint corner (its y is ignored)"))
                        .req("stairs", str("Stair block id, e.g. dark_oak_stairs")).req("style", enumOf("Roof style", "gable", "hip", "flat"))
                        .opt("ridge_axis", enumOf("Gable ridge direction (default: the longer side)", "x", "z"))
                        .opt("overhang", integer("Blocks the roof extends past the footprint (0-3, default 1)"))
                        .opt("gable_fill", str("Block to fill the triangular gable ends (default: matching planks/full block)")).build()));
        t.add(new ToolSpec("replace",
                "Replace every block of one type with another inside a box. 'match' may be a bare id (any properties) or a full state.",
                object().req("from", pos("Corner")).req("to", pos("Opposite corner")).req("match", str("Block to replace"))
                        .req("block", block).build()));
        t.add(new ToolSpec("clear", "Set a box to air.", object().req("from", pos("Corner")).req("to", pos("Opposite corner")).build()));
        t.add(new ToolSpec("copy",
                "Copy a box and paste it with its minimum corner at 'dest', optionally rotated (quarter turns clockwise seen from above) and/or mirrored. Directional blocks are rotated correctly.",
                object().req("from", pos("Corner")).req("to", pos("Opposite corner")).req("dest", pos("Destination min corner"))
                        .opt("rotate", integer("Quarter turns clockwise (0-3)")).opt("mirror", enumOf("Mirror axis", "none", "x", "z"))
                        .opt("skip_air", bool("Don't overwrite with air (default true)")).build()));
        t.add(new ToolSpec("list_layers", "List layers with ids, names, offsets and bounds.", object().build()));
        t.add(new ToolSpec("create_layer",
                "Create a new empty layer and make it active. Put separate parts (a second building, a tower, a garden) on their own layers so the user can move them independently.",
                object().req("name", str("Layer name")).build()));
        t.add(new ToolSpec("set_active_layer", "Make an existing layer active (by id or name); later edits go there.",
                object().req("layer", str("Layer id or name")).build()));
        t.add(new ToolSpec("move_layer", "Move a layer (default: the active one) by a world offset.",
                object().req("dx", integer("X delta")).req("dy", integer("Y delta")).req("dz", integer("Z delta"))
                        .opt("layer", str("Layer id or name")).build()));
        t.add(new ToolSpec("render_view",
                "Render a screenshot of the current build so you can check how it looks. Use after major steps and before finishing. Views: iso_se (default), iso_sw, iso_ne, iso_nw, top, north, south, east, west.",
                object().opt("view", enumOf("Viewpoint", "iso_se", "iso_sw", "iso_ne", "iso_nw", "top", "north", "south", "east", "west")).build()));
        return t;
    }

    // ---- dispatch ------------------------------------------------------------------------------------------

    /** Runs a tool. Must be called on the scene's owning thread. */
    public Outcome execute(String name, JsonNode in) {
        try {
            if (in == null || !in.isObject()) throw new ToolError("Arguments must be a JSON object");
            return switch (name) {
                case "get_build_info" -> Outcome.ok(buildInfo());
                case "get_layer_slice" -> Outcome.ok(slice(in));
                case "get_region" -> Outcome.ok(region(in));
                case "search_blocks" -> Outcome.ok(search(in));
                case "get_block_properties" -> Outcome.ok(properties(in));
                case "set_blocks" -> Outcome.ok(setBlocks(in));
                case "fill" -> Outcome.ok(fill(in));
                case "line" -> Outcome.ok(line(in));
                case "sphere" -> Outcome.ok(sphere(in));
                case "cylinder" -> Outcome.ok(cylinder(in));
                case "roof" -> Outcome.ok(roof(in));
                case "replace" -> Outcome.ok(replace(in));
                case "clear" -> Outcome.ok(clear(in));
                case "copy" -> Outcome.ok(copy(in));
                case "list_layers" -> Outcome.ok(listLayers());
                case "create_layer" -> Outcome.ok(createLayer(in));
                case "set_active_layer" -> Outcome.ok(setActive(in));
                case "move_layer" -> Outcome.ok(moveLayer(in));
                case "render_view" -> {
                    String view = in.path("view").asText("iso_se");
                    yield new Outcome("Screenshot (" + view + ") of the current build:", ctx.renderView(view, 1024, 768), false);
                }
                default -> Outcome.fail("Unknown tool: " + name);
            };
        } catch (ToolError e) {
            return Outcome.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Outcome.fail("Tool failed: " + e);
        }
    }

    /** One-line human description of a call, for the chat's tool chips. */
    public static String describe(String name, JsonNode in) {
        try {
            return switch (name) {
                case "fill" -> "fill " + size(in) + " " + shortId(in.path("block").asText()) + modeSuffix(in);
                case "set_blocks" -> "place " + in.path("blocks").size() + " blocks";
                case "line" -> "line of " + shortId(in.path("block").asText());
                case "sphere" -> ("top".equals(in.path("part").asText()) ? "dome" : "sphere") + " r" + in.path("radius").asInt() + " " + shortId(in.path("block").asText());
                case "cylinder" -> "cylinder r" + in.path("radius").asInt() + " h" + in.path("height").asInt() + " " + shortId(in.path("block").asText());
                case "roof" -> in.path("style").asText("gable") + " roof " + shortId(in.path("stairs").asText());
                case "replace" -> "replace " + shortId(in.path("match").asText()) + " → " + shortId(in.path("block").asText());
                case "clear" -> "clear " + size(in);
                case "copy" -> "copy " + size(in);
                case "render_view" -> "look (" + in.path("view").asText("iso_se") + ")";
                case "get_layer_slice" -> "inspect y=" + in.path("y").asInt();
                case "get_region" -> "inspect " + size(in);
                case "search_blocks" -> "search “" + in.path("query").asText() + "”";
                case "create_layer" -> "new layer “" + in.path("name").asText() + "”";
                case "move_layer" -> "move layer " + in.path("dx").asInt() + "," + in.path("dy").asInt() + "," + in.path("dz").asInt();
                default -> name.replace('_', ' ');
            };
        } catch (RuntimeException e) {
            return name;
        }
    }

    private static String modeSuffix(JsonNode in) {
        String m = in.path("mode").asText("solid");
        return m.equals("solid") ? "" : " (" + m + ")";
    }

    private static String size(JsonNode in) {
        try {
            Box b = Box.of(p(in, "from"), p(in, "to"));
            return b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String shortId(String s) {
        return s.replace("minecraft:", "");
    }

    // ---- argument helpers ----------------------------------------------------------------------------------

    static BlockPos p(JsonNode in, String field) {
        JsonNode a = in.get(field);
        if (a == null || !a.isArray() || a.size() != 3 || !a.get(0).isNumber() || !a.get(1).isNumber() || !a.get(2).isNumber()) {
            throw new ToolError("'" + field + "' must be [x, y, z] integers");
        }
        return new BlockPos(a.get(0).asInt(), a.get(1).asInt(), a.get(2).asInt());
    }

    private static int intArg(JsonNode in, String field, int min, int max) {
        JsonNode n = in.get(field);
        if (n == null || !n.isNumber()) throw new ToolError("'" + field + "' must be an integer");
        int v = n.asInt();
        if (v < min || v > max) throw new ToolError("'" + field + "' must be between " + min + " and " + max);
        return v;
    }

    private static String strArg(JsonNode in, String field) {
        JsonNode n = in.get(field);
        if (n == null || !n.isTextual() || n.asText().isBlank()) throw new ToolError("'" + field + "' is required");
        return n.asText().strip();
    }

    /** Parses and validates a block state against the loaded registry, completing missing properties. */
    BlockState block(String text) {
        BlockState s;
        try {
            s = BlockState.parse(text);
        } catch (IllegalArgumentException e) {
            throw new ToolError("Can't parse block state '" + text + "': " + e.getMessage());
        }
        if (s.isAir()) return BlockState.AIR;
        BlockRegistry reg = ctx.registry();
        if (reg == null) return s;
        Optional<BlockRegistry.BlockInfo> info = reg.get(s.name());
        if (info.isEmpty()) {
            List<String> sugg = reg.search(s.path().replace('_', ' '), 5).stream().map(BlockRegistry.BlockInfo::id).toList();
            throw new ToolError("Unknown block '" + s.name() + "'." + (sugg.isEmpty() ? " Use search_blocks." : " Did you mean: " + String.join(", ", sugg) + "?"));
        }
        for (var e : s.properties().entrySet()) {
            List<String> allowed = info.get().properties().get(e.getKey());
            if (allowed == null) throw new ToolError(s.name() + " has no property '" + e.getKey() + "'. Properties: " + info.get().properties().keySet());
            if (!allowed.contains(e.getValue())) throw new ToolError(s.name() + "[" + e.getKey() + "] must be one of " + allowed);
        }
        return reg.complete(s);
    }

    private static Box box(JsonNode in) {
        Box b = Box.of(p(in, "from"), p(in, "to"));
        if (b.volume() > MAX_VOLUME) throw new ToolError("Box is " + b.volume() + " blocks; the limit per call is " + MAX_VOLUME + ". Split it up.");
        return b;
    }

    private Layer targetLayer() {
        Optional<Layer> a = ctx.scene().active();
        if (a.isPresent()) {
            if (a.get().locked()) throw new ToolError("The active layer '" + a.get().name() + "' is locked by the user. Create a new layer or choose another.");
            return a.get();
        }
        return ctx.createLayer("AI build");
    }

    /** Collects world-space writes into the active layer and commits them as one edit. */
    private final class Writer implements AutoCloseable {
        final Layer layer = targetLayer();
        final SceneEditor.BlockSession session;
        final Transform inverse = layer.transform().inverse();
        int count;
        Box touched;

        Writer(String label) {
            session = ctx.editor().edit(layer, label);
        }

        BlockState getWorld(int x, int y, int z) {
            BlockPos l = layer.toLocal(new BlockPos(x, y, z));
            return transformer.apply(layer.structure().get(l), layer.transform());
        }

        void set(int x, int y, int z, BlockState worldState) {
            BlockPos l = layer.toLocal(new BlockPos(x, y, z));
            session.set(l.x(), l.y(), l.z(), transformer.apply(worldState, inverse));
            count++;
            Box b = new Box(x, y, z, x, y, z);
            touched = touched == null ? b : touched.union(b);
        }

        @Override
        public void close() {
            session.commit();
            if (touched != null) ctx.highlight(touched);
        }

        String summary(String what) {
            return what + ": " + count + " blocks written in layer '" + layer.name() + "'" + (touched == null ? "" : " within " + touched);
        }
    }

    // ---- inspection ----------------------------------------------------------------------------------------

    private String buildInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Target Minecraft version: ").append(ctx.targetVersion()).append('\n');
        sb.append("Coordinates: X east, Y up, Z south. Ground level is y=0 unless the build says otherwise.\n");
        sb.append(listLayers());
        ctx.scene().active().ifPresent(l -> {
            Map<String, Integer> counts = new HashMap<>();
            l.structure().forEachBlock((x, y, z, s) -> counts.merge(s.name(), 1, Integer::sum));
            if (!counts.isEmpty()) {
                sb.append("Most used blocks in active layer: ");
                counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(15)
                        .forEach(e -> sb.append(shortId(e.getKey())).append(" ×").append(e.getValue()).append(", "));
                sb.setLength(sb.length() - 2);
                sb.append('\n');
            }
        });
        return sb.toString();
    }

    private String listLayers() {
        StringBuilder sb = new StringBuilder();
        List<Layer> layers = ctx.scene().layers();
        if (layers.isEmpty()) return "No layers yet (the first edit creates one).\n";
        Layer active = ctx.scene().active().orElse(null);
        sb.append("Layers (bottom to top):\n");
        for (Layer l : layers) {
            sb.append(l == active ? "* " : "- ").append('"').append(l.name()).append("\" id=").append(l.id(), 0, 8)
                    .append(" blocks=").append(l.structure().blockCount())
                    .append(" bounds=").append(l.worldBounds().map(Box::toString).orElse("empty"))
                    .append(l.visible() ? "" : " [hidden]").append(l.locked() ? " [locked]" : "").append('\n');
        }
        return sb.toString();
    }

    private BlockState worldBlock(int x, int y, int z) {
        // Topmost visible layer wins, matching how layers flatten.
        List<Layer> layers = ctx.scene().layers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (!l.visible()) continue;
            BlockState s = l.structure().get(l.toLocal(new BlockPos(x, y, z)));
            if (!s.isAir()) return transformer.apply(s, l.transform());
        }
        return BlockState.AIR;
    }

    private String slice(JsonNode in) {
        int y = in.path("y").asInt();
        Box area;
        if (in.has("from") && in.has("to")) area = Box.of(p(in, "from"), p(in, "to"));
        else area = ctx.scene().worldBounds().orElseThrow(() -> new ToolError("Scene is empty"));
        int x0 = area.minX(), x1 = Math.min(area.maxX(), x0 + 63), z0 = area.minZ(), z1 = Math.min(area.maxZ(), z0 + 63);
        Map<String, Character> legend = new LinkedHashMap<>();
        String symbols = "#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$%&*+=?";
        StringBuilder grid = new StringBuilder();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                BlockState s = worldBlock(x, y, z);
                if (s.isAir()) {
                    grid.append('.');
                    continue;
                }
                String key = shortId(s.toString());
                Character c = legend.get(key);
                if (c == null) {
                    c = legend.size() < symbols.length() ? symbols.charAt(legend.size()) : '~';
                    legend.put(key, c);
                }
                grid.append(c);
            }
            grid.append("   z=").append(z).append('\n');
        }
        StringBuilder sb = new StringBuilder("Slice y=" + y + ", x " + x0 + ".." + x1 + " (left→right), z " + z0 + ".." + z1 + " (top→bottom, north at top)\n");
        sb.append(grid);
        sb.append("Legend: . = air");
        legend.forEach((k, c) -> sb.append(", ").append(c).append(" = ").append(k));
        return sb.toString();
    }

    private String region(JsonNode in) {
        Box b = box(in);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        outer:
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    BlockState s = worldBlock(x, y, z);
                    if (s.isAir()) continue;
                    if (++n > 1500) {
                        sb.append("… truncated at 1500 blocks; query a smaller box.\n");
                        break outer;
                    }
                    sb.append(x).append(' ').append(y).append(' ').append(z).append(' ').append(shortId(s.toString())).append('\n');
                }
            }
        }
        return n == 0 ? "No blocks in " + b : sb.toString();
    }

    private String search(JsonNode in) {
        BlockRegistry reg = ctx.registry();
        if (reg == null) return "No block assets loaded; vanilla ids will still work.";
        int limit = Math.clamp(in.path("limit").asInt(20), 1, 60);
        List<BlockRegistry.BlockInfo> r = reg.search(strArg(in, "query"), limit);
        if (r.isEmpty()) return "No blocks match.";
        StringBuilder sb = new StringBuilder();
        for (BlockRegistry.BlockInfo b : r) {
            sb.append(b.id());
            if (!b.properties().isEmpty()) sb.append("  ").append(b.properties().keySet());
            sb.append('\n');
        }
        return sb.toString();
    }

    private String properties(JsonNode in) {
        BlockRegistry reg = ctx.registry();
        String id = BlockState.normalizeId(strArg(in, "block").replaceAll("\\[.*", ""));
        if (reg == null) return "No block assets loaded.";
        BlockRegistry.BlockInfo b = reg.get(id).orElseThrow(() -> new ToolError("Unknown block " + id));
        if (b.properties().isEmpty()) return id + " has no properties.";
        StringBuilder sb = new StringBuilder(id + " properties (default " + b.defaultState() + "):\n");
        b.properties().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
        if (id.endsWith("_stairs")) sb.append("Note: stairs 'facing' is the side the tall back is on; you walk up the stairs toward 'facing'.\n");
        return sb.toString();
    }

    // ---- building ------------------------------------------------------------------------------------------

    private String setBlocks(JsonNode in) {
        JsonNode arr = in.get("blocks");
        if (arr == null || !arr.isArray() || arr.isEmpty()) throw new ToolError("'blocks' must be a non-empty array");
        if (arr.size() > 20_000) throw new ToolError("At most 20000 blocks per call");
        // Validate everything first so a bad entry doesn't leave a half-applied edit.
        List<BlockPos> ps = new ArrayList<>();
        List<BlockState> ss = new ArrayList<>();
        Map<String, BlockState> cache = new HashMap<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonNode e = arr.get(i);
            try {
                ps.add(p(e, "pos"));
                String t = strArg(e, "block");
                BlockState s = cache.get(t);
                if (s == null) {
                    s = block(t);
                    cache.put(t, s);
                }
                ss.add(s);
            } catch (ToolError err) {
                throw new ToolError("blocks[" + i + "]: " + err.getMessage());
            }
        }
        try (Writer w = new Writer("AI: place blocks")) {
            for (int i = 0; i < ps.size(); i++) w.set(ps.get(i).x(), ps.get(i).y(), ps.get(i).z(), ss.get(i));
            return w.summary("Placed");
        }
    }

    private String fill(JsonNode in) {
        Box b = box(in);
        BlockState s = block(strArg(in, "block"));
        String mode = in.path("mode").asText("solid");
        boolean onlyAir = in.path("only_air").asBoolean(false);
        try (Writer w = new Writer("AI: fill")) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int x = b.minX(); x <= b.maxX(); x++) {
                        boolean shell = x == b.minX() || x == b.maxX() || y == b.minY() || y == b.maxY() || z == b.minZ() || z == b.maxZ();
                        boolean side = x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ();
                        BlockState put = switch (mode) {
                            case "hollow" -> shell ? s : BlockState.AIR;
                            case "outline" -> shell ? s : null;
                            case "walls" -> side ? s : null;
                            case "solid" -> s;
                            default -> throw new ToolError("Unknown mode " + mode);
                        };
                        if (put == null) continue;
                        if (onlyAir && !w.getWorld(x, y, z).isAir()) continue;
                        w.set(x, y, z, put);
                    }
                }
            }
            return w.summary("Filled " + b);
        }
    }

    private String line(JsonNode in) {
        BlockPos a = p(in, "from"), b = p(in, "to");
        BlockState s = block(strArg(in, "block"));
        int dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps > 2048) throw new ToolError("Line too long (max 2048)");
        try (Writer w = new Writer("AI: line")) {
            for (int i = 0; i <= steps; i++) {
                double t = steps == 0 ? 0 : i / (double) steps;
                w.set((int) Math.round(a.x() + dx * t), (int) Math.round(a.y() + dy * t), (int) Math.round(a.z() + dz * t), s);
            }
            return w.summary("Line");
        }
    }

    private String sphere(JsonNode in) {
        BlockPos c = p(in, "center");
        int r = intArg(in, "radius", 1, 48);
        BlockState s = block(strArg(in, "block"));
        boolean hollow = in.path("hollow").asBoolean(false);
        String part = in.path("part").asText("full");
        double outer = (r + 0.5) * (r + 0.5), inner = (r - 0.5) * (r - 0.5);
        try (Writer w = new Writer("AI: sphere")) {
            for (int y = -r; y <= r; y++) {
                if (part.equals("top") && y < 0) continue;
                if (part.equals("bottom") && y > 0) continue;
                for (int z = -r; z <= r; z++) {
                    for (int x = -r; x <= r; x++) {
                        double d = x * x + y * y + z * z;
                        if (d > outer || (hollow && d < inner)) continue;
                        w.set(c.x() + x, c.y() + y, c.z() + z, s);
                    }
                }
            }
            return w.summary("Sphere");
        }
    }

    private String cylinder(JsonNode in) {
        BlockPos base = p(in, "base");
        int r = intArg(in, "radius", 1, 48), h = intArg(in, "height", 1, 512);
        BlockState s = block(strArg(in, "block"));
        boolean hollow = in.path("hollow").asBoolean(false);
        String axis = in.path("axis").asText("y");
        double outer = (r + 0.5) * (r + 0.5), inner = (r - 0.5) * (r - 0.5);
        try (Writer w = new Writer("AI: cylinder")) {
            for (int t = 0; t < h; t++) {
                for (int a = -r; a <= r; a++) {
                    for (int b = -r; b <= r; b++) {
                        double d = a * a + b * b;
                        if (d > outer || (hollow && d < inner)) continue;
                        switch (axis) {
                            case "x" -> w.set(base.x() + t, base.y() + a, base.z() + b, s);
                            case "z" -> w.set(base.x() + a, base.y() + b, base.z() + t, s);
                            default -> w.set(base.x() + a, base.y() + t, base.z() + b, s);
                        }
                    }
                }
            }
            return w.summary("Cylinder");
        }
    }

    private String replace(JsonNode in) {
        Box b = box(in);
        String matchText = strArg(in, "match");
        BlockState to = block(strArg(in, "block"));
        boolean exact = matchText.contains("[");
        BlockState match = exact ? block(matchText) : BlockState.of(matchText);
        BiPredicate<BlockState, BlockState> test = exact ? (s, m) -> s == m : (s, m) -> s.name().equals(m.name());
        try (Writer w = new Writer("AI: replace")) {
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++)
                    for (int x = b.minX(); x <= b.maxX(); x++) {
                        BlockState cur = w.getWorld(x, y, z);
                        if (!test.test(cur, match)) continue;
                        // Keep shared properties (facing, half...) when swapping between similar blocks.
                        BlockState put = to;
                        if (!exact && ctx.registry() != null && !to.isAir()) {
                            Map<String, String> props = new java.util.TreeMap<>(to.properties());
                            cur.properties().forEach((k, v) -> {
                                if (props.containsKey(k)) props.put(k, v);
                            });
                            put = ctx.registry().complete(to.withProperties(props));
                        }
                        w.set(x, y, z, put);
                    }
            return w.summary("Replaced");
        }
    }

    private String clear(JsonNode in) {
        Box b = box(in);
        try (Writer w = new Writer("AI: clear")) {
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++)
                    for (int x = b.minX(); x <= b.maxX(); x++) if (!w.getWorld(x, y, z).isAir()) w.set(x, y, z, BlockState.AIR);
            return w.summary("Cleared");
        }
    }

    private String copy(JsonNode in) {
        Box b = box(in);
        BlockPos dest = p(in, "dest");
        int rot = Math.floorMod(in.path("rotate").asInt(0), 4);
        Transform.Mirror mirror = switch (in.path("mirror").asText("none")) {
            case "x" -> Transform.Mirror.X;
            case "z" -> Transform.Mirror.Z;
            default -> Transform.Mirror.NONE;
        };
        boolean skipAir = in.path("skip_air").asBoolean(true);
        Transform t = new Transform(rot, mirror);
        // Snapshot the source first so overlapping copies read original blocks.
        Structure src = new Structure();
        for (int y = b.minY(); y <= b.maxY(); y++)
            for (int z = b.minZ(); z <= b.maxZ(); z++)
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    BlockState s = worldBlock(x, y, z);
                    if (!s.isAir()) src.set(x - b.minX(), y - b.minY(), z - b.minZ(), s);
                }
        // Transform relative coordinates, then shift so the result's min corner lands on dest.
        Box rel = new Box(0, 0, 0, b.sizeX() - 1, b.sizeY() - 1, b.sizeZ() - 1);
        Box tb = Box.of(t.apply(rel.min()), t.apply(rel.max()));
        try (Writer w = new Writer("AI: copy")) {
            for (int y = 0; y < b.sizeY(); y++)
                for (int z = 0; z < b.sizeZ(); z++)
                    for (int x = 0; x < b.sizeX(); x++) {
                        BlockState s = src.get(x, y, z);
                        if (s.isAir() && skipAir) continue;
                        BlockPos q = t.apply(x, y, z);
                        w.set(dest.x() + q.x() - tb.minX(), dest.y() + q.y(), dest.z() + q.z() - tb.minZ(), transformer.apply(s, t));
                    }
            return w.summary("Copied");
        }
    }

    // ---- roofs ---------------------------------------------------------------------------------------------

    private String roof(JsonNode in) {
        BlockPos a = p(in, "from"), b = p(in, "to");
        String stairsId = BlockState.normalizeId(strArg(in, "stairs"));
        if (!stairsId.endsWith("_stairs")) throw new ToolError("'stairs' must be a stairs block such as oak_stairs");
        BlockState stairs = block(stairsId);
        String style = in.path("style").asText("gable");
        int ov = Math.clamp(in.path("overhang").asInt(1), 0, 3);
        int x0 = Math.min(a.x(), b.x()) - ov, x1 = Math.max(a.x(), b.x()) + ov;
        int z0 = Math.min(a.z(), b.z()) - ov, z1 = Math.max(a.z(), b.z()) + ov;
        int y = a.y();
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 128 * 128) throw new ToolError("Footprint too large for one roof (max 128×128)");
        String base = stairsId.substring(0, stairsId.length() - "_stairs".length());
        BlockState slab = optionalBlock(base + "_slab").orElse(null);
        BlockState full = in.hasNonNull("gable_fill") ? block(in.get("gable_fill").asText()) : fullBlockFor(base).orElse(stairs);

        try (Writer w = new Writer("AI: roof")) {
            switch (style) {
                case "flat" -> {
                    BlockState top = slab != null ? slab.with("type", "bottom") : full;
                    for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) w.set(x, y, z, top);
                }
                case "hip" -> hipRoof(w, stairs, slab, full, x0, x1, z0, z1, y);
                case "gable" -> {
                    String axis = in.path("ridge_axis").asText((x1 - x0) >= (z1 - z0) ? "x" : "z");
                    gableRoof(w, stairs, slab, full, x0, x1, z0, z1, y, axis.equals("x"), ov);
                }
                default -> throw new ToolError("Unknown roof style " + style);
            }
            return w.summary(style + " roof");
        }
    }

    private static BlockState stair(BlockState s, String facing, String shape) {
        return s.with("facing", facing).with("half", "bottom").with("shape", shape);
    }

    private void gableRoof(Writer w, BlockState stairs, BlockState slab, BlockState full, int x0, int x1, int z0, int z1, int y,
                           boolean ridgeAlongX, int ov) {
        // Slopes rise from the two long edges toward the ridge; gable ends get filled triangles.
        int lo = ridgeAlongX ? z0 : x0, hi = ridgeAlongX ? z1 : x1;
        int a0 = ridgeAlongX ? x0 : z0, a1 = ridgeAlongX ? x1 : z1;
        String upLo = ridgeAlongX ? "south" : "east", upHi = ridgeAlongX ? "north" : "west";
        for (int i = 0; lo + i <= hi - i; i++) {
            int ly = y + i;
            int rowLo = lo + i, rowHi = hi - i;
            for (int t = a0; t <= a1; t++) {
                if (rowLo == rowHi) {
                    BlockState ridge = slab != null ? slab.with("type", "bottom") : full;
                    put(w, ridgeAlongX, t, ly, rowLo, ridge);
                } else {
                    put(w, ridgeAlongX, t, ly, rowLo, stair(stairs, upLo, "straight"));
                    put(w, ridgeAlongX, t, ly, rowHi, stair(stairs, upHi, "straight"));
                }
            }
            // Gable end triangles (inside the overhang, at the building's end walls).
            for (int end : new int[]{a0 + ov, a1 - ov}) {
                for (int k = rowLo + 1; k <= rowHi - 1; k++) put(w, ridgeAlongX, end, ly, k, full);
            }
        }
    }

    private void hipRoof(Writer w, BlockState stairs, BlockState slab, BlockState full, int x0, int x1, int z0, int z1, int y) {
        for (int i = 0; x0 + i <= x1 - i && z0 + i <= z1 - i; i++) {
            int ax = x0 + i, bx = x1 - i, az = z0 + i, bz = z1 - i, ly = y + i;
            if (ax == bx || az == bz) {
                // Final ridge line or single top block.
                BlockState cap = slab != null ? slab.with("type", "bottom") : full;
                for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) w.set(x, ly, z, cap);
                break;
            }
            for (int x = ax + 1; x < bx; x++) {
                w.set(x, ly, az, stair(stairs, "south", "straight"));
                w.set(x, ly, bz, stair(stairs, "north", "straight"));
            }
            for (int z = az + 1; z < bz; z++) {
                w.set(ax, ly, z, stair(stairs, "east", "straight"));
                w.set(bx, ly, z, stair(stairs, "west", "straight"));
            }
            // Outer corners point their raised quarter toward the middle of the roof.
            w.set(ax, ly, az, stair(stairs, "south", "outer_left"));
            w.set(bx, ly, az, stair(stairs, "south", "outer_right"));
            w.set(ax, ly, bz, stair(stairs, "north", "outer_right"));
            w.set(bx, ly, bz, stair(stairs, "north", "outer_left"));
        }
    }

    private static void put(Writer w, boolean ridgeAlongX, int along, int y, int across, BlockState s) {
        if (ridgeAlongX) w.set(along, y, across, s);
        else w.set(across, y, along, s);
    }

    private Optional<BlockState> optionalBlock(String id) {
        BlockRegistry reg = ctx.registry();
        if (reg == null) return Optional.of(BlockState.of(id));
        return reg.get(id).map(BlockRegistry.BlockInfo::defaultState);
    }

    /** The full block matching a stairs material: oak → oak_planks, stone_brick → stone_bricks, cobblestone → cobblestone. */
    private Optional<BlockState> fullBlockFor(String base) {
        for (String c : new String[]{base + "_planks", base + "s", base, base.replace("_brick", "_bricks"), base + "_block"}) {
            Optional<BlockState> s = optionalBlock(c);
            if (s.isPresent() && ctx.registry() != null) return s;
        }
        return Optional.empty();
    }

    // ---- layers --------------------------------------------------------------------------------------------

    private Layer findLayer(String ref) {
        String r = ref.strip();
        for (Layer l : ctx.scene().layers()) if (l.id().equals(r) || l.id().startsWith(r) || l.name().equalsIgnoreCase(r)) return l;
        throw new ToolError("No layer '" + ref + "'. " + listLayers());
    }

    private String createLayer(JsonNode in) {
        Layer l = ctx.createLayer(strArg(in, "name"));
        ctx.scene().setActive(l);
        return "Created layer '" + l.name() + "' (id " + l.id().substring(0, 8) + ") and made it active.";
    }

    private String setActive(JsonNode in) {
        Layer l = findLayer(strArg(in, "layer"));
        ctx.scene().setActive(l);
        return "Active layer is now '" + l.name() + "'.";
    }

    private String moveLayer(JsonNode in) {
        Layer l = in.hasNonNull("layer") ? findLayer(in.get("layer").asText()) : ctx.scene().active().orElseThrow(() -> new ToolError("No active layer"));
        if (l.locked()) throw new ToolError("Layer '" + l.name() + "' is locked");
        int dx = intArg(in, "dx", -4096, 4096), dy = intArg(in, "dy", -512, 512), dz = intArg(in, "dz", -4096, 4096);
        ctx.editor().nudge(List.of(l), dx, dy, dz, null);
        return "Moved '" + l.name() + "' to offset " + l.offset() + "; bounds now " + l.worldBounds().map(Box::toString).orElse("empty");
    }
}
