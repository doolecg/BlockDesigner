package io.blockdesigner.core.nbt;

public record IntTag(int value) implements Tag {
    @Override
    public byte id() {
        return INT;
    }

    @Override
    public IntTag copy() {
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
