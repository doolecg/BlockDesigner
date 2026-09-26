package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final Options NONE = new Options(List.of());

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

    private final List<Option> options;

    private Options(List<Option> options) {
        this.options = List.copyOf(options);
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

    /** Values with every option at its default. */
    public OptionValues defaults() {
        return OptionValues.defaults(this);
    }

    /** Builds {@link Options}; keys must be unique and use {@code A-Z a-z 0-9 _ . -}. */
    public static final class Builder {
        private final Map<String, Option> options = new LinkedHashMap<>();

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
            return this;
        }

        public Options build() {
            return options.isEmpty() ? NONE : new Options(new ArrayList<>(options.values()));
        }
    }
}
