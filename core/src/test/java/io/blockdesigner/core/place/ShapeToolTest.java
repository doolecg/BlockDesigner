package io.blockdesigner.core.place;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.place.ShapeTool.Shape;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShapeToolTest {
    static final BlockPos O = new BlockPos(0, 64, 0);

    @Test
    void singleLineWallFloor() {
        assertThat(ShapeTool.cells(Shape.SINGLE, O, new BlockPos(5, 64, 5), 1)).containsExactly(O);
        assertThat(ShapeTool.cells(Shape.LINE, O, new BlockPos(4, 64, 0), 1)).hasSize(5).allMatch(p -> p.y() == 64 && p.z() == 0);
        assertThat(ShapeTool.cells(Shape.LINE, O, new BlockPos(0, 70, 0), 1)).hasSize(7);
        assertThat(ShapeTool.cells(Shape.WALL, O, new BlockPos(3, 67, 0), 1)).hasSize(16);
        assertThat(ShapeTool.cells(Shape.FLOOR, O, new BlockPos(-2, 64, 3), 1)).hasSize(12);
    }

    @Test
    void boxesUseTheHeight() {
        BlockPos b = new BlockPos(4, 64, 3);
        assertThat(ShapeTool.cells(Shape.BOX, O, b, 3)).hasSize(5 * 4 * 3);
        // 5x4x3 box minus its 3x2x1 inside.
        assertThat(ShapeTool.cells(Shape.HOLLOW_BOX, O, b, 3)).hasSize(60 - 3 * 2 * 1);
        // Walls: perimeter of 5x4 is 14, times 3 layers.
        assertThat(ShapeTool.cells(Shape.WALLS, O, b, 3)).hasSize(14 * 3);
        assertThat(ShapeTool.bounds(ShapeTool.cells(Shape.BOX, O, b, 3))).isEqualTo(new Box(0, 64, 0, 4, 66, 3));
    }

    @Test
    void roundShapes() {
        BlockPos b = new BlockPos(4, 64, 0);
        List<BlockPos> disc = ShapeTool.cells(Shape.CIRCLE, O, b, 1);
        List<BlockPos> ring = ShapeTool.cells(Shape.RING, O, b, 1);
        assertThat(disc).contains(O, new BlockPos(4, 64, 0), new BlockPos(0, 64, -4));
        assertThat(ring).doesNotContain(O).contains(new BlockPos(4, 64, 0)).hasSizeLessThan(disc.size());
        assertThat(new HashSet<>(disc)).containsAll(ring);
        assertThat(ShapeTool.cells(Shape.CYLINDER, O, b, 5)).hasSize(ring.size() * 5);

        List<BlockPos> sphere = ShapeTool.cells(Shape.SPHERE, O, b, 1);
        Box sb = ShapeTool.bounds(sphere);
        assertThat(sb.minY()).isEqualTo(64); // rests on the start
        assertThat(sb.sizeX()).isEqualTo(9);
        assertThat(sphere).doesNotContain(new BlockPos(0, 68, 0)); // hollow centre
        assertThat(ShapeTool.bounds(ShapeTool.cells(Shape.DOME, O, b, 1)).minY()).isEqualTo(64);
    }

    @Test
    void pyramidSteps() {
        List<BlockPos> p = ShapeTool.cells(Shape.PYRAMID, O, new BlockPos(2, 64, 1), 1);
        Box b = ShapeTool.bounds(p);
        assertThat(b).isEqualTo(new Box(-2, 64, -2, 2, 66, 2));
        assertThat(p).contains(new BlockPos(0, 66, 0)).doesNotContain(new BlockPos(0, 64, 0));
    }

    @Test
    void hugeShapesAreRefused() {
        assertThat(ShapeTool.cells(Shape.BOX, O, new BlockPos(1000, 64, 1000), 10)).isEmpty();
        assertThat(ShapeTool.estimate(Shape.BOX, O, new BlockPos(1000, 64, 1000), 10)).isGreaterThan(ShapeTool.MAX_BLOCKS);
    }
}
