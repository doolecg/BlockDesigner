package io.blockdesigner.core.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.NbtIO;
import io.blockdesigner.core.transform.Transform;
import io.blockdesigner.core.version.McVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The {@code .bdproj} project container: a zip with {@code project.json} (layer list, offsets, transforms, flags,
 * target version), one Litematica-encoded {@code layers/<id>.litematic} per layer (preserving local coordinates),
 * and free-form extra entries (chat history, snapshots) owned by other modules.
 */
public final class ProjectFile {
    public static final String EXTENSION = "bdproj";
    private static final int FORMAT = 1;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Everything stored in a project. {@code extras} maps zip entry names to raw bytes. */
    public record Contents(String name, McVersion targetVersion, List<Layer> layers, String activeLayerId, Map<String, byte[]> extras) {
    }

    private ProjectFile() {
    }

    public static void save(Contents c, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            ObjectNode root = JSON.createObjectNode();
            root.put("format", FORMAT);
            root.put("name", c.name());
            root.put("targetVersion", c.targetVersion().id());
            root.put("dataVersion", c.targetVersion().dataVersion());
            if (c.activeLayerId() != null) root.put("activeLayer", c.activeLayerId());
            ArrayNode layers = root.putArray("layers");
            WriteOptions opts = WriteOptions.defaults(c.targetVersion());
            for (Layer l : c.layers()) {
                ObjectNode n = layers.addObject();
                n.put("id", l.id());
                n.put("name", l.name());
                n.putArray("offset").add(l.offset().x()).add(l.offset().y()).add(l.offset().z());
                n.put("rotation", l.transform().rotation());
                n.put("mirror", l.transform().mirror().name());
                n.put("visible", l.visible());
                n.put("locked", l.locked());
                n.put("ghost", l.ghost());
                n.put("color", String.format("#%06X", l.color() & 0xFFFFFF));
                if (l.source() != null) n.put("source", l.source());
                String entry = "layers/" + l.id() + ".litematic";
                n.put("file", entry);

                Structure s = l.structure();
                BlockPos min = s.bounds().map(Box::min).orElse(BlockPos.ORIGIN);
                SchematicFile sf = new SchematicFile(l.name(), "", "", c.targetVersion().dataVersion(),
                        List.of(new SchematicFile.Region(l.name(), s, min)));
                var enc = Schematics.LITEMATICA.write(sf, opts);
                // Litematica stores region positions relative to the enclosing box; keep the absolute min separately.
                n.putArray("origin").add(min.x()).add(min.y()).add(min.z());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIO.write(enc.root(), enc.rootName(), bytes, true);
                put(zip, entry, bytes.toByteArray());
            }
            put(zip, "project.json", JSON.writeValueAsBytes(root));
            for (var e : c.extras().entrySet()) put(zip, e.getKey(), e.getValue());
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    public static Contents load(Path file) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(file); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (e.getName().contains("..")) continue;
                entries.put(e.getName(), zip.readAllBytes());
            }
        }
        byte[] pj = entries.remove("project.json");
        if (pj == null) throw new IOException("Not a BlockDesigner project (missing project.json)");
        JsonNode root = JSON.readTree(pj);
        if (root.path("format").asInt() > FORMAT) throw new IOException("Project was saved by a newer BlockDesigner");
        McVersion version = McVersion.byId(root.path("targetVersion").asText())
                .orElseGet(() -> McVersion.forDataVersion(root.path("dataVersion").asInt()));

        List<Layer> layers = new ArrayList<>();
        Map<String, Boolean> used = new HashMap<>();
        for (JsonNode n : root.path("layers")) {
            String entry = n.path("file").asText();
            byte[] data = entries.remove(entry);
            Structure s = new Structure();
            if (data != null) {
                var nbt = NbtIO.read(new ByteArrayInputStream(data)).tag();
                Structure normalized = Schematics.LITEMATICA.read(nbt).merged();
                normalized.normalizeToOrigin();
                JsonNode o = n.path("origin");
                s.paste(normalized, o.path(0).asInt(), o.path(1).asInt(), o.path(2).asInt());
            }
            String id = n.path("id").asText();
            if (used.put(id, true) != null) id = java.util.UUID.randomUUID().toString();
            Layer l = new Layer(id, n.path("name").asText("Layer"), s);
            JsonNode off = n.path("offset");
            l.setOffset(new BlockPos(off.path(0).asInt(), off.path(1).asInt(), off.path(2).asInt()));
            l.setTransform(new Transform(n.path("rotation").asInt(0), parseMirror(n.path("mirror").asText("NONE"))));
            l.setVisible(n.path("visible").asBoolean(true));
            l.setLocked(n.path("locked").asBoolean(false));
            l.setGhost(n.path("ghost").asBoolean(false));
            l.setSource(n.path("source").asText(null));
            String color = n.path("color").asText("#7C9CFF");
            try {
                l.setColor(Integer.parseInt(color.replace("#", ""), 16));
            } catch (NumberFormatException ignored) {
                // keep default colour
            }
            layers.add(l);
        }
        return new Contents(root.path("name").asText("Untitled"), version, layers, root.path("activeLayer").asText(null), entries);
    }

    private static Transform.Mirror parseMirror(String s) {
        try {
            return Transform.Mirror.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Transform.Mirror.NONE;
        }
    }
}
