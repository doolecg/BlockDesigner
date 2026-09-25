package io.blockdesigner.assets.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A parsed {@code blockstates/<id>.json}: either a variant table or a list of multipart cases. */
public final class BlockStateDefinition {

    /** One model reference with its whole-model rotation. */
    public record ModelRef(String model, int x, int y, boolean uvlock, int weight) {
    }

    private record Variant(Map<String, String> conditions, List<ModelRef> models) {
    }

    private sealed interface Condition permits Always, Match, Or, And {
        boolean test(BlockState s);
    }

    private record Always() implements Condition {
        public boolean test(BlockState s) {
            return true;
        }
    }

    private record Match(Map<String, Set<String>> props) implements Condition {
        public boolean test(BlockState s) {
            for (var e : props.entrySet()) {
                String v = s.get(e.getKey());
                if (v == null || !e.getValue().contains(v)) return false;
            }
            return true;
        }
    }

    private record Or(List<Condition> any) implements Condition {
        public boolean test(BlockState s) {
            for (Condition c : any) if (c.test(s)) return true;
            return false;
        }
    }

    private record And(List<Condition> all) implements Condition {
        public boolean test(BlockState s) {
            for (Condition c : all) if (!c.test(s)) return false;
            return true;
        }
    }

    private record Part(Condition when, List<ModelRef> apply) {
    }

    private final List<Variant> variants = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    /** Property name → values in the order they first appear. */
    private final Map<String, Set<String>> properties = new LinkedHashMap<>();
    private final Map<String, String> firstVariant = new LinkedHashMap<>();

    public static BlockStateDefinition parse(JsonNode root) {
        BlockStateDefinition d = new BlockStateDefinition();
        if (root.has("variants")) {
            boolean first = true;
            for (var e : root.get("variants").properties()) {
                Map<String, String> cond = parseKey(e.getKey());
                cond.forEach((k, v) -> d.properties.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(v));
                if (first) {
                    d.firstVariant.putAll(cond);
                    first = false;
                }
                d.variants.add(new Variant(cond, refs(e.getValue())));
            }
        }
        if (root.has("multipart")) {
            for (JsonNode p : root.get("multipart")) {
                d.parts.add(new Part(p.has("when") ? d.condition(p.get("when")) : new Always(), refs(p.path("apply"))));
            }
        }
        return d;
    }

    private static Map<String, String> parseKey(String key) {
        Map<String, String> m = new LinkedHashMap<>();
        if (key.isBlank()) return m;
        for (String pair : key.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) m.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return m;
    }

    private Condition condition(JsonNode n) {
        if (n.has("OR")) {
            List<Condition> l = new ArrayList<>();
            n.get("OR").forEach(c -> l.add(condition(c)));
            return new Or(l);
        }
        if (n.has("AND")) {
            List<Condition> l = new ArrayList<>();
            n.get("AND").forEach(c -> l.add(condition(c)));
            return new And(l);
        }
        Map<String, Set<String>> props = new LinkedHashMap<>();
        n.properties().forEach(e -> {
            Set<String> vals = new LinkedHashSet<>(List.of(e.getValue().asText().split("\\|")));
            props.put(e.getKey(), vals);
            vals.forEach(v -> properties.computeIfAbsent(e.getKey(), x -> new LinkedHashSet<>()).add(v));
        });
        return new Match(props);
    }

    private static List<ModelRef> refs(JsonNode n) {
        List<ModelRef> out = new ArrayList<>();
        if (n.isArray()) n.forEach(m -> out.add(ref(m)));
        else if (n.isObject()) out.add(ref(n));
        return out;
    }

    private static ModelRef ref(JsonNode m) {
        return new ModelRef(m.path("model").asText(), m.path("x").asInt(0), m.path("y").asInt(0), m.path("uvlock").asBoolean(false), m.path("weight").asInt(1));
    }

    /** Model references to render for {@code state}. Weighted alternatives pick the first entry. */
    public List<ModelRef> select(BlockState state) {
        List<ModelRef> out = new ArrayList<>();
        for (Variant v : variants) {
            boolean ok = true;
            for (var c : v.conditions.entrySet()) {
                if (!c.getValue().equals(state.get(c.getKey()))) {
                    ok = false;
                    break;
                }
            }
            if (ok && !v.models.isEmpty()) {
                out.add(v.models.getFirst());
                break;
            }
        }
        for (Part p : parts) if (p.when.test(state) && !p.apply.isEmpty()) out.add(p.apply.getFirst());
        return out;
    }

    /** Every model id this definition can reference. */
    public Set<String> allModels() {
        Set<String> out = new LinkedHashSet<>();
        variants.forEach(v -> v.models.forEach(m -> out.add(m.model())));
        parts.forEach(p -> p.apply.forEach(m -> out.add(m.model())));
        return out;
    }

    /** Property names and their known values. */
    public Map<String, Set<String>> properties() {
        return properties;
    }

    /** A sensible default state: the first variant's values, falling back to each property's first/"false" value. */
    public Map<String, String> defaultProperties() {
        Map<String, String> m = new LinkedHashMap<>();
        properties.forEach((k, vals) -> {
            String v = firstVariant.get(k);
            if (v == null) v = vals.contains("false") ? "false" : vals.contains("none") ? "none" : vals.iterator().next();
            m.put(k, v);
        });
        return m;
    }
}
