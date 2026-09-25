package io.blockdesigner.core.nbt;

public record ByteTag(byte value) implements Tag {
    @Override
    public byte id() {
        return BYTE;
    }

    @Override
    public ByteTag copy() {
        return this;
    }

    @Override
    public Number asNumber() {
        return value;
    }

    @Override
    public String toString() {
        return toSnbt();
    }
}
