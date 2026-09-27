package io.blockdesigner.plugin.ui;

import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the app does for a plugin's own UI, from {@code PluginContext.ui()}: its options form, dark or light, the main
 * window, and small dialogs in the app's look. Use from the JavaFX thread. Since API 6.
 */
public interface PluginUi {

    /**
     * The app's form for {@code options}, for a panel of the plugin's own.
     *
     * @param rememberAs null for a form whose values the plugin keeps itself (start from the defaults; use
     *                   {@link OptionsForm#setValues}), or a name such as {@code "panel"} under which the app keeps
     *                   the values in its settings between runs. {@code "importer/<id>"} and {@code "exporter/<id>"}
     *                   share the values of the plugin's importer or exporter with that id, so the page and the
     *                   Import window agree. Names use {@code A-Z a-z 0-9 _ . -} and {@code /}.
     * @param onChange   runs after each change the user makes (not for the initial values)
     */
    OptionsForm optionsForm(Options options, String rememberAs, Consumer<OptionValues> onChange);

    /** True while the app is dark; listen to redraw anything drawn by hand (a canvas, an image). */
    ReadOnlyBooleanProperty darkProperty();

    /** Whether the app is dark right now. */
    default boolean isDark() {
        return darkProperty().get();
    }

    /** The main window, as the owner of choosers and dialogs. */
    Window owner();

    /**
     * Gives a dialog the app's look: the main window as its owner, the app's and the kit's stylesheets, its font and
     * dark or light. Returns the dialog, so {@code ui.style(new Dialog<ButtonType>())} reads well.
     */
    <D extends Dialog<?>> D style(D dialog);

    /**
     * Asks a yes/no question. {@code destructive} draws the confirm button in red (Cancel stays the default, so Enter
     * doesn't delete).
     *
     * @return true when the user confirmed
     */
    boolean confirm(String title, String message, String confirmLabel, boolean destructive);

    /** Asks for a line of text (a name); empty when cancelled. */
    Optional<String> askText(String title, String label, String initial);

    /** Asks to pick one of {@code choices} (shown with {@code toString()}); empty when cancelled. */
    <T> Optional<T> choose(String title, String label, List<T> choices, T initial);

    /** Copies text to the clipboard and says so with a short toast ({@code toast} may be null for none). */
    void copyText(String text, String toast);

    /** Opens a file with its app, or a folder in Explorer; never blocks the UI. */
    void open(Path fileOrFolder);

    /**
     * Brings the main window forward (restoring it when minimized), for something the user asked for elsewhere, such
     * as a game sending a build to edit. Where the system doesn't let a background app take the focus, the window's
     * taskbar button flashes instead. Since API 7.
     */
    void toFront();
}
