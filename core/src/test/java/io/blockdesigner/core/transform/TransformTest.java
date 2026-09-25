package io.blockdesigner.core.transform;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransformTest {
    final BlockTransformer bt = BlockTransformer.defaults();
    final Transform cw = Transform.rotation(1);
    final Transform mirrorX = new Transform(0, Transform.Mirror.X);
    final Transform mirrorZ = new Transform(0, Transform.Mirror.Z);

    @Test
    void clockwiseRotationMapsNorthToEast() {
        // North is -Z; one clockwise turn (viewed from above) points it east (+X).
        assertThat(cw.apply(0, 0, -1)).isEqualTo(new BlockPos(1, 0, 0));
        assertThat(Transform.rotation(4).apply(3, 1, 2)).isEqualTo(new BlockPos(3, 1, 2));
    }

    @Test
    void compositionAndInverse() {
        Transform[] all = new Transform[12];
        int n = 0;
        for (Transform.Mirror m : Transform.Mirror.values()) for (int r = 0; r < 4; r++) all[n++] = new Transform(r, m);
        BlockPos p = new BlockPos(3, 5, -7);
        for (Transform a : all) {
            assertThat(a.inverse().apply(a.apply(p))).isEqualTo(p);
            for (Transform b : all) assertThat(a.then(b).apply(p)).isEqualTo(b.apply(a.apply(p)));
        }
    }

    @Test
    void entityPositionsStayInsideTheirBlock() {
        for (int r = 0; r < 4; r++) {
            Transform t = Transform.rotation(r);
            BlockPos cell = t.apply(2, 0, 5);
            double[] e = t.apply(2.25, 0, 5.75);
            assertThat((int) Math.floor(e[0])).isEqualTo(cell.x());
            assertThat((int) Math.floor(e[2])).isEqualTo(cell.z());
        }
    }

    @Test
    void stairsRotateAndMirror() {
        BlockState stair = BlockState.parse("oak_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]");
        assertThat(bt.apply(stair, cw).get("facing")).isEqualTo("east");
        assertThat(bt.apply(stair, cw).get("shape")).isEqualTo("inner_left");
        BlockState m = bt.apply(stair, mirrorZ);
        assertThat(m.get("facing")).isEqualTo("south");
        assertThat(m.get("shape")).isEqualTo("inner_right");
        assertThat(bt.apply(stair, mirrorX).get("facing")).isEqualTo("north");
    }

    @Test
    void fenceSidesPermute() {
        BlockState fence = BlockState.parse("oak_fence[north=true,east=false,south=false,west=false,waterlogged=false]");
        BlockState r = bt.apply(fence, cw);
        assertThat(r.get("east")).isEqualTo("true");
        assertThat(r.get("north")).isEqualTo("false");
        BlockState wall = BlockState.parse("cobblestone_wall[north=tall,east=low,south=none,west=none,up=true,waterlogged=false]");
        assertThat(bt.apply(wall, Transform.rotation(2)).get("south")).isEqualTo("tall");
        assertThat(bt.apply(wall, Transform.rotation(2)).get("west")).isEqualTo("low");
    }

    @Test
    void axisSwapsOnQuarterTurns() {
        BlockState log = BlockState.parse("oak_log[axis=x]");
        assertThat(bt.apply(log, cw).get("axis")).isEqualTo("z");
        assertThat(bt.apply(log, Transform.rotation(2)).get("axis")).isEqualTo("x");
        assertThat(bt.apply(BlockState.parse("oak_log[axis=y]"), cw).get("axis")).isEqualTo("y");
    }

    @Test
    void signRotation16() {
        BlockState sign = BlockState.parse("oak_sign[rotation=0,waterlogged=false]"); // facing south
        assertThat(bt.apply(sign, cw).get("rotation")).isEqualTo("4"); // west
        assertThat(bt.apply(sign, mirrorZ).get("rotation")).isEqualTo("8"); // north
        assertThat(bt.apply(sign.with("rotation", "3"), mirrorX).get("rotation")).isEqualTo("13");
    }

    @Test
    void railShapesStayCanonical() {
        BlockState rail = BlockState.parse("rail[shape=north_south,waterlogged=false]");
        assertThat(bt.apply(rail, cw).get("shape")).isEqualTo("east_west");
        assertThat(bt.apply(rail.with("shape", "south_east"), cw).get("shape")).isEqualTo("south_west");
        assertThat(bt.apply(rail.with("shape", "north_east"), cw).get("shape")).isEqualTo("south_east");
        assertThat(bt.apply(rail.with("shape", "ascending_north"), cw).get("shape")).isEqualTo("ascending_east");
    }

    @Test
    void doorHingeFlipsOnMirror() {
        BlockState door = BlockState.parse("oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]");
        BlockState m = bt.apply(door, mirrorX);
        assertThat(m.get("facing")).isEqualTo("west");
        assertThat(m.get("hinge")).isEqualTo("right");
        assertThat(bt.apply(door, cw).get("hinge")).isEqualTo("left");
    }

    @Test
    void jigsawOrientationTokens() {
        BlockState j = BlockState.parse("jigsaw[orientation=north_up]");
        assertThat(bt.apply(j, cw).get("orientation")).isEqualTo("east_up");
        assertThat(bt.apply(j.with("orientation", "down_south"), cw).get("orientation")).isEqualTo("down_west");
    }

    @Test
    void slabTypeUnaffectedByMirror() {
        BlockState slab = BlockState.parse("stone_slab[type=top,waterlogged=false]");
        assertThat(bt.apply(slab, mirrorX)).isSameAs(slab);
    }
}
