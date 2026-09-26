package io.blockdesigner.plugin;

/** A {@link PluginPanel}'s handle on its tab. Use from the JavaFX thread. */
public interface PanelContext {

    /** The plugin's context, for the scene, events and edits. */
    PluginContext plugin();

    /** Whether the panel is open and its tab is the selected one (so it is worth updating). */
    boolean isShowing();

    /**
     * Runs {@code action} each time the panel comes into view (its tab selected or reopened). Handy for panels that
     * only refresh while visible: skip work while hidden, catch up here.
     */
    void onShown(Runnable action);

    /** A short text next to the tab's title, such as a count; {@code null} or empty removes it. */
    void setBadge(String text);

    /** Opens the panel if it was closed and selects its tab. */
    void reveal();
}
