package io.blockdesigner.app.ui;

import atlantafx.base.controls.Popover;
import javafx.scene.Node;

/**
 * The one look for the viewport's icon popups (settings, shortcuts, select by type): a titled panel that opens beside
 * the icon it belongs to, with its arrow pointing back at that icon. Only one is open at a time.
 */
final class SidePopover {
    private static Popover open;

    private SidePopover() {
    }

    static Popover create(String title, Node content) {
        Popover p = new Popover(content);
        p.setTitle(title);
        p.setHeaderAlwaysVisible(true);
        p.setDetachable(false);
        p.setArrowLocation(Popover.ArrowLocation.RIGHT_TOP);
        // The same panel as every other menu (app.css › menus & popups): no arrow, the shared corner radius.
        p.setArrowSize(0);
        p.setArrowIndent(0);
        p.setCornerRadius(10);
        p.getStyleClass().add("side-popover");
        return p;
    }

    /** Shows {@code p} beside {@code icon}, closing any other side popover first. */
    static void show(Popover p, Node icon) {
        if (open != null && open != p && open.isShowing()) open.hide();
        open = p;
        p.show(icon);
    }
}
