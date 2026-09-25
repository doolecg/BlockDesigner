package io.blockdesigner.assets;

import io.blockdesigner.core.model.BlockState;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Every block id known to the loaded assets, with its properties and a default state. */
public final class BlockRegistry {

    public record BlockInfo(String id, Map<String, List<String>> properties, BlockState defaultState) {
        public String namespace() {
            return id.substring(0, id.indexOf(':'));
        }

        public String path() {
            return id.substring(id.indexOf(':') + 1);
        }

        /** "oak_stairs" → "Oak Stairs". */
        public String displayName() {
            StringBuilder sb = new StringBuilder();
            for (String w : path().split("[_/]")) {
                if (w.isEmpty()) continue;
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            return sb.toString();
        }
    }

    private final Map<String, BlockInfo> blocks;

    BlockRegistry(Map<String, BlockInfo> blocks) {
        this.blocks = Collections.unmodifiableMap(new TreeMap<>(blocks));
    }

    public Collection<BlockInfo> all() {
        return blocks.values();
    }

    public int size() {
        return blocks.size();
    }

    public Optional<BlockInfo> get(String id) {
        return Optional.ofNullable(blocks.get(BlockState.normalizeId(id)));
    }

    public boolean contains(String id) {
        return blocks.containsKey(BlockState.normalizeId(id));
    }

    /** Case-insensitive substring search on id and display name, exact/prefix matches first. */
    public List<BlockInfo> search(String query, int limit) {
        String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (q.isEmpty()) return blocks.values().stream().limit(limit).toList();
        return blocks.values().stream()
                .filter(b -> b.id().contains(q))
                .sorted((a, b) -> Integer.compare(score(a, q), score(b, q)))
                .limit(limit)
                .toList();
    }

    private static int score(BlockInfo b, String q) {
        String p = b.path();
        int base = b.namespace().equals("minecraft") ? 0 : 1;
        if (p.equals(q)) return base;
        if (p.startsWith(q)) return 10 + base + p.length();
        return 100 + base + p.indexOf(q) + p.length();
    }

    /**
     * Returns {@code state} with missing properties filled from the block's defaults and unknown values replaced,
     * so hand-typed or AI-produced states are always renderable.
     */
    public BlockState complete(BlockState state) {
        BlockInfo info = blocks.get(state.name());
        if (info == null) return state;
        Map<String, String> props = new TreeMap<>(info.defaultState().properties());
        state.properties().forEach((k, v) -> {
            List<String> allowed = info.properties().get(k);
            if (allowed != null && allowed.contains(v)) props.put(k, v);
        });
        return state.withProperties(props);
    }
}
