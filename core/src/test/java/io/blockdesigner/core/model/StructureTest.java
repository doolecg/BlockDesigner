package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructureTest {
    static final BlockState STONE = BlockState.of("stone");

    @Test
    void blockStatesAreInternedAndCanonical() {
        BlockState a = BlockState.parse("minecraft:oak_stairs[half=bottom,facing=north]");
        BlockState b = BlockState.of("oak_stairs", java.util.Map.of("facing", "north", "half", "bottom"));
        assertThat(a).isSameAs(b);
        assertThat(a.toString()).isEqualTo("minecraft:oak_stairs[facing=north,half=bottom]");
        assertThat(a.with("facing", "east").get("facing")).isEqualTo("east");
        assertThat(BlockState.fromNbt(a.toNbt())).isSameAs(a);
        assertThat(BlockState.parse("create:shaft[axis=y]").namespace()).isEqualTo("create");
    }

    @Test
    void setGetAcrossSectionsIncludingNegative() {
        Structure s = new Structure();
        s.set(0, 0, 0, STONE);
        s.set(-1, -17, 33, STONE);
        s.set(15, 16, -16, BlockState.of("dirt"));
        assertThat(s.get(-1, -17, 33)).isSameAs(STONE);
        assertThat(s.get(15, 16, -16).path()).isEqualTo("dirt");
        assertThat(s.get(5, 5, 5)).isSameAs(BlockState.AIR);
        assertThat(s.blockCount()).isEqualTo(3);
        assertThat(s.bounds()).contains(new Box(-1, -17, -16, 15, 16, 33));
    }

    @Test
    void settingAirRemovesBlocksSectionsAndBlockEntities() {
        Structure s = new Structure();
        s.set(1, 2, 3, BlockState.of("chest"));
        s.setBlockEntity(new BlockPos(1, 2, 3), new CompoundTag().putString("id", "minecraft:chest"));
        s.set(1, 2, 3, BlockState.AIR);
        assertThat(s.blockCount()).isZero();
        assertThat(s.sectionKeys()).isEmpty();
        assertThat(s.blockEntities()).isEmpty();
        assertThat(s.bounds()).isEmpty();
    }

    @Test
    void forEachVisitsExactPositions() {
        Structure s = new Structure();
        s.set(3, 7, 11, STONE);
        s.set(-20, 40, 2, STONE);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        s.forEachBlock((x, y, z, st) -> seen.add(new BlockPos(x, y, z)));
        assertThat(seen).containsExactlyInAnyOrder(new BlockPos(3, 7, 11), new BlockPos(-20, 40, 2));
    }

    @Test
    void normalizeMovesMinCornerToOrigin() {
        Structure s = new Structure();
        s.set(-5, 10, 3, STONE);
        s.set(-2, 12, 8, STONE);
        s.setBlockEntity(new BlockPos(-2, 12, 8), new CompoundTag().putString("id", "x"));
        s.normalizeToOrigin();
        assertThat(s.bounds()).contains(new Box(0, 0, 0, 3, 2, 5));
        assertThat(s.blockEntity(new BlockPos(3, 2, 5))).isNotNull();
    }

    @Test
    void packUnpackPositions() {
        for (BlockPos p : new BlockPos[]{new BlockPos(0, 0, 0), new BlockPos(-1, -1, -1), new BlockPos(30_000_000, 2047, -30_000_000), new BlockPos(-5, -2048, 7)}) {
            assertThat(BlockPos.unpack(p.pack())).isEqualTo(p);
        }
    }
}
