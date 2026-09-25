package io.blockdesigner.core.place;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.place.BlockPlacement.Context;
import io.blockdesigner.core.place.BlockPlacement.Dir;
import io.blockdesigner.core.place.BlockPlacement.Info;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockPlacementTest {
    static final List<String> H4 = List.of("north", "east", "south", "west");
    static final List<String> H6 = List.of("north", "east", "south", "west", "up", "down");
    static final Map<String, Info> BLOCKS = new HashMap<>();

    static {
        add("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                Map.of("facing", H4, "half", List.of("top", "bottom"), "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")));
        add("minecraft:oak_slab[type=bottom,waterlogged=false]", Map.of("type", List.of("top", "bottom", "double")));
        add("minecraft:oak_log[axis=y]", Map.of("axis", List.of("x", "y", "z")));
        add("minecraft:torch", Map.of());
        add("minecraft:wall_torch[facing=north]", Map.of("facing", H4));
        add("minecraft:oak_sign[rotation=0,waterlogged=false]", Map.of("rotation", List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15")));
        add("minecraft:oak_wall_sign[facing=north,waterlogged=false]", Map.of("facing", H4));
        add("minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
                Map.of("facing", H4, "half", List.of("upper", "lower"), "hinge", List.of("left", "right")));
        add("minecraft:red_bed[facing=north,occupied=false,part=foot]", Map.of("facing", H4, "part", List.of("head", "foot")));
        add("minecraft:furnace[facing=north,lit=false]", Map.of("facing", H4));
        add("minecraft:piston[extended=false,facing=north]", Map.of("facing", H6));
        add("minecraft:oak_trapdoor[facing=north,half=bottom,open=false]", Map.of("facing", H4, "half", List.of("top", "bottom")));
        add("minecraft:stone_button[face=wall,facing=north,powered=false]", Map.of("face", List.of("floor", "wall", "ceiling"), "facing", H4));
    }

    static void add(String state, Map<String, List<String>> props) {
        BlockState s = BlockState.parse(state);
        BLOCKS.put(s.name(), new Info(s, props));
    }

    final Map<BlockPos, BlockState> world = new HashMap<>();
    final BlockPlacement.World w = p -> world.getOrDefault(p, BlockState.AIR);
    final BlockPlacement.Blocks blocks = BLOCKS::get;

    static BlockState def(String id) {
        return BLOCKS.get("minecraft:" + id).defaultState();
    }

    /** Clicks {@code face} of the block at {@code clicked}, {@code fy} of the way up, looking along {@code look}. */
    Map<BlockPos, BlockState> click(BlockState held, BlockPos clicked, Dir face, double fy, float lx, float ly, float lz) {
        BlockPos pos = face.offset(clicked);
        double hx = clicked.x() + 0.5 + face.x * 0.5, hz = clicked.z() + 0.5 + face.z * 0.5;
        double hy = face == Dir.UP ? clicked.y() + 1 : face == Dir.DOWN ? clicked.y() : clicked.y() + fy;
        return BlockPlacement.place(held, new Context(pos, clicked, face, hx, hy, hz, lx, ly, lz), w, blocks);
    }

    static final BlockPos O = BlockPos.ORIGIN;

    @Test
    void stairsFaceTheLookDirectionAndFlipOnUpperHalf() {
        world.put(O, BlockState.of("stone"));
        // Looking north onto the top: bottom half, facing north.
        assertThat(click(def("oak_stairs"), O, Dir.UP, 0, 0, -0.5f, -1).get(O.add(0, 1, 0)))
                .isEqualTo(def("oak_stairs").with("facing", "north"));
        // Upper half of a side: upside down.
        BlockState top = click(def("oak_stairs"), O, Dir.EAST, 0.8, -1, 0, 0).get(O.add(1, 0, 0));
        assertThat(top.get("half")).isEqualTo("top");
        assertThat(top.get("facing")).isEqualTo("west");
        assertThat(click(def("oak_stairs"), O, Dir.DOWN, 0, 0, 1, 1).get(O.add(0, -1, 0)).get("half")).isEqualTo("top");
    }

    @Test
    void stairsFormCornersWithNeighbours() {
        world.put(O, BlockState.of("stone"));
        world.put(new BlockPos(0, 1, -1), def("oak_stairs").with("facing", "east"));
        // New stair at (0,1,0) facing north; the one in front faces east, so this becomes an outer corner.
        BlockState st = click(def("oak_stairs"), O, Dir.UP, 0, 0, -0.5f, -1).get(O.add(0, 1, 0));
        assertThat(st.get("shape")).isEqualTo("outer_right");
    }

    @Test
    void slabsGoTopOrBottomAndDouble() {
        world.put(O, BlockState.of("stone"));
        assertThat(click(def("oak_slab"), O, Dir.NORTH, 0.7, 0, 0, 1).get(O.add(0, 0, -1)).get("type")).isEqualTo("top");
        assertThat(click(def("oak_slab"), O, Dir.NORTH, 0.3, 0, 0, 1).get(O.add(0, 0, -1)).get("type")).isEqualTo("bottom");
        world.put(O, def("oak_slab"));
        assertThat(click(def("oak_slab"), O, Dir.UP, 0, 0, -1, 0)).containsExactly(Map.entry(O, def("oak_slab").with("type", "double")));
    }

    @Test
    void logsFollowTheClickedAxis() {
        world.put(O, BlockState.of("stone"));
        assertThat(click(def("oak_log"), O, Dir.EAST, 0.5, -1, 0, 0).get(O.add(1, 0, 0)).get("axis")).isEqualTo("x");
        assertThat(click(def("oak_log"), O, Dir.UP, 0, 0, -1, 0).get(O.add(0, 1, 0)).get("axis")).isEqualTo("y");
    }

    @Test
    void torchesAndSignsUseTheirWallFormOnSides() {
        world.put(O, BlockState.of("stone"));
        assertThat(click(def("torch"), O, Dir.SOUTH, 0.5, 0, 0, -1).get(O.add(0, 0, 1))).isEqualTo(def("wall_torch").with("facing", "south"));
        assertThat(click(def("torch"), O, Dir.UP, 0, 0, -1, 0).get(O.add(0, 1, 0))).isEqualTo(def("torch"));
        // A picked wall torch placed on top stands up again.
        assertThat(click(def("wall_torch"), O, Dir.UP, 0, 0, -1, 0).get(O.add(0, 1, 0))).isEqualTo(def("torch"));
        // Standing sign looking north faces back at the player (rotation 0 = facing south).
        assertThat(click(def("oak_sign"), O, Dir.UP, 0, 0, -0.3f, -1).get(O.add(0, 1, 0)).get("rotation")).isEqualTo("0");
        assertThat(click(def("oak_sign"), O, Dir.WEST, 0.5, 1, 0, 0).get(O.add(-1, 0, 0)).name()).isEqualTo("minecraft:oak_wall_sign");
    }

    @Test
    void doorsAndBedsPlaceBothHalves() {
        world.put(O, BlockState.of("stone"));
        Map<BlockPos, BlockState> door = click(def("oak_door"), O, Dir.UP, 0, 0, -0.5f, -1);
        assertThat(door).hasSize(2);
        assertThat(door.get(O.add(0, 1, 0)).get("half")).isEqualTo("lower");
        assertThat(door.get(O.add(0, 2, 0)).get("half")).isEqualTo("upper");
        assertThat(door.get(O.add(0, 2, 0)).get("facing")).isEqualTo("north");

        Map<BlockPos, BlockState> bed = click(def("red_bed"), O, Dir.UP, 0, 1, -0.5f, 0);
        assertThat(bed.get(O.add(0, 1, 0))).isEqualTo(def("red_bed").with("facing", "east"));
        assertThat(bed.get(O.add(1, 1, 0)).get("part")).isEqualTo("head");

        world.put(O.add(0, 2, 0), BlockState.of("stone"));
        assertThat(click(def("oak_door"), O, Dir.UP, 0, 0, -0.5f, -1)).isEmpty();
    }

    @Test
    void machinesFaceThePlayer() {
        world.put(O, BlockState.of("stone"));
        assertThat(click(def("furnace"), O, Dir.UP, 0, 0, -0.3f, -1).get(O.add(0, 1, 0)).get("facing")).isEqualTo("south");
        // Looking steeply down: a piston points up at the player.
        assertThat(click(def("piston"), O, Dir.UP, 0, 0, -1, -0.2f).get(O.add(0, 1, 0)).get("facing")).isEqualTo("up");
    }

    @Test
    void trapdoorsAndButtonsAttachToTheClickedFace() {
        world.put(O, BlockState.of("stone"));
        BlockState td = click(def("oak_trapdoor"), O, Dir.EAST, 0.9, -1, 0, 0).get(O.add(1, 0, 0));
        assertThat(td.get("facing")).isEqualTo("east");
        assertThat(td.get("half")).isEqualTo("top");
        BlockState b = click(def("stone_button"), O, Dir.UP, 0, 0, -1, 1).get(O.add(0, 1, 0));
        assertThat(b.get("face")).isEqualTo("floor");
        assertThat(click(def("stone_button"), O, Dir.NORTH, 0.5, 0, 0, 1).get(O.add(0, 0, -1)).get("facing")).isEqualTo("north");
    }

    static final BlockState FENCE = BlockState.parse("minecraft:oak_fence[east=true,north=true,south=true,waterlogged=false,west=true]");
    static final BlockState WALL = BlockState.parse("minecraft:cobblestone_wall[east=low,north=low,south=low,up=true,waterlogged=false,west=low]");

    @Test
    void fencesOnlyConnectToFencesAndSolidBlocks() {
        world.put(O, BlockState.of("stone"));
        // Alone on top of stone: nothing around, so no arms (even though the held state had them all).
        BlockState alone = click(FENCE, O, Dir.UP, 0, 0, -1, -0.5f).get(O.add(0, 1, 0));
        assertThat(alone.properties()).containsEntry("north", "false").containsEntry("east", "false")
                .containsEntry("south", "false").containsEntry("west", "false");

        // Next to another fence: both join up.
        BlockPos a = O.add(0, 1, 0);
        world.put(a, alone);
        world.put(O.add(1, 0, 0), BlockState.of("stone"));
        Map<BlockPos, BlockState> next = click(FENCE, O.add(1, 0, 0), Dir.UP, 0, 0, -1, -0.5f);
        assertThat(next.get(O.add(1, 1, 0)).get("west")).isEqualTo("true");
        assertThat(next.get(a).get("east")).isEqualTo("true");

        // Breaking it: the first fence lets go.
        world.putAll(next);
        world.remove(O.add(1, 1, 0));
        assertThat(BlockPlacement.reconnect(List.of(O.add(1, 1, 0)), w).get(a).get("east")).isEqualTo("false");
    }

    @Test
    void wallsMakeStraightRunsWithoutPosts() {
        world.put(new BlockPos(-1, 0, 0), WALL.with("east", "low").with("west", "none").with("north", "none").with("south", "none"));
        world.put(new BlockPos(1, 0, 0), WALL.with("west", "low").with("east", "none").with("north", "none").with("south", "none"));
        world.put(new BlockPos(0, -1, 0), BlockState.of("stone"));
        BlockState mid = click(WALL, new BlockPos(0, -1, 0), Dir.UP, 0, 0, -1, -0.5f).get(O);
        assertThat(mid.get("east")).isEqualTo("low");
        assertThat(mid.get("west")).isEqualTo("low");
        assertThat(mid.get("north")).isEqualTo("none");
        assertThat(mid.get("up")).isEqualTo("false");
    }

    @Test
    void replaceKeepsTheOldBlocksFacingAndShape() {
        add("minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                Map.of("facing", H4, "half", List.of("top", "bottom"), "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")));
        add("minecraft:spruce_log[axis=y]", Map.of("axis", List.of("x", "y", "z")));
        world.put(O, def("oak_stairs").with("facing", "east").with("half", "top"));
        assertThat(BlockPlacement.replace(def("stone_brick_stairs"), O, w, blocks).get(O))
                .isEqualTo(def("stone_brick_stairs").with("facing", "east").with("half", "top"));

        world.put(O, def("oak_log").with("axis", "x"));
        assertThat(BlockPlacement.replace(def("spruce_log"), O, w, blocks).get(O).get("axis")).isEqualTo("x");

        // A wall torch replaced with a torch stays on its wall.
        world.put(O, def("wall_torch").with("facing", "west"));
        add("minecraft:soul_torch", Map.of());
        add("minecraft:soul_wall_torch[facing=north]", Map.of("facing", H4));
        assertThat(BlockPlacement.replace(def("soul_torch"), O, w, blocks).get(O)).isEqualTo(def("soul_wall_torch").with("facing", "west"));

        // Nothing to replace in air; the same block is a no-op.
        assertThat(BlockPlacement.replace(def("spruce_log"), O.add(5, 5, 5), w, blocks)).isEmpty();
        world.put(O, def("spruce_log"));
        assertThat(BlockPlacement.replace(def("spruce_log"), O, w, blocks)).isEmpty();
    }

    @Test
    void replacingADoorSwapsBothHalves() {
        add("minecraft:birch_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
                Map.of("facing", H4, "half", List.of("upper", "lower"), "hinge", List.of("left", "right")));
        BlockState lower = def("oak_door").with("facing", "south").with("hinge", "right");
        world.put(O, lower);
        world.put(O.add(0, 1, 0), lower.with("half", "upper"));
        Map<BlockPos, BlockState> r = BlockPlacement.replace(def("birch_door"), O.add(0, 1, 0), w, blocks);
        assertThat(r.get(O.add(0, 1, 0))).isEqualTo(def("birch_door").with("facing", "south").with("hinge", "right").with("half", "upper"));
        assertThat(r.get(O)).isEqualTo(def("birch_door").with("facing", "south").with("hinge", "right"));
    }

    @Test
    void occupiedCellsAreLeftAlone() {
        world.put(O, BlockState.of("stone"));
        world.put(O.add(0, 1, 0), BlockState.of("dirt"));
        assertThat(click(def("oak_log"), O, Dir.UP, 0, 0, -1, 0)).isEmpty();
    }
}
