package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.plugins.PluginManager;
import javafx.scene.input.KeyCombination;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keys for plugin tools and actions, as Settings › Keybinds edits them: the user's key, else the plugin's default
 * (tools only; actions have none by default), else none. A key BlockDesigner itself uses keeps doing that, so such a
 * key picks nothing. Stored in {@code Settings.pluginToolKeys} ("plugin/tool") and {@code Settings.pluginActionKeys}
 * ("plugin/action label"); {@code ""} there means "no key".
 */
final class PluginKeys {
    enum Kind { TOOL, ACTION }

    /**
     * One plugin tool or action that can have a key.
     *
     * @param id         its key in the settings map
     * @param pluginName for the Keybinds page's group heading and clash tooltips
     * @param label      "Tool: Palette", or the action's label
     * @param defaultKey the plugin's default key (tools), or null
     */
    record Target(Kind kind, String id, String pluginName, String label, String defaultKey) {
        static Target of(PluginManager.Tool t) {
            return new Target(Kind.TOOL, t.key(), t.plugin().info().name(), "Tool: " + t.tool().name(), t.tool().defaultKey());
        }

        static Target of(PluginManager.Action a) {
            return new Target(Kind.ACTION, a.plugin().info().id() + "/" + a.action().label(), a.plugin().info().name(),
                    a.action().label(), null);
        }

        String fullName() {
            return pluginName + ": " + label;
        }
    }

    private final Settings settings;
    private final Keybinds keys;

    PluginKeys(Settings settings, Keybinds keys) {
        this.settings = settings;
        this.keys = keys;
    }

    private Map<String, String> store(Kind kind) {
        return kind == Kind.TOOL ? settings.pluginToolKeys : settings.pluginActionKeys;
    }

    /** The key set for it (the user's or the default), whether or not an app key takes it; null for none. */
    KeyCombination bound(Target t) {
        Map<String, String> s = store(t.kind());
        String k = s.containsKey(t.id()) ? s.get(t.id()) : t.defaultKey();
        return k == null || k.isBlank() ? null : Keybinds.parse(k);
    }

    /** The key that picks it: {@link #bound} unless one of BlockDesigner's own actions uses it; null for none. */
    KeyCombination effective(Target t) {
        KeyCombination k = bound(t);
        return k == null || !appUsers(k).isEmpty() ? null : k;
    }

    KeyCombination tool(PluginManager.Tool t) {
        return effective(Target.of(t));
    }

    KeyCombination action(PluginManager.Action a) {
        return effective(Target.of(a));
    }

    /** Sets its key; null for none. Back to the default drops the saved entry. */
    void set(Target t, KeyCombination k) {
        Map<String, String> s = store(t.kind());
        String name = k == null ? "" : k.getName();
        String def = t.defaultKey() == null ? "" : normalise(t.defaultKey());
        if (name.equals(def)) s.remove(t.id());
        else s.put(t.id(), name);
    }

    void reset(Target t) {
        store(t.kind()).remove(t.id());
    }

    boolean isDefault(Target t) {
        return !store(t.kind()).containsKey(t.id());
    }

    String defaultText(Target t) {
        KeyCombination d = t.defaultKey() == null || t.defaultKey().isBlank() ? null : Keybinds.parse(t.defaultKey());
        return d == null ? "no key" : Keybinds.text(d);
    }

    /** BlockDesigner's own actions bound to {@code k} (those win over a plugin's key). */
    List<Keybinds.Action> appUsers(KeyCombination k) {
        return keys == null ? List.of() : keys.usersOf(k, null);
    }

    /**
     * Everything else bound to {@code k}: app actions by label, and the other plugin targets among {@code all} by
     * "Plugin: label".
     */
    List<String> clashes(KeyCombination k, Target except, List<Target> all) {
        List<String> out = new ArrayList<>();
        if (k == null) return out;
        for (Keybinds.Action a : appUsers(k)) out.add(a.label);
        for (Target t : all) {
            if (t.equals(except)) continue;
            if (k.equals(bound(t))) out.add(t.fullName());
        }
        return out;
    }

    private static String normalise(String key) {
        KeyCombination k = Keybinds.parse(key);
        return k == null ? "" : k.getName();
    }
}
