package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.OptionStore;
import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.app.plugins.TransformRunner;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.PluginTransform;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Random;

/**
 * The dialog of a plugin transform: its options, the seed for random ones, and a live preview. Every change re-runs
 * the transform (after a short pause) and shows the result as ghost blocks in the viewport; Apply runs it for real as
 * one undo step. It doesn't block the window, so the view can be turned around the preview while it is open.
 */
final class TransformDialog extends Dialog<Void> {
    private static final ButtonType APPLY = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);

    private final PluginManager plugins;
    private final PluginManager.Transform transform;
    private final ViewportPane viewport;
    private final Workspace ws;
    private final String key;
    private final OptionsEditor editor;
    private final TextField seedField = new TextField();
    private final Label target = new Label();
    private final Label status = new Label();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(160));
    private long seed;

    TransformDialog(Window owner, Workspace ws, PluginManager plugins, PluginManager.Transform transform, ViewportPane viewport) {
        this.plugins = plugins;
        this.transform = transform;
        this.viewport = viewport;
        this.ws = ws;
        PluginTransform t = transform.transform();
        this.key = OptionStore.key(transform.plugin().info().id(), "transform", t.id());
        OptionStore store = plugins.optionStore();
        OptionValues values = store.load(key, t.options(), plugins.blocks());
        seed = store.number(key, "seed", new Random().nextInt(1_000_000));

        initOwner(owner);
        initModality(Modality.NONE);
        setTitle(t.name());
        setResizable(true);
        var dp = getDialogPane();
        dp.getStylesheets().add(TransformDialog.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        dp.getStyleClass().addAll("app-root", ws.darkProperty().get() ? "dark" : "light");

        Label title = new Label(t.name(), ToolIcons.plugin(t.icon(), 22));
        title.getStyleClass().add("export-title");
        title.setGraphicTextGap(10);
        VBox body = new VBox(10, title);
        if (!t.description().isBlank()) {
            Label d = new Label(t.description());
            d.setWrapText(true);
            d.getStyleClass().add("export-hint");
            body.getChildren().add(d);
        }
        Label by = new Label("From the plugin “" + transform.plugin().info().name() + "”");
        by.getStyleClass().add("plugin-meta");
        target.getStyleClass().add("export-summary");
        target.setMaxWidth(Double.MAX_VALUE);
        body.getChildren().addAll(by, target);

        editor = new OptionsEditor(values, plugins.blocks(), () -> ws.selectedBlockProperty().get(), v -> schedulePreview());
        if (!t.options().isEmpty()) body.getChildren().add(editor);
        if (t.randomized()) body.getChildren().add(seedRow());
        status.setWrapText(true);
        body.getChildren().add(status);
        body.setPadding(new Insets(4));
        body.setPrefWidth(420);
        dp.setContent(body);
        dp.getButtonTypes().setAll(APPLY, ButtonType.CANCEL);

        // Apply keeps the dialog open when the transform fails, so the user can fix the options.
        dp.lookupButton(APPLY).addEventFilter(ActionEvent.ACTION, e -> {
            if (!apply()) e.consume();
        });
        debounce.setOnFinished(e -> preview());
        setOnHidden(e -> {
            debounce.stop();
            viewport.clearPreview();
            // Remembered even when cancelled: the next time opens the way it was left.
            plugins.optionStore().save(key, editor.values());
            plugins.optionStore().setNumber(key, "seed", seed);
        });
        setOnShown(e -> preview());
    }

    private HBox seedRow() {
        Label l = new Label("Seed");
        l.getStyleClass().add("prop-label");
        seedField.setText(Long.toString(seed));
        seedField.setPrefColumnCount(9);
        seedField.textProperty().addListener((o, a, b) -> {
            try {
                seed = Long.parseLong(b.strip());
                seedField.getStyleClass().remove("error");
                schedulePreview();
            } catch (NumberFormatException ex) {
                if (!seedField.getStyleClass().contains("error")) seedField.getStyleClass().add("error");
            }
        });
        Button reroll = new Button("Reroll", new FontIcon(Feather.SHUFFLE));
        reroll.setTooltip(new Tooltip("A new random result with the same settings"));
        reroll.setOnAction(e -> seedField.setText(Long.toString(new Random().nextInt(1_000_000))));
        HBox row = new HBox(8, l, seedField, reroll);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seedField, Priority.NEVER);
        return row;
    }

    private void schedulePreview() {
        debounce.playFromStart();
    }

    /** Runs the transform as a preview and shows the result; returns the changes, or null when it failed. */
    private TransformRunner.Changes preview() {
        PluginTransform t = transform.transform();
        TransformRunner.Target tg = viewport.transformTarget(t.scope());
        Button apply = (Button) getDialogPane().lookupButton(APPLY);
        if (tg == null) {
            target.setText(viewport.transformTargetMissing(t.scope()));
            status.setText("");
            apply.setDisable(true);
            viewport.clearPreview();
            return null;
        }
        target.setText("Applies to " + tg.label());
        try {
            TransformRunner.Changes c = TransformRunner.run(t, viewport.transformWorld(tg), tg, editor.values(), seed, plugins.blocks(), true);
            viewport.showPreview(TransformTargets.ghosts(c), TransformTargets.removed(c), List.of(tg.bounds()));
            status.getStyleClass().remove("export-error");
            status.setText(c.isEmpty() ? "Nothing changes with these settings."
                    : String.format("Changes %,d block%s · shown as ghosts in the view", c.size(), c.size() == 1 ? "" : "s"));
            apply.setDisable(c.isEmpty());
            return c;
        } catch (Throwable ex) {
            fail(ex, "Preview of " + t.name());
            apply.setDisable(true);
            viewport.clearPreview();
            return null;
        }
    }

    private void fail(Throwable ex, String what) {
        String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
        if (!status.getStyleClass().contains("export-error")) status.getStyleClass().add("export-error");
        status.setText("✖ " + msg);
        // A friendly IllegalArgumentException is the plugin talking to the user; anything else is a bug worth logging.
        if (!(ex instanceof IllegalArgumentException)) plugins.log(transform.plugin(), what + " failed: " + ex);
    }

    /** Runs the transform for real as one undo step; false when it failed (the dialog stays open). */
    private boolean apply() {
        PluginTransform t = transform.transform();
        TransformRunner.Target tg = viewport.transformTarget(t.scope());
        if (tg == null) return false;
        OptionStore store = plugins.optionStore();
        store.save(key, editor.values());
        store.setNumber(key, "seed", seed);
        TransformRunner.Changes c;
        try {
            c = TransformRunner.run(t, viewport.transformWorld(tg), tg, editor.values(), seed, plugins.blocks(), false);
        } catch (Throwable ex) {
            fail(ex, t.name());
            return false;
        }
        viewport.clearPreview();
        int n = viewport.applyTransform(t.name(), tg, c);
        viewport.showToast(n == 0 ? t.name() + " · nothing changed" : String.format("%s · changed %,d block%s", t.name(), n, n == 1 ? "" : "s"));
        ws.statusProperty().set(String.format("%s changed %,d blocks · %s to undo", t.name(), n, Keybinds.keyOf(Keybinds.Action.UNDO)));
        return true;
    }
}
