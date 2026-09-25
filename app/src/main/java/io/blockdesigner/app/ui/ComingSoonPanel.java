package io.blockdesigner.app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Placeholder tab for a planned feature: icon, title, a "Coming soon" badge and one line on what it will do. */
public final class ComingSoonPanel extends VBox {
    public ComingSoonPanel(Feather icon, String title, String description) {
        getStyleClass().add("coming-soon");
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setPadding(new Insets(24));

        FontIcon i = new FontIcon(icon);
        i.getStyleClass().add("coming-soon-icon");
        Label t = new Label(title);
        t.getStyleClass().add("coming-soon-title");
        Label soon = new Label("Coming soon");
        soon.getStyleClass().add("badge");
        Label body = new Label(description);
        body.setWrapText(true);
        body.getStyleClass().add("coming-soon-body");
        getChildren().addAll(i, t, soon, body);
    }

    /** The Resource Tracker tab: materials a build needs, what has been gathered and what is left. */
    static ComingSoonPanel resourceTracker() {
        return new ComingSoonPanel(Feather.PACKAGE, "Resource Tracker",
                "Track the materials your build needs: a block count per layer, what you have already gathered and what is left.");
    }

    /** The AI Assistant tab while the assistant is switched off. */
    public static ComingSoonPanel assistant() {
        return new ComingSoonPanel(Feather.MESSAGE_SQUARE, "AI Assistant",
                "Describe a build or show a reference image, and an AI assistant builds it block by block. Refine it by chatting.");
    }
}
