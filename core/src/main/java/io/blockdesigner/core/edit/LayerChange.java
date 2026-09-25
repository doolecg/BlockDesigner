package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.transform.Transform;

/** Layer-level changes: property edits, adding, removing and reordering. */
public final class LayerChange {
    private LayerChange() {
    }

    /** Snapshot of the editable properties of a layer. */
    public record Props(String name, BlockPos offset, Transform transform, boolean visible, boolean locked, boolean ghost, int color) {
        public static Props of(Layer l) {
            return new Props(l.name(), l.offset(), l.transform(), l.visible(), l.locked(), l.ghost(), l.color());
        }

        void applyTo(Layer l) {
            l.setName(name);
            l.setOffset(offset);
            l.setTransform(transform);
            l.setVisible(visible);
            l.setLocked(locked);
            l.setGhost(ghost);
            l.setColor(color);
        }
    }

    public record Properties(String layerId, Props before, Props after) implements Change {
        @Override
        public void undo(Scene scene) {
            scene.find(layerId).ifPresent(l -> {
                before.applyTo(l);
                scene.firePropertiesChanged(l);
            });
        }

        @Override
        public void redo(Scene scene) {
            scene.find(layerId).ifPresent(l -> {
                after.applyTo(l);
                scene.firePropertiesChanged(l);
            });
        }

        /** Combined change from this change's {@code before} to {@code next}'s {@code after}, or null if unrelated. */
        Properties merge(Change next) {
            return next instanceof Properties o && o.layerId.equals(layerId) ? new Properties(layerId, before, o.after) : null;
        }
    }

    public record Add(Layer layer, int index) implements Change {
        @Override
        public void undo(Scene scene) {
            scene.remove(layer);
        }

        @Override
        public void redo(Scene scene) {
            scene.add(layer, Math.min(index, scene.layers().size()));
        }
    }

    public record Remove(Layer layer, int index) implements Change {
        @Override
        public void undo(Scene scene) {
            scene.add(layer, Math.min(index, scene.layers().size()));
        }

        @Override
        public void redo(Scene scene) {
            scene.remove(layer);
        }
    }

    public record Reorder(String layerId, int from, int to) implements Change {
        @Override
        public void undo(Scene scene) {
            scene.find(layerId).ifPresent(l -> scene.move(l, from));
        }

        @Override
        public void redo(Scene scene) {
            scene.find(layerId).ifPresent(l -> scene.move(l, to));
        }
    }
}
