package io.blockdesigner.app.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.blockdesigner.core.edit.Change;
import io.blockdesigner.core.edit.Transaction;
import io.blockdesigner.core.edit.UndoStack;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.ImageData;
import io.blockdesigner.plugin.ObjectHandle;
import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.SceneObject;
import io.blockdesigner.plugin.SceneObjectType;
import io.blockdesigner.plugin.SceneObjects;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The plugins' scene objects ({@link SceneObject}) in the open project: what every object has (name, pose, visibility,
 * lock), the selected one, undo, and saving them in the project file. No UI: the viewport draws and picks them from
 * {@link #draw}, the Layers panel lists {@link #list()}.
 *
 * <p>Objects whose plugin is off stay in the project "parked": their saved state and blobs are kept as they were, they
 * aren't listed or drawn, and they come back when a plugin registers their type again. Use from the JavaFX thread.
 */
public final class SceneObjectStore {
    /** Project file entries: the index, one {@code objects/<id>.bin} per object and {@code objects/blobs/<key>}. */
    static final String INDEX = "objects/objects.json", PREFIX = "objects/", BLOBS = "objects/blobs/";
    private static final int FORMAT = 1;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ViewInfo NO_VIEW = new ViewInfo(false, Optional.empty(), new Vec3(0, 16, 32), new Vec3(0, 0, -1), new Vec3(0, 0, 0));

    /** A registered type, who registered it (the plugin, for error reports) and its key {@code plugin/type}. */
    public record Registered(String key, Object owner, SceneObjectType type) {
    }

    /** What every object has, besides the plugin's own state. */
    private record Props(String name, Pose pose, boolean visible, boolean locked) {
    }

    private final UndoStack undo;
    /** Every object, bottom first (the newest on top), parked ones included. */
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Registered> types = new LinkedHashMap<>();
    private final Map<String, byte[]> blobs = new HashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private Supplier<ViewInfo> view = () -> NO_VIEW;
    private BiConsumer<Object, Throwable> errors = (owner, t) -> {
    };
    private Entry selected;

    /** @param undo where changes are recorded; null to apply them without undo */
    public SceneObjectStore(UndoStack undo) {
        this.undo = undo;
    }

    /** Called after anything about the objects changed: added, removed, moved, shown, selected, edited. */
    public void addListener(Runnable r) {
        listeners.add(r);
    }

    /** Where the 3D view looks (the viewport sets it). */
    public void setView(Supplier<ViewInfo> view) {
        this.view = view;
    }

    public ViewInfo view() {
        return view.get();
    }

    /** Where a plugin's failure (a draw, a load) is reported: the owner is who registered the type. */
    public void setErrors(BiConsumer<Object, Throwable> errors) {
        this.errors = errors;
    }

    /** Reports a failure of an object's plugin code. */
    public void report(Entry e, Throwable t) {
        Registered r = types.get(e.typeKey);
        errors.accept(r == null ? null : r.owner, t);
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }

    // ---- types ------------------------------------------------------------------------------------------------

    /** Adds a type for {@code pluginId}; parked objects of it come back now. */
    public Registered register(Object owner, String pluginId, SceneObjectType type) {
        if (!type.id().matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Object type ids use a-z, 0-9, _ . - only: " + type.id());
        String key = pluginId + "/" + type.id();
        if (types.containsKey(key)) throw new IllegalArgumentException("Object type '" + type.id() + "' registered twice");
        Registered r = new Registered(key, owner, type);
        types.put(key, r);
        boolean any = false;
        for (Entry e : entries) if (e.typeKey.equals(key)) any |= revive(e, r);
        if (any) fire();
        return r;
    }

    /** Removes every type {@code owner} registered; its objects are parked (kept in the project, not shown). */
    public void unregister(Object owner) {
        boolean any = false;
        for (var it = types.values().iterator(); it.hasNext(); ) {
            Registered r = it.next();
            if (r.owner != owner) continue;
            for (Entry e : entries) if (e.typeKey.equals(r.key) && e.object != null) any |= park(e);
            it.remove();
        }
        if (any) fire();
    }

    public Optional<Registered> type(String key) {
        return Optional.ofNullable(types.get(key));
    }

    /** Types that {@link SceneObjectType#open open} this file, by its extension. */
    public List<Registered> typesFor(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<Registered> out = new ArrayList<>();
        for (Registered r : types.values()) {
            for (String ext : r.type.extensions()) {
                if (n.endsWith("." + ext.toLowerCase(Locale.ROOT))) {
                    out.add(r);
                    break;
                }
            }
        }
        return out;
    }

    /** The types that open files. */
    public List<Registered> fileTypes() {
        return types.values().stream().filter(r -> !r.type.extensions().isEmpty()).toList();
    }

    /** Every extension an object type opens. */
    public List<String> extensions() {
        return types.values().stream().flatMap(r -> r.type.extensions().stream()).distinct().toList();
    }

    private boolean revive(Entry e, Registered r) {
        try {
            SceneObject o = r.type.create();
            o.load(e.parkedData == null ? new byte[0] : e.parkedData);
            e.object = o;
            e.parkedData = null;
            e.parkedBlobs = Set.of();
            return true;
        } catch (Throwable t) {
            errors.accept(r.owner, t);
            return false;
        }
    }

    private boolean park(Entry e) {
        e.parkedData = saveOf(e);
        e.parkedBlobs = blobsOf(e);
        e.object = null;
        if (selected == e) selected = null;
        return true;
    }

    // ---- the objects -------------------------------------------------------------------------------------------

    /** The objects shown now (their plugin is on), top first as in the Layers panel. */
    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i).object != null) out.add(entries.get(i));
        return out;
    }

    /** Every object including parked ones, bottom first. */
    List<Entry> all() {
        return List.copyOf(entries);
    }

    public Optional<Entry> find(String id) {
        for (Entry e : entries) if (e.id.equals(id)) return Optional.of(e);
        return Optional.empty();
    }

    /** Adds an object of a registered type on top, as one undo step, and selects it. */
    public Entry add(String typeKey, String name, Pose pose, SceneObject object) {
        if (!types.containsKey(typeKey)) throw new IllegalArgumentException("No object type " + typeKey + " is registered");
        Entry e = new Entry(UUID.randomUUID().toString(), typeKey);
        e.name = name == null || name.isBlank() ? types.get(typeKey).type.name() : name;
        e.pose = Objects.requireNonNull(pose, "pose");
        e.object = Objects.requireNonNull(object, "object");
        attach(e, entries.size());
        record("Add " + e.name, new Membership(e, entries.size() - 1, true));
        selected = e;
        fire();
        return e;
    }

    /** Deletes an object as one undo step. */
    public void remove(Entry e) {
        int i = entries.indexOf(e);
        if (i < 0) return;
        detach(e);
        record("Delete " + e.name, new Membership(e, i, false));
        fire();
    }

    private void attach(Entry e, int index) {
        entries.add(Math.clamp(index, 0, entries.size()), e);
        e.inScene = true;
    }

    private void detach(Entry e) {
        entries.remove(e);
        e.inScene = false;
        if (selected == e) selected = null;
    }

    public Optional<Entry> selected() {
        return Optional.ofNullable(selected);
    }

    /** Selects an object (null for none); only shown objects can be selected. */
    public void select(Entry e) {
        Entry want = e != null && e.exists() ? e : null;
        if (want == selected) return;
        selected = want;
        fire();
    }

    private void record(String label, Change c) {
        if (undo != null) undo.push(new Transaction(label).add(c));
    }

    private byte[] saveOf(Entry e) {
        if (e.object == null) return e.parkedData == null ? new byte[0] : e.parkedData;
        try {
            byte[] b = e.object.save();
            return b == null ? new byte[0] : b;
        } catch (Throwable t) {
            report(e, t);
            return e.parkedData == null ? new byte[0] : e.parkedData;
        }
    }

    private Set<String> blobsOf(Entry e) {
        if (e.object == null) return e.parkedBlobs;
        try {
            Set<String> b = e.object.blobs();
            return b == null ? Set.of() : Set.copyOf(b);
        } catch (Throwable t) {
            report(e, t);
            return Set.of();
        }
    }

    private void loadInto(Entry e, byte[] data) {
        if (data == null) return;
        if (e.object == null) {
            e.parkedData = data;
            return;
        }
        try {
            e.object.load(data);
        } catch (Throwable t) {
            report(e, t);
        }
    }

    // ---- blobs -------------------------------------------------------------------------------------------------

    /** Keeps {@code data} and returns its key (its SHA-256, so the same bytes share one copy). */
    public String storeBlob(byte[] data) {
        String key;
        try {
            key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        blobs.putIfAbsent(key, data.clone());
        return key;
    }

    public Optional<byte[]> blob(String key) {
        return Optional.ofNullable(blobs.get(key));
    }

    // ---- saving in the project ---------------------------------------------------------------------------------

    /** The objects as project file entries (nothing when there are none), parked ones included. */
    public Map<String, byte[]> save() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        if (entries.isEmpty()) return out;
        ObjectNode root = JSON.createObjectNode();
        root.put("format", FORMAT);
        ArrayNode list = root.putArray("objects");
        Set<String> used = new LinkedHashSet<>();
        for (Entry e : entries) {
            ObjectNode n = list.addObject();
            n.put("id", e.id);
            n.put("type", e.typeKey);
            n.put("name", e.name);
            n.put("visible", e.visible);
            n.put("locked", e.locked);
            putVec(n, "position", e.pose.position());
            putVec(n, "rotation", e.pose.rotation());
            putVec(n, "scale", e.pose.scale());
            String data = PREFIX + e.id + ".bin";
            n.put("data", data);
            out.put(data, saveOf(e));
            ArrayNode b = n.putArray("blobs");
            for (String k : blobsOf(e)) {
                b.add(k);
                used.add(k);
            }
        }
        for (String k : used) {
            byte[] bytes = blobs.get(k);
            if (bytes != null) out.put(BLOBS + k, bytes);
        }
        try {
            out.put(INDEX, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /**
     * Replaces the objects with those in a project's entries (none for a new project); objects of types that are
     * registered come back straight away, the rest wait parked for their plugin.
     */
    public void load(Map<String, byte[]> extras) {
        entries.forEach(e -> e.inScene = false);
        entries.clear();
        blobs.clear();
        selected = null;
        byte[] index = extras.get(INDEX);
        if (index != null) {
            try {
                JsonNode root = JSON.readTree(index);
                if (root.path("format").asInt(FORMAT) <= FORMAT) read(root, extras);
            } catch (IOException | RuntimeException bad) {
                errors.accept(null, bad);
            }
        }
        fire();
    }

    private void read(JsonNode root, Map<String, byte[]> extras) {
        extras.forEach((name, bytes) -> {
            if (name.startsWith(BLOBS)) blobs.put(name.substring(BLOBS.length()), bytes);
        });
        Set<String> ids = new java.util.HashSet<>();
        for (JsonNode n : root.path("objects")) {
            String id = n.path("id").asText("");
            if (id.isBlank() || !ids.add(id)) id = UUID.randomUUID().toString();
            Entry e = new Entry(id, n.path("type").asText(""));
            e.name = n.path("name").asText("Object");
            e.visible = n.path("visible").asBoolean(true);
            e.locked = n.path("locked").asBoolean(false);
            e.pose = new Pose(vec(n.path("position"), 0), vec(n.path("rotation"), 0), vec(n.path("scale"), 1));
            e.parkedData = extras.getOrDefault(n.path("data").asText(""), new byte[0]);
            Set<String> b = new LinkedHashSet<>();
            for (JsonNode k : n.path("blobs")) b.add(k.asText());
            e.parkedBlobs = Set.copyOf(b);
            attach(e, entries.size());
            Registered r = types.get(e.typeKey);
            if (r != null) revive(e, r);
        }
    }

    private static void putVec(ObjectNode n, String name, Vec3 v) {
        n.putArray(name).add(v.x()).add(v.y()).add(v.z());
    }

    private static Vec3 vec(JsonNode a, double fallback) {
        return new Vec3(a.path(0).asDouble(fallback), a.path(1).asDouble(fallback), a.path(2).asDouble(fallback));
    }

    // ---- drawing and picking -----------------------------------------------------------------------------------

    /** An image an object drew, in world space: corners x y z × 4, uv u v × 4. */
    public record DrawnImage(Entry entry, ImageData image, double[] corners, double[] uv, double opacity, Drawing.Depth depth) {
    }

    /** A line an object drew, in world space. */
    public record DrawnLine(Vec3 from, Vec3 to, int argb) {
    }

    /** What the shown, visible objects drew for one view. */
    public record Drawn(List<DrawnImage> images, List<DrawnLine> lines) {

        /**
         * The object under a ray (origin, unit direction): one drawn in front of the blocks, else the nearest image hit
         * before {@code before} (the first block hit); one drawn behind the blocks only where no block is hit.
         *
         * @param locked whether locked objects count
         */
        public Optional<Entry> pick(Vec3 o, Vec3 d, double before, boolean locked) {
            Entry best = null;
            double bestT = Double.MAX_VALUE;
            boolean bestFront = false;
            for (DrawnImage im : images) {
                if ((im.entry.locked && !locked) || im.opacity <= 0) continue;
                double t = rayQuad(o, d, im.corners);
                if (t < 0) continue;
                boolean front = im.depth == Drawing.Depth.IN_FRONT;
                if (!front && t > before) continue;
                if (im.depth == Drawing.Depth.BEHIND_BLOCKS && before < Double.MAX_VALUE) continue;
                if (best == null || (front && !bestFront) || (front == bestFront && t < bestT)) {
                    best = im.entry;
                    bestT = t;
                    bestFront = front;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    /**
     * Asks every shown, visible object to draw itself for {@code view} and puts the result in world space. A plugin
     * that throws is reported once per call and skipped.
     */
    public Drawn draw(ViewInfo view) {
        List<DrawnImage> images = new ArrayList<>();
        List<DrawnLine> lines = new ArrayList<>();
        for (Entry e : entries) {
            if (e.object == null || !e.visible) continue;
            Pose pose = e.pose;
            Drawing out = new Drawing() {
                @Override
                public void image(ImageData image, Vec3[] corners, double[] uv, double opacity, Depth depth) {
                    if (image == null || corners == null || corners.length != 4 || uv == null || uv.length != 8) {
                        throw new IllegalArgumentException("An image needs 4 corners and 8 uv values");
                    }
                    double[] w = new double[12];
                    for (int i = 0; i < 4; i++) {
                        Vec3 p = pose.toWorld(corners[i]);
                        w[i * 3] = p.x();
                        w[i * 3 + 1] = p.y();
                        w[i * 3 + 2] = p.z();
                    }
                    images.add(new DrawnImage(e, image, w, uv.clone(), Math.clamp(opacity, 0, 1), depth == null ? Depth.IN_SCENE : depth));
                }

                @Override
                public void line(Vec3 from, Vec3 to, int argb) {
                    lines.add(new DrawnLine(pose.toWorld(from), pose.toWorld(to), argb));
                }
            };
            try {
                e.object.draw(view, out);
            } catch (Throwable t) {
                report(e, t);
            }
        }
        return new Drawn(images, lines);
    }

    /** Distance along a unit ray to a (planar) quad of 4 corners, or -1 when it misses. */
    static double rayQuad(Vec3 o, Vec3 d, double[] c) {
        double t = rayTriangle(o, d, c, 0, 1, 2);
        double u = rayTriangle(o, d, c, 0, 2, 3);
        if (t < 0) return u;
        return u < 0 ? t : Math.min(t, u);
    }

    /** Möller–Trumbore: distance to triangle (corners a, b, c of {@code p}), or -1. */
    private static double rayTriangle(Vec3 o, Vec3 d, double[] p, int a, int b, int c) {
        double ax = p[a * 3], ay = p[a * 3 + 1], az = p[a * 3 + 2];
        double e1x = p[b * 3] - ax, e1y = p[b * 3 + 1] - ay, e1z = p[b * 3 + 2] - az;
        double e2x = p[c * 3] - ax, e2y = p[c * 3 + 1] - ay, e2z = p[c * 3 + 2] - az;
        double px = d.y() * e2z - d.z() * e2y, py = d.z() * e2x - d.x() * e2z, pz = d.x() * e2y - d.y() * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) return -1;
        double inv = 1 / det;
        double tx = o.x() - ax, ty = o.y() - ay, tz = o.z() - az;
        double u = (tx * px + ty * py + tz * pz) * inv;
        if (u < 0 || u > 1) return -1;
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double v = (d.x() * qx + d.y() * qy + d.z() * qz) * inv;
        if (v < 0 || u + v > 1) return -1;
        double t = (e2x * qx + e2y * qy + e2z * qz) * inv;
        return t >= 0 ? t : -1;
    }

    // ---- a plugin's view ---------------------------------------------------------------------------------------

    /** {@link SceneObjects} for one plugin: its own objects and types only. */
    public SceneObjects forPlugin(String pluginId) {
        String prefix = pluginId + "/";
        return new SceneObjects() {
            @Override
            public List<ObjectHandle> list() {
                return SceneObjectStore.this.list().stream().filter(e -> e.typeKey.startsWith(prefix)).map(e -> (ObjectHandle) e).toList();
            }

            @Override
            public ObjectHandle add(String type, String name, Pose pose, SceneObject object) {
                return SceneObjectStore.this.add(prefix + type, name, pose, object);
            }

            @Override
            public Optional<ObjectHandle> selected() {
                return SceneObjectStore.this.selected().filter(e -> e.typeKey.startsWith(prefix)).map(e -> (ObjectHandle) e);
            }

            @Override
            public ViewInfo view() {
                return SceneObjectStore.this.view();
            }

            @Override
            public String storeBlob(byte[] data) {
                return SceneObjectStore.this.storeBlob(data);
            }

            @Override
            public Optional<byte[]> blob(String key) {
                return SceneObjectStore.this.blob(key);
            }
        };
    }

    // ---- one object --------------------------------------------------------------------------------------------

    /** One object: the handle a plugin gets, and what the Layers panel and viewport show. */
    public final class Entry implements ObjectHandle {
        private final String id;
        private final String typeKey;
        private String name;
        private Pose pose = Pose.IDENTITY;
        private boolean visible = true, locked;
        /** The plugin's object; null while parked. */
        private SceneObject object;
        /** While parked: the object's last saved state and blobs. */
        private byte[] parkedData;
        private Set<String> parkedBlobs = Set.of();
        private boolean inScene;

        private Entry(String id, String typeKey) {
            this.id = id;
            this.typeKey = typeKey;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String type() {
            return typeKey.substring(typeKey.indexOf('/') + 1);
        }

        /** {@code plugin/type}. */
        public String typeKey() {
            return typeKey;
        }

        /** The tag for the Layers panel ("REFERENCE"). */
        public String badge() {
            Registered r = types.get(typeKey);
            return r == null ? "OBJECT" : r.type.badge();
        }

        /** The plugin's line for the Layers panel (empty when it has none or fails). */
        public String description() {
            if (object == null) return "";
            try {
                String s = object.description(this);
                return s == null ? "" : s;
            } catch (Throwable t) {
                report(this, t);
                return "";
            }
        }

        @Override
        public SceneObject object() {
            return object;
        }

        @Override
        public boolean exists() {
            return inScene && object != null;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void setName(String name) {
            if (name == null || name.isBlank() || name.equals(this.name)) return;
            change("Rename " + this.name, new Props(name, pose, visible, locked));
        }

        @Override
        public Pose pose() {
            return pose;
        }

        @Override
        public void setPose(Pose pose, String label) {
            if (Objects.requireNonNull(pose, "pose").equals(this.pose)) return;
            change(label == null ? "Move " + name : label, new Props(name, pose, visible, locked));
        }

        /** Sets the pose with no undo step, for a drag in progress; {@link #commitPose} records the whole drag. */
        public void setPoseLive(Pose pose) {
            this.pose = Objects.requireNonNull(pose, "pose");
            fire();
        }

        /** Records a live drag that started at {@code before} as one undo step. */
        public void commitPose(Pose before, String label) {
            if (before.equals(pose)) return;
            record(label, new PropsChange(this, new Props(name, before, visible, locked), props(), null, null));
            fire();
        }

        @Override
        public boolean visible() {
            return visible;
        }

        @Override
        public void setVisible(boolean visible) {
            if (visible == this.visible) return;
            change((visible ? "Show " : "Hide ") + name, new Props(name, pose, visible, locked));
        }

        @Override
        public boolean locked() {
            return locked;
        }

        @Override
        public void setLocked(boolean locked) {
            if (locked == this.locked) return;
            change((locked ? "Lock " : "Unlock ") + name, new Props(name, pose, visible, locked));
        }

        @Override
        public void edit(String label, Runnable change) {
            byte[] before = saveOf(this);
            change.run();
            byte[] after = saveOf(this);
            record(label, new PropsChange(this, props(), props(), before, after));
            fire();
        }

        @Override
        public void refresh() {
            fire();
        }

        @Override
        public boolean selected() {
            return selected == this;
        }

        @Override
        public void select() {
            SceneObjectStore.this.select(this);
        }

        @Override
        public void remove() {
            SceneObjectStore.this.remove(this);
        }

        private Props props() {
            return new Props(name, pose, visible, locked);
        }

        private void change(String label, Props after) {
            Props before = props();
            apply(after);
            record(label, new PropsChange(this, before, after, null, null));
            fire();
        }

        private void apply(Props p) {
            name = p.name;
            pose = p.pose;
            visible = p.visible;
            locked = p.locked;
        }

        @Override
        public String toString() {
            return "SceneObject[" + name + ", " + typeKey + "]";
        }
    }

    /** Name, pose, flags and (for {@link Entry#edit}) the plugin's state, before and after. */
    private final class PropsChange implements Change {
        private final Entry e;
        private final Props before, after;
        private final byte[] dataBefore, dataAfter;

        PropsChange(Entry e, Props before, Props after, byte[] dataBefore, byte[] dataAfter) {
            this.e = e;
            this.before = before;
            this.after = after;
            this.dataBefore = dataBefore;
            this.dataAfter = dataAfter;
        }

        @Override
        public void undo(Scene scene) {
            e.apply(before);
            loadInto(e, dataBefore);
            fire();
        }

        @Override
        public void redo(Scene scene) {
            e.apply(after);
            loadInto(e, dataAfter);
            fire();
        }
    }

    /** An object added to or removed from the scene at {@code index}. */
    private final class Membership implements Change {
        private final Entry e;
        private final int index;
        private final boolean added;

        Membership(Entry e, int index, boolean added) {
            this.e = e;
            this.index = index;
            this.added = added;
        }

        @Override
        public void undo(Scene scene) {
            if (added) detach(e);
            else attach(e, index);
            fire();
        }

        @Override
        public void redo(Scene scene) {
            if (added) attach(e, index);
            else detach(e);
            fire();
        }
    }
}
