package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsTest {
    private static final PluginCommand.BlockResolver PARSE = BlockState::parse;

    private final Options options = Options.builder()
            .integer("radius", "Radius", 4, 1, 32)
            .decimal("amount", "Amount", 0.3, 0, 1)
            .toggle("mossy", "Moss", true)
            .block("edge", "Edge block", BlockState.of("stone_bricks"))
            .blockList("fill", "Fill", List.of(BlockState.of("stone"), BlockState.of("andesite")))
            .choice("axis", "Axis", List.of("x", "y", "z"), "y")
            .text("name", "Name", "hello")
            .file("image", "Image", List.of("png"))
            .build();

    @Test
    void defaultsAndTypedGetters() {
        OptionValues v = options.defaults();
        assertThat(v.integer("radius")).isEqualTo(4);
        assertThat(v.decimal("amount")).isEqualTo(0.3);
        assertThat(v.toggle("mossy")).isTrue();
        assertThat(v.block("edge")).isEqualTo(BlockState.of("stone_bricks"));
        assertThat(v.blockList("fill").blocks()).containsExactly(BlockState.of("stone"), BlockState.of("andesite"));
        assertThat(v.choice("axis")).isEqualTo("y");
        assertThat(v.text("name")).isEqualTo("hello");
        assertThat(v.file("image")).isEmpty();
        assertThat(Options.none().isEmpty()).isTrue();
    }

    @Test
    void withClampsAndChecksTypes() {
        OptionValues v = options.defaults().with("radius", 100).with("amount", -2.0).with("image", Path.of("a.png"));
        assertThat(v.integer("radius")).isEqualTo(32);
        assertThat(v.decimal("amount")).isEqualTo(0.0);
        assertThat(v.file("image")).contains(Path.of("a.png"));
        assertThat(options.defaults().integer("radius")).as("immutable").isEqualTo(4);
        assertThatThrownBy(() -> v.with("axis", "w")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> v.with("radius", "big")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> v.integer("amount")).hasMessageContaining("DecimalOption");
        assertThatThrownBy(() -> v.toggle("nope")).hasMessageContaining("No option");
    }

    @Test
    void stringsRoundTripAndBadValuesFallBack() {
        OptionValues v = options.defaults().with("radius", 7).with("amount", 0.75).with("mossy", false)
                .with("edge", BlockState.parse("oak_stairs[facing=east,half=top]"))
                .with("fill", BlockPattern.parse("70%stone,30%andesite", PARSE))
                .with("axis", "x").with("name", "a, b = c").with("image", Path.of("pics", "x.png"));
        Map<String, String> saved = v.toStrings();
        assertThat(OptionValues.fromStrings(options, saved, PARSE)).isEqualTo(v);

        Map<String, String> bad = new HashMap<>(saved);
        bad.put("radius", "lots");
        bad.put("axis", "w");
        bad.put("fill", "70%");
        bad.remove("name");
        bad.put("unknown", "1");
        OptionValues back = OptionValues.fromStrings(options, bad, PARSE);
        assertThat(back.integer("radius")).isEqualTo(4);
        assertThat(back.choice("axis")).isEqualTo("y");
        assertThat(back.blockList("fill")).isEqualTo(options.defaults().blockList("fill"));
        assertThat(back.text("name")).isEqualTo("hello");
        assertThat(back.decimal("amount")).isEqualTo(0.75);
    }

    @Test
    void builderRejectsBadKeysAndDefaults() {
        assertThatThrownBy(() -> Options.builder().toggle("a", "A", true).toggle("a", "A", false))
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> Options.builder().toggle("a b", "A", true)).hasMessageContaining("keys");
        assertThatThrownBy(() -> Options.builder().integer("n", "N", 50, 1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Options.builder().choice("c", "C", List.of("a"), "b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patternsParsePickAndPrint() {
        BlockPattern p = BlockPattern.parse("70%stone, 30%oak_stairs[facing=east,half=top]", PARSE);
        assertThat(p.entries()).hasSize(2);
        assertThat(p.entries().get(1).block()).isEqualTo(BlockState.parse("oak_stairs[facing=east,half=top]"));
        assertThat(BlockPattern.parse(p.toString(), PARSE)).isEqualTo(p);
        Random r = new Random(1);
        int stone = 0;
        for (int i = 0; i < 10_000; i++) if (p.pick(r).name().equals("minecraft:stone")) stone++;
        assertThat(stone).isBetween(6700, 7300);
        assertThat(BlockPattern.parse("stone,andesite", PARSE).entries()).allMatch(e -> e.weight() == 1);
        assertThatThrownBy(() -> BlockPattern.parse("x%stone", PARSE)).hasMessageContaining("weight");
        assertThatThrownBy(() -> BlockPattern.parse(" , ", PARSE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void showWhenHidesAnOptionOutsideItsChoices() {
        Options o = Options.builder()
                .choice("mode", "Mode", List.of("swap", "weather", "gradient"), "swap")
                .block("from", "Replace", BlockState.of("oak_planks"))
                .showWhen("mode", "swap")
                .decimal("amount", "Amount", 0.3, 0, 1)
                .showWhen("mode", "weather", "gradient")
                .toggle("always", "Always", true)
                .build();
        OptionValues v = o.defaults();
        assertThat(o.shown("from", v)).isTrue();
        assertThat(o.shown("amount", v)).isFalse();
        assertThat(o.shown("always", v)).isTrue();
        assertThat(o.shown("mode", v)).isTrue();
        OptionValues g = v.with("mode", "gradient");
        assertThat(o.shown("from", g)).isFalse();
        assertThat(o.shown("amount", g)).isTrue();
        // Hidden options keep their values.
        assertThat(g.block("from")).isEqualTo(BlockState.of("oak_planks"));
        assertThat(o.conditions("from")).containsExactly(new Options.Condition("mode", java.util.Set.of("swap")));
        assertThat(o.conditions("always")).isEmpty();
        // Several conditions: all must hold.
        Options two = Options.builder()
                .choice("mode", "Mode", List.of("a", "b"), "a")
                .choice("style", "Style", List.of("soft", "hard"), "soft")
                .decimal("blend", "Blend", 0.5, 0, 1)
                .showWhen("style", "soft")
                .showWhen("mode", "a")
                .build();
        OptionValues t = two.defaults();
        assertThat(two.shown("blend", t)).isTrue();
        assertThat(two.shown("blend", t.with("style", "hard"))).isFalse();
        assertThat(two.shown("blend", t.with("mode", "b"))).isFalse();

        assertThatThrownBy(() -> Options.builder().showWhen("mode", "x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Options.builder().toggle("a", "A", true).showWhen("a", "x")).hasMessageContaining("choice");
        assertThatThrownBy(() -> Options.builder().choice("m", "M", List.of("a"), "a").toggle("t", "T", true).showWhen("m", "b"))
                .hasMessageContaining("not a value");
    }

    // ---- layout metadata (API 6) ----------------------------------------------------------------------------------

    @Test
    void groupsKeepTheirOrderAndLeadingOptionsFormAnUntitledGroup() {
        Options o = Options.builder()
                .choice("mode", "Mode", List.of("a", "b"), "a")
                .group("Shape")
                .integer("width", "Width", 64, 1, 512)
                .toggle("hollow", "Hollow", false)
                .group("Empty")
                .advanced("Picture adjustments")
                .decimal("brightness", "Brightness", 0, -1, 1)
                .build();
        assertThat(o.groups()).containsExactly(
                new Options.Group(null, false, List.of("mode")),
                new Options.Group("Shape", false, List.of("width", "hollow")),
                new Options.Group("Picture adjustments", true, List.of("brightness")));
        assertThat(o.groups().stream().flatMap(g -> g.keys().stream()).toList())
                .as("every option in exactly one group, in order").isEqualTo(o.all().stream().map(Options.Option::key).toList());
        Options plain = Options.builder().toggle("a", "A", true).toggle("b", "B", false).build();
        assertThat(plain.groups()).containsExactly(new Options.Group(null, false, List.of("a", "b")));
        assertThat(Options.none().groups()).isEmpty();
        assertThatThrownBy(() -> Options.builder().group(" ")).hasMessageContaining("blank");
        assertThatThrownBy(() -> new Options.Group("", false, List.of())).hasMessageContaining("blank");
    }

    @Test
    void helpUnitsAndEnabledWhenAreStoredAndChecked() {
        Options o = Options.builder()
                .integer("width", "Width", 64, 1, 512).unit("blocks").help("How wide the result is")
                .decimal("tolerance", "Tolerance", 0.1, 0, 0.4).unit("%")
                .decimal("angle", "Angle", 0, -180, 180).unit("°")
                .toggle("relief", "Relief", false)
                .integer("depth", "Depth", 3, 1, 16).enabledWhen("relief")
                .build();
        assertThat(o.unit("width")).contains("blocks");
        assertThat(o.help("width")).contains("How wide the result is");
        assertThat(o.unit("tolerance")).contains("%");
        assertThat(o.unit("angle")).contains("°");
        assertThat(o.help("angle")).isEmpty();
        assertThat(o.unit("relief")).isEmpty();
        assertThat(o.enabledWhen("depth")).contains("relief");
        assertThat(o.enabledWhen("width")).isEmpty();
        OptionValues v = o.defaults();
        assertThat(o.enabled("depth", v)).isFalse();
        assertThat(o.enabled("depth", v.with("relief", true))).isTrue();
        assertThat(o.enabled("width", v)).isTrue();

        assertThatThrownBy(() -> Options.builder().help("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Options.builder().unit("px")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Options.builder().enabledWhen("t")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Options.builder().toggle("t", "T", true).unit("px")).hasMessageContaining("integer and decimal");
        assertThatThrownBy(() -> Options.builder().integer("i", "I", 1, 0, 1).unit("%")).hasMessageContaining("between 0 and 1");
        assertThatThrownBy(() -> Options.builder().decimal("d", "D", 1, 0, 2).unit("%")).hasMessageContaining("between 0 and 1");
        assertThatThrownBy(() -> Options.builder().integer("i", "I", 1, 0, 1).toggle("t", "T", true).enabledWhen("t"))
                .as("not the option itself").hasMessageContaining("earlier toggle");
        assertThatThrownBy(() -> Options.builder().choice("c", "C", List.of("a"), "a").integer("i", "I", 1, 0, 1).enabledWhen("c"))
                .hasMessageContaining("earlier toggle");
    }

    @Test
    void optionCopiesARecordFromOtherOptions() {
        Options from = Options.builder()
                .decimal("amount", "Amount", 0.3, 0, 1).unit("%").help("How much")
                .toggle("mossy", "Moss", true)
                .build();
        Options.Builder b = Options.builder().choice("mode", "Mode", List.of("x"), "x");
        for (Options.Option opt : from.all()) {
            b.option(opt);
            from.help(opt.key()).ifPresent(b::help);
            from.unit(opt.key()).ifPresent(b::unit);
        }
        Options copy = b.build();
        assertThat(copy.all()).hasSize(3);
        assertThat(copy.get("amount")).contains(from.get("amount").orElseThrow());
        assertThat(copy.unit("amount")).contains("%");
        assertThat(copy.help("amount")).contains("How much");
        assertThatThrownBy(() -> Options.builder().option(from.get("amount").orElseThrow()).option(from.get("amount").orElseThrow()))
                .hasMessageContaining("twice");
    }

    @Test
    void builtOptionsAreImmutable() {
        Options.Builder b = Options.builder().group("One").toggle("a", "A", true).help("first");
        Options first = b.build();
        b.group("Two").toggle("b", "B", true).help("second");
        Options second = b.build();
        assertThat(first.groups()).hasSize(1);
        assertThat(first.help("b")).isEmpty();
        assertThat(second.groups()).hasSize(2);
        assertThatThrownBy(() -> first.groups().add(new Options.Group("X", false, List.of()))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.groups().getFirst().keys().add("z")).isInstanceOf(UnsupportedOperationException.class);
    }
}
