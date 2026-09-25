package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.StringTag;
import io.blockdesigner.core.nbt.Tag;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An interned block state: a namespaced block id plus sorted properties, e.g.
 * {@code minecraft:oak_stairs[facing=north,half=bottom]}. Instances are canonical, so {@code ==} comparison is valid.
 */
public final class BlockState {
    private static final Map<String, BlockState> INTERN = new ConcurrentHashMap<>();

    public static final BlockState AIR = of("minecraft:air");
    public static final BlockState STRUCTURE_VOID = of("minecraft:structure_void");

    private final String name;
    private final SortedMap<String, String> properties;
    private final String canonical;

    private BlockState(String name, SortedMap<String, String> properties, String canonical) {
        this.name = name;
        this.properties = properties;
        this.canonical = canonical;
    }

    public static BlockState of(String name) {
        return of(name, Map.of());
    }

    public static BlockState of(String name, Map<String, String> properties) {
        String id = normalizeId(name);
        TreeMap<String, String> sorted = new TreeMap<>(properties);
        String canonical = canonical(id, sorted);
        BlockState existing = INTERN.get(canonical);
        if (existing != null) return existing;
        return INTERN.computeIfAbsent(canonical, k -> new BlockState(id, Collections.unmodifiableSortedMap(sorted), k));
    }

    /** Parses {@code namespace:id[key=value,...]}; the namespace defaults to {@code minecraft}. */
    public static BlockState parse(String text) {
        String s = text.strip();
        BlockState cached = INTERN.get(s);
        if (cached != null) return cached;
        int bracket = s.indexOf('[');
        if (bracket < 0) return of(s);
        if (!s.endsWith("]")) throw new IllegalArgumentException("Malformed block state: " + text);
        Map<String, String> props = new TreeMap<>();
        String body = s.substring(bracket + 1, s.length() - 1).strip();
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                int eq = pair.indexOf('=');
                if (eq < 0) throw new IllegalArgumentException("Malformed property '" + pair + "' in " + text);
                props.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
            }
        }
        return of(s.substring(0, bracket), props);
    }

    public static String normalizeId(String id) {
        String s = id.strip().toLowerCase(java.util.Locale.ROOT);
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    private static String canonical(String id, SortedMap<String, String> props) {
        if (props.isEmpty()) return id;
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (var e : props.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return name.substring(0, name.indexOf(':'));
    }

    /** The id without namespace, e.g. {@code oak_stairs}. */
    public String path() {
        return name.substring(name.indexOf(':') + 1);
    }

    public SortedMap<String, String> properties() {
        return properties;
    }

    public String get(String property) {
        return properties.get(property);
    }

    public boolean has(String property) {
        return properties.containsKey(property);
    }

    public BlockState with(String property, String value) {
        if (Objects.equals(properties.get(property), value)) return this;
        TreeMap<String, String> p = new TreeMap<>(properties);
        p.put(property, value);
        return of(name, p);
    }

    public BlockState withProperties(Map<String, String> props) {
        return of(name, props);
    }

    public BlockState withName(String newName) {
        return of(newName, properties);
    }

    public boolean isAir() {
        return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air");
    }

    /** Palette entry in vanilla structure / Litematica form: {@code {Name:"...", Properties:{...}}}. */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag().putString("Name", name);
        if (!properties.isEmpty()) {
            CompoundTag p = new CompoundTag();
            properties.forEach(p::putString);
            tag.put("Properties", p);
        }
        return tag;
    }

    public static BlockState fromNbt(CompoundTag tag) {
        String name = tag.getString("Name");
        if (name.isEmpty()) return AIR;
        CompoundTag p = tag.getCompound("Properties");
        if (p.isEmpty()) return of(name);
        TreeMap<String, String> props = new TreeMap<>();
        for (var e : p.entries().entrySet()) {
            Tag v = e.getValue();
            props.put(e.getKey(), v instanceof StringTag s ? s.value() : v.toSnbt());
        }
        return of(name, props);
    }

    @Override
    public String toString() {
        return canonical;
    }
}
