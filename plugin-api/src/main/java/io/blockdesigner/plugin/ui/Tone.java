package io.blockdesigner.plugin.ui;

import javafx.css.PseudoClass;
import javafx.scene.Node;

import java.util.Locale;

/**
 * What a status means, drawn in the theme's colours: a {@link StatusBadge}, a {@link Banner}, a page's status dot, a
 * list row's meta line. Never shown by colour alone: there is always text with it. Since API 6.
 */
public enum Tone {
    /** Nothing to act on: waiting, off, not set. Muted text. */
    NEUTRAL,
    /** Busy or informative: answering, baking, downloading. The theme's accent. */
    ACCENT,
    /** Done, connected, running. Green. */
    SUCCESS,
    /** Needs a look: connecting, almost out, will be replaced. Yellow. */
    WARNING,
    /** Failed, missing, will be deleted. Red. */
    DANGER;

    /** The CSS pseudo-class for this tone ({@code :accent}, {@code :success}…); null for {@link #NEUTRAL}. */
    public PseudoClass pseudoClass() {
        return this == NEUTRAL ? null : PseudoClass.getPseudoClass(name().toLowerCase(Locale.ROOT));
    }

    /** Sets this tone's pseudo-class on the node and clears the others ({@code null} clears them all). */
    public static void apply(Node node, Tone tone) {
        for (Tone t : values()) {
            PseudoClass pc = t.pseudoClass();
            if (pc != null) node.pseudoClassStateChanged(pc, t == tone);
        }
    }
}
