package io.blockdesigner.app.ui;

import io.blockdesigner.plugin.ui.Theme;
import javafx.scene.control.DialogPane;

/** The one way the app's dialogs (and plugins' dialogs, through {@code PluginUi.style}) get the app's look. */
final class Dialogs {
    static final String APP_CSS = Dialogs.class.getResource("/io/blockdesigner/app/app.css").toExternalForm();

    private Dialogs() {
    }

    /** Adds app.css and the kit's bd.css, the app font ({@code app-root}) and dark or light to a dialog. */
    static void style(DialogPane p, boolean dark) {
        if (!p.getStylesheets().contains(APP_CSS)) p.getStylesheets().add(APP_CSS);
        if (!p.getStylesheets().contains(Theme.STYLESHEET)) p.getStylesheets().add(Theme.STYLESHEET);
        if (!p.getStyleClass().contains("app-root")) p.getStyleClass().add("app-root");
        mode(p, dark);
    }

    /** Switches a styled dialog between dark and light. */
    static void mode(DialogPane p, boolean dark) {
        p.getStyleClass().removeAll("dark", "light");
        p.getStyleClass().add(dark ? "dark" : "light");
    }
}
