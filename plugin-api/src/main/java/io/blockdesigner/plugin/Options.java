package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parameters a plugin feature asks the user for (a transform's strength, an exporter's scale, a tool's block…),
 * described declaratively. BlockDesigner draws the controls itself (in the transform dialog, the export card, the
 * tool's options bar), remembers the last values per feature, and hands them over as {@link OptionValues}, so a plugin
 * needs no JavaFX for its settings:
 *
 * <pre>{@code
 * Options.builder()
 *         .decimal("amount", "Amount", 0.3, 0, 1)
 *         .toggle("mossy", "Moss as well as cracks", true)
 *         .blockList("fill", "Fill with", List.of(BlockState.of("stone"), BlockState.of("andesite")))
 *         .build();
 * }</pre>
 */
public final class Options {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Options NONE = new Options(List.of(), Map.of());

    /** One parameter. {@link #key()} identifies it in {@link OptionValues}; {@link #label()} is shown next to its control. */
    public sealed interface Option permits IntegerOption, DecimalOption, ToggleOption, BlockOption, BlockListOption,
            ChoiceOption, TextOption, FileOption {
        String key();

        String label();
    }

    /** A whole number between {@code min} and {@code max} (a spinner). */
    public record IntegerOption(String key, String label, int defaultValue, int min, int max) implements Option {
        public IntegerOption {
            if (min > max || defaultValue < min || defaultValue > max) throw new IllegalArgumentException(key + ": default outside " + min + "…" + max);
        }
    }

    /** A number between {@code min} and {@code max} (a slider with a field). */
    public record DecimalOption(String key, String label, double defaultValue, double min, double max) implements Option {
        public DecimalOption {
            if (!(min <= max) || defaultValue < min || defaultValue > max) throw new IllegalArgumentException(key + ": default outside " + min + "…" + max);
        }
    }

    /** On or off (a check box). */
    public record ToggleOption(String key, String label, boolean defaultValue) implements Option {
    }

    /** One block state (a block picker; the held block is one click away). */
    public record BlockOption(String key, String label, BlockState defaultValue) implements Option {
        public BlockOption {
            Objects.requireNonNull(defaultValue, key + ": default block");
        }
    }

    /** A weighted mix of blocks such as 70% stone, 30% andesite (see {@link BlockPattern}). */
    public record BlockListOption(String key, String label, BlockPattern defaultValue) implements Option {
        public BlockListOption {
            Objects.requireNonNull(defaultValue, key + ": default pattern");
        }
    }

    /** One of a fixed list of words (a drop-down). */
    public record ChoiceOption(String key, String label, List<String> values, String defaultValue) implements Option {
        public ChoiceOption {
            values = List.copyOf(values);
            if (!values.contains(defaultValue)) throw new IllegalArgumentException(key + ": default '" + defaultValue + "' isn't one of " + values);
        }
    }

    /** Free text (a text field). */
    public record TextOption(String key, String label, String defaultValue) implements Option {
        public TextOption {
            defaultValue = defaultValue == null ? "" : defaultValue;
        }
    }

    /**
     * A file on disk (a field with a Browse button), empty until the user picks one.
     *
     * @param extensions accepted extensions without the dot, e.g. {@code png}; empty for any file
     */
    public record FileOption(String key, String label, List<String> extensions) implements Option {
        public FileOption {
            extensions = List.copyOf(extensions);
        }
    }

    /**
     * Shows an option only while a choice option has one of {@code values} (API 5): a tool with a "Mode" choice can
     * show just the options of the chosen mode.
     */
    public record Condition(String choiceKey, Set<String> values) {
        public Condition {
            Objects.requireNonNull(choiceKey, "choiceKey");
            values = Set.copyOf(values);
        }

        public boolean test(OptionValues v) {
            return values.contains(v.choice(choiceKey));
        }
    }

    private final List<Option> options;
    private final Map<String, List<Condition>> conditions;

    private Options(List<Option> options, Map<String, List<Condition>> conditions) {
        this.options = List.copyOf(options);
        Map<String, List<Condition>> c = new LinkedHashMap<>();
        conditions.forEach((k, v) -> c.put(k, List.copyOf(v)));
        this.conditions = Map.copyOf(c);
    }

    /** No parameters: the feature runs straight away. */
    public static Options none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Every option, in the order they are shown. */
    public List<Option> all() {
        return options;
    }

    public Optional<Option> get(String key) {
        for (Option o : options) if (o.key().equals(key)) return Optional.of(o);
        return Optional.empty();
    }

    public boolean isEmpty() {
        return options.isEmpty();
    }

    /** When the option {@code key} is shown: every condition must hold; empty when always (see {@link Builder#showWhen}). */
    public List<Condition> conditions(String key) {
        return conditions.getOrDefault(key, List.of());
    }

    /** Whether the option {@code key} is shown with these values. Hidden options keep their values. */
    public boolean shown(String key, OptionValues values) {
        for (Condition c : conditions(key)) if (!c.test(values)) return false;
        return true;
    }

    /** Values with every option at its default. */
    public OptionValues defaults() {
        return OptionValues.defaults(this);
    }

    /** Builds {@link Options}; keys must be unique and use {@code A-Z a-z 0-9 _ . -}. */
    public static final class Builder {
        private final Map<String, Option> options = new LinkedHashMap<>();
        private final Map<String, List<Condition>> conditions = new LinkedHashMap<>();
        private String last;

        private Builder() {
        }

        public Builder integer(String key, String label, int defaultValue, int min, int max) {
            return add(new IntegerOption(key, label, defaultValue, min, max));
        }

        public Builder decimal(String key, String label, double defaultValue, double min, double max) {
            return add(new DecimalOption(key, label, defaultValue, min, max));
        }

        public Builder toggle(String key, String label, boolean defaultValue) {
            return add(new ToggleOption(key, label, defaultValue));
        }

        public Builder block(String key, String label, BlockState defaultValue) {
            return add(new BlockOption(key, label, defaultValue));
        }

        /** A weighted block mix, starting as equal parts of {@code defaultBlocks}. */
        public Builder blockList(String key, String label, List<BlockState> defaultBlocks) {
            return add(new BlockListOption(key, label, BlockPattern.of(defaultBlocks)));
        }

        public Builder blockList(String key, String label, BlockPattern defaultValue) {
            return add(new BlockListOption(key, label, defaultValue));
        }

        public Builder choice(String key, String label, List<String> values, String defaultValue) {
            return add(new ChoiceOption(key, label, values, defaultValue));
        }

        public Builder text(String key, String label, String defaultValue) {
            return add(new TextOption(key, label, defaultValue));
        }

        public Builder file(String key, String label, List<String> extensions) {
            return add(new FileOption(key, label, extensions));
        }

        private Builder add(Option o) {
            if (!KEY.matcher(o.key()).matches()) throw new IllegalArgumentException("Option keys use A-Z, a-z, 0-9, _ . - only: " + o.key());
            if (options.putIfAbsent(o.key(), o) != null) throw new IllegalArgumentException("Option '" + o.key() + "' added twice");
            last = o.key();
            return this;
        }

        /**
         * Shows the option added just before this call only while the choice option {@code choiceKey} (added earlier)
         * is one of {@code values} (API 5; older BlockDesigners show it always). Called more than once, every
         * condition must hold.
         */
        public Builder showWhen(String choiceKey, String... values) {
            if (last == null) throw new IllegalStateException("showWhen follows the option it applies to");
            if (!(options.get(choiceKey) instanceof ChoiceOption c) || choiceKey.equals(last))
                throw new IllegalArgumentException("showWhen needs an earlier choice option, not '" + choiceKey + "'");
            for (String v : values)
                if (!c.values().contains(v)) throw new IllegalArgumentException("'" + v + "' is not a value of '" + choiceKey + "'");
            conditions.computeIfAbsent(last, k -> new ArrayList<>()).add(new Condition(choiceKey, Set.of(values)));
            return this;
        }

        public Options build() {
            return options.isEmpty() ? NONE : new Options(new ArrayList<>(options.values()), conditions);
        }
    }
}
