package io.blockdesigner.core.nbt;

import java.util.Objects;

public record StringTag(String value) implements Tag {
    public StringTag {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public byte id() {
        return STRING;
    }

    @Override
    public StringTag copy() {
        return this;
    }

    @Override
    public String toString() {
        return toSnbt();
    }
}
