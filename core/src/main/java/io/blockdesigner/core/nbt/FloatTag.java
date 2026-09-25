package io.blockdesigner.core.nbt;

public record FloatTag(float value) implements Tag {
    @Override
    public byte id() {
        return FLOAT;
    }

    @Override
    public FloatTag copy() {
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
