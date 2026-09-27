package io.blockdesigner.app.plugins;

import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.ui.OptionsForm;
import io.blockdesigner.plugin.ui.PluginUi;
import io.blockdesigner.plugin.ui.Theme;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link PluginUi} for one plugin: the options form and the look come from the host (the main window), the small
 * dialogs are built here in the kit's look (24 px padding, a title, one line, the main button on the right).
 */
final class AppPluginUi implements PluginUi {
    private final PluginManager plugins;
    private final PluginManager.Plugin plugin;

    AppPluginUi(PluginManager plugins, PluginManager.Plugin plugin) {
        this.plugins = plugins;
        this.plugin = plugin;
    }

    private PluginHost host() {
        return plugins.host();
    }

    @Override
    public OptionsForm optionsForm(Options options, String rememberAs, Consumer<OptionValues> onChange) {
        Objects.requireNonNull(options, "options");
        String key = PluginManager.formKey(plugin, rememberAs);
        OptionStore store = plugins.optionStore();
        OptionValues initial = key == null ? options.defaults() : store.load(key, options, plugins.blocks());
        Consumer<OptionValues> changed = v -> {
            if (key != null) store.save(key, v);
            if (onChange != null) {
                try {
                    onChange.accept(v);
                } catch (Throwable t) {
                    plugins.report(plugin, "Options form", t);
                }
            }
        };
        OptionsForm form = host().optionsForm(plugin, options, initial, changed);
        if (form == null) throw new IllegalStateException("No options forms without the app's window");
        return new OptionsForm() {
            @Override
            public Node node() {
                return form.node();
            }

            @Override
            public OptionValues values() {
                return form.values();
            }

            @Override
            public void setValues(OptionValues values) {
                form.setValues(values);
                if (key != null) store.save(key, form.values());
            }

            @Override
            public void reset() {
                form.reset();
            }
        };
    }

    @Override
    public ReadOnlyBooleanProperty darkProperty() {
        return host().dark();
    }

    @Override
    public Window owner() {
        return host().owner();
    }

    @Override
    public <D extends Dialog<?>> D style(D dialog) {
        Objects.requireNonNull(dialog, "dialog");
        host().styleDialog(dialog);
        return dialog;
    }

    /** A dialog in the kit's look: title as its first line, then the content, the main button on the right. */
    private Dialog<ButtonType> dialog(String title, Node... content) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(null);
        d.setGraphic(null);
        style(d);
        d.getDialogPane().getStyleClass().add("bd-dialog");
        Label heading = new Label(title);
        heading.getStyleClass().add("bd-page-title");
        VBox body = new VBox(Theme.MD, heading);
        body.getChildren().addAll(content);
        body.setPrefWidth(380);
        d.getDialogPane().setContent(body);
        return d;
    }

    private static Label message(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("bd-dialog-message");
        l.setWrapText(true);
        l.setMinHeight(Label.USE_PREF_SIZE);
        return l;
    }

    @Override
    public boolean confirm(String title, String message, String confirmLabel, boolean destructive) {
        Dialog<ButtonType> d = dialog(title, message(message));
        ButtonType ok = new ButtonType(confirmLabel == null || confirmLabel.isBlank() ? "OK" : confirmLabel, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ok);
        Button okButton = (Button) d.getDialogPane().lookupButton(ok);
        Button cancel = (Button) d.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (destructive) {
            // Enter must not delete: Cancel is the default and the red button needs a click.
            okButton.setDefaultButton(false);
            okButton.getStyleClass().add("danger");
            cancel.setDefaultButton(true);
        } else {
            okButton.getStyleClass().add("accent");
        }
        return d.showAndWait().filter(b -> b == ok).isPresent();
    }

    @Override
    public Optional<String> askText(String title, String label, String initial) {
        TextField field = new TextField(initial == null ? "" : initial);
        field.setAccessibleText(label);
        Label l = new Label(label);
        l.getStyleClass().add("bd-form-label");
        l.setLabelFor(field);
        Dialog<ButtonType> d = dialog(title, new VBox(Theme.XS, l, field));
        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ok);
        Button okButton = (Button) d.getDialogPane().lookupButton(ok);
        okButton.getStyleClass().add("accent");
        okButton.disableProperty().bind(field.textProperty().isEmpty());
        d.setOnShown(e -> {
            field.requestFocus();
            field.selectAll();
        });
        return d.showAndWait().filter(b -> b == ok).map(b -> field.getText().strip()).filter(s -> !s.isEmpty());
    }

    @Override
    public <T> Optional<T> choose(String title, String label, List<T> choices, T initial) {
        if (choices.isEmpty()) return Optional.empty();
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(choices));
        box.setValue(initial != null && choices.contains(initial) ? initial : choices.getFirst());
        box.setMaxWidth(Double.MAX_VALUE);
        box.setAccessibleText(label);
        Label l = new Label(label);
        l.getStyleClass().add("bd-form-label");
        l.setLabelFor(box);
        Dialog<ButtonType> d = dialog(title, new VBox(Theme.XS, l, box));
        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ok);
        d.getDialogPane().lookupButton(ok).getStyleClass().add("accent");
        return d.showAndWait().filter(b -> b == ok).map(b -> box.getValue());
    }

    @Override
    public void copyText(String text, String toast) {
        ClipboardContent c = new ClipboardContent();
        c.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(c);
        if (toast != null && !toast.isBlank()) host().toast(toast);
    }

    @Override
    public void open(Path fileOrFolder) {
        Objects.requireNonNull(fileOrFolder, "fileOrFolder");
        Thread t = new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().open(fileOrFolder.toFile());
            } catch (Exception e) {
                host().runOnUiThread(() -> plugins.log(plugin, "Couldn't open " + fileOrFolder + ": " + e.getMessage()));
            }
        }, "open-" + plugin.info().id());
        t.setDaemon(true);
        t.start();
    }
}
