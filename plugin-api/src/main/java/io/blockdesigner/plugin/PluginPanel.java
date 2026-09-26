package io.blockdesigner.plugin;

import javafx.scene.Node;

/**
 * A panel of the plugin's own, docked next to BlockDesigner's (the right-hand tabs, by default). This is the one part
 * of the API that uses JavaFX: build the content in {@link #create}, style it with the theme's looked-up colours
 * (see PLUGINS.md) so it follows every theme, light and dark.
 *
 * <p>Like the built-in tabs, the user can close a plugin panel and reopen it from the bar on the window's right edge;
 * BlockDesigner remembers which panels were open. Registered with {@link PluginContext#registerPanel}.
 */
public interface PluginPanel {

    /** Where the panel docks. Only {@link #RIGHT} is laid out as tabs today; the others fall back to it for now. */
    enum Dock { RIGHT, LEFT, BOTTOM }

    /** Stable id, unique within the plugin (used to remember whether the panel is open). */
    String id();

    /** Tab title. */
    String title();

    /**
     * A 16×16 icon as SVG path data, drawn as 1.5px rounded strokes in the tab's text colour; {@code null} for a
     * default puzzle-piece glyph.
     */
    default String icon() {
        return null;
    }

    default Dock dock() {
        return Dock.RIGHT;
    }

    /**
     * Builds the panel's content, once, the first time the panel is shown. Runs on the JavaFX thread. Keep the context
     * to find out when the panel is showing, to set a badge on its tab, or to bring it forward.
     */
    Node create(PanelContext context);

    /** The panel is being removed (plugin disabled, app closing): stop timers and listeners here. */
    default void dispose() {
    }
}
