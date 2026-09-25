package io.blockdesigner.app.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.assets.AssetSource;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.worldgen.DatapackExporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Biome tags read from the loaded game jar and mods ({@code data/<ns>/tags/worldgen/biome/<path>.json}), merged the
 * way Minecraft does: lower-priority files first, each adding its values unless it says {@code "replace": true}.
 */
final class BiomeTagReader {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BiomeTagReader() {
    }

    /** A tag reader over the assets' jars, or null when no Minecraft version is loaded. */
    static DatapackExporter.BiomeTags of(BlockAssets assets) {
        if (assets == null) return null;
        List<AssetSource> sources = assets.assetStack().sources();
        Map<String, Optional<List<String>>> cache = new HashMap<>();
        return id -> cache.computeIfAbsent(id, k -> read(sources, k));
    }

    private static Optional<List<String>> read(List<AssetSource> highestFirst, String id) {
        int c = id.indexOf(':');
        String path = "data/" + id.substring(0, c) + "/tags/worldgen/biome/" + id.substring(c + 1) + ".json";
        List<String> values = null;
        for (int i = highestFirst.size() - 1; i >= 0; i--) {
            Optional<byte[]> bytes;
            try {
                bytes = highestFirst.get(i).read(path);
            } catch (Exception e) {
                continue;
            }
            if (bytes.isEmpty()) continue;
            try {
                JsonNode n = JSON.readTree(bytes.get());
                if (values == null || n.path("replace").asBoolean(false)) values = new ArrayList<>();
                for (JsonNode v : n.path("values")) {
                    String entry = v.isTextual() ? v.asText() : v.path("id").asText("");
                    if (!entry.isBlank()) values.add(entry);
                }
            } catch (Exception e) {
                // a broken tag file in some pack: skip it
            }
        }
        return Optional.ofNullable(values);
    }
}
