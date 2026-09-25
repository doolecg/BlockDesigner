package io.blockdesigner.core.nbt;

public record LongTag(long value) implements Tag {
    @Override
    public byte id() {
        return LONG;
    }

    @Override
    public LongTag copy() {
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
