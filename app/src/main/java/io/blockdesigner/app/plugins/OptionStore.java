package io.blockdesigner.app.plugins;

import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginCommand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the last {@link OptionValues} used for each plugin feature, as text in the settings file
 * ({@code Settings.pluginOptions}), keyed like {@code "palette-tools/transform/weather"}.
 */
public final class OptionStore {
    private final Map<String, Map<String, String>> saved;

    /** @param saved the settings' map; read and written in place */
    public OptionStore(Map<String, Map<String, String>> saved) {
        this.saved = saved;
    }

    /** The key a plugin feature's values are kept under. */
    public static String key(String pluginId, String kind, String id) {
        return pluginId + "/" + kind + "/" + id;
    }

    /** The values last saved under {@code key}, with defaults for anything missing or no longer valid. */
    public OptionValues load(String key, Options options, PluginCommand.BlockResolver blocks) {
        Map<String, String> s = saved.get(key);
        return s == null ? options.defaults() : OptionValues.fromStrings(options, s, blocks);
    }

    public void save(String key, OptionValues values) {
        saved.put(key, new LinkedHashMap<>(values.toStrings()));
    }

    /** A number kept beside the values, such as a transform's last seed. */
    public long number(String key, String name, long fallback) {
        Map<String, String> s = saved.get(key + "#");
        try {
            return s == null || !s.containsKey(name) ? fallback : Long.parseLong(s.get(name));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setNumber(String key, String name, long value) {
        saved.computeIfAbsent(key + "#", k -> new LinkedHashMap<>()).put(name, Long.toString(value));
    }
}
