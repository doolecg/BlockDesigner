package io.blockdesigner.core.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.model.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Rotates and mirrors block states so that directional blocks (stairs, doors, rails, fences, signs...) keep facing the
 * right way after a structure transform. Rules are data-driven, see {@code transform_rules.json}.
 */
public final class BlockTransformer {
    private static final List<String> HORIZONTAL = List.of("north", "east", "south", "west");
    private static volatile BlockTransformer defaultInstance;

    private final Set<String> directionProps;
    private final Set<String> axisProps;
    private final Set<String> rotation16Props;
    private final List<String> sideKeys;
    private final Map<String, List<Pattern>> tokenProps;
    private final Map<String, Map<String, String>> mirrorSwaps;
    private final Set<String> rotationToggles;
    private final Map<CacheKey, BlockState> cache = new ConcurrentHashMap<>();

    private record CacheKey(BlockState state, Transform transform) {
    }

    private BlockTransformer(JsonNode rules) {
        directionProps = strings(rules.path("direction_properties"));
        axisProps = strings(rules.path("axis_properties"));
        rotation16Props = strings(rules.path("rotation16_properties"));
        sideKeys = new ArrayList<>(strings(rules.path("side_keys")));
        tokenProps = new HashMap<>();
        rules.path("direction_token_properties").properties().forEach(e -> {
            List<Pattern> patterns = new ArrayList<>();
            e.getValue().path("blocks").forEach(b -> patterns.add(glob(b.asText())));
            tokenProps.put(e.getKey(), patterns);
        });
        mirrorSwaps = new HashMap<>();
        rules.path("mirror_value_swaps").properties().forEach(e -> {
            Map<String, String> m = new HashMap<>();
            e.getValue().properties().forEach(v -> m.put(v.getKey(), v.getValue().asText()));
            mirrorSwaps.put(e.getKey(), m);
        });
        rotationToggles = strings(rules.path("rotation_boolean_toggles"));
    }

    public static BlockTransformer load(InputStream json) {
        try {
            return new BlockTransformer(new ObjectMapper().readTree(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BlockTransformer defaults() {
        BlockTransformer t = defaultInstance;
        if (t == null) {
            synchronized (BlockTransformer.class) {
                if (defaultInstance == null) {
                    try (InputStream in = BlockTransformer.class.getResourceAsStream("/io/blockdesigner/core/transform_rules.json")) {
                        defaultInstance = load(in);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                t = defaultInstance;
            }
        }
        return t;
    }

    private static Set<String> strings(JsonNode arr) {
        Set<String> out = new java.util.LinkedHashSet<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static Pattern glob(String g) {
        return Pattern.compile(("\\Q" + g + "\\E").replace("*", "\\E.*\\Q"));
    }

    public BlockState apply(BlockState state, Transform t) {
        if (t.isIdentity() || state.properties().isEmpty()) return state;
        return cache.computeIfAbsent(new CacheKey(state, t), k -> compute(state, t));
    }

    private BlockState compute(BlockState state, Transform t) {
        Map<String, String> in = state.properties();
        Map<String, String> out = new TreeMap<>(in);

        // Side-keyed properties (fence/wall/pane/vine connections): move values between keys.
        if (!sideKeys.isEmpty()) {
            Set<String> present = new HashSet<>();
            for (String k : sideKeys) if (in.containsKey(k)) present.add(k);
            if (!present.isEmpty()) {
                for (String k : present) out.remove(k);
                for (String k : present) out.put(dir(k, t), in.get(k));
            }
        }

        for (var e : in.entrySet()) {
            String key = e.getKey(), value = e.getValue();
            if (sideKeys.contains(key)) continue;
            if (directionProps.contains(key) && HORIZONTAL.contains(value)) {
                out.put(key, dir(value, t));
            } else if (axisProps.contains(key) && (value.equals("x") || value.equals("z")) && t.rotation() % 2 == 1) {
                out.put(key, value.equals("x") ? "z" : "x");
            } else if (rotation16Props.contains(key) && isInt(value)) {
                out.put(key, Integer.toString(rotate16(Integer.parseInt(value), t)));
            } else if (rotationToggles.contains(key) && t.rotation() % 2 == 1 && (value.equals("true") || value.equals("false"))) {
                out.put(key, value.equals("true") ? "false" : "true");
            } else if (tokenProps.containsKey(key) && matches(tokenProps.get(key), state.name())) {
                out.put(key, rotateTokens(value, t));
            }
        }

        // Mirroring flips handedness (stair corners, door hinges, double chests).
        if (t.mirror() != Transform.Mirror.NONE) {
            for (var e : mirrorSwaps.entrySet()) {
                String v = out.get(e.getKey());
                if (v != null && e.getValue().containsKey(v)) out.put(e.getKey(), e.getValue().get(v));
            }
        }
        return state.withProperties(out);
    }

    private static boolean matches(List<Pattern> patterns, String name) {
        for (Pattern p : patterns) if (p.matcher(name).matches()) return true;
        return false;
    }

    private static boolean isInt(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    /** Transforms a horizontal direction name; non-horizontal names pass through. */
    public static String dir(String d, Transform t) {
        int i = HORIZONTAL.indexOf(d);
        if (i < 0) return d;
        if (t.mirror() == Transform.Mirror.X && (i == 1 || i == 3)) i = 4 - i;
        else if (t.mirror() == Transform.Mirror.Z && (i == 0 || i == 2)) i = 2 - i;
        return HORIZONTAL.get((i + t.rotation()) & 3);
    }

    /** 16-step rotation (0 = south, 4 = west, 8 = north, 12 = east). */
    static int rotate16(int r, Transform t) {
        if (t.mirror() == Transform.Mirror.X) r = (16 - r) & 15;
        else if (t.mirror() == Transform.Mirror.Z) r = (8 - r) & 15;
        return (r + 4 * t.rotation()) & 15;
    }

    /** Rotates each direction token in values like {@code ascending_east}, {@code south_west} or {@code north_up}. */
    private static String rotateTokens(String value, Transform t) {
        String[] parts = value.split("_");
        boolean any = false;
        for (int i = 0; i < parts.length; i++) {
            String r = dir(parts[i], t);
            if (!r.equals(parts[i])) any = true;
            parts[i] = r;
        }
        if (!any) return value;
        // Rail-style pairs of horizontal directions use a canonical order: north/south before east/west.
        if (parts.length == 2 && HORIZONTAL.contains(parts[0]) && HORIZONTAL.contains(parts[1])) {
            boolean ns0 = parts[0].equals("north") || parts[0].equals("south");
            boolean ns1 = parts[1].equals("north") || parts[1].equals("south");
            if (ns0 && ns1) return "north_south";
            if (!ns0 && !ns1) return "east_west";
            return ns0 ? parts[0] + "_" + parts[1] : parts[1] + "_" + parts[0];
        }
        return String.join("_", parts);
    }
}
