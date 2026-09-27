package io.blockdesigner.plugin.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * A message box next to the action it is about: an error with one next step ("Couldn't reach the game. Retry"), a
 * finished install, a warning. Hidden until {@link #show}n; its × hides it. Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class Banner extends HBox {
    private final StackPane icon = new StackPane();
    private final Label message = new Label();
    private final Button action = new Button();
    private final Button close;

    public Banner() {
        Theme.attach(this);
        getStyleClass().add("bd-banner");
        setAlignment(Pos.TOP_LEFT);
        icon.getStyleClass().add("bd-banner-icon");
        message.getStyleClass().add("bd-banner-message");
        message.setWrapText(true);
        message.setMinWidth(0);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(message, Priority.ALWAYS);
        action.getStyleClass().addAll("flat", "small", "bd-banner-action");
        action.setMinWidth(Region.USE_PREF_SIZE);
        close = Controls.iconButton(Icon.CLOSE, "Dismiss", this::hide);
        close.getStyleClass().add("small");
        getChildren().addAll(icon, message, action, close);
        setMinWidth(0);
        hide();
    }

    /** Shows a message. */
    public void show(Tone tone, String message) {
        show(tone, message, null, null);
    }

    /** Shows a message with one button for the next step (such as "Retry" or "Open settings…"). */
    public void show(Tone tone, String message, String actionText, Runnable action) {
        Tone t = tone == null ? Tone.NEUTRAL : tone;
        Tone.apply(this, t);
        icon.getChildren().setAll((switch (t) {
            case SUCCESS -> Icon.CHECK;
            case WARNING -> Icon.WARNING;
            case DANGER -> Icon.ERROR;
            default -> Icon.INFO;
        }).node(16));
        this.message.setText(message);
        boolean hasAction = actionText != null && action != null;
        this.action.setText(hasAction ? actionText : "");
        this.action.setOnAction(hasAction ? e -> action.run() : null);
        Controls.show(this.action, hasAction);
        Controls.show(this, true);
    }

    /** Hides it (the × does the same). */
    public void hide() {
        Controls.show(this, false);
    }

    /** The message showing, or "" while hidden. */
    public String message() {
        return isVisible() ? message.getText() : "";
    }
}
