package io.blockdesigner.plugin;

/**
 * A {@link PluginPanel}'s handle on its page of the plugin's tab. Use from the JavaFX thread. To open another of the
 * plugin's pages, or show a status dot on a page button, see {@link PluginContext#showPanel} and
 * {@link PluginContext#setPanelStatus} (API 6).
 */
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

    /** A short text on the page's button, such as a count; {@code null} or empty removes it. */
    void setBadge(String text);

    /** Opens the plugin's tab (reopening it if it was closed) at this page. */
    void reveal();
}
