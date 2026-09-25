package io.blockdesigner.core.nbt;

public record DoubleTag(double value) implements Tag {
    @Override
    public byte id() {
        return DOUBLE;
    }

    @Override
    public DoubleTag copy() {
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
