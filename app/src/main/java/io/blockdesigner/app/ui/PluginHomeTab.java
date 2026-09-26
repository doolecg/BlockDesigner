package io.blockdesigner.app.ui;

import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.OptionValues;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A plugin's own tab on the right, one per enabled (or failed) plugin, and the only one it gets: its Overview (that it
 * is running, its settings (API 4) and everything it adds, each with a button to use it) and its panels, as pages
 * picked in a row of buttons along the top. So everything a plugin adds is in one place.
 */
final class PluginHomeTab {
    /** What the tab's buttons do, provided by the main window. */
    interface Actions {
        void runAction(PluginManager.Action a);

        void pickTool(PluginManager.Tool t);

        void openTransform(PluginManager.Transform t);

        void managePlugins();

        void disable(PluginManager.Plugin p);

        /** Whether this tab is on screen (selected, its side panel open, the window showing). */
        boolean isShowing(Tab tab);

        /** Opens this tab (reopening it if it was closed) and selects it. */
        void reveal(Tab tab);
    }

    final PluginManager.Plugin plugin;
    final Tab tab = new Tab();
    private final PluginManager plugins;
    private final Supplier<BlockState> held;
    private final Actions actions;
    private final HBox pageBar = new HBox(4);
    private final StackPane pageHolder = new StackPane();
    private final ToggleGroup pageGroup = new ToggleGroup();
    private Node overview;
    /** The plugin's panels by key, kept across refreshes so each is created once. */
    private final Map<String, PanelPage> pages = new LinkedHashMap<>();
    /** The page showing: null for the Overview. */
    private PanelPage current;

    PluginHomeTab(PluginManager.Plugin plugin, PluginManager plugins, Supplier<BlockState> held, Actions actions) {
        this.plugin = plugin;
        this.plugins = plugins;
        this.held = held;
        this.actions = actions;
        Label title = new Label(plugin.info().name());
        HBox head = new HBox(6, ToolIcons.plugin(icon(), 14), title);
        head.setAlignment(Pos.CENTER_LEFT);
        tab.setGraphic(head);
        tab.setTooltip(new Tooltip(plugin.info().name() + " · everything the plugin adds"));
        pageBar.getStyleClass().add("plugin-pages");
        pageBar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(pageHolder, Priority.ALWAYS);
        tab.setContent(new VBox(pageBar, pageHolder));
        tab.selectedProperty().addListener((o, a, selected) -> {
            if (selected) shown();
        });
        refresh();
    }

    /** The tab came into view: the page showing runs its on-shown actions (and is built on first show). */
    void shown() {
        if (current != null) current.shown();
    }

    /** Shows one of the plugin's panels. */
    void showPanel(PluginManager.Panel p) {
        PanelPage page = pages.get(p.key());
        if (page != null) select(page);
    }

    private void select(PanelPage page) {
        current = page;
        for (javafx.scene.control.Toggle t : pageGroup.getToggles()) {
            if (((ToggleButton) t).getUserData() == page) pageGroup.selectToggle(t);
        }
        if (page == null) {
            pageHolder.getChildren().setAll(overview);
        } else {
            page.shown();
            pageHolder.getChildren().setAll(page.content);
        }
    }

    /** The first panel's icon, so the tab matches the plugin's own; null for the puzzle piece. */
    String icon() {
        for (var p : plugins.panels()) if (p.plugin() == plugin) return p.panel().icon();
        return null;
    }

    /** Rebuilds the content (the plugin was enabled, reloaded or failed); panels that are still there are kept. */
    void refresh() {
        Map<String, PluginManager.Panel> now = new LinkedHashMap<>();
        for (var p : plugins.panels()) if (p.plugin() == plugin) now.put(p.key(), p);
        pages.entrySet().removeIf(e -> now.get(e.getKey()) == null || now.get(e.getKey()).panel() != e.getValue().panel.panel());
        for (var e : now.entrySet()) pages.computeIfAbsent(e.getKey(), k -> new PanelPage(e.getValue()));
        overview = overview();
        pageBar.getChildren().clear();
        pageGroup.getToggles().clear();
        if (!pages.isEmpty()) {
            pageBar.getChildren().add(pageButton("Overview", ToolIcons.plugin(null, 13), null, null));
            for (PanelPage page : pages.values()) {
                pageBar.getChildren().add(pageButton(page.panel.panel().title(), ToolIcons.plugin(page.panel.panel().icon(), 13), page.badge, page));
            }
        }
        pageBar.setVisible(!pages.isEmpty());
        pageBar.setManaged(!pages.isEmpty());
        select(current != null && pages.containsValue(current) ? current : null);
    }

    private ToggleButton pageButton(String title, Node icon, Label badge, PanelPage page) {
        HBox g = new HBox(5, icon, new Label(title));
        if (badge != null) g.getChildren().add(badge);
        g.setAlignment(Pos.CENTER_LEFT);
        ToggleButton b = new ToggleButton(null, g);
        b.getStyleClass().add("plugin-page");
        b.setToggleGroup(pageGroup);
        b.setUserData(page);
        b.setFocusTraversable(false);
        // A page button stays down: clicking the one showing keeps it showing.
        b.setOnAction(e -> {
            if (!b.isSelected()) b.setSelected(true);
            select(page);
        });
        return b;
    }

    private Node overview() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(14));
        box.getStyleClass().add("plugin-home");
        box.getChildren().add(header());
        if (plugin.state() == PluginManager.State.FAILED) {
            Label err = new Label("It failed to start: " + plugin.error());
            err.setWrapText(true);
            err.getStyleClass().add("plugin-error");
            box.getChildren().add(err);
        }
        Node settings = settings();
        if (settings != null) box.getChildren().add(settings);
        box.getChildren().addAll(contributions(), footer());
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plugin-home-scroll");
        return scroll;
    }

    private Node header() {
        var info = plugin.info();
        Label name = new Label(info.name());
        name.getStyleClass().add("plugin-home-name");
        Label version = new Label(info.version().isBlank() ? "" : info.version());
        version.getStyleClass().add("plugin-meta");
        HBox top = new HBox(8, ToolIcons.plugin(icon(), 20), name, version);
        top.setAlignment(Pos.CENTER_LEFT);

        boolean ok = plugin.state() == PluginManager.State.ENABLED;
        Label status = new Label(ok ? "Running" : "Failed");
        status.setGraphic(new Region());
        status.getGraphic().getStyleClass().add("plugin-status-dot");
        status.getStyleClass().addAll("plugin-status", ok ? "plugin-status-ok" : "plugin-status-failed");
        Label meta = new Label((info.author().isBlank() ? "" : "by " + info.author() + " · ") + "plugin API " + info.api());
        meta.getStyleClass().add("plugin-meta");
        HBox line = new HBox(8, status, meta);
        line.setAlignment(Pos.CENTER_LEFT);

        Label updates = new Label(io.blockdesigner.app.plugins.PluginUpdater.describe(info));
        updates.setGraphic(new FontIcon(io.blockdesigner.app.plugins.PluginUpdater.updatable(info) ? Feather.REFRESH_CW : Feather.DOWNLOAD));
        updates.getStyleClass().add("plugin-meta");
        VBox v = new VBox(6, top, line, updates);
        if (!info.description().isBlank()) {
            Label d = new Label(info.description());
            d.setWrapText(true);
            d.getStyleClass().add("plugin-home-description");
            v.getChildren().add(d);
        }
        return v;
    }

    private Node settings() {
        var options = plugin.settingsOptions();
        if (options == null || options.all().isEmpty()) return null;
        VBox holder = new VBox();
        Consumer<OptionValues> set = v -> plugins.setSettings(plugin, v);
        holder.getChildren().setAll(new OptionsEditor(plugin.settingsValues(), plugins.blocks(), held, set));
        Button reset = new Button("Reset to defaults", new FontIcon(Feather.ROTATE_CCW));
        reset.getStyleClass().add("flat");
        reset.setOnAction(e -> {
            OptionValues d = options.defaults();
            set.accept(d);
            holder.getChildren().setAll(new OptionsEditor(plugin.settingsValues(), plugins.blocks(), held, set));
        });
        return section("Settings", holder, reset);
    }

    private Node contributions() {
        VBox v = new VBox(12);
        List<PluginManager.Action> acts = plugins.actions().stream().filter(a -> a.plugin() == plugin).toList();
        if (!acts.isEmpty()) {
            v.getChildren().add(section("Actions", buttons(acts, a -> button(a.action().label(), a.action().description(), Feather.PLAY,
                    () -> actions.runAction(a)))));
        }
        List<PluginManager.Tool> tools = plugins.tools().stream().filter(t -> t.plugin() == plugin).toList();
        if (!tools.isEmpty()) {
            v.getChildren().add(section("Tools", buttons(tools, t -> button(t.tool().name(), t.tool().description(), Feather.MOUSE_POINTER,
                    () -> actions.pickTool(t)))));
        }
        List<PluginManager.Transform> transforms = plugins.transforms().stream().filter(t -> t.plugin() == plugin).toList();
        if (!transforms.isEmpty()) {
            v.getChildren().add(section("Transforms", buttons(transforms, t -> button(t.transform().name(), t.transform().description(),
                    Feather.SLIDERS, () -> actions.openTransform(t)))));
        }
        List<PluginManager.Panel> panels = plugins.panels().stream().filter(p -> p.plugin() == plugin).toList();
        if (!panels.isEmpty()) {
            v.getChildren().add(section("Panels", buttons(panels, p -> button(p.panel().title(), "Show it (also in the row along the top)",
                    Feather.SIDEBAR, () -> showPanel(p)))));
        }
        if (!plugin.commands().isEmpty()) {
            VBox list = new VBox(4);
            for (var c : plugin.commands()) list.getChildren().add(item(c.usage().isBlank() ? "/" + c.name() : c.usage(), c.description()));
            v.getChildren().add(section("Commands (type them in the command line)", list));
        }
        VBox files = new VBox(4);
        for (var f : plugin.formats()) files.getChildren().add(item(f.displayName(), "Import and export · ." + String.join(", .", f.extensions())));
        for (var i : plugin.importers()) files.getChildren().add(item(i.displayName(), "Import · ." + String.join(", .", i.extensions())));
        for (var x : plugin.exporters()) files.getChildren().add(item(x.displayName(), "A card in the Export window"));
        for (var o : plugin.objectTypes()) {
            files.getChildren().add(item(o.name(), "Scene objects, listed in Layers as " + o.badge()
                    + (o.extensions().isEmpty() ? "" : " · import ." + String.join(", .", o.extensions()))));
        }
        if (!files.getChildren().isEmpty()) v.getChildren().add(section("Files and objects", files));
        if (v.getChildren().isEmpty()) {
            Label none = new Label("It hasn't added anything you can use from here.");
            none.getStyleClass().add("plugin-meta");
            v.getChildren().add(none);
        }
        return v;
    }

    private Node footer() {
        Button manage = new Button("Manage plugins…", new FontIcon(Feather.SETTINGS));
        manage.setOnAction(e -> actions.managePlugins());
        Button off = new Button("Turn off", new FontIcon(Feather.POWER));
        off.setTooltip(new Tooltip("Disable " + plugin.info().name() + " (turn it back on in Manage plugins)"));
        off.setOnAction(e -> actions.disable(plugin));
        HBox h = new HBox(8, manage, off);
        h.setPadding(new Insets(6, 0, 0, 0));
        return h;
    }

    /** One of the plugin's panels as a page of the tab, created the first time it is shown. */
    private final class PanelPage implements io.blockdesigner.plugin.PanelContext {
        final PluginManager.Panel panel;
        final StackPane content = new StackPane();
        final Label badge = new Label();
        final List<Runnable> onShown = new java.util.ArrayList<>();
        boolean created;

        PanelPage(PluginManager.Panel panel) {
            this.panel = panel;
            badge.getStyleClass().add("badge");
            badge.setVisible(false);
            badge.setManaged(false);
        }

        void shown() {
            if (!created) {
                created = true;
                try {
                    content.getChildren().setAll(panel.panel().create(this));
                } catch (Throwable t) {
                    plugins.report(panel.plugin(), "Panel '" + panel.panel().title() + "'", t);
                    Label err = new Label("This panel failed to open: " + t.getMessage());
                    err.setWrapText(true);
                    err.getStyleClass().add("plugin-error");
                    err.setPadding(new Insets(12));
                    content.getChildren().setAll(err);
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
            return current == this && actions.isShowing(tab);
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
            actions.reveal(tab);
            select(this);
        }
    }

    private static Node section(String title, Node content, Node... extra) {
        Label t = new Label(title.toUpperCase(java.util.Locale.ROOT));
        t.getStyleClass().add("plugin-home-section");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox head = new HBox(6, t, grow);
        head.getChildren().addAll(extra);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox v = new VBox(8, head, content);
        v.getStyleClass().add("plugin-home-card");
        return v;
    }

    private static <T> Node buttons(List<T> items, java.util.function.Function<T, Button> make) {
        FlowPane f = new FlowPane(6, 6);
        for (T t : items) f.getChildren().add(make.apply(t));
        return f;
    }

    private static Button button(String text, String tip, Feather icon, Runnable run) {
        Button b = new Button(text, new FontIcon(icon));
        if (tip != null && !tip.isBlank()) b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> run.run());
        return b;
    }

    private static Node item(String name, String what) {
        Label n = new Label(name);
        n.getStyleClass().add("plugin-home-item");
        Label w = new Label(what);
        w.setWrapText(true);
        w.getStyleClass().add("plugin-meta");
        return new VBox(1, n, w);
    }
}
