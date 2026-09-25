package io.blockdesigner.core.nbt;

import java.util.Arrays;
import java.util.Objects;

public final class IntArrayTag implements Tag {
    private final int[] value;

    public IntArrayTag(int[] value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** The backing array (not copied). */
    public int[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    @Override
    public byte id() {
        return INT_ARRAY;
    }

    @Override
    public IntArrayTag copy() {
        return new IntArrayTag(value.clone());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntArrayTag other && Arrays.equals(value, other.value);
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
