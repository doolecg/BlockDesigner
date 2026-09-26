package io.blockdesigner.plugin;

/** A listener added with {@link PluginContext#on}. Listeners are removed automatically when the plugin is disabled. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    /** Stops the listener; safe to call more than once. */
    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
