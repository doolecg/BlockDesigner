package io.blockdesigner.plugin.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Small building blocks in the app's look: buttons (one {@link #primary} per section at most), icon buttons that
 * always have a tooltip, hints and captions, a search field, block icons. Everything else is a component
 * ({@link Section}, {@link Form}, {@link ActionBar}…). Since API 6.
 */
public final class Controls {
    private static final PseudoClass BUSY = PseudoClass.getPseudoClass("busy");
    private static final String BUSY_GRAPHIC = "bd.busy.graphic";

    private Controls() {
    }

    /** The main action of a section or dialog, in the accent colour; label it with a verb ("Build", "Send"). */
    public static Button primary(String text, Runnable action) {
        Button b = button(text, null, action);
        b.getStyleClass().add("accent");
        return b;
    }

    /** A plain button; the tooltip may be null. */
    public static Button button(String text, String tooltip, Runnable action) {
        Button b = new Button(text);
        tooltip(b, tooltip);
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    /** A button that deletes or resets something, in red. Ask first ({@code PluginUi.confirm}). */
    public static Button danger(String text, String tooltip, Runnable action) {
        Button b = button(text, tooltip, action);
        b.getStyleClass().add("danger");
        return b;
    }

    /** A flat button showing only an icon; the tooltip is required and is also what screen readers say. */
    public static Button iconButton(Icon icon, String tooltip, Runnable action) {
        if (tooltip == null || tooltip.isBlank()) throw new IllegalArgumentException("An icon button needs a tooltip");
        Button b = new Button(null, icon.node(16));
        b.getStyleClass().addAll("flat", "bd-icon-button");
        tooltip(b, tooltip);
        b.setAccessibleText(tooltip);
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    /** A button that stays down while on, such as "Live" or "Hide done". */
    public static ToggleButton toggle(String text, String tooltip) {
        ToggleButton b = new ToggleButton(text);
        tooltip(b, tooltip);
        return b;
    }

    /**
     * Shows that the button's action is running (a spinner, and the button disabled) for work of 1–4 seconds; longer
     * work gets a progress bar and Cancel. Call again with false when it is done.
     */
    public static void busy(ButtonBase b, boolean busy) {
        boolean now = b.getProperties().containsKey(BUSY_GRAPHIC);
        if (busy == now) return;
        if (busy) {
            b.getProperties().put(BUSY_GRAPHIC, b.getGraphic() == null ? "" : b.getGraphic());
            ProgressIndicator p = new ProgressIndicator();
            p.setPrefSize(14, 14);
            p.setMaxSize(14, 14);
            p.getStyleClass().add("bd-busy");
            b.setGraphic(p);
        } else {
            Object g = b.getProperties().remove(BUSY_GRAPHIC);
            b.setGraphic(g instanceof Node n ? n : null);
        }
        b.setDisable(busy);
        b.pseudoClassStateChanged(BUSY, busy);
    }

    /** A muted line of explanation (12 px) that wraps; for under a control or a section's first line. */
    public static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("bd-hint");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /**
     * Small muted text (11 px) on one line, cut with an ellipsis when too long; the tooltip shows it whole. Paths and
     * ids read best with {@code setTextOverrun(OverrunStyle.CENTER_ELLIPSIS)}.
     */
    public static Label caption(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("bd-caption");
        l.setMinWidth(0);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setTextOverrun(OverrunStyle.ELLIPSIS);
        Tooltip t = new Tooltip();
        t.textProperty().bind(l.textProperty());
        l.setTooltip(t);
        return l;
    }

    /** A caption for a path or id: the middle is cut rather than the end. */
    public static Label pathCaption(String text) {
        Label l = caption(text);
        l.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        return l;
    }

    /** A link-styled button, for "Open the BlockCompanion page" and the like. */
    public static Hyperlink link(String text, Runnable action) {
        Hyperlink h = new Hyperlink(text);
        h.getStyleClass().add("bd-link");
        h.setMinWidth(Region.USE_PREF_SIZE);
        if (action != null) h.setOnAction(e -> {
            h.setVisited(false);
            action.run();
        });
        return h;
    }

    /** A search field with a magnifier inside it; Esc clears it. */
    public static TextField search(String prompt) {
        TextField f = new SearchField();
        f.setPromptText(prompt);
        f.getStyleClass().add("bd-search");
        f.setMinWidth(0);
        f.setMaxWidth(Double.MAX_VALUE);
        f.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && !f.getText().isEmpty()) {
                f.clear();
                e.consume();
            }
        });
        return f;
    }

    /**
     * A block's icon in a small slot ({@code bd-block-slot}), or a square of the block's colour while no icon exists
     * (no Minecraft assets loaded). {@code fallbackArgb} is 0xAARRGGBB.
     */
    public static Node blockIcon(Image icon, int fallbackArgb, double size) {
        StackPane slot = new StackPane();
        slot.getStyleClass().add("bd-block-slot");
        slot.setMinSize(size, size);
        slot.setPrefSize(size, size);
        slot.setMaxSize(size, size);
        double inner = Math.max(4, size - 6);
        if (icon != null) {
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(inner);
            iv.setFitHeight(inner);
            iv.setPreserveRatio(true);
            iv.setSmooth(false);
            slot.getChildren().add(iv);
        } else {
            Region swatch = new Region();
            swatch.setMinSize(inner, inner);
            swatch.setMaxSize(inner, inner);
            swatch.setBackground(new Background(new BackgroundFill(argb(fallbackArgb), new CornerRadii(3), null)));
            slot.getChildren().add(swatch);
        }
        return slot;
    }

    /** A colour square in the size of a block icon, for rows with no icon at all. */
    static Node swatch(int argb, double size) {
        Region swatch = new Region();
        swatch.getStyleClass().add("bd-swatch");
        swatch.setMinSize(size, size);
        swatch.setMaxSize(size, size);
        swatch.setBackground(new Background(new BackgroundFill(argb(argb), new CornerRadii(4), null)));
        return swatch;
    }

    private static Color argb(int argb) {
        int a = argb >>> 24;
        return Color.rgb((argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, a == 0 ? 1 : a / 255.0);
    }

    /** Empty space that takes the free width of a row (between left and right-aligned buttons). */
    public static Region spacer() {
        Region r = new Region();
        r.getStyleClass().add("bd-spacer");
        HBox.setHgrow(r, Priority.ALWAYS);
        r.setMinWidth(0);
        return r;
    }

    /** Shows or hides a node, and takes it out of the layout while hidden (no gap). Returns it. */
    public static <N extends Node> N show(N node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
        return node;
    }

    static void tooltip(javafx.scene.control.Control c, String text) {
        if (text != null && !text.isBlank()) c.setTooltip(new Tooltip(text));
    }

    /** A text field with the search icon drawn inside, left of the text. */
    private static final class SearchField extends TextField {
        private final Node icon = Icon.SEARCH.node(14);

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (!getChildren().contains(icon)) getChildren().add(icon);
            icon.resizeRelocate(9, Math.round((getHeight() - 14) / 2), 14, 14);
            icon.setViewOrder(-1);
        }

        {
            setAlignment(Pos.CENTER_LEFT);
        }
    }
}
