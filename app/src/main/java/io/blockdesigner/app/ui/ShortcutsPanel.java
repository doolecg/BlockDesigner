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

/** Keyboard and mouse reference card shown over the viewport (Alt+K, or from the viewport settings). */
final class ShortcutsPanel extends VBox {
    private static final String[][][] SECTIONS = {
            {{"Modes"},
                    {"V", "View mode: look around only"},
                    {"Q", "Select mode"},
                    {"B", "Build mode on / off (also while flying)"},
                    {"Esc", "Clear selection, then back to Select"}},
            {{"Camera"},
                    {"Middle-drag", "Orbit around the point under the mouse"},
                    {"Shift+middle-drag", "Pan"},
                    {"Middle-click", "Pick block (no drag)"},
                    {"Wheel", "Zoom"},
                    {"F / Shift+F", "Frame active layer / everything"},
                    {"C", "Fly (creative flight) on / off"}},
            {{"Flying"},
                    {"WASD", "Move (W/S follow where you look)"},
                    {"Space / Shift", "Up / down"},
                    {"Ctrl", "Sprint"},
                    {"Wheel", "Next / previous hotbar slot"},
                    {"C / Esc", "Stop flying"},
                    {"Reach", "5 blocks, like creative mode"}},
            {{"Build mode"},
                    {"Left-click", "Break (hold to repeat)"},
                    {"Right-click", "Place (hold to repeat)"},
                    {"Middle-click", "Pick block into the hotbar"}},
            {{"Select mode"},
                    {"Click", "Select a block (empty space clears)"},
                    {"Drag", "Marquee select"},
                    {"Shift / Ctrl", "Add to / remove from the selection"},
                    {"Delete", "Delete the selected blocks"},
                    {"Right-click", "Menu: delete, replace, copy to layer, select all, hide, lock"}},
            {{"Hotbar"},
                    {"1 - 9", "Hold a slot"},
                    {"Middle-click", "Record the block under the cursor"},
                    {"Drag from palette", "Put a block in a slot"},
                    {"Alt+C", "Clear the hotbar"},
                    {"R", "Shuffle mode: place random blocks from the hotbar"}},
            {{"Moving layers"},
                    {"Ctrl+wheel", "Along the hovered face's axis (left / right elsewhere)"},
                    {"Ctrl+Shift+wheel", "Up / down"},
                    {"Shift+wheel", "Back / forward"},
                    {"Arrow keys", "Left / right, back / forward"},
                    {"Hold Tab", "Bigger steps"},
                    {"Alt+wheel", "Spin (over the top) or flip (over a side)"}},
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
                    {"Ctrl+E", "Export"},
                    {"Ctrl+Z / Ctrl+Y", "Undo / redo"},
                    {"Alt+K", "This list"}},
    };

    ShortcutsPanel(Runnable close) {
        getStyleClass().add("shortcuts-panel");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setSpacing(8);

        Label title = new Label("Keyboard shortcuts");
        title.getStyleClass().add("shortcuts-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button x = new Button(null, new FontIcon(Feather.X));
        x.getStyleClass().add("flat");
        x.setOnAction(e -> close.run());
        HBox header = new HBox(title, spacer, x);
        header.setAlignment(Pos.CENTER_LEFT);

        // Two columns of sections.
        VBox left = new VBox(10), right = new VBox(10);
        for (int i = 0; i < SECTIONS.length; i++) (i < (SECTIONS.length + 1) / 2 ? left : right).getChildren().add(section(SECTIONS[i]));
        HBox columns = new HBox(28, left, right);
        ScrollPane scroll = new ScrollPane(columns);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.setMaxHeight(560);
        getChildren().addAll(header, scroll);
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
