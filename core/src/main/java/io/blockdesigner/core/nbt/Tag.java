package io.blockdesigner.core.nbt;

/**
 * A Named Binary Tag value. Primitive tags are immutable records; compound and list tags are mutable containers.
 */
public sealed interface Tag
        permits ByteTag, ShortTag, IntTag, LongTag, FloatTag, DoubleTag, ByteArrayTag, StringTag,
        ListTag, CompoundTag, IntArrayTag, LongArrayTag {

    byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    byte id();

    /** Deep copy; immutable tags return themselves. */
    Tag copy();

    /** Numeric tags expose their value as a {@link Number}; others return {@code null}. */
    default Number asNumber() {
        return null;
    }

    default String toSnbt() {
        return Snbt.write(this);
    }
}
