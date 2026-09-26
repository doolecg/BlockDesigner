package io.blockdesigner.core.model;

import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.transform.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ordered set of layers being edited. Index 0 is the bottom layer; when layers overlap, higher layers win.
 * Mutations fire {@link Listener} events so views (renderer, layer list) can update incrementally.
 */
public final class Scene {
    private final List<Layer> layers = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private String activeLayerId;

    public interface Listener {
        default void layerAdded(Layer layer, int index) {
        }

        default void layerRemoved(Layer layer) {
        }

        default void layersReordered() {
        }

        /** Offset, transform, visibility, lock, ghost, name or colour changed. */
        default void layerPropertiesChanged(Layer layer) {
        }

        /** Blocks changed inside {@code localBox} (layer-local coordinates). */
        default void blocksChanged(Layer layer, Box localBox) {
        }

        /** The layer's entities were added, removed, moved or turned. */
        default void entitiesChanged(Layer layer) {
        }

        default void activeLayerChanged(Layer layer) {
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<Layer> layers() {
        return Collections.unmodifiableList(layers);
    }

    public int indexOf(Layer layer) {
        return layers.indexOf(layer);
    }

    public Optional<Layer> find(String id) {
        for (Layer l : layers) if (l.id().equals(id)) return Optional.of(l);
        return Optional.empty();
    }

    public Layer require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("No layer with id " + id));
    }

    public void add(Layer layer) {
        add(layer, layers.size());
    }

    public void add(Layer layer, int index) {
        layers.add(index, layer);
        listeners.forEach(l -> l.layerAdded(layer, index));
        if (activeLayerId == null) setActive(layer);
    }

    public void remove(Layer layer) {
        int idx = layers.indexOf(layer);
        if (idx < 0) return;
        layers.remove(idx);
        listeners.forEach(l -> l.layerRemoved(layer));
        if (layer.id().equals(activeLayerId)) {
            activeLayerId = null;
            if (!layers.isEmpty()) setActive(layers.get(Math.min(idx, layers.size() - 1)));
            else listeners.forEach(l -> l.activeLayerChanged(null));
        }
    }

    public void move(Layer layer, int newIndex) {
        if (!layers.remove(layer)) return;
        layers.add(Math.clamp(newIndex, 0, layers.size()), layer);
        listeners.forEach(Listener::layersReordered);
    }

    public Optional<Layer> active() {
        return activeLayerId == null ? Optional.empty() : find(activeLayerId);
    }

    public void setActive(Layer layer) {
        String id = layer == null ? null : layer.id();
        if (java.util.Objects.equals(id, activeLayerId)) return;
        activeLayerId = id;
        listeners.forEach(l -> l.activeLayerChanged(layer));
    }

    /** Call after changing a layer's offset/transform/flags directly. */
    public void firePropertiesChanged(Layer layer) {
        listeners.forEach(l -> l.layerPropertiesChanged(layer));
    }

    public void fireBlocksChanged(Layer layer, Box localBox) {
        listeners.forEach(l -> l.blocksChanged(layer, localBox));
    }

    public void fireEntitiesChanged(Layer layer) {
        listeners.forEach(l -> l.entitiesChanged(layer));
    }

    /** Union of all visible layers' world bounds. */
    public Optional<Box> worldBounds() {
        Box b = null;
        for (Layer l : layers) {
            if (!l.visible()) continue;
            Optional<Box> lb = l.worldBounds();
            if (lb.isPresent()) b = b == null ? lb.get() : b.union(lb.get());
        }
        return Optional.ofNullable(b);
    }

    /**
     * Merges the given layers (in scene order, higher wins) into one world-space structure, applying each layer's
     * transform to positions, block states, block entities and entities.
     */
    public static Structure flatten(List<Layer> toMerge) {
        BlockTransformer bt = BlockTransformer.defaults();
        Structure out = new Structure();
        for (Layer layer : toMerge) {
            Structure s = layer.structure();
            Transform t = layer.transform();
            // The lattice transform is linear: world = M·local + offset. Inlined so no position objects are made per block,
            // and each distinct state is transformed once.
            BlockPos ex = t.apply(1, 0, 0), ez = t.apply(0, 0, 1), off = layer.offset();
            int m00 = ex.x(), m01 = ez.x(), m10 = ex.z(), m11 = ez.z();
            int ox = off.x(), oy = off.y(), oz = off.z();
            java.util.IdentityHashMap<BlockState, BlockState> turned = new java.util.IdentityHashMap<>();
            s.forEachBlock((x, y, z, state) -> {
                BlockState w = turned.get(state);
                if (w == null) {
                    w = bt.apply(state, t);
                    turned.put(state, w);
                }
                out.set(m00 * x + m01 * z + ox, y + oy, m10 * x + m11 * z + oz, w);
            });
            for (var e : s.blockEntities().entrySet()) {
                BlockPos wp = layer.toWorld(e.getKey());
                CompoundTag nbt = e.getValue().copy();
                if (out.get(wp).name().equals(s.get(e.getKey()).name())) out.setBlockEntity(wp, nbt);
            }
            for (StructureEntity e : s.entities()) {
                // Positions, yaw, and a painting's wall all turn with the layer.
                out.addEntity(EntityTypes.transform(e, t, layer.offset().x(), layer.offset().y(), layer.offset().z()));
            }
        }
        if (!toMerge.isEmpty()) out.metadata().dataVersion = toMerge.getFirst().structure().metadata().dataVersion;
        return out;
    }

    public Structure flattenVisible() {
        return flatten(layers.stream().filter(Layer::visible).toList());
    }
}
