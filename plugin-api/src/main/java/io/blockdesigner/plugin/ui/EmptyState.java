package io.blockdesigner.plugin.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * What a page or list shows when there is nothing yet: a muted icon, one sentence, an optional hint and at most two
 * buttons for getting started ("No picture open." · Open picture…). Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class EmptyState extends VBox {
    private final Label hint = Controls.hint("");
    private final HBox actions = new HBox();

    public EmptyState(Icon icon, String message) {
        Theme.attach(this);
        getStyleClass().add("bd-empty");
        setAlignment(Pos.CENTER);
        setFillWidth(true);
        Label text = new Label(message);
        text.getStyleClass().add("bd-empty-message");
        text.setWrapText(true);
        text.setTextAlignment(TextAlignment.CENTER);
        text.setMinHeight(Region.USE_PREF_SIZE);
        hint.setTextAlignment(TextAlignment.CENTER);
        hint.setAlignment(Pos.CENTER);
        actions.getStyleClass().add("bd-empty-actions");
        actions.setAlignment(Pos.CENTER);
        Controls.show(hint, false);
        Controls.show(actions, false);
        if (icon != null) getChildren().add(icon.node(32));
        getChildren().addAll(text, hint, actions);
        setMinWidth(0);
    }

    /** A muted line under the sentence: how to fill it. */
    public EmptyState hint(String text) {
        hint.setText(text);
        Controls.show(hint, text != null && !text.isBlank());
        return this;
    }

    /** A button under it; at most two (the first is usually {@link Controls#primary}). */
    public EmptyState action(Button b) {
        if (actions.getChildren().size() >= 2) throw new IllegalStateException("An empty state has at most two actions");
        actions.getChildren().add(b);
        Controls.show(actions, true);
        return this;
    }
}
