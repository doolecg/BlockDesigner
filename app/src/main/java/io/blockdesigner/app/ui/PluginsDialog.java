package io.blockdesigner.app.ui;

import io.blockdesigner.app.plugins.PluginCatalog;
import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.app.plugins.PluginUpdater;
import io.blockdesigner.plugin.PluginApi;
import io.blockdesigner.plugin.ui.Banner;
import io.blockdesigner.plugin.ui.Controls;
import io.blockdesigner.plugin.ui.EmptyState;
import io.blockdesigner.plugin.ui.Icon;
import io.blockdesigner.plugin.ui.Section;
import io.blockdesigner.plugin.ui.StatusBadge;
import io.blockdesigner.plugin.ui.Theme;
import io.blockdesigner.plugin.ui.Tone;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.css.PseudoClass;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Plugins window: the installed plugins (turn them on and off, their settings, uninstall, errors and logs), the
 * BlockDesigner team's plugins the user doesn't have yet (installed with one click from GitHub), installing a jar, the
 * plugins folder and updates.
 */
final class PluginsDialog extends Dialog<Void> {
    /** Plugin updates: whether they happen by themselves, and checking now. */
    interface Updates {
        boolean auto();

        void setAuto(boolean on);

        /** Checks and installs updates in the background; {@code done} gets a summary on the JavaFX thread. */
        void checkNow(java.util.function.Consumer<String> done);
    }

    private final PluginManager plugins;
    private final PluginUpdater updater = new PluginUpdater();
    private final VBox installed = new VBox(Theme.SM);
    private final VBox suggested = new VBox(Theme.SM);
    private final Label installedCount = Controls.caption("");
    private final Banner banner = new Banner();
    private static final PseudoClass DROP = PseudoClass.getPseudoClass("drop-target");
    /** Suggestions downloading now, by plugin id: their rows stay busy through a rebuild. */
    private final Set<String> installing = new HashSet<>();
    /** Opens a plugin's settings (null: no settings buttons, such as when the Settings window opened this one). */
    private Consumer<PluginManager.Plugin> openSettings;

    PluginsDialog(Window owner, PluginManager plugins, boolean dark, Updates updates) {
        this.plugins = plugins;
        initOwner(owner);
        setTitle("Plugins");
        setResizable(true);
        var dp = getDialogPane();
        Dialogs.style(dp, dark);
        dp.getStyleClass().add("plugins-dialog");

        Label title = new Label("Plugins", FormatIcons.tile(FormatIcons.Kind.PLUGIN, 34));
        title.getStyleClass().add("export-title");
        title.setGraphicTextGap(12);
        Label api = Controls.caption("Plugin API " + PluginApi.VERSION);
        api.setMinWidth(Region.USE_PREF_SIZE);
        api.setTooltip(new Tooltip("Plugins made for this API version or an older one run in this BlockDesigner"));
        HBox head = new HBox(12, title, Controls.spacer(), api);
        head.setAlignment(Pos.CENTER_LEFT);
        Label intro = Controls.hint("Plugins add tools, panels, importers and exporters. They run with the same access as "
                + "BlockDesigner, so only install plugins you trust.");

        Button folder = new Button("Open folder", new FontIcon(Feather.FOLDER));
        folder.setTooltip(new Tooltip("The plugins folder: .jar files dropped in it load on the next start or Reload"));
        folder.setOnAction(e -> openFolder());
        Button reload = new Button("Reload", new FontIcon(Feather.REFRESH_CW));
        reload.setTooltip(new Tooltip("Rescan the folder and restart every enabled plugin"));
        reload.setOnAction(e -> {
            plugins.loadAll();
            rebuild();
        });
        Button check = new Button("Check for updates", new FontIcon(Feather.DOWNLOAD_CLOUD));
        check.setTooltip(new Tooltip("Update every plugin that links its release source to its newest release, now"));
        HBox tools = new HBox(Theme.SM, folder, reload, Controls.spacer(), check);
        tools.setAlignment(Pos.CENTER_LEFT);

        CheckBox auto = new CheckBox("Update plugins automatically");
        auto.setSelected(updates.auto());
        auto.setTooltip(new Tooltip("At startup, plugins that link their release source update to their newest release"));
        auto.setOnAction(e -> updates.setAuto(auto.isSelected()));
        auto.setMinWidth(Region.USE_PREF_SIZE);
        Label checked = Controls.hint("");
        checked.setMinWidth(0);
        HBox.setHgrow(checked, Priority.ALWAYS);
        check.setOnAction(e -> {
            Controls.busy(check, true);
            checked.setText("Checking…");
            updates.checkNow(summary -> {
                Controls.busy(check, false);
                checked.setText(summary);
                rebuild();
            });
        });
        HBox updateRow = new HBox(Theme.MD, auto, checked);
        updateRow.setAlignment(Pos.CENTER_LEFT);

        Section installedSection = new Section("Installed").actions(installedCount);
        installedSection.add(installed);
        Section suggestedSection = new Section("Suggested to install");
        suggestedSection.add(Controls.hint("Made by the BlockDesigner team. Install downloads the newest release from GitHub."),
                suggested);
        VBox list = new VBox(Theme.XL, installedSection, suggestedSection);
        list.setPadding(new Insets(2, 14, 8, 2));
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("export-cards-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Node drop = dropBox();
        VBox body = Theme.attach(new VBox(Theme.MD, head, intro, tools, updateRow, drop, banner, scroll));
        body.setPadding(new Insets(4));
        acceptJars(body, drop);
        dp.setContent(body);
        dp.setPrefSize(760, 720);
        dp.setMinSize(560, 480);
        dp.getButtonTypes().add(ButtonType.CLOSE);
        rebuild();
    }

    /** Shows a settings button on the plugins that have settings; {@code open} opens their page. */
    void onSettings(Consumer<PluginManager.Plugin> open) {
        openSettings = open;
        rebuild();
    }

    private void rebuild() {
        installed.getChildren().clear();
        var all = plugins.plugins();
        int count = all.size() + plugins.brokenJars().size();
        installedCount.setText(count == 0 ? "" : count == 1 ? "1 plugin" : count + " plugins");
        if (count == 0) {
            installed.getChildren().add(new EmptyState(Icon.DOWNLOAD, "No plugins installed yet.")
                    .hint("Drop a plugin .jar in the box above, or install one of the suggestions below. Making one? See "
                            + "PLUGINS.md in the BlockDesigner repository."));
        }
        for (PluginManager.Plugin p : all) installed.getChildren().add(row(p));
        plugins.brokenJars().forEach((jar, why) -> installed.getChildren().add(brokenRow(jar, why)));

        suggested.getChildren().clear();
        List<PluginCatalog.Entry> offer = PluginCatalog.suggestions(plugins);
        if (offer.isEmpty()) suggested.getChildren().add(Controls.hint("You have every suggested plugin."));
        for (PluginCatalog.Entry e : offer) suggested.getChildren().add(suggestionRow(e));
    }

    // ---- rows ---------------------------------------------------------------------------------------------

    /** An installed plugin: icon, name, version and status; what it does; who made it and how it updates; actions. */
    private VBox row(PluginManager.Plugin p) {
        var info = p.info();
        List<Node> actions = new ArrayList<>();
        if (openSettings != null && p.state() == PluginManager.State.ENABLED && p.settingsOptions() != null
                && !p.settingsOptions().isEmpty()) {
            Button gear = iconButton(Feather.SETTINGS, info.name() + " settings (in the Settings window)");
            gear.setOnAction(e -> openSettings.accept(p));
            actions.add(gear);
        }
        boolean on = p.state() == PluginManager.State.ENABLED || p.state() == PluginManager.State.FAILED;
        Button toggle = new Button(on ? "Turn off" : "Turn on", new FontIcon(Feather.POWER));
        toggle.setTooltip(new Tooltip(on ? "Disable " + info.name() + " (it stays installed)" : "Enable " + info.name()));
        toggle.setDisable(p.state() == PluginManager.State.INCOMPATIBLE);
        toggle.setOnAction(e -> {
            plugins.setEnabled(p, !on);
            rebuild();
        });
        actions.add(toggle);
        Button remove = iconButton(Feather.TRASH_2, "Uninstall " + info.name() + " (deletes its jar)");
        remove.setOnAction(e -> uninstall(p));
        actions.add(remove);

        String meta = String.join(" · ", nonBlank(info.author().isBlank() ? "" : "by " + info.author(),
                p.state() == PluginManager.State.ENABLED ? p.contributions() : "",
                PluginUpdater.describe(info), p.jar().getFileName().toString()));
        VBox box = card(ToolIcons.plugin(pluginIcon(p), 20), info.name(), info.version(), badge(p), info.description(), meta, actions);
        if (p.error() != null) box.getChildren().add(indent(error(p.error())));
        List<String> log = p.log();
        if (!log.isEmpty()) {
            TextArea t = new TextArea(String.join("\n", log));
            t.setEditable(false);
            t.setPrefRowCount(Math.min(6, log.size()));
            t.getStyleClass().add("plugin-log");
            Controls.show(t, p.state() == PluginManager.State.FAILED);
            Hyperlink more = Controls.link("", null);
            Runnable label = () -> more.setText((t.isVisible() ? "Hide log" : "Show log") + " (" + log.size() + ")");
            label.run();
            more.setOnAction(e -> {
                more.setVisited(false);
                Controls.show(t, !t.isVisible());
                label.run();
            });
            box.getChildren().addAll(indent(more), indent(t));
        }
        return box;
    }

    /** A jar in the folder that isn't a plugin BlockDesigner can load. */
    private VBox brokenRow(String jar, String why) {
        Button folder = iconButton(Feather.FOLDER, "Open the plugins folder to remove or replace " + jar);
        folder.setOnAction(e -> openFolder());
        VBox box = card(FormatIcons.icon(FormatIcons.Kind.PLUGIN, 20), jar, "", new StatusBadge(Tone.DANGER, "Couldn't load"),
                "", "", List.of(folder));
        box.getChildren().add(indent(error(why)));
        return box;
    }

    /** A plugin to install: what it does, its GitHub page and Install. */
    private VBox suggestionRow(PluginCatalog.Entry e) {
        boolean busy = installing.contains(e.id());
        Button install = new Button("Install", new FontIcon(Feather.DOWNLOAD));
        install.setTooltip(new Tooltip("Download " + e.name() + "'s newest release from GitHub and install it"));
        install.setOnAction(x -> installSuggested(e));
        if (busy) Controls.busy(install, true);
        Hyperlink page = Controls.link("github.com/" + e.repo(), () -> browse(e.page()));
        page.setTooltip(new Tooltip("Open " + e.page() + " in your browser"));
        VBox box = card(FormatIcons.icon(FormatIcons.Kind.PLUGIN, 20), e.name(), "",
                busy ? new StatusBadge(Tone.ACCENT, "Downloading…") : null, e.description(), "", List.of(install));
        box.getChildren().add(indent(page));
        return box;
    }

    /**
     * The card every row is drawn on: icon, bold name, version and a status badge on the first line with the actions
     * on the right; the description and a muted meta line below, lined up with the name.
     */
    private static VBox card(Node icon, String name, String version, StatusBadge badge, String description, String meta,
                             List<? extends Node> actions) {
        Label n = new Label(name);
        n.getStyleClass().add("plugin-name");
        n.setMinWidth(0);
        HBox top = new HBox(8, iconSlot(icon), n);
        if (version != null && !version.isBlank()) {
            Label v = Controls.caption(version);
            v.setMinWidth(Region.USE_PREF_SIZE);
            top.getChildren().add(v);
        }
        if (badge != null) {
            badge.setMinWidth(Region.USE_PREF_SIZE);
            top.getChildren().add(badge);
        }
        HBox buttons = new HBox(6);
        buttons.getChildren().addAll(actions);
        for (Node a : actions) a.getStyleClass().add("small");
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setMinWidth(Region.USE_PREF_SIZE);
        top.getChildren().addAll(Controls.spacer(), buttons);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(4, top);
        if (description != null && !description.isBlank()) {
            Label d = new Label(description);
            d.setWrapText(true);
            d.setMinHeight(Region.USE_PREF_SIZE);
            d.getStyleClass().add("plugin-description");
            box.getChildren().add(indent(d));
        }
        if (meta != null && !meta.isBlank()) {
            Label m = Controls.caption(meta);
            m.setWrapText(true);
            m.setMinHeight(Region.USE_PREF_SIZE);
            box.getChildren().add(indent(m));
        }
        box.getStyleClass().add("plugin-row");
        return box;
    }

    /** The row's icon in a fixed 24 px column, so names line up whatever the icon. */
    private static Node iconSlot(Node icon) {
        HBox slot = new HBox(icon);
        slot.setAlignment(Pos.CENTER);
        slot.setMinSize(24, 24);
        slot.setPrefSize(24, 24);
        slot.setMaxSize(24, 24);
        return slot;
    }

    /** Lines a node up with the row's name, past the icon column. */
    private static <N extends Node> N indent(N n) {
        VBox.setMargin(n, new Insets(0, 0, 0, 32));
        return n;
    }

    private static Label error(String text) {
        Label err = new Label(text);
        err.setWrapText(true);
        err.setMinHeight(Region.USE_PREF_SIZE);
        err.getStyleClass().add("plugin-error");
        return err;
    }

    private static Button iconButton(Feather icon, String tooltip) {
        Button b = new Button(null, new FontIcon(icon));
        b.getStyleClass().add("flat");
        b.setTooltip(new Tooltip(tooltip));
        b.setAccessibleText(tooltip);
        return b;
    }

    private static StatusBadge badge(PluginManager.Plugin p) {
        return switch (p.state()) {
            case ENABLED -> new StatusBadge(Tone.SUCCESS, "On");
            case DISABLED -> new StatusBadge(Tone.NEUTRAL, "Off");
            case FAILED -> new StatusBadge(Tone.DANGER, "Failed to start");
            case INCOMPATIBLE -> new StatusBadge(Tone.WARNING, "Needs a newer BlockDesigner");
        };
    }

    private static String[] nonBlank(String... parts) {
        return Arrays.stream(parts).filter(s -> s != null && !s.isBlank()).toArray(String[]::new);
    }

    /** The plugin's first panel icon, so the row matches its tab; null for the puzzle piece. */
    private String pluginIcon(PluginManager.Plugin p) {
        for (var panel : plugins.panels()) if (panel.plugin() == p) return panel.panel().icon();
        return null;
    }

    // ---- actions ------------------------------------------------------------------------------------------

    /**
     * Downloads a suggestion's newest release in the background (the same download and checks as plugin updates) and
     * installs it; the banner says how it went, with Retry when it failed.
     */
    private void installSuggested(PluginCatalog.Entry e) {
        if (!installing.add(e.id())) return;
        banner.hide();
        rebuild();
        Thread.ofVirtual().name("plugin-install").start(() -> {
            PluginUpdater.Fetched got = null;
            Exception failure = null;
            try {
                got = updater.fetch(e.info());
                if (got == null) failure = new IOException("github.com/" + e.repo() + " has no release to install");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = ex;
            } catch (Exception ex) {
                failure = ex;
            }
            PluginUpdater.Fetched fetched = got;
            Exception problem = failure;
            Platform.runLater(() -> {
                installing.remove(e.id());
                if (problem != null) {
                    failed(e, problem);
                } else {
                    try {
                        var p = plugins.install(fetched.jar());
                        plugins.log(p, "Installed " + fetched.info().version() + " from " + fetched.release().page());
                        switch (p.state()) {
                            case FAILED -> banner.show(Tone.DANGER, e.name() + " was installed but failed to start. Its log is in its row.");
                            case INCOMPATIBLE -> banner.show(Tone.WARNING, e.name() + " was installed but needs a newer BlockDesigner.");
                            default -> banner.show(Tone.SUCCESS, "Installed " + e.name() + " " + p.info().version() + ". It's on and ready to use.");
                        }
                    } catch (IOException | RuntimeException ex) {
                        failed(e, ex);
                    }
                }
                rebuild();
            });
        });
    }

    private void failed(PluginCatalog.Entry e, Throwable why) {
        banner.show(Tone.DANGER, "Couldn't install " + e.name() + ": " + PluginUpdater.explain(why), "Retry", () -> installSuggested(e));
    }

    private void install() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Install plugin");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("BlockDesigner plugin (*.jar)", "*.jar"));
        File f = fc.showOpenDialog(getDialogPane().getScene().getWindow());
        if (f != null) install(f.toPath());
    }

    /** The box to drop plugin jars on, as other apps have; clicking it picks a file instead. */
    private Node dropBox() {
        Node icon = Icon.DOWNLOAD.node(26);
        Label title = new Label("Drop plugin .jar files here");
        title.getStyleClass().add("plugins-drop-title");
        Label browse = new Label("or click to choose a file");
        browse.getStyleClass().add("plugins-drop-hint");
        VBox box = new VBox(4, icon, title, browse);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("plugins-drop");
        box.setCursor(Cursor.HAND);
        box.setOnMouseClicked(e -> install());
        return box;
    }

    /**
     * .jar files dropped anywhere on the window ({@code target}) install one after another, as a jar picked from the box does;
     * {@code highlight}, the drop box, lights up while they're over it.
     */
    private void acceptJars(Node target, Node highlight) {
        target.setOnDragOver(e -> {
            if (!jars(e.getDragboard().getFiles()).isEmpty()) {
                e.acceptTransferModes(TransferMode.COPY);
                highlight.pseudoClassStateChanged(DROP, true);
            }
            e.consume();
        });
        target.setOnDragExited(e -> highlight.pseudoClassStateChanged(DROP, false));
        target.setOnDragDropped(e -> {
            highlight.pseudoClassStateChanged(DROP, false);
            List<Path> jars = jars(e.getDragboard().getFiles());
            e.setDropCompleted(!jars.isEmpty());
            e.consume();
            // After the drop finishes, so the confirmations don't block the drag.
            if (!jars.isEmpty()) Platform.runLater(() -> jars.forEach(this::install));
        });
    }

    /** The .jar files among dropped files. */
    static List<Path> jars(List<File> files) {
        if (files == null) return List.of();
        return files.stream().filter(f -> f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                .map(File::toPath).toList();
    }

    private void install(Path jar) {
        try {
            var info = PluginManager.readDescriptor(jar);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(getDialogPane().getScene().getWindow());
            confirm.setHeaderText("Install " + info.name() + "?");
            confirm.setContentText((info.author().isBlank() ? "Unknown author" : "By " + info.author()) + "\n\n" + info.description()
                    + "\n\nPlugins run with full access to your computer. Only continue if you trust where this came from.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            var p = plugins.install(jar);
            rebuild();
            if (p.state() == PluginManager.State.FAILED) alert("Installed, but it failed to start", p.error());
        } catch (IOException | RuntimeException ex) {
            alert("Could not install", ex.getMessage());
        }
    }

    private void uninstall(PluginManager.Plugin p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(getDialogPane().getScene().getWindow());
        confirm.setHeaderText("Uninstall " + p.info().name() + "?");
        confirm.setContentText("This disables it and deletes " + p.jar().getFileName() + ".");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            plugins.uninstall(p);
        } catch (IOException e) {
            // Windows keeps the jar locked until its class loader is gone; it is removed on the next start instead.
            alert("Couldn't delete the jar yet", e.getMessage() + "\nDelete it from the plugins folder after restarting.");
        }
        rebuild();
    }

    private void openFolder() {
        try {
            java.nio.file.Files.createDirectories(plugins.folder());
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(plugins.folder().toFile());
        } catch (IOException | RuntimeException e) {
            alert("Could not open the folder", plugins.folder().toString());
        }
    }

    private static void browse(String url) {
        Thread.ofVirtual().name("open-link").start(() -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
            } catch (IOException | RuntimeException ignored) {
                // No browser: the link's tooltip shows the address.
            }
        });
    }

    private void alert(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setHeaderText(header);
        a.setContentText(msg);
        a.showAndWait();
    }
}
