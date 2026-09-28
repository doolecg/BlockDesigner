package io.blockdesigner.app.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PluginCatalogTest {
    private static List<String> ids(List<PluginCatalog.Entry> entries) {
        return entries.stream().map(PluginCatalog.Entry::id).toList();
    }

    @Test
    void suggestsTheHousePluginsButNotTheBuiltInOnes() {
        assertThat(ids(PluginCatalog.SUGGESTED)).containsExactly("palette-tools", "terrain-generator", "pixel-art-generator",
                "ai-builder");
        // Reference Planes is part of the app now; the BlockCompanion Plugin installs by itself.
        assertThat(ids(PluginCatalog.SUGGESTED)).doesNotContain("reference-planes", "resource-tracker");
    }

    @Test
    void everySuggestionUpdatesFromItsGithubRepository() {
        for (PluginCatalog.Entry e : PluginCatalog.SUGGESTED) {
            assertThat(e.description()).isNotBlank();
            assertThat(PluginUpdater.repoOf(e.page())).isEqualTo(e.repo());
            assertThat(PluginUpdater.updatable(e.info())).isTrue();
            assertThat(e.info().id()).isEqualTo(e.id());
            assertThat(e.info().version()).isEmpty();
        }
    }

    @Test
    void hidesInstalledPluginsAndKeepsTheOrder() {
        assertThat(ids(PluginCatalog.notInstalled(PluginCatalog.SUGGESTED, Set.of())))
                .isEqualTo(ids(PluginCatalog.SUGGESTED));
        assertThat(ids(PluginCatalog.notInstalled(PluginCatalog.SUGGESTED, Set.of("terrain-generator", "resource-tracker", "hello"))))
                .containsExactly("palette-tools", "pixel-art-generator", "ai-builder");
        assertThat(PluginCatalog.notInstalled(PluginCatalog.SUGGESTED, ids(PluginCatalog.SUGGESTED))).isEmpty();
    }
}
