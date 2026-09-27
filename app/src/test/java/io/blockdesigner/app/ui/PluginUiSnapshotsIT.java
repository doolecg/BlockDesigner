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
 * Renders the example plugin's transform dialogs and panel (plugin API 2), and the plugin UI kit (API 6: a component
 * gallery at 280, 360 and 440 px, a plugin tab's Overview, a plugin's Settings page, Keybinds with plugin keys, grouped
 * options in a dialog) to PNGs under build/ui-snapshots, dark and light. Opens real windows, so it only runs with the
 * environment variable UI_SNAPSHOTS=1.
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
            // The newest one: builds of earlier versions stay in build/libs next to it.
            Path newest = null;
            for (Path p : ds) if (newest == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(newest)) > 0) newest = p;
            if (newest != null) Files.copy(newest, plugins.resolve("palette-tools.jar"));
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

        // ---- API 6: the UI kit
        for (int w : new int[]{280, 360, 440}) save(inPanel(gallery(), w, dark), out.resolve("kit-gallery-" + w + suffix + ".png"));
        var palette = pm.find("palette-tools").orElseThrow();
        palette.context().registerSettings(groupedOptions(), v -> {
        });
        palette.context().setPanelStatus("palette", io.blockdesigner.plugin.ui.Tone.SUCCESS, "Counting the selection");
        java.util.Map<String, String> pages = new java.util.HashMap<>();
        pages.put("palette-tools", PluginHomeTab.OVERVIEW);
        PluginHomeTab home = new PluginHomeTab(palette, pm, pages, homeActions());
        for (int w : new int[]{300, 440}) {
            save(inPanel(home.tab.getContent(), w, dark), out.resolve("plugin-overview-" + w + suffix + ".png"));
        }
        home.showPanel("palette");
        save(inPanel(home.tab.getContent(), 360, dark), out.resolve("plugin-page-bar" + suffix + ".png"));
        Keybinds keys = new Keybinds(ws.settings());
        SettingsDialog sd = new SettingsDialog(null, ws, keys, () -> {
        }, () -> {
        }, () -> {
        }, () -> {
        }, pm, new PluginKeys(ws.settings(), keys), null, "palette-tools");
        snapshotDialog(sd, out.resolve("settings-plugin-page" + suffix + ".png"), false);
        sd.getDialogPane().lookupAll(".settings-nav-item").stream()
                .filter(n -> n instanceof javafx.scene.control.ToggleButton tb && "Keybinds".equals(tb.getText()))
                .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).fire());
        sd.getDialogPane().lookupAll(".search-field").stream().filter(f -> f instanceof javafx.scene.control.TextField)
                .findFirst().ifPresent(f -> ((javafx.scene.control.TextField) f).setText("palette"));
        snapshotDialog(sd, out.resolve("settings-keybinds-plugins" + suffix + ".png"), true);

        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> importDialog = new javafx.scene.control.Dialog<>();
        importDialog.setHeaderText(null);
        Dialogs.style(importDialog.getDialogPane(), dark);
        importDialog.getDialogPane().getStyleClass().add("bd-dialog");
        OptionsEditor editor = new OptionsEditor(groupedOptions().defaults(), pm.blocks(), () -> null, v -> {
        });
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(editor);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("bd-scroll");
        scroll.setPrefViewportHeight(420);
        javafx.scene.control.Label title = new javafx.scene.control.Label("Pixel art from a picture (Pixel Art Generator)");
        title.getStyleClass().add("bd-page-title");
        javafx.scene.layout.VBox body = new javafx.scene.layout.VBox(12, title, scroll);
        body.setPrefWidth(420);
        importDialog.getDialogPane().setContent(body);
        importDialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.CANCEL,
                new javafx.scene.control.ButtonType("Import", javafx.scene.control.ButtonBar.ButtonData.OK_DONE));
        snapshotDialog(importDialog, out.resolve("import-dialog-grouped" + suffix + ".png"), true);
        pm.shutdown();
    }

    /** Options laid out with API 6 metadata: groups, an advanced group, help, units, enabledWhen. */
    private static io.blockdesigner.plugin.Options groupedOptions() {
        return io.blockdesigner.plugin.Options.builder()
                .choice("mode", "Mode", List.of("Flat", "Relief", "Model"), "Flat")
                .group("Shape")
                .integer("width", "Width", 64, 1, 512).unit("blocks").help("How wide the art is; the height follows the picture.")
                .choice("orientation", "Stands", List.of("Upright", "Lying flat"), "Upright")
                .toggle("hollow", "Hollow inside", false)
                .integer("wall", "Wall thickness", 1, 1, 8).unit("blocks").enabledWhen("hollow")
                .group("Blocks")
                .choice("blocks", "Blocks", List.of("Concrete", "Wool", "Terracotta", "Everything"), "Concrete")
                .integer("maxBlocks", "Most kinds", 0, 0, 64).help("0: any number")
                .group("Background")
                .decimal("tolerance", "Tolerance", 0.1, 0, 0.4).unit("%").help("How close to the background colour counts as background.")
                .toggle("trim", "Trim the empty edges", true)
                .advanced("Picture adjustments")
                .decimal("brightness", "Brightness", 0, -1, 1)
                .decimal("contrast", "Contrast", 0, -1, 1)
                .build();
    }

    /** A page of every kit component, as a plugin would build it. */
    private static javafx.scene.Node gallery() {
        io.blockdesigner.plugin.ui.Form form = new io.blockdesigner.plugin.ui.Form();
        javafx.scene.control.ComboBox<String> scope = new javafx.scene.control.ComboBox<>();
        scope.getItems().setAll("The selection", "Visible layers", "The active layer");
        scope.setValue("Visible layers");
        form.row("Count in", scope);
        javafx.scene.control.Spinner<Integer> height = new javafx.scene.control.Spinner<>(1, 512, 16);
        height.setPrefWidth(88);
        form.row("Height", height).unit("blocks").help("New pictures are this many blocks tall.");
        form.row("Seed", new javafx.scene.control.TextField("12ab")).error("A seed should be a whole number.");
        javafx.scene.control.CheckBox live = new javafx.scene.control.CheckBox("Send changes live");
        form.row(live);
        form.row("Delay", new javafx.scene.control.TextField("2")).enabledWhen(live.selectedProperty());
        var badges = new javafx.scene.layout.FlowPane(6, 6);
        for (io.blockdesigner.plugin.ui.Tone t : io.blockdesigner.plugin.ui.Tone.values()) {
            badges.getChildren().add(new io.blockdesigner.plugin.ui.StatusBadge(t, t.name().charAt(0) + t.name().substring(1).toLowerCase()));
        }
        io.blockdesigner.plugin.ui.Banner banner = new io.blockdesigner.plugin.ui.Banner();
        banner.show(io.blockdesigner.plugin.ui.Tone.DANGER, "Couldn't reach the game. Is BlockCompanion running?", "Retry", () -> {
        });
        io.blockdesigner.plugin.ui.ItemList<String> list = new io.blockdesigner.plugin.ui.ItemList<>(s -> io.blockdesigner.plugin.ui.ItemRow.of(s)
                .swatch(0xFF8A8F98).meta(s.length() * 7 + " of 120 · " + (s.length() % 2 == 0 ? "done" : "missing"),
                        s.length() % 2 == 0 ? io.blockdesigner.plugin.ui.Tone.SUCCESS : io.blockdesigner.plugin.ui.Tone.NEUTRAL)
                .trailing(io.blockdesigner.plugin.ui.Controls.caption(s.length() + "×"),
                        io.blockdesigner.plugin.ui.Controls.iconButton(io.blockdesigner.plugin.ui.Icon.CHECK, "Mark as gathered", null)));
        list.getItems().setAll("Stone bricks", "Oak planks", "Spruce log with a very long name that has to be cut", "Glass");
        list.visibleRows(2, 6);
        var icons = new javafx.scene.layout.FlowPane(4, 4);
        for (io.blockdesigner.plugin.ui.Icon i : io.blockdesigner.plugin.ui.Icon.values()) {
            icons.getChildren().add(io.blockdesigner.plugin.ui.Controls.iconButton(i, i.name(), null));
        }
        io.blockdesigner.plugin.ui.Section adv = new io.blockdesigner.plugin.ui.Section("Advanced",
                io.blockdesigner.plugin.ui.Controls.hint("Collapsed until opened.")).collapsible(false);
        return new io.blockdesigner.plugin.ui.PanelScaffold()
                .top(new io.blockdesigner.plugin.ui.Segmented<>(List.of("Picture", "Regions", "Blocks"), s -> s))
                .add(new io.blockdesigner.plugin.ui.Section("Count", form)
                                .actions(io.blockdesigner.plugin.ui.Controls.iconButton(io.blockdesigner.plugin.ui.Icon.REFRESH, "Count again", null)),
                        new io.blockdesigner.plugin.ui.Section("Status", badges, banner).badge(
                                new io.blockdesigner.plugin.ui.StatusBadge(io.blockdesigner.plugin.ui.Tone.SUCCESS, "Connected")),
                        new io.blockdesigner.plugin.ui.Section("Items", io.blockdesigner.plugin.ui.Controls.search("Filter blocks…"), list),
                        new io.blockdesigner.plugin.ui.Section("Nothing yet", new io.blockdesigner.plugin.ui.EmptyState(
                                io.blockdesigner.plugin.ui.Icon.IMAGE, "No picture open.").hint("Open a picture or drop one here.")
                                .action(io.blockdesigner.plugin.ui.Controls.primary("Open picture…", null))),
                        new io.blockdesigner.plugin.ui.Section("Icons", icons), adv)
                .footer(new io.blockdesigner.plugin.ui.ActionBar(io.blockdesigner.plugin.ui.Controls.button("Copy list", "Copy", null),
                        io.blockdesigner.plugin.ui.Controls.button("Save CSV…", "Save", null), io.blockdesigner.plugin.ui.Controls.spacer(),
                        io.blockdesigner.plugin.ui.Controls.danger("Reset…", "Reset", null)));
    }

    /** Lays a node out as the right-hand panel would at this width (as tall as its content) and snapshots it. */
    private static WritableImage inPanel(javafx.scene.Node node, double width, boolean dark) {
        StackPane host = new StackPane(node);
        host.getStyleClass().addAll("app-root", "plugin-side", dark ? "dark" : "light");
        host.getStylesheets().addAll(PluginUiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm(),
                io.blockdesigner.plugin.ui.Theme.STYLESHEET);
        javafx.scene.Scene measure = new javafx.scene.Scene(host, width, 1600);
        host.applyCss();
        host.layout();
        // A panel that scrolls (a PanelScaffold) is shown as tall as its content, so the whole page is in the picture.
        double h = Math.max(320, Math.min(1600, Math.max(host.prefHeight(width), contentHeight(node, width))));
        measure.setRoot(new javafx.scene.Group());
        new javafx.scene.Scene(host, width, h);
        host.applyCss();
        host.layout();
        return host.snapshot(null, null);
    }

    private static double contentHeight(javafx.scene.Node node, double width) {
        if (node instanceof io.blockdesigner.plugin.ui.PanelScaffold s) {
            double h = 0;
            for (javafx.scene.Node c : s.getChildren()) {
                if (c instanceof javafx.scene.control.ScrollPane sp && sp.getContent() instanceof javafx.scene.layout.Region r) h += r.prefHeight(width);
                else if (c instanceof javafx.scene.layout.Region r && c.isManaged()) h += r.prefHeight(width);
            }
            return h;
        }
        if (node instanceof javafx.scene.Parent p) {
            double h = 0;
            for (javafx.scene.Node c : p.getChildrenUnmodifiable()) h = Math.max(h, contentHeight(c, width));
            return h + 60;
        }
        return 0;
    }

    private static PluginHomeTab.Actions homeActions() {
        return new PluginHomeTab.Actions() {
            public void runAction(PluginManager.Action a) {
            }

            public void pickTool(PluginManager.Tool t) {
            }

            public void openTransform(PluginManager.Transform t) {
            }

            public void managePlugins() {
            }

            public void disable(PluginManager.Plugin p) {
            }

            public void openSettings(PluginManager.Plugin p) {
            }

            public String toolKey(PluginManager.Tool t) {
                return "Shift+P";
            }

            public boolean isShowing(javafx.scene.control.Tab tab) {
                return true;
            }

            public void reveal(javafx.scene.control.Tab tab) {
            }
        };
    }

    private static void snapshotDialog(javafx.scene.control.Dialog<?> d, Path out, boolean close) throws Exception {
        d.show();
        d.getDialogPane().applyCss();
        d.getDialogPane().layout();
        save(d.getDialogPane().snapshot(null, null), out);
        if (close) {
            d.setResult(null);
            d.close();
        }
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
