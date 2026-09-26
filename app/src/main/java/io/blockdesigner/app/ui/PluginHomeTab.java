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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A plugin's own tab on the right, one per enabled (or failed) plugin: that it is running, its settings (API 4), and
 * everything it adds, each with a button to use it. So a user can see at a glance that a plugin works and what it does,
 * even when it has no panel of its own.
 */
final class PluginHomeTab {
    /** What the tab's buttons do, provided by the main window. */
    interface Actions {
        void runAction(PluginManager.Action a);

        void pickTool(PluginManager.Tool t);

        void openTransform(PluginManager.Transform t);

        void openPanel(PluginManager.Panel p);

        void managePlugins();

        void disable(PluginManager.Plugin p);
    }

    final PluginManager.Plugin plugin;
    final Tab tab = new Tab();
    private final PluginManager plugins;
    private final Supplier<BlockState> held;
    private final Actions actions;

    PluginHomeTab(PluginManager.Plugin plugin, PluginManager plugins, Supplier<BlockState> held, Actions actions) {
        this.plugin = plugin;
        this.plugins = plugins;
        this.held = held;
        this.actions = actions;
        Label title = new Label(plugin.info().name());
        HBox head = new HBox(6, ToolIcons.plugin(icon(), 14), title);
        head.setAlignment(Pos.CENTER_LEFT);
        tab.setGraphic(head);
        tab.setTooltip(new Tooltip(plugin.info().name() + " · plugin settings and what it adds"));
        refresh();
    }

    /** The first panel's icon, so the tab matches the plugin's own; null for the puzzle piece. */
    String icon() {
        for (var p : plugins.panels()) if (p.plugin() == plugin) return p.panel().icon();
        return null;
    }

    /** Rebuilds the content (the plugin was enabled, reloaded or failed). */
    void refresh() {
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
        tab.setContent(scroll);
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

        VBox v = new VBox(6, top, line);
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
            v.getChildren().add(section("Panels", buttons(panels, p -> button(p.panel().title(), "Open its tab", Feather.SIDEBAR,
                    () -> actions.openPanel(p)))));
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
