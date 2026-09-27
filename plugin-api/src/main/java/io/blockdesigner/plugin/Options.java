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
 *
 * <p>Since API 6 options can also be laid out: {@link Builder#group headings}, {@link Builder#advanced collapsed
 * groups}, a line of {@link Builder#help help} under an option, a {@link Builder#unit unit} after a number and
 * options {@link Builder#enabledWhen greyed out} while a toggle is off. That is metadata only: older BlockDesigners
 * ignore it and show the options as one list.
 */
public final class Options {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Options NONE = new Options(List.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of());

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

    /**
     * A heading over some options, in the order they are shown (API 6). The options added before the first
     * {@link Builder#group} form a group without a title ({@code title} null).
     *
     * @param title    the heading, or null for the untitled leading group
     * @param advanced drawn collapsed until opened (see {@link Builder#advanced})
     * @param keys     the options under it, in order
     */
    public record Group(String title, boolean advanced, List<String> keys) {
        public Group {
            if (title != null && title.isBlank()) throw new IllegalArgumentException("A group title can't be blank");
            keys = List.copyOf(keys);
        }
    }

    private final List<Option> options;
    private final Map<String, List<Condition>> conditions;
    private final List<Group> groups;
    private final Map<String, String> help;
    private final Map<String, String> units;
    private final Map<String, String> enabledWhen;

    private Options(List<Option> options, Map<String, List<Condition>> conditions, List<Group> groups, Map<String, String> help,
                    Map<String, String> units, Map<String, String> enabledWhen) {
        this.options = List.copyOf(options);
        Map<String, List<Condition>> c = new LinkedHashMap<>();
        conditions.forEach((k, v) -> c.put(k, List.copyOf(v)));
        this.conditions = Map.copyOf(c);
        this.groups = List.copyOf(groups);
        this.help = Map.copyOf(help);
        this.units = Map.copyOf(units);
        this.enabledWhen = Map.copyOf(enabledWhen);
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

    // ---- layout metadata (API 6) -------------------------------------------------------------------------------

    /**
     * The options under their headings, in order; every option is in exactly one group. Options without any
     * {@link Builder#group} come back as one untitled group; empty for no options. Since API 6.
     */
    public List<Group> groups() {
        return groups;
    }

    /** The line of help shown under the option (a tooltip where space is tight), if it has one. Since API 6. */
    public Optional<String> help(String key) {
        return Optional.ofNullable(help.get(key));
    }

    /**
     * The unit shown after a number, such as "blocks" or "°", if it has one. {@code "%"} on a decimal means the value
     * is a fraction shown ×100. Since API 6.
     */
    public Optional<String> unit(String key) {
        return Optional.ofNullable(units.get(key));
    }

    /** The toggle option that the option {@code key} depends on (greyed out while it is off), if any. Since API 6. */
    public Optional<String> enabledWhen(String key) {
        return Optional.ofNullable(enabledWhen.get(key));
    }

    /**
     * Whether the option {@code key} can be changed with these values: false while the toggle it
     * {@link Builder#enabledWhen depends on} is off. Disabled options keep their values. Since API 6.
     */
    public boolean enabled(String key, OptionValues values) {
        String toggle = enabledWhen.get(key);
        return toggle == null || values.toggle(toggle);
    }

    /** Builds {@link Options}; keys must be unique and use {@code A-Z a-z 0-9 _ . -}. */
    public static final class Builder {
        private final Map<String, Option> options = new LinkedHashMap<>();
        private final Map<String, List<Condition>> conditions = new LinkedHashMap<>();
        private final List<Group> groups = new ArrayList<>();
        private final Map<String, String> help = new LinkedHashMap<>();
        private final Map<String, String> units = new LinkedHashMap<>();
        private final Map<String, String> enabledWhen = new LinkedHashMap<>();
        /** The group being filled: its title (null before the first group()), whether it is advanced, and its keys. */
        private String groupTitle;
        private boolean groupAdvanced;
        private List<String> groupKeys = new ArrayList<>();
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
            groupKeys.add(o.key());
            return this;
        }

        /**
         * Adds any option, such as one taken from another {@link Options} (a tool offering a transform's options).
         * Its help, unit and conditions are not copied; add them after it. Since API 6.
         */
        public Builder option(Option o) {
            Objects.requireNonNull(o, "option");
            return add(o);
        }

        /**
         * Puts the options added after this under a heading such as "Shape" (API 6; older BlockDesigners show one
         * list). A group left empty is dropped.
         */
        public Builder group(String title) {
            return startGroup(title, false);
        }

        /**
         * Like {@link #group}, but drawn collapsed until the user opens it: for settings most people never touch,
         * such as "Advanced" or "Picture adjustments". Since API 6.
         */
        public Builder advanced(String title) {
            return startGroup(title, true);
        }

        private Builder startGroup(String title, boolean advanced) {
            Objects.requireNonNull(title, "title");
            if (title.isBlank()) throw new IllegalArgumentException("A group title can't be blank");
            closeGroup();
            groupTitle = title.strip();
            groupAdvanced = advanced;
            return this;
        }

        private void closeGroup() {
            if (!groupKeys.isEmpty()) groups.add(new Group(groupTitle, groupAdvanced, groupKeys));
            groupKeys = new ArrayList<>();
        }

        /**
         * A line of help for the option added just before this call, shown under its control (or as its tooltip where
         * space is tight). Since API 6.
         */
        public Builder help(String text) {
            String key = requireLast("help");
            Objects.requireNonNull(text, "text");
            if (!text.isBlank()) help.put(key, text.strip());
            return this;
        }

        /**
         * A unit shown after the integer or decimal option added just before this call: "blocks", "px", "°",
         * "minutes"… {@code "%"} on a decimal between 0 and 1 shows the fraction as a percentage. Since API 6.
         */
        public Builder unit(String unit) {
            String key = requireLast("unit");
            Objects.requireNonNull(unit, "unit");
            Option o = options.get(key);
            if (!(o instanceof IntegerOption) && !(o instanceof DecimalOption))
                throw new IllegalArgumentException("unit applies to integer and decimal options, not '" + key + "'");
            if (unit.strip().equals("%") && !(o instanceof DecimalOption d && d.min() >= 0 && d.max() <= 1))
                throw new IllegalArgumentException("'%' needs a decimal option between 0 and 1: '" + key + "'");
            if (!unit.isBlank()) units.put(key, unit.strip());
            return this;
        }

        /**
         * Greys out the option added just before this call while the toggle option {@code toggleKey} (added earlier)
         * is off, and indents it under that toggle. It keeps its value. Since API 6.
         */
        public Builder enabledWhen(String toggleKey) {
            String key = requireLast("enabledWhen");
            if (!(options.get(toggleKey) instanceof ToggleOption) || toggleKey.equals(key))
                throw new IllegalArgumentException("enabledWhen needs an earlier toggle option, not '" + toggleKey + "'");
            enabledWhen.put(key, toggleKey);
            return this;
        }

        private String requireLast(String what) {
            if (last == null) throw new IllegalStateException(what + " follows the option it applies to");
            return last;
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
            if (options.isEmpty()) return NONE;
            List<Group> all = new ArrayList<>(groups);
            if (!groupKeys.isEmpty()) all.add(new Group(groupTitle, groupAdvanced, groupKeys));
            return new Options(new ArrayList<>(options.values()), conditions, all, help, units, enabledWhen);
        }
    }
}
