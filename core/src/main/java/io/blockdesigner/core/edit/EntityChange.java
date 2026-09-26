package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.StructureEntity;

import java.util.List;

/** A layer's entity list before and after an edit. Entity lists are short, so whole lists are kept. */
public final class EntityChange implements Change {
    private final String layerId;
    private final List<StructureEntity> before;
    private List<StructureEntity> after;

    EntityChange(String layerId, List<StructureEntity> before, List<StructureEntity> after) {
        this.layerId = layerId;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    public String layerId() {
        return layerId;
    }

    @Override
    public void undo(Scene scene) {
        apply(scene, before);
    }

    @Override
    public void redo(Scene scene) {
        apply(scene, after);
    }

    private void apply(Scene scene, List<StructureEntity> list) {
        scene.find(layerId).ifPresent(l -> {
            l.structure().setEntities(list.stream().map(StructureEntity::copy).toList());
            scene.fireEntitiesChanged(l);
        });
    }

    @Override
    public boolean absorb(Change next) {
        if (!(next instanceof EntityChange o) || !o.layerId.equals(layerId)) return false;
        after = o.after;
        return true;
    }
}
