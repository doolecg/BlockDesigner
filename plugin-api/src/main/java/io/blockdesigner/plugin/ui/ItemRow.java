package io.blockdesigner.plugin.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One row of an {@link ItemList}: a leading icon or swatch, a title (13 px, cut with an ellipsis), an optional meta
 * line under it (11 px, muted or in a {@link Tone}), anything else below, and up to three trailing nodes (a count, an
 * icon button). 32 px tall, or 48 with a meta line. Since API 6.
 *
 * <pre>{@code
 * new ItemList<Entry>(e -> ItemRow.of(e.name()).image(icon(e)).trailing(Controls.caption(e.count() + "×")));
 * }</pre>
 */
public final class ItemRow {
    private final String title;
    private String meta;
    private Tone metaTone = Tone.NEUTRAL;
    private Node leading;
    private final List<Node> below = new ArrayList<>();
    private final List<Node> trailing = new ArrayList<>();
    private String tooltip;

    private ItemRow(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public static ItemRow of(String title) {
        return new ItemRow(title);
    }

    /** A muted second line. */
    public ItemRow meta(String text) {
        return meta(text, Tone.NEUTRAL);
    }

    /** A second line in a tone ("Done" in green). */
    public ItemRow meta(String text, Tone tone) {
        this.meta = text;
        this.metaTone = tone == null ? Tone.NEUTRAL : tone;
        return this;
    }

    /** A block icon (or any 24 px image) at the start. */
    public ItemRow image(Image icon) {
        if (icon == null) return this;
        ImageView iv = new ImageView(icon);
        iv.setFitWidth(24);
        iv.setFitHeight(24);
        iv.setPreserveRatio(true);
        iv.setSmooth(false);
        return leading(iv);
    }

    /** A colour square at the start (0xAARRGGBB), for a block with no icon. */
    public ItemRow swatch(int argb) {
        return leading(Controls.swatch(argb, 20));
    }

    /** Any node at the start (a tick box, a status dot). */
    public ItemRow leading(Node n) {
        this.leading = n;
        return this;
    }

    /** A node under the title and meta, full width (a progress bar). */
    public ItemRow below(Node n) {
        below.add(n);
        return this;
    }

    /** Nodes at the end, right-aligned: a count, an icon button. At most three. */
    public ItemRow trailing(Node... n) {
        if (trailing.size() + n.length > 3) throw new IllegalArgumentException("A row has at most three trailing nodes");
        trailing.addAll(List.of(n));
        return this;
    }

    /** A tooltip over the whole row. */
    public ItemRow tooltip(String text) {
        this.tooltip = text;
        return this;
    }

    /** Builds the row's node ({@code bd-row}). */
    public Node node() {
        Label t = new Label(title);
        t.getStyleClass().add("bd-row-title");
        t.setMinWidth(0);
        t.setMaxWidth(Double.MAX_VALUE);
        t.setTextOverrun(OverrunStyle.ELLIPSIS);
        VBox text = new VBox(t);
        text.getStyleClass().add("bd-row-text");
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMinWidth(0);
        if (meta != null && !meta.isBlank()) {
            Label m = new Label(meta);
            m.getStyleClass().add("bd-row-meta");
            m.setMinWidth(0);
            m.setMaxWidth(Double.MAX_VALUE);
            m.setTextOverrun(OverrunStyle.ELLIPSIS);
            Tone.apply(m, metaTone);
            text.getChildren().add(m);
        }
        text.getChildren().addAll(below);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox();
        row.getStyleClass().add("bd-row");
        if (meta != null && !meta.isBlank() || !below.isEmpty()) row.getStyleClass().add("bd-row-tall");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setPrefWidth(0);
        if (leading != null) row.getChildren().add(leading);
        row.getChildren().add(text);
        for (Node n : trailing) {
            if (n instanceof Region r) r.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(n);
        }
        if (tooltip != null && !tooltip.isBlank()) Tooltip.install(row, new Tooltip(tooltip));
        return row;
    }
}
