package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.Scene;

/** One reversible modification of a {@link Scene}. */
public interface Change {
    void undo(Scene scene);

    void redo(Scene scene);

    /** Attempts to absorb {@code next} (same kind, same target) into this change; returns false if not possible. */
    default boolean absorb(Change next) {
        return false;
    }
}
