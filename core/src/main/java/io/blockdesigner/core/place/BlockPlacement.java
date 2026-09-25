package io.blockdesigner.core.place;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minecraft's "state for placement": how a block orients itself from the face you click, where on the face you click
 * and which way you look. Stairs and slabs go top or bottom, logs follow the clicked axis, torches, signs, banners and
 * heads switch to their wall form on sides, doors and beds place both halves, and so on. Blocks without a vanilla rule
 * (most modded ones) get a generic one from their properties. Everything is in world space.
 */
public final class BlockPlacement {
    private BlockPlacement() {
    }

    public enum Dir {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        public final int x, y, z;

        Dir(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public BlockPos offset(BlockPos p) {
            return p.add(x, y, z);
        }

        public boolean horizontal() {
            return y == 0;
        }

        public String axis() {
            return x != 0 ? "x" : y != 0 ? "y" : "z";
        }

        public Dir opposite() {
            return switch (this) {
                case DOWN -> UP;
                case UP -> DOWN;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }

        /** Clockwise seen from above: north, east, south, west. Up and down stay put. */
        public Dir clockWise() {
            return switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
                default -> this;
            };
        }

        public Dir counterClockWise() {
            return clockWise().clockWise().clockWise();
        }

        /** The direction of a unit axis vector (a face normal). */
        public static Dir of(BlockPos n) {
            if (n.y() > 0) return UP;
            if (n.y() < 0) return DOWN;
            if (n.x() > 0) return EAST;
            if (n.x() < 0) return WEST;
            return n.z() > 0 ? SOUTH : NORTH;
        }

        public static Dir parse(String s) {
            if (s == null) return null;
            for (Dir d : values()) if (d.id().equals(s)) return d;
            return null;
        }
    }

    /** What a block id allows: its default state and every value of each property. */
    public record Info(BlockState defaultState, Map<String, List<String>> properties) {
    }

    public interface Blocks {
        /** Null when the block is unknown. */
        Info info(String id);

        Blocks NONE = id -> null;
    }

    public interface World {
        /** World-facing state at a position (air when empty). */
        BlockState get(BlockPos p);
    }

    /**
     * @param pos     where the block goes (the cell in front of the clicked face)
     * @param clicked the clicked block, or null when clicking the ground grid
     * @param face    the clicked face
     * @param hitX    exact world point that was clicked
     * @param lookX   look direction (need not be normalised)
     */
    public record Context(BlockPos pos, BlockPos clicked, Dir face, double hitX, double hitY, double hitZ,
                          float lookX, float lookY, float lookZ) {
        /** The horizontal way the player faces. */
        public Dir horizontal() {
            if (Math.abs(lookX) > Math.abs(lookZ)) return lookX > 0 ? Dir.EAST : Dir.WEST;
            return lookZ > 0 ? Dir.SOUTH : Dir.NORTH;
        }

        /** The axis direction closest to where the player looks, up and down included. */
        public Dir nearest() {
            float ax = Math.abs(lookX), ay = Math.abs(lookY), az = Math.abs(lookZ);
            if (ay > ax && ay > az) return lookY > 0 ? Dir.UP : Dir.DOWN;
            return horizontal();
        }

        /** Minecraft yaw in degrees: 0 looks south, 90 west, 180 north. */
        public float yaw() {
            return (float) Math.toDegrees(Math.atan2(-lookX, lookZ));
        }

        /** How far up the clicked point is inside the cell at {@code p} (0..1). */
        double fracY(BlockPos p) {
            return hitY - p.y();
        }
    }

    /**
     * The blocks to set for placing {@code held}: usually one, two for doors, beds and tall plants, and neighbouring
     * stairs whose corner shape changes. Empty when it cannot be placed (the cell is taken, the other half is blocked).
     */
    public static Map<BlockPos, BlockState> place(BlockState held, Context c, World w, Blocks blocks) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        if (held == null || held.isAir()) return out;
        BlockPos pos = c.pos();

        // A slab clicked onto the matching half of the same slab doubles it, as in Minecraft.
        if (isSlab(held)) {
            BlockPos merge = slabMerge(held, c, w);
            if (merge != null) {
                out.put(merge, withIf(w.get(merge).with("type", "double"), "waterlogged", "false"));
                return out;
            }
        }
        if (!w.get(pos).isAir()) return out;

        BlockState st = orient(standing(held, blocks), c, w, blocks);
        out.put(pos, st);

        // Second halves.
        if ("lower".equals(st.get("half")) || "upper".equals(st.get("half"))) {
            st = st.with("half", "lower");
            BlockPos up = pos.add(0, 1, 0);
            if (!w.get(up).isAir()) return Map.of();
            out.put(pos, st);
            out.put(up, st.with("half", "upper"));
        } else if (st.has("part") && Dir.parse(st.get("facing")) != null && isBed(st)) {
            st = st.with("part", "foot");
            BlockPos head = Dir.parse(st.get("facing")).offset(pos);
            if (!w.get(head).isAir()) return Map.of();
            out.put(pos, st);
            out.put(head, st.with("part", "head"));
        }

        // Stairs join into corners, fences / walls / panes connect, and the neighbours change to meet them.
        out.putAll(reconnect(List.copyOf(out.keySet()), p -> out.containsKey(p) ? out.get(p) : w.get(p)));
        return out;
    }

    private static final Dir[] HORIZONTAL = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};

    /**
     * Build mode's Replace: swaps the block at {@code pos} for {@code held}, keeping the old block's facing, half,
     * axis, shape and so on wherever the new block has them (stairs stay stairs the same way round, a wall torch stays
     * on its wall). Both halves of doors, beds and tall plants are swapped, and neighbours reconnect. Empty when there
     * is nothing to replace or nothing would change.
     */
    public static Map<BlockPos, BlockState> replace(BlockState held, BlockPos pos, World w, Blocks blocks) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        BlockState old = w.get(pos);
        if (held == null || held.isAir() || old.isAir()) return out;
        BlockState st = replacement(old, held, blocks);
        if (st == old) return out;
        out.put(pos, st);

        BlockPos partner = null;
        if (("lower".equals(old.get("half")) || "upper".equals(old.get("half"))) && st.has("half")) {
            partner = pos.add(0, "lower".equals(old.get("half")) ? 1 : -1, 0);
        } else if (old.has("part") && Dir.parse(old.get("facing")) != null && isBed(old) && st.has("part")) {
            Dir f = Dir.parse(old.get("facing"));
            partner = ("foot".equals(old.get("part")) ? f : f.opposite()).offset(pos);
        }
        if (partner != null) {
            BlockState other = w.get(partner);
            if (other.name().equals(old.name())) out.put(partner, replacement(other, held, blocks));
        }
        out.putAll(reconnect(List.copyOf(out.keySet()), p -> out.containsKey(p) ? out.get(p) : w.get(p)));
        return out;
    }

    /** {@code held}, or its wall form when {@code old} was on a wall, with {@code old}'s shared property values. */
    static BlockState replacement(BlockState old, BlockState held, Blocks blocks) {
        BlockState base = standing(held, blocks);
        boolean oldOnWall = standing(old, blocks) != old || old.path().contains("_wall_") || old.path().endsWith("wall_torch");
        String wall = wallVariant(base.name());
        Info info;
        if (oldOnWall && wall != null && blocks.info(wall) != null) {
            info = blocks.info(wall);
            base = carry(base, info);
        } else {
            info = blocks.info(base.name());
        }
        Map<String, List<String>> allowed = info != null ? info.properties() : null;
        BlockState out = base;
        for (var e : old.properties().entrySet()) {
            if (out.has(e.getKey())) out = set(out, allowed, e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Minecraft's shape updates after blocks at {@code changed} were placed or removed: stairs pick their corner
     * shape, and fences, walls, panes and iron bars connect only to their own kind, fence gates side-on and solid
     * blocks (never to air). Covers the changed blocks, their horizontal neighbours and the block below (a wall post).
     * Returns only the states that differ; {@code w} must already show the change.
     */
    public static Map<BlockPos, BlockState> reconnect(java.util.Collection<BlockPos> changed, World w) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        java.util.Set<BlockPos> todo = new java.util.LinkedHashSet<>();
        for (BlockPos p : changed) {
            todo.add(p);
            // Redstone and rails also join one block up or down, so the layers above and below are checked too.
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos q = p.add(0, dy, 0);
                if (dy != 0) todo.add(q);
                for (Dir d : HORIZONTAL) todo.add(d.offset(q));
            }
        }
        World now = p -> out.containsKey(p) ? out.get(p) : w.get(p);
        for (BlockPos p : todo) {
            BlockState s = now.get(p), c = connect(s, p, now);
            if (c != s) out.put(p, c);
        }
        for (BlockPos p : todo) {
            BlockState s = now.get(p);
            if (!isStairs(s)) continue;
            String shape = stairsShape(s, p, now);
            if (!shape.equals(s.get("shape"))) out.put(p, s.with("shape", shape));
        }
        return out;
    }

    private enum Connector { FENCE, WALL, PANE }

    private static Connector connector(BlockState s) {
        if (!s.has("north") || !s.has("east") || !s.has("south") || !s.has("west")) return null;
        String p = s.path();
        if (p.endsWith("_fence")) return Connector.FENCE;
        if (p.endsWith("_wall") && s.has("up")) return Connector.WALL;
        if (p.endsWith("_pane") || p.endsWith("_bars")) return Connector.PANE;
        return null;
    }

    private static BlockState connect(BlockState s, BlockPos pos, World w) {
        if (isWire(s)) return connectWire(s, pos, w);
        if (isRail(s)) return connectRail(s, pos, w);
        Connector kind = connector(s);
        if (kind == null) return s;
        BlockState above = w.get(pos.add(0, 1, 0));
        boolean[] on = new boolean[4];
        BlockState out = s;
        for (int i = 0; i < 4; i++) {
            Dir d = HORIZONTAL[i];
            on[i] = connectsTo(kind, s, w.get(d.offset(pos)), d);
            String v;
            if (kind == Connector.WALL && isLowTall(s.get(d.id()))) {
                // A wall side goes tall under a solid block, or under a wall that also runs that way.
                boolean tall = on[i] && (sturdy(above) || connector(above) == Connector.WALL && !"none".equals(above.get(d.id())));
                v = on[i] ? (tall ? "tall" : "low") : "none";
            } else {
                v = Boolean.toString(on[i]);
            }
            out = out.with(d.id(), v);
        }
        if (kind == Connector.WALL) {
            // Straight runs have no post, unless something standing on top needs one.
            boolean straight = on[0] == on[2] && on[1] == on[3] && on[0] != on[1];
            boolean abovePost = !above.isAir() && !sturdy(above)
                    && !(connector(above) == Connector.WALL && "false".equals(above.get("up")));
            out = out.with("up", Boolean.toString(!straight || abovePost));
        }
        return out;
    }

    // ---- redstone dust -------------------------------------------------------------------------------------------

    private static boolean isWire(BlockState s) {
        return s.path().equals("redstone_wire") && s.has("north");
    }

    private static final java.util.Set<String> SIGNAL_SOURCES = java.util.Set.of("redstone_torch", "redstone_wall_torch",
            "lever", "redstone_block", "target", "daylight_detector", "detector_rail", "trapped_chest", "tripwire_hook",
            "comparator", "sculk_sensor", "calibrated_sculk_sensor", "lectern", "observer", "repeater");

    private static boolean signalSource(BlockState s) {
        String p = s.path();
        return SIGNAL_SOURCES.contains(p) || p.endsWith("_button") || p.endsWith("_pressure_plate");
    }

    /** Minecraft's RedStoneWireBlock.shouldConnectTo: repeaters only along their line, observers only from behind. */
    private static boolean wireJoins(BlockState s, Dir d) {
        if (isWire(s)) return true;
        Dir f = Dir.parse(s.get("facing"));
        if (s.path().equals("repeater")) return f != null && (f == d || f == d.opposite());
        if (s.path().equals("observer")) return f == d;
        return signalSource(s);
    }

    /** Solid, opaque blocks carry redstone (glass does not). */
    private static boolean conductor(BlockState s) {
        String p = s.path();
        return sturdy(s) && !p.equals("glass") && !p.endsWith("_stained_glass") && !p.equals("tinted_glass");
    }

    /**
     * Minecraft's dust shape: joins other dust (stepping up a block or down one), redstone parts; a lone dust is a
     * cross, and dust joined on one side runs straight through.
     */
    private static BlockState connectWire(BlockState s, BlockPos pos, World w) {
        boolean openAbove = !conductor(w.get(pos.add(0, 1, 0)));
        String[] side = new String[4];
        for (int i = 0; i < 4; i++) {
            Dir d = HORIZONTAL[i];
            BlockPos np = d.offset(pos);
            BlockState ns = w.get(np);
            String v = "none";
            if (openAbove && sturdy(ns) && isWire(w.get(np.add(0, 1, 0)))) v = "up";
            else if (wireJoins(ns, d) || !conductor(ns) && isWire(w.get(np.add(0, -1, 0)))) v = "side";
            side[i] = v;
        }
        boolean n = !side[0].equals("none"), e = !side[1].equals("none"), so = !side[2].equals("none"), we = !side[3].equals("none");
        boolean nsEmpty = !n && !so, ewEmpty = !e && !we;
        if (!we && nsEmpty) side[3] = "side";
        if (!e && nsEmpty) side[1] = "side";
        if (!n && ewEmpty) side[0] = "side";
        if (!so && ewEmpty) side[2] = "side";
        BlockState out = s;
        for (int i = 0; i < 4; i++) out = out.with(HORIZONTAL[i].id(), side[i]);
        return out;
    }

    // ---- rails -----------------------------------------------------------------------------------------------------

    private static boolean isRail(BlockState s) {
        return s.path().endsWith("rail") && s.has("shape");
    }

    /**
     * Rails join the rails next to them: straight runs, slopes up to a rail one block higher, and (plain rails only)
     * curves where two meet at a corner. A rail with no neighbours keeps its placed direction.
     */
    private static BlockState connectRail(BlockState s, BlockPos pos, World w) {
        Integer[] level = new Integer[4];
        for (int i = 0; i < 4; i++) {
            BlockPos np = HORIZONTAL[i].offset(pos);
            if (isRail(w.get(np))) level[i] = 0;
            else if (isRail(w.get(np.add(0, 1, 0)))) level[i] = 1;
            else if (isRail(w.get(np.add(0, -1, 0)))) level[i] = -1;
        }
        boolean n = level[0] != null, e = level[1] != null, so = level[2] != null, we = level[3] != null;
        boolean curves = s.path().equals("rail");
        String shape = null;
        if (curves && (n || so) && (e || we) && !(n && so) && !(e && we)) {
            shape = (so ? "south" : "north") + "_" + (e ? "east" : "west");
        } else if (n || so) {
            shape = n && level[0] == 1 ? "ascending_north" : so && level[2] == 1 ? "ascending_south" : "north_south";
        } else if (e || we) {
            shape = e && level[1] == 1 ? "ascending_east" : we && level[3] == 1 ? "ascending_west" : "east_west";
        }
        return shape == null || shape.equals(s.get("shape")) ? s : s.with("shape", shape);
    }

    private static boolean isLowTall(String v) {
        return "none".equals(v) || "low".equals(v) || "tall".equals(v);
    }

    /** Whether a fence, wall or pane joins {@code other}, which lies in direction {@code d} from it. */
    private static boolean connectsTo(Connector kind, BlockState self, BlockState other, Dir d) {
        if (other.isAir()) return false;
        Connector ok = connector(other);
        if (ok != null) {
            return switch (kind) {
                // Wooden fences join any wooden fence; nether brick only joins nether brick.
                case FENCE -> ok == Connector.FENCE && isNetherFence(self) == isNetherFence(other);
                case WALL, PANE -> ok == Connector.WALL || ok == Connector.PANE;
            };
        }
        if (other.path().endsWith("fence_gate")) {
            // A gate joins along its hinge line (side-on), not across its opening.
            Dir f = Dir.parse(other.get("facing"));
            return kind != Connector.PANE && f != null && !f.axis().equals(d.axis());
        }
        return sturdy(other);
    }

    private static boolean isNetherFence(BlockState s) {
        return s.path().equals("nether_brick_fence");
    }

    private static final String[] NOT_STURDY = {"leaves", "pumpkin", "melon", "barrier", "shulker_box"};

    /** A full block that fences and walls join: glass counts, leaves, pumpkins and melons do not (as in Minecraft). */
    private static boolean sturdy(BlockState s) {
        if (s.isAir()) return false;
        String p = s.path();
        if (p.equals("glass") || p.endsWith("_stained_glass") || p.equals("tinted_glass")) return true;
        for (String n : NOT_STURDY) if (p.contains(n)) return false;
        return solid(s);
    }

    // ---- orientation ------------------------------------------------------------------------------------------

    private static BlockState orient(BlockState s, Context c, World w, Blocks blocks) {
        Info info = blocks.info(s.name());
        Map<String, List<String>> allowed = info != null ? info.properties() : null;
        String p = s.path();
        Dir face = c.face(), look = c.horizontal();

        // Torches, signs, banners, heads and coral fans: the wall form on sides.
        String wall = wallVariant(s.name());
        Info wi = wall == null ? null : blocks.info(wall);
        if (wi != null || wall != null && s.has("rotation")) {
            boolean hanging = p.endsWith("_hanging_sign");
            boolean standing = hanging ? face == Dir.DOWN : face == Dir.UP;
            if (!standing && wi != null) {
                BlockState ws = carry(s, wi);
                Dir facing;
                if (hanging) {
                    // Hanging signs on a wall run across the clicked face.
                    Dir across = face.horizontal()
                            ? (face.axis().equals("x") ? (c.lookZ() > 0 ? Dir.SOUTH : Dir.NORTH) : (c.lookX() > 0 ? Dir.EAST : Dir.WEST))
                            : look;
                    facing = across.opposite();
                } else {
                    facing = face.horizontal() ? face : look.opposite();
                }
                return set(ws, wi.properties(), "facing", facing.id());
            }
            // Signs and banners face back at you (+180°); a head's model already faces you at the player's own yaw.
            boolean head = p.endsWith("_skull") || p.endsWith("_head");
            return set(s, allowed, "rotation", Integer.toString(rotation16(c.yaw() + (head ? 0 : 180))));
        }

        if (s.has("face") && values(allowed, s, "face").contains("floor")) {
            // Buttons, levers, grindstones.
            if (face == Dir.UP) return set(set(s, allowed, "face", "floor"), allowed, "facing", look.id());
            if (face == Dir.DOWN) return set(set(s, allowed, "face", "ceiling"), allowed, "facing", look.id());
            return set(set(s, allowed, "face", "wall"), allowed, "facing", face.id());
        }
        if (p.equals("bell") && s.has("attachment")) {
            if (!face.horizontal()) return set(set(s, allowed, "attachment", face == Dir.DOWN ? "ceiling" : "floor"), allowed, "facing", look.id());
            return set(set(s, allowed, "attachment", "single_wall"), allowed, "facing", face.opposite().id());
        }
        if (isStairs(s)) {
            s = set(s, allowed, "facing", look.id());
            return set(s, allowed, "half", topHalf(face, c.fracY(c.pos())) ? "top" : "bottom");
        }
        if (isSlab(s)) return set(s, allowed, "type", topHalf(face, c.fracY(c.pos())) ? "top" : "bottom");
        if (p.contains("trapdoor") && s.has("half") && s.has("facing")) {
            if (face.horizontal()) return set(set(s, allowed, "facing", face.id()), allowed, "half", c.fracY(c.pos()) > 0.5 ? "top" : "bottom");
            return set(set(s, allowed, "facing", look.opposite().id()), allowed, "half", face == Dir.UP ? "bottom" : "top");
        }
        if (s.has("hinge") && s.has("facing")) {
            s = set(s, allowed, "facing", look.id());
            return set(s, allowed, "hinge", doorHinge(s, look, c, w));
        }
        if (p.equals("hopper") && s.has("facing")) {
            Dir d = face.opposite();
            return set(s, allowed, "facing", d.horizontal() ? d.id() : "down");
        }
        if (p.equals("end_rod") || p.equals("lightning_rod") || p.endsWith("_lightning_rod")) {
            // A rod clicked onto the end of a matching rod continues it the same way round.
            BlockState behind = w.get(face.opposite().offset(c.pos()));
            boolean flip = behind.name().equals(s.name()) && face.id().equals(behind.get("facing"));
            return set(s, allowed, "facing", (flip ? face.opposite() : face).id());
        }
        if (p.equals("observer")) return set(s, allowed, "facing", c.nearest().id());

        if (s.has("facing")) {
            Dir f;
            if (p.equals("ladder") || p.equals("tripwire_hook") || p.endsWith("_wall_fan")) {
                f = face.horizontal() ? face : look.opposite();
            } else if (isBed(s) || p.endsWith("fence_gate") || p.endsWith("campfire") || p.equals("decorated_pot")) {
                f = look;
            } else if (p.endsWith("anvil")) {
                f = look.clockWise();
            } else if (p.endsWith("shulker_box") || p.contains("amethyst_") || p.endsWith("_bud")) {
                f = face;
            } else if (values(allowed, s, "facing").contains("up")) {
                // Pistons, dispensers, droppers, barrels and most modded directional blocks face the player.
                f = c.nearest().opposite();
            } else {
                // Furnaces, chests, glazed terracotta, repeaters, pumpkins and most modded machines face the player.
                f = look.opposite();
            }
            s = set(s, allowed, "facing", f.id());
        }
        if (s.has("axis")) s = set(s, allowed, "axis", face.axis());
        if (s.has("rotation")) s = set(s, allowed, "rotation", Integer.toString(rotation16(c.yaw() + 180)));
        if (s.has("hanging")) s = set(s, allowed, "hanging", Boolean.toString(face == Dir.DOWN));
        if (s.has("vertical_direction")) s = set(s, allowed, "vertical_direction", face == Dir.DOWN ? "down" : "up");
        if (p.contains("rail") && s.has("shape")) s = set(s, allowed, "shape", look.axis().equals("x") ? "east_west" : "north_south");
        return s;
    }

    /** Minecraft's rule for stairs and slabs: the top half unless clicking a top face or the lower half of a side. */
    private static boolean topHalf(Dir face, double fracY) {
        return !(face != Dir.DOWN && (face == Dir.UP || fracY <= 0.5));
    }

    private static int rotation16(float degrees) {
        return Math.floorMod(Math.round(degrees / 22.5f), 16);
    }

    // ---- wall / standing variants -------------------------------------------------------------------------------

    private static final String[][] WALL_SUFFIXES = {
            {"_hanging_sign", "_wall_hanging_sign"},
            {"_sign", "_wall_sign"},
            {"_banner", "_wall_banner"},
            {"torch", "wall_torch"},
            {"_skull", "_wall_skull"},
            {"_head", "_wall_head"},
            {"coral_fan", "coral_wall_fan"},
    };

    /** The wall form's id of a standing block, or null. */
    static String wallVariant(String id) {
        for (String[] s : WALL_SUFFIXES) {
            if (id.endsWith(s[1])) return null;
            if (id.endsWith(s[0])) return id.substring(0, id.length() - s[0].length()) + s[1];
        }
        return null;
    }

    /** A picked wall torch, sign and so on goes back to its standing form, which then picks the right one. */
    private static BlockState standing(BlockState s, Blocks blocks) {
        for (String[] suf : WALL_SUFFIXES) {
            if (!s.name().endsWith(suf[1])) continue;
            String id = s.name().substring(0, s.name().length() - suf[1].length()) + suf[0];
            Info i = blocks.info(id);
            return i != null ? carry(s, i) : s;
        }
        return s;
    }

    /** The block's default state, keeping the values it shares with {@code from} (lit, waterlogged, …). */
    private static BlockState carry(BlockState from, Info to) {
        BlockState out = to.defaultState();
        for (var e : from.properties().entrySet()) {
            if (e.getKey().equals("facing") || e.getKey().equals("rotation")) continue;
            if (out.has(e.getKey())) out = set(out, to.properties(), e.getKey(), e.getValue());
        }
        return out;
    }

    // ---- slabs ------------------------------------------------------------------------------------------------

    private static boolean isSlab(BlockState s) {
        String t = s.get("type");
        return s.path().endsWith("slab") && ("bottom".equals(t) || "top".equals(t) || "double".equals(t));
    }

    /** Where a slab would double an existing one: the clicked slab itself, or one already in the placement cell. */
    private static BlockPos slabMerge(BlockState held, Context c, World w) {
        if (c.clicked() != null) {
            BlockState at = w.get(c.clicked());
            if (at.name().equals(held.name())) {
                boolean upper = c.fracY(c.clicked()) > 0.5;
                if ("bottom".equals(at.get("type")) && (c.face() == Dir.UP || upper && c.face().horizontal())) return c.clicked();
                if ("top".equals(at.get("type")) && (c.face() == Dir.DOWN || !upper && c.face().horizontal())) return c.clicked();
            }
        }
        BlockState at = w.get(c.pos());
        if (at.name().equals(held.name()) && !"double".equals(at.get("type"))) return c.pos();
        return null;
    }

    // ---- stairs -----------------------------------------------------------------------------------------------

    private static boolean isStairs(BlockState s) {
        return s.path().endsWith("stairs") && s.has("facing") && s.has("half") && s.has("shape");
    }

    /** Minecraft's StairBlock.getStairsShape. */
    static String stairsShape(BlockState st, BlockPos pos, World w) {
        Dir facing = Dir.parse(st.get("facing"));
        if (facing == null || !facing.horizontal()) return "straight";
        BlockState front = w.get(facing.offset(pos));
        if (isStairs(front) && st.get("half").equals(front.get("half"))) {
            Dir f = Dir.parse(front.get("facing"));
            if (f != null && !f.axis().equals(facing.axis()) && canTakeShape(st, pos, f.opposite(), w)) {
                return f == facing.counterClockWise() ? "outer_left" : "outer_right";
            }
        }
        BlockState back = w.get(facing.opposite().offset(pos));
        if (isStairs(back) && st.get("half").equals(back.get("half"))) {
            Dir f = Dir.parse(back.get("facing"));
            if (f != null && !f.axis().equals(facing.axis()) && canTakeShape(st, pos, f, w)) {
                return f == facing.counterClockWise() ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    private static boolean canTakeShape(BlockState st, BlockPos pos, Dir side, World w) {
        BlockState o = w.get(side.offset(pos));
        return !isStairs(o) || !st.get("facing").equals(o.get("facing")) || !st.get("half").equals(o.get("half"));
    }

    // ---- doors and beds ------------------------------------------------------------------------------------------

    private static boolean isBed(BlockState s) {
        return s.path().endsWith("_bed") || s.path().equals("bed");
    }

    /** Minecraft's DoorBlock.getHinge: away from solid blocks and other doors, otherwise the side you click. */
    private static String doorHinge(BlockState door, Dir facing, Context c, World w) {
        BlockPos pos = c.pos(), above = pos.add(0, 1, 0);
        Dir left = facing.counterClockWise(), right = facing.clockWise();
        BlockState l = w.get(left.offset(pos)), lu = w.get(left.offset(above));
        BlockState r = w.get(right.offset(pos)), ru = w.get(right.offset(above));
        int i = (solid(l) ? -1 : 0) + (solid(lu) ? -1 : 0) + (solid(r) ? 1 : 0) + (solid(ru) ? 1 : 0);
        boolean doorLeft = l.name().equals(door.name()) && "lower".equals(l.get("half"));
        boolean doorRight = r.name().equals(door.name()) && "lower".equals(r.get("half"));
        if ((doorLeft && !doorRight) || i > 0) return "right";
        if ((doorRight && !doorLeft) || i < 0) return "left";
        double dx = c.hitX() - pos.x(), dz = c.hitZ() - pos.z();
        int j = facing.x, k = facing.z;
        boolean leftHinge = (j >= 0 || dz >= 0.5) && (j <= 0 || dz <= 0.5) && (k >= 0 || dx <= 0.5) && (k <= 0 || dx >= 0.5);
        return leftHinge ? "left" : "right";
    }

    private static final String[] NOT_SOLID = {"slab", "stairs", "door", "fence", "wall", "pane", "bars", "torch", "sign",
            "banner", "button", "lever", "carpet", "pressure_plate", "rail", "flower", "sapling", "grass", "fern", "ladder",
            "chain", "lantern", "candle", "rod", "head", "skull", "bed", "glass", "leaves", "vine"};

    /** Rough stand-in for "full collision block". */
    private static boolean solid(BlockState s) {
        if (s.isAir()) return false;
        String p = s.path();
        for (String n : NOT_SOLID) if (p.contains(n)) return false;
        return true;
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static List<String> values(Map<String, List<String>> allowed, BlockState s, String key) {
        if (allowed != null && allowed.containsKey(key)) return allowed.get(key);
        String v = s.get(key);
        return v == null ? List.of() : List.of(v);
    }

    /** Sets a property the block has, to a value it allows (anything goes when the allowed values are unknown). */
    private static BlockState set(BlockState s, Map<String, List<String>> allowed, String key, String value) {
        if (!s.has(key)) return s;
        if (allowed != null) {
            List<String> vs = allowed.get(key);
            if (vs != null && !vs.isEmpty() && !vs.contains(value)) return s;
        }
        return s.with(key, value);
    }

    private static BlockState withIf(BlockState s, String key, String value) {
        return s.has(key) ? s.with(key, value) : s;
    }
}
