package io.blockdesigner.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A kind of {@link SceneObject}, registered with {@link PluginContext#registerObjectType}. BlockDesigner uses it to make
 * objects when a project is opened (then {@link SceneObject#load}), and, when the type lists {@link #extensions()},
 * when such a file is imported or dropped on the window. Objects of a type whose plugin is off are kept in the project
 * untouched and come back when it is on again. Since API 3.
 */
public interface SceneObjectType {

    /** Stable id, unique within the plugin; saved in projects, so never change it. Uses a-z, 0-9, _ . - */
    String id();

    /** What one is called, e.g. "Reference image". */
    String name();

    /** The tag on its rows in the Layers panel, e.g. {@code "REFERENCE"}. */
    String badge();

    /** A new, empty object ({@link SceneObject#load} follows when it comes from a project). */
    SceneObject create();

    /** File extensions (without the dot) that {@link #open} turns into objects, e.g. png and jpg; empty for none. */
    default List<String> extensions() {
        return List.of();
    }

    /**
     * A file with one of {@link #extensions()} was imported or dropped on the window: add an object for it, usually
     * with {@code context.objects().add(...)} placed around {@link ViewInfo#target()}. Runs on the JavaFX thread; an
     * {@code IOException}'s message is shown to the user.
     */
    default void open(Path file, ViewInfo view) throws IOException {
    }
}
