package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.PluginHost;
import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.ToolEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the example plugin's Wall tool the way the viewport does: preview while dragging, one undo step per stroke. */
class PluginToolSessionTest {
    @TempDir
    Path dir;

    private final Workspace ws = new Workspace(new Settings());
    private PluginManager pm;
    private Map<BlockPos, BlockState> ghosts = Map.of();
    private List<Box> outlines = List.of();
    private Layer layer;

    private final PluginToolSession.Viewport viewport = new PluginToolSession.Viewport() {
        public void showPreview(Map<BlockPos, BlockState> g, List<BlockPos> removed, List<Box> o) {
            ghosts = g;
            outlines = o;
        }

        public LayeredEdit newEdit(String label) {
            return new LayeredEdit(ws, ws.scene().layers(), layer, null, label, null);
        }
    };

    @BeforeEach
    void load() throws IOException {
        Path plugins = dir.resolve("plugins");
        Files.createDirectories(plugins);
        Path libs = Path.of(System.getProperty("blockdesigner.paletteToolsDir", "../examples/palette-tools/build/libs"));
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(libs, "palette-tools*.jar")) {
            for (Path p : ds) Files.copy(p, plugins.resolve("palette-tools.jar"));
        }
        pm = new PluginManager(plugins, host(), new HashSet<>());
        pm.loadAll();
        layer = new Layer("Ground", new Structure());
        for (int x = 0; x < 10; x++) layer.structure().set(x, 0, 0, BlockState.of("grass_block"));
        ws.editor().addLayer(layer);
    }

    @AfterEach
    void shutdown() {
        pm.shutdown();
    }

    private static ToolEvent at(int x, ToolEvent.Button button) {
        var hit = new ToolEvent.Hit(new BlockPos(x, 0, 0), BlockPlacement.Dir.UP, new BlockPos(x, 1, 0), Optional.empty());
        return new ToolEvent(Optional.of(hit), button, Set.of(), new ToolEvent.Vec3(0, 10, 0), new ToolEvent.Vec3(0, -1, 0));
    }

    @Test
    void dragPreviewsThenReleaseBuildsOneUndoStep() {
        PluginManager.Tool wall = pm.tools().getFirst();
        assertThat(wall.key()).isEqualTo("palette-tools/wall");
        PluginToolSession s = new PluginToolSession(ws, pm, wall, viewport);
        assertThat(s.activate()).isTrue();
        s.setOptions(s.options().with("height", 2));
        int steps = ws.editor().undoStack().size();

        s.press(at(2, ToolEvent.Button.PRIMARY));
        s.drag(at(6, ToolEvent.Button.PRIMARY));
        assertThat(ghosts).hasSize(10); // 5 long, 2 high
        assertThat(outlines).containsExactly(new Box(2, 1, 0, 6, 2, 0));
        assertThat(layer.structure().blockCount()).as("nothing built while dragging").isEqualTo(10);

        s.release(at(6, ToolEvent.Button.PRIMARY));
        assertThat(ghosts).isEmpty();
        assertThat(layer.structure().blockCount()).isEqualTo(20);
        assertThat(layer.structure().get(4, 2, 0).path()).endsWith("stone_bricks");
        assertThat(ws.editor().undoStack().size()).isEqualTo(steps + 1);
        assertThat(ws.editor().undoStack().undoLabel()).contains("Build wall");
        ws.editor().undoStack().undo();
        assertThat(layer.structure().blockCount()).isEqualTo(10);
        // The chosen height is remembered for next time.
        assertThat(new PluginToolSession(ws, pm, wall, viewport).options().integer("height")).isEqualTo(2);
    }

    @Test
    void escapeAndTheWheelGoToTheTool() {
        PluginToolSession s = new PluginToolSession(ws, pm, pm.tools().getFirst(), viewport);
        s.activate();
        assertThat(s.key("Esc")).as("nothing to cancel yet").isFalse();
        assertThat(s.scroll(at(1, ToolEvent.Button.NONE), 1)).as("the wheel zooms when not dragging").isFalse();
        s.press(at(1, ToolEvent.Button.PRIMARY));
        int before = ghosts.size();
        assertThat(s.scroll(at(1, ToolEvent.Button.NONE), 1)).isTrue();
        assertThat(ghosts.size()).isGreaterThan(before);
        assertThat(s.key("Esc")).isTrue();
        assertThat(ghosts).isEmpty();
        s.release(at(1, ToolEvent.Button.PRIMARY));
        assertThat(layer.structure().blockCount()).as("cancelled: nothing built").isEqualTo(10);
        s.deactivate();
    }

    private PluginHost host() {
        return new PluginHost() {
            public Scene scene() {
                return ws.scene();
            }

            public SceneEditor editor() {
                return ws.editor();
            }

            public Optional<Layer> activeLayer() {
                return ws.scene().active();
            }

            public List<Layer> selectedLayers() {
                return List.of();
            }

            public McVersion targetVersion() {
                return McVersion.latestKnown();
            }

            public void editWorld(String label, Consumer<WorldEdit.World> edit) {
            }

            public Layer addLayer(String name, Structure blocks) {
                return null;
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
    }
}
