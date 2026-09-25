package io.blockdesigner.core.model;

import io.blockdesigner.core.util.LongObjectMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** The fast paths behind rendering and export: primitive section map, cached bounds, bulk copies, content ids. */
class StructurePerfPathsTest {
    static final BlockState STONE = BlockState.of("stone");
    static final BlockState DIRT = BlockState.of("dirt");

    @Test
    void longMapMatchesHashMapUnderRandomChurn() {
        LongObjectMap<String> fast = new LongObjectMap<>();
        Map<Long, String> ref = new HashMap<>();
        Random r = new Random(7);
        for (int i = 0; i < 200_000; i++) {
            long k = r.nextInt(5000) - 2500L;
            switch (r.nextInt(3)) {
                case 0 -> assertThat(fast.put(k, "v" + i)).isEqualTo(ref.put(k, "v" + i));
                case 1 -> assertThat(fast.remove(k)).isEqualTo(ref.remove(k));
                default -> assertThat(fast.get(k)).isEqualTo(ref.get(k));
            }
        }
        assertThat(fast.size()).isEqualTo(ref.size());
        for (long k : fast.keys()) assertThat(fast.get(k)).isEqualTo(ref.get(k));
    }

    @Test
    void boundsFollowAddsAndRemovalsIncrementally() {
        Structure s = new Structure();
        assertThat(s.bounds()).isEmpty();
        s.set(0, 0, 0, STONE);
        s.set(10, 3, -4, STONE);
        assertThat(s.bounds()).contains(new Box(0, 0, -4, 10, 3, 0));
        s.set(5, 1, -2, STONE);
        s.set(5, 1, -2, BlockState.AIR); // interior removal
        assertThat(s.bounds()).contains(new Box(0, 0, -4, 10, 3, 0));
        s.set(10, 3, -4, BlockState.AIR); // surface removal shrinks
        assertThat(s.bounds()).contains(new Box(0, 0, 0, 0, 0, 0));
        s.set(0, 0, 0, BlockState.AIR);
        assertThat(s.bounds()).isEmpty();
        s.set(-40, 70, 9, DIRT);
        assertThat(s.bounds()).contains(new Box(-40, 70, 9, -40, 70, 9));
    }

    @Test
    void randomEditsKeepBoundsExact() {
        Structure s = new Structure();
        Random r = new Random(3);
        for (int i = 0; i < 20_000; i++) {
            int x = r.nextInt(80) - 40, y = r.nextInt(40) - 10, z = r.nextInt(80) - 40;
            s.set(x, y, z, r.nextInt(3) == 0 ? BlockState.AIR : STONE);
            if (i % 997 == 0) assertThat(s.bounds()).isEqualTo(slowBounds(s));
        }
        assertThat(s.bounds()).isEqualTo(slowBounds(s));
    }

    private static java.util.Optional<Box> slowBounds(Structure s) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        s.forEachBlock((x, y, z, st) -> {
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x);
            b[4] = Math.max(b[4], y);
            b[5] = Math.max(b[5], z);
        });
        return s.blockCount() == 0 ? java.util.Optional.empty() : java.util.Optional.of(new Box(b[0], b[1], b[2], b[3], b[4], b[5]));
    }

    @Test
    void copyRegionMatchesGetAcrossSectionBorders() {
        Structure s = new Structure();
        Random r = new Random(11);
        for (int i = 0; i < 3000; i++) s.set(r.nextInt(50) - 25, r.nextInt(50) - 25, r.nextInt(50) - 25, r.nextBoolean() ? STONE : DIRT);
        int x0 = -17, y0 = -1, z0 = 14, sx = 18, sy = 18, sz = 18;
        BlockState[] out = new BlockState[sx * sy * sz];
        s.copyRegion(x0, y0, z0, sx, sy, sz, out);
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++)
                    assertThat(out[(y * sz + z) * sx + x]).isSameAs(s.get(x0 + x, y0 + y, z0 + z));
    }

    @Test
    void contentIdIsSharedByCopiesUntilOneIsEdited() {
        Structure a = new Structure();
        a.set(1, 1, 1, STONE);
        Structure b = a.copy();
        assertThat(b.contentId()).isEqualTo(a.contentId());
        b.set(2, 2, 2, DIRT);
        assertThat(b.contentId()).isNotEqualTo(a.contentId());
        long before = a.contentId();
        a.set(3, 3, 3, DIRT);
        assertThat(a.contentId()).isNotEqualTo(b.contentId());
        // An unshared structure keeps its id while edited (the renderer keys meshes by it).
        long now = a.contentId();
        a.set(4, 4, 4, DIRT);
        assertThat(a.contentId()).isEqualTo(now).isNotEqualTo(before);
    }

    @Test
    void stateCountsAndUsedStates() {
        Structure s = new Structure();
        for (int i = 0; i < 5; i++) s.set(i, 0, 0, STONE);
        s.set(0, 1, 0, DIRT);
        assertThat(s.stateCounts()).containsEntry(STONE, 5L).containsEntry(DIRT, 1L).hasSize(2);
        assertThat(s.usedStates()).containsExactlyInAnyOrder(STONE, DIRT);
    }

    @Test
    void flattenMatchesPerBlockTransform() {
        Structure s = new Structure();
        s.set(1, 0, 2, BlockState.parse("oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"));
        s.set(3, 4, -1, STONE);
        Layer l = new Layer("l", s);
        l.setOffset(new BlockPos(10, 5, -3));
        l.setTransform(new io.blockdesigner.core.transform.Transform(1, io.blockdesigner.core.transform.Transform.Mirror.X));
        Structure flat = Scene.flatten(java.util.List.of(l));
        var bt = io.blockdesigner.core.transform.BlockTransformer.defaults();
        s.forEachBlock((x, y, z, st) -> assertThat(flat.get(l.toWorld(x, y, z))).isSameAs(bt.apply(st, l.transform())));
        assertThat(flat.blockCount()).isEqualTo(2);
    }
}
