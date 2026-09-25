package io.blockdesigner.core.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An ordered, mutable map of named tags. Getters are lenient and return defaults when a key is missing or mistyped. */
public final class CompoundTag implements Tag {
    private final LinkedHashMap<String, Tag> entries = new LinkedHashMap<>();

    @Override
    public byte id() {
        return COMPOUND;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    public boolean contains(String key, byte type) {
        Tag t = entries.get(key);
        return t != null && t.id() == type;
    }

    public Tag get(String key) {
        return entries.get(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public Map<String, Tag> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public CompoundTag put(String key, Tag value) {
        entries.put(key, value);
        return this;
    }

    public Tag remove(String key) {
        return entries.remove(key);
    }

    public CompoundTag putByte(String key, int v) {
        return put(key, new ByteTag((byte) v));
    }

    public CompoundTag putBoolean(String key, boolean v) {
        return putByte(key, v ? 1 : 0);
    }

    public CompoundTag putShort(String key, int v) {
        return put(key, new ShortTag((short) v));
    }

    public CompoundTag putInt(String key, int v) {
        return put(key, new IntTag(v));
    }

    public CompoundTag putLong(String key, long v) {
        return put(key, new LongTag(v));
    }

    public CompoundTag putFloat(String key, float v) {
        return put(key, new FloatTag(v));
    }

    public CompoundTag putDouble(String key, double v) {
        return put(key, new DoubleTag(v));
    }

    public CompoundTag putString(String key, String v) {
        return put(key, new StringTag(v));
    }

    public CompoundTag putIntArray(String key, int... v) {
        return put(key, new IntArrayTag(v));
    }

    public CompoundTag putLongArray(String key, long... v) {
        return put(key, new LongArrayTag(v));
    }

    public CompoundTag putByteArray(String key, byte... v) {
        return put(key, new ByteArrayTag(v));
    }

    private Number number(String key) {
        Tag t = entries.get(key);
        return t == null ? null : t.asNumber();
    }

    public byte getByte(String key) {
        Number n = number(key);
        return n == null ? 0 : n.byteValue();
    }

    public boolean getBoolean(String key) {
        return getByte(key) != 0;
    }

    public short getShort(String key) {
        Number n = number(key);
        return n == null ? 0 : n.shortValue();
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int fallback) {
        Number n = number(key);
        return n == null ? fallback : n.intValue();
    }

    public long getLong(String key) {
        Number n = number(key);
        return n == null ? 0L : n.longValue();
    }

    public float getFloat(String key) {
        Number n = number(key);
        return n == null ? 0f : n.floatValue();
    }

    public double getDouble(String key) {
        Number n = number(key);
        return n == null ? 0d : n.doubleValue();
    }

    public String getString(String key) {
        return entries.get(key) instanceof StringTag s ? s.value() : "";
    }

    public Optional<String> findString(String key) {
        return entries.get(key) instanceof StringTag s ? Optional.of(s.value()) : Optional.empty();
    }

    public int[] getIntArray(String key) {
        return entries.get(key) instanceof IntArrayTag a ? a.value() : new int[0];
    }

    public long[] getLongArray(String key) {
        return entries.get(key) instanceof LongArrayTag a ? a.value() : new long[0];
    }

    public byte[] getByteArray(String key) {
        return entries.get(key) instanceof ByteArrayTag a ? a.value() : new byte[0];
    }

    /** Returns the compound at {@code key}, or a new detached empty compound if absent. */
    public CompoundTag getCompound(String key) {
        return entries.get(key) instanceof CompoundTag c ? c : new CompoundTag();
    }

    public Optional<CompoundTag> findCompound(String key) {
        return entries.get(key) instanceof CompoundTag c ? Optional.of(c) : Optional.empty();
    }

    /** Returns the list at {@code key}, or a new detached empty list if absent. */
    public ListTag getList(String key) {
        return entries.get(key) instanceof ListTag l ? l : new ListTag();
    }

    @Override
    public CompoundTag copy() {
        CompoundTag c = new CompoundTag();
        entries.forEach((k, v) -> c.entries.put(k, v.copy()));
        return c;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompoundTag other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return toSnbt();
    }
}
