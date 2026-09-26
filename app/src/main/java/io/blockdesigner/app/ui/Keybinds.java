package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The rebindable keyboard shortcuts: each action has up to two key combinations (a main one and an alternative),
 * with defaults here and the user's changes kept in {@link Settings#keybinds} as {@code "Ctrl+Shift+J|Z"}.
 */
public final class Keybinds {
    /** Which part of the app an action belongs to (the editor groups them this way). */
    public enum Group {
        FILE("File & edit"), TOOLS("Tools"), BUILD("Building"), HOTBAR("Hotbar"), SELECTION("Selection"), LAYERS("Layers"),
        MOVE("Moving layers & placing"), BRUSH("Brush & eraser"), CAMERA("Camera & views"), FLYING("Flying"), VIEW("View & panels");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    public enum Action {
        UNDO(Group.FILE, "Undo", "Shortcut+Z"),
        REDO(Group.FILE, "Redo", "Shortcut+Y", "Shortcut+Shift+Z"),
        SAVE(Group.FILE, "Save", "Shortcut+S"),
        SAVE_AS(Group.FILE, "Save as", "Shortcut+Shift+S"),
        OPEN(Group.FILE, "Open", "Shortcut+O"),
        IMPORT(Group.FILE, "Import", "Shortcut+I"),
        EXPORT(Group.FILE, "Export schematic", "Shortcut+E"),
        EXPORT_DATAPACK(Group.FILE, "Export worldgen data pack", "Shortcut+Shift+E"),
        NEW_PROJECT(Group.FILE, "New project", "Shortcut+N"),
        SETTINGS(Group.FILE, "Settings", "Shortcut+Comma"),
        SEARCH_BLOCKS(Group.FILE, "Search blocks", "Shortcut+F"),

        // The toolbar's tools on the number keys in its order (Move, Rotate and Scale on letters, as in Blender). In Build mode the numbers
        // pick hotbar slots instead, as in Minecraft.
        TOOL_VIEW(Group.TOOLS, "View mode", "1"),
        TOOL_SELECT(Group.TOOLS, "Select mode", "2"),
        TOOL_BUILD(Group.TOOLS, "Build mode (toggle)", "3"),
        TOOL_MOVE(Group.TOOLS, "Move", "G"),
        TOOL_ROTATE(Group.TOOLS, "Rotate", "R"),
        TOOL_SCALE(Group.TOOLS, "Scale", "S"),
        TOOL_BRUSH(Group.TOOLS, "Paint brush", "4"),
        TOOL_ERASER(Group.TOOLS, "Eraser", "5"),

        SHUFFLE(Group.BUILD, "Shuffle hotbar blocks on / off", "Shift+Z"),
        REPLACE_MODE(Group.BUILD, "Replace mode on / off", "Shift+X"),
        CLEAR_HOTBAR(Group.BUILD, "Clear the hotbar", "Shift+C"),
        COMMAND_BAR(Group.BUILD, "WorldEdit command line", "T", "Slash"),
        SHAPE_WHEEL(Group.BUILD, true, "Shape wheel (hold)", "Alt"),
        SYMMETRY(Group.BUILD, "Symmetry settings", "M"),
        SYMMETRY_CENTRE(Group.BUILD, "Put the symmetry centre on the aimed block", "Shift+M"),
        DELETE(Group.BUILD, "Delete the selection (Build mode: empty the held hotbar slot)", "Delete", "Backspace"),
        CANCEL(Group.BUILD, "Cancel / clear the selection / back to Select", "Esc"),

        HOTBAR_1(Group.HOTBAR, "Hotbar slot 1", "1"),
        HOTBAR_2(Group.HOTBAR, "Hotbar slot 2", "2"),
        HOTBAR_3(Group.HOTBAR, "Hotbar slot 3", "3"),
        HOTBAR_4(Group.HOTBAR, "Hotbar slot 4", "4"),
        HOTBAR_5(Group.HOTBAR, "Hotbar slot 5", "5"),
        HOTBAR_6(Group.HOTBAR, "Hotbar slot 6", "6"),
        HOTBAR_7(Group.HOTBAR, "Hotbar slot 7", "7"),
        HOTBAR_8(Group.HOTBAR, "Hotbar slot 8", "8"),
        HOTBAR_9(Group.HOTBAR, "Hotbar slot 9", "9"),

        SELECT_BY_TYPE(Group.SELECTION, "Select or replace by type", "Alt+T"),
        SELECT_ALL(Group.SELECTION, "Select all in the active layer", "Shortcut+A"),
        DESELECT(Group.SELECTION, "Deselect", "Alt+A"),
        COPY_TO_LAYER(Group.SELECTION, "Copy selection to a new layer", "Shortcut+J"),
        MOVE_TO_LAYER(Group.SELECTION, "Move selection to a new layer", "Shortcut+Shift+J"),
        FILL_SELECTION(Group.SELECTION, "Fill the selection with the held block", "Shortcut+R"),

        NEW_LAYER(Group.LAYERS, "New empty layer", "Shortcut+Shift+N"),
        RENAME_LAYER(Group.LAYERS, "Rename the active layer", "F2"),
        DUPLICATE_LAYERS(Group.LAYERS, "Duplicate layers", "Shortcut+D"),
        MERGE_LAYERS(Group.LAYERS, "Merge layers", "Shortcut+M"),
        DELETE_LAYERS(Group.LAYERS, "Delete layers", "Shift+Delete"),
        HIDE_LAYERS(Group.LAYERS, "Hide / show layers", "H"),
        GHOST_LAYERS(Group.LAYERS, "Ghost layers", "Shift+H"),
        SHOW_ALL_LAYERS(Group.LAYERS, "Show every layer", "Alt+H"),
        LOCK_LAYERS(Group.LAYERS, "Lock / unlock layers", "L"),
        LAYER_BELOW(Group.LAYERS, "Active layer: the one below", "Open Bracket"),
        LAYER_ABOVE(Group.LAYERS, "Active layer: the one above", "Close Bracket"),

        NUDGE_LEFT(Group.MOVE, "Move layers left", "Left"),
        NUDGE_RIGHT(Group.MOVE, "Move layers right", "Right"),
        NUDGE_FORWARD(Group.MOVE, "Move layers away from you", "Up"),
        NUDGE_BACK(Group.MOVE, "Move layers towards you", "Down"),
        FAST_NUDGE(Group.MOVE, true, "Bigger steps (hold)", "Tab"),
        ROTATE_PLACEMENT(Group.MOVE, "Turn the import being placed", "R"),
        PLACE(Group.MOVE, "Place the import", "Enter"),
        SLICE_UP(Group.MOVE, "Slice view: level up", "Page Up"),
        SLICE_DOWN(Group.MOVE, "Slice view: level down", "Page Down"),
        SLICE_SINGLE(Group.MOVE, "Slice view: single level on / off", "Insert"),

        BRUSH_SMALLER(Group.BRUSH, "Smaller brush", "Open Bracket", "Minus", "Subtract"),
        BRUSH_BIGGER(Group.BRUSH, "Bigger brush", "Close Bracket", "Equals", "Add"),
        BRUSH_WEAKER(Group.BRUSH, "Weaker brush", "Comma"),
        BRUSH_STRONGER(Group.BRUSH, "Stronger brush", "Period"),
        BRUSH_MODE_1(Group.BRUSH, "Brush mode: Draw", "Alt+1"),
        BRUSH_MODE_2(Group.BRUSH, "Brush mode: Erase", "Alt+2"),
        BRUSH_MODE_3(Group.BRUSH, "Brush mode: Smooth", "Alt+3"),
        BRUSH_MODE_4(Group.BRUSH, "Brush mode: Erode", "Alt+4"),
        BRUSH_MODE_5(Group.BRUSH, "Brush mode: Fill", "Alt+5"),
        BRUSH_MODE_6(Group.BRUSH, "Brush mode: Pinch", "Alt+6"),
        BRUSH_MODE_7(Group.BRUSH, "Brush mode: Raise", "Alt+7"),
        BRUSH_MODE_8(Group.BRUSH, "Brush mode: Lower", "Alt+8"),
        BRUSH_MODE_9(Group.BRUSH, "Brush mode: Flatten", "Alt+9"),
        BRUSH_MODE_10(Group.BRUSH, "Brush mode: Slope", "Alt+0"),

        FRAME_ALL(Group.CAMERA, "Frame everything", "Home"),
        FRAME_ACTIVE(Group.CAMERA, "Frame the active layer", "Decimal"),
        VIEW_FRONT(Group.CAMERA, "Front view", "Numpad 1"),
        VIEW_BACK(Group.CAMERA, "Back view", "Shortcut+Numpad 1"),
        VIEW_RIGHT(Group.CAMERA, "Right view", "Numpad 3"),
        VIEW_LEFT(Group.CAMERA, "Left view", "Shortcut+Numpad 3"),
        VIEW_TOP(Group.CAMERA, "Top view", "Numpad 7"),
        VIEW_BOTTOM(Group.CAMERA, "Bottom view", "Shortcut+Numpad 7"),
        VIEW_OPPOSITE(Group.CAMERA, "The opposite view", "Numpad 9"),
        VIEW_ORTHO_TOGGLE(Group.CAMERA, "Perspective / orthographic", "Numpad 5"),
        ORBIT_LEFT(Group.CAMERA, "Orbit left 15°", "Numpad 4"),
        ORBIT_RIGHT(Group.CAMERA, "Orbit right 15°", "Numpad 6"),
        ORBIT_UP(Group.CAMERA, "Orbit up 15°", "Numpad 8"),
        ORBIT_DOWN(Group.CAMERA, "Orbit down 15°", "Numpad 2"),

        FLY(Group.FLYING, "Creative flight on / off", "C"),
        FLY_FORWARD(Group.FLYING, true, "Fly forward (hold)", "W"),
        FLY_BACK(Group.FLYING, true, "Fly back (hold)", "S"),
        FLY_LEFT(Group.FLYING, true, "Fly left (hold)", "A"),
        FLY_RIGHT(Group.FLYING, true, "Fly right (hold)", "D"),
        FLY_UP(Group.FLYING, true, "Fly up (hold)", "Space"),
        FLY_DOWN(Group.FLYING, true, "Fly down (hold)", "Shift"),
        FLY_SPRINT(Group.FLYING, true, "Sprint (hold)", "Ctrl"),

        SHORTCUTS(Group.VIEW, "Shortcuts list", "F1", "Alt+K"),
        KEY_HINTS(Group.VIEW, "Key hints on / off", "Shift+F1"),
        VIEWPORT_SETTINGS(Group.VIEW, "Viewport settings", "N"),
        FRAME(Group.VIEW, "Focus: frame the selection (or else the active layer)", "F"),
        GRID(Group.VIEW, "Ground grid on / off", "Alt+G"),
        PERSPECTIVE(Group.VIEW, "Perspective view", "P"),
        ORTHOGRAPHIC(Group.VIEW, "Orthographic view", "O"),
        FULL_SCREEN(Group.VIEW, "Full screen", "F11");

        public final Group group;
        public final String label;
        /**
         * A key that is held rather than pressed (flying, bigger steps, the shape wheel): bound to a single key,
         * modifiers included (Shift, Ctrl, Alt), and matched whatever else is held with it.
         */
        public final boolean held;
        final String[] defaults;

        Action(Group group, String label, String... defaults) {
            this(group, false, label, defaults);
        }

        Action(Group group, boolean held, String label, String... defaults) {
            this.group = group;
            this.held = held;
            this.label = label;
            this.defaults = defaults;
        }

        /** The default bindings (main, then alternative). */
        public List<KeyCombination> defaults() {
            List<KeyCombination> out = new ArrayList<>();
            for (String d : defaults) out.add(parse(d));
            return out;
        }
    }

    private final Settings settings;

    // ---- labels and tooltips ------------------------------------------------------------------------------------
    // Every key the UI shows (button tooltips, menu hints, toasts) comes from here, so it always matches Settings ›
    // Keybinds. MainWindow installs the app's instance.

    private static Keybinds current;

    static void install(Keybinds k) {
        current = k;
    }

    /** The main key bound to an action (or its alternative), e.g. "Shift+Z"; "" when unbound or not installed. */
    public static String keyOf(Action a) {
        if (current == null) return text(a.defaults().isEmpty() ? null : a.defaults().getFirst());
        KeyCombination[] k = current.get(a);
        return text(k[0] != null ? k[0] : k[1]);
    }

    /** The keys of several actions, e.g. "P / O"; "" when none is bound. */
    public static String keysOf(Action... actions) {
        List<String> keys = new ArrayList<>();
        for (Action a : actions) {
            String k = keyOf(a);
            if (!k.isEmpty()) keys.add(k);
        }
        return String.join(" / ", keys);
    }

    /** "Name (key)", or just the name when the actions have no key. */
    public static String named(String name, Action... actions) {
        String k = keysOf(actions);
        return k.isEmpty() ? name : name + " (" + k + ")";
    }

    /**
     * A tooltip reading "Title (key)" over a description, with the key looked up each time it shows (so a rebind
     * shows at once). Several actions list all their keys ("Perspective / orthographic (P / O)").
     */
    public static javafx.scene.control.Tooltip tooltip(String title, String description, Action... actions) {
        javafx.scene.control.Tooltip t = new javafx.scene.control.Tooltip();
        Runnable fill = () -> t.setText(named(title, actions) + (description == null || description.isBlank() ? "" : "\n" + description));
        fill.run();
        t.setOnShowing(e -> fill.run());
        return t;
    }

    public Keybinds(Settings settings) {
        this.settings = settings;
        if (settings.keybinds == null) settings.keybinds = new java.util.LinkedHashMap<>();
        // Saved binds that match today's defaults are no longer changes (and so follow future default changes).
        settings.keybinds.entrySet().removeIf(e -> {
            try {
                return defaultValue(Action.valueOf(e.getKey())).equals(normalise(e.getValue()));
            } catch (IllegalArgumentException unknown) {
                return true;
            }
        });
    }

    private static String defaultValue(Action a) {
        List<KeyCombination> d = a.defaults();
        return name(d.isEmpty() ? null : d.get(0)) + "|" + name(d.size() < 2 ? null : d.get(1));
    }

    private static String normalise(String saved) {
        String[] parts = saved.split("\\|", -1);
        return name(parts.length > 0 && !parts[0].isBlank() ? parse(parts[0]) : null) + "|"
                + name(parts.length > 1 && !parts[1].isBlank() ? parse(parts[1]) : null);
    }

    /** The action's two slots (main, alternative); an empty slot is null. */
    public KeyCombination[] get(Action a) {
        KeyCombination[] out = new KeyCombination[2];
        String saved = settings.keybinds == null ? null : settings.keybinds.get(a.name());
        if (saved == null) {
            List<KeyCombination> d = a.defaults();
            for (int i = 0; i < Math.min(2, d.size()); i++) out[i] = d.get(i);
            return out;
        }
        String[] parts = saved.split("\\|", -1);
        for (int i = 0; i < Math.min(2, parts.length); i++) out[i] = parts[i].isBlank() ? null : parse(parts[i]);
        return out;
    }

    /** Sets one slot (0 main, 1 alternative; null clears it); back to the defaults drops the saved entry. */
    public void set(Action a, int slot, KeyCombination k) {
        KeyCombination[] cur = get(a);
        cur[slot] = k;
        String value = name(cur[0]) + "|" + name(cur[1]);
        KeyCombination[] def = new KeyCombination[2];
        List<KeyCombination> d = a.defaults();
        for (int i = 0; i < Math.min(2, d.size()); i++) def[i] = d.get(i);
        if (value.equals(name(def[0]) + "|" + name(def[1]))) settings.keybinds.remove(a.name());
        else settings.keybinds.put(a.name(), value);
    }

    public void reset(Action a) {
        settings.keybinds.remove(a.name());
    }

    public void resetAll() {
        settings.keybinds.clear();
    }

    public boolean isDefault(Action a) {
        return !settings.keybinds.containsKey(a.name());
    }

    /** Whether the key event is one of the action's bindings (for a held action: its key, whatever else is down). */
    public boolean matches(Action a, KeyEvent e) {
        if (a.held) return holds(a, e.getCode());
        for (KeyCombination k : get(a)) if (k != null && k.match(e)) return true;
        return false;
    }

    /** Whether {@code code} is one of a held action's keys. */
    public boolean holds(Action a, KeyCode code) {
        for (KeyCombination k : get(a)) {
            if (k instanceof KeyCodeCombination kc && kc.getCode() == code) return true;
            if (k instanceof HeldKey h && h.code == code) return true;
        }
        return false;
    }

    /**
     * One key on its own, modifier keys included (JavaFX's KeyCodeCombination refuses Shift, Ctrl or Alt alone):
     * what a held action is bound to when that key is a modifier. It matches the key whatever else is down.
     */
    public static final class HeldKey extends KeyCombination {
        final KeyCode code;

        HeldKey(KeyCode code) {
            this.code = code;
        }

        @Override
        public boolean match(KeyEvent e) {
            return e.getCode() == code;
        }

        @Override
        public String getName() {
            return code.getName();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HeldKey h && h.code == code;
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    /** The action for a held key (e.g. which flying direction {@code code} is), or null. */
    public boolean isHeldKey(KeyCode code, Action... among) {
        for (Action a : among) if (holds(a, code)) return true;
        return false;
    }

    /**
     * Pairs that share a key on purpose, each acting in its own situation: the hotbar slots and the tools (Build mode
     * vs the others), turning an import being placed vs the Rotate tool, and [ / ] (brush size with the brush or
     * eraser, the active layer otherwise).
     */
    static boolean sharedByDesign(Action a, Action b) {
        return hotbarVsTool(a, b) || hotbarVsTool(b, a)
                || brushVsLayer(a, b) || brushVsLayer(b, a)
                || (a == Action.ROTATE_PLACEMENT && b == Action.TOOL_ROTATE) || (b == Action.ROTATE_PLACEMENT && a == Action.TOOL_ROTATE)
                || (a == Action.PLACE && b.group == Group.TOOLS) || (b == Action.PLACE && a.group == Group.TOOLS);
    }

    private static boolean brushVsLayer(Action a, Action b) {
        return (a == Action.BRUSH_SMALLER || a == Action.BRUSH_BIGGER) && (b == Action.LAYER_BELOW || b == Action.LAYER_ABOVE);
    }

    private static boolean hotbarVsTool(Action a, Action b) {
        return a.group == Group.HOTBAR && b.group == Group.TOOLS;
    }

    /** Whether the key event is one of the hotbar slot keys. */
    public boolean isHotbarKey(KeyEvent e) {
        for (Action a : Action.values()) if (a.group == Group.HOTBAR && matches(a, e)) return true;
        return false;
    }

    /** Whether the key event is one of the tool keys. */
    public boolean isToolKey(KeyEvent e) {
        for (Action a : Action.values()) if (a.group == Group.TOOLS && matches(a, e)) return true;
        return false;
    }

    /** Keys only held while flying, which take their keys over from every other action then (and only then). */
    static boolean whileFlying(Action a) {
        return a != null && a.group == Group.FLYING && a.held;
    }

    /**
     * The other actions bound to {@code k} (for the editor's conflict warning). The flying keys don't clash with the
     * rest: they only count while flying, when nothing else gets those keys (W is both Move and fly forward).
     */
    public List<Action> usersOf(KeyCombination k, Action except) {
        List<Action> out = new ArrayList<>();
        if (k == null) return out;
        for (Action a : Action.values()) {
            if (a == except) continue;
            if (except != null && whileFlying(a) != whileFlying(except)) continue;
            if (except != null && sharedByDesign(a, except)) continue;
            for (KeyCombination o : get(a)) if (o != null && o.equals(k)) out.add(a);
        }
        return out;
    }

    /** The main binding as keycap labels, e.g. ["Alt", "X"] (empty when unbound), for the key hints. */
    public List<String> caps(Action a) {
        KeyCombination[] k = get(a);
        KeyCombination main = k[0] != null ? k[0] : k[1];
        return main == null ? List.of() : caps(main);
    }

    /** "Ctrl+Shift+J" as keycap labels. */
    public static List<String> caps(KeyCombination k) {
        List<String> out = new ArrayList<>();
        if (k.getShortcut() == KeyCombination.ModifierValue.DOWN || k.getControl() == KeyCombination.ModifierValue.DOWN) out.add("Ctrl");
        if (k.getShift() == KeyCombination.ModifierValue.DOWN) out.add("Shift");
        if (k.getAlt() == KeyCombination.ModifierValue.DOWN) out.add("Alt");
        if (k instanceof HeldKey h) return List.of(keyName(h.code));
        if (k instanceof KeyCodeCombination kc) out.add(keyName(kc.getCode()));
        return out;
    }

    /** Short display text: "Ctrl+Shift+J", "Alt+X", "[". */
    public static String text(KeyCombination k) {
        return k == null ? "" : String.join("+", caps(k));
    }

    /** A key's short name as printed on a keycap. */
    static String keyName(KeyCode c) {
        return switch (c) {
            case OPEN_BRACKET -> "[";
            case CLOSE_BRACKET -> "]";
            case MINUS, SUBTRACT -> c == KeyCode.SUBTRACT ? "Num -" : "-";
            case EQUALS -> "=";
            case ADD -> "Num +";
            case COMMA -> ",";
            case PERIOD -> ".";
            case SLASH -> "/";
            case BACK_SLASH -> "\\";
            case SEMICOLON -> ";";
            case QUOTE -> "'";
            case BACK_QUOTE -> "`";
            case DELETE -> "Del";
            case BACK_SPACE -> "Backspace";
            case CONTROL -> "Ctrl";
            case ENTER -> "Enter";
            case INSERT -> "Ins";
            case HOME -> "Home";
            case TAB -> "Tab";
            // Spelled out: the key-label fonts don't all have arrow glyphs.
            case LEFT -> "Left";
            case RIGHT -> "Right";
            case UP -> "Up";
            case DOWN -> "Down";
            case DECIMAL -> "Num .";
            case NUMPAD0, NUMPAD1, NUMPAD2, NUMPAD3, NUMPAD4, NUMPAD5, NUMPAD6, NUMPAD7, NUMPAD8, NUMPAD9 -> "Num " + c.getName().substring(c.getName().length() - 1);
            case CONTEXT_MENU -> "Menu";
            case ESCAPE -> "Esc";
            case PAGE_UP -> "PgUp";
            case PAGE_DOWN -> "PgDn";
            case SPACE -> "Space";
            default -> {
                String n = c.getName();
                yield c.isDigitKey() && !c.isKeypadKey() ? n : n.length() > 1 && !n.matches("F\\d+") ? capitalise(n) : n;
            }
        };
    }

    private static String capitalise(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** The binding a held action records: the key alone (modifier keys too), whatever else is down. */
    public static KeyCombination heldFromEvent(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.UNDEFINED || c == KeyCode.WINDOWS || c == KeyCode.META || c == KeyCode.COMMAND) return null;
        return c.isModifierKey() ? new HeldKey(c) : new KeyCodeCombination(c);
    }

    /** The combination for a key event (null for a lone modifier), as the editor records it. */
    public static KeyCombination fromEvent(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c.isModifierKey() || c == KeyCode.UNDEFINED) return null;
        List<KeyCombination.Modifier> mods = new ArrayList<>();
        if (e.isShortcutDown()) mods.add(KeyCombination.SHORTCUT_DOWN);
        if (e.isShiftDown()) mods.add(KeyCombination.SHIFT_DOWN);
        if (e.isAltDown()) mods.add(KeyCombination.ALT_DOWN);
        return new KeyCodeCombination(c, mods.toArray(KeyCombination.Modifier[]::new));
    }

    private static String name(KeyCombination k) {
        return k == null ? "" : k.getName();
    }

    static KeyCombination parse(String s) {
        try {
            return KeyCombination.valueOf(s);
        } catch (RuntimeException e) {
            // A held key on its own ("Shift", "Ctrl", "Alt") isn't a combination JavaFX can parse.
            KeyCode c = KeyCode.getKeyCode(s.strip());
            return c == null ? null : new HeldKey(c);
        }
    }

}
