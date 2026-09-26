package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Renders the export window, data pack window, plugins window and the format icons to PNGs under build/ui-snapshots
 * for a visual check. Opens real windows, so it only runs with the environment variable UI_SNAPSHOTS=1.
 */
class UiSnapshotsIT {

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
        Path dir = Path.of("build", "ui-snapshots");
        Files.createDirectories(dir);
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                for (boolean dark : new boolean[]{true, false}) render(dir, dark);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(60, TimeUnit.SECONDS);

        CompletableFuture<Void> themed = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                for (AppTheme t : AppTheme.values()) {
                    for (boolean dark : new boolean[]{true, false}) {
                        javafx.application.Application.setUserAgentStylesheet(t.userAgentStylesheet(dark));
                        Settings settings = new Settings();
                        Workspace ws = new Workspace(settings);
                        ws.themeProperty().set(t.name());
                        ws.darkProperty().set(dark);
                        String n = t.name().toLowerCase() + (dark ? "-dark" : "-light");
                        SettingsDialog sd = new SettingsDialog(null, ws, new Keybinds(settings), () -> {
                        }, () -> {
                        }, () -> {
                        }, () -> {
                        });
                        // General opens first; the theme cards are on Appearance, and Keybinds is checked once.
                        if (t == AppTheme.values()[0]) {
                            settings.keybinds.put("SHUFFLE", "Alt+X|R");
                            navTo(sd, "General");
                            snapshotDialog(sd, dir.resolve("settings-general" + (dark ? "-dark" : "-light") + ".png"));
                            navTo(sd, "Keybinds");
                            snapshotDialog(sd, dir.resolve("settings-keybinds" + (dark ? "-dark" : "-light") + ".png"));
                            // Held keys (flying) on their own, via the search.
                            sd.getDialogPane().lookupAll(".search-field").stream()
                                    .filter(f -> f instanceof javafx.scene.control.TextField tf && tf.getPromptText() != null && tf.getPromptText().startsWith("Search actions"))
                                    .findFirst().ifPresent(f -> ((javafx.scene.control.TextField) f).setText("fly"));
                            snapshotDialog(sd, dir.resolve("settings-keybinds-fly" + (dark ? "-dark" : "-light") + ".png"));
                        }
                        navTo(sd, "Appearance");
                        snapshotDialog(sd, dir.resolve("settings-" + n + ".png"));
                        Structure s = new Structure();
                        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) s.set(x, 0, z, BlockState.of("stone"));
                        ws.scene().add(new Layer("House", s));
                        snapshotDialog(new ExportDialog(null, ws, null, List.of(), null, "litematica"), dir.resolve("theme-export-" + n + ".png"));
                    }
                }
                themed.complete(null);
            } catch (Throwable t) {
                themed.completeExceptionally(t);
            }
        });
        themed.get(120, TimeUnit.SECONDS);
    }

    private static void render(Path dir, boolean dark) throws Exception {
        String suffix = dark ? "-dark" : "-light";
        javafx.application.Application.setUserAgentStylesheet(dark ? new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
                : new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        Settings settings = new Settings();
        settings.darkTheme = dark;
        Workspace ws = new Workspace(settings);
        ws.projectNameProperty().set("Harbour Town");
        Structure house = new Structure();
        for (int x = 0; x < 7; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 6; z++) {
            if (y == 0 || x == 0 || x == 6 || z == 0 || z == 5) house.set(x, y, z, BlockState.of(y == 0 ? "oak_planks" : "cobblestone"));
        }
        house.set(2, 1, 2, BlockState.of("chest"));
        house.set(3, 1, 2, BlockState.of("chest"));
        house.set(4, 1, 3, BlockState.of("barrel"));
        Layer a = new Layer("Fisher's house", house);
        a.setSource("litematica");
        Structure road = new Structure();
        for (int x = 0; x < 20; x++) for (int z = 0; z < 3; z++) road.set(x, 0, z, BlockState.of("dirt_path"));
        Layer b = new Layer("Main street", road);
        b.setSource("sponge");
        Layer c = new Layer("Well", house.copy());
        c.setSource("vanilla");
        for (Layer l : List.of(a, b, c)) ws.scene().add(l);

        ExportDialog export = new ExportDialog(null, ws, null, List.of(), null, "litematica");
        snapshotDialog(export, dir.resolve("export" + suffix + ".png"));
        ExportDialog we = new ExportDialog(null, ws, null, List.of(), null, "sponge");
        snapshotDialog(we, dir.resolve("export-worldedit" + suffix + ".png"));
        ExportDialog dp = new ExportDialog(null, ws, null, List.of(), null, "datapack");
        snapshotDialog(dp, dir.resolve("export-datapack-card" + suffix + ".png"));

        DatapackDialog pack = new DatapackDialog(null, ws, null);
        // Show the village layout.
        pack.getDialogPane().lookupAll(".toggle-button").stream()
                .filter(n -> n instanceof javafx.scene.control.ToggleButton tb && tb.getText() != null && tb.getText().startsWith("Village"))
                .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).setSelected(true));
        snapshotDialog(pack, dir.resolve("datapack-village" + suffix + ".png"));
        DatapackDialog full = new DatapackDialog(null, ws, null);
        full.show();
        var content = ((javafx.scene.control.ScrollPane) full.getDialogPane().getContent()).getContent();
        content.applyCss();
        save(content.snapshot(null, null), dir.resolve("datapack-full" + suffix + ".png"));
        full.setResult(null);
        full.close();
        var table = new io.blockdesigner.worldgen.LootTables.CustomTable("Pirate treasure", 2, 5, List.of(
                new io.blockdesigner.worldgen.LootTables.Item("minecraft:gold_ingot", 10, 1, 4, false),
                new io.blockdesigner.worldgen.LootTables.Item("minecraft:emerald", 5, 1, 2, false),
                new io.blockdesigner.worldgen.LootTables.Item("minecraft:diamond_sword", 1, 1, 1, true)));
        snapshotDialog(new LootTableEditor(null, ws, table, List.of()), dir.resolve("loot-editor" + suffix + ".png"));

        // Plugins window with the example plugin installed.
        Path pluginDir = Files.createTempDirectory("bd-plugins");
        Path libs = Path.of(System.getProperty("blockdesigner.examplePluginDir", "../examples/hello-plugin/build/libs"));
        try (var ds = Files.newDirectoryStream(libs, "hello-plugin*.jar")) {
            for (Path jar : ds) Files.copy(jar, pluginDir.resolve("hello.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var host = new io.blockdesigner.app.plugins.PluginHost() {
            public io.blockdesigner.core.model.Scene scene() {
                return ws.scene();
            }

            public io.blockdesigner.core.edit.SceneEditor editor() {
                return ws.editor();
            }

            public java.util.Optional<Layer> activeLayer() {
                return ws.scene().active();
            }

            public List<Layer> selectedLayers() {
                return List.of();
            }

            public io.blockdesigner.core.version.McVersion targetVersion() {
                return io.blockdesigner.core.version.McVersion.latestKnown();
            }

            public void editWorld(String label, java.util.function.Consumer<io.blockdesigner.core.worldedit.WorldEdit.World> edit) {
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
        var pm = new io.blockdesigner.app.plugins.PluginManager(pluginDir, host, new java.util.HashSet<>());
        pm.loadAll();
        snapshotDialog(new PluginsDialog(null, pm, dark), dir.resolve("plugins" + suffix + ".png"));
        snapshotDialog(new ExportDialog(null, ws, null, pm.exporters(), null, "plugin:hello/bom"), dir.resolve("export-plugin" + suffix + ".png"));
        pm.shutdown();

        // The shape wheel over a dark backdrop, pointing at "Wall".
        ShapeRadial wheel = new ShapeRadial(x -> {
        });
        javafx.scene.layout.StackPane stage = new javafx.scene.layout.StackPane(wheel);
        stage.setStyle("-fx-background-color: #2a3348;");
        stage.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        stage.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        stage.setPrefSize(420, 420);
        new javafx.scene.Scene(stage);
        stage.applyCss();
        stage.layout();
        wheel.open(210, 210, io.blockdesigner.core.place.ShapeTool.Shape.BOX);
        wheel.pointAt(210 + 30, 210 - 100);
        stage.applyCss();
        stage.layout();
        save(stage.snapshot(null, null), dir.resolve("shape-wheel" + suffix + ".png"));

        // Symmetry panel content with X + Z mirrors and 4 radial copies.
        settings.symOn = true;
        settings.symZ = true;
        settings.symRadial = 4;
        SymmetryPopup sp = new SymmetryPopup(settings, () -> {
        }, () -> {
        });
        javafx.scene.layout.StackPane sbox = new javafx.scene.layout.StackPane(sp.getContent().getFirst());
        sbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        sbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        new javafx.scene.Scene(sbox);
        sbox.applyCss();
        sbox.layout();
        save(sbox.snapshot(null, null), dir.resolve("symmetry" + suffix + ".png"));

        // Icon sheet: every kind at menu, list and card sizes.
        VBox rows = new VBox(10);
        rows.setPadding(new Insets(12));
        rows.setStyle("-fx-background-color: " + (dark ? "#0d1117" : "#ffffff") + ";");
        for (double size : new double[]{16, 24, 48, 96}) {
            HBox row = new HBox(14);
            for (FormatIcons.Kind k : FormatIcons.Kind.values()) {
                if (k == FormatIcons.Kind.PROJECT) continue;
                row.getChildren().add(size >= 48 ? FormatIcons.tile(k, size) : FormatIcons.icon(k, size));
            }
            rows.getChildren().add(row);
        }
        save(snapshotNode(rows), dir.resolve("icons" + suffix + ".png"));

        // The palette's Mobs tab, holding a pink sheep, and the HUD over a villager (faces from the game when installed).
        var jars = new io.blockdesigner.assets.McInstallLocator().scan().jars();
        if (!jars.isEmpty() && ws.assets() == null) {
            var jar = jars.stream().filter(j -> j.version().id().startsWith("1.21.1")).findFirst().orElse(jars.getFirst());
            ws.assetsProperty().set(io.blockdesigner.assets.BlockAssets.open(jar.jar(), List.of(), List.of(), null));
        }
        BlockPalette palette = new BlockPalette(ws);
        javafx.scene.layout.StackPane pbox = new javafx.scene.layout.StackPane(palette);
        pbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        pbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        pbox.setPrefSize(330, 620);
        new javafx.scene.Scene(pbox);
        pbox.applyCss();
        pbox.layout();
        // The blocks tab first, its creative-style category tabs showing block icons.
        save(pbox.snapshot(null, null), dir.resolve("palette-blocks" + suffix + ".png"));
        pbox.lookupAll(".palette-tab").stream()
                .filter(n -> n instanceof javafx.scene.control.ToggleButton tb && "Mobs".equals(tb.getText()))
                .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).fire());
        ws.holdEntity("sheep");
        var sheep = ws.heldEntityProperty().get().copy();
        sheep.putByte("Color", 6);
        ws.heldEntityProperty().set(sheep);
        pbox.applyCss();
        pbox.layout();
        save(pbox.snapshot(null, null), dir.resolve("palette-mobs" + suffix + ".png"));

        // The tool dock with the brush selected, then the eraser.
        for (var tool : new Workspace.ToolKind[]{Workspace.ToolKind.BUILD, Workspace.ToolKind.BRUSH, Workspace.ToolKind.ERASER}) {
            javafx.scene.layout.HBox strip = new javafx.scene.layout.HBox(8);
            strip.setStyle("-fx-padding: 10;");
            javafx.scene.layout.StackPane dbox = new javafx.scene.layout.StackPane(strip);
            dbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
            dbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
            new javafx.scene.Scene(dbox);
            ws.toolProperty().set(tool);
            java.util.function.DoubleFunction<javafx.scene.Node> glyph = switch (tool) {
                case BUILD -> ToolIcons::build;
                case BRUSH -> ToolIcons::brush;
                default -> ToolIcons::eraser;
            };
            javafx.scene.control.ToggleButton on = new javafx.scene.control.ToggleButton(null, glyph.apply(17));
            on.getStyleClass().addAll("flat", "tool-button");
            javafx.scene.control.ToggleButton off = new javafx.scene.control.ToggleButton(null, glyph.apply(17));
            off.getStyleClass().addAll("flat", "tool-button");
            on.setSelected(true);
            javafx.scene.control.ToggleButton big = new javafx.scene.control.ToggleButton(null, glyph.apply(64));
            big.getStyleClass().addAll("flat");
            javafx.scene.control.ToggleButton ref = new javafx.scene.control.ToggleButton(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.MOVE));
            ref.getStyleClass().addAll("flat", "tool-button");
            // A Feather icon beside them, to compare sizes.
            strip.getChildren().addAll(on, off, ref, big);
            dbox.applyCss();
            dbox.layout();
            save(dbox.snapshot(null, null), dir.resolve("tool-" + tool.name().toLowerCase() + suffix + ".png"));
        }

        BlockInfoHud hud = new BlockInfoHud();
        javafx.scene.layout.StackPane hbox = new javafx.scene.layout.StackPane(hud);
        hbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        hbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        hbox.setStyle("-fx-background-color: #2a3348; -fx-padding: 12;");
        new javafx.scene.Scene(hbox);
        var villager = io.blockdesigner.core.model.EntityTypes.create("villager", 3.5, 64, -2.5, 180);
        villager.nbt().put("VillagerData", new io.blockdesigner.core.nbt.CompoundTag().putString("type", "minecraft:desert")
                .putString("profession", "minecraft:farmer").putInt("level", 1));
        hud.showEntity(ws.assets(), villager, a);
        hbox.applyCss();
        hbox.layout();
        save(hbox.snapshot(null, null), dir.resolve("hud-entity" + suffix + ".png"));

        // The view cube at its default angle.
        ViewCube cube = new ViewCube(v -> {
        }, (x, y) -> {
        }, () -> {
        });
        cube.update(new io.blockdesigner.render.Camera());
        javafx.scene.layout.StackPane cbox = new javafx.scene.layout.StackPane(cube);
        cbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        cbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        cbox.setStyle("-fx-background-color: #6d8fb8; -fx-padding: 10;");
        new javafx.scene.Scene(cbox);
        cbox.applyCss();
        cbox.layout();
        save(cbox.snapshot(null, null), dir.resolve("view-cube" + suffix + ".png"));

        // Key hints for Build mode, over a viewport-coloured backdrop.
        KeyHints hints = new KeyHints();
        hints.show(List.of(KeyHints.Hint.of("Break", "LMB"), KeyHints.Hint.of("Place", "RMB"), KeyHints.Hint.of("Pick block", "MMB"),
                KeyHints.Hint.of("Shapes", "Alt"), KeyHints.Hint.of("Select or replace by type", "Alt", "T"),
                KeyHints.Hint.of("Up / down", "Space", "Shift"), KeyHints.Hint.of("All shortcuts", "F1")));
        javafx.scene.layout.StackPane kbox = new javafx.scene.layout.StackPane(hints);
        kbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        kbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        kbox.setStyle("-fx-background-color: #6d8fb8; -fx-padding: 14;");
        new javafx.scene.Scene(kbox);
        kbox.applyCss();
        kbox.layout();
        save(kbox.snapshot(null, null), dir.resolve("key-hints" + suffix + ".png"));

        // Select / replace by type, switched to Replace with oak stairs ticked.
        java.util.Map<String, Long> counts = java.util.Map.of("minecraft:oak_stairs", 120L, "minecraft:stone_bricks", 2400L, "minecraft:oak_planks", 640L);
        SelectByTypePanel byType = new SelectByTypePanel(ws.assets(), SelectByTypePanel.Scope.VISIBLE, false, false,
                BlockState.of("minecraft:oak_stairs"), BlockState.of("minecraft:stone_brick_stairs"), q -> counts, o -> {
        }, r -> {
        }, () -> {
        });
        javafx.scene.layout.StackPane tbox = new javafx.scene.layout.StackPane(byType);
        tbox.getStyleClass().addAll("app-root", dark ? "dark" : "light");
        tbox.getStylesheets().add(UiSnapshotsIT.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        tbox.setStyle("-fx-background-color: -color-bg-default; -fx-padding: 12;");
        new javafx.scene.Scene(tbox, 530, 720);
        tbox.applyCss();
        tbox.layout();
        save(tbox.snapshot(null, null), dir.resolve("by-type-select" + suffix + ".png"));
        tbox.lookupAll(".right-pill").stream().filter(n -> n instanceof javafx.scene.control.ToggleButton tb && "Replace".equals(tb.getText()))
                .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).fire());
        tbox.applyCss();
        tbox.layout();
        save(tbox.snapshot(null, null), dir.resolve("by-type-replace" + suffix + ".png"));
    }

    /** Clicks a Settings page in the side bar. */
    private static void navTo(SettingsDialog d, String page) {
        d.getDialogPane().applyCss();
        d.getDialogPane().lookupAll(".settings-nav-item").stream()
                .filter(n -> n instanceof javafx.scene.control.ToggleButton tb && page.equals(tb.getText()))
                .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).fire());
    }

    private static void snapshotDialog(Dialog<?> d, Path out) throws Exception {
        d.show();
        d.getDialogPane().applyCss();
        d.getDialogPane().layout();
        save(d.getDialogPane().snapshot(null, null), out);
        d.setResult(null);
        d.close();
    }

    private static WritableImage snapshotNode(Node n) {
        javafx.scene.Scene s = new javafx.scene.Scene(new javafx.scene.Group(n));
        n.applyCss();
        return n.snapshot(null, null);
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
