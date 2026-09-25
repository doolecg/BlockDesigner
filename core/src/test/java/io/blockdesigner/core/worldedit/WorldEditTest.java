package io.blockdesigner.core.worldedit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.place.BlockPlacement.Dir;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WorldEditTest {
    final Map<BlockPos, BlockState> blocks = new HashMap<>();
    final WorldEdit.World world = new WorldEdit.World() {
        public BlockState get(BlockPos p) {
            return blocks.getOrDefault(p, BlockState.AIR);
        }

        public void set(BlockPos p, BlockState s) {
            if (s.isAir()) blocks.remove(p);
            else blocks.put(p, s);
        }
    };
    final WorldEdit we = new WorldEdit(new Random(1));
    final WorldEdit.Context ctx = new WorldEdit.Context(world, Dir.NORTH, null, BlockState.of("gold_block"),
            s -> BlockState.parse(s.contains(":") ? s : "minecraft:" + s));

    static final BlockState STONE = BlockState.of("stone"), DIRT = BlockState.of("dirt");

    WorldEdit.Result run(String cmd) {
        return we.run(cmd, ctx);
    }

    void box(int x0, int y0, int z0, int x1, int y1, int z1) {
        we.setPos1(new BlockPos(x0, y0, z0));
        we.setPos2(new BlockPos(x1, y1, z1));
    }

    @Test
    void setFillsTheRegionAndCountsChanges() {
        box(0, 0, 0, 2, 1, 3);
        WorldEdit.Result r = run("//set stone");
        assertThat(r.ok()).isTrue();
        assertThat(r.changed()).isEqualTo(24);
        assertThat(blocks).hasSize(24);
        assertThat(run("//set stone").changed()).isZero();
        assertThat(run("//count stone").message()).contains("24");
        assertThat(run("//set hand").changed()).isEqualTo(24);
        assertThat(blocks.values()).containsOnly(BlockState.of("gold_block"));
    }

    @Test
    void replaceUsesTheMaskAndMixes() {
        box(0, 0, 0, 9, 0, 9);
        run("//set 50%stone,50%dirt");
        assertThat(blocks.values()).contains(STONE, DIRT);
        run("//replace dirt air");
        assertThat(blocks.values()).containsOnly(STONE);
        run("//replace glass");
        assertThat(blocks.values()).containsOnly(BlockState.of("glass"));
    }

    @Test
    void wallsSkipTheInsideAndFacesAddCaps() {
        box(0, 0, 0, 4, 2, 4);
        assertThat(run("//walls stone").changed()).isEqualTo(3 * 16);
        blocks.clear();
        assertThat(run("//faces stone").changed()).isEqualTo(5 * 5 * 3 - 3 * 3);
    }

    @Test
    void copyPasteRelativeToPos1() {
        box(0, 0, 0, 1, 0, 0);
        blocks.put(new BlockPos(0, 0, 0), STONE);
        blocks.put(new BlockPos(1, 0, 0), DIRT);
        run("//copy");
        we.setPos1(new BlockPos(10, 5, 10));
        run("//paste");
        assertThat(blocks).containsEntry(new BlockPos(10, 5, 10), STONE).containsEntry(new BlockPos(11, 5, 10), DIRT);
        run("//rotate 90");
        we.setPos1(new BlockPos(20, 0, 0));
        run("//paste");
        // Clockwise from above, +X turns to +Z.
        assertThat(blocks).containsEntry(new BlockPos(20, 0, 0), STONE).containsEntry(new BlockPos(20, 0, 1), DIRT);
    }

    @Test
    void stackRepeatsAndMoveCarriesTheRegion() {
        box(0, 0, 0, 1, 1, 1);
        run("//set stone");
        assertThat(run("//stack 2 up").changed()).isEqualTo(16);
        assertThat(blocks).containsKey(new BlockPos(0, 5, 0));
        blocks.clear();
        run("//set stone");
        run("//move 3 east");
        assertThat(blocks).containsKey(new BlockPos(3, 0, 0)).doesNotContainKey(new BlockPos(0, 0, 0));
        assertThat(we.pos1()).isEqualTo(new BlockPos(3, 0, 0));
    }

    @Test
    void expandContractAndShiftMoveTheRightCorner() {
        box(0, 0, 0, 4, 4, 4);
        run("//expand 3 up");
        assertThat(we.region().maxY()).isEqualTo(7);
        run("//expand 2 down");
        assertThat(we.region().minY()).isEqualTo(-2);
        run("//contract 1 north");
        assertThat(we.region().minZ()).isEqualTo(1);
        run("//shift 10 east");
        assertThat(we.region().minX()).isEqualTo(10);
        run("//outset 1");
        assertThat(we.region().sizeX()).isEqualTo(7);
    }

    @Test
    void shapesAreSymmetricAndHollowIsThinner() {
        we.setPos1(new BlockPos(0, 64, 0));
        int solid = run("//sphere stone 4").changed();
        blocks.clear();
        int hollow = run("//hsphere stone 4").changed();
        assertThat(hollow).isLessThan(solid).isGreaterThan(0);
        blocks.clear();
        run("//cyl stone 3 5");
        assertThat(blocks).containsKey(new BlockPos(3, 68, 0)).containsKey(new BlockPos(-3, 64, 0)).doesNotContainKey(new BlockPos(0, 69, 0));
        blocks.clear();
        assertThat(run("//pyramid stone 3").changed()).isEqualTo(25 + 9 + 1);
    }

    @Test
    void errorsAreFriendly() {
        assertThat(run("//set stone").ok()).isFalse();
        box(0, 0, 0, 1, 1, 1);
        assertThat(run("//nope").message()).contains("Unknown command");
        assertThat(run("//move 2 sideways").message()).contains("Unknown direction");
        assertThat(run("//paste").message()).contains("clipboard");
        assertThat(run("//undo").special()).isEqualTo(WorldEdit.Special.UNDO);
    }
}
