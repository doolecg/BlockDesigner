package io.blockdesigner.core.edit;

import io.blockdesigner.core.edit.Sculpt.Brush;
import io.blockdesigner.core.edit.Sculpt.Mode;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SculptTest {
    static final BlockState STONE = BlockState.of("stone"), GRASS = BlockState.of("grass_block");
    final Map<BlockPos, BlockState> blocks = new HashMap<>();
    final Sculpt.World w = new Sculpt.World() {
        public BlockState get(BlockPos p) {
            return blocks.getOrDefault(p, BlockState.AIR);
        }

        public void set(BlockPos p, BlockState s) {
            if (s.isAir()) blocks.remove(p);
            else blocks.put(p, s);
        }
    };

    /** A flat 21×21 floor of stone at y = 0. */
    void floor() {
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) blocks.put(new BlockPos(x, 0, z), STONE);
    }

    int topAt(int x, int z) {
        for (int y = 40; y >= -10; y--) if (blocks.containsKey(new BlockPos(x, y, z))) return y;
        return Integer.MIN_VALUE;
    }

    java.util.List<BlockPos> run(Mode m, int size, int strength, BlockPos c) {
        return Sculpt.apply(m, false, new Brush(size, false, strength), c, w, () -> GRASS, null, y -> true);
    }

    @Test
    void drawAndEraseFillAndClearTheSphere() {
        int drawn = run(Mode.DRAW, 3, 1, BlockPos.ORIGIN).size();
        assertThat(drawn).isEqualTo(Sculpt.shape(new Brush(3, false, 1), BlockPos.ORIGIN).size());
        assertThat(run(Mode.ERASE, 3, 1, BlockPos.ORIGIN)).hasSize(drawn);
        assertThat(blocks).isEmpty();
    }

    @Test
    void raiseMakesAHillThatSlopesAway() {
        floor();
        run(Mode.RAISE, 5, 4, new BlockPos(0, 0, 0));
        assertThat(topAt(0, 0)).isEqualTo(4);
        assertThat(topAt(2, 0)).isBetween(1, 3);
        assertThat(topAt(8, 0)).isZero();
        run(Mode.LOWER, 5, 4, new BlockPos(0, 4, 0));
        assertThat(topAt(0, 0)).isZero();
    }

    @Test
    void smoothKnocksOffASpikeAndErodeWearsCorners() {
        floor();
        blocks.put(new BlockPos(0, 1, 0), STONE);
        blocks.put(new BlockPos(0, 2, 0), STONE);
        run(Mode.SMOOTH, 3, 1, new BlockPos(0, 2, 0));
        assertThat(blocks).doesNotContainKey(new BlockPos(0, 2, 0));
        // The floor under the spike survives.
        assertThat(blocks).containsKey(new BlockPos(0, 0, 0)).containsKey(new BlockPos(1, 0, 0));

        blocks.clear();
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) blocks.put(new BlockPos(x, y, z), STONE);
        run(Mode.ERODE, 3, 1, new BlockPos(1, 1, 1));
        assertThat(blocks).doesNotContainKey(new BlockPos(0, 0, 0)).containsKey(new BlockPos(1, 1, 1));
    }

    @Test
    void smoothingTheTopRoundsAHillButKeepsTheFloorAndTopBlocks() {
        floor();
        // A steep 3×3 tower of stone capped with grass, 6 high.
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                for (int y = 1; y < 6; y++) blocks.put(new BlockPos(x, y, z), STONE);
                blocks.put(new BlockPos(x, 6, z), GRASS);
            }
        Sculpt.apply(Mode.SMOOTH, false, new Brush(6, false, 3), new BlockPos(0, 6, 0), new BlockPos(0, 1, 0), w, null, null, y -> true);
        // Lower in the middle, raised a little around it, floor untouched far away, grass still on top.
        assertThat(topAt(0, 0)).isLessThan(6).isGreaterThan(0);
        assertThat(topAt(3, 0)).isGreaterThan(0);
        assertThat(topAt(10, 10)).isZero();
        assertThat(blocks.get(new BlockPos(0, topAt(0, 0), 0))).isEqualTo(GRASS);
    }

    @Test
    void smoothingASideRoundsACorner() {
        // A solid 9×9×9 cube; smoothing at a corner edge rounds it off but keeps the inside.
        for (int x = 0; x < 9; x++) for (int y = 0; y < 9; y++) for (int z = 0; z < 9; z++) blocks.put(new BlockPos(x, y, z), STONE);
        Sculpt.apply(Mode.SMOOTH, false, new Brush(4, false, 2), new BlockPos(8, 8, 8), new BlockPos(1, 0, 0), w, null, null, y -> true);
        assertThat(blocks).doesNotContainKey(new BlockPos(8, 8, 8)).containsKey(new BlockPos(4, 4, 4));
    }

    @Test
    void fillPlugsAHole() {
        floor();
        blocks.remove(new BlockPos(0, 0, 0));
        run(Mode.FILL, 2, 1, new BlockPos(0, 0, 0));
        assertThat(blocks).containsKey(new BlockPos(0, 0, 0));
    }

    @Test
    void flattenLevelsToTheClickedHeight() {
        floor();
        blocks.put(new BlockPos(1, 1, 0), STONE);
        blocks.put(new BlockPos(1, 2, 0), STONE);
        blocks.remove(new BlockPos(-1, 0, 0));
        run(Mode.FLATTEN, 3, 1, new BlockPos(0, 0, 0));
        assertThat(topAt(1, 0)).isZero();
        assertThat(blocks).containsKey(new BlockPos(-1, 0, 0));
    }

    @Test
    void slopeRampsUpFromTheStart() {
        floor();
        for (int x = 0; x <= 8; x += 2) {
            Sculpt.apply(Mode.SLOPE, false, new Brush(2, false, 2), new BlockPos(x, 0, 0), w, () -> GRASS, new BlockPos(0, 0, 0), y -> true);
        }
        // Strength 2 = half a block up per block along.
        assertThat(topAt(0, 0)).isZero();
        assertThat(topAt(4, 0)).isEqualTo(2);
        assertThat(topAt(8, 0)).isEqualTo(4);
    }

    @Test
    void pinchPullsBlocksInAndTheSliceLimitHolds() {
        blocks.put(new BlockPos(2, 0, 0), STONE);
        run(Mode.PINCH, 3, 1, BlockPos.ORIGIN);
        assertThat(blocks).containsKey(new BlockPos(1, 0, 0)).doesNotContainKey(new BlockPos(2, 0, 0));

        blocks.clear();
        Sculpt.apply(Mode.DRAW, false, new Brush(3, true, 1), BlockPos.ORIGIN, w, () -> GRASS, null, y -> y <= 0);
        assertThat(blocks.keySet()).allMatch(p -> p.y() <= 0);
    }
}
