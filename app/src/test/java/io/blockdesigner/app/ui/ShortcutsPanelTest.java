package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The shortcuts list and every other key label come from the key binds. */
class ShortcutsPanelTest {
    @AfterEach
    void uninstall() {
        Keybinds.install(null);
    }

    @Test
    void everyTemplateNamesARealActionAndResolves() {
        Keybinds.install(new Keybinds(new Settings()));
        for (String[][] section : ShortcutsPanel.SECTIONS) {
            for (String[] row : section) {
                for (String cell : row) {
                    // valueOf throws for a mistyped {ACTION}; nothing may be left unfilled.
                    assertThat(ShortcutsPanel.keys(cell)).as(cell).doesNotContain("{");
                }
            }
        }
    }

    @Test
    void labelsFollowRebinds() {
        Settings s = new Settings();
        Keybinds k = new Keybinds(s);
        Keybinds.install(k);
        assertThat(ShortcutsPanel.keys("{TOOL_MOVE} / {TOOL_ROTATE}")).isEqualTo("G / R");
        assertThat(Keybinds.named("Symmetry", Keybinds.Action.SYMMETRY)).isEqualTo("Symmetry (M)");
        assertThat(Keybinds.keysOf(Keybinds.Action.PERSPECTIVE, Keybinds.Action.ORTHOGRAPHIC)).isEqualTo("P / O");

        k.set(Keybinds.Action.SYMMETRY, 0, Keybinds.parse("F7"));
        k.set(Keybinds.Action.TOOL_ROTATE, 0, null);
        assertThat(Keybinds.named("Symmetry", Keybinds.Action.SYMMETRY)).isEqualTo("Symmetry (F7)");
        assertThat(ShortcutsPanel.keys("{TOOL_MOVE} / {TOOL_ROTATE}")).isEqualTo("G / —");
        assertThat(Keybinds.named("Rotate", Keybinds.Action.TOOL_ROTATE)).isEqualTo("Rotate");
    }
}
