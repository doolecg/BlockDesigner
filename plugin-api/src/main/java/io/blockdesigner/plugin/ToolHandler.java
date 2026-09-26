package io.blockdesigner.plugin;

/**
 * Receives a {@link PluginTool}'s input while it is the active tool. Every method runs on the JavaFX thread; each has
 * a do-nothing default, so implement only what the tool uses. Exceptions are logged against the plugin and shown as
 * a toast; the tool stays active.
 */
public interface ToolHandler {

    /** The mouse moved with no button held (update a preview here). */
    default void hover(ToolEvent e) {
    }

    /** The left or right button went down. */
    default void press(ToolEvent e) {
    }

    /** The mouse moved with the left or right button held. */
    default void drag(ToolEvent e) {
    }

    /** The button went up. */
    default void release(ToolEvent e) {
    }

    /**
     * The wheel turned; {@code delta} is +1 / -1 per notch (away from the user is positive).
     *
     * @return true when the tool used it; false leaves the wheel zooming the view
     */
    default boolean scroll(ToolEvent e, double delta) {
        return false;
    }

    /**
     * A key was pressed (outside text fields), e.g. {@code "R"}, {@code "Shift+R"}, {@code "Esc"}, {@code "Enter"}.
     *
     * @return true when the tool used it; false lets the key do what it normally does
     */
    default boolean key(String key) {
        return false;
    }

    /** Another tool was picked (or the plugin is being disabled): finish or cancel what is in progress. */
    default void deactivate() {
    }
}
