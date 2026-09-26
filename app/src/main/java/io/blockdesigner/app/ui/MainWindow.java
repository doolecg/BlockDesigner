package io.blockdesigner.app.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.Workspace.ToolKind;
import io.blockdesigner.app.update.Updater;
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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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

/** The main editor window: top bar, layers + palette on the left, viewport in the centre, side tabs on the right. */
public final class MainWindow {
    private LayersPanel layers;
    private BlockPalette palette;
    private static final int[] LAYER_COLORS = {0x7C9CFF, 0x46C46E, 0xFFB454, 0xF2668B, 0x3DD6D0, 0xB18CFF, 0xE8D35A, 0xFF7A45};

    private final Stage stage;
    private final Workspace ws;
    private final BorderPane root = new BorderPane();
    /** Scene root: the main layout plus overlays such as the start screen. Holds the theme classes so every overlay sees the CSS variables. */
    private final StackPane windowRoot = new StackPane(root);
    private StartScreen startScreen;
    private java.util.function.Consumer<String> browser = url -> {
    };
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
    private final javafx.scene.control.Tab resourcesTab = new javafx.scene.control.Tab("Resource Tracker");
    /** Thin bar on the right edge listing closed side tabs; click one to reopen it. */
    private final javafx.scene.layout.VBox closedTabsBar = new javafx.scene.layout.VBox(4);
    private SplitPane mainSplit, leftSplit;
    /** The user's key binds (Settings › Keybinds). */
    private final Keybinds keys;
    private final BorderPane centerColumn = new BorderPane();
    private final java.util.Set<String> disabledPlugins;
    private final io.blockdesigner.app.plugins.PluginManager plugins;
    private final Updater updater = new Updater();
    private ToolDock toolDock;

    public MainWindow(Stage stage, Workspace ws) {
        this.stage = stage;
        this.ws = ws;
        this.viewport = new ViewportPane(ws);
        this.keys = new Keybinds(ws.settings());
        Keybinds.install(keys);
        viewport.setKeybinds(keys);
        this.disabledPlugins = new java.util.LinkedHashSet<>(ws.settings().disabledPlugins);
        this.plugins = new io.blockdesigner.app.plugins.PluginManager(io.blockdesigner.app.Settings.dir().resolve("plugins"), pluginHost(), disabledPlugins,
                ws.settings().pluginOptions);
        viewport.onSelectionChanged(plugins::selectionChanged);
        viewport.setTransformItems(this::transformItems);

        resolveDark();
        applyTheme();
        ws.darkProperty().addListener((o, a, b) -> applyTheme());
        ws.themeProperty().addListener((o, a, b) -> applyTheme());
        ws.themeModeProperty().addListener((o, a, b) -> resolveDark());
        // "Match Windows" follows a change of the Windows app mode the next time the window gets focus.
        stage.focusedProperty().addListener((o, a, focused) -> {
            if (focused) resolveDark();
        });

        // Left: layers over palette
        layers = new LayersPanel(ws, new LayersPanel.Actions(this::importDialog, l -> exportDialog(null, List.of(l)), viewport::frameLayer,
                viewport::fixLayerShapes));
        palette = new BlockPalette(ws);
        SplitPane left = new SplitPane(layers, palette);
        left.setOrientation(javafx.geometry.Orientation.VERTICAL);
        left.setDividerPositions(0.36);
        left.getStyleClass().add("left-split");
        left.setPrefWidth(330);
        left.setMinWidth(260);
        leftSplit = left;

        // Centre: viewport with floating dock and loading overlay
        ToolDock dock = new ToolDock(ws, viewport);
        toolDock = dock;
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
        mainSplit = main;
        main.setDividerPositions(0.2, 0.76);
        SplitPane.setResizableWithParent(left, false);
        SplitPane.setResizableWithParent(rightPanel, false);

        root.setTop(topBar());
        root.setCenter(main);
        closedTabsBar.getStyleClass().add("closed-tabs-bar");
        closedTabsBar.setAlignment(Pos.TOP_CENTER);
        root.setRight(closedTabsBar);
        root.setBottom(statusBar());
        windowRoot.getStyleClass().add("app-root");

        startScreen = new StartScreen(ws.settings(), new StartScreen.Actions(this::newProject, this::openDialog, this::importDialog,
                f -> {
                    if (f.getFileName().toString().endsWith("." + ProjectFile.EXTENSION)) openProject(f);
                    else importFile(f);
                }, () -> startAssetLoading(true), url -> browser.accept(url)));
        windowRoot.getChildren().add(startScreen);
        javafx.scene.Scene scene = new javafx.scene.Scene(windowRoot, 1560, 940);
        scene.getStylesheets().add(MainWindow.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        installShortcuts(scene);
        installDragAndDrop(scene);
        stage.setScene(scene);
        stage.titleProperty().bind(ws.projectNameProperty().concat(" — BlockDesigner"));
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        restoreWindow();
        stage.getIcons().setAll(appIcons());
        // Every other window (dialogs, alerts, pop-out panels) gets the app icon too, instead of Java's default.
        javafx.stage.Window.getWindows().addListener((javafx.collections.ListChangeListener<javafx.stage.Window>) c -> {
            while (c.next()) {
                for (javafx.stage.Window w : c.getAddedSubList()) {
                    if (w instanceof Stage st && st.getIcons().isEmpty()) st.getIcons().setAll(appIcons());
                }
            }
        });

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
        stage.setOnCloseRequest(e -> shutdown());
    }

    /** Puts the window back where it was last closed, if that place is still on a screen. */
    private void restoreWindow() {
        double[] b = ws.settings().windowBounds;
        if (b != null && b.length == 4 && b[2] >= 1000 && b[3] >= 640
                && !javafx.stage.Screen.getScreensForRectangle(b[0] + 40, b[1] + 10, b[2] - 80, 40).isEmpty()) {
            stage.setX(b[0]);
            stage.setY(b[1]);
            stage.setWidth(b[2]);
            stage.setHeight(b[3]);
        }
        stage.setMaximized(ws.settings().windowMaximized);
        // Normal bounds are tracked while not maximised, so a maximised window still restores to its old size.
        javafx.beans.InvalidationListener track = o -> {
            if (!stage.isMaximized() && !stage.isFullScreen() && !stage.isIconified() && stage.isShowing())
                ws.settings().windowBounds = new double[]{stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()};
        };
        stage.xProperty().addListener(track);
        stage.yProperty().addListener(track);
        stage.widthProperty().addListener(track);
        stage.heightProperty().addListener(track);
    }

    /** Sets the panel widths and the layers / palette split to how they were last left (once the window has a size). */
    private void restorePanels() {
        io.blockdesigner.app.Settings s = ws.settings();
        double total = mainSplit.getWidth();
        if (total <= 0) return;
        if (s.leftPanelWidth > 0) mainSplit.setDividerPosition(0, Math.clamp(s.leftPanelWidth / total, 0.1, 0.45));
        if (s.rightPanelWidth > 0 && mainSplit.getItems().contains(rightPanel))
            mainSplit.setDividerPosition(1, Math.clamp(1 - s.rightPanelWidth / total, 0.55, 0.92));
        if (s.leftSplit > 0) leftSplit.setDividerPosition(0, Math.clamp(s.leftSplit, 0.1, 0.9));
    }

    /** Remembers the window and panel layout for next time. */
    private void saveLayout() {
        io.blockdesigner.app.Settings s = ws.settings();
        s.windowMaximized = stage.isMaximized();
        double left = mainSplit.getItems().getFirst() instanceof javafx.scene.layout.Region r ? r.getWidth() : 0;
        if (left > 0) s.leftPanelWidth = left;
        if (mainSplit.getItems().contains(rightPanel) && rightPanel.getWidth() > 0) s.rightPanelWidth = rightPanel.getWidth();
        if (!leftSplit.getDividers().isEmpty()) s.leftSplit = leftSplit.getDividerPositions()[0];
    }

    /** Stops the viewport and plugins and saves settings; run as the app closes. */
    private void shutdown() {
        saveLayout();
        viewport.detach();
        plugins.shutdown();
        ws.settings().save();
    }

    /** What plugins can reach: the scene, undoable edits, the viewport's world editing and feedback. */
    private io.blockdesigner.app.plugins.PluginHost pluginHost() {
        return new io.blockdesigner.app.plugins.PluginHost() {
            @Override
            public Scene scene() {
                return ws.scene();
            }

            @Override
            public io.blockdesigner.core.edit.SceneEditor editor() {
                return ws.editor();
            }

            @Override
            public java.util.Optional<Layer> activeLayer() {
                return java.util.Optional.ofNullable(ws.activeLayerProperty().get());
            }

            @Override
            public List<Layer> selectedLayers() {
                return ws.nudgeTargets();
            }

            @Override
            public io.blockdesigner.core.version.McVersion targetVersion() {
                return ws.targetVersionProperty().get();
            }

            @Override
            public void editWorld(String label, java.util.function.Consumer<io.blockdesigner.core.worldedit.WorldEdit.World> edit) {
                viewport.editWorld(label, edit);
            }

            @Override
            public Layer addLayer(String name, Structure blocks) {
                Layer l = new Layer(name, blocks);
                ws.editor().addLayer(l);
                ws.selectedLayers().setAll(l);
                return l;
            }

            @Override
            public void status(String message) {
                ws.statusProperty().set(message);
            }

            @Override
            public void toast(String message) {
                viewport.showToast(message);
            }

            @Override
            public void runOnUiThread(Runnable task) {
                if (Platform.isFxApplicationThread()) task.run();
                else Platform.runLater(task);
            }

            @Override
            public void pluginsChanged() {
                ws.settings().disabledPlugins = new ArrayList<>(disabledPlugins);
                ws.settings().save();
                syncPluginPanels();
                if (toolDock != null) toolDock.setPluginTools(plugins.tools(), MainWindow.this::pickPluginTool, MainWindow.this::pluginToolKeyText);
            }

            @Override
            public void pluginUnloading(io.blockdesigner.app.plugins.PluginManager.Plugin plugin) {
                if (viewport.pluginTool().map(t -> t.plugin() == plugin).orElse(false)) {
                    viewport.setPluginTool(null, null);
                    ws.toolProperty().set(ToolKind.SELECT);
                }
            }

            @Override
            public void runLater(Runnable task) {
                Platform.runLater(task);
            }

            @Override
            public io.blockdesigner.assets.BlockAssets assets() {
                return ws.assets();
            }

            @Override
            public java.util.Optional<io.blockdesigner.core.model.Box> selection() {
                return viewport.selectionBounds();
            }

            @Override
            public void openTransform(io.blockdesigner.app.plugins.PluginManager.Transform transform) {
                MainWindow.this.openTransform(transform);
            }
        };
    }

    // ---- plugin tools -----------------------------------------------------------------------------------------

    /** Makes a plugin tool the active tool. */
    private void pickPluginTool(io.blockdesigner.app.plugins.PluginManager.Tool t) {
        if (viewport.setPluginTool(t, plugins)) ws.toolProperty().set(ToolKind.PLUGIN);
        else if (ws.toolProperty().get() == ToolKind.PLUGIN) ws.toolProperty().set(ToolKind.SELECT);
    }

    /** The key that picks a plugin tool: the user's (settings file) or the plugin's default; null for none. */
    private KeyCombination pluginToolKey(io.blockdesigner.app.plugins.PluginManager.Tool t) {
        String k = ws.settings().pluginToolKeys.getOrDefault(t.key(), t.tool().defaultKey());
        if (k == null || k.isBlank()) return null;
        try {
            KeyCombination kc = KeyCombination.valueOf(k);
            // A key already bound to one of BlockDesigner's own actions keeps doing that.
            for (Keybinds.Action a : Keybinds.Action.values()) {
                for (KeyCombination bound : keys.get(a)) if (kc.equals(bound)) return null;
            }
            return kc;
        } catch (RuntimeException bad) {
            return null;
        }
    }

    private String pluginToolKeyText(io.blockdesigner.app.plugins.PluginManager.Tool t) {
        KeyCombination k = pluginToolKey(t);
        return k == null ? "" : Keybinds.text(k);
    }

    /** Picks the plugin tool bound to this key, if any. */
    private boolean pluginToolShortcut(KeyEvent e) {
        for (var t : plugins.tools()) {
            KeyCombination k = pluginToolKey(t);
            if (k != null && k.match(e)) {
                pickPluginTool(t);
                return true;
            }
        }
        return false;
    }

    // ---- plugin transforms ------------------------------------------------------------------------------------

    private TransformDialog openTransformDialog;

    /** Opens a plugin transform's dialog on the current selection (or layer), or says why it can't. */
    private void openTransform(io.blockdesigner.app.plugins.PluginManager.Transform t) {
        if (viewport.transformTarget(t.transform().scope()) == null) {
            viewport.showToast(viewport.transformTargetMissing(t.transform().scope()));
            return;
        }
        if (openTransformDialog != null) openTransformDialog.close();
        openTransformDialog = new TransformDialog(stage, ws, plugins, t, viewport);
        openTransformDialog.show();
    }

    /** One menu item per plugin transform, for the Plugins menu and the viewport's right-click menu. */
    private List<MenuItem> transformItems() {
        List<MenuItem> out = new ArrayList<>();
        for (var t : plugins.transforms()) {
            MenuItem m = new MenuItem(t.transform().name() + "…", ToolIcons.plugin(t.transform().icon(), 14));
            m.setOnAction(e -> openTransform(t));
            out.add(m);
        }
        return out;
    }

    // ---- plugin panels ----------------------------------------------------------------------------------------

    /** A plugin panel's tab in the right-hand tabs, created the first time it is shown. */
    private final class PanelTab implements io.blockdesigner.plugin.PanelContext {
        final io.blockdesigner.app.plugins.PluginManager.Panel panel;
        final javafx.scene.control.Tab tab = new javafx.scene.control.Tab();
        final Label badge = new Label();
        final List<Runnable> onShown = new ArrayList<>();
        boolean created;

        PanelTab(io.blockdesigner.app.plugins.PluginManager.Panel panel) {
            this.panel = panel;
            Label title = new Label(panel.panel().title());
            badge.getStyleClass().add("badge");
            badge.setVisible(false);
            badge.setManaged(false);
            HBox head = new HBox(6, ToolIcons.plugin(panel.panel().icon(), 14), title, badge);
            head.setAlignment(Pos.CENTER_LEFT);
            tab.setGraphic(head);
            tab.setTooltip(new Tooltip(panel.panel().title() + " · from the plugin " + panel.plugin().info().name()));
            tab.selectedProperty().addListener((o, a, selected) -> {
                if (selected) shown();
            });
        }

        /** Builds the content on first show, then runs the plugin's on-shown actions. */
        void shown() {
            if (!created) {
                created = true;
                try {
                    tab.setContent(panel.panel().create(this));
                } catch (Throwable t) {
                    plugins.report(panel.plugin(), "Panel '" + panel.panel().title() + "'", t);
                    Label err = new Label("This panel failed to open: " + t.getMessage());
                    err.setWrapText(true);
                    err.getStyleClass().add("plugin-error");
                    err.setPadding(new Insets(12));
                    tab.setContent(err);
                }
            }
            for (Runnable r : List.copyOf(onShown)) {
                try {
                    r.run();
                } catch (Throwable t) {
                    plugins.log(panel.plugin(), "Panel '" + panel.panel().title() + "' on-shown action failed: " + t);
                }
            }
        }

        @Override
        public io.blockdesigner.plugin.PluginContext plugin() {
            return panel.plugin().context();
        }

        @Override
        public boolean isShowing() {
            return tab.isSelected() && sideTabs.getTabs().contains(tab) && mainSplit.getItems().contains(rightPanel) && stage.isShowing();
        }

        @Override
        public void onShown(Runnable action) {
            onShown.add(action);
        }

        @Override
        public void setBadge(String text) {
            boolean show = text != null && !text.isBlank();
            badge.setText(show ? text : "");
            badge.setVisible(show);
            badge.setManaged(show);
        }

        @Override
        public void reveal() {
            reopenTab(tab);
        }
    }

    private final java.util.Map<String, PanelTab> pluginTabs = new java.util.LinkedHashMap<>();
    /** Set while plugin tabs are added or removed with their plugins, so that isn't mistaken for the user closing them. */
    private boolean syncingPanels;

    /**
     * Adds tabs for newly enabled plugins' panels and removes those of disabled ones. Every panel docks on the right
     * for now; {@link io.blockdesigner.plugin.PluginPanel.Dock LEFT and BOTTOM} fall back to it.
     */
    private void syncPluginPanels() {
        if (rightPanel.getCenter() == null) return; // the tabs aren't built yet (setRightPanel adds them)
        java.util.Map<String, io.blockdesigner.app.plugins.PluginManager.Panel> now = new java.util.LinkedHashMap<>();
        for (var p : plugins.panels()) now.put(p.key(), p);
        syncingPanels = true;
        try {
            for (var it = pluginTabs.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                var current = now.get(e.getKey());
                if (current != null && current.panel() == e.getValue().panel.panel()) continue;
                sideTabs.getTabs().remove(e.getValue().tab);
                it.remove();
            }
            for (var e : now.entrySet()) {
                if (pluginTabs.containsKey(e.getKey())) continue;
                PanelTab pt = new PanelTab(e.getValue());
                pluginTabs.put(e.getKey(), pt);
                if (!ws.settings().closedPluginPanels.contains(e.getKey())) sideTabs.getTabs().add(pt.tab);
            }
        } finally {
            syncingPanels = false;
        }
        sideTabsChanged();
    }

    /** Right-hand panel: the Resource Tracker tab. */
    public void setRightPanel() {
        // Tabs can be closed; closed tabs wait in the bar on the right edge. With none open the panel folds away.
        resourcesTab.setContent(ComingSoonPanel.resourceTracker());
        resourcesTab.setGraphic(new FontIcon(Feather.PACKAGE));
        sideTabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.ALL_TABS);
        sideTabs.getStyleClass().add("side-tabs");
        rightPanel.setCenter(sideTabs);
        if (ws.settings().showResources) sideTabs.getTabs().add(resourcesTab);
        sideTabs.getTabs().addListener((javafx.collections.ListChangeListener<javafx.scene.control.Tab>) c -> sideTabsChanged());
        sideTabsChanged();
        syncPluginPanels();
    }

    private void reopenTab(javafx.scene.control.Tab tab) {
        if (!sideTabs.getTabs().contains(tab)) sideTabs.getTabs().add(tab);
        sideTabs.getSelectionModel().select(tab);
        // A tab that was already selected doesn't fire a selection change: run its on-shown actions anyway.
        for (PanelTab pt : pluginTabs.values()) if (pt.tab == tab) pt.shown();
    }

    /** Syncs settings, the closed-tabs bar and whether the right panel is shown at all. */
    private void sideTabsChanged() {
        boolean resources = sideTabs.getTabs().contains(resourcesTab);
        ws.settings().showResources = resources;
        if (!syncingPanels) {
            // Remember which plugin panels the user closed, so they stay closed next time.
            for (var e : pluginTabs.entrySet()) {
                boolean open = sideTabs.getTabs().contains(e.getValue().tab);
                if (open) ws.settings().closedPluginPanels.remove(e.getKey());
                else if (!ws.settings().closedPluginPanels.contains(e.getKey())) ws.settings().closedPluginPanels.add(e.getKey());
            }
        }

        closedTabsBar.getChildren().clear();
        if (!resources) closedTabsBar.getChildren().add(closedTabButton(resourcesTab, resourcesTab.getText(), new FontIcon(Feather.PACKAGE)));
        for (PanelTab pt : pluginTabs.values()) {
            if (!sideTabs.getTabs().contains(pt.tab)) {
                closedTabsBar.getChildren().add(closedTabButton(pt.tab, pt.panel.panel().title(), ToolIcons.plugin(pt.panel.panel().icon(), 14)));
            }
        }
        boolean anyClosed = !closedTabsBar.getChildren().isEmpty();
        closedTabsBar.setVisible(anyClosed);
        closedTabsBar.setManaged(anyClosed);

        boolean anyOpen = !sideTabs.getTabs().isEmpty();
        if (anyOpen && !mainSplit.getItems().contains(rightPanel)) {
            mainSplit.getItems().add(rightPanel);
            double w = ws.settings().rightPanelWidth, total = mainSplit.getWidth();
            mainSplit.setDividerPosition(1, w > 0 && total > 0 ? Math.clamp(1 - w / total, 0.55, 0.92) : 0.76);
        } else if (!anyOpen) {
            mainSplit.getItems().remove(rightPanel);
        }
    }

    /** A vertical tab (icon plus rotated title) that reopens a closed side tab. */
    private javafx.scene.Node closedTabButton(javafx.scene.control.Tab tab, String title, javafx.scene.Node icon) {
        Button b = new Button(title, icon);
        b.getStyleClass().addAll("flat", "closed-tab");
        b.setTooltip(new Tooltip("Open " + title));
        b.setRotate(90);
        b.setOnAction(e -> reopenTab(tab));
        return new javafx.scene.Group(b);
    }

    public ViewportPane viewport() {
        return viewport;
    }

    public Stage stage() {
        return stage;
    }

    private static List<javafx.scene.image.Image> appIcons;

    /** The BlockDesigner icon at every size (drawn by packaging/make_icon.py); the same art marks .bdproj saves. */
    public static synchronized List<javafx.scene.image.Image> appIcons() {
        if (appIcons == null) {
            List<javafx.scene.image.Image> out = new java.util.ArrayList<>();
            for (int size : new int[]{16, 24, 32, 48, 64, 128, 256, 512}) {
                var url = MainWindow.class.getResource("/io/blockdesigner/app/icons/icon-" + size + ".png");
                if (url != null) out.add(new javafx.scene.image.Image(url.toExternalForm()));
            }
            appIcons = List.copyOf(out);
        }
        return appIcons;
    }

    /** The app icon as a graphic {@code size} px tall, from an image twice that size so it stays sharp on HiDPI. */
    public static javafx.scene.image.ImageView appIconView(double size) {
        javafx.scene.image.Image best = null;
        for (javafx.scene.image.Image i : appIcons()) {
            if (best == null || best.getWidth() < size * 2) best = i;
            if (i.getWidth() >= size * 2) {
                best = i;
                break;
            }
        }
        javafx.scene.image.ImageView v = new javafx.scene.image.ImageView(best);
        v.setFitWidth(size);
        v.setFitHeight(size);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        return v;
    }

    public void show() {
        stage.show();
        javafx.application.Platform.runLater(this::restorePanels);
        plugins.loadAll();
        long failed = plugins.plugins().stream().filter(p -> p.state() == io.blockdesigner.app.plugins.PluginManager.State.FAILED).count();
        if (failed > 0) ws.statusProperty().set(failed + " plugin" + (failed == 1 ? "" : "s") + " failed to load · see Plugins > Manage plugins");
        if (ws.settings().showStartScreen) startScreen.open();
        startAssetLoading(false);
        if (ws.settings().checkForUpdates) checkForUpdates(false);
    }

    // ---- updates ------------------------------------------------------------------------------------------------

    /**
     * Looks for a newer release on GitHub in the background. At startup ({@code manual} false) it stays quiet unless
     * there's a version the user hasn't skipped; from Settings it also says when BlockDesigner is up to date or the
     * check failed.
     */
    public void checkForUpdates(boolean manual) {
        if (manual) ws.statusProperty().set("Checking for updates…");
        Thread.ofVirtual().name("update-check").start(() -> {
            try {
                var release = updater.latest();
                boolean newer = Updater.compareVersions(release.version(), Updater.currentVersion()) > 0;
                Platform.runLater(() -> {
                    if (newer && (manual || !release.version().equals(ws.settings().skippedVersion))) {
                        showUpdate(release);
                    } else if (manual) {
                        ws.statusProperty().set("BlockDesigner " + Updater.currentVersion() + " is up to date");
                        info("No updates", "You have the latest version, BlockDesigner " + Updater.currentVersion() + ".");
                    }
                });
            } catch (Exception e) {
                if (manual) Platform.runLater(() -> error("Couldn't check for updates", e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        });
    }

    private void showUpdate(Updater.Release release) {
        new UpdateDialog(stage, ws.darkProperty().get(), updater, release, this::readyToQuitForUpdate,
                () -> {
                    shutdown();
                    Platform.exit();
                },
                version -> {
                    ws.settings().skippedVersion = version;
                    ws.settings().save();
                },
                url -> browser.accept(url)).show();
    }

    /** Offers to save the project before BlockDesigner closes to update; false if the user cancels. */
    private boolean readyToQuitForUpdate() {
        if (!ws.editor().undoStack().canUndo()) return true;
        ButtonType saveFirst = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType dontSave = new ButtonType("Don't save", ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "BlockDesigner will close to install the update.",
                saveFirst, dontSave, ButtonType.CANCEL);
        a.initOwner(stage);
        a.setTitle("Save before updating?");
        a.setHeaderText("Save changes to " + ws.projectNameProperty().get() + "?");
        var choice = a.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == ButtonType.CANCEL) return false;
        if (choice == saveFirst) {
            Path before = ws.projectFileProperty().get();
            save(false);
            // Cancelled the file chooser for an unsaved project: don't quit.
            if (before == null && ws.projectFileProperty().get() == null) return false;
        }
        return true;
    }

    /** Opens web links (the start screen's download sites); the app passes in its host services. */
    public void setBrowser(java.util.function.Consumer<String> browser) {
        this.browser = browser;
    }

    public void closeStartScreen() {
        startScreen.close();
    }

    /** Clears everything for a fresh, unsaved project. */
    public void newProject() {
        for (Layer l : List.copyOf(ws.scene().layers())) ws.scene().remove(l);
        ws.editor().undoStack().clear();
        ws.projectNameProperty().set("Untitled");
        ws.projectFileProperty().set(null);
        projectExtrasLoaded(Map.of());
        plugins.projectOpened(java.util.Optional.empty());
        ws.statusProperty().set("New project");
    }

    /** Sets dark/light from the theme mode (Dark, Light, or Windows' app mode). */
    private void resolveDark() {
        ws.darkProperty().set(AppTheme.isDark(AppTheme.mode(ws.themeModeProperty().get())));
    }

    /** Installs the theme as the user-agent stylesheet, so every window, menu and popup follows it. */
    private void applyTheme() {
        boolean dark = ws.darkProperty().get();
        AppTheme theme = AppTheme.byId(ws.themeProperty().get());
        try {
            Application.setUserAgentStylesheet(theme.userAgentStylesheet(dark));
        } catch (RuntimeException e) {
            // Fall back to plain Primer if the themed sheet can't be built.
            Application.setUserAgentStylesheet(dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
        }
        windowRoot.getStyleClass().removeAll("dark", "light");
        windowRoot.getStyleClass().add(dark ? "dark" : "light");
        ws.settings().darkTheme = dark;
    }

    /** The Settings window (theme, mode, general options). */
    public void openSettings() {
        new SettingsDialog(stage, ws, keys, this::applyAccelerators, () -> startAssetLoading(true),
                () -> new PluginsDialog(stage, plugins, ws.darkProperty().get()).showAndWait(),
                () -> checkForUpdates(true)).showAndWait();
        ws.settings().save();
    }

    // ---- top & status bars ------------------------------------------------------------------------------------

    private HBox topBar() {
        Label logo = new Label("BlockDesigner", appIconView(20));
        logo.getStyleClass().add("app-logo");
        logo.setCursor(javafx.scene.Cursor.HAND);
        logo.setTooltip(new Tooltip("Start screen: new, open, recent, Minecraft jar, schematic sites"));
        logo.setOnMouseClicked(e -> startScreen.open());

        TextField name = new TextField();
        name.getStyleClass().add("project-name");
        name.textProperty().bindBidirectional(ws.projectNameProperty());
        name.setPrefColumnCount(14);

        Button newDoc = LayersPanel.iconButton(Feather.FILE_PLUS, Keybinds.tooltip("New project", "Start an empty project", Keybinds.Action.NEW_PROJECT), this::newProject);
        Button open = LayersPanel.iconButton(Feather.FOLDER, Keybinds.tooltip("Open", "A project (.bdproj) or a schematic: .nbt, .litematic, .schem", Keybinds.Action.OPEN), this::openDialog);
        Button save = LayersPanel.iconButton(Feather.SAVE, Keybinds.tooltip("Save project", "Save as a .bdproj project (Save as: " + Keybinds.keyOf(Keybinds.Action.SAVE_AS) + ")", Keybinds.Action.SAVE), () -> save(false));
        Button imp = new Button("Import", new FontIcon(Feather.DOWNLOAD));
        imp.getStyleClass().add("flat");
        imp.setOnAction(e -> importDialog());

        MenuButton export = new MenuButton("Export", new FontIcon(Feather.UPLOAD));
        export.getStyleClass().add("accent");
        export.setOnShowing(e -> fillExportMenu(export));
        fillExportMenu(export);

        MenuButton pluginMenu = new MenuButton(null, FormatIcons.icon(FormatIcons.Kind.PLUGIN, 16));
        pluginMenu.getStyleClass().add("flat");
        pluginMenu.setTooltip(new Tooltip("Plugins"));
        pluginMenu.setOnShowing(e -> fillPluginMenu(pluginMenu));
        fillPluginMenu(pluginMenu);

        Button undo = LayersPanel.iconButton(Feather.CORNER_UP_LEFT, Keybinds.tooltip("Undo", "Nothing to undo", Keybinds.Action.UNDO), () -> ws.editor().undoStack().undo());
        Button redo = LayersPanel.iconButton(Feather.CORNER_UP_RIGHT, Keybinds.tooltip("Redo", "Nothing to redo", Keybinds.Action.REDO), () -> ws.editor().undoStack().redo());
        ws.editor().undoStack().addListener(() -> {
            undo.setDisable(!ws.editor().undoStack().canUndo());
            redo.setDisable(!ws.editor().undoStack().canRedo());
            undo.setTooltip(Keybinds.tooltip("Undo", ws.editor().undoStack().undoLabel().orElse("Nothing to undo"), Keybinds.Action.UNDO));
            redo.setTooltip(Keybinds.tooltip("Redo", ws.editor().undoStack().redoLabel().orElse("Nothing to redo"), Keybinds.Action.REDO));
        });
        undo.setDisable(true);
        redo.setDisable(true);

        Button theme = LayersPanel.iconButton(Feather.MOON, "Light / dark (Settings has themes)",
                () -> ws.themeModeProperty().set(ws.darkProperty().get() ? "LIGHT" : "DARK"));
        Button assets = LayersPanel.iconButton(Feather.SETTINGS, Keybinds.tooltip("Settings", "General, appearance and themes, keybinds", Keybinds.Action.SETTINGS), this::openSettings);
        assetBadge.getStyleClass().add("badge");
        assetBadge.setCursor(javafx.scene.Cursor.HAND);
        assetBadge.setOnMouseClicked(e -> startAssetLoading(true));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, logo, name, newDoc, open, save, spacer, undo, redo, theme, assetBadge, assets, pluginMenu, imp, export);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    /** Export menu: the Export window, one entry per card (with its format icon), then plugin exporters. */
    private void fillExportMenu(MenuButton menu) {
        List<MenuItem> items = new ArrayList<>();
        MenuItem all = new MenuItem(Keybinds.named("Export…", Keybinds.Action.EXPORT), new FontIcon(Feather.UPLOAD));
        all.setOnAction(e -> exportDialog(null, null));
        items.add(all);
        items.add(new SeparatorMenuItem());
        items.add(exportItem("Litematica (.litematic)…", FormatIcons.Kind.LITEMATICA, "litematica"));
        items.add(exportItem("WorldEdit (.schem)…", FormatIcons.Kind.WORLDEDIT, "sponge"));
        items.add(exportItem("Create / Structure (.nbt)…", FormatIcons.Kind.CREATE, "vanilla"));
        items.add(new SeparatorMenuItem());
        MenuItem datapack = new MenuItem(Keybinds.named("Worldgen data pack…", Keybinds.Action.EXPORT_DATAPACK), FormatIcons.icon(FormatIcons.Kind.DATAPACK, 16));
        datapack.setOnAction(e -> exportDatapack());
        items.add(datapack);
        boolean first = true;
        for (SchematicFormat f : Schematics.formats()) {
            if (Schematics.isBuiltIn(f) || !f.canWrite()) continue;
            if (first) items.add(new SeparatorMenuItem());
            first = false;
            items.add(exportItem(f.displayName() + " (." + f.extensions().getFirst() + ")…", FormatIcons.Kind.PLUGIN, f.id()));
        }
        for (var ex : plugins.exporters()) {
            if (first) items.add(new SeparatorMenuItem());
            first = false;
            items.add(exportItem(ex.exporter().displayName() + "…", FormatIcons.Kind.PLUGIN,
                    "plugin:" + ex.plugin().info().id() + "/" + ex.exporter().id()));
        }
        menu.getItems().setAll(items);
    }

    private MenuItem exportItem(String text, FormatIcons.Kind kind, String card) {
        MenuItem m = new MenuItem(text, FormatIcons.icon(kind, 16));
        m.setOnAction(e -> exportDialog(card, null));
        return m;
    }

    /** Plugins menu: the transforms, every plugin action, then the Plugins window. */
    private void fillPluginMenu(MenuButton menu) {
        List<MenuItem> items = new ArrayList<>();
        List<MenuItem> transforms = transformItems();
        if (!transforms.isEmpty()) {
            javafx.scene.control.Menu tm = new javafx.scene.control.Menu("Transform", new FontIcon(Feather.SLIDERS));
            tm.getItems().setAll(transforms);
            items.add(tm);
        }
        for (var a : plugins.actions()) {
            MenuItem m = new MenuItem(a.action().label());
            m.setOnAction(e -> plugins.run(a));
            items.add(m);
        }
        if (items.isEmpty()) {
            MenuItem none = new MenuItem("No plugin actions");
            none.setDisable(true);
            items.add(none);
        }
        items.add(new SeparatorMenuItem());
        MenuItem manage = new MenuItem("Manage plugins…", new FontIcon(Feather.SETTINGS));
        manage.setOnAction(e -> new PluginsDialog(stage, plugins, ws.darkProperty().get()).showAndWait());
        items.add(manage);
        menu.getItems().setAll(items);
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

    private boolean statusQueued;

    /** Status-bar totals; change events arrive in bursts (brush strokes, fills), so recompute once per pulse. */
    private void updateStatus() {
        if (statusQueued) return;
        statusQueued = true;
        Platform.runLater(() -> {
            statusQueued = false;
            updateStatusNow();
        });
    }

    private void updateStatusNow() {
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

    /** Every readable extension, including formats and importers added by plugins. */
    private FileChooser.ExtensionFilter schematicFilter() {
        List<String> exts = java.util.stream.Stream.concat(
                        Schematics.formats().stream().filter(SchematicFormat::canRead).flatMap(f -> f.extensions().stream()),
                        plugins.importers().stream().flatMap(i -> i.importer().extensions().stream()))
                .distinct().map(e -> "*." + e).toList();
        return new FileChooser.ExtensionFilter("Schematics (" + String.join(", ", exts) + ")", exts);
    }

    /** One filter per plugin importer, after the combined one. */
    private List<FileChooser.ExtensionFilter> importerFilters() {
        return plugins.importers().stream().map(i -> new FileChooser.ExtensionFilter(i.importer().displayName(),
                i.importer().extensions().stream().map(e -> "*." + e).toList())).toList();
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
        fc.getExtensionFilters().addAll(importerFilters());
        File dir = initialDir();
        if (dir != null) fc.setInitialDirectory(dir);
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null) files.forEach(f -> importFile(f.toPath()));
    }

    /**
     * Reads a schematic and starts placement; multi-region Litematica files become one layer per region. Files no
     * schematic format reads go to a plugin importer for their extension.
     */
    public void importFile(Path file) {
        if (!Schematics.isSupported(file)) {
            var imp = plugins.importerFor(file);
            if (imp.isPresent()) {
                importWithPlugin(imp.get(), file);
                return;
            }
        }
        ws.statusProperty().set("Reading " + file.getFileName() + "…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return Schematics.readDetailed(file);
            } catch (Exception e) {
                throw new RuntimeException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }).thenAcceptAsync(read -> {
            SchematicFile sf = read.file();
            List<Layer> layers = new ArrayList<>();
            for (SchematicFile.Region r : sf.regions()) {
                String name = sf.regions().size() == 1 ? sf.name() : sf.name() + " · " + r.name();
                Structure s = r.structure();
                s.metadata().name = name;
                s.metadata().author = sf.author();
                Layer l = new Layer(name, s);
                l.setOffset(r.position());
                l.setSource(read.format().id());
                layers.add(l);
            }
            ws.settings().addRecent(file);
            ws.statusProperty().set(String.format("Imported %s — %,d blocks (DataVersion %d)", file.getFileName(), sf.totalBlocks(), sf.dataVersion()));
            if (ws.projectNameProperty().get().equals("Untitled") && ws.scene().layers().isEmpty()) ws.projectNameProperty().set(sf.name());
            viewport.beginPlacement(layers, null);
        }, Platform::runLater).exceptionally(this::fail);
    }

    /** Imports through a plugin: asks for its options (if any), reads on a background thread, then starts placement. */
    private void importWithPlugin(io.blockdesigner.app.plugins.PluginManager.Import imp, Path file) {
        var importer = imp.importer();
        String key = io.blockdesigner.app.plugins.OptionStore.key(imp.plugin().info().id(), "importer", importer.id());
        io.blockdesigner.plugin.OptionValues values = plugins.optionStore().load(key, importer.options(), plugins.blocks());
        if (!importer.options().isEmpty()) {
            OptionsEditor editor = new OptionsEditor(values, plugins.blocks(), () -> ws.selectedBlockProperty().get(), v -> {
            });
            javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
            d.initOwner(stage);
            d.setTitle(importer.displayName());
            d.setHeaderText("Import " + file.getFileName());
            d.getDialogPane().getStylesheets().add(MainWindow.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
            d.getDialogPane().getStyleClass().addAll("app-root", ws.darkProperty().get() ? "dark" : "light");
            editor.setPrefWidth(380);
            d.getDialogPane().setContent(editor);
            d.getDialogPane().getButtonTypes().setAll(new ButtonType("Import", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
            var choice = d.showAndWait();
            if (choice.isEmpty() || choice.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
            values = editor.values();
            plugins.optionStore().save(key, values);
        }
        io.blockdesigner.plugin.OptionValues chosen = values;
        ws.statusProperty().set("Reading " + file.getFileName() + " with " + importer.displayName() + "…");
        io.blockdesigner.plugin.Progress progress = (fraction, message) -> {
            if (message != null) Platform.runLater(() -> ws.statusProperty().set(message));
        };
        CompletableFuture.supplyAsync(() -> {
            try {
                return importer.importFile(file, chosen, progress, plugins.blocks());
            } catch (Exception e) {
                plugins.log(imp.plugin(), importer.displayName() + " failed on " + file.getFileName() + ": " + e);
                throw new RuntimeException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }).thenAcceptAsync(imported -> {
            List<Layer> layers = new ArrayList<>();
            for (var il : imported == null ? List.<io.blockdesigner.plugin.PluginImporter.ImportedLayer>of() : imported) {
                Layer l = new Layer(il.name(), il.blocks());
                l.setOffset(il.offset());
                l.setSource("plugin:" + imp.key());
                layers.add(l);
            }
            if (layers.isEmpty() || layers.stream().allMatch(l -> l.structure().isEmpty())) {
                ws.statusProperty().set(file.getFileName() + ": nothing to import");
                viewport.showToast("Nothing to import in " + file.getFileName());
                return;
            }
            ws.settings().addRecent(file);
            long blocks = layers.stream().mapToLong(l -> l.structure().blockCount()).sum();
            ws.statusProperty().set(String.format("Imported %s with %s — %,d blocks", file.getFileName(), importer.displayName(), blocks));
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
            plugins.projectOpened(java.util.Optional.of(file));
            viewport.frameAll();
            ws.statusProperty().set("Opened " + file.getFileName());
        }, Platform::runLater).exceptionally(this::fail);
    }

    /** Hook for other modules to store their own data in the project file. */
    private java.util.function.Consumer<Map<String, byte[]>> extrasLoader = m -> {
    };
    private java.util.function.Supplier<Map<String, byte[]>> extrasSaver = Map::of;

    public void setProjectExtras(java.util.function.Supplier<Map<String, byte[]>> saver, java.util.function.Consumer<Map<String, byte[]>> loader) {
        this.extrasSaver = saver;
        this.extrasLoader = loader;
    }

    private void projectExtrasLoaded(Map<String, byte[]> extras) {
        // The block selection, selected entities and WorldEdit region come back as they were saved.
        viewport.loadSelectionExtras(extras);
        extrasLoader.accept(extras);
    }

    private Map<String, byte[]> projectExtras() {
        Map<String, byte[]> out = new java.util.LinkedHashMap<>(viewport.selectionExtras());
        out.putAll(extrasSaver.get());
        return out;
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
                ws.activeLayerProperty().get() == null ? null : ws.activeLayerProperty().get().id(), projectExtras());
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

    /**
     * Opens the Export window on a card ({@code "litematica"}, {@code "sponge"}, {@code "vanilla"}, {@code "datapack"}, a
     * plugin card key) or on the last one used when null. {@code layers} fixes what is exported (a layer's own menu).
     */
    private void exportDialog(String preselect, List<Layer> layers) {
        if (ws.scene().layers().isEmpty()) {
            error("Nothing to export", "Build or import something first.");
            return;
        }
        new ExportDialog(stage, ws, scan, plugins.exporters(), layers, preselect).showAndWait().ifPresent(out -> {
            ws.settings().save();
            if (out.datapack()) {
                exportDatapack();
                return;
            }
            String where = out.written().size() == 1 ? out.written().getFirst().getFileName().toString() : out.written().size() + " files";
            ws.statusProperty().set(String.format("Exported %,d blocks to %s", out.blocks(),
                    out.written().size() == 1 ? out.written().getFirst() : out.written().getFirst().getParent()));
            viewport.showToast("Exported " + where);
        });
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

    public void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(stage);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(message);
        a.show();
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

    /** The accelerators currently installed from the key binds (so a rebind can take them out again). */
    private final List<KeyCombination> installedAccelerators = new ArrayList<>();

    /**
     * Scene accelerators for the file actions and full screen, from the key binds. These work even while a text
     * field has focus, as before. Called again after the binds change.
     */
    void applyAccelerators() {
        var acc = mainScene.getAccelerators();
        installedAccelerators.forEach(acc::remove);
        installedAccelerators.clear();
        Map<Keybinds.Action, Runnable> run = new java.util.EnumMap<>(Keybinds.Action.class);
        run.put(Keybinds.Action.UNDO, () -> ws.editor().undoStack().undo());
        run.put(Keybinds.Action.REDO, () -> ws.editor().undoStack().redo());
        run.put(Keybinds.Action.SAVE, () -> save(false));
        run.put(Keybinds.Action.SAVE_AS, () -> save(true));
        run.put(Keybinds.Action.OPEN, this::openDialog);
        run.put(Keybinds.Action.IMPORT, this::importDialog);
        run.put(Keybinds.Action.EXPORT, () -> exportDialog(null, null));
        run.put(Keybinds.Action.EXPORT_DATAPACK, this::exportDatapack);
        run.put(Keybinds.Action.NEW_PROJECT, this::newProject);
        run.put(Keybinds.Action.SETTINGS, this::openSettings);
        run.put(Keybinds.Action.SEARCH_BLOCKS, () -> palette.focusSearch());
        run.put(Keybinds.Action.FULL_SCREEN, () -> {
            stage.setFullScreenExitHint("Esc or " + Keybinds.keyOf(Keybinds.Action.FULL_SCREEN) + " leaves full screen");
            stage.setFullScreen(!stage.isFullScreen());
        });
        run.forEach((action, r) -> {
            for (KeyCombination k : keys.get(action)) {
                if (k == null || acc.containsKey(k)) continue;
                acc.put(k, r);
                installedAccelerators.add(k);
            }
        });
    }

    private javafx.scene.Scene mainScene;

    private void installShortcuts(javafx.scene.Scene scene) {
        mainScene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (scene.getFocusOwner() instanceof TextInputControl) return;
            // Any key pressed with Alt is a shortcut, so the Alt-held shape wheel must not open (or stay open).
            if (!viewport.isWheelKey(e.getCode())) viewport.altComboPressed();
            // While flying, the flying keys (W A S D, Space, Shift, Ctrl) belong to flight: no shortcut may take them.
            if (viewport.isFlying() && viewport.isFlyKey(e.getCode())) return;
            // The brush popup's own keys (mode letters, size, strength) while it is open.
            if (viewport.brushPopupKey(e)) {
                e.consume();
                return;
            }
            if (startScreen.isVisible()) {
                if (e.getCode() == KeyCode.ESCAPE) startScreen.close();
                e.consume();
                return;
            }
            // The active plugin tool sees plain keys first (R to turn, Esc to cancel…); then keys that pick plugin tools.
            if (viewport.pluginToolKey(e) || pluginToolShortcut(e)) {
                e.consume();
                return;
            }
            if (boundKey(e, scene)) e.consume();
        });
        applyAccelerators();
    }

    /** Shuffle mode (random blocks from the hotbar) on or off. */
    private void toggleShuffle() {
        ws.shuffleProperty().set(!ws.shuffleProperty().get());
        long filled = ws.hotbar().stream().filter(java.util.Objects::nonNull).count();
        viewport.showToast(!ws.shuffleProperty().get() ? "Shuffle off"
                : filled == 0 ? "Shuffle on · add blocks to the hotbar (middle-click or drag) to mix them"
                : "Shuffle on · placing random blocks from " + filled + " hotbar slot" + (filled == 1 ? "" : "s"));
    }

    /** Replace mode (right-click swaps the aimed block) on or off. */
    private void toggleReplace() {
        boolean on = !ws.replaceProperty().get();
        ws.replaceProperty().set(on);
        viewport.showToast(on ? "Replace on · right-click swaps the aimed block for the held one · " + Keybinds.text(keys.get(Keybinds.Action.REPLACE_MODE)[0]) + " to leave"
                : "Replace off · right-click places");
    }

    /**
     * Runs the action bound to this key, if any (the file actions are scene accelerators instead). Some only apply
     * in a context: the brush keys with the brush or eraser, R-style keys not while an import is being placed (R turns
     * it). Returns whether the key was used.
     */
    private boolean boundKey(KeyEvent e, javafx.scene.Scene scene) {
        ToolKind tool = ws.toolProperty().get();
        boolean brush = tool == ToolKind.BRUSH || tool == ToolKind.ERASER;
        boolean placing = viewport.isPlacing();
        // Leave Ctrl+A etc. to a focused list (e.g. selecting every layer row).
        boolean listFocused = scene.getFocusOwner() instanceof javafx.scene.control.ListView<?>;
        for (Keybinds.Action a : Keybinds.Action.values()) {
            if (!keys.matches(a, e)) continue;
            Runnable r = switch (a) {
                case TOOL_VIEW -> () -> ws.toolProperty().set(ToolKind.VIEW);
                case TOOL_SELECT -> () -> ws.toolProperty().set(ToolKind.SELECT);
                case TOOL_BUILD -> ws::toggleBuild;
                case TOOL_MOVE -> () -> ws.toolProperty().set(ToolKind.MOVE);
                case TOOL_ROTATE -> () -> ws.toolProperty().set(ToolKind.ROTATE);
                case TOOL_BRUSH -> () -> ws.toolProperty().set(ToolKind.BRUSH);
                case TOOL_ERASER -> () -> ws.toolProperty().set(ToolKind.ERASER);
                case SHUFFLE -> this::toggleShuffle;
                case REPLACE_MODE -> placing ? null : this::toggleReplace;
                case CLEAR_HOTBAR -> () -> {
                    ws.clearHotbar();
                    viewport.showToast("Hotbar cleared");
                };
                case COMMAND_BAR -> placing ? null : viewport::openCommandBar;
                case SELECT_BY_TYPE -> viewport::openSelectByTypeAtAim;
                case SELECT_ALL -> listFocused ? null : viewport::selectAllInActive;
                case DESELECT -> viewport::deselectBlocks;
                case COPY_TO_LAYER -> viewport::copySelectionToNewLayer;
                case MOVE_TO_LAYER -> viewport::moveSelectionToNewLayer;
                case FILL_SELECTION -> viewport::replaceSelectionWithHeld;
                case NEW_LAYER -> layers::newLayer;
                case RENAME_LAYER -> layers::renameActive;
                case DUPLICATE_LAYERS -> this::duplicateLayers;
                case MERGE_LAYERS -> this::mergeLayers;
                case DELETE_LAYERS -> this::deleteLayers;
                case HIDE_LAYERS -> () -> toggleLayers("hide", l -> !l.visible(), (l, v) -> l.setVisible(!v));
                case GHOST_LAYERS -> () -> toggleLayers("ghost", Layer::ghost, Layer::setGhost);
                case SHOW_ALL_LAYERS -> this::showAllLayers;
                case LOCK_LAYERS -> () -> toggleLayers("lock", Layer::locked, Layer::setLocked);
                case LAYER_BELOW -> () -> stepActiveLayer(-1);
                case LAYER_ABOVE -> () -> stepActiveLayer(1);
                case BRUSH_SMALLER -> brush ? () -> viewport.stepBrush(-1) : null;
                case BRUSH_BIGGER -> brush ? () -> viewport.stepBrush(1) : null;
                case BRUSH_WEAKER -> brush ? () -> viewport.stepBrushStrength(-1) : null;
                case BRUSH_STRONGER -> brush ? () -> viewport.stepBrushStrength(1) : null;
                case BRUSH_MODE_1, BRUSH_MODE_2, BRUSH_MODE_3, BRUSH_MODE_4, BRUSH_MODE_5, BRUSH_MODE_6, BRUSH_MODE_7, BRUSH_MODE_8,
                     BRUSH_MODE_9, BRUSH_MODE_10 -> brush ? () -> viewport.setBrushMode(a.ordinal() - Keybinds.Action.BRUSH_MODE_1.ordinal()) : null;
                case HOTBAR_1, HOTBAR_2, HOTBAR_3, HOTBAR_4, HOTBAR_5, HOTBAR_6, HOTBAR_7, HOTBAR_8, HOTBAR_9 -> () -> {
                    int slot = a.ordinal() - Keybinds.Action.HOTBAR_1.ordinal();
                    // Over a block in the palette the key fills that slot, like Minecraft's creative inventory.
                    io.blockdesigner.core.model.BlockState over = palette.hoveredBlock();
                    if (over != null) {
                        ws.putInHotbar(slot, over);
                        viewport.showToast(BlockInfoHud.name(ws.assets(), over) + " → hotbar slot " + (slot + 1));
                    } else {
                        ws.selectHotbarSlot(slot);
                    }
                };
                case VIEW_FRONT, VIEW_BACK, VIEW_RIGHT, VIEW_LEFT, VIEW_TOP, VIEW_BOTTOM, VIEW_OPPOSITE, VIEW_ORTHO_TOGGLE,
                     ORBIT_LEFT, ORBIT_RIGHT, ORBIT_UP, ORBIT_DOWN, FRAME_ACTIVE -> () -> viewport.cameraKey(a);
                case SHORTCUTS -> viewport::toggleShortcuts;
                case KEY_HINTS -> viewport::toggleKeyHints;
                case VIEWPORT_SETTINGS -> viewport::toggleSettings;
                case FRAME -> viewport::frameSelectionOrLayer;
                case GRID -> viewport::toggleGrid;
                case PERSPECTIVE -> () -> viewport.setOrtho(false);
                case ORTHOGRAPHIC -> () -> viewport.setOrtho(true);
                // Scene accelerators (see applyAccelerators).
                default -> null;
            };
            if (r == null) continue;
            r.run();
            return true;
        }
        return false;
    }

    private List<Layer> targetLayers() {
        return ws.nudgeTargets();
    }

    /** H / Shift+H / L: hide, ghost or lock the selected layers; if they all already are, undo that. */
    private void toggleLayers(String what, java.util.function.Predicate<Layer> is, java.util.function.BiConsumer<Layer, Boolean> set) {
        List<Layer> ls = targetLayers();
        if (ls.isEmpty()) {
            viewport.showToast("Select a layer first");
            return;
        }
        boolean on = !ls.stream().allMatch(is);
        String verb = switch (what) {
            case "hide" -> on ? "Hide" : "Show";
            case "ghost" -> on ? "Ghost" : "Unghost";
            default -> on ? "Lock" : "Unlock";
        };
        String label = verb + (ls.size() == 1 ? " " + ls.getFirst().name() : " " + ls.size() + " layers");
        ws.editor().undoStack().beginGroup(label);
        try {
            for (Layer l : ls) ws.editor().modifyLayer(l, label, null, x -> set.accept(x, on));
        } finally {
            ws.editor().undoStack().endGroup();
        }
        viewport.showToast(label);
    }

    /** Alt+H: every layer visible again. */
    private void showAllLayers() {
        List<Layer> hidden = ws.scene().layers().stream().filter(l -> !l.visible()).toList();
        if (hidden.isEmpty()) {
            viewport.showToast("Every layer is already visible");
            return;
        }
        ws.editor().undoStack().beginGroup("Show all layers");
        try {
            for (Layer l : hidden) ws.editor().modifyLayer(l, "Show all layers", null, x -> x.setVisible(true));
        } finally {
            ws.editor().undoStack().endGroup();
        }
        viewport.showToast("Showing " + hidden.size() + " hidden layer" + (hidden.size() == 1 ? "" : "s"));
    }

    /** Ctrl+D: copies of the selected layers, each just above its original. */
    private void duplicateLayers() {
        List<Layer> ls = targetLayers();
        if (ls.isEmpty()) return;
        List<Layer> made = new java.util.ArrayList<>();
        ws.editor().undoStack().beginGroup("Duplicate layers");
        try {
            for (Layer l : ls) {
                Layer copy = l.duplicate(l.name() + " copy");
                ws.editor().addLayer(copy, ws.scene().indexOf(l) + 1);
                made.add(copy);
            }
        } finally {
            ws.editor().undoStack().endGroup();
        }
        ws.scene().setActive(made.getLast());
        ws.selectedLayers().setAll(made);
        viewport.showToast(made.size() == 1 ? "Duplicated as " + made.getFirst().name() : "Duplicated " + made.size() + " layers");
    }

    /** Ctrl+M: merges the selected layers into the lowest, or the active layer into the one below. */
    private void mergeLayers() {
        List<Layer> sel = ws.scene().layers().stream().filter(ws.selectedLayers()::contains).toList();
        if (sel.size() >= 2) {
            Layer bottom = sel.getFirst();
            ws.editor().undoStack().beginGroup("Merge layers");
            try {
                for (int i = sel.size() - 1; i >= 1; i--) ws.editor().mergeDown(sel.get(i), bottom);
            } finally {
                ws.editor().undoStack().endGroup();
            }
            viewport.showToast("Merged " + sel.size() + " layers into " + bottom.name());
            return;
        }
        Layer a = ws.activeLayerProperty().get();
        int idx = a == null ? -1 : ws.scene().indexOf(a);
        if (idx <= 0) {
            viewport.showToast("Nothing below to merge into");
            return;
        }
        Layer below = ws.scene().layers().get(idx - 1);
        ws.editor().mergeDown(a, below);
        viewport.showToast("Merged into " + below.name());
    }

    /** Shift+Delete: removes the selected layers (undo brings them back). */
    private void deleteLayers() {
        List<Layer> ls = targetLayers();
        if (ls.isEmpty()) return;
        ws.editor().undoStack().beginGroup(ls.size() == 1 ? "Delete " + ls.getFirst().name() : "Delete layers");
        try {
            for (Layer l : ls) ws.editor().removeLayer(l);
        } finally {
            ws.editor().undoStack().endGroup();
        }
        viewport.showToast((ls.size() == 1 ? "Deleted " + ls.getFirst().name() : "Deleted " + ls.size() + " layers") + " · " + Keybinds.keyOf(Keybinds.Action.UNDO) + " to undo");
    }

    /** [ and ]: the layer below / above becomes the active one. */
    private void stepActiveLayer(int dir) {
        List<Layer> all = ws.scene().layers();
        if (all.isEmpty()) return;
        Layer a = ws.activeLayerProperty().get();
        int i = a == null ? (dir > 0 ? -1 : all.size()) : ws.scene().indexOf(a);
        int j = Math.clamp(i + dir, 0, all.size() - 1);
        Layer l = all.get(j);
        ws.scene().setActive(l);
        ws.selectedLayers().setAll(l);
        viewport.showToast("Active layer: " + l.name());
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
                else if (Schematics.isSupported(f.toPath()) || plugins.importerFor(f.toPath()).isPresent()) importFile(f.toPath());
            }
            e.setDropCompleted(true);
        });
    }
}
