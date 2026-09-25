package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Symmetry settings (M in Build mode), like Effortless Building's mirror and radial mirror: mirror planes across X,
 * Y and Z through a centre point, and N copies around a vertical axis. Applies to shapes, placing and breaking.
 */
final class SymmetryPopup extends Popup {
    private final Settings s;
    private final Runnable changed, centreHere;
    private final ToggleButton on = new ToggleButton("Off");
    private final ToggleButton mx = axis("X", "Mirror east ↔ west across the red plane");
    private final ToggleButton my = axis("Y", "Mirror up ↔ down across the green plane");
    private final ToggleButton mz = axis("Z", "Mirror north ↔ south across the blue plane");
    private final ComboBox<Integer> radial = new ComboBox<>();
    private final Label centre = new Label();
    private final ToggleButton onBlock = new ToggleButton("Block centre"), onEdge = new ToggleButton("Block edge");
    private final CheckBox flip = new CheckBox("Turn blocks to match (stairs, doors, logs…)");
    private final CheckBox planes = new CheckBox("Show planes and spokes");
    private boolean syncing;

    SymmetryPopup(Settings settings, Runnable changed, Runnable centreHere) {
        this.s = settings;
        this.changed = changed;
        this.centreHere = centreHere;
        setAutoHide(true);
        setHideOnEscape(true);

        Label title = new Label("Symmetry");
        title.getStyleClass().add("brush-title");
        on.getStyleClass().addAll("chip", "small");
        on.setFocusTraversable(false);
        on.setOnAction(e -> {
            s.symOn = on.isSelected();
            update();
        });
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(8, title, sp, on);
        head.setAlignment(Pos.CENTER_LEFT);

        for (ToggleButton b : new ToggleButton[]{mx, my, mz}) {
            b.setOnAction(e -> {
                s.symX = mx.isSelected();
                s.symY = my.isSelected();
                s.symZ = mz.isSelected();
                if (b.isSelected()) s.symOn = true;
                update();
            });
        }
        HBox mirrors = new HBox(6, mx, my, mz);

        radial.getItems().setAll(1, 2, 3, 4, 5, 6, 8, 12, 16);
        radial.setCellFactory(v -> radialCell());
        radial.setButtonCell(radialCell());
        radial.setFocusTraversable(false);
        radial.setOnAction(e -> {
            if (syncing || radial.getValue() == null) return;
            s.symRadial = radial.getValue();
            if (s.symRadial > 1) s.symOn = true;
            update();
        });

        ToggleGroup where = new ToggleGroup();
        for (ToggleButton t : new ToggleButton[]{onBlock, onEdge}) {
            t.getStyleClass().addAll("chip", "small");
            t.setToggleGroup(where);
            t.setFocusTraversable(false);
        }
        onBlock.setTooltip(new Tooltip("Mirror through the middle of the centre block: odd-width builds, the centre column stays single"));
        onEdge.setTooltip(new Tooltip("Mirror between two blocks: even-width builds"));
        onBlock.setOnAction(e -> {
            setOffset(true);
            update();
        });
        onEdge.setOnAction(e -> {
            setOffset(false);
            update();
        });
        Button here = new Button("Centre on aimed block", new FontIcon(Feather.CROSSHAIR));
        here.getStyleClass().add("small");
        here.setFocusTraversable(false);
        here.setTooltip(new Tooltip("Shift+M"));
        here.setOnAction(e -> {
            centreHere.run();
            sync();
        });
        centre.getStyleClass().add("layer-meta");

        flip.setFocusTraversable(false);
        flip.setOnAction(e -> {
            s.symFlip = flip.isSelected();
            update();
        });
        planes.setFocusTraversable(false);
        planes.setOnAction(e -> {
            s.symShowPlanes = planes.isSelected();
            update();
        });

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Mirror"), mirrors);
        g.addRow(1, new Label("Radial"), radial);
        g.addRow(2, new Label("Centre"), new VBox(4, centre, new HBox(6, onBlock, onEdge), here));
        Label hint = new Label("Shapes, placing and breaking in Build mode are repeated in every copy. M opens this; Shift+M centres on the aimed block.");
        hint.setWrapText(true);
        hint.setMaxWidth(290);
        hint.getStyleClass().add("layer-meta");
        VBox box = new VBox(10, head, g, flip, planes, hint);
        box.getStyleClass().add("brush-popup");
        box.setPrefWidth(320);
        getContent().add(box);
        sync();
    }

    private static ToggleButton axis(String text, String tip) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().addAll("chip", "symmetry-axis-" + text.toLowerCase());
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
        b.setPrefWidth(48);
        return b;
    }

    private static javafx.scene.control.ListCell<Integer> radialCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v == 1 ? "Off" : v + " copies around");
            }
        };
    }

    /** Moves the centre between the middle of its block and that block's corner (keeping the block). */
    private void setOffset(boolean blockCentre) {
        s.symCenter = new int[]{half(s.symCenter[0], blockCentre), half(s.symCenter[1], blockCentre), half(s.symCenter[2], blockCentre)};
    }

    private static int half(int v2, boolean blockCentre) {
        int block = Math.floorDiv(v2, 2);
        return 2 * block + (blockCentre ? 1 : 0);
    }

    private void update() {
        sync();
        changed.run();
    }

    void sync() {
        syncing = true;
        on.setSelected(s.symOn);
        on.setText(s.symOn ? "On" : "Off");
        mx.setSelected(s.symX);
        my.setSelected(s.symY);
        mz.setSelected(s.symZ);
        radial.setValue(s.symRadial);
        boolean blockCentre = Math.floorMod(s.symCenter[0], 2) == 1;
        onBlock.setSelected(blockCentre);
        onEdge.setSelected(!blockCentre);
        centre.setText(String.format("x %s · y %s · z %s", fmt(s.symCenter[0]), fmt(s.symCenter[1]), fmt(s.symCenter[2])));
        flip.setSelected(s.symFlip);
        planes.setSelected(s.symShowPlanes);
        syncing = false;
    }

    private static String fmt(int v2) {
        return v2 % 2 == 0 ? String.valueOf(v2 / 2) : String.format(java.util.Locale.ROOT, "%.1f", v2 / 2.0);
    }
}
