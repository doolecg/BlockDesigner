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
    private static final String[][][] SECTIONS = {
            {{"Modes"},
                    {"V", "View mode: look around only"},
                    {"Q", "Select mode"},
                    {"B", "Build mode on / off (also while flying)"},
                    {"G / E", "Move / Rotate tool (gizmos for the selected layers)"},
                    {"U / X", "Paint brush / Eraser"},
                    {"Esc", "Clear selection, then back to Select"}},
            {{"Camera"},
                    {"Middle-drag", "Orbit around the point under the mouse"},
                    {"Shift+middle-drag", "Pan"},
                    {"Middle-click", "Pick block (no drag)"},
                    {"Wheel", "Zoom"},
                    {"F / Shift+F", "Frame the selected blocks (or else the active layer) / everything"},
                    {"C", "Fly (creative flight) on / off"},
                    {"View cube", "Click a face for that view (again: opposite side), drag to orbit"},
                    {"Numpad 1 / 3 / 7", "Front / right / top (Ctrl: back / left / bottom)"},
                    {"P / O", "Perspective / orthographic"},
                    {"Numpad 5", "Toggle perspective / orthographic"},
                    {"Alt+middle-drag", "Swing to the next ortho view that way (left, right, top, bottom…)"},
                    {"Numpad 9", "Opposite side"},
                    {"Numpad 2 4 6 8", "Orbit 15°"},
                    {"Numpad .", "Frame active layer"}},
            {{"Flying"},
                    {"WASD", "Move (W/S follow where you look)"},
                    {"Space / Shift", "Up / down"},
                    {"Ctrl", "Sprint"},
                    {"Wheel", "Next / previous hotbar slot"},
                    {"C / Esc", "Stop flying"},
                    {"Reach", "5 blocks, like creative mode"}},
            {{"Build mode"},
                    {"Left-click", "Break (hold to repeat)"},
                    {"Right-click", "Place (hold to repeat); stairs, slabs, logs, doors, torches… orient like Minecraft"},
                    {"Middle-click", "Pick block into the hotbar"},
                    {"R", "Replace mode: right-click swaps the aimed block, keeping its facing (drag to paint)"},
                    {"Hold Alt", "Shape wheel: point at a shape (line, wall, floor, box, room, walls, circle, ring, cylinder, sphere, dome, pyramid) and let go; None is single blocks"},
                    {"Right-drag", "With a shape: drag it out, release to place (one undo step); fills only empty cells, or everything in Replace mode"},
                    {"Wheel (dragging)", "Height of boxes, rooms, walls and cylinders"},
                    {"Esc / left-click", "Cancel the shape being dragged"},
                    {"M", "Symmetry: mirror X / Y / Z and radial copies for shapes, placing and breaking"},
                    {"Shift+M", "Put the symmetry centre on the aimed block (and switch symmetry on)"}},
            {{"Mobs & other entities"},
                    {"Palette › Mobs", "Click a mob to hold it (colour, job, baby… below the grid)"},
                    {"Right-click", "Place it on the aimed block, facing you (Build mode)"},
                    {"Left-click", "Remove the aimed mob"},
                    {"Middle-click", "Hold one like the aimed mob"},
                    {"Alt+wheel", "Turn the aimed mob 22.5° · a painting: next picture"},
                    {"Click (Select)", "Select mobs (Shift adds); arrows move them, Delete removes"},
                    {"Click (View)", "Open or shut a chest or shulker box"}},
            {{"Select mode"},
                    {"Click / right-click", "pos1 / pos2: the WorldEdit region box (its blocks get selected)"},
                    {"Drag", "Marquee select"},
                    {"Shift / Ctrl", "Add to / remove from the selection"},
                    {"Alt+T", "Select by type, with layer and property filters"},
                    {"Ctrl+A / Alt+A", "Select every block of the active layer / deselect"},
                    {"Ctrl+J", "Copy the selected blocks to a new layer"},
                    {"Ctrl+R", "Fill the selected blocks with the held block"},
                    {"Delete", "Delete the selected blocks"},
                    {"Shift+right-click", "Menu: region fill, delete, replace, fix block shapes, select by type, copy to layer, hide, lock"},
                    {"Esc", "Clear the region and selection"}},
            {{"WorldEdit (T or /, like chat)"},
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
                    {"Alt+1 … Alt+0", "Draw · Erase · Smooth · Erode · Fill · Pinch · Raise · Lower · Flatten · Slope"},
                    {"Shift+right-click", "Brush settings: mode, size, strength, shape"},
                    {"Shift+drag", "Smooth (not while flying)"},
                    {"Ctrl+drag", "Inverse mode: erase, lower, fill… (not while flying)"},
                    {"- / =", "Smaller / bigger brush"},
                    {", / .", "Weaker / stronger brush"}},
            {{"Move & Rotate tools"},
                    {"Drag an arrow", "Move along that axis"},
                    {"Drag a square", "Move in that plane"},
                    {"Drag the centre", "Move freely in the view plane"},
                    {"Drag a ring", "Turn about that axis in 90° steps"},
                    {"Click a layer", "Select it (Shift adds)"},
                    {"Esc / right-click", "Cancel the drag"}},
            {{"Hotbar"},
                    {"1 - 9", "Hold a slot"},
                    {"Middle-click", "Record the block under the cursor"},
                    {"Drag from palette", "Put a block in a slot"},
                    {"Delete (Build mode)", "Remove the held block from its slot"},
                    {"Alt+C", "Clear the hotbar"},
                    {"Z", "Shuffle mode: place random blocks from the hotbar"}},
            {{"Moving layers"},
                    {"Ctrl+wheel", "Along the hovered face's axis (left / right elsewhere)"},
                    {"Ctrl+Shift+wheel", "Up / down"},
                    {"Shift+wheel", "Back / forward"},
                    {"Arrow keys", "Left / right, back / forward"},
                    {"Hold Tab", "Bigger steps"},
                    {"Alt+wheel", "Spin (over the top) or flip (over a side)"}},
            {{"Layers"},
                    {"[ / ]", "Make the layer below / above active"},
                    {"Ctrl+Shift+N", "New empty layer"},
                    {"Ctrl+D", "Duplicate the selected layers"},
                    {"Ctrl+M", "Merge the selected layers (or the active one down)"},
                    {"F2", "Rename the active layer"},
                    {"H / Alt+H", "Hide or show the selected layers / show every layer"},
                    {"Shift+H", "Ghost the selected layers"},
                    {"L", "Lock or unlock the selected layers"},
                    {"Shift+Delete", "Delete the selected layers"}},
            {{"Placing an import"},
                    {"Click / Enter", "Place"},
                    {"R / Alt+wheel", "Rotate"},
                    {"Esc", "Cancel"}},
            {{"Slice view"},
                    {"PgUp / PgDn", "Step through Y levels"},
                    {"Insert", "Single level on / off"}},
            {{"File & edit"},
                    {"Ctrl+O / Ctrl+I", "Open / import"},
                    {"Ctrl+S / Ctrl+Shift+S", "Save / save as"},
                    {"Ctrl+N", "New project"},
                    {"Ctrl+E / Ctrl+Shift+E", "Export schematic / worldgen data pack"},
                    {"Ctrl+F", "Search blocks"},
                    {"Ctrl+Z / Ctrl+Y", "Undo / redo"},
                    {"Alt+K / F1", "This list"},
                    {"N", "Viewport settings"},
                    {"Alt+G", "Ground grid on / off"},
                    {"Home", "Frame everything"},
                    {"F11", "Full screen"}},
    };

    private final ScrollPane scroll;

    ShortcutsPanel() {
        getStyleClass().add("shortcuts-panel");
        setSpacing(8);

        // Two columns of sections.
        VBox left = new VBox(10), right = new VBox(10);
        for (int i = 0; i < SECTIONS.length; i++) (i < (SECTIONS.length + 1) / 2 ? left : right).getChildren().add(section(SECTIONS[i]));
        HBox columns = new HBox(28, left, right);
        scroll = new ScrollPane(columns);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.setMaxHeight(560);
        getChildren().add(scroll);
    }

    /** Scrolls the list by a wheel delta (for wheel events that land outside the card). */
    void scroll(double deltaY) {
        double extra = scroll.getContent().getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        if (extra <= 0) return;
        scroll.setVvalue(Math.clamp(scroll.getVvalue() - deltaY / extra, scroll.getVmin(), scroll.getVmax()));
    }

    private static VBox section(String[][] rows) {
        Label h = new Label(rows[0][0].toUpperCase());
        h.getStyleClass().add("viewport-settings-section");
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(3);
        ColumnConstraints keys = new ColumnConstraints(130);
        keys.setHalignment(HPos.LEFT);
        g.getColumnConstraints().addAll(keys, new ColumnConstraints(250));
        for (int r = 1; r < rows.length; r++) {
            Label k = new Label(rows[r][0]);
            k.getStyleClass().add("shortcut-key");
            Label d = new Label(rows[r][1]);
            d.setWrapText(true);
            g.addRow(r - 1, k, d);
        }
        return new VBox(2, h, g);
    }
}
