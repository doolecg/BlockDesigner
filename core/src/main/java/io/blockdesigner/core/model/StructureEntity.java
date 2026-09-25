package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;

/**
 * An entity inside a structure. {@code x/y/z} are local coordinates; {@code nbt} holds the entity data
 * (including its {@code id}) without the {@code Pos}/{@code UUID} keys, which formats manage themselves.
 */
public record StructureEntity(double x, double y, double z, CompoundTag nbt) {

    public String id() {
        return nbt.getString("id");
    }

    public StructureEntity translated(double dx, double dy, double dz) {
        return new StructureEntity(x + dx, y + dy, z + dz, nbt.copy());
    }

    public StructureEntity copy() {
        return new StructureEntity(x, y, z, nbt.copy());
    }

    /** Builds from a full entity compound that contains {@code Pos}; position keys are stripped. */
    public static StructureEntity fromFullNbt(CompoundTag full, double dx, double dy, double dz) {
        CompoundTag nbt = full.copy();
        ListTag pos = nbt.getList("Pos");
        double x = pos.size() == 3 ? pos.getDouble(0) : 0, y = pos.size() == 3 ? pos.getDouble(1) : 0, z = pos.size() == 3 ? pos.getDouble(2) : 0;
        nbt.remove("Pos");
        nbt.remove("UUID");
        nbt.remove("UUIDMost");
        nbt.remove("UUIDLeast");
        return new StructureEntity(x + dx, y + dy, z + dz, nbt);
    }

    /** Full entity compound with {@code Pos} set to this entity's position shifted by the given delta. */
    public CompoundTag toFullNbt(double dx, double dy, double dz) {
        CompoundTag c = nbt.copy();
        c.put("Pos", ListTag.ofDoubles(x + dx, y + dy, z + dz));
        return c;
    }
}
