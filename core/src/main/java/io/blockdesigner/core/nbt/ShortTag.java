package io.blockdesigner.core.nbt;

public record ShortTag(short value) implements Tag {
    @Override
    public byte id() {
        return SHORT;
    }

    @Override
    public ShortTag copy() {
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
