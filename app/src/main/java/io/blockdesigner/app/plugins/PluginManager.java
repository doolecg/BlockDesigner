package io.blockdesigner.app.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.BlockDesignerPlugin;
import io.blockdesigner.plugin.PluginAction;
import io.blockdesigner.plugin.PluginApi;
import io.blockdesigner.plugin.PluginCommand;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginExporter;
import io.blockdesigner.plugin.PluginImporter;
import io.blockdesigner.plugin.PluginInfo;
import io.blockdesigner.plugin.PluginPanel;
import io.blockdesigner.plugin.PluginTool;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.SceneEvent;
import io.blockdesigner.plugin.SceneObjectType;
import io.blockdesigner.plugin.SceneObjects;
import io.blockdesigner.plugin.Subscription;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Finds plugin jars in a folder, loads each in its own class loader and tracks what it registered, so a plugin can be
 * disabled (or fail) without leaving formats, commands or menu entries behind. Use from the JavaFX thread.
 */
public final class PluginManager {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");

    public enum State { ENABLED, DISABLED, FAILED, INCOMPATIBLE }

    /** An exporter together with the plugin that added it. */
    public record Export(Plugin plugin, PluginExporter exporter) {
    }

    /** A menu action together with the plugin that added it. */
    public record Action(Plugin plugin, PluginAction action) {
    }

    /** A transform together with the plugin that added it. */
    public record Transform(Plugin plugin, PluginTransform transform) {
        /** {@code plugin/transform-id}, unique across plugins. */
        public String key() {
            return plugin.info().id() + "/" + transform.id();
        }
    }

    /** A tool together with the plugin that added it. */
    public record Tool(Plugin plugin, PluginTool tool) {
        /** {@code plugin/tool-id}, unique across plugins; the key its options and key bind are kept under. */
        public String key() {
            return plugin.info().id() + "/" + tool.id();
        }
    }

    /** An importer together with the plugin that added it. */
    public record Import(Plugin plugin, PluginImporter importer) {
        /** {@code plugin/importer-id}, unique across plugins; the key its options are remembered under. */
        public String key() {
            return plugin.info().id() + "/" + importer.id();
        }
    }

    /** A panel together with the plugin that added it. */
    public record Panel(Plugin plugin, PluginPanel panel) {
        /** {@code plugin/panel-id}, unique across plugins. */
        public String key() {
            return plugin.info().id() + "/" + panel.id();
        }
    }

    /** One plugin jar and its current state. */
    public final class Plugin {
        private final PluginInfo info;
        private final Path jar;
        private State state = State.DISABLED;
        private String error;
        private final List<String> log = new ArrayList<>();
        private URLClassLoader loader;
        private BlockDesignerPlugin instance;
        private final List<SchematicFormat> formats = new ArrayList<>();
        private final List<PluginExporter> exporters = new ArrayList<>();
        private final List<PluginAction> actions = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();
        private final List<PluginTransform> transforms = new ArrayList<>();
        private final List<PluginPanel> panels = new ArrayList<>();
        private final List<PluginImporter> importers = new ArrayList<>();
        private final List<PluginTool> tools = new ArrayList<>();
        private final List<SceneObjectType> objectTypes = new ArrayList<>();
        private Context context;
        /** The last scene object failure shown as a toast (a failing draw repeats every frame). */
        private String lastObjectError;

        private Plugin(PluginInfo info, Path jar) {
            this.info = info;
            this.jar = jar;
        }

        public PluginInfo info() {
            return info;
        }

        public Path jar() {
            return jar;
        }

        public State state() {
            return state;
        }

        /** Why it failed or can't load, or null. */
        public String error() {
            return error;
        }

        public List<String> log() {
            return List.copyOf(log);
        }

        /** Summary of what it adds, e.g. "2 formats · 1 command". */
        public String contributions() {
            List<String> parts = new ArrayList<>();
            if (!formats.isEmpty()) parts.add(formats.size() + (formats.size() == 1 ? " format" : " formats"));
            if (!exporters.isEmpty()) parts.add(exporters.size() + (exporters.size() == 1 ? " exporter" : " exporters"));
            if (!importers.isEmpty()) parts.add(importers.size() + (importers.size() == 1 ? " importer" : " importers"));
            if (!actions.isEmpty()) parts.add(actions.size() + (actions.size() == 1 ? " action" : " actions"));
            if (!commands.isEmpty()) parts.add(commands.size() + (commands.size() == 1 ? " command" : " commands"));
            if (!tools.isEmpty()) parts.add(tools.size() + (tools.size() == 1 ? " tool" : " tools"));
            if (!transforms.isEmpty()) parts.add(transforms.size() + (transforms.size() == 1 ? " transform" : " transforms"));
            if (!panels.isEmpty()) parts.add(panels.size() + (panels.size() == 1 ? " panel" : " panels"));
            if (!objectTypes.isEmpty()) parts.add(objectTypes.size() + (objectTypes.size() == 1 ? " object type" : " object types"));
            return parts.isEmpty() ? "nothing registered" : String.join(" · ", parts);
        }

        /** The context handed to the plugin while it is enabled (null otherwise). */
        public PluginContext context() {
            return context;
        }

        private void log(String line) {
            log.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + line);
            if (log.size() > 200) log.removeFirst();
        }
    }

    private final Path folder;
    private final PluginHost host;
    private final Set<String> disabled;
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    /** Jars that couldn't even be read (no descriptor, broken zip), by file name. */
    private final Map<String, String> broken = new LinkedHashMap<>();

    private final SceneEventBus events;
    private final BlockCatalog catalog;
    private final io.blockdesigner.plugin.AssetAccess assetAccess;
    private final OptionStore optionStore;
    private final SceneObjectStore objects;
    private boolean transformCommand;

    /**
     * @param disabled ids the user switched off; this set is updated as plugins are enabled and disabled
     */
    public PluginManager(Path folder, PluginHost host, Set<String> disabled) {
        this(folder, host, disabled, new LinkedHashMap<>());
    }

    /**
     * @param disabled     ids the user switched off; this set is updated as plugins are enabled and disabled
     * @param savedOptions where the last values of plugin options are kept (the settings' map), updated in place
     */
    public PluginManager(Path folder, PluginHost host, Set<String> disabled, Map<String, Map<String, String>> savedOptions) {
        this.folder = folder;
        this.host = host;
        this.disabled = disabled;
        this.catalog = new AppBlockCatalog(host::assets);
        this.assetAccess = new AppAssetAccess(host::assets);
        this.optionStore = new OptionStore(savedOptions);
        this.events = new SceneEventBus(host.scene(), host::selection, host::runLater, (owner, t) -> {
            if (owner instanceof Plugin p) p.log("Event listener failed: " + t);
        });
        SceneObjectStore s = host.objects();
        this.objects = s != null ? s : new SceneObjectStore(host.editor() == null ? null : host.editor().undoStack());
        objects.setErrors((owner, t) -> {
            if (!(owner instanceof Plugin p)) return;
            p.log("Scene object failed: " + t);
            String msg = t.getMessage() == null ? t.toString() : t.getMessage();
            if (!msg.equals(p.lastObjectError)) host.toast("✖ " + p.info().name() + ": " + msg);
            p.lastObjectError = msg;
        });
    }

    public Path folder() {
        return folder;
    }

    public Collection<Plugin> plugins() {
        return List.copyOf(plugins.values());
    }

    public Map<String, String> brokenJars() {
        return Map.copyOf(broken);
    }

    public Optional<Plugin> find(String id) {
        return Optional.ofNullable(plugins.get(id));
    }

    public List<Export> exporters() {
        List<Export> out = new ArrayList<>();
        for (Plugin p : plugins.values()) for (PluginExporter e : p.exporters) out.add(new Export(p, e));
        return out;
    }

    public List<Action> actions() {
        List<Action> out = new ArrayList<>();
        for (Plugin p : plugins.values()) for (PluginAction a : p.actions) out.add(new Action(p, a));
        return out;
    }

    public List<Transform> transforms() {
        List<Transform> out = new ArrayList<>();
        for (Plugin p : plugins.values()) for (PluginTransform t : p.transforms) out.add(new Transform(p, t));
        return out;
    }

    /** A transform by its id ({@code weather}) or, when two plugins use the same id, {@code plugin/id}. */
    public Optional<Transform> findTransform(String id) {
        List<Transform> all = transforms();
        for (Transform t : all) if (t.key().equals(id)) return Optional.of(t);
        return all.stream().filter(t -> t.transform().id().equals(id)).findFirst();
    }

    public List<Tool> tools() {
        List<Tool> out = new ArrayList<>();
        for (Plugin p : plugins.values()) for (PluginTool t : p.tools) out.add(new Tool(p, t));
        return out;
    }

    public List<Import> importers() {
        List<Import> out = new ArrayList<>();
        for (Plugin p : plugins.values()) for (PluginImporter i : p.importers) out.add(new Import(p, i));
        return out;
    }

    /** The importer for a file, by its extension. */
    public Optional<Import> importerFor(Path file) {
        String n = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        for (Import i : importers()) {
            for (String ext : i.importer().extensions()) if (n.endsWith("." + ext.toLowerCase(java.util.Locale.ROOT))) return Optional.of(i);
        }
        return Optional.empty();
    }

    public List<Panel> panels() {
        List<Panel> out = new ArrayList<>();
        for (Plugin p : plugins.values()) for (PluginPanel x : p.panels) out.add(new Panel(p, x));
        return out;
    }

    /** The block catalog plugins see. */
    public BlockCatalog blocks() {
        return catalog;
    }

    /** Block models, the atlas and texture files plugins see. */
    public io.blockdesigner.plugin.AssetAccess assets() {
        return assetAccess;
    }

    /** Last-used option values per plugin feature. */
    public OptionStore optionStore() {
        return optionStore;
    }

    /** The plugins' scene objects in the open project. */
    public SceneObjectStore objects() {
        return objects;
    }

    /** The selection changed: tells plugins listening for {@link SceneEvent.SelectionChanged} (measured via the host). */
    public void selectionChanged() {
        events.selectionChanged();
    }

    /** A project was opened or a new one started: tells plugins listening for {@link SceneEvent.ProjectOpened}. */
    public void projectOpened(Optional<Path> file) {
        events.projectOpened(file);
    }

    /** Writes a line to the plugin's log (shown in the Plugins window). */
    public void log(Plugin p, String line) {
        p.log(line);
    }

    /** Logs a failure of one of a plugin's features against it and shows it as a toast. */
    public void report(Plugin p, String what, Throwable t) {
        p.log(what + " failed: " + t);
        host.toast("✖ " + p.info().name() + ": " + (t.getMessage() == null ? t.toString() : t.getMessage()));
    }

    /** Runs a plugin's menu action; an exception is logged against the plugin and shown as a toast. */
    public void run(Action a) {
        try {
            a.action().action().run();
        } catch (Throwable t) {
            a.plugin().log("'" + a.action().label() + "' failed: " + t);
            host.toast("✖ " + a.plugin().info().name() + ": " + t.getMessage());
        }
    }

    /** Scans the folder and enables every plugin the user hasn't switched off. Safe to call again (reload). */
    public void loadAll() {
        for (Plugin p : List.copyOf(plugins.values())) unload(p);
        plugins.clear();
        broken.clear();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            broken.put(folder.toString(), "Can't create the plugins folder: " + e.getMessage());
            return;
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "*.jar")) {
            ds.forEach(jars::add);
        } catch (IOException e) {
            broken.put(folder.toString(), e.getMessage());
        }
        jars.sort(null);
        for (Path jar : jars) discover(jar);
        for (Plugin p : plugins.values()) {
            if (p.state == State.INCOMPATIBLE) continue;
            if (disabled.contains(p.info.id())) p.state = State.DISABLED;
            else enable(p);
        }
        host.pluginsChanged();
    }

    private Plugin discover(Path jar) {
        PluginInfo info;
        try {
            info = readDescriptor(jar);
        } catch (IOException | RuntimeException e) {
            broken.put(jar.getFileName().toString(), e.getMessage());
            return null;
        }
        if (plugins.containsKey(info.id())) {
            broken.put(jar.getFileName().toString(), "Another jar already provides the plugin id '" + info.id() + "'");
            return null;
        }
        Plugin p = new Plugin(info, jar);
        if (info.api() > PluginApi.VERSION) {
            p.state = State.INCOMPATIBLE;
            p.error = "Needs plugin API " + info.api() + "; this BlockDesigner has API " + PluginApi.VERSION + ". Update BlockDesigner.";
        }
        plugins.put(info.id(), p);
        return p;
    }

    /** Reads and checks {@code blockdesigner-plugin.json} from a jar. */
    public static PluginInfo readDescriptor(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry(PluginApi.DESCRIPTOR);
            if (entry == null) throw new IOException("Not a BlockDesigner plugin: no " + PluginApi.DESCRIPTOR + " inside");
            JsonNode n;
            try (InputStream in = zip.getInputStream(entry)) {
                n = JSON.readTree(in);
            }
            String id = n.path("id").asText("");
            String main = n.path("main").asText("");
            if (!ID.matcher(id).matches()) throw new IOException("Plugin id '" + id + "' must use a-z, 0-9, _ . -");
            if (main.isBlank()) throw new IOException("The descriptor has no \"main\" class");
            return new PluginInfo(id, n.path("name").asText(null), n.path("version").asText(null), n.path("author").asText(null),
                    n.path("description").asText(null), main, n.path("api").asInt(1));
        }
    }

    /** Copies a jar into the plugins folder (replacing an older copy of the same plugin) and enables it. */
    public Plugin install(Path jar) throws IOException {
        PluginInfo info = readDescriptor(jar);
        Plugin existing = plugins.get(info.id());
        if (existing != null) {
            unload(existing);
            plugins.remove(info.id());
            if (!existing.jar.getFileName().equals(jar.getFileName())) Files.deleteIfExists(existing.jar);
        }
        Files.createDirectories(folder);
        Path target = folder.resolve(jar.getFileName());
        if (!target.toAbsolutePath().normalize().equals(jar.toAbsolutePath().normalize())) {
            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
        }
        broken.remove(target.getFileName().toString());
        Plugin p = discover(target);
        if (p == null) throw new IOException(broken.getOrDefault(target.getFileName().toString(), "Could not load " + jar.getFileName()));
        disabled.remove(info.id());
        if (p.state != State.INCOMPATIBLE) enable(p);
        host.pluginsChanged();
        return p;
    }

    /** Disables the plugin and deletes its jar. */
    public void uninstall(Plugin p) throws IOException {
        unload(p);
        plugins.remove(p.info.id());
        disabled.remove(p.info.id());
        Files.deleteIfExists(p.jar);
        host.pluginsChanged();
    }

    public void setEnabled(Plugin p, boolean on) {
        if (on) {
            disabled.remove(p.info.id());
            if (p.state != State.ENABLED && p.state != State.INCOMPATIBLE) enable(p);
        } else {
            disabled.add(p.info.id());
            unload(p);
            if (p.state != State.INCOMPATIBLE) p.state = State.DISABLED;
        }
        host.pluginsChanged();
    }

    private void enable(Plugin p) {
        p.error = null;
        try {
            p.loader = new URLClassLoader("plugin-" + p.info.id(), new URL[]{p.jar.toUri().toURL()}, PluginManager.class.getClassLoader());
            Class<?> main = Class.forName(p.info.mainClass(), true, p.loader);
            if (!BlockDesignerPlugin.class.isAssignableFrom(main)) {
                throw new IllegalStateException(p.info.mainClass() + " does not implement BlockDesignerPlugin");
            }
            p.instance = (BlockDesignerPlugin) main.getDeclaredConstructor().newInstance();
            p.context = new Context(p);
            p.instance.enable(p.context);
            p.state = State.ENABLED;
            p.log("Enabled · " + p.contributions());
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
            p.error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            p.log("Failed to enable: " + p.error);
            unload(p);
            p.state = State.FAILED;
        }
    }

    /** Calls disable, removes everything the plugin registered and closes its class loader. */
    private void unload(Plugin p) {
        // Let the app put down the plugin's active tool (and its preview) while the plugin is still whole.
        if (!p.tools.isEmpty()) host.pluginUnloading(p);
        if (p.instance != null) {
            try {
                p.instance.disable();
            } catch (Throwable t) {
                p.log("Error while disabling: " + t);
            }
            p.instance = null;
        }
        p.formats.forEach(Schematics::unregister);
        p.formats.clear();
        p.commands.forEach(WorldEdit::unregister);
        p.commands.clear();
        p.exporters.clear();
        p.actions.clear();
        p.transforms.clear();
        p.importers.clear();
        p.tools.clear();
        updateTransformCommand();
        for (PluginPanel panel : p.panels) {
            try {
                panel.dispose();
            } catch (Throwable t) {
                p.log("Error while closing panel '" + panel.id() + "': " + t);
            }
        }
        p.panels.clear();
        // Its objects stay in the project, parked until the plugin is back.
        if (!p.objectTypes.isEmpty()) objects.unregister(p);
        p.objectTypes.clear();
        p.lastObjectError = null;
        events.removeAll(p);
        p.context = null;
        if (p.loader != null) {
            try {
                p.loader.close();
            } catch (IOException ignored) {
                // the jar handle is released when the loader is collected
            }
            p.loader = null;
        }
        if (p.state == State.ENABLED) p.state = State.DISABLED;
    }

    /** Disables everything (app shutdown). */
    public void shutdown() {
        for (Plugin p : plugins.values()) unload(p);
        events.close();
    }

    /** {@code /transform <id>} exists while any plugin has a transform. */
    private void updateTransformCommand() {
        boolean want = plugins.values().stream().anyMatch(p -> !p.transforms.isEmpty());
        if (want == transformCommand) return;
        if (want) {
            try {
                WorldEdit.register(new WorldEdit.Command("transform", "/transform <id>", "Open a plugin transform (/transform lists them)"),
                        (args, flags, c, region) -> transformCommand(args));
                transformCommand = true;
            } catch (IllegalArgumentException taken) {
                // A plugin already has a /transform command of its own; the menus still offer the transforms.
            }
        } else {
            WorldEdit.unregister("transform");
            transformCommand = false;
        }
    }

    private WorldEdit.Result transformCommand(List<String> args) {
        if (args.isEmpty()) {
            List<String> ids = transforms().stream().map(t -> t.transform().id() + " (" + t.transform().name() + ")").toList();
            return WorldEdit.Result.ok("Transforms: " + String.join(", ", ids), 0);
        }
        Optional<Transform> t = findTransform(args.getFirst());
        if (t.isEmpty()) return WorldEdit.Result.error("No transform '" + args.getFirst() + "' · /transform lists them");
        // The command runs inside an edit (an undo group); the dialog opens once that is over.
        host.runLater(() -> host.openTransform(t.get()));
        return WorldEdit.Result.ok("Opening " + t.get().transform().name() + "…", 0);
    }

    // ---- the context handed to each plugin -------------------------------------------------------------------

    private final class Context implements PluginContext {
        private final Plugin p;
        private SceneObjects sceneObjects;

        Context(Plugin p) {
            this.p = p;
        }

        @Override
        public PluginInfo info() {
            return p.info;
        }

        @Override
        public Path dataFolder() {
            Path d = folder.resolve(p.info.id());
            try {
                Files.createDirectories(d);
            } catch (IOException e) {
                p.log("Can't create data folder: " + e.getMessage());
            }
            return d;
        }

        @Override
        public void log(String message) {
            p.log(message);
        }

        @Override
        public void registerFormat(SchematicFormat format) {
            Schematics.register(format);
            p.formats.add(format);
        }

        @Override
        public void registerExporter(PluginExporter exporter) {
            for (PluginExporter e : p.exporters) {
                if (e.id().equals(exporter.id())) throw new IllegalArgumentException("Exporter id '" + exporter.id() + "' registered twice");
            }
            p.exporters.add(exporter);
        }

        @Override
        public void registerAction(PluginAction action) {
            p.actions.add(action);
        }

        @Override
        public void registerTransform(PluginTransform transform) {
            if (!transform.id().matches("[a-z0-9_]+")) throw new IllegalArgumentException("Transform ids use a-z, 0-9 and _ only: " + transform.id());
            for (PluginTransform t : p.transforms) {
                if (t.id().equals(transform.id())) throw new IllegalArgumentException("Transform id '" + transform.id() + "' registered twice");
            }
            p.transforms.add(transform);
            updateTransformCommand();
        }

        @Override
        public void registerPanel(PluginPanel panel) {
            if (!panel.id().matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Panel ids use a-z, 0-9, _ . - only: " + panel.id());
            for (PluginPanel x : p.panels) {
                if (x.id().equals(panel.id())) throw new IllegalArgumentException("Panel id '" + panel.id() + "' registered twice");
            }
            p.panels.add(panel);
        }

        @Override
        public void registerTool(PluginTool tool) {
            if (!tool.id().matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Tool ids use a-z, 0-9, _ . - only: " + tool.id());
            for (PluginTool t : p.tools) {
                if (t.id().equals(tool.id())) throw new IllegalArgumentException("Tool id '" + tool.id() + "' registered twice");
            }
            p.tools.add(tool);
        }

        @Override
        public void registerObjectType(SceneObjectType type) {
            objects.register(p, p.info.id(), type);
            p.objectTypes.add(type);
        }

        @Override
        public SceneObjects objects() {
            if (sceneObjects == null) sceneObjects = objects.forPlugin(p.info.id());
            return sceneObjects;
        }

        @Override
        public void registerImporter(PluginImporter importer) {
            for (PluginImporter i : p.importers) {
                if (i.id().equals(importer.id())) throw new IllegalArgumentException("Importer id '" + importer.id() + "' registered twice");
            }
            if (importer.extensions().isEmpty()) throw new IllegalArgumentException("Importer '" + importer.id() + "' has no extensions");
            p.importers.add(importer);
        }

        @Override
        public <E extends SceneEvent> Subscription on(Class<E> type, Consumer<? super E> listener) {
            return events.subscribe(p, type, listener);
        }

        @Override
        public BlockCatalog blocks() {
            return catalog;
        }

        @Override
        public io.blockdesigner.plugin.AssetAccess assets() {
            return assetAccess;
        }

        @Override
        public Optional<Box> selection() {
            return host.selection();
        }

        @Override
        public void registerCommand(PluginCommand command) {
            WorldEdit.register(new WorldEdit.Command(command.name(), command.usage(), command.description() + " (" + p.info.name() + ")"),
                    (args, flags, c, region) -> runCommand(command, args, flags, c, region));
            p.commands.add(command.name());
        }

        private WorldEdit.Result runCommand(PluginCommand command, List<String> args, List<String> flags, WorldEdit.Context c,
                                            io.blockdesigner.core.model.Box region) {
            PluginCommand.BlockResolver blocks = text -> {
                BlockState st = c.resolve() == null ? null : c.resolve().apply(text);
                if (st == null) throw new IllegalArgumentException("Unknown block: " + text);
                return st;
            };
            var ctx = new PluginCommand.Context(List.copyOf(args), List.copyOf(flags), c.world(), Optional.ofNullable(region),
                    Optional.ofNullable(c.aim()), Optional.ofNullable(c.hand()), blocks);
            try {
                String msg = command.handler().run(ctx);
                return WorldEdit.Result.ok(msg == null || msg.isBlank() ? "/" + command.name() + " done." : msg, 0);
            } catch (IllegalArgumentException e) {
                return WorldEdit.Result.error(e.getMessage());
            } catch (Throwable t) {
                p.log("/" + command.name() + " failed: " + t);
                return WorldEdit.Result.error(p.info.name() + " failed: " + t.getMessage());
            }
        }

        @Override
        public Scene scene() {
            return host.scene();
        }

        @Override
        public SceneEditor editor() {
            return host.editor();
        }

        @Override
        public Optional<Layer> activeLayer() {
            return host.activeLayer();
        }

        @Override
        public List<Layer> selectedLayers() {
            return host.selectedLayers();
        }

        @Override
        public McVersion targetVersion() {
            return host.targetVersion();
        }

        @Override
        public void editWorld(String label, Consumer<WorldEdit.World> edit) {
            host.editWorld(label, edit);
        }

        @Override
        public Layer addLayer(String name, Structure blocks) {
            return host.addLayer(name, blocks);
        }

        @Override
        public void status(String message) {
            host.status(message);
        }

        @Override
        public void toast(String message) {
            host.toast(message);
        }

        @Override
        public void runOnUiThread(Runnable task) {
            host.runOnUiThread(task);
        }
    }
}
