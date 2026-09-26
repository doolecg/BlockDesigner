package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeybindsTest {
    @Test
    void everyDefaultParses() {
        for (Keybinds.Action a : Keybinds.Action.values()) {
            assertThat(a.defaults()).as(a.name()).isNotEmpty().doesNotContainNull();
        }
    }

    @Test
    void changesAreSavedAndResetDropsThem() {
        Settings s = new Settings();
        Keybinds k = new Keybinds(s);
        KeyCombination shiftZ = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHIFT_DOWN);
        assertThat(k.get(Keybinds.Action.SHUFFLE)[0]).isEqualTo(shiftZ);
        assertThat(k.isDefault(Keybinds.Action.SHUFFLE)).isTrue();

        KeyCombination f6 = new KeyCodeCombination(KeyCode.F6);
        k.set(Keybinds.Action.SHUFFLE, 1, f6);
        assertThat(s.keybinds).containsEntry("SHUFFLE", shiftZ.getName() + "|F6");
        assertThat(new Keybinds(s).get(Keybinds.Action.SHUFFLE)).containsExactly(shiftZ, f6);

        k.set(Keybinds.Action.SHUFFLE, 0, null);
        assertThat(k.get(Keybinds.Action.SHUFFLE)[0]).isNull();
        k.reset(Keybinds.Action.SHUFFLE);
        assertThat(s.keybinds).isEmpty();
    }

    @Test
    void savedBindsEqualToTheDefaultsAreDropped() {
        Settings s = new Settings();
        s.keybinds.put("SHUFFLE", "Shift+Z|");
        s.keybinds.put("TOOL_MOVE", "W|");
        s.keybinds.put("TOOL_VIEW", "F7|");
        s.keybinds.put("NO_SUCH_ACTION", "K|");
        new Keybinds(s);
        assertThat(s.keybinds).containsOnlyKeys("TOOL_VIEW");
    }

    @Test
    void clashesAndCaps() {
        Keybinds k = new Keybinds(new Settings());
        KeyCombination z = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHIFT_DOWN);
        assertThat(k.usersOf(z, null)).containsExactly(Keybinds.Action.SHUFFLE);
        k.set(Keybinds.Action.TOOL_VIEW, 1, z);
        assertThat(k.usersOf(z, Keybinds.Action.TOOL_VIEW)).containsExactly(Keybinds.Action.SHUFFLE);
        assertThat(k.caps(Keybinds.Action.MOVE_TO_LAYER)).isEqualTo(List.of("Ctrl", "Shift", "J"));
        assertThat(k.caps(Keybinds.Action.LAYER_BELOW)).isEqualTo(List.of("["));
        assertThat(Keybinds.text(k.get(Keybinds.Action.REPLACE_MODE)[0])).isEqualTo("Shift+X");
        assertThat(k.get(Keybinds.Action.TOOL_ERASER)[1]).as("the old second keys are gone").isNull();
    }
}
