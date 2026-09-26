package io.blockdesigner.app.ui;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Keyboard and mouse reference, shown in a side popover from the viewport's ⌘ button (Alt+K). */
final class ShortcutsPanel extends VBox {
    static final String[][][] SECTIONS = {
            {{"Modes"},
                    {"{TOOL_VIEW}", "View mode: look around only"},
                    {"{TOOL_SELECT}", "Select mode"},
                    {"{TOOL_BUILD}", "Build mode on / off (also while flying)"},
                    {"{TOOL_MOVE} / {TOOL_ROTATE} / {TOOL_SCALE}", "Move / Rotate / Scale tool (gizmos for the selected layers)"},
                    {"{TOOL_BRUSH} / {TOOL_ERASER}", "Paint brush / Eraser"},
                    {"{CANCEL}", "Clear selection, then back to Select"}},
            {{"Camera"},
                    {"Middle-drag", "Orbit around the point under the mouse"},
                    {"Shift+middle-drag", "Pan"},
                    {"Middle-click", "Pick block (no drag)"},
                    {"Wheel", "Zoom"},
                    {"{FRAME} / {FRAME_ALL}", "Focus on the selected blocks (or else the active layer) / everything"},
                    {"{FLY}", "Fly (creative flight) on / off"},
                    {"View cube", "Click a face for that view (again: opposite side), drag to orbit"},
                    {"{VIEW_FRONT} / {VIEW_RIGHT} / {VIEW_TOP}", "Front / right / top ({VIEW_BACK} / {VIEW_LEFT} / {VIEW_BOTTOM}: back / left / bottom)"},
                    {"{PERSPECTIVE} / {ORTHOGRAPHIC}", "Perspective / orthographic"},
                    {"{VIEW_ORTHO_TOGGLE}", "Toggle perspective / orthographic"},
                    {"Alt+middle-drag", "Swing to the next ortho view that way (left, right, top, bottom…)"},
                    {"{VIEW_OPPOSITE}", "Opposite side"},
                    {"{ORBIT_DOWN} {ORBIT_LEFT} {ORBIT_RIGHT} {ORBIT_UP}", "Orbit 15° (down, left, right, up)"},
                    {"{FRAME_ACTIVE}", "Frame active layer"}},
            {{"Flying"},
                    {"{FLY_FORWARD}{FLY_LEFT}{FLY_BACK}{FLY_RIGHT}", "Move (forward / back follow where you look)"},
                    {"{FLY_UP} / {FLY_DOWN}", "Up / down"},
                    {"{FLY_SPRINT}", "Sprint"},
                    {"Wheel", "Next / previous hotbar slot"},
                    {"{FLY} / {CANCEL}", "Stop flying"},
                    {"Reach", "5 blocks, like creative mode"}},
            {{"Build mode"},
                    {"Left-click", "Break (hold to repeat)"},
                    {"Right-click", "Place (hold to repeat); stairs, slabs, logs, doors, torches… orient like Minecraft"},
                    {"Middle-click", "Pick block into the hotbar"},
                    {"{REPLACE_MODE}", "Replace mode: right-click swaps the aimed block, keeping its facing (drag to paint)"},
                    {"Hold {SHAPE_WHEEL}", "Shape wheel: point at a shape (line, wall, floor, box, room, walls, circle, ring, cylinder, sphere, dome, pyramid) and let go; None is single blocks"},
                    {"Right-drag", "With a shape: drag it out, release to place (one undo step); fills only empty cells, or everything in Replace mode"},
                    {"Wheel (dragging)", "Height of boxes, rooms, walls and cylinders"},
                    {"{CANCEL} / left-click", "Cancel the shape being dragged"},
                    {"{SYMMETRY}", "Symmetry: mirror X / Y / Z and radial copies for shapes, placing and breaking"},
                    {"{SYMMETRY_CENTRE}", "Put the symmetry centre on the aimed block (and switch symmetry on)"}},
            {{"Mobs & other entities"},
                    {"Palette › Mobs", "Click a mob to hold it (colour, job, baby… below the grid)"},
                    {"Right-click", "Place it on the aimed block, facing you (Build mode)"},
                    {"Left-click", "Remove the aimed mob"},
                    {"Middle-click", "Hold one like the aimed mob"},
                    {"Alt+wheel", "Turn the aimed mob 22.5° · a painting: next picture (Shift+wheel in Build mode)"},
                    {"Click (Select)", "Select mobs (Shift adds); {NUDGE_FORWARD} {NUDGE_BACK} {NUDGE_LEFT} {NUDGE_RIGHT} move them, {DELETE} removes"},
                    {"Click (View)", "Open or shut a chest or shulker box"}},
            {{"Select mode"},
                    {"Click / right-click", "pos1 / pos2: the WorldEdit region box (its blocks get selected)"},
                    {"Drag", "Marquee select"},
                    {"Shift / Ctrl", "Add to / remove from the selection"},
                    {"{SELECT_BY_TYPE}", "Select or replace blocks by type"},
                    {"{SELECT_ALL} / {DESELECT}", "Select every block of the active layer / deselect"},
                    {"{TOOL_MOVE} / {TOOL_ROTATE} / {TOOL_SCALE}", "With blocks selected: move / rotate / scale just those blocks within their layer"},
                    {"{COPY_TO_LAYER}", "Copy the selected blocks to a new layer"},
                    {"{MOVE_TO_LAYER}", "Move the selected blocks to a new layer"},
                    {"Shift+right-click", "Menu: move the selected blocks to the active layer"},
                    {"{FILL_SELECTION}", "Fill the selected blocks with the held block"},
                    {"{DELETE}", "Delete the selected blocks"},
                    {"Shift+right-click", "Menu: region fill, delete, replace, fix block shapes, select by type, copy to layer, hide, lock"},
                    {"{CANCEL}", "Clear the region and selection"}},
            {{"WorldEdit ({COMMAND_BAR}, like chat)"},
                    {"/set <pattern>", "Fill the region: stone · 70%stone,30%andesite · hand"},
                    {"/replace [from] <to>", "Replace blocks in the region"},
                    {"/walls · /faces · /overlay", "Walls, all faces, a layer on top"},
                    {"/copy · /cut · /paste", "Clipboard (relative to pos1; -a skips air, -e takes mobs along)"},
                    {"/fixshapes", "Rejoin fences, walls, panes, redstone, rails and stair corners in the region"},
                    {"/rotate · /flip", "Turn or mirror the clipboard"},
                    {"/move · /stack", "Move or repeat the contents [n] [dir]"},
                    {"/expand · /contract · /shift", "Resize or move the region <n> [dir]"},
                    {"/sphere · /cyl · /pyramid", "Shapes at pos1 (h… for hollow)"},
                    {"/line · /count · /distr · /size", "Line pos1→pos2, and region info"},
                    {"/smooth · /naturalize", "Smooth the terrain · grass, dirt, stone by depth"},
                    {"/hollow · /center", "Hollow out, keeping a shell · mark the middle"},
                    {"/undo · /redo · /help", "Tab completes commands and block names, ↑ ↓ history"}},
            {{"Paint brush & Eraser"},
                    {"Drag", "Paint with the brush mode · Eraser: remove blocks"},
                    {"Right-drag", "Smooth (also while flying)"},
                    {"{BRUSH_MODE_1} … {BRUSH_MODE_10}", "Draw · Erase · Smooth · Erode · Fill · Pinch · Raise · Lower · Flatten · Slope"},
                    {"Shift+right-click", "Brush settings: mode, size, strength, shape"},
                    {"Shift+drag", "Smooth (not while flying)"},
                    {"Ctrl+drag", "Inverse mode: erase, lower, fill… (not while flying)"},
                    {"{BRUSH_SMALLER} / {BRUSH_BIGGER}", "Smaller / bigger brush"},
                    {"{BRUSH_WEAKER} / {BRUSH_STRONGER}", "Weaker / stronger brush"}},
            {{"Move & Rotate tools"},
                    {"Drag an arrow", "Move along that axis"},
                    {"Drag a square", "Move in that plane"},
                    {"Drag the centre", "Move freely in the view plane"},
                    {"Drag a ring", "Turn about that axis in 90° steps"},
                    {"Click a layer", "Select it (Shift adds)"},
                    {"{CANCEL} / right-click", "Cancel the drag"}},
            {{"Hotbar"},
                    {"{HOTBAR_1} - {HOTBAR_9}", "Hold a slot"},
                    {"Middle-click", "Record the block under the cursor"},
                    {"Drag from palette", "Put a block in a slot"},
                    {"{HOTBAR_1} - {HOTBAR_9} over a block", "Put the palette block under the mouse in that slot"},
                    {"{DELETE} (Build mode)", "Remove the held block from its slot"},
                    {"{CLEAR_HOTBAR}", "Clear the hotbar"},
                    {"{SHUFFLE}", "Shuffle mode: place random blocks from the hotbar"}},
            {{"Moving layers"},
                    {"Ctrl+wheel", "Along the axis of the layer's bounding-box side under the mouse (not the camera angle)"},
                    {"Ctrl+Shift+wheel", "Up / down"},
                    {"Shift+wheel", "Same as Ctrl+wheel (outside Build mode)"},
                    {"{NUDGE_LEFT} {NUDGE_RIGHT} {NUDGE_FORWARD} {NUDGE_BACK}", "Left / right, away / towards you"},
                    {"Hold {FAST_NUDGE}", "Bigger steps"},
                    {"Alt+wheel", "Turn by the bounding-box side under the mouse: spin (top or bottom) or flip (a side)"},
                    {"Build mode", "Wheel: hotbar slot · Alt+wheel: zoom · Shift+wheel: turn (as Alt+wheel above)"}},
            {{"Layers"},
                    {"{LAYER_BELOW} / {LAYER_ABOVE}", "Make the layer below / above active"},
                    {"{NEW_LAYER}", "New empty layer"},
                    {"{DUPLICATE_LAYERS}", "Duplicate the selected layers"},
                    {"{MERGE_LAYERS}", "Merge the selected layers (or the active one down)"},
                    {"{RENAME_LAYER}", "Rename the active layer"},
                    {"{HIDE_LAYERS} / {SHOW_ALL_LAYERS}", "Hide or show the selected layers / show every layer"},
                    {"{GHOST_LAYERS}", "Ghost the selected layers"},
                    {"{LOCK_LAYERS}", "Lock or unlock the selected layers"},
                    {"{DELETE_LAYERS}", "Delete the selected layers"}},
            {{"Placing an import"},
                    {"Click / {PLACE}", "Place"},
                    {"{ROTATE_PLACEMENT} / Alt+wheel", "Rotate"},
                    {"{CANCEL}", "Cancel"}},
            {{"Slice view"},
                    {"{SLICE_UP} / {SLICE_DOWN}", "Step through Y levels"},
                    {"{SLICE_SINGLE}", "Single level on / off"}},
            {{"File & edit"},
                    {"{OPEN} / {IMPORT}", "Open / import"},
                    {"{SAVE} / {SAVE_AS}", "Save / save as"},
                    {"{NEW_PROJECT}", "New project"},
                    {"{EXPORT} / {EXPORT_DATAPACK}", "Export schematic / worldgen data pack"},
                    {"{SEARCH_BLOCKS}", "Search blocks"},
                    {"{UNDO} / {REDO}", "Undo / redo"},
                    {"{SHORTCUTS}", "This list"},
                    {"{KEY_HINTS}", "Key hints in the corner on / off"},
                    {"{SETTINGS}", "Settings (Keybinds is one of its pages)"},
                    {"{VIEWPORT_SETTINGS}", "Viewport settings"},
                    {"{GRID}", "Ground grid on / off"},
                    {"{FRAME_ALL}", "Frame everything"},
                    {"{FULL_SCREEN}", "Full screen"}},
    };

    private final ScrollPane scroll = new ScrollPane();
    private final Label note = new Label();

    ShortcutsPanel() {
        getStyleClass().add("shortcuts-panel");
        setSpacing(8);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.setMaxHeight(560);
        note.getStyleClass().add("layer-meta");
        getChildren().addAll(scroll, note);
        refresh();
    }

    /** Rebuilds the list with the keys currently bound (called each time it opens). */
    void refresh() {
        // Two columns of sections.
        VBox left = new VBox(10), right = new VBox(10);
        for (int i = 0; i < SECTIONS.length; i++) (i < (SECTIONS.length + 1) / 2 ? left : right).getChildren().add(section(SECTIONS[i]));
        scroll.setContent(new HBox(28, left, right));
        note.setText(Keybinds.named("Change keys in Settings › Keybinds", Keybinds.Action.SETTINGS) + ".");
    }

    private static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("\\{([A-Z0-9_]+)}");

    /** Fills in "{ACTION}" with that action's current key ("—" when it has none). */
    static String keys(String template) {
        return KEY.matcher(template).replaceAll(m -> {
            String k = Keybinds.keyOf(Keybinds.Action.valueOf(m.group(1)));
            return java.util.regex.Matcher.quoteReplacement(k.isEmpty() ? "—" : k);
        });
    }

    /** Scrolls the list by a wheel delta (for wheel events that land outside the card). */
    void scroll(double deltaY) {
        double extra = scroll.getContent().getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        if (extra <= 0) return;
        scroll.setVvalue(Math.clamp(scroll.getVvalue() - deltaY / extra, scroll.getVmin(), scroll.getVmax()));
    }

    private static VBox section(String[][] rows) {
        Label h = new Label(keys(rows[0][0]).toUpperCase());
        h.getStyleClass().add("viewport-settings-section");
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(3);
        ColumnConstraints keys = new ColumnConstraints(130);
        keys.setHalignment(HPos.LEFT);
        g.getColumnConstraints().addAll(keys, new ColumnConstraints(250));
        for (int r = 1; r < rows.length; r++) {
            Label k = new Label(keys(rows[r][0]));
            k.getStyleClass().add("shortcut-key");
            // Several rebound keys can be long ("Ctrl+Num 1 / Ctrl+Num 3 / …"): wrap rather than cut them off.
            k.setWrapText(true);
            k.setMaxWidth(130);
            Label d = new Label(keys(rows[r][1]));
            d.setWrapText(true);
            g.addRow(r - 1, k, d);
        }
        return new VBox(2, h, g);
    }
}
