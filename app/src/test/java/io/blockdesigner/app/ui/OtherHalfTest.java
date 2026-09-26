package io.blockdesigner.app.ui;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Breaking one half of a door, tall plant or bed finds the other half. */
class OtherHalfTest {
    private final Map<BlockPos, BlockState> world = new HashMap<>();

    private BlockPos other(BlockPos p) {
        return ViewportPane.otherHalf(p, world.get(p), q -> world.getOrDefault(q, BlockState.AIR));
    }

    @Test
    void doorsAndTallPlantsGoUpAndDown() {
        BlockPos lo = new BlockPos(0, 64, 0), hi = new BlockPos(0, 65, 0);
        world.put(lo, BlockState.parse("oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]"));
        world.put(hi, BlockState.parse("oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]"));
        assertThat(other(lo)).isEqualTo(hi);
        assertThat(other(hi)).isEqualTo(lo);

        BlockPos g = new BlockPos(3, 64, 0), g2 = new BlockPos(3, 65, 0);
        world.put(g, BlockState.parse("tall_grass[half=lower]"));
        world.put(g2, BlockState.parse("tall_grass[half=upper]"));
        assertThat(other(g2)).isEqualTo(g);
    }

    @Test
    void bedsGoHeadToFoot() {
        BlockPos foot = new BlockPos(0, 64, 0), head = new BlockPos(0, 64, -1);
        world.put(foot, BlockState.parse("red_bed[facing=north,occupied=false,part=foot]"));
        world.put(head, BlockState.parse("red_bed[facing=north,occupied=false,part=head]"));
        assertThat(other(foot)).isEqualTo(head);
        assertThat(other(head)).isEqualTo(foot);
    }

    @Test
    void notAPairOrMissingHalf() {
        BlockPos p = new BlockPos(0, 64, 0);
        // Stairs and trapdoors use half=top/bottom: one block each.
        world.put(p, BlockState.parse("oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"));
        assertThat(other(p)).isNull();
        // A lone lower half (the upper one already gone or another block there).
        world.put(p, BlockState.parse("oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]"));
        world.put(new BlockPos(0, 65, 0), BlockState.of("stone"));
        assertThat(other(p)).isNull();
    }
}
