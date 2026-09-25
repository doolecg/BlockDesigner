package io.blockdesigner.core.util;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * A small open-addressing {@code long → V} hash map (linear probing, backward-shift deletion). It avoids boxing the
 * key on every lookup, which matters in voxel inner loops where {@code HashMap<Long, V>} allocates a {@code Long} per
 * call. Null values are not allowed. Not thread-safe.
 */
public final class LongObjectMap<V> {
    private long[] keys;
    private Object[] values;
    private int size;
    private int mask;
    private int resizeAt;

    public LongObjectMap() {
        this(16);
    }

    public LongObjectMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(4, (int) (expected / 0.6f)) * 2 - 1);
        alloc(Math.max(8, cap));
    }

    private void alloc(int cap) {
        keys = new long[cap];
        values = new Object[cap];
        mask = cap - 1;
        resizeAt = (int) (cap * 0.6f);
    }

    private static int mix(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int i = mix(key) & mask;
        Object v;
        while ((v = values[i]) != null) {
            if (keys[i] == key) return (V) v;
            i = (i + 1) & mask;
        }
        return null;
    }

    public boolean containsKey(long key) {
        return get(key) != null;
    }

    /** Stores {@code value} and returns the previous value, or null. */
    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        if (value == null) throw new NullPointerException("null values are not supported");
        int i = mix(key) & mask;
        Object v;
        while ((v = values[i]) != null) {
            if (keys[i] == key) {
                values[i] = value;
                return (V) v;
            }
            i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = value;
        if (++size > resizeAt) rehash(values.length * 2);
        return null;
    }

    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int i = mix(key) & mask;
        Object v;
        while ((v = values[i]) != null) {
            if (keys[i] == key) {
                shiftBack(i);
                size--;
                return (V) v;
            }
            i = (i + 1) & mask;
        }
        return null;
    }

    /** Backward-shift deletion keeps probe chains intact without tombstones. */
    private void shiftBack(int gap) {
        int i = gap;
        while (true) {
            i = (i + 1) & mask;
            Object v = values[i];
            if (v == null) break;
            int home = mix(keys[i]) & mask;
            // Move the entry into the gap if the gap lies cyclically between its home slot and its current slot.
            if (((i - home) & mask) >= ((i - gap) & mask)) {
                keys[gap] = keys[i];
                values[gap] = v;
                gap = i;
            }
        }
        values[gap] = null;
    }

    private void rehash(int cap) {
        long[] ok = keys;
        Object[] ov = values;
        alloc(cap);
        for (int j = 0; j < ov.length; j++) {
            if (ov[j] == null) continue;
            int i = mix(ok[j]) & mask;
            while (values[i] != null) i = (i + 1) & mask;
            keys[i] = ok[j];
            values[i] = ov[j];
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        Arrays.fill(values, null);
        size = 0;
    }

    /** All keys, in no particular order. */
    public long[] keys() {
        long[] out = new long[size];
        int n = 0;
        for (int i = 0; i < values.length; i++) if (values[i] != null) out[n++] = keys[i];
        return out;
    }

    @FunctionalInterface
    public interface EntryVisitor<V> {
        void visit(long key, V value);
    }

    @SuppressWarnings("unchecked")
    public void forEach(EntryVisitor<? super V> visitor) {
        long[] k = keys;
        Object[] v = values;
        for (int i = 0; i < v.length; i++) if (v[i] != null) visitor.visit(k[i], (V) v[i]);
    }

    @SuppressWarnings("unchecked")
    public void forEachValue(Consumer<? super V> visitor) {
        for (Object o : values) if (o != null) visitor.accept((V) o);
    }
}
