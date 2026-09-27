package io.blockdesigner.plugin.ui;

import io.blockdesigner.plugin.OptionValues;
import javafx.scene.Node;

/**
 * The app's form for some {@link io.blockdesigner.plugin.Options}, made by {@link PluginUi#optionsForm}: the same
 * controls as in the transform dialog and the Import and Export windows (headings, help, units, block slots with icons
 * and the held block), for a plugin's own panel. Use from the JavaFX thread. Since API 6.
 */
public interface OptionsForm {

    /** The form, to put on a page (usually in a {@link Section} or straight into a {@link PanelScaffold}). */
    Node node();

    /** The values showing now. */
    OptionValues values();

    /**
     * Shows other values (redrawing the controls) without calling the form's {@code onChange}; a remembered form saves
     * them.
     */
    void setValues(OptionValues values);

    /** Back to the defaults; calls {@code onChange}. */
    void reset();
}
