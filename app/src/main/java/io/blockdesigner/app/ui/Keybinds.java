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
        FILE("File & edit"), TOOLS("Tools"), BUILD("Building"), SELECTION("Selection"), LAYERS("Layers"), BRUSH("Brush & eraser"), VIEW("View & panels");

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

        TOOL_VIEW(Group.TOOLS, "View mode", "V"),
        TOOL_SELECT(Group.TOOLS, "Select mode", "Q"),
        TOOL_BUILD(Group.TOOLS, "Build mode (toggle)", "G"),
        TOOL_MOVE(Group.TOOLS, "Move", "W"),
        TOOL_ROTATE(Group.TOOLS, "Rotate", "E"),
        TOOL_BRUSH(Group.TOOLS, "Paint brush", "B"),
        TOOL_ERASER(Group.TOOLS, "Eraser", "F"),

        SHUFFLE(Group.BUILD, "Shuffle hotbar blocks on / off", "Shift+Z"),
        REPLACE_MODE(Group.BUILD, "Replace mode on / off", "Shift+X"),
        CLEAR_HOTBAR(Group.BUILD, "Clear the hotbar", "Shift+C"),
        COMMAND_BAR(Group.BUILD, "WorldEdit command line", "T", "Slash"),

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

        BRUSH_SMALLER(Group.BRUSH, "Smaller brush", "Minus", "Subtract"),
        BRUSH_BIGGER(Group.BRUSH, "Bigger brush", "Equals", "Add"),
        BRUSH_WEAKER(Group.BRUSH, "Weaker brush", "Comma"),
        BRUSH_STRONGER(Group.BRUSH, "Stronger brush", "Period"),

        SHORTCUTS(Group.VIEW, "Shortcuts list", "F1", "Alt+K"),
        KEY_HINTS(Group.VIEW, "Key hints on / off", "Shift+F1"),
        VIEWPORT_SETTINGS(Group.VIEW, "Viewport settings", "N"),
        FRAME(Group.VIEW, "Frame the selection (or else the active layer)", "Shift+F"),
        GRID(Group.VIEW, "Ground grid on / off", "Alt+G"),
        PERSPECTIVE(Group.VIEW, "Perspective view", "P"),
        ORTHOGRAPHIC(Group.VIEW, "Orthographic view", "O"),
        FULL_SCREEN(Group.VIEW, "Full screen", "F11");

        public final Group group;
        public final String label;
        final String[] defaults;

        Action(Group group, String label, String... defaults) {
            this.group = group;
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

    /** Whether the key event is one of the action's bindings. */
    public boolean matches(Action a, KeyEvent e) {
        for (KeyCombination k : get(a)) if (k != null && k.match(e)) return true;
        return false;
    }

    /** The other actions bound to {@code k} (for the editor's conflict warning). */
    public List<Action> usersOf(KeyCombination k, Action except) {
        List<Action> out = new ArrayList<>();
        if (k == null) return out;
        for (Action a : Action.values()) {
            if (a == except) continue;
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
            return null;
        }
    }

}
