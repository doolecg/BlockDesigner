package io.blockdesigner.core.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A homogeneous, mutable list of tags. The element type is fixed by the first element added. */
public final class ListTag implements Tag, Iterable<Tag> {
    private final List<Tag> items = new ArrayList<>();
    private byte elementType;

    public ListTag() {
        this(END);
    }

    public ListTag(byte elementType) {
        this.elementType = elementType;
    }

    public static ListTag of(Tag... tags) {
        ListTag l = new ListTag();
        for (Tag t : tags) l.add(t);
        return l;
    }

    public static ListTag ofInts(int... values) {
        ListTag l = new ListTag(INT);
        for (int v : values) l.add(new IntTag(v));
        return l;
    }

    public static ListTag ofDoubles(double... values) {
        ListTag l = new ListTag(DOUBLE);
        for (double v : values) l.add(new DoubleTag(v));
        return l;
    }

    @Override
    public byte id() {
        return LIST;
    }

    public byte elementType() {
        return items.isEmpty() && elementType == END ? END : elementType;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Tag get(int index) {
        return items.get(index);
    }

    public ListTag add(Tag tag) {
        if (items.isEmpty() && (elementType == END || elementType == tag.id())) {
            elementType = tag.id();
        } else if (tag.id() != elementType) {
            throw new IllegalArgumentException("List of type " + elementType + " cannot hold tag type " + tag.id());
        }
        items.add(tag);
        return this;
    }

    public CompoundTag getCompound(int index) {
        return items.get(index) instanceof CompoundTag c ? c : new CompoundTag();
    }

    public int getInt(int index) {
        Number n = items.get(index).asNumber();
        return n == null ? 0 : n.intValue();
    }

    public double getDouble(int index) {
        Number n = items.get(index).asNumber();
        return n == null ? 0 : n.doubleValue();
    }

    public String getString(int index) {
        return items.get(index) instanceof StringTag s ? s.value() : "";
    }

    /** Typed view of the compound elements; non-compound elements are skipped. */
    public List<CompoundTag> compounds() {
        List<CompoundTag> out = new ArrayList<>(items.size());
        for (Tag t : items) if (t instanceof CompoundTag c) out.add(c);
        return out;
    }

    public List<Tag> items() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public Iterator<Tag> iterator() {
        return items().iterator();
    }

    @Override
    public ListTag copy() {
        ListTag l = new ListTag(elementType);
        for (Tag t : items) l.items.add(t.copy());
        return l;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ListTag other && items.equals(other.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return toSnbt();
    }
}
