package io.blockdesigner.plugin;

import java.util.Objects;

/**
 * A menu entry the plugin adds to the Plugins menu in the top bar.
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
