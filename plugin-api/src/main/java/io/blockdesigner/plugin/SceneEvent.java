package io.blockdesigner.plugin;

import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Something that changed in the open project, delivered to listeners added with {@link PluginContext#on}. Events
 * arrive on the JavaFX thread and are coalesced to at most one of each kind per frame: a brush stroke that touches
 * thousands of blocks becomes one {@link BlocksChanged} whose {@link BlocksChanged#dirty() dirty} box covers them all.
 */
public sealed interface SceneEvent {

    /**
     * Blocks changed (edits, undo, redo, imports).
     *
     * @param layers the layers whose blocks changed, bottom first
     * @param dirty  world-space box around every changed block
     */
    record BlocksChanged(List<Layer> layers, Box dirty) implements SceneEvent {
        public BlocksChanged {
            layers = List.copyOf(layers);
        }
    }

    /** Layers were added, removed, reordered, renamed, moved, shown or hidden. Re-read {@link PluginContext#scene()}. */
    record LayersChanged() implements SceneEvent {
    }

    /** Another layer became the active one (empty when there are no layers). */
    record ActiveLayerChanged(Optional<Layer> layer) implements SceneEvent {
    }

    /**
     * The block selection or the //pos1 //pos2 region changed.
     *
     * @param region world-space box around what is selected now, or empty when nothing is
     */
    record SelectionChanged(Optional<Box> region) implements SceneEvent {
    }

    /**
     * A project was opened, or a new one started.
     *
     * @param file the project file, or empty for a new, unsaved project
     */
    record ProjectOpened(Optional<Path> file) implements SceneEvent {
    }
}
