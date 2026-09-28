package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Plugin tool and action keys as Settings › Keybinds edits them (no toolkit needed). */
class PluginKeysTest {
    private final Settings settings = new Settings();
    private final Keybinds keys = new Keybinds(settings);
    private final PluginKeys pk = new PluginKeys(settings, keys);

    private static final PluginKeys.Target PALETTE = new PluginKeys.Target(PluginKeys.Kind.TOOL, "palette-tools/palette",
            "Palette Tools", "Tool: Palette", "Shift+P");
    private static final PluginKeys.Target REGION = new PluginKeys.Target(PluginKeys.Kind.TOOL, "terrain/region",
            "Terrain Generator", "Tool: Terrain region", null);
    private static final PluginKeys.Target COPY = new PluginKeys.Target(PluginKeys.Kind.ACTION, "resource-tracker/Copy materials list",
            "BlockCompanion Plugin", "Copy materials list", null);

    /** A key one of the app's own actions uses by default. */
    private static KeyCombination appKey() {
        return Arrays.stream(Keybinds.Action.values()).filter(a -> !a.held && !a.defaults().isEmpty())
                .map(a -> a.defaults().getFirst()).findFirst().orElseThrow();
    }

    @Test
    void defaultKeyUntilTheUserChangesIt() {
        assertThat(pk.bound(PALETTE)).isEqualTo(KeyCombination.valueOf("Shift+P"));
        assertThat(pk.effective(PALETTE)).isEqualTo(KeyCombination.valueOf("Shift+P"));
        assertThat(pk.isDefault(PALETTE)).isTrue();
        assertThat(pk.bound(REGION)).as("no default").isNull();

        pk.set(PALETTE, KeyCombination.valueOf("Ctrl+Alt+P"));
        assertThat(pk.effective(PALETTE)).isEqualTo(KeyCombination.valueOf("Ctrl+Alt+P"));
        assertThat(settings.pluginToolKeys).containsKey("palette-tools/palette");
        assertThat(pk.isDefault(PALETTE)).isFalse();

        pk.set(PALETTE, null);
        assertThat(settings.pluginToolKeys.get("palette-tools/palette")).as("\"\" means no key").isEmpty();
        assertThat(pk.bound(PALETTE)).isNull();

        pk.set(PALETTE, KeyCombination.valueOf("Shift+P"));
        assertThat(settings.pluginToolKeys).as("back to the default drops the entry").doesNotContainKey("palette-tools/palette");
        pk.set(PALETTE, KeyCombination.valueOf("J"));
        pk.reset(PALETTE);
        assertThat(pk.isDefault(PALETTE)).isTrue();
        assertThat(pk.defaultText(PALETTE)).isEqualTo("Shift+P");
        assertThat(pk.defaultText(REGION)).isEqualTo("no key");
    }

    @Test
    void anAppKeyWins() {
        KeyCombination app = appKey();
        pk.set(REGION, app);
        assertThat(pk.bound(REGION)).isEqualTo(app);
        assertThat(pk.effective(REGION)).as("the app's action keeps its key").isNull();
        assertThat(pk.clashes(app, REGION, List.of(REGION))).isNotEmpty();
    }

    @Test
    void actionsHaveNoKeyUntilGivenOne() {
        assertThat(pk.bound(COPY)).isNull();
        pk.set(COPY, KeyCombination.valueOf("Ctrl+Shift+C"));
        assertThat(pk.effective(COPY)).isEqualTo(KeyCombination.valueOf("Ctrl+Shift+C"));
        assertThat(settings.pluginActionKeys).containsEntry("resource-tracker/Copy materials list", KeyCombination.valueOf("Ctrl+Shift+C").getName());
        pk.set(COPY, null);
        assertThat(settings.pluginActionKeys).as("none is the default for actions").isEmpty();
    }

    @Test
    void pluginKeysClashWithEachOther() {
        pk.set(REGION, KeyCombination.valueOf("Shift+P"));
        assertThat(pk.clashes(KeyCombination.valueOf("Shift+P"), REGION, List.of(PALETTE, REGION, COPY)))
                .containsExactly("Palette Tools: Tool: Palette");
        assertThat(pk.clashes(null, REGION, List.of(PALETTE))).isEmpty();
    }

    @Test
    void resetToDefaultsClearsPluginKeysPagesAndOpenGroups() {
        pk.set(PALETTE, KeyCombination.valueOf("J"));
        pk.set(COPY, KeyCombination.valueOf("K"));
        settings.pluginPages.put("resource-tracker", "blockcompanion");
        settings.expandedOptionGroups.add("pixel-art-generator/importer/image#Picture adjustments");
        settings.resetToDefaults();
        assertThat(settings.pluginToolKeys).isEmpty();
        assertThat(settings.pluginActionKeys).isEmpty();
        assertThat(settings.pluginPages).isEmpty();
        assertThat(settings.expandedOptionGroups).isEmpty();
        assertThat(pk.bound(PALETTE)).isEqualTo(KeyCombination.valueOf("Shift+P"));
    }
}
