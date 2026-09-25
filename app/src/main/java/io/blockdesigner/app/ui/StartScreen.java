package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Blender-style startup screen over the main window: new / open / import, recent projects, the Minecraft jar used for
 * assets, and recommended places to download schematics. Can be turned off; the logo in the top bar reopens it.
 */
final class StartScreen extends StackPane {
    /** What the screen can ask the main window to do. */
    record Actions(Runnable newProject, Runnable open, Runnable importSchematic, Consumer<Path> openRecent,
                   Runnable changeAssets, Consumer<String> browse) {
    }

    private static final String[][] SITES = {
            {"Planet Minecraft", "Huge community library of builds and schematics", "https://www.planetminecraft.com/projects/tag/schematic/"},
            {"Minecraft-Schematics.com", "Schematics sorted by category, .schematic / .schem", "https://www.minecraft-schematics.com/"},
            {"CreateMod.com", "Create mod contraptions as .nbt schematics", "https://createmod.com/schematics"},
            {"GrabCraft", "Blueprints with layer-by-layer views", "https://www.grabcraft.com/"},
    };

    private final Settings settings;
    private final Actions actions;
    private final VBox card = new VBox(14);

    StartScreen(Settings settings, Actions actions) {
        this.settings = settings;
        this.actions = actions;
        getStyleClass().add("start-backdrop");
        card.getStyleClass().add("start-card");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        getChildren().add(card);
        // Clicking the dimmed backdrop (not the card) closes it.
        setOnMouseClicked(e -> {
            if (e.getTarget() == this) close();
        });
        setVisible(false);
    }

    void open() {
        rebuild();
        setVisible(true);
        requestFocus();
    }

    void close() {
        setVisible(false);
    }

    private void rebuild() {
        Label title = new Label("BlockDesigner", new FontIcon(Feather.BOX));
        title.getStyleClass().add("start-title");
        Label sub = new Label("Design Minecraft builds, then export them as .nbt, .litematic or .schem");
        sub.getStyleClass().add("start-sub");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button x = new Button(null, new FontIcon(Feather.X));
        x.getStyleClass().add("flat");
        x.setOnAction(e -> close());
        HBox header = new HBox(10, new VBox(2, title, sub), sp, x);
        header.setAlignment(Pos.TOP_LEFT);

        // Left: start actions + recent projects
        VBox start = new VBox(4, heading("Start"),
                action(Feather.FILE_PLUS, "New project", actions.newProject()),
                action(Feather.FOLDER, "Open…", actions.open()),
                action(Feather.DOWNLOAD, "Import schematic…", actions.importSchematic()));
        VBox recent = new VBox(2, heading("Recent"));
        if (settings.recentFiles.isEmpty()) {
            Label none = new Label("Nothing yet: projects and schematics you open show up here.");
            none.getStyleClass().add("start-muted");
            none.setWrapText(true);
            recent.getChildren().add(none);
        }
        for (String f : settings.recentFiles.stream().limit(10).toList()) {
            Path p = Path.of(f);
            boolean exists = Files.isRegularFile(p);
            Button b = new Button(p.getFileName().toString(), new FontIcon(f.endsWith(".bdproj") ? Feather.BOX : Feather.FILE));
            b.getStyleClass().addAll("flat", "start-recent");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setTooltip(new Tooltip(exists ? f : f + "\n(missing)"));
            b.setDisable(!exists);
            b.setOnAction(e -> {
                close();
                actions.openRecent().accept(p);
            });
            recent.getChildren().add(b);
        }
        VBox left = new VBox(16, start, recent);
        left.setPrefWidth(300);

        // Right: Minecraft jar + download sites
        String jar = settings.gameJar == null || settings.gameJar.isBlank() ? "Not set: pick a Minecraft client jar" : settings.gameJar;
        Label jarPath = new Label(jar);
        jarPath.getStyleClass().add("start-path");
        jarPath.setWrapText(true);
        Label instance = new Label(settings.instanceName == null || settings.instanceName.isBlank()
                ? "No launcher instance (vanilla assets)" : "Instance: " + settings.instanceName);
        instance.getStyleClass().add("start-muted");
        Button change = new Button("Change…", new FontIcon(Feather.SETTINGS));
        change.setOnAction(e -> {
            close();
            actions.changeAssets().run();
        });
        VBox assets = new VBox(4, heading("Minecraft jar"), jarPath, instance, change);

        VBox sites = new VBox(6, heading("Get schematics"));
        for (String[] s : SITES) {
            Hyperlink link = new Hyperlink(s[0]);
            link.setOnAction(e -> actions.browse().accept(s[2]));
            link.setTooltip(new Tooltip(s[2]));
            Label d = new Label(s[1]);
            d.getStyleClass().add("start-muted");
            sites.getChildren().add(new VBox(0, link, d));
        }
        VBox right = new VBox(18, assets, sites);
        right.setPrefWidth(320);

        HBox body = new HBox(32, left, right);

        CheckBox show = new CheckBox("Show this screen on startup");
        show.setSelected(settings.showStartScreen);
        show.selectedProperty().addListener((o, a, b) -> {
            settings.showStartScreen = b;
            settings.save();
        });
        Label reopen = new Label("Click the BlockDesigner logo to open it again");
        reopen.getStyleClass().add("start-muted");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        HBox footer = new HBox(12, show, sp2, reopen);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 0, 0, 0));

        card.getChildren().setAll(header, body, footer);
    }

    private static Label heading(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("viewport-settings-section");
        return l;
    }

    private Button action(Feather icon, String text, Runnable r) {
        Button b = new Button(text, new FontIcon(icon));
        b.getStyleClass().addAll("flat", "start-action");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setOnAction(e -> {
            close();
            r.run();
        });
        return b;
    }
}
