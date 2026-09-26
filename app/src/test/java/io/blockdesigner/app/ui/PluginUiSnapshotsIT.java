package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.PluginHost;
import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.PanelContext;
import io.blockdesigner.plugin.PluginContext;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Renders the example plugin's transform dialogs and panel (plugin API 2) to PNGs under build/ui-snapshots, dark and
 * light. Opens real windows, so it only runs with the environment variable UI_SNAPSHOTS=1.
 */
class PluginUiSnapshotsIT {
    @TempDir
    Path dir;

    @Test
    void snapshots() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("UI_SNAPSHOTS")), "set UI_SNAPSHOTS=1 to render UI snapshots");
        CompletableFuture<Void> started = new CompletableFuture<>();
        try {
            Platform.startup(() -> started.complete(null));
        } catch (IllegalStateException alreadyRunning) {
            started.complete(null);
        }
        started.get(10, TimeUnit.SECONDS);
        Path out = Path.of("build", "ui-snapshots");
        Files.createDirectories(out);
        Path plugins = dir.resolve("plugins");
        Files.createDirectories(plugins);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(System.getProperty("blockdesigner.paletteToolsDir")), "palette-tools*.jar")) {
            for (Path p : ds) Files.copy(p, plugins.resolve("palette-tools.jar"));
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                for (boolean dark : new boolean[]{true, false}) render(plugins, out, dark);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(60, TimeUnit.SECONDS);
    }

    private static void render(Path plugins, Path out, boolean dark) throws Exception {
        String suffix = dark ? "-dark" : "-light";
        AppTheme theme = AppTheme.values()[0];
        javafx.application.Application.setUserAgentStylesheet(theme.userAgentStylesheet(dark));
        Workspace ws = new Workspace(new Settings());
        ws.darkProperty().set(dark);
        Structure s = new Structure();
        for (int x = 0; x < 12; x++) for (int y = 0; y < 6; y++) s.set(x, y, 0, BlockState.of(y == 0 ? "cobblestone" : "stone_bricks"));
        s.set(3, 6, 0, BlockState.parse("oak_stairs[facing=east]"));
        Layer wall = new Layer("Castle wall", s);
        ws.editor().addLayer(wall);
        ViewportPane viewport = new ViewportPane(ws);
        PluginManager pm = new PluginManager(plugins, host(ws), new HashSet<>());
        pm.loadAll();
        for (var t : pm.transforms()) {
            TransformDialog d = new TransformDialog(null, ws, pm, t, viewport);
            d.show();
            // The preview runs when the dialog shows; lay out once more for its status line.
            d.getDialogPane().applyCss();
            d.getDialogPane().layout();
            save(d.getDialogPane().snapshot(null, null), out.resolve("plugin-transform-" + t.transform().id() + suffix + ".png"));
            d.close();
        }
        ExportDialog export = new ExportDialog(null, ws, null, pm.exporters(), null, "plugin:palette-tools/gpl");
        export.show();
        export.getDialogPane().applyCss();
        export.getDialogPane().layout();
        save(export.getDialogPane().snapshot(null, null), out.resolve("plugin-export-card" + suffix + ".png"));
        export.setResult(null);
        export.close();
        var panel = pm.panels().getFirst();
        PluginContext ctx = panel.plugin().context();
        var node = panel.panel().create(new PanelContext() {
            public PluginContext plugin() {
                return ctx;
            }

            public boolean isShowing() {
                return true;
            }

            public void onShown(Runnable action) {
            }

            public void setBadge(String text) {
            }

            public void reveal() {
            }
        });
        StackPane host = new StackPane(node);
        host.getStyleClass().addAll("app-root", "side-panel", dark ? "dark" : "light");
        host.getStylesheets().add(PluginUiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        host.setPrefSize(340, 260);
        javafx.scene.Scene scene = new javafx.scene.Scene(host);
        host.applyCss();
        host.layout();
        save(host.snapshot(null, null), out.resolve("plugin-panel-palette" + suffix + ".png"));
        pm.shutdown();
    }

    private static PluginHost host(Workspace ws) {
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

    private static void save(WritableImage img, Path out) throws Exception {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, w);
        bi.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(bi, "png", out.toFile());
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
