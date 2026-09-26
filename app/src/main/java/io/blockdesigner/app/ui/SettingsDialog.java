package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.util.List;

/**
 * The Settings window: General (author, start screen, Minecraft assets, plugins, updates, where settings live),
 * Appearance (colour theme, dark / light / match Windows) and Keybinds. Changes apply immediately.
 */
final class SettingsDialog extends Dialog<Void> {
    private final Workspace ws;
    private final VBox page = new VBox(14);
    private final ToggleGroup nav = new ToggleGroup();
    private final FlowPane themeCards = new FlowPane(12, 12);

    {
        themeCards.setRowValignment(javafx.geometry.VPos.TOP);
        themeCards.setPrefWrapLength(660);
    }

    private final Keybinds keys;
    private final Runnable keysChanged;

    /** Restarts BlockDesigner (after loading a backup or resetting, so everything picks the settings up). */
    private Runnable restartApp = () -> {
    };

    void setRestart(Runnable restart) {
        restartApp = restart;
    }

    SettingsDialog(Window owner, Workspace ws, Keybinds keys, Runnable keysChanged, Runnable changeAssets, Runnable openPlugins, Runnable checkUpdates) {
        this.ws = ws;
        this.keys = keys;
        this.keysChanged = keysChanged;
        initOwner(owner);
        setTitle("Settings");
        setResizable(true);
        var dp = getDialogPane();
        dp.getStylesheets().add(SettingsDialog.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        dp.getStyleClass().addAll("app-root", "settings-dialog");
        syncModeClass();
        ws.darkProperty().addListener((o, a, b) -> {
            syncModeClass();
            rebuildThemeCards();
        });

        ToggleButton general = navButton("General", Feather.SLIDERS);
        ToggleButton appearance = navButton("Appearance", Feather.DROPLET);
        ToggleButton keybinds = navButton("Keybinds", Feather.COMMAND);
        VBox side = new VBox(4, general, appearance, keybinds);
        side.getStyleClass().add("settings-nav");
        side.setPrefWidth(190);
        side.setMinWidth(170);

        page.setPadding(new Insets(4, 16, 8, 22));
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("export-cards-scroll");
        HBox.setHgrow(scroll, Priority.ALWAYS);
        HBox body = new HBox(side, scroll);
        dp.setContent(body);
        dp.setPrefSize(900, 620);
        dp.setMinSize(720, 520);
        dp.getButtonTypes().add(ButtonType.CLOSE);

        appearance.setOnAction(e -> showAppearance());
        general.setOnAction(e -> showGeneral(changeAssets, openPlugins, checkUpdates));
        keybinds.setOnAction(e -> showKeybinds());
        nav.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null && a != null) a.setSelected(true);
        });
        general.setSelected(true);
        showGeneral(changeAssets, openPlugins, checkUpdates);
    }

    private void syncModeClass() {
        getDialogPane().getStyleClass().removeAll("dark", "light");
        getDialogPane().getStyleClass().add(ws.darkProperty().get() ? "dark" : "light");
    }

    private ToggleButton navButton(String text, Feather icon) {
        ToggleButton b = new ToggleButton(text, new FontIcon(icon));
        b.getStyleClass().add("settings-nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setToggleGroup(nav);
        return b;
    }

    private static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("export-title");
        return l;
    }

    private static Label section(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("viewport-settings-section");
        return l;
    }

    private static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("plugin-meta");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    // ---- Appearance ------------------------------------------------------------------------------------------

    private void showAppearance() {
        // Mode: three pills.
        ToggleGroup modes = new ToggleGroup();
        HBox modeRow = new HBox(0);
        AppTheme.Mode current = AppTheme.mode(ws.themeModeProperty().get());
        AppTheme.Mode[] all = AppTheme.Mode.values();
        for (int i = 0; i < all.length; i++) {
            AppTheme.Mode m = all[i];
            Feather icon = switch (m) {
                case DARK -> Feather.MOON;
                case LIGHT -> Feather.SUN;
                case SYSTEM -> Feather.MONITOR;
            };
            ToggleButton b = new ToggleButton(m.label, new FontIcon(icon));
            b.setToggleGroup(modes);
            b.getStyleClass().add(i == 0 ? "left-pill" : i == all.length - 1 ? "right-pill" : "center-pill");
            b.setSelected(m == current);
            b.setOnAction(e -> {
                if (!b.isSelected()) {
                    b.setSelected(true);
                    return;
                }
                ws.themeModeProperty().set(m.name());
            });
            modeRow.getChildren().add(b);
        }

        rebuildThemeCards();
        page.getChildren().setAll(title("Appearance"),
                section("Mode"), modeRow,
                hint("Match Windows follows Settings > Personalisation > Colours > \"Choose your app mode\"."),
                section("Theme"), themeCards,
                hint("The theme colours the whole app: panels, menus, dialogs, the accent of buttons and outlines, and the sky and grid of the 3D view."));
    }

    private void rebuildThemeCards() {
        ToggleGroup group = new ToggleGroup();
        AppTheme selected = AppTheme.byId(ws.themeProperty().get());
        boolean dark = ws.darkProperty().get();
        themeCards.getChildren().clear();
        for (AppTheme t : AppTheme.values()) {
            Label name = new Label(t.label);
            name.getStyleClass().add("export-card-title");
            Label desc = new Label(t.description);
            desc.getStyleClass().add("export-card-sub");
            desc.setWrapText(true);
            desc.setPrefWidth(196);
            desc.setMaxWidth(196);
            desc.setMinHeight(34);
            desc.setPrefHeight(34);
            VBox box = new VBox(8, preview(t, dark), name, desc);
            box.setPrefWidth(196);
            ToggleButton card = new ToggleButton(null, box);
            card.setMinHeight(Region.USE_PREF_SIZE);
            card.setMaxHeight(Region.USE_PREF_SIZE);
            card.getStyleClass().addAll("export-card", "theme-card");
            card.setToggleGroup(group);
            card.setSelected(t == selected);
            card.setTooltip(new Tooltip(t.label + ": " + t.description));
            card.setOnAction(e -> {
                if (!card.isSelected()) {
                    card.setSelected(true);
                    return;
                }
                ws.themeProperty().set(t.name());
            });
            themeCards.getChildren().add(card);
        }
    }

    /** A tiny mock window in the theme's colours: title bar, side panel, text lines and an accent button. */
    static Node preview(AppTheme t, boolean dark) {
        List<Color> c = t.swatches(dark);
        Color bg = c.get(0), panel = c.get(1), accent = c.get(2), fg = c.get(3), muted = c.get(4);
        double w = 196, h = 104;
        Rectangle frame = new Rectangle(w, h, bg);
        frame.setArcWidth(12);
        frame.setArcHeight(12);
        frame.setStroke(Color.color(muted.getRed(), muted.getGreen(), muted.getBlue(), 0.35));
        Rectangle bar = new Rectangle(0, 0, w, 16);
        bar.setFill(panel);
        Rectangle side = new Rectangle(0, 16, 54, h - 16);
        side.setFill(panel);
        Pane art = new Pane(frame, bar, side);
        for (int i = 0; i < 3; i++) art.getChildren().add(line(8, 26 + i * 12, 34 - i * 6, muted));
        art.getChildren().add(line(66, 28, 92, fg));
        art.getChildren().add(line(66, 40, 118, muted));
        art.getChildren().add(line(66, 50, 76, muted));
        Rectangle button = new Rectangle(66, 70, 58, 18);
        button.setArcWidth(8);
        button.setArcHeight(8);
        button.setFill(accent);
        Rectangle chip = new Rectangle(132, 70, 44, 18);
        chip.setArcWidth(8);
        chip.setArcHeight(8);
        chip.setFill(Color.TRANSPARENT);
        chip.setStroke(accent);
        Rectangle dot = new Rectangle(8, 5, 6, 6);
        dot.setArcWidth(6);
        dot.setArcHeight(6);
        dot.setFill(accent);
        art.getChildren().addAll(button, chip, dot);
        // Clip the art to the rounded frame.
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        art.setClip(clip);
        art.setMinSize(w, h);
        art.setPrefSize(w, h);
        art.setMaxSize(w, h);
        return new StackPane(art);
    }

    private static Rectangle line(double x, double y, double width, Color color) {
        Rectangle r = new Rectangle(x, y, width, 5);
        r.setArcWidth(5);
        r.setArcHeight(5);
        r.setFill(color);
        return r;
    }

    // ---- General ---------------------------------------------------------------------------------------------

    private void showGeneral(Runnable changeAssets, Runnable openPlugins, Runnable checkUpdates) {
        Settings s = ws.settings();
        TextField author = new TextField(s.author);
        author.setPromptText("Your name");
        author.textProperty().addListener((o, a, b) -> s.author = b.strip());
        CheckBox start = new CheckBox("Show the start screen when BlockDesigner opens");
        start.setSelected(s.showStartScreen);
        start.selectedProperty().addListener((o, a, b) -> s.showStartScreen = b);
        GridPane who = new GridPane();
        who.setHgap(12);
        who.setVgap(8);
        Label authorLabel = new Label("Author");
        authorLabel.setMinWidth(Region.USE_PREF_SIZE);
        who.addRow(0, authorLabel, author);
        GridPane.setHgrow(author, Priority.ALWAYS);

        Button assets = new Button("Change Minecraft version, mods & resource packs…", new FontIcon(Feather.BOX));
        assets.setOnAction(e -> {
            close();
            changeAssets.run();
        });
        Button plugins = new Button("Manage plugins…", FormatIcons.icon(FormatIcons.Kind.PLUGIN, 16));
        plugins.setOnAction(e -> openPlugins.run());
        Button folder = new Button("Open settings folder", new FontIcon(Feather.FOLDER));
        folder.setOnAction(e -> {
            try {
                java.nio.file.Files.createDirectories(Settings.dir());
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(Settings.dir().toFile());
            } catch (Exception ignored) {
                // nothing to open
            }
        });

        CheckBox autoUpdate = new CheckBox("Check for updates when BlockDesigner opens");
        autoUpdate.setSelected(s.checkForUpdates);
        autoUpdate.selectedProperty().addListener((o, a, b) -> s.checkForUpdates = b);
        Button checkNow = new Button("Check for updates now", new FontIcon(Feather.REFRESH_CW));
        checkNow.setOnAction(e -> checkUpdates.run());

        page.getChildren().setAll(title("General"),
                section("You"), who, hint("Written into exported schematics, and used as the default data pack namespace."),
                start,
                section("Minecraft"), assets, hint("Blocks, models and textures come from a Minecraft client jar plus an instance's mods and resource packs."),
                section("Extensions"), plugins,
                section("Updates"), autoUpdate, checkNow,
                hint("You have BlockDesigner " + io.blockdesigner.app.update.Updater.currentVersion()
                        + ". New versions come from github.com/" + io.blockdesigner.app.update.Updater.REPO + "/releases."),
                section("Your settings"), yourSettings(),
                hint("Appearance, keybinds, the hotbar, viewport options and everything else here are kept in "
                        + Settings.dir() + " and carry over every update. The first start of each new version also "
                        + "copies them into " + Settings.backupDir() + " (the last ten are kept)."),
                section("Files"), folder, hint(Settings.dir().toString()));
    }

    // ---- Keybinds --------------------------------------------------------------------------------------------

    /** The action whose slot is waiting for a key, and that slot's button. */
    private Button listening;

    private void showKeybinds() {
        TextField filter = new TextField();
        filter.setPromptText("Search actions or keys…  (e.g. layer, Shift+Z)");
        filter.getStyleClass().add("search-field");
        HBox searchBox = new HBox(6, new FontIcon(Feather.SEARCH), filter);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");
        HBox.setHgrow(filter, Priority.ALWAYS);
        Button resetAll = new Button("Reset all to defaults", new FontIcon(Feather.ROTATE_CCW));
        resetAll.setOnAction(e -> {
            keys.resetAll();
            keysChanged.run();
            showKeybinds();
        });
        HBox top = new HBox(8, searchBox, resetAll);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        VBox list = new VBox(2);
        Runnable fill = () -> {
            list.getChildren().clear();
            String q = filter.getText() == null ? "" : filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
            Keybinds.Group group = null;
            for (Keybinds.Action a : Keybinds.Action.values()) {
                KeyCombination[] k = keys.get(a);
                String hay = (a.label + " " + a.group.label + " " + Keybinds.text(k[0]) + " " + Keybinds.text(k[1])).toLowerCase(java.util.Locale.ROOT);
                if (!q.isEmpty() && !hay.contains(q)) continue;
                if (a.group != group) {
                    group = a.group;
                    Label sec = section(group.label);
                    VBox.setMargin(sec, new Insets(list.getChildren().isEmpty() ? 4 : 12, 0, 2, 0));
                    list.getChildren().add(sec);
                }
                list.getChildren().add(keyRow(a));
            }
            if (list.getChildren().isEmpty()) list.getChildren().add(hint("No action or key matches \"" + q + "\"."));
        };
        filter.textProperty().addListener((o, a, b) -> fill.run());
        refillKeys = fill;
        fill.run();

        page.getChildren().setAll(title("Keybinds"),
                hint("Click a key to change it, then press the new key or combination (click it again, or anywhere else, to "
                        + "cancel). Each action can have a second key. Actions marked \"hold\" take a single key, and Shift, Ctrl or Alt "
                        + "on its own works for them. Keys shown in red are used by another action too; the first one that applies "
                        + "wins. Mouse buttons are fixed."),
                top, list);
    }

    private HBox keyRow(Keybinds.Action a) {
        Label name = new Label(a.label);
        // Names give way (with an ellipsis) before the key columns do, so narrow windows don't scroll sideways.
        name.setMinWidth(90);
        name.setTooltip(new Tooltip(a.label));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(6, name, grow);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("keybind-row");
        KeyCombination[] k = keys.get(a);
        for (int slot = 0; slot < 2; slot++) row.getChildren().add(keySlot(a, slot, k[slot], row));
        Button reset = new Button(null, new FontIcon(Feather.ROTATE_CCW));
        reset.getStyleClass().addAll("flat", "small");
        reset.setTooltip(new Tooltip("Back to " + defaultsText(a)));
        reset.setDisable(keys.isDefault(a));
        reset.setOnAction(e -> {
            keys.reset(a);
            keysChanged.run();
            refreshRow(row, a);
        });
        row.getChildren().add(reset);
        return row;
    }

    /** Redraws the key list (keeping the search): a change can make other rows clash or stop clashing. */
    private Runnable refillKeys = () -> {
    };

    private void refreshRow(HBox row, Keybinds.Action a) {
        refillKeys.run();
    }

    private String defaultsText(Keybinds.Action a) {
        List<String> d = a.defaults().stream().map(Keybinds::text).toList();
        return d.isEmpty() ? "no key" : String.join(" / ", d);
    }

    /** One binding: a keycap-like button that listens for the next key when clicked, with a small clear button. */
    private Node keySlot(Keybinds.Action a, int slot, KeyCombination k, HBox row) {
        Button b = new Button(k == null ? (slot == 0 ? "None" : "+ Add") : Keybinds.text(k));
        b.getStyleClass().add("keybind-key");
        if (k == null) b.getStyleClass().add("keybind-empty");
        List<Keybinds.Action> clash = keys.usersOf(k, a);
        if (!clash.isEmpty()) {
            b.getStyleClass().add("keybind-clash");
            b.setTooltip(new Tooltip("Also: " + String.join(", ", clash.stream().map(c -> c.label).toList())));
        }
        b.setMinWidth(96);
        b.setFocusTraversable(false);
        b.setOnAction(e -> {
            // Clicking the key again while it waits cancels, as does clicking anywhere else.
            if (listening == b) {
                listening = null;
                refreshRow(row, a);
                return;
            }
            if (listening != null) {
                listening.getStyleClass().remove("keybind-listening");
            }
            listening = b;
            b.setText(a.held ? "Hold a key…" : "Press a key…");
            b.getStyleClass().add("keybind-listening");
            b.requestFocus();
        });
        b.focusedProperty().addListener((o, was, is) -> {
            if (!is && listening == b) {
                listening = null;
                refreshRow(row, a);
            }
        });
        b.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (listening != b) return;
            e.consume();
            // Held actions take one key on its own, Shift, Ctrl and Alt included; the rest take a combination.
            KeyCombination got = a.held ? Keybinds.heldFromEvent(e) : Keybinds.fromEvent(e);
            if (got == null) return;
            listening = null;
            keys.set(a, slot, got);
            keysChanged.run();
            refreshRow(row, a);
        });
        // Alt, F10 and the like must not reach the dialog's own handling while a key is being recorded.
        b.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> {
            if (listening == b) e.consume();
        });
        HBox box = new HBox(0, b);
        box.setAlignment(Pos.CENTER_LEFT);
        // A fixed width per slot keeps the main and second keys in straight columns.
        box.setMinWidth(128);
        box.setPrefWidth(128);
        if (k == null) return box;
        Button clear = new Button(null, new FontIcon(Feather.X));
        clear.getStyleClass().addAll("flat", "small", "keybind-clear");
        clear.setTooltip(new Tooltip("Remove this key"));
        clear.setFocusTraversable(false);
        clear.setOnAction(e -> {
            keys.set(a, slot, null);
            keysChanged.run();
            refreshRow(row, a);
        });
        box.getChildren().add(clear);
        return box;
    }

    // ---- settings backups ----------------------------------------------------------------------------------------

    /** Save a backup, load one, or reset: settings as a .json file you can keep or take to another PC. */
    private Node yourSettings() {
        Button save = new Button("Save a backup…", new FontIcon(Feather.SAVE));
        save.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Save settings backup");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("BlockDesigner settings", "*.json"));
            fc.setInitialFileName("BlockDesigner-settings-" + java.time.LocalDate.now() + ".json");
            java.io.File f = fc.showSaveDialog(getDialogPane().getScene().getWindow());
            if (f == null) return;
            try {
                ws.settings().saveTo(f.toPath());
                info("Settings saved", "Saved to " + f + ".\nLoad it later (or on another PC) with Load a backup….");
            } catch (java.io.IOException ex) {
                error("Couldn't save the backup", ex.getMessage());
            }
        });
        Button load = new Button("Load a backup…", new FontIcon(Feather.FOLDER));
        load.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Load settings backup");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("BlockDesigner settings", "*.json"));
            java.io.File f = fc.showOpenDialog(getDialogPane().getScene().getWindow());
            if (f == null) return;
            try {
                Settings loaded = Settings.readFrom(f.toPath());
                if (!confirm("Load these settings?", "Every setting is replaced by the backup's: appearance, keybinds, "
                        + "the hotbar, viewport options, export and data pack choices. Your projects aren't touched.")) return;
                ws.settings().copyFrom(loaded);
                applied("Settings loaded");
            } catch (java.io.IOException ex) {
                error("Couldn't load that file", ex.getMessage());
            }
        });
        Button reset = new Button("Reset to defaults…", new FontIcon(Feather.ROTATE_CCW));
        reset.setOnAction(e -> {
            if (!confirm("Reset every setting?", "Appearance, keybinds, the hotbar and every other setting go back to the "
                    + "defaults. Your Minecraft version, mods and recent files are kept, and so are your projects. "
                    + "Tip: Save a backup… first if you might want them back.")) return;
            ws.settings().resetToDefaults();
            applied("Settings reset");
        });
        FlowPane row = new FlowPane(8, 8, save, load, reset);
        return row;
    }

    /** Saves the changed settings and offers the restart that applies everything (theme, layout, keys…). */
    private void applied(String what) {
        ws.settings().save();
        keysChanged.run();
        javafx.scene.control.ButtonType restart = new javafx.scene.control.ButtonType("Restart now", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType later = new javafx.scene.control.ButtonType("Later", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                "Some settings (the theme, window layout, panels) only take effect when BlockDesigner starts.", restart, later);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setTitle(what);
        a.setHeaderText(what);
        if (a.showAndWait().orElse(later) == restart) {
            close();
            restartApp.run();
        }
    }

    private boolean confirm(String title, String text) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION, text);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setTitle(title);
        a.setHeaderText(title);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void info(String title, String text) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, text);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    private void error(String title, String text) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, text);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }
}
