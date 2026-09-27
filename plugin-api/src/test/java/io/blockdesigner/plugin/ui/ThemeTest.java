package io.blockdesigner.plugin.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The kit's stylesheet has every documented class and pseudo-class, and uses no fixed colours. No toolkit needed. */
class ThemeTest {
    /** The stable style classes PLUGINS.md documents. */
    static final List<String> CLASSES = List.of("bd-root", "bd-scaffold", "bd-top", "bd-content", "bd-footer", "bd-section",
            "bd-section-header", "bd-section-title", "bd-hint", "bd-caption", "bd-form", "bd-form-label", "bd-action-bar",
            "bd-badge", "bd-banner", "bd-empty", "bd-list", "bd-row", "bd-row-title", "bd-row-meta", "bd-segmented",
            "bd-segment", "bd-block-slot", "bd-icon", "bd-card", "bd-mono", "bd-search", "bd-progress", "bd-inline-field");
    static final List<String> PSEUDO = List.of(":narrow", ":error", ":accent", ":success", ":warning", ":danger", ":busy");

    private static String css() throws IOException {
        try (InputStream in = ThemeTest.class.getResourceAsStream("/io/blockdesigner/plugin/ui/bd.css")) {
            assertThat(in).as("bd.css is in the jar next to Theme").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyDocumentedClassAndPseudoClassIsStyled() throws IOException {
        String css = css();
        for (String c : CLASSES) {
            assertThat(Pattern.compile("\\." + Pattern.quote(c) + "(?![\\w-])").matcher(css).find()).as("." + c).isTrue();
        }
        for (String p : PSEUDO) assertThat(Pattern.compile(Pattern.quote(p) + "(?![\\w-])").matcher(css).find()).as(p).isTrue();
    }

    @Test
    void onlyThemeColours() throws IOException {
        String rules = css().replaceAll("(?s)/\\*.*?\\*/", "");
        assertThat(Pattern.compile("#[0-9a-fA-F]{3,8}\\b").matcher(rules).find()).as("no hex colours").isFalse();
        assertThat(rules).doesNotContain("rgb(").doesNotContain("rgba(").doesNotContain("white").doesNotContain("black");
    }

    @Test
    void iconsHavePathsAndTheLicenceShips() throws IOException {
        for (Icon i : Icon.values()) {
            assertThat(i.path()).as(i.name()).isNotBlank().startsWith("M").doesNotContain("NaN");
        }
        try (InputStream in = ThemeTest.class.getResourceAsStream("/io/blockdesigner/plugin/ui/FEATHER-LICENSE.txt")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("MIT").contains("Cole Bemis");
        }
    }
}
