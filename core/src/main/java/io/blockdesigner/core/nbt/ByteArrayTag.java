package io.blockdesigner.core.nbt;

import java.util.Arrays;
import java.util.Objects;

public final class ByteArrayTag implements Tag {
    private final byte[] value;

    public ByteArrayTag(byte[] value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** The backing array (not copied). */
    public byte[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    @Override
    public byte id() {
        return BYTE_ARRAY;
    }

    @Override
    public ByteArrayTag copy() {
        return new ByteArrayTag(value.clone());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteArrayTag other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return toSnbt();
    }
}
