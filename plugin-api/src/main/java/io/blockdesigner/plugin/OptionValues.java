package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.plugin.Options.BlockListOption;
import io.blockdesigner.plugin.Options.BlockOption;
import io.blockdesigner.plugin.Options.ChoiceOption;
import io.blockdesigner.plugin.Options.DecimalOption;
import io.blockdesigner.plugin.Options.FileOption;
import io.blockdesigner.plugin.Options.IntegerOption;
import io.blockdesigner.plugin.Options.Option;
import io.blockdesigner.plugin.Options.TextOption;
import io.blockdesigner.plugin.Options.ToggleOption;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The values the user chose for some {@link Options}. Immutable: {@link #with} returns a changed copy. Every option
 * always has a value (its default until changed), except a {@link Options.FileOption file} nobody picked yet.
 *
 * <p>BlockDesigner remembers the last values per plugin feature (in its settings), so the dialog, card or tool bar
 * opens the way the user left it.
 */
public final class OptionValues {
    private final Options options;
    private final Map<String, Object> values;

    private OptionValues(Options options, Map<String, Object> values) {
        this.options = options;
        this.values = values;
    }

    /** Every option at its default. */
    public static OptionValues defaults(Options options) {
        Map<String, Object> v = new LinkedHashMap<>();
        for (Option o : options.all()) {
            Object d = switch (o) {
                case IntegerOption i -> i.defaultValue();
                case DecimalOption d2 -> d2.defaultValue();
                case ToggleOption t -> t.defaultValue();
                case BlockOption b -> b.defaultValue();
                case BlockListOption b -> b.defaultValue();
                case ChoiceOption c -> c.defaultValue();
                case TextOption t -> t.defaultValue();
                case FileOption f -> null;
            };
            v.put(o.key(), d);
        }
        return new OptionValues(options, v);
    }

    public Options options() {
        return options;
    }

    public int integer(String key) {
        require(key, IntegerOption.class);
        return (Integer) values.get(key);
    }

    public double decimal(String key) {
        require(key, DecimalOption.class);
        return (Double) values.get(key);
    }

    public boolean toggle(String key) {
        require(key, ToggleOption.class);
        return (Boolean) values.get(key);
    }

    public BlockState block(String key) {
        require(key, BlockOption.class);
        return (BlockState) values.get(key);
    }

    public BlockPattern blockList(String key) {
        require(key, BlockListOption.class);
        return (BlockPattern) values.get(key);
    }

    public String choice(String key) {
        require(key, ChoiceOption.class);
        return (String) values.get(key);
    }

    public String text(String key) {
        require(key, TextOption.class);
        return (String) values.get(key);
    }

    /** The picked file, or empty while none is. */
    public Optional<Path> file(String key) {
        require(key, FileOption.class);
        return Optional.ofNullable((Path) values.get(key));
    }

    /** The raw value (Integer, Double, Boolean, BlockState, BlockPattern, String or Path; null for an unpicked file). */
    public Object get(String key) {
        if (options.get(key).isEmpty()) throw new IllegalArgumentException("No option '" + key + "'");
        return values.get(key);
    }

    /**
     * A copy with one value changed. Numbers are clamped to the option's range; a choice must be one of its values.
     *
     * @throws IllegalArgumentException when the key is unknown or the value has the wrong type
     */
    public OptionValues with(String key, Object value) {
        Option o = options.get(key).orElseThrow(() -> new IllegalArgumentException("No option '" + key + "'"));
        Object v = switch (o) {
            case IntegerOption i when value instanceof Number n -> Math.clamp(n.longValue(), i.min(), i.max());
            case DecimalOption d when value instanceof Number n -> Math.clamp(n.doubleValue(), d.min(), d.max());
            case ToggleOption t when value instanceof Boolean b -> b;
            case BlockOption b when value instanceof BlockState s -> s;
            case BlockListOption b when value instanceof BlockPattern p -> p;
            case BlockListOption b when value instanceof BlockState s -> BlockPattern.of(s);
            case ChoiceOption c when value instanceof String s && c.values().contains(s) -> s;
            case TextOption t when value instanceof String s -> s;
            case FileOption f when value == null || value instanceof Path -> value;
            default -> throw new IllegalArgumentException("Bad value for option '" + key + "': " + value);
        };
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(key, v);
        return new OptionValues(options, copy);
    }

    /** Each value as text, for saving (an unpicked file is left out). {@link #fromStrings} reads it back. */
    public Map<String, String> toStrings() {
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            if (v != null) out.put(k, v.toString());
        });
        return out;
    }

    /**
     * Values read back from {@link #toStrings}. Anything missing, unreadable or no longer valid (a changed range, a
     * removed choice) falls back to the default, so saved values survive a plugin update.
     */
    public static OptionValues fromStrings(Options options, Map<String, String> saved, PluginCommand.BlockResolver blocks) {
        OptionValues out = defaults(options);
        for (Option o : options.all()) {
            String s = saved.get(o.key());
            if (s == null) continue;
            try {
                Object v = switch (o) {
                    case IntegerOption i -> Integer.parseInt(s.strip());
                    case DecimalOption d -> Double.parseDouble(s.strip());
                    case ToggleOption t -> Boolean.parseBoolean(s.strip());
                    case BlockOption b -> blocks.resolve(s);
                    case BlockListOption b -> BlockPattern.parse(s, blocks);
                    case ChoiceOption c -> c.values().contains(s) ? s : c.defaultValue();
                    case TextOption t -> s;
                    case FileOption f -> s.isBlank() ? null : Path.of(s);
                };
                out = out.with(o.key(), v);
            } catch (IllegalArgumentException e) {
                // unreadable (a bad number, an unknown block, an invalid path): keep the default
            }
        }
        return out;
    }

    private void require(String key, Class<? extends Option> type) {
        Option o = options.get(key).orElseThrow(() -> new IllegalArgumentException("No option '" + key + "'"));
        if (!type.isInstance(o)) {
            throw new IllegalArgumentException("Option '" + key + "' is a " + o.getClass().getSimpleName() + ", not a " + type.getSimpleName());
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OptionValues v && v.values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
