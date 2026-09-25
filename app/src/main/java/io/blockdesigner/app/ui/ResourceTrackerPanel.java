package io.blockdesigner.app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Placeholder for the Resource Tracker tab (future feature): the materials a build needs, what the player already has
 * and what is left to gather.
 */
final class ResourceTrackerPanel extends VBox {
    ResourceTrackerPanel() {
        getStyleClass().add("resource-tracker");
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setPadding(new Insets(24));

        FontIcon icon = new FontIcon(Feather.PACKAGE);
        icon.getStyleClass().add("resource-tracker-icon");
        Label title = new Label("Resource Tracker");
        title.getStyleClass().add("resource-tracker-title");
        Label soon = new Label("Coming soon");
        soon.getStyleClass().add("badge");
        Label body = new Label("Track the materials your build needs: a block count per layer, "
                + "what you have already gathered and what is left.");
        body.setWrapText(true);
        body.getStyleClass().add("resource-tracker-body");
        getChildren().addAll(icon, title, soon, body);
    }
}
