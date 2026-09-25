package io.blockdesigner.app.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.BlockDesignerPlugin;
import io.blockdesigner.plugin.PluginAction;
import io.blockdesigner.plugin.PluginApi;
import io.blockdesigner.plugin.PluginCommand;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginExporter;
import io.blockdesigner.plugin.PluginInfo;

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
            if (!actions.isEmpty()) parts.add(actions.size() + (actions.size() == 1 ? " action" : " actions"));
            if (!commands.isEmpty()) parts.add(commands.size() + (commands.size() == 1 ? " command" : " commands"));
            return parts.isEmpty() ? "nothing registered" : String.join(" · ", parts);
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

    /**
     * @param disabled ids the user switched off; this set is updated as plugins are enabled and disabled
     */
    public PluginManager(Path folder, PluginHost host, Set<String> disabled) {
        this.folder = folder;
        this.host = host;
        this.disabled = disabled;
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
            p.instance.enable(new Context(p));
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
    }

    // ---- the context handed to each plugin -------------------------------------------------------------------

    private final class Context implements PluginContext {
        private final Plugin p;

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
