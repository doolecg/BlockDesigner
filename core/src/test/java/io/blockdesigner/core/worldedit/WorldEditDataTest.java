package io.blockdesigner.core.worldedit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.EntityTypes;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;
import io.blockdesigner.core.place.BlockPlacement.Dir;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Block entity data and entities carried through WorldEdit commands, and /fixshapes. */
class WorldEditDataTest {
    final Map<BlockPos, BlockState> blocks = new HashMap<>();
    final Map<BlockPos, CompoundTag> nbt = new HashMap<>();
    final List<StructureEntity> mobs = new ArrayList<>();
    final WorldEdit.World world = new WorldEdit.World() {
        public BlockState get(BlockPos p) {
            return blocks.getOrDefault(p, BlockState.AIR);
        }

        public void set(BlockPos p, BlockState s) {
            if (s.isAir()) blocks.remove(p);
            else blocks.put(p, s);
            // Like a structure: a different block loses the old one's data.
            nbt.remove(p);
        }

        public CompoundTag blockEntity(BlockPos p) {
            return nbt.get(p);
        }

        public void set(BlockPos p, BlockState s, CompoundTag be) {
            set(p, s);
            if (be != null) nbt.put(p, be);
        }

        public List<StructureEntity> entities(Box b) {
            return mobs.stream().filter(e -> b.contains((int) Math.floor(e.x()), (int) Math.floor(e.y()), (int) Math.floor(e.z()))).toList();
        }

        public void addEntity(StructureEntity e) {
            mobs.add(e);
        }

        public int removeEntities(Box b) {
            List<StructureEntity> gone = entities(b);
            mobs.removeAll(gone);
            return gone.size();
        }
    };
    final WorldEdit we = new WorldEdit(new Random(1));
    final WorldEdit.Context ctx = new WorldEdit.Context(world, Dir.NORTH, null, null, s -> BlockState.parse(s.contains(":") ? s : "minecraft:" + s));

    static final BlockState BANNER = BlockState.of("red_banner").with("rotation", "0");
    static final CompoundTag PATTERNS = new CompoundTag().put("patterns", ListTag.of(
            new CompoundTag().putString("pattern", "minecraft:stripe_bottom").putString("color", "white")));

    WorldEdit.Result run(String cmd) {
        return we.run(cmd, ctx);
    }

    void box(int x0, int y0, int z0, int x1, int y1, int z1) {
        we.setPos1(new BlockPos(x0, y0, z0));
        we.setPos2(new BlockPos(x1, y1, z1));
    }

    @Test
    void copyAndPasteKeepBannerPatterns() {
        blocks.put(new BlockPos(0, 0, 0), BANNER);
        nbt.put(new BlockPos(0, 0, 0), PATTERNS.copy());
        box(0, 0, 0, 0, 0, 0);
        run("/copy");
        we.setPos1(new BlockPos(5, 0, 0));
        run("/paste");
        assertThat(blocks.get(new BlockPos(5, 0, 0))).isEqualTo(BANNER);
        assertThat(nbt.get(new BlockPos(5, 0, 0))).isEqualTo(PATTERNS);
    }

    @Test
    void rotatedClipboardKeepsDataWithItsBlock() {
        blocks.put(new BlockPos(1, 0, 0), BANNER);
        nbt.put(new BlockPos(1, 0, 0), PATTERNS.copy());
        box(0, 0, 0, 1, 0, 0);
        run("/copy");
        run("/rotate 90");
        we.setPos1(new BlockPos(10, 0, 10));
        run("/paste");
        // (1, 0, 0) relative to pos1 turns a quarter clockwise to (0, 0, 1).
        assertThat(nbt).containsKey(new BlockPos(10, 0, 11));
    }

    @Test
    void moveAndStackCarryData() {
        blocks.put(new BlockPos(0, 0, 0), BANNER);
        nbt.put(new BlockPos(0, 0, 0), PATTERNS.copy());
        box(0, 0, 0, 0, 0, 0);
        run("/stack 2 east");
        assertThat(nbt.get(new BlockPos(1, 0, 0))).isEqualTo(PATTERNS);
        assertThat(nbt.get(new BlockPos(2, 0, 0))).isEqualTo(PATTERNS);
        box(0, 0, 0, 0, 0, 0);
        run("/move 3 up");
        assertThat(nbt.get(new BlockPos(0, 3, 0))).isEqualTo(PATTERNS);
        assertThat(nbt).doesNotContainKey(new BlockPos(0, 0, 0));
    }

    @Test
    void entitiesOnlyWithTheFlag() {
        mobs.add(EntityTypes.create("pig", 0.5, 0, 0.5, 90));
        box(0, 0, 0, 1, 1, 1);
        run("/copy");
        we.setPos1(new BlockPos(10, 0, 0));
        run("/paste");
        assertThat(mobs).hasSize(1);

        box(0, 0, 0, 1, 1, 1);
        run("/copy -e");
        we.setPos1(new BlockPos(10, 0, 0));
        run("/paste");
        assertThat(mobs).hasSize(2);
        assertThat(mobs.get(1).x()).isEqualTo(10.5);
        assertThat(mobs.get(1).yaw()).isEqualTo(90);
    }

    @Test
    void cutWithEntitiesTakesThem() {
        mobs.add(EntityTypes.create("cow", 0.5, 0, 0.5, 0));
        box(0, 0, 0, 0, 0, 0);
        assertThat(run("/cut -e").message()).contains("1 entity");
        assertThat(mobs).isEmpty();
        we.setPos1(new BlockPos(4, 0, 4));
        run("/paste");
        assertThat(mobs).singleElement().satisfies(e -> assertThat(e.x()).isEqualTo(4.5));
    }

    @Test
    void rotatingTheClipboardTurnsItsMobs() {
        mobs.add(EntityTypes.create("pig", 2.5, 0, 0.5, 0));
        box(0, 0, 0, 3, 0, 0);
        run("/copy -e");
        run("/rotate 90");
        we.setPos1(new BlockPos(0, 0, 0));
        run("/paste");
        StructureEntity turned = mobs.getLast();
        assertThat(turned.x()).isEqualTo(0.5);
        assertThat(turned.z()).isEqualTo(2.5);
        // Facing south, turned a quarter clockwise, faces west.
        assertThat(turned.yaw()).isEqualTo(90);
    }

    @Test
    void fixShapesJoinsFences() {
        BlockState post = BlockState.of("oak_fence").withProperties(Map.of("north", "false", "east", "false", "south", "false", "west", "false",
                "waterlogged", "false"));
        blocks.put(new BlockPos(0, 0, 0), post);
        blocks.put(new BlockPos(1, 0, 0), post);
        box(0, 0, 0, 1, 0, 0);
        WorldEdit.Result r = run("/fixshapes");
        assertThat(r.changed()).isEqualTo(2);
        assertThat(blocks.get(new BlockPos(0, 0, 0)).get("east")).isEqualTo("true");
        assertThat(blocks.get(new BlockPos(1, 0, 0)).get("west")).isEqualTo("true");
        assertThat(run("/fixshapes").changed()).isZero();
    }
}
