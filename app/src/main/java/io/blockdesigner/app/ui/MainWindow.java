package io.blockdesigner.app.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.Workspace.ToolKind;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.project.ProjectFile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** The main editor window: top bar, layers + palette on the left, viewport in the centre, assistant on the right. */
public final class MainWindow {
    private static final int[] LAYER_COLORS = {0x7C9CFF, 0x46C46E, 0xFFB454, 0xF2668B, 0x3DD6D0, 0xB18CFF, 0xE8D35A, 0xFF7A45};

    private final Stage stage;
    private final Workspace ws;
    private final BorderPane root = new BorderPane();
    private final ViewportPane viewport;
    private final StackPane center;
    private final VBox loadingOverlay = new VBox(12);
    private final Label loadingText = new Label("Looking for Minecraft…");
    private final Label assetBadge = new Label("No assets");
    private final Label statusLeft = new Label();
    private final Label statusRight = new Label();
    private McInstallLocator.Result scan;
    private final BorderPane rightPanel = new BorderPane();
    private final javafx.scene.control.TabPane sideTabs = new javafx.scene.control.TabPane();
    private final javafx.scene.control.Tab assistantTab = new javafx.scene.control.Tab("Assistant");
    private final ToggleButton assistantToggle = new ToggleButton(null, new FontIcon(Feather.MESSAGE_SQUARE));
    private final BorderPane centerColumn = new BorderPane();

    public MainWindow(Stage stage, Workspace ws) {
        this.stage = stage;
        this.ws = ws;
        this.viewport = new ViewportPane(ws);

        applyTheme();
        ws.darkProperty().addListener((o, a, b) -> applyTheme());

        // Left: layers over palette
        LayersPanel layers = new LayersPanel(ws, new LayersPanel.Actions(this::importDialog, l -> export(List.of(l), null), viewport::frameLayer));
        BlockPalette palette = new BlockPalette(ws);
        SplitPane left = new SplitPane(layers, palette);
        left.setOrientation(javafx.geometry.Orientation.VERTICAL);
        left.setDividerPositions(0.36);
        left.getStyleClass().add("left-split");
        left.setPrefWidth(330);
        left.setMinWidth(260);

        // Centre: viewport with floating dock and loading overlay
        ToolDock dock = new ToolDock(ws, viewport);
        StackPane.setAlignment(dock, Pos.CENTER_LEFT);
        StackPane.setMargin(dock, new Insets(0, 0, 0, 12));
        loadingOverlay.getStyleClass().add("loading-overlay");
        loadingOverlay.setAlignment(Pos.CENTER);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(44, 44);
        loadingOverlay.getChildren().addAll(spinner, loadingText);
        center = new StackPane(viewport, dock, loadingOverlay);
        center.getStyleClass().add("viewport-host");

        rightPanel.getStyleClass().add("side-panel");
        rightPanel.setPrefWidth(360);
        rightPanel.setMinWidth(280);

        centerColumn.setCenter(center);
        SplitPane main = new SplitPane(left, centerColumn, rightPanel);
        main.setDividerPositions(0.2, 0.76);
        SplitPane.setResizableWithParent(left, false);
        SplitPane.setResizableWithParent(rightPanel, false);

        root.setTop(topBar());
        root.setCenter(main);
        root.setBottom(statusBar());
        root.getStyleClass().add("app-root");

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 1560, 940);
        scene.getStylesheets().add(MainWindow.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        installShortcuts(scene);
        installDragAndDrop(scene);
        stage.setScene(scene);
        stage.titleProperty().bind(ws.projectNameProperty().concat(" — BlockDesigner"));
        stage.setMinWidth(1000);
        stage.setMinHeight(640);

        ws.scene().addListener(new Scene.Listener() {
            @Override
            public void layerAdded(Layer layer, int index) {
                if (layer.color() == 0x7C9CFF && ws.scene().layers().size() > 1) {
                    layer.setColor(LAYER_COLORS[(ws.scene().layers().size() - 1) % LAYER_COLORS.length]);
                }
                updateStatus();
            }

            @Override
            public void layerRemoved(Layer layer) {
                updateStatus();
            }

            @Override
            public void blocksChanged(Layer layer, io.blockdesigner.core.model.Box localBox) {
                updateStatus();
            }
        });
        ws.editor().undoStack().addListener(this::updateStatus);
        stage.setOnCloseRequest(e -> {
            viewport.detach();
            ws.settings().save();
        });
    }

    /** Right-hand panel: the assistant (set by the app once the AI module is wired) plus the Resource Tracker tab. */
    public void setRightPanel(javafx.scene.Node node) {
        // The Assistant tab can be closed (the top-bar chat button reopens it); the Resource Tracker stays.
        assistantTab.setContent(node);
        assistantTab.setGraphic(new FontIcon(Feather.MESSAGE_SQUARE));
        assistantTab.setOnClosed(e -> {
            ws.settings().showAssistant = false;
            assistantToggle.setSelected(false);
        });
        javafx.scene.control.Tab resources = new javafx.scene.control.Tab("Resource Tracker", new ResourceTrackerPanel());
        resources.setGraphic(new FontIcon(Feather.PACKAGE));
        resources.setClosable(false);
        sideTabs.getTabs().setAll(resources);
        sideTabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.SELECTED_TAB);
        sideTabs.getStyleClass().add("side-tabs");
        rightPanel.setCenter(sideTabs);
        showAssistant(ws.settings().showAssistant);
    }

    private void showAssistant(boolean show) {
        ws.settings().showAssistant = show;
        assistantToggle.setSelected(show);
        if (show) {
            if (!sideTabs.getTabs().contains(assistantTab)) sideTabs.getTabs().addFirst(assistantTab);
            sideTabs.getSelectionModel().select(assistantTab);
        } else {
            sideTabs.getTabs().remove(assistantTab);
        }
    }

    /** Strip under the viewport (the iteration timeline). */
    public void setCenterBottom(javafx.scene.Node node) {
        centerColumn.setBottom(node);
    }

    public ViewportPane viewport() {
        return viewport;
    }

    public Stage stage() {
        return stage;
    }

    public void show() {
        stage.show();
        startAssetLoading(false);
    }

    private void applyTheme() {
        boolean dark = ws.darkProperty().get();
        Application.setUserAgentStylesheet(dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
        root.getStyleClass().removeAll("dark", "light");
        root.getStyleClass().add(dark ? "dark" : "light");
        ws.settings().darkTheme = dark;
    }

    // ---- top & status bars ------------------------------------------------------------------------------------

    private HBox topBar() {
        Label logo = new Label("BlockDesigner", new FontIcon(Feather.BOX));
        logo.getStyleClass().add("app-logo");

        TextField name = new TextField();
        name.getStyleClass().add("project-name");
        name.textProperty().bindBidirectional(ws.projectNameProperty());
        name.setPrefColumnCount(14);

        Button open = LayersPanel.iconButton(Feather.FOLDER, "Open project or schematic (Ctrl+O)", this::openDialog);
        Button save = LayersPanel.iconButton(Feather.SAVE, "Save project (Ctrl+S)", () -> save(false));
        Button imp = new Button("Import", new FontIcon(Feather.DOWNLOAD));
        imp.getStyleClass().add("flat");
        imp.setOnAction(e -> importDialog());

        MenuButton export = new MenuButton("Export", new FontIcon(Feather.UPLOAD));
        export.getStyleClass().add("accent");
        MenuItem nbt = new MenuItem("Structure / Create (.nbt)…");
        nbt.setOnAction(e -> exportDialog(Schematics.VANILLA));
        MenuItem lit = new MenuItem("Litematica (.litematic)…");
        lit.setOnAction(e -> exportDialog(Schematics.LITEMATICA));
        MenuItem schem = new MenuItem("WorldEdit (.schem)…");
        schem.setOnAction(e -> exportDialog(Schematics.SPONGE));
        MenuItem datapack = new MenuItem("Worldgen data pack…", new FontIcon(Feather.GLOBE));
        datapack.setOnAction(e -> exportDatapack());
        export.getItems().addAll(nbt, lit, schem, new SeparatorMenuItem(), datapack);

        Button undo = LayersPanel.iconButton(Feather.CORNER_UP_LEFT, "Undo (Ctrl+Z)", () -> ws.editor().undoStack().undo());
        Button redo = LayersPanel.iconButton(Feather.CORNER_UP_RIGHT, "Redo (Ctrl+Y)", () -> ws.editor().undoStack().redo());
        ws.editor().undoStack().addListener(() -> {
            undo.setDisable(!ws.editor().undoStack().canUndo());
            redo.setDisable(!ws.editor().undoStack().canRedo());
            undo.setTooltip(new Tooltip(ws.editor().undoStack().undoLabel().map(s -> "Undo " + s).orElse("Undo") + " (Ctrl+Z)"));
            redo.setTooltip(new Tooltip(ws.editor().undoStack().redoLabel().map(s -> "Redo " + s).orElse("Redo") + " (Ctrl+Y)"));
        });
        undo.setDisable(true);
        redo.setDisable(true);

        assistantToggle.getStyleClass().addAll("flat", "icon-toggle");
        assistantToggle.setTooltip(new Tooltip("Show / hide the AI assistant tab"));
        assistantToggle.setOnAction(e -> showAssistant(assistantToggle.isSelected()));
        Button theme = LayersPanel.iconButton(Feather.MOON, "Light / dark", () -> ws.darkProperty().set(!ws.darkProperty().get()));
        Button assets = LayersPanel.iconButton(Feather.SETTINGS, "Minecraft assets & mods", () -> startAssetLoading(true));
        assetBadge.getStyleClass().add("badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, logo, name, open, save, spacer, undo, redo, assistantToggle, theme, assetBadge, assets, imp, export);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private HBox statusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLeft.textProperty().bind(ws.statusProperty());
        HBox bar = new HBox(12, statusLeft, spacer, statusRight);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void updateStatus() {
        long blocks = 0;
        for (Layer l : ws.scene().layers()) blocks += l.structure().blockCount();
        String size = ws.scene().worldBounds().map(b -> b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ()).orElse("—");
        statusRight.setText(String.format("%d layers · %,d blocks · %s · %s", ws.scene().layers().size(), blocks, size, viewport.glInfo()));
    }

    // ---- assets -------------------------------------------------------------------------------------------

    private void startAssetLoading(boolean askAgain) {
        loadingOverlay.setVisible(true);
        loadingText.setText("Looking for Minecraft installs…");
        CompletableFuture.supplyAsync(() -> new McInstallLocator().scan()).thenAcceptAsync(result -> {
            scan = result;
            var saved = askAgain ? java.util.Optional.<AssetSetupDialog.Choice>empty() : AssetSetupDialog.fromSettings(ws.settings(), result);
            AssetSetupDialog.Choice choice = saved.orElseGet(() -> {
                if (result.jars().isEmpty() && !askAgain) {
                    loadingText.setText("No Minecraft install found — click the gear to pick a client jar.");
                }
                return new AssetSetupDialog(stage, result, ws.settings()).showAndWait().orElse(null);
            });
            if (choice == null) {
                if (ws.assets() == null) loadingText.setText("No assets loaded — click the gear icon to choose a Minecraft version.");
                else loadingOverlay.setVisible(false);
                return;
            }
            ws.settings().gameJar = choice.gameJar().toString();
            ws.settings().instanceName = choice.instanceName();
            ws.settings().resourcePacks = choice.resourcePacks().stream().map(Path::toString).toList();
            ws.settings().save();
            loadAssets(choice);
        }, Platform::runLater).exceptionally(this::fail);
    }

    private void loadAssets(AssetSetupDialog.Choice c) {
        loadingOverlay.setVisible(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return BlockAssets.open(c.gameJar(), c.mods(), c.resourcePacks(), msg -> Platform.runLater(() -> loadingText.setText(msg)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(assets -> {
            BlockAssets old = ws.assets();
            ws.assetsProperty().set(assets);
            if (ws.targetVersionProperty().get() == null || old == null) ws.targetVersionProperty().set(assets.version());
            String mods = c.mods().isEmpty() ? "" : " + " + (c.instanceName() != null ? c.instanceName() : c.mods().size() + " mods");
            assetBadge.setText(assets.version().id() + mods);
            assetBadge.setTooltip(new Tooltip(assets.registry().size() + " blocks · " + c.gameJar()));
            viewport.attach(assets).thenRunAsync(() -> {
                loadingOverlay.setVisible(false);
                updateStatus();
                ws.statusProperty().set("Ready · " + assets.registry().size() + " blocks from " + assets.version().id() + mods);
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception ignored) {
                        // old jar handles
                    }
                }
            }, Platform::runLater).exceptionally(this::fail);
        }, Platform::runLater).exceptionally(this::fail);
    }

    private Void fail(Throwable t) {
        Platform.runLater(() -> {
            loadingOverlay.setVisible(false);
            Throwable cause = t;
            while (cause.getCause() != null) cause = cause.getCause();
            error("Something went wrong", String.valueOf(cause.getMessage()));
            cause.printStackTrace();
        });
        return null;
    }

    // ---- import / open / save -------------------------------------------------------------------------------

    private FileChooser.ExtensionFilter schematicFilter() {
        return new FileChooser.ExtensionFilter("Schematics (*.litematic, *.schem, *.nbt)", "*.litematic", "*.schem", "*.nbt");
    }

    private File initialDir() {
        if (scan != null) {
            for (var i : scan.instances()) if (Files.isDirectory(i.schematicsDir())) return i.schematicsDir().toFile();
        }
        return null;
    }

    public void importDialog() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import schematics");
        fc.getExtensionFilters().add(schematicFilter());
        File dir = initialDir();
        if (dir != null) fc.setInitialDirectory(dir);
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null) files.forEach(f -> importFile(f.toPath()));
    }

    /** Reads a schematic and starts placement; multi-region Litematica files become one layer per region. */
    public void importFile(Path file) {
        ws.statusProperty().set("Reading " + file.getFileName() + "…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return Schematics.read(file);
            } catch (Exception e) {
                throw new RuntimeException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }).thenAcceptAsync(sf -> {
            List<Layer> layers = new ArrayList<>();
            for (SchematicFile.Region r : sf.regions()) {
                String name = sf.regions().size() == 1 ? sf.name() : sf.name() + " · " + r.name();
                Structure s = r.structure();
                s.metadata().name = name;
                s.metadata().author = sf.author();
                Layer l = new Layer(name, s);
                l.setOffset(r.position());
                layers.add(l);
            }
            ws.settings().addRecent(file);
            ws.statusProperty().set(String.format("Imported %s — %,d blocks (DataVersion %d)", file.getFileName(), sf.totalBlocks(), sf.dataVersion()));
            if (ws.projectNameProperty().get().equals("Untitled") && ws.scene().layers().isEmpty()) ws.projectNameProperty().set(sf.name());
            viewport.beginPlacement(layers, null);
        }, Platform::runLater).exceptionally(this::fail);
    }

    private void openDialog() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("BlockDesigner project (*.bdproj)", "*.bdproj"), schematicFilter());
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        if (f.getName().endsWith("." + ProjectFile.EXTENSION)) openProject(f.toPath());
        else importFile(f.toPath());
    }

    public void openProject(Path file) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return ProjectFile.load(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(c -> {
            for (Layer l : List.copyOf(ws.scene().layers())) ws.scene().remove(l);
            ws.editor().undoStack().clear();
            for (Layer l : c.layers()) ws.scene().add(l);
            if (c.activeLayerId() != null) ws.scene().find(c.activeLayerId()).ifPresent(ws.scene()::setActive);
            ws.projectNameProperty().set(c.name());
            ws.targetVersionProperty().set(c.targetVersion());
            ws.projectFileProperty().set(file);
            ws.settings().addRecent(file);
            projectExtrasLoaded(c.extras());
            viewport.frameAll();
            ws.statusProperty().set("Opened " + file.getFileName());
        }, Platform::runLater).exceptionally(this::fail);
    }

    /** Hook for other modules (chat history, snapshots) to restore their data. */
    private java.util.function.Consumer<Map<String, byte[]>> extrasLoader = m -> {
    };
    private java.util.function.Supplier<Map<String, byte[]>> extrasSaver = Map::of;

    public void setProjectExtras(java.util.function.Supplier<Map<String, byte[]>> saver, java.util.function.Consumer<Map<String, byte[]>> loader) {
        this.extrasSaver = saver;
        this.extrasLoader = loader;
    }

    private void projectExtrasLoaded(Map<String, byte[]> extras) {
        extrasLoader.accept(extras);
    }

    public void save(boolean saveAs) {
        Path file = ws.projectFileProperty().get();
        if (file == null || saveAs) {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save project");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("BlockDesigner project (*.bdproj)", "*.bdproj"));
            fc.setInitialFileName(safeName(ws.projectNameProperty().get()) + ".bdproj");
            File f = fc.showSaveDialog(stage);
            if (f == null) return;
            file = f.toPath();
        }
        Path target = file;
        var contents = new ProjectFile.Contents(ws.projectNameProperty().get(), ws.targetVersionProperty().get(),
                List.copyOf(ws.scene().layers().stream().filter(l -> !(viewport.isPlacing() && l.ghost())).toList()),
                ws.activeLayerProperty().get() == null ? null : ws.activeLayerProperty().get().id(), extrasSaver.get());
        try {
            ProjectFile.save(contents, target);
            ws.projectFileProperty().set(target);
            ws.settings().addRecent(target);
            ws.statusProperty().set("Saved " + target.getFileName());
            viewport.showToast("Saved");
        } catch (Exception e) {
            error("Save failed", e.getMessage());
        }
    }

    // ---- export -------------------------------------------------------------------------------------------

    private void exportDialog(SchematicFormat preselect) {
        new ExportDialog(stage, ws.targetVersionProperty().get(), preselect).showAndWait().ifPresent(opts -> {
            List<Layer> layers = switch (opts.source()) {
                case ACTIVE -> ws.activeLayerProperty().get() == null ? List.of() : List.of(ws.activeLayerProperty().get());
                case SELECTED -> ws.scene().layers().stream().filter(ws.selectedLayers()::contains).toList();
                case VISIBLE, EACH -> ws.scene().layers().stream().filter(Layer::visible).toList();
            };
            if (layers.isEmpty()) {
                error("Nothing to export", "There are no layers matching that choice.");
                return;
            }
            export(layers, opts);
        });
    }

    private void export(List<Layer> layers, ExportDialog.Options opts) {
        if (opts == null) {
            new ExportDialog(stage, ws.targetVersionProperty().get(), Schematics.LITEMATICA).showAndWait()
                    .ifPresent(o -> export(layers, new ExportDialog.Options(o.format(), o.version(), ExportDialog.Source.VISIBLE, o.includeAir(), o.spongeVersion())));
            return;
        }
        WriteOptions wo = new WriteOptions(opts.version(), opts.includeAir(), opts.spongeVersion());
        SchematicFormat format = opts.format();
        String ext = format.extensions().getFirst();
        String author = ws.settings().author;
        try {
            if (opts.source() == ExportDialog.Source.EACH && format != Schematics.LITEMATICA) {
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle("Export each layer into…");
                File dir = dc.showDialog(stage);
                if (dir == null) return;
                for (Layer l : layers) {
                    Structure flat = Scene.flatten(List.of(l));
                    SchematicFile sf = new SchematicFile(l.name(), author, "", opts.version().dataVersion(),
                            List.of(new SchematicFile.Region(l.name(), flat, BlockPos.ORIGIN)));
                    Schematics.write(sf, format, wo, dir.toPath().resolve(safeName(l.name()) + "." + ext));
                }
                ws.statusProperty().set("Exported " + layers.size() + " files to " + dir);
                return;
            }
            FileChooser fc = new FileChooser();
            fc.setTitle("Export " + format.displayName());
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.displayName(), "*." + ext));
            String baseName = layers.size() == 1 ? layers.getFirst().name() : ws.projectNameProperty().get();
            fc.setInitialFileName(safeName(baseName) + "." + ext);
            File dir = initialDir();
            if (dir != null) fc.setInitialDirectory(dir);
            File f = fc.showSaveDialog(stage);
            if (f == null) return;
            SchematicFile sf;
            if (opts.source() == ExportDialog.Source.EACH) {
                List<SchematicFile.Region> regions = new ArrayList<>();
                for (Layer l : layers) regions.add(new SchematicFile.Region(l.name(), Scene.flatten(List.of(l)), BlockPos.ORIGIN));
                sf = new SchematicFile(ws.projectNameProperty().get(), author, "", opts.version().dataVersion(), regions);
            } else {
                sf = new SchematicFile(baseName, author, "", opts.version().dataVersion(),
                        List.of(new SchematicFile.Region(baseName, Scene.flatten(layers), BlockPos.ORIGIN)));
            }
            Schematics.write(sf, format, wo, f.toPath());
            ws.statusProperty().set(String.format("Exported %,d blocks to %s", sf.totalBlocks(), f.getName()));
            viewport.showToast("Exported " + f.getName());
        } catch (Exception e) {
            error("Export failed", e.getMessage());
        }
    }

    /** Replaced by the worldgen module; shows a message until then. */
    private Runnable datapackExporter;

    public void setDatapackExporter(Runnable r) {
        this.datapackExporter = r;
    }

    private void exportDatapack() {
        if (datapackExporter != null) {
            datapackExporter.run();
            return;
        }
        if (ws.scene().layers().isEmpty()) {
            error("Nothing to export", "Build or import something first.");
            return;
        }
        new DatapackDialog(stage, ws, scan).showAndWait();
    }

    static String safeName(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return n.isEmpty() ? "untitled" : n;
    }

    public void error(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.initOwner(stage);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.show();
    }

    // ---- input plumbing -----------------------------------------------------------------------------------

    private void installShortcuts(javafx.scene.Scene scene) {
        var acc = scene.getAccelerators();
        acc.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), () -> ws.editor().undoStack().undo());
        acc.put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), () -> ws.editor().undoStack().redo());
        acc.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> ws.editor().undoStack().redo());
        acc.put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), () -> save(false));
        acc.put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> save(true));
        acc.put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::openDialog);
        acc.put(new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN), this::importDialog);
        acc.put(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN), () -> exportDialog(null));

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (scene.getFocusOwner() instanceof TextInputControl) return;
            if (e.getCode() == KeyCode.K && e.isAltDown() && !e.isShortcutDown()) {
                viewport.toggleShortcuts();
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.C && e.isAltDown() && !e.isShortcutDown()) {
                ws.clearHotbar();
                viewport.showToast("Hotbar cleared");
                e.consume();
                return;
            }
            if (e.isShortcutDown() || e.isAltDown()) return;
            if (e.getCode().isDigitKey() && e.getCode() != KeyCode.DIGIT0 && e.getCode() != KeyCode.NUMPAD0) {
                // 1-9 hold a hotbar slot, like Minecraft.
                String name = e.getCode().getName();
                ws.selectHotbarSlot(name.charAt(name.length() - 1) - '1');
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.B) {
                ws.toggleBuild();
                e.consume();
                return;
            }
            ToolKind t = switch (e.getCode()) {
                case V -> ToolKind.VIEW;
                case Q -> ToolKind.SELECT;
                default -> null;
            };
            if (t != null) {
                ws.toolProperty().set(t);
                e.consume();
            }
        });
    }

    private void installDragAndDrop(javafx.scene.Scene scene) {
        scene.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
        });
        scene.setOnDragDropped(e -> {
            var files = e.getDragboard().getFiles();
            if (files == null || files.isEmpty()) return;
            for (File f : files) {
                if (f.getName().endsWith("." + ProjectFile.EXTENSION)) openProject(f.toPath());
                else if (Schematics.isSupported(f.toPath())) importFile(f.toPath());
            }
            e.setDropCompleted(true);
        });
    }
}
