package io.blockdesigner.core.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

/**
 * A Minecraft version profile: the numbers that decide how files are written for that version.
 * Format rules (folder names, pack.mcmeta shape) are derived from the data-pack format so new versions work
 * without code changes.
 */
public record McVersion(String id, int dataVersion, int dataPackMajor, int dataPackMinor, int javaVersion) implements Comparable<McVersion> {
    /** First data-pack format using singular folder names ({@code structure/}, {@code worldgen/structure/} unchanged). */
    private static final int SINGULAR_FOLDERS_FORMAT = 45;
    /** First data-pack format where pack.mcmeta uses {@code min_format}/{@code max_format} instead of {@code pack_format}. */
    private static final int MIN_MAX_FORMAT = 82;

    private static volatile List<McVersion> builtIn;

    /** Folder under {@code data/<namespace>/} holding structure templates. */
    public String structureFolder() {
        return dataPackMajor >= SINGULAR_FOLDERS_FORMAT ? "structure" : "structures";
    }

    /** Folder under {@code data/<namespace>/tags/} for block tags ({@code block} vs legacy {@code blocks}). */
    public String blockTagFolder() {
        return dataPackMajor >= SINGULAR_FOLDERS_FORMAT ? "block" : "blocks";
    }

    public boolean usesMinMaxPackFormat() {
        return dataPackMajor >= MIN_MAX_FORMAT;
    }

    public static List<McVersion> builtIn() {
        List<McVersion> v = builtIn;
        if (v == null) {
            try (InputStream in = McVersion.class.getResourceAsStream("/io/blockdesigner/core/versions.json")) {
                JsonNode root = new ObjectMapper().readTree(in);
                List<McVersion> list = new ArrayList<>();
                for (JsonNode n : root.path("versions")) {
                    list.add(new McVersion(n.path("id").asText(), n.path("dataVersion").asInt(), n.path("dataPackMajor").asInt(),
                            n.path("dataPackMinor").asInt(), n.path("javaVersion").asInt()));
                }
                list.sort(Comparator.naturalOrder());
                builtIn = v = List.copyOf(list);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return v;
    }

    public static Optional<McVersion> byId(String id) {
        return builtIn().stream().filter(v -> v.id.equals(id)).findFirst();
    }

    /** Best known version for a DataVersion: exact match, else the newest version not newer than it. */
    public static McVersion forDataVersion(int dataVersion) {
        McVersion best = builtIn().getFirst();
        for (McVersion v : builtIn()) if (v.dataVersion <= dataVersion) best = v;
        return best;
    }

    public static McVersion latestKnown() {
        return builtIn().getLast();
    }

    /** Reads the profile from a client or server jar's {@code version.json}. */
    public static McVersion fromClientJar(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("version.json");
            if (entry == null) throw new IOException("No version.json in " + jar);
            try (InputStream in = zip.getInputStream(entry)) {
                return fromVersionJson(new ObjectMapper().readTree(in));
            }
        }
    }

    static McVersion fromVersionJson(JsonNode n) {
        JsonNode pv = n.path("pack_version");
        int major, minor;
        if (pv.has("data_major")) {
            major = pv.path("data_major").asInt();
            minor = pv.path("data_minor").asInt();
        } else if (pv.isObject()) {
            major = pv.path("data").asInt();
            minor = 0;
        } else {
            major = pv.asInt();
            minor = 0;
        }
        return new McVersion(n.path("id").asText(), n.path("world_version").asInt(), major, minor, n.path("java_version").asInt());
    }

    @Override
    public int compareTo(McVersion o) {
        return Integer.compare(dataVersion, o.dataVersion);
    }

    @Override
    public String toString() {
        return id;
    }
}
