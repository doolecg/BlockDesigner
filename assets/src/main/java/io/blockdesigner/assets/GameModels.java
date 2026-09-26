package io.blockdesigner.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.assets.EntityModels.Cube;
import io.blockdesigner.assets.EntityModels.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Minecraft's own entity models, as {@code mob_models.json} holds them: every model layer the game registers
 * ({@code minecraft:fox#main}, {@code minecraft:slime#outer}…) with its cubes, texture offsets, rest pose and texture
 * size, read out of the game's model code by {@code packaging/mob_models.py}.
 */
final class GameModels {
    private GameModels() {
    }

    /** A model layer: its parts and the texture size its UVs are in. */
    record Layer(List<Part> parts, int texWidth, int texHeight) {
    }

    private static volatile JsonNode layers;

    private static JsonNode layers() {
        JsonNode l = layers;
        if (l != null) return l;
        synchronized (GameModels.class) {
            if (layers != null) return layers;
            try (InputStream in = GameModels.class.getResourceAsStream("mob_models.json")) {
                layers = in == null ? new ObjectMapper().createObjectNode() : new ObjectMapper().readTree(in).path("layers");
            } catch (IOException e) {
                layers = new ObjectMapper().createObjectNode();
            }
            return layers;
        }
    }

    static boolean has(String layer) {
        return layers().has(layer);
    }

    /**
     * A layer's parts, leaving out the parts named in {@code hidden} (with everything under them): the game hides
     * some parts until they're needed (a llama's chests, an illager's separate arms).
     */
    static Optional<Layer> layer(String layer, Set<String> hidden) {
        return layer(layer, hidden, Map.of());
    }

    /**
     * As {@link #layer(String, Set)}, with {@code tweaks} added to named parts' poses ({x, y, z, xRot, yRot, zRot}):
     * the rest pose a renderer or setupAnim gives a part that its layer doesn't (a floating crystal's tilt).
     */
    static Optional<Layer> layer(String layer, Set<String> hidden, Map<String, float[]> tweaks) {
        JsonNode l = layers().get(layer);
        if (l == null) return Optional.empty();
        JsonNode tex = l.path("texture");
        JsonNode root = l.path("root");
        // The root's own pose carries a whole-model scale (MeshTransformer.scaling: villagers, ghasts, giants).
        List<Part> parts = root.has("pose") || root.has("cubes") ? List.of(part(root, hidden, null, tweaks)) : children(root, hidden, tweaks);
        return Optional.of(new Layer(parts, tex.path(0).asInt(64), tex.path(1).asInt(64)));
    }

    /**
     * The poses from the layer's root down to the part named {@code part} (its own last), each {x, y, z, xRot, yRot,
     * zRot, xScale, yScale, zScale}: where a renderer draws something on that part (a snow golem's pumpkin).
     */
    static Optional<List<float[]>> chain(String layer, String part) {
        JsonNode l = layers().get(layer);
        if (l == null) return Optional.empty();
        List<float[]> out = new ArrayList<>();
        JsonNode root = l.path("root");
        out.add(pose(root));
        return find(root, part, out) ? Optional.of(out) : Optional.empty();
    }

    private static boolean find(JsonNode node, String part, List<float[]> path) {
        for (Map.Entry<String, JsonNode> e : node.path("parts").properties()) {
            path.add(pose(e.getValue()));
            if (e.getKey().equals(part) || find(e.getValue(), part, path)) return true;
            path.removeLast();
        }
        return false;
    }

    private static float[] pose(JsonNode n) {
        JsonNode p = n.path("pose");
        return new float[]{f(p, 0), f(p, 1), f(p, 2), f(p, 3), f(p, 4), f(p, 5),
                p.size() > 6 ? f(p, 6) : 1, p.size() > 7 ? f(p, 7) : 1, p.size() > 8 ? f(p, 8) : 1};
    }

    private static List<Part> children(JsonNode node, Set<String> hidden, Map<String, float[]> tweaks) {
        List<Part> out = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : node.path("parts").properties()) {
            if (hidden.contains(e.getKey())) continue;
            out.add(part(e.getValue(), hidden, tweaks.get(e.getKey()), tweaks));
        }
        return out;
    }

    private static Part part(JsonNode n, Set<String> hidden, float[] tweak, Map<String, float[]> tweaks) {
        JsonNode p = n.path("pose");
        float[] t = tweak != null ? tweak : new float[6];
        List<Cube> cubes = new ArrayList<>();
        for (JsonNode c : n.path("cubes")) {
            JsonNode g = c.path("grow");
            // Every model the game has grows its cubes evenly; a per-axis grow keeps its largest.
            float grow = g.isArray() ? Math.max(f(g, 0), Math.max(f(g, 1), f(g, 2))) : (float) g.asDouble(0);
            cubes.add(new Cube(c.path("uv").path(0).asInt(), c.path("uv").path(1).asInt(), f(c.path("at"), 0), f(c.path("at"), 1), f(c.path("at"), 2),
                    f(c.path("size"), 0), f(c.path("size"), 1), f(c.path("size"), 2), grow, c.path("mirror").asBoolean(false)));
        }
        return new Part(cubes, f(p, 0) + t[0], f(p, 1) + t[1], f(p, 2) + t[2], f(p, 3) + t[3], f(p, 4) + t[4], f(p, 5) + t[5],
                children(n, hidden, tweaks),
                p.size() > 6 ? f(p, 6) : 1, p.size() > 7 ? f(p, 7) : 1, p.size() > 8 ? f(p, 8) : 1);
    }

    private static float f(JsonNode array, int i) {
        return (float) array.path(i).asDouble(0);
    }
}
