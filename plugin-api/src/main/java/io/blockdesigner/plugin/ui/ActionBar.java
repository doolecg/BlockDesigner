package io.blockdesigner.plugin.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A row of at most three buttons for a page's or section's actions, 8 px apart. The {@link Controls#primary accent}
 * button takes the free width; a {@link Controls#spacer} pushes the buttons after it to the right. When the buttons
 * don't fit side by side (a narrow panel) they stack, full width, with {@code :narrow} set. Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class ActionBar extends HBox {
    private static final PseudoClass NARROW = PseudoClass.getPseudoClass("narrow");
    private boolean stacked;

    public ActionBar(Node... buttons) {
        Theme.attach(this);
        getStyleClass().add("bd-action-bar");
        setAlignment(Pos.CENTER_LEFT);
        long count = java.util.Arrays.stream(buttons).filter(b -> b instanceof ButtonBase).count();
        if (count > 3) throw new IllegalArgumentException("An action bar holds at most three buttons, not " + count);
        for (Node b : buttons) {
            if (b.getStyleClass().contains("accent") && b instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(r, Priority.ALWAYS);
            }
            if (b instanceof ButtonBase bb) bb.setMinWidth(Region.USE_PREF_SIZE);
        }
        getChildren().addAll(buttons);
    }

    private boolean fits(double width) {
        double need = snappedLeftInset() + snappedRightInset();
        int n = 0;
        for (Node c : getManagedChildren()) {
            if (isSpacer(c)) continue;
            need += c.prefWidth(-1);
            n++;
        }
        need += Math.max(0, n - 1) * getSpacing();
        return width <= 0 || width + 0.5 >= need;
    }

    private static boolean isSpacer(Node c) {
        return !(c instanceof ButtonBase) && c.getStyleClass().contains("bd-spacer");
    }

    /** The height depends on the width (the buttons stack when narrow). */
    @Override
    public javafx.geometry.Orientation getContentBias() {
        return javafx.geometry.Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinWidth(double height) {
        double min = 0;
        for (Node c : getManagedChildren()) if (!isSpacer(c)) min = Math.max(min, c.minWidth(-1));
        return snappedLeftInset() + snappedRightInset() + min;
    }

    @Override
    protected double computePrefHeight(double width) {
        if (width < 0 || fits(width)) return super.computePrefHeight(width);
        double h = snappedTopInset() + snappedBottomInset();
        int n = 0;
        for (Node c : getManagedChildren()) {
            if (isSpacer(c)) continue;
            h += c.prefHeight(-1);
            n++;
        }
        return h + Math.max(0, n - 1) * getSpacing();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        boolean s = !fits(getWidth());
        if (s != stacked) {
            stacked = s;
            pseudoClassStateChanged(NARROW, s);
        }
        if (!s) {
            super.layoutChildren();
            return;
        }
        Insets in = getInsets();
        double x = in.getLeft(), y = in.getTop(), w = getWidth() - in.getLeft() - in.getRight();
        for (Node c : getManagedChildren()) {
            if (isSpacer(c)) {
                c.resizeRelocate(x, y, 0, 0);
                continue;
            }
            double h = c.prefHeight(w);
            c.resizeRelocate(x, y, w, h);
            y += h + getSpacing();
        }
    }
}
