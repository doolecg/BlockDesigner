package io.blockdesigner.app.plugins;

import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.PluginExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the real example plugin (examples/hello-plugin) and exercises every extension point. */
class PluginManagerTest {
    @TempDir
    Path dir;

    private final Scene scene = new Scene();
    private final SceneEditor editor = new SceneEditor(scene);
    private final Map<BlockPos, BlockState> world = new HashMap<>();
    private final Set<String> disabled = new LinkedHashSet<>();
    private PluginManager pm;

    private final PluginHost host = new PluginHost() {
        public Scene scene() {
            return scene;
        }

        public SceneEditor editor() {
            return editor;
        }

        public Optional<Layer> activeLayer() {
            return scene.active();
        }

        public List<Layer> selectedLayers() {
            return scene.active().map(List::of).orElse(List.of());
        }

        public McVersion targetVersion() {
            return McVersion.latestKnown();
        }

        public void editWorld(String label, Consumer<WorldEdit.World> edit) {
            edit.accept(worldView());
        }

        public Layer addLayer(String name, Structure blocks) {
            Layer l = new Layer(name, blocks);
            editor.addLayer(l);
            return l;
        }

        public void status(String message) {
        }

        public void toast(String message) {
        }

        public void runOnUiThread(Runnable task) {
            task.run();
        }

        public void pluginsChanged() {
        }
    };

    private WorldEdit.World worldView() {
        return new WorldEdit.World() {
            public BlockState get(BlockPos p) {
                return world.getOrDefault(p, BlockState.AIR);
            }

            public void set(BlockPos p, BlockState s) {
                world.put(p, s);
            }
        };
    }

    @BeforeEach
    void install() throws IOException {
        Path libs = Path.of(System.getProperty("blockdesigner.examplePluginDir", "../examples/hello-plugin/build/libs"));
        Path jar = null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(libs, "hello-plugin*.jar")) {
            for (Path p : ds) jar = p;
        }
        assertThat(jar).as("example plugin jar in " + libs).isNotNull();
        Path plugins = dir.resolve("plugins");
        Files.createDirectories(plugins);
        Files.copy(jar, plugins.resolve("hello.jar"));
        pm = new PluginManager(plugins, host, disabled);
        pm.loadAll();
    }

    @AfterEach
    void shutdown() {
        pm.shutdown();
    }

    @Test
    void loadsAndRegistersEverything() {
        var p = pm.find("hello").orElseThrow();
        assertThat(p.state()).as(String.valueOf(p.error())).isEqualTo(PluginManager.State.ENABLED);
        assertThat(p.info().name()).isEqualTo("Hello Plugin");
        assertThat(WorldEdit.commands()).anyMatch(c -> c.name().equals("pillar"));
        assertThat(Schematics.byId("hello-text")).isPresent();
        assertThat(pm.exporters()).extracting(e -> e.exporter().id()).containsExactly("bom");
        assertThat(pm.actions()).extracting(a -> a.action().label()).containsExactly("Add test platform");
        assertThat(p.log()).anyMatch(l -> l.contains("Hello from Hello Plugin"));
    }

    @Test
    void commandEditsTheWorldAndValidatesInput() {
        WorldEdit we = new WorldEdit();
        var ctx = new WorldEdit.Context(worldView(), BlockPlacement.Dir.NORTH, new BlockPos(1, 64, 1), BlockState.of("oak_planks"),
                s -> BlockState.parse(s.contains(":") ? s : "minecraft:" + s));
        var r = we.run("/pillar 3", ctx);
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(world).containsEntry(new BlockPos(1, 65, 1), BlockState.of("oak_planks")).containsEntry(new BlockPos(1, 67, 1), BlockState.of("oak_planks"));
        assertThat(we.run("/pillar tall", ctx).ok()).isFalse();
        assertThat(we.run("/help pillar", ctx).message()).contains("/pillar <height>");
    }

    @Test
    void actionAddsALayer() {
        pm.run(pm.actions().getFirst());
        assertThat(scene.layers()).hasSize(1);
        assertThat(scene.layers().getFirst().structure().blockCount()).isEqualTo(81);
    }

    @Test
    void pluginFormatAndExporterWriteFiles() throws IOException {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.of("stone"));
        s.set(2, 1, 0, BlockState.parse("oak_stairs[facing=east]"));
        s.set(3, 1, 0, BlockState.of("stone"));
        var fmt = Schematics.byId("hello-text").orElseThrow();
        Path f = dir.resolve("test.bdtxt");
        Schematics.write(SchematicFile.single("test", s), fmt, WriteOptions.defaults(McVersion.latestKnown()), f);
        var back = Schematics.readDetailed(f);
        assertThat(back.format()).isSameAs(fmt);
        assertThat(back.file().merged().contentEquals(s)).isTrue();

        PluginExporter bom = pm.exporters().getFirst().exporter();
        Path csv = dir.resolve("bom.csv");
        bom.export(new PluginExporter.Request("x", "", McVersion.latestKnown(), s, List.of(), csv));
        assertThat(Files.readString(csv)).startsWith("block,count,stacks\nminecraft:stone,2,");
    }

    @Test
    void disablingRemovesRegistrationsAndIsRemembered() {
        var p = pm.find("hello").orElseThrow();
        pm.setEnabled(p, false);
        assertThat(p.state()).isEqualTo(PluginManager.State.DISABLED);
        assertThat(disabled).contains("hello");
        assertThat(WorldEdit.commands()).noneMatch(c -> c.name().equals("pillar"));
        assertThat(Schematics.byId("hello-text")).isEmpty();
        assertThat(pm.exporters()).isEmpty();
        pm.loadAll();
        assertThat(pm.find("hello").orElseThrow().state()).isEqualTo(PluginManager.State.DISABLED);
        pm.setEnabled(pm.find("hello").orElseThrow(), true);
        assertThat(Schematics.byId("hello-text")).isPresent();
    }

    @Test
    void jarsWithoutDescriptorAreReportedNotLoaded() throws IOException {
        Path bad = pm.folder().resolve("junk.jar");
        try (var z = new java.util.zip.ZipOutputStream(Files.newOutputStream(bad))) {
            z.putNextEntry(new java.util.zip.ZipEntry("x.txt"));
            z.write(1);
        }
        pm.loadAll();
        assertThat(pm.brokenJars()).containsKey("junk.jar");
        assertThat(pm.find("hello").orElseThrow().state()).isEqualTo(PluginManager.State.ENABLED);
    }
}
