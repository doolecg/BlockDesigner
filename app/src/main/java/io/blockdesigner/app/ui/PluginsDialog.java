package io.blockdesigner.app.ui;

import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.plugin.PluginApi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Lists installed plugins: switch them on and off, install a jar, open the folder, see errors and logs. */
final class PluginsDialog extends Dialog<Void> {
    private final PluginManager plugins;
    private final VBox rows = new VBox(8);

    PluginsDialog(Window owner, PluginManager plugins, boolean dark) {
        this.plugins = plugins;
        initOwner(owner);
        setTitle("Plugins");
        setResizable(true);
        var dp = getDialogPane();
        dp.getStylesheets().add(PluginsDialog.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        dp.getStyleClass().addAll("app-root", dark ? "dark" : "light");

        Label title = new Label("Plugins", FormatIcons.tile(FormatIcons.Kind.PLUGIN, 34));
        title.getStyleClass().add("export-title");
        title.setGraphicTextGap(12);
        Label warn = new Label("Plugins are programs made by other people and run with the same access as BlockDesigner. "
                + "Only install plugins you trust. Drop .jar files into the plugins folder, or use Install.");
        warn.setWrapText(true);
        warn.getStyleClass().add("plugin-warning");

        Button install = new Button("Install…", new FontIcon(Feather.PLUS));
        install.getStyleClass().add("accent");
        install.setOnAction(e -> install());
        Button folder = new Button("Open folder", new FontIcon(Feather.FOLDER));
        folder.setOnAction(e -> openFolder());
        Button reload = new Button("Reload", new FontIcon(Feather.REFRESH_CW));
        reload.setTooltip(new Tooltip("Rescan the folder and restart every enabled plugin"));
        reload.setOnAction(e -> {
            plugins.loadAll();
            rebuild();
        });
        Label api = new Label("Plugin API " + PluginApi.VERSION);
        api.getStyleClass().add("plugin-meta");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox tools = new HBox(8, install, folder, reload, sp, api);
        tools.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("export-cards-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox body = new VBox(12, title, warn, tools, scroll);
        body.setPadding(new Insets(4));
        body.setPrefSize(640, 520);
        dp.setContent(body);
        dp.getButtonTypes().add(ButtonType.CLOSE);
        rebuild();
    }

    private void rebuild() {
        rows.getChildren().clear();
        var all = plugins.plugins();
        if (all.isEmpty() && plugins.brokenJars().isEmpty()) {
            Label none = new Label("No plugins installed yet.\nPlugins go in " + plugins.folder()
                    + "\n\nMaking one? See PLUGINS.md in the BlockDesigner repository and the hello-plugin example.");
            none.setWrapText(true);
            none.getStyleClass().add("plugin-meta");
            rows.getChildren().add(none);
        }
        for (PluginManager.Plugin p : all) rows.getChildren().add(row(p));
        plugins.brokenJars().forEach((jar, why) -> {
            Label name = new Label(jar);
            name.getStyleClass().add("plugin-name");
            Label err = new Label(why);
            err.setWrapText(true);
            err.getStyleClass().add("plugin-error");
            VBox box = new VBox(3, name, err);
            box.getStyleClass().add("plugin-row");
            rows.getChildren().add(box);
        });
    }

    private VBox row(PluginManager.Plugin p) {
        var info = p.info();
        CheckBox on = new CheckBox();
        on.setSelected(p.state() == PluginManager.State.ENABLED);
        on.setDisable(p.state() == PluginManager.State.INCOMPATIBLE);
        on.setTooltip(new Tooltip("Enable or disable"));
        on.setOnAction(e -> {
            plugins.setEnabled(p, on.isSelected());
            rebuild();
        });
        Label name = new Label(info.name() + (info.version().isBlank() ? "" : "  " + info.version()));
        name.getStyleClass().add("plugin-name");
        Label meta = new Label((info.author().isBlank() ? "" : "by " + info.author() + " · ") + stateText(p) + " · " + p.jar().getFileName());
        meta.getStyleClass().add("plugin-meta");
        Label desc = new Label(info.description());
        desc.setWrapText(true);
        desc.setManaged(!info.description().isBlank());
        Button remove = new Button(null, new FontIcon(Feather.TRASH_2));
        remove.getStyleClass().add("flat");
        remove.setTooltip(new Tooltip("Uninstall (deletes the jar)"));
        remove.setOnAction(e -> uninstall(p));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        VBox text = new VBox(2, name, meta, desc);
        HBox head = new HBox(10, on, text, sp, remove);
        head.setAlignment(Pos.TOP_LEFT);
        VBox box = new VBox(6, head);
        if (p.error() != null) {
            Label err = new Label(p.error());
            err.setWrapText(true);
            err.getStyleClass().add("plugin-error");
            box.getChildren().add(err);
        }
        List<String> log = p.log();
        if (!log.isEmpty()) {
            TextArea t = new TextArea(String.join("\n", log));
            t.setEditable(false);
            t.setPrefRowCount(Math.min(6, log.size()));
            t.getStyleClass().add("plugin-log");
            TitledPane lp = new TitledPane("Log (" + log.size() + ")", t);
            lp.setExpanded(p.state() == PluginManager.State.FAILED);
            box.getChildren().add(lp);
        }
        box.getStyleClass().add("plugin-row");
        return box;
    }

    private static String stateText(PluginManager.Plugin p) {
        return switch (p.state()) {
            case ENABLED -> "on · " + p.contributions();
            case DISABLED -> "off";
            case FAILED -> "failed to start";
            case INCOMPATIBLE -> "needs a newer BlockDesigner";
        };
    }

    private void install() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Install plugin");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("BlockDesigner plugin (*.jar)", "*.jar"));
        File f = fc.showOpenDialog(getDialogPane().getScene().getWindow());
        if (f == null) return;
        try {
            var info = PluginManager.readDescriptor(f.toPath());
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(getDialogPane().getScene().getWindow());
            confirm.setHeaderText("Install " + info.name() + "?");
            confirm.setContentText((info.author().isBlank() ? "Unknown author" : "By " + info.author()) + "\n\n" + info.description()
                    + "\n\nPlugins run with full access to your computer. Only continue if you trust where this came from.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            var p = plugins.install(f.toPath());
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

    private void alert(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setHeaderText(header);
        a.setContentText(msg);
        a.showAndWait();
    }
}
