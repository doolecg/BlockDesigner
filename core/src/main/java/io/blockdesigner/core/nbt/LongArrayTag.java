package io.blockdesigner.core.nbt;

import java.util.Arrays;
import java.util.Objects;

public final class LongArrayTag implements Tag {
    private final long[] value;

    public LongArrayTag(long[] value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** The backing array (not copied). */
    public long[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    @Override
    public byte id() {
        return LONG_ARRAY;
    }

    @Override
    public LongArrayTag copy() {
        return new LongArrayTag(value.clone());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LongArrayTag other && Arrays.equals(value, other.value);
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
