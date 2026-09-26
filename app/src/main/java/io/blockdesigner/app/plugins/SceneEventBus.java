package io.blockdesigner.app.plugins;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.plugin.SceneEvent;
import io.blockdesigner.plugin.Subscription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Turns scene changes into {@link SceneEvent}s for plugins. Changes arrive in bursts (a brush stroke sets thousands of
 * blocks, a project load adds every layer), so they are gathered and delivered once, on the next pulse of
 * {@code later}: one event of each kind, with the blocks' dirty boxes merged. Use from the JavaFX thread.
 */
final class SceneEventBus implements Scene.Listener {
    private record Listener(Object owner, Class<? extends SceneEvent> type, Consumer<SceneEvent> action) {
    }

    private final Scene scene;
    private final Consumer<Runnable> later;
    /** Told when a listener throws: the owner and the error. */
    private final BiConsumer<Object, Throwable> onError;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    // What happened since the last delivery.
    private final Set<Layer> dirtyLayers = new LinkedHashSet<>();
    private Box dirty;
    private boolean layersChanged;
    private boolean activeChanged;
    private final Supplier<Optional<Box>> selection;
    private boolean selectionChanged;
    private Optional<Path> opened;
    private boolean queued;

    /**
     * @param selection measures the current selection (only when a selection event is delivered)
     * @param later     runs a task on a later pulse of the UI thread ({@code Platform::runLater}); events are merged until then
     * @param onError   told when a listener throws, with the owner it was registered for
     */
    SceneEventBus(Scene scene, Supplier<Optional<Box>> selection, Consumer<Runnable> later, BiConsumer<Object, Throwable> onError) {
        this.selection = selection;
        this.scene = scene;
        this.later = later;
        this.onError = onError;
        scene.addListener(this);
    }

    @SuppressWarnings("unchecked")
    <E extends SceneEvent> Subscription subscribe(Object owner, Class<E> type, Consumer<? super E> action) {
        Listener l = new Listener(owner, type, e -> ((Consumer<SceneEvent>) action).accept(e));
        listeners.add(l);
        return () -> listeners.remove(l);
    }

    /** Drops every listener {@code owner} added (a plugin being disabled). */
    void removeAll(Object owner) {
        listeners.removeIf(l -> l.owner() == owner);
    }

    int listenerCount() {
        return listeners.size();
    }

    // ---- sources other than the scene ---------------------------------------------------------------------------

    void selectionChanged() {
        selectionChanged = true;
        queue();
    }

    void projectOpened(Optional<Path> file) {
        opened = file;
        queue();
    }

    // ---- scene listener -----------------------------------------------------------------------------------------

    @Override
    public void blocksChanged(Layer layer, Box localBox) {
        dirtyLayers.add(layer);
        // A layer's transform is a quarter-turn lattice map, so the local box's corners land on the world box's corners.
        Box world = Box.of(layer.toWorld(new BlockPos(localBox.minX(), localBox.minY(), localBox.minZ())),
                layer.toWorld(new BlockPos(localBox.maxX(), localBox.maxY(), localBox.maxZ())));
        dirty = dirty == null ? world : dirty.union(world);
        queue();
    }

    @Override
    public void layerAdded(Layer layer, int index) {
        layersChanged = true;
        queue();
    }

    @Override
    public void layerRemoved(Layer layer) {
        layersChanged = true;
        queue();
    }

    @Override
    public void layersReordered() {
        layersChanged = true;
        queue();
    }

    @Override
    public void layerPropertiesChanged(Layer layer) {
        layersChanged = true;
        queue();
    }

    @Override
    public void activeLayerChanged(Layer layer) {
        activeChanged = true;
        queue();
    }

    private void queue() {
        // Nobody listening: forget it, so nothing piles up.
        if (listeners.isEmpty()) {
            reset();
            return;
        }
        if (queued) return;
        queued = true;
        later.accept(this::flush);
    }

    private void reset() {
        dirtyLayers.clear();
        dirty = null;
        layersChanged = activeChanged = false;
        selectionChanged = false;
        opened = null;
    }

    /** Delivers what gathered since the last call, in a fixed order: project, layers, active layer, blocks, selection. */
    void flush() {
        queued = false;
        List<SceneEvent> events = new ArrayList<>();
        if (opened != null) events.add(new SceneEvent.ProjectOpened(opened));
        if (layersChanged) events.add(new SceneEvent.LayersChanged());
        if (activeChanged) events.add(new SceneEvent.ActiveLayerChanged(scene.active()));
        if (dirty != null) {
            // Bottom first, and only layers still in the scene (an undo may have removed one since).
            List<Layer> ls = dirtyLayers.stream().filter(l -> scene.indexOf(l) >= 0).sorted(Comparator.comparingInt(scene::indexOf)).toList();
            if (!ls.isEmpty()) events.add(new SceneEvent.BlocksChanged(ls, dirty));
        }
        // Measured now rather than at each change: the selection can be large and change many times a frame.
        if (selectionChanged) events.add(new SceneEvent.SelectionChanged(selection.get()));
        reset();
        for (SceneEvent e : events) {
            for (Listener l : listeners) {
                if (!l.type().isInstance(e)) continue;
                try {
                    l.action().accept(e);
                } catch (Throwable t) {
                    onError.accept(l.owner(), t);
                }
            }
        }
    }

    void close() {
        scene.removeListener(this);
        listeners.clear();
    }
}
