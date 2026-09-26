package io.blockdesigner.plugin;

/**
 * A tool of the plugin's own in the tool dock over the viewport, next to Select, Build and the brushes. While it is
 * the active tool, the left and right mouse buttons, the wheel (when the handler wants it) and plain keys go to its
 * {@link ToolHandler}; the middle button still orbits and pans. Its {@link #options()} show in a bar at the bottom of
 * the view. Registered with {@link PluginContext#registerTool}. Since API 2.
 *
 * <p>Edits go through {@link ToolContext#beginStroke}: everything from press to release is one undo step, just like a
 * brush stroke. Ghost blocks and outlines go through {@link ToolContext#preview()}.
 */
public interface PluginTool {

    /** Stable id, unique within the plugin. */
    String id();

    /** Tooltip title and toast, e.g. "Arch". */
    String name();

    /** One line for the tooltip: what the buttons do. */
    default String description() {
        return "";
    }

    /** A 16×16 icon as SVG path data, drawn as 1.5px rounded strokes like the built-in tool icons; null for a default. */
    default String icon() {
        return null;
    }

    /**
     * The key that picks the tool, in JavaFX's {@code KeyCombination} form ({@code "Shift+K"}, {@code "J"}), or null for
     * none. A key the user already has bound to something else is left alone. Users can change it in the settings file.
     */
    default String defaultKey() {
        return null;
    }

    /**
     * True for a tool that works on the selection: the left mouse button then selects blocks as in Select mode (click,
     * drag a box, Shift adds, Ctrl removes, a plain click sets //pos1) while the tool is active, and the tool gets the
     * right button, the wheel and keys. API 5; older BlockDesigners ignore it.
     */
    default boolean selects() {
        return false;
    }

    /** Parameters shown in the tool's options bar while it is active; the current values are {@link ToolContext#options()}. */
    default Options options() {
        return Options.none();
    }

    /** The tool was picked: return the handler that gets its input until another tool is picked. */
    ToolHandler activate(ToolContext context);
}
