package io.blockdesigner.core.transform;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TiltTest {
    final BlockPos east = new BlockPos(1, 0, 0), south = new BlockPos(0, 0, 1);

    @Test
    void anticlockwiseAboutEastTipsUpToSouth() {
        // Right-hand rule about +X: up turns to south, south turns to down.
        Tilt t = new Tilt(east, 1);
        assertThat(t.apply(0, 1, 0)).isEqualTo(new BlockPos(0, 0, 1));
        assertThat(t.apply(0, 0, 1)).isEqualTo(new BlockPos(0, -1, 0));
        assertThat(t.apply(1, 0, 0)).isEqualTo(new BlockPos(1, 0, 0));
    }

    @Test
    void fourTurnsAndOppositeTurnsCancel() {
        BlockPos p = new BlockPos(3, -2, 7), pivot = new BlockPos(1, 1, 1);
        for (BlockPos axis : new BlockPos[]{east, south, new BlockPos(0, 1, 0), new BlockPos(0, 0, -1)}) {
            Tilt cw = new Tilt(axis, -1), ccw = new Tilt(axis, 1);
            assertThat(ccw.apply(cw.apply(p, pivot), pivot)).isEqualTo(p);
            BlockPos q = p;
            for (int i = 0; i < 4; i++) q = cw.apply(q, pivot);
            assertThat(q).isEqualTo(p);
        }
    }

    @Test
    void logsAndFacingTurn() {
        Tilt t = new Tilt(east, 1);
        assertThat(t.apply(BlockState.parse("minecraft:oak_log[axis=y]"), s -> true).get("axis")).isEqualTo("z");
        assertThat(t.apply(BlockState.parse("minecraft:oak_log[axis=x]"), s -> true).get("axis")).isEqualTo("x");
        assertThat(t.apply(BlockState.parse("minecraft:observer[facing=up]"), s -> true).get("facing")).isEqualTo("south");
    }

    @Test
    void invalidTurnedValuesAreKept() {
        // Stairs cannot face up: the facing stays as it was.
        Set<String> horizontal = Set.of("north", "south", "east", "west");
        Tilt t = new Tilt(east, 1);
        BlockState stairs = BlockState.parse("minecraft:oak_stairs[facing=south,half=bottom]");
        assertThat(t.apply(stairs, s -> horizontal.contains(s.get("facing")))).isEqualTo(stairs);
    }

    @Test
    void wallButtonTipsOntoTheFloor() {
        // Attached to the block south of it (facing north); tipping so south goes down puts it on the floor.
        Tilt t = new Tilt(east, 1);
        BlockState b = t.apply(BlockState.parse("minecraft:stone_button[face=wall,facing=north]"), s -> true);
        assertThat(b.get("face")).isEqualTo("floor");
        assertThat(b.get("facing")).isEqualTo("north");
    }

    @Test
    void sixSidedFlagsPermute() {
        Tilt t = new Tilt(east, 1);
        BlockState m = t.apply(BlockState.parse("minecraft:brown_mushroom_block[up=true,down=false,north=false,south=false,east=false,west=false]"), s -> true);
        assertThat(m.get("south")).isEqualTo("true");
        assertThat(m.get("up")).isEqualTo("false");
    }
}
