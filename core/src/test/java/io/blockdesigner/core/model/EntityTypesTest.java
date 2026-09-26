package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.transform.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTypesTest {

    @Test
    void knownAndModdedKinds() {
        assertThat(EntityTypes.kind("pig").name()).isEqualTo("Pig");
        assertThat(EntityTypes.kind("minecraft:pig").width()).isEqualTo(0.9f);
        EntityTypes.Kind modded = EntityTypes.kind("alexsmobs:grizzly_bear");
        assertThat(modded.name()).isEqualTo("Grizzly Bear");
        assertThat(modded.living()).isTrue();
    }

    @Test
    void newMobsStayPutAndFaceTheirYaw() {
        StructureEntity cow = EntityTypes.create("cow", 1.5, 64, 2.5, 180);
        assertThat(cow.id()).isEqualTo("minecraft:cow");
        assertThat(cow.yaw()).isEqualTo(180);
        assertThat(cow.nbt().getBoolean("PersistenceRequired")).isTrue();
        assertThat(EntityTypes.create("armor_stand", 0, 0, 0, 0).nbt().contains("PersistenceRequired")).isFalse();
        assertThat(EntityTypes.create("villager", 0, 0, 0, 0).nbt().getCompound("VillagerData").getString("profession")).isEqualTo("minecraft:none");
    }

    @Test
    void boxesStandOnTheFeetAndHalveForBabies() {
        StructureEntity cow = EntityTypes.create("cow", 0.5, 10, 0.5, 0);
        assertThat(EntityTypes.box(cow)).containsExactly(new double[]{0.05, 10, 0.05, 0.95, 11.4, 0.95}, org.assertj.core.data.Offset.offset(1e-6));
        cow.nbt().putInt("Age", -24000);
        double[] b = EntityTypes.box(cow);
        assertThat(b[4] - b[1]).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void paintingsUseTheHorizontalFacing() {
        CompoundTag nbt = new CompoundTag().putString("id", "minecraft:painting").putByte("facing", 2).putString("variant", "minecraft:pool");
        assertThat(EntityTypes.facing(nbt)).isEqualTo(2);
        StructureEntity p = new StructureEntity(0.5, 0.5, 0.96875, nbt);
        double[] b = EntityTypes.box(p);
        // A 2×1 picture on a north-facing wall: wide along x, thin along z.
        assertThat(b[3] - b[0]).isEqualTo(2);
        assertThat(b[4] - b[1]).isEqualTo(1);
        assertThat(b[5] - b[2]).isEqualTo(1 / 16.0);
    }

    @Test
    void turningALayerTurnsAPaintingsWall() {
        CompoundTag nbt = new CompoundTag().putString("id", "minecraft:painting").putByte("facing", 0).putString("variant", "minecraft:kebab");
        nbt.putInt("TileX", 2).putInt("TileY", 0).putInt("TileZ", 0);
        StructureEntity p = new StructureEntity(2.5, 0.5, 0.03125, nbt);
        StructureEntity turned = EntityTypes.transform(p, Transform.rotation(1), 0, 0, 0);
        // Facing south, a quarter turn clockwise, faces west (2D value 1); its block turns with it.
        assertThat(turned.nbt().getByte("facing")).isEqualTo((byte) 1);
        assertThat(List.of(turned.nbt().getInt("TileX"), turned.nbt().getInt("TileZ"))).containsExactly(0, 2);
        CompoundTag frame = new CompoundTag().putString("id", "minecraft:item_frame").putByte("Facing", 5);
        StructureEntity f = EntityTypes.transform(new StructureEntity(0.03, 0.5, 0.5, frame), Transform.rotation(1), 0, 0, 0);
        // East turned clockwise is south (3D value 3).
        assertThat(f.nbt().getByte("Facing")).isEqualTo((byte) 3);
    }

    @Test
    void evenPaintingsShiftHalfABlock() {
        CompoundTag nbt = new CompoundTag().putString("id", "minecraft:painting").putString("variant", "minecraft:match");
        double[] c = EntityTypes.hangingPosition(0, 0, 0, 3, nbt);
        // Facing south: pressed to the wall at z = 0.03125, moved east and up half a block for its 2×2 size.
        assertThat(c).containsExactly(1.0, 1.0, 0.03125);
    }

    @Test
    void translatingMovesTheHangingBlock() {
        CompoundTag nbt = new CompoundTag().putString("id", "minecraft:item_frame").putInt("TileX", 1).putInt("TileY", 2).putInt("TileZ", 3);
        StructureEntity e = new StructureEntity(1.5, 2.5, 3.03, nbt).translated(10, -2, 0);
        assertThat(e.nbt().getInt("TileX")).isEqualTo(11);
        assertThat(e.nbt().getInt("TileY")).isZero();
    }
}
