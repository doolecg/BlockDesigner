package io.blockdesigner.core.place;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SymmetryTest {
    static final BlockState STONE = BlockState.of("stone");

    @Test
    void offKeepsOnlyTheOriginal() {
        assertThat(Symmetry.OFF.apply(new BlockPos(1, 2, 3), STONE)).containsOnlyKeys(new BlockPos(1, 2, 3));
    }

    @Test
    void mirrorAboutABlockCentreAndAboutABoundary() {
        // Centre on block x=0: block 3 mirrors to -3; the centre column maps onto itself.
        Symmetry onBlock = new Symmetry(true, false, false, 1, 0, 0, 0, true).centeredOn(new BlockPos(0, 0, 0), true);
        assertThat(onBlock.apply(new BlockPos(3, 5, 1), STONE)).containsOnlyKeys(new BlockPos(3, 5, 1), new BlockPos(-3, 5, 1));
        assertThat(onBlock.apply(new BlockPos(0, 5, 1), STONE)).hasSize(1);
        // Centre on the boundary x=0 (between -1 and 0): block 0 mirrors to -1.
        Symmetry between = new Symmetry(true, false, false, 1, 0, 0, 0, true);
        assertThat(between.apply(new BlockPos(0, 5, 1), STONE)).containsOnlyKeys(new BlockPos(0, 5, 1), new BlockPos(-1, 5, 1));
    }

    @Test
    void threeMirrorsMakeEightCopiesWithFlippedStates() {
        Symmetry s = new Symmetry(true, true, true, 1, 0, 0, 0, true).centeredOn(BlockPos.ORIGIN, true);
        BlockState stair = BlockState.parse("oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        Map<BlockPos, BlockState> out = s.apply(new BlockPos(2, 3, 4), stair);
        assertThat(out).hasSize(8);
        assertThat(out.get(new BlockPos(-2, 3, 4)).get("facing")).isEqualTo("west");
        assertThat(out.get(new BlockPos(2, -3, 4)).get("half")).isEqualTo("top");
        assertThat(out.get(new BlockPos(-2, -3, -4)).get("facing")).isEqualTo("west");
        assertThat(out.get(new BlockPos(-2, -3, -4)).get("half")).isEqualTo("top");
    }

    @Test
    void radialFourIsExactQuarterTurns() {
        Symmetry s = new Symmetry(false, false, false, 4, 0, 0, 0, true).centeredOn(BlockPos.ORIGIN, true);
        BlockState stair = BlockState.parse("oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        Map<BlockPos, BlockState> out = s.apply(new BlockPos(5, 0, 0), stair);
        assertThat(out).containsOnlyKeys(new BlockPos(5, 0, 0), new BlockPos(0, 0, 5), new BlockPos(-5, 0, 0), new BlockPos(0, 0, -5));
        assertThat(out.get(new BlockPos(0, 0, 5)).get("facing")).isEqualTo("south");
        assertThat(out.get(new BlockPos(-5, 0, 0)).get("facing")).isEqualTo("west");
        assertThat(out.get(new BlockPos(0, 0, -5)).get("facing")).isEqualTo("north");
    }

    @Test
    void radialSixSpreadsCopiesAround() {
        Symmetry s = new Symmetry(false, false, false, 6, 0, 0, 0, true).centeredOn(BlockPos.ORIGIN, true);
        Map<BlockPos, BlockState> out = s.apply(new BlockPos(10, 0, 0), STONE);
        assertThat(out).hasSize(6);
        for (BlockPos p : out.keySet()) assertThat(Math.hypot(p.x(), p.z())).isBetween(9.0, 11.0);
        assertThat(s.copies()).isEqualTo(6);
    }

    @Test
    void positionsOnlyForBreaking() {
        Symmetry s = new Symmetry(false, false, true, 1, 0, 0, 0, true).centeredOn(new BlockPos(0, 0, 10), false);
        assertThat(s.apply(new BlockPos(0, 0, 12), null)).containsOnlyKeys(new BlockPos(0, 0, 12), new BlockPos(0, 0, 7));
    }
}
