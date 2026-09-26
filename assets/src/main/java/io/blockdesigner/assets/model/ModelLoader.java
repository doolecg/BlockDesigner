package io.blockdesigner.assets.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.assets.AssetStack;
import io.blockdesigner.assets.Dir;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Loads block model JSON and resolves parent chains and texture variables into flat {@link ResolvedModel}s. */
public final class ModelLoader {
    private static final int MAX_PARENT_DEPTH = 32;

    public record Face(float[] uv, String texture, Dir cull, int rotation, int tintIndex) {
    }

    public record ElementRotation(float[] origin, char axis, float angle, boolean rescale) {
    }

    public record Element(float[] from, float[] to, ElementRotation rotation, boolean shade, Map<Dir, Face> faces) {
    }

    private record Unbaked(String parent, Map<String, String> textures, List<Element> elements, Boolean ao, float[] gui) {
    }

    /**
     * A model with parents merged and every face texture resolved to a texture id (or null if unresolvable).
     * {@code gui} is the inventory transform from {@code display.gui} (rotation xyz in degrees, translation xyz in
     * pixels, scale xyz), or null when no model in the chain has one.
     */
    public record ResolvedModel(String id, List<Element> elements, Map<String, String> textures, boolean ambientOcclusion, float[] gui) {
        public ResolvedModel(String id, List<Element> elements, Map<String, String> textures, boolean ambientOcclusion) {
            this(id, elements, textures, ambientOcclusion, null);
        }

        public String resolve(String ref) {
            String cur = ref;
            for (int i = 0; i < 16 && cur != null && cur.startsWith("#"); i++) cur = textures.get(cur.substring(1));
            return cur == null || cur.startsWith("#") ? null : normalize(cur);
        }

        public Optional<String> particle() {
            return Optional.ofNullable(resolve("#particle"));
        }
    }

    private final AssetStack assets;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Optional<Unbaked>> unbaked = new ConcurrentHashMap<>();
    private final Map<String, Optional<ResolvedModel>> resolved = new ConcurrentHashMap<>();

    public ModelLoader(AssetStack assets) {
        this.assets = assets;
    }

    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    static String modelPath(String id) {
        String n = normalize(id);
        int c = n.indexOf(':');
        return "assets/" + n.substring(0, c) + "/models/" + n.substring(c + 1) + ".json";
    }

    public Optional<ResolvedModel> resolve(String modelId) {
        String id = normalize(modelId);
        Optional<ResolvedModel> cached = resolved.get(id);
        if (cached != null) return cached;
        Optional<ResolvedModel> r = doResolve(id);
        resolved.put(id, r);
        return r;
    }

    private Optional<ResolvedModel> doResolve(String id) {
        Map<String, String> textures = new HashMap<>();
        List<Element> elements = null;
        Boolean ao = null;
        float[] gui = null;
        String cur = id;
        boolean any = false;
        for (int depth = 0; cur != null && depth < MAX_PARENT_DEPTH; depth++) {
            Optional<Unbaked> u = load(cur);
            if (u.isEmpty()) break;
            any = true;
            Unbaked m = u.get();
            m.textures.forEach(textures::putIfAbsent);
            if (elements == null && m.elements != null) elements = m.elements;
            if (ao == null && m.ao != null) ao = m.ao;
            if (gui == null && m.gui != null) gui = m.gui;
            cur = m.parent == null || m.parent.startsWith("builtin/") ? null : normalize(m.parent);
        }
        if (!any) return Optional.empty();
        return Optional.of(new ResolvedModel(id, elements == null ? List.of() : elements, Map.copyOf(textures), ao == null || ao, gui));
    }

    private Optional<Unbaked> load(String id) {
        return unbaked.computeIfAbsent(id, k -> {
            Optional<byte[]> bytes = assets.read(modelPath(k));
            if (bytes.isEmpty()) return Optional.empty();
            try {
                return Optional.of(parse(json.readTree(bytes.get())));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    private static Unbaked parse(JsonNode n) {
        String parent = n.hasNonNull("parent") ? n.get("parent").asText() : null;
        Map<String, String> textures = new HashMap<>();
        n.path("textures").properties().forEach(e -> {
            JsonNode v = e.getValue();
            // Newer formats allow {"sprite": "...", "force_translucent": true}
            textures.put(e.getKey(), v.isObject() ? v.path("sprite").asText() : v.asText());
        });
        List<Element> elements = null;
        if (n.has("elements") && n.get("elements").isArray()) {
            elements = new ArrayList<>();
            for (JsonNode e : n.get("elements")) elements.add(parseElement(e));
        }
        Boolean ao = n.has("ambientocclusion") ? n.get("ambientocclusion").asBoolean(true) : null;
        JsonNode g = n.path("display").path("gui");
        float[] gui = null;
        if (g.isObject()) {
            float[] r = vec3(g.path("rotation")), t = vec3(g.path("translation")), sc = vec3(g.path("scale"), 1);
            gui = new float[]{r[0], r[1], r[2], t[0], t[1], t[2], sc[0], sc[1], sc[2]};
        }
        return new Unbaked(parent, textures, elements, ao, gui);
    }

    private static Element parseElement(JsonNode e) {
        float[] from = vec3(e.path("from"));
        float[] to = vec3(e.path("to"));
        ElementRotation rot = null;
        JsonNode r = e.path("rotation");
        if (r.isObject()) {
            if (r.has("axis")) {
                rot = new ElementRotation(vec3(r.path("origin"), 8), r.path("axis").asText("y").charAt(0), (float) r.path("angle").asDouble(0),
                        r.path("rescale").asBoolean(false));
            } else {
                // Per-axis form {"x":..,"y":..,"z":..}; only a single non-zero axis is supported.
                for (char axis : new char[]{'x', 'y', 'z'}) {
                    double a = r.path(String.valueOf(axis)).asDouble(0);
                    if (a != 0) {
                        rot = new ElementRotation(vec3(r.path("origin"), 8), axis, (float) a, r.path("rescale").asBoolean(false));
                        break;
                    }
                }
            }
        }
        Map<Dir, Face> faces = new EnumMap<>(Dir.class);
        e.path("faces").properties().forEach(f -> {
            Dir d = Dir.byName(f.getKey());
            if (d == null) return;
            JsonNode fn = f.getValue();
            float[] uv = fn.has("uv") && fn.get("uv").size() == 4 ? new float[]{
                    (float) fn.get("uv").get(0).asDouble(), (float) fn.get("uv").get(1).asDouble(),
                    (float) fn.get("uv").get(2).asDouble(), (float) fn.get("uv").get(3).asDouble()} : null;
            faces.put(d, new Face(uv, fn.path("texture").asText("#missing"), Dir.byName(fn.path("cullface").asText(null)),
                    Math.floorMod(fn.path("rotation").asInt(0), 360), fn.path("tintindex").asInt(-1)));
        });
        return new Element(from, to, rot, e.path("shade").asBoolean(true), faces);
    }

    private static float[] vec3(JsonNode a) {
        return vec3(a, 0);
    }

    private static float[] vec3(JsonNode a, float fallback) {
        if (!a.isArray() || a.size() < 3) return new float[]{fallback, fallback, fallback};
        return new float[]{(float) a.get(0).asDouble(), (float) a.get(1).asDouble(), (float) a.get(2).asDouble()};
    }
}
