package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.Workspace.ToolKind;
import javafx.geometry.Pos;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;

/** Floating vertical tool bar over the viewport. */
public final class ToolDock extends VBox {
    private final Map<ToolKind, ToggleButton> buttons = new EnumMap<>(ToolKind.class);

    private final ToggleButton flyToggle = new ToggleButton(null, new FontIcon(Feather.NAVIGATION));

    public ToolDock(Workspace ws, ViewportPane viewport) {
        getStyleClass().add("tool-dock");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(2);
        setMaxHeight(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        ToggleGroup group = new ToggleGroup();
        add(ws, group, ToolKind.VIEW, Feather.EYE, "View mode (V): look around, no editing");
        add(ws, group, ToolKind.SELECT, Feather.MOUSE_POINTER, "Select mode (Q): click to select layers, drag to slide them");
        add(ws, group, ToolKind.BUILD, Feather.TOOL, "Build mode (B toggles, also while flying): left break, right place, middle pick");
        add(ws, group, ToolKind.MOVE, Feather.MOVE, "Move (G): drag an arrow, a plane square or the centre to move the selected layers");
        add(ws, group, ToolKind.ROTATE, Feather.ROTATE_CW, "Rotate (E): drag a ring to turn the selected layers in 90° steps");
        add(ws, group, ToolKind.BRUSH, Feather.EDIT_3, "Paint brush (U): drag to add blocks with the held block (right-drag erases); - / = size");
        add(ws, group, ToolKind.ERASER, Feather.DELETE, "Eraser (X): drag to remove blocks; - / = size");
        getChildren().add(new Separator());
        flyToggle.getStyleClass().addAll("flat", "tool-button");
        flyToggle.setTooltip(new Tooltip("Creative flight (C): WASD, Space/Shift, mouse look"));
        flyToggle.setOnAction(e -> viewport.setFly(flyToggle.isSelected()));
        viewport.onFlyChanged(() -> flyToggle.setSelected(viewport.isFlying()));
        getChildren().add(flyToggle);
        ws.toolProperty().addListener((o, a, b) -> buttons.get(b).setSelected(true));
        buttons.get(ws.toolProperty().get()).setSelected(true);
    }

    private void add(Workspace ws, ToggleGroup g, ToolKind kind, Feather icon, String tip) {
        ToggleButton b = new ToggleButton(null, new FontIcon(icon));
        b.getStyleClass().addAll("flat", "tool-button");
        b.setToggleGroup(g);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            if (!b.isSelected()) b.setSelected(true);
            ws.toolProperty().set(kind);
        });
        buttons.put(kind, b);
        getChildren().add(b);
    }
}
