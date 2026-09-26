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

    /** Minecraft yaw in degrees (0 faces south, 90 west), from {@code Rotation}; 0 when unset. */
    public float yaw() {
        ListTag rot = nbt.getList("Rotation");
        return rot.size() == 2 ? (float) rot.getDouble(0) : 0f;
    }

    /** A copy turned to {@code yaw} degrees (pitch kept). */
    public StructureEntity withYaw(float yaw) {
        CompoundTag c = nbt.copy();
        ListTag rot = nbt.getList("Rotation");
        float pitch = rot.size() == 2 ? (float) rot.getDouble(1) : 0f;
        c.put("Rotation", ListTag.of(new io.blockdesigner.core.nbt.FloatTag(yaw), new io.blockdesigner.core.nbt.FloatTag(pitch)));
        return new StructureEntity(x, y, z, c);
    }

    public StructureEntity at(double nx, double ny, double nz) {
        return new StructureEntity(nx, ny, nz, nbt.copy());
    }

    /** Moved by a delta; a hanging entity's block ({@code TileX/Y/Z}) moves with it. */
    public StructureEntity translated(double dx, double dy, double dz) {
        CompoundTag c = nbt.copy();
        if (c.contains("TileX")) {
            c.putInt("TileX", c.getInt("TileX") + (int) Math.round(dx)).putInt("TileY", c.getInt("TileY") + (int) Math.round(dy))
                    .putInt("TileZ", c.getInt("TileZ") + (int) Math.round(dz));
        }
        return new StructureEntity(x + dx, y + dy, z + dz, c);
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
