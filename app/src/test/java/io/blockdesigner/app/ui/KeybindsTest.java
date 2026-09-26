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
    void noTwoDefaultsClash() {
        Keybinds k = new Keybinds(new Settings());
        for (Keybinds.Action a : Keybinds.Action.values())
            for (KeyCombination c : k.get(a)) assertThat(k.usersOf(c, a)).as(a + " " + Keybinds.text(c)).isEmpty();
    }

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
        s.keybinds.put("TOOL_MOVE", "G|");
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

    static javafx.scene.input.KeyEvent press(KeyCode c, boolean shift, boolean ctrl, boolean alt) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", c, shift, ctrl, alt, false);
    }

    @Test
    void heldKeysTakeModifiersAloneAndMatchWhateverElseIsDown() {
        Settings s = new Settings();
        Keybinds k = new Keybinds(s);
        assertThat(k.holds(Keybinds.Action.FLY_DOWN, KeyCode.SHIFT)).isTrue();
        assertThat(k.holds(Keybinds.Action.FLY_SPRINT, KeyCode.CONTROL)).isTrue();
        assertThat(k.caps(Keybinds.Action.FLY_DOWN)).isEqualTo(List.of("Shift"));
        // Sprinting forward: W is still "forward" with Ctrl held.
        assertThat(k.matches(Keybinds.Action.FLY_FORWARD, press(KeyCode.W, false, true, false))).isTrue();

        k.set(Keybinds.Action.FLY_DOWN, 0, Keybinds.parse("Ctrl"));
        k.set(Keybinds.Action.FLY_SPRINT, 0, Keybinds.parse("Shift"));
        Keybinds again = new Keybinds(s);
        assertThat(again.holds(Keybinds.Action.FLY_DOWN, KeyCode.CONTROL)).isTrue();
        assertThat(again.holds(Keybinds.Action.FLY_DOWN, KeyCode.SHIFT)).isFalse();
        assertThat(again.holds(Keybinds.Action.FLY_SPRINT, KeyCode.SHIFT)).isTrue();
    }

    @Test
    void pressedKeysMatchExactly() {
        Keybinds k = new Keybinds(new Settings());
        assertThat(k.matches(Keybinds.Action.HOTBAR_1, press(KeyCode.DIGIT1, false, false, false))).isTrue();
        assertThat(k.matches(Keybinds.Action.HOTBAR_1, press(KeyCode.DIGIT1, false, false, true))).isFalse();
        assertThat(k.matches(Keybinds.Action.BRUSH_MODE_10, press(KeyCode.DIGIT0, false, false, true))).isTrue();
        assertThat(k.matches(Keybinds.Action.VIEW_BACK, press(KeyCode.NUMPAD1, false, true, false))).isTrue();
        assertThat(k.matches(Keybinds.Action.VIEW_FRONT, press(KeyCode.NUMPAD1, false, true, false))).isFalse();
        assertThat(k.matches(Keybinds.Action.CANCEL, press(KeyCode.ESCAPE, false, false, false))).isTrue();
        assertThat(k.caps(Keybinds.Action.SLICE_UP)).isEqualTo(List.of("PgUp"));
        // W is the Move tool and flying forward, which never meet: not a clash.
        assertThat(k.usersOf(k.get(Keybinds.Action.TOOL_MOVE)[0], Keybinds.Action.TOOL_MOVE)).isEmpty();
        assertThat(k.caps(Keybinds.Action.ORBIT_LEFT)).isEqualTo(List.of("Num 4"));
    }
}
