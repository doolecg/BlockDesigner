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
        this.viewport = viewport;
        add(ws, group, ToolKind.VIEW, Feather.EYE, "View mode", Keybinds.Action.TOOL_VIEW, "look around, no editing");
        add(ws, group, ToolKind.SELECT, Feather.MOUSE_POINTER, "Select mode", Keybinds.Action.TOOL_SELECT, "click to select layers, drag to slide them");
        add(ws, group, ToolKind.BUILD, ToolIcons.build(17), "Build mode", Keybinds.Action.TOOL_BUILD, "left break, right place, middle pick (the key toggles it, also while flying)");
        add(ws, group, ToolKind.MOVE, Feather.MOVE, "Move", Keybinds.Action.TOOL_MOVE, "drag an arrow, a plane square or the centre to move the selected layers (or the selected blocks)");
        add(ws, group, ToolKind.ROTATE, Feather.ROTATE_CW, "Rotate", Keybinds.Action.TOOL_ROTATE, "drag a ring to turn the selected layers (or blocks) in 90° steps");
        add(ws, group, ToolKind.BRUSH, ToolIcons.brush(17), "Paint brush", Keybinds.Action.TOOL_BRUSH, "drag to add blocks with the held block (right-drag smooths); - / = size");
        add(ws, group, ToolKind.ERASER, ToolIcons.eraser(17), "Eraser", Keybinds.Action.TOOL_ERASER, "drag to remove blocks; - / = size");
        getChildren().add(new Separator());
        flyToggle.getStyleClass().addAll("flat", "tool-button");
        flyToggle.setTooltip(new Tooltip("Creative flight (C): WASD, Space/Shift, mouse look"));
        flyToggle.setOnAction(e -> viewport.setFly(flyToggle.isSelected()));
        viewport.onFlyChanged(() -> flyToggle.setSelected(viewport.isFlying()));
        getChildren().add(flyToggle);
        ws.toolProperty().addListener((o, a, b) -> buttons.get(b).setSelected(true));
        buttons.get(ws.toolProperty().get()).setSelected(true);
    }

    private ViewportPane viewport;

    private void add(Workspace ws, ToggleGroup g, ToolKind kind, Feather icon, String name, Keybinds.Action key, String tip) {
        add(ws, g, kind, new FontIcon(icon), name, key, tip);
    }

    private void add(Workspace ws, ToggleGroup g, ToolKind kind, javafx.scene.Node icon, String name, Keybinds.Action key, String tip) {
        ToggleButton b = new ToggleButton(null, icon);
        b.getStyleClass().addAll("flat", "tool-button");
        b.setToggleGroup(g);
        // The key is looked up each time the tooltip shows, so it follows Settings › Keybinds.
        Tooltip t = new Tooltip();
        t.setOnShowing(e -> {
            String k = viewport.keyText(key);
            t.setText(name + (k.isEmpty() ? "" : " (" + k + ")") + ": " + tip);
        });
        b.setTooltip(t);
        b.setOnAction(e -> {
            if (!b.isSelected()) b.setSelected(true);
            ws.toolProperty().set(kind);
        });
        buttons.put(kind, b);
        getChildren().add(b);
    }
}
