package io.blockdesigner.plugin.ui;

import javafx.scene.Parent;

import java.util.Objects;

/**
 * The plugin UI kit's measures and stylesheet. The components ({@link PanelScaffold}, {@link Section}, {@link Form}…)
 * attach the stylesheet themselves and own their spacing, so a plugin never types a pixel number; these constants are
 * for the rare custom layout that has to line up with them. Colours always come from the theme (dark or light, any
 * accent), through the looked-up colours listed in PLUGINS.md. Since API 6.
 *
 * <p>The classes in {@code bd.css} whose names start with {@code bd-} (and the pseudo-classes {@code :narrow},
 * {@code :error}, {@code :accent}, {@code :success}, {@code :warning}, {@code :danger}, {@code :busy}) are stable: a
 * plugin may put them on its own nodes.
 */
public final class Theme {
    /** The kit's stylesheet ({@code bd.css}), for {@code getStylesheets().add(...)} on a plugin's own window. */
    public static final String STYLESHEET = Objects.requireNonNull(Theme.class.getResource("bd.css"), "bd.css").toExternalForm();

    /** Spacing steps: 4 label to hint, 8 between rows, 12 page padding, 16 between sections, 24 dialogs. */
    public static final int XS = 4, SM = 8, MD = 12, LG = 16, XL = 24;

    /** The label column of a form: at least, about and at most this wide (about 40% of the form). */
    public static final double LABEL_MIN = 96, LABEL_PREF = 120, LABEL_MAX = 140;

    /** Panels narrower than this are drawn narrow ({@code :narrow}): labels above controls, buttons stacked. */
    public static final double NARROW = 300;

    /** The height of a control (fields, buttons, combo boxes). */
    public static final double ROW = 28;

    private Theme() {
    }

    /** Adds the kit's stylesheet to {@code node} (once) and returns it. */
    public static <T extends Parent> T attach(T node) {
        if (!node.getStylesheets().contains(STYLESHEET)) node.getStylesheets().add(STYLESHEET);
        return node;
    }

    /**
     * {@link #attach} plus the app's font ({@code bd-root}): for the root of a window or popup the plugin makes itself.
     * Dialogs are simpler through {@code PluginUi.style(dialog)}.
     */
    public static <T extends Parent> T root(T node) {
        attach(node);
        if (!node.getStyleClass().contains("bd-root")) node.getStyleClass().add("bd-root");
        return node;
    }
}
