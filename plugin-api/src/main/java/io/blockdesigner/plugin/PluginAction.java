package io.blockdesigner.plugin;

import java.util.Objects;

/**
 * A menu entry the plugin adds to the Plugins menu in the top bar (with the description as its tooltip), and a
 * button on the plugin tab's Overview. Since API 6 the user can give it a key in Settings › Keybinds; it has none by
 * default.
 *
 * @param label       menu text
 * @param description tooltip
 * @param action      runs on the JavaFX thread when chosen
 */
public record PluginAction(String label, String description, Runnable action) {

    public PluginAction {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(action, "action");
        description = description == null ? "" : description;
    }
}
