package io.blockdesigner.plugin.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A titled group on a page: a heading (12 px, sentence case), optional actions on the right of it (icon buttons, a
 * badge), and the content 8 px below. Pages are sections 16 px apart, with no boxes around them. A section can be
 * {@link #collapsible} for settings most people skip; only one level (a collapsible section inside another throws).
 * Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class Section extends VBox {
    private final HBox header = new HBox();
    private final Label title = new Label();
    private final HBox actions = new HBox();
    private final VBox body = new VBox();
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", true);
    private StackPane chevron;
    private boolean collapsible;

    /** @param title the heading, or null for a section without one */
    public Section(String title, Node... content) {
        Theme.attach(this);
        getStyleClass().add("bd-section");
        header.getStyleClass().add("bd-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        this.title.getStyleClass().add("bd-section-title");
        this.title.setMinWidth(0);
        this.title.setAccessibleRole(AccessibleRole.TEXT);
        actions.getStyleClass().add("bd-section-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        body.getStyleClass().add("bd-section-body");
        body.setFillWidth(true);
        HBox.setHgrow(this.title, Priority.SOMETIMES);
        header.getChildren().addAll(this.title, Controls.spacer(), actions);
        if (title != null && !title.isBlank()) this.title.setText(title);
        else Controls.show(header, false);
        getChildren().addAll(header, body);
        setMinWidth(0);
        body.setMinWidth(0);
        expanded.addListener((o, a, open) -> {
            Controls.show(body, open);
            if (chevron != null) chevron.getChildren().setAll((open ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT).node(14));
        });
        add(content);
    }

    /** Adds content below what is there, 8 px apart. */
    public Section add(Node... content) {
        for (Node n : content) {
            if (collapsible && n instanceof Section s && s.collapsible)
                throw new IllegalArgumentException("A collapsible section can't hold another collapsible section");
            body.getChildren().add(n);
        }
        return this;
    }

    /** Adds a node that takes the section's free height (a list), when the section is a page's {@code grow} node. */
    public Section grow(Node content) {
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        return add(content);
    }

    /** Puts buttons (icon buttons, a link) on the right of the heading. */
    public Section actions(Node... headerActions) {
        actions.getChildren().addAll(headerActions);
        Controls.show(header, true);
        return this;
    }

    /** Puts a status badge right after the title. */
    public Section badge(StatusBadge badge) {
        header.getChildren().add(header.getChildren().indexOf(this.title) + 1, badge);
        Controls.show(header, true);
        return this;
    }

    /** Lets the user fold the content away with a chevron by the title; {@code expanded} is how it starts. */
    public Section collapsible(boolean expanded) {
        if (collapsible) return this;
        for (Node n : body.getChildren())
            if (n instanceof Section s && s.collapsible) throw new IllegalArgumentException("A collapsible section can't hold another collapsible section");
        for (javafx.scene.Parent p = getParent(); p != null; p = p.getParent())
            if (p instanceof Section s && s.collapsible) throw new IllegalArgumentException("Collapsible sections can't be nested");
        collapsible = true;
        chevron = new StackPane((expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT).node(14));
        chevron.getStyleClass().add("bd-chevron");
        header.getChildren().addFirst(chevron);
        header.getStyleClass().add("bd-collapsible");
        header.setFocusTraversable(true);
        header.setAccessibleRole(AccessibleRole.BUTTON);
        header.setAccessibleText(title.getText());
        header.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Node t && isInside(t, actions)) return;
            this.expanded.set(!this.expanded.get());
        });
        header.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                this.expanded.set(!this.expanded.get());
                e.consume();
            }
        });
        this.expanded.set(expanded);
        Controls.show(body, expanded);
        return this;
    }

    private static boolean isInside(Node n, Node ancestor) {
        for (Node p = n; p != null; p = p.getParent()) if (p == ancestor) return true;
        return false;
    }

    /** Whether the content shows (always true unless {@link #collapsible}). */
    public BooleanProperty expandedProperty() {
        return expanded;
    }

    /** The heading's label, for a tooltip or to change its text. */
    public Label titleLabel() {
        return title;
    }
}
