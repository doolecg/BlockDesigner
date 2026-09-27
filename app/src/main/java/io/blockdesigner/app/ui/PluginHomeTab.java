package io.blockdesigner.app.ui;

import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.plugin.ui.ActionBar;
import io.blockdesigner.plugin.ui.Banner;
import io.blockdesigner.plugin.ui.Controls;
import io.blockdesigner.plugin.ui.Icon;
import io.blockdesigner.plugin.ui.PanelScaffold;
import io.blockdesigner.plugin.ui.Section;
import io.blockdesigner.plugin.ui.StatusBadge;
import io.blockdesigner.plugin.ui.Tone;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

/**
 * A plugin's own tab on the right, one per enabled (or failed) plugin, and the only one it gets: its panels as pages
 * picked in a row along the top (the tab opens on the one used last, else the first), plus its Overview (that it is
 * running, where its settings are, everything it adds with a button to use it) behind the info button at the end of
 * that row. A plugin without panels shows just the Overview. So everything a plugin adds is in one place.
 */
final class PluginHomeTab {
    /** What the tab's buttons do, provided by the main window. */
    interface Actions {
        void runAction(PluginManager.Action a);

        void pickTool(PluginManager.Tool t);

        void openTransform(PluginManager.Transform t);

        void managePlugins();

        void disable(PluginManager.Plugin p);

        /** Opens the Settings window at the plugin's page. */
        void openSettings(PluginManager.Plugin p);

        /** The key that picks a tool, for its tooltip ("" for none). */
        String toolKey(PluginManager.Tool t);

        /** Whether this tab is on screen (selected, its side panel open, the window showing). */
        boolean isShowing(Tab tab);

        /** Opens this tab (reopening it if it was closed) and selects it. */
        void reveal(Tab tab);
    }

    static final String OVERVIEW = "overview";
    /** Below this width the page buttons leave out their icons. */
    private static final double NARROW_BAR = 340;

    final PluginManager.Plugin plugin;
    final Tab tab = new Tab();
    private final PluginManager plugins;
    private final Actions actions;
    /** Where the page each plugin's tab was left on is remembered (the settings' map). */
    private final Map<String, String> lastPages;
    private final HBox pageBar = new HBox(4);
    private final StackPane pageHolder = new StackPane();
    private final ToggleGroup pageGroup = new ToggleGroup();
    private final ToggleButton about = new ToggleButton();
    private Node overview;
    /** The plugin's panels by key, kept across refreshes so each is created once. */
    private final Map<String, PanelPage> pages = new LinkedHashMap<>();
    /** The page showing: null for the Overview. */
    private PanelPage current;

    PluginHomeTab(PluginManager.Plugin plugin, PluginManager plugins, Map<String, String> lastPages, Actions actions) {
        this.plugin = plugin;
        this.plugins = plugins;
        this.actions = actions;
        this.lastPages = lastPages;
        Label title = new Label(plugin.info().name());
        HBox head = new HBox(6, ToolIcons.plugin(icon(), 14), title);
        head.setAlignment(Pos.CENTER_LEFT);
        tab.setGraphic(head);
        tab.setTooltip(new Tooltip(plugin.info().name() + " · everything the plugin adds"));
        pageBar.getStyleClass().add("plugin-pages");
        pageBar.setAlignment(Pos.CENTER_LEFT);
        about.setGraphic(Icon.INFO.node(16));
        about.getStyleClass().addAll("flat", "plugin-page-icon");
        about.setTooltip(new Tooltip("About " + plugin.info().name() + ": what it adds, its version, turn it off"));
        about.setAccessibleText("About " + plugin.info().name());
        about.setFocusTraversable(false);
        about.setToggleGroup(pageGroup);
        about.setOnAction(e -> {
            if (!about.isSelected()) about.setSelected(true);
            select(null);
        });
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

    /** Shows the panel with this id (the plugin's own id for it); false when it has none such. */
    boolean showPanel(String panelId) {
        for (PanelPage page : pages.values()) {
            if (page.panel.panel().id().equals(panelId)) {
                select(page);
                return true;
            }
        }
        return false;
    }

    /** Shows the Overview. */
    void showOverview() {
        select(null);
    }

    private void select(PanelPage page) {
        current = page;
        for (javafx.scene.control.Toggle t : pageGroup.getToggles()) {
            if (((ToggleButton) t).getUserData() == page) pageGroup.selectToggle(t);
        }
        if (page == null) {
            about.setSelected(true);
            pageHolder.getChildren().setAll(overview);
        } else {
            page.shown();
            pageHolder.getChildren().setAll(page.holder);
        }
        if (!pages.isEmpty()) lastPages.put(plugin.info().id(), page == null ? OVERVIEW : page.panel.panel().id());
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
        pageGroup.getToggles().setAll(about);
        if (!pages.isEmpty()) {
            for (PanelPage page : pages.values()) pageBar.getChildren().add(page.button());
            pageBar.getChildren().add(Controls.spacer());
            if (hasSettings()) {
                Button gear = Controls.iconButton(Icon.SETTINGS, plugin.info().name() + " settings (in the Settings window)",
                        () -> actions.openSettings(plugin));
                gear.getStyleClass().add("plugin-page-icon");
                gear.setFocusTraversable(false);
                pageBar.getChildren().add(gear);
            }
            pageBar.getChildren().add(about);
        }
        Controls.show(pageBar, !pages.isEmpty());
        statusChanged();
        PanelPage keep;
        if (current != null && pages.containsValue(current)) keep = current;
        else if (built && current == null) keep = null; // it was showing the Overview: it stays there
        else keep = startPage();
        built = true;
        select(keep);
    }

    /** Set once the first refresh has picked the page to open on. */
    private boolean built;

    /** The page the tab opens on: the one it was left on, else its first panel (the Overview when it has none). */
    private PanelPage startPage() {
        String last = lastPages.get(plugin.info().id());
        if (OVERVIEW.equals(last)) return null;
        for (PanelPage p : pages.values()) if (p.panel.panel().id().equals(last)) return p;
        return pages.values().stream().findFirst().orElse(null);
    }

    private boolean hasSettings() {
        var o = plugin.settingsOptions();
        return o != null && !o.all().isEmpty();
    }

    /** A plugin set or cleared a page's status dot. */
    void statusChanged() {
        for (PanelPage p : pages.values()) p.updateStatus();
    }

    // ---- Overview --------------------------------------------------------------------------------------------------

    private Node overview() {
        PanelScaffold page = new PanelScaffold();
        page.getStyleClass().add("plugin-home");
        // What it adds and its settings come first; what it is and whether it runs sit at the bottom.
        if (hasSettings()) {
            page.add(new Section("Settings",
                    button("Open settings…", "Settings › " + plugin.info().name(), Feather.SETTINGS, () -> actions.openSettings(plugin)),
                    Controls.hint("Its main settings are on its page in the Settings window.")));
        }
        contributions(page);
        Section about = new Section("About", header());
        if (plugin.state() == PluginManager.State.FAILED) {
            Banner failed = new Banner();
            failed.show(Tone.DANGER, "It failed to start: " + plugin.error(), "Manage plugins…", actions::managePlugins);
            about.add(failed);
        }
        page.add(about);
        Button manage = button("Manage plugins…", "Install, update, turn on and off, and see each plugin's log", Feather.PACKAGE,
                actions::managePlugins);
        Button off = button("Turn off", "Disable " + plugin.info().name() + " (turn it back on in Manage plugins)", Feather.POWER,
                () -> actions.disable(plugin));
        page.footer(new ActionBar(manage, Controls.spacer(), off));
        return page;
    }

    private Node header() {
        var info = plugin.info();
        Label name = new Label(info.name());
        name.getStyleClass().add("bd-page-title");
        name.setMinWidth(0);
        Label version = Controls.caption(info.version().isBlank() ? "" : info.version());
        version.setMinWidth(Region.USE_PREF_SIZE);
        boolean ok = plugin.state() == PluginManager.State.ENABLED;
        StatusBadge status = new StatusBadge(ok ? Tone.SUCCESS : Tone.DANGER, ok ? "Running" : "Failed");
        HBox top = new HBox(8, ToolIcons.plugin(icon(), 20), name, version, Controls.spacer(), status);
        top.setAlignment(Pos.CENTER_LEFT);

        String by = (info.author().isBlank() ? "" : "by " + info.author() + " · ") + "plugin API " + info.api()
                + " · " + io.blockdesigner.app.plugins.PluginUpdater.describe(info);
        Label meta = Controls.caption(by);
        meta.setWrapText(true);
        meta.setMinHeight(Region.USE_PREF_SIZE);
        VBox v = new VBox(6, top);
        if (!info.description().isBlank()) {
            Label d = new Label(info.description());
            d.setWrapText(true);
            d.setMinHeight(Region.USE_PREF_SIZE);
            d.getStyleClass().add("plugin-home-description");
            v.getChildren().add(d);
        }
        v.getChildren().add(meta);
        return v;
    }

    private void contributions(PanelScaffold page) {
        int before = 0;
        List<PluginManager.Action> acts = plugins.actions().stream().filter(a -> a.plugin() == plugin).toList();
        if (!acts.isEmpty()) {
            page.add(new Section("Actions", buttons(acts, a -> button(a.action().label(), a.action().description(), Feather.PLAY,
                    () -> actions.runAction(a)))));
            before++;
        }
        List<PluginManager.Tool> tools = plugins.tools().stream().filter(t -> t.plugin() == plugin).toList();
        if (!tools.isEmpty()) {
            page.add(new Section("Tools", buttons(tools, t -> {
                String key = actions.toolKey(t);
                String tip = t.tool().description() + (key.isEmpty() ? "\nNo key (give it one in Settings › Keybinds)" : "\nKey: " + key);
                return button(t.tool().name(), tip.strip(), Feather.MOUSE_POINTER, () -> actions.pickTool(t));
            })));
            before++;
        }
        List<PluginManager.Transform> transforms = plugins.transforms().stream().filter(t -> t.plugin() == plugin).toList();
        if (!transforms.isEmpty()) {
            page.add(new Section("Transforms", buttons(transforms, t -> button(t.transform().name(), t.transform().description(),
                    Feather.SLIDERS, () -> actions.openTransform(t)))));
            before++;
        }
        List<PluginManager.Panel> panels = plugins.panels().stream().filter(p -> p.plugin() == plugin).toList();
        if (!panels.isEmpty()) {
            page.add(new Section("Panels", buttons(panels, p -> button(p.panel().title(), "Show it (also in the row along the top)",
                    Feather.SIDEBAR, () -> showPanel(p)))));
            before++;
        }
        if (!plugin.commands().isEmpty()) {
            VBox list = new VBox(6);
            for (var c : plugin.commands()) list.getChildren().add(item(c.usage().isBlank() ? "/" + c.name() : c.usage(), c.description()));
            page.add(new Section("Commands", Controls.hint("Type them in the command line."), list));
            before++;
        }
        VBox files = new VBox(6);
        for (var f : plugin.formats()) files.getChildren().add(item(f.displayName(), "Import and export · ." + String.join(", .", f.extensions())));
        for (var i : plugin.importers()) files.getChildren().add(item(i.displayName(), "Import · ." + String.join(", .", i.extensions())));
        for (var x : plugin.exporters()) files.getChildren().add(item(x.displayName(), "A card in the Export window"));
        for (var o : plugin.objectTypes()) {
            files.getChildren().add(item(o.name(), "Scene objects, listed in Layers as " + o.badge()
                    + (o.extensions().isEmpty() ? "" : " · import ." + String.join(", .", o.extensions()))));
        }
        if (!files.getChildren().isEmpty()) {
            page.add(new Section("Files and objects", files));
            before++;
        }
        if (before == 0) page.add(Controls.hint("It hasn't added anything you can use from here."));
    }

    private static <T> Node buttons(List<T> items, java.util.function.Function<T, Button> make) {
        FlowPane f = new FlowPane(8, 8);
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
        n.getStyleClass().add("bd-row-title");
        Label w = Controls.hint(what);
        return new VBox(2, n, w);
    }

    // ---- panels ----------------------------------------------------------------------------------------------------

    /** One of the plugin's panels as a page of the tab, created the first time it is shown. */
    private final class PanelPage implements io.blockdesigner.plugin.PanelContext {
        final PluginManager.Panel panel;
        /** Holds the panel's node: with the old 10 px padding for panels that don't use the kit's PanelScaffold. */
        final StackPane holder = new StackPane();
        final Label badge = new Label();
        final Region dot = new Region();
        final List<Runnable> onShown = new java.util.ArrayList<>();
        ToggleButton button;
        boolean created;

        PanelPage(PluginManager.Panel panel) {
            this.panel = panel;
            badge.getStyleClass().add("badge");
            Controls.show(badge, false);
            dot.getStyleClass().add("plugin-page-status");
            Controls.show(dot, false);
        }

        ToggleButton button() {
            Node icon = ToolIcons.plugin(panel.panel().icon(), 13);
            Label title = new Label(panel.panel().title());
            title.setMinWidth(0);
            badge.setMinWidth(Region.USE_PREF_SIZE);
            dot.setMinWidth(Region.USE_PREF_SIZE);
            // A narrow tab drops the page icons first, so the titles and badges keep their room.
            icon.visibleProperty().bind(pageBar.widthProperty().greaterThan(NARROW_BAR).or(pageBar.widthProperty().isEqualTo(0, 0.5)));
            icon.managedProperty().bind(icon.visibleProperty());
            HBox g = new HBox(5, icon, title, badge, dot);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setMinWidth(0);
            ToggleButton b = new ToggleButton(null, g);
            b.getStyleClass().add("plugin-page");
            b.setToggleGroup(pageGroup);
            b.setUserData(this);
            b.setFocusTraversable(false);
            b.setMinWidth(0);
            // A page button stays down: clicking the one showing keeps it showing.
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true);
                select(this);
            });
            button = b;
            updateStatus();
            return b;
        }

        void updateStatus() {
            var s = plugins.panelStatus(panel.plugin(), panel.panel().id()).orElse(null);
            Tone.apply(dot, s == null ? null : s.tone());
            Controls.show(dot, s != null);
            if (button != null) {
                String tip = panel.panel().title() + (s == null || s.text().isBlank() ? "" : " · " + s.text());
                button.setTooltip(new Tooltip(tip));
                button.setAccessibleText(tip);
            }
        }

        void shown() {
            if (!created) {
                created = true;
                try {
                    Node n = panel.panel().create(this);
                    holder.getChildren().setAll(n);
                    // Panels built with the kit own their padding; older ones keep the 10 px they always had.
                    holder.setPadding(n.getStyleClass().contains("bd-scaffold") ? Insets.EMPTY : new Insets(10));
                } catch (Throwable t) {
                    plugins.report(panel.plugin(), "Panel '" + panel.panel().title() + "'", t);
                    Banner err = new Banner();
                    err.show(Tone.DANGER, "This panel failed to open: " + t.getMessage());
                    holder.setPadding(new Insets(12));
                    holder.setAlignment(Pos.TOP_LEFT);
                    holder.getChildren().setAll(err);
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
            Controls.show(badge, show);
        }

        @Override
        public void reveal() {
            actions.reveal(tab);
            select(this);
        }
    }
}
