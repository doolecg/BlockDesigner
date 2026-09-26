package io.blockdesigner.plugin;

/** Reports how far a long job (an export) has got. Safe to call from any thread, as often as you like. */
@FunctionalInterface
public interface Progress {
    /** Ignores every update. */
    Progress NONE = (fraction, message) -> {
    };

    /**
     * @param fraction 0 to 1, or a negative number when it can't be told
     * @param message  what is happening, e.g. "Writing chunk 3 of 12"; null keeps the last one
     */
    void update(double fraction, String message);
}
