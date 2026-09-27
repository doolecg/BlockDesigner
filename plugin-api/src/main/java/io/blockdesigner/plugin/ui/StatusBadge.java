package io.blockdesigner.plugin.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * A small pill with a dot and a word or two, coloured by its {@link Tone}: "Connected", "Model running", "No key".
 * The text always says it, so the colour is never the only sign. Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class StatusBadge extends Label {
    private Tone tone = Tone.NEUTRAL;

    public StatusBadge(Tone tone, String text) {
        Theme.attach(this);
        getStyleClass().add("bd-badge");
        Region dot = new Region();
        dot.getStyleClass().add("bd-badge-dot");
        setGraphic(dot);
        setMinWidth(Region.USE_PREF_SIZE);
        set(tone, text);
    }

    /** Changes the tone and text. */
    public void set(Tone tone, String text) {
        this.tone = tone == null ? Tone.NEUTRAL : tone;
        Tone.apply(this, this.tone);
        setText(text);
        setAccessibleText(text);
    }

    public Tone tone() {
        return tone;
    }
}
