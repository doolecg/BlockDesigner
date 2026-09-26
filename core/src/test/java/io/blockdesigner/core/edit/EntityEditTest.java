package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.EntityTypes;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.model.StructureEntity;
import io.blockdesigner.core.transform.Transform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityEditTest {
    final Scene scene = new Scene();
    final SceneEditor editor = new SceneEditor(scene);

    @Test
    void placingAndRemovingEntitiesUndoes() {
        Layer l = new Layer("a", new Structure());
        editor.addLayer(l);
        List<Layer> changed = new ArrayList<>();
        scene.addListener(new Scene.Listener() {
            @Override
            public void entitiesChanged(Layer layer) {
                changed.add(layer);
            }
        });
        StructureEntity pig = EntityTypes.create("pig", 1.5, 0, 1.5, 0);
        editor.editEntities(l, "Place pig", null, list -> list.add(pig));
        assertThat(l.structure().entities()).containsExactly(pig);
        editor.editEntities(l, "Remove pig", null, list -> list.remove(pig));
        assertThat(l.structure().entities()).isEmpty();
        editor.undoStack().undo();
        assertThat(l.structure().entities()).containsExactly(pig);
        editor.undoStack().undo();
        assertThat(l.structure().entities()).isEmpty();
        editor.undoStack().redo();
        assertThat(l.structure().entities()).containsExactly(pig);
        assertThat(changed).hasSize(5).containsOnly(l);
    }

    @Test
    void turnsWithTheSameKeyUndoTogether() {
        Layer l = new Layer("a", new Structure());
        editor.addLayer(l);
        editor.editEntities(l, "Place", null, list -> list.add(EntityTypes.create("cow", 0.5, 0, 0.5, 0)));
        for (int i = 1; i <= 4; i++) {
            float yaw = 22.5f * i;
            editor.editEntities(l, "Turn", "turn", list -> list.set(0, list.getFirst().withYaw(yaw)));
        }
        assertThat(l.structure().entities().getFirst().yaw()).isEqualTo(90);
        editor.undoStack().undo();
        assertThat(l.structure().entities().getFirst().yaw()).isZero();
    }

    @Test
    void editingACopysEntitiesSplitsItsContent() {
        Structure a = new Structure();
        a.set(0, 0, 0, BlockState.of("stone"));
        Structure b = a.copy();
        assertThat(b.contentId()).isEqualTo(a.contentId());
        b.addEntity(EntityTypes.create("pig", 0.5, 1, 0.5, 0));
        assertThat(b.contentId()).isNotEqualTo(a.contentId());
        assertThat(a.entities()).isEmpty();
    }

    @Test
    void mergeDownBringsEntitiesAlong() {
        Layer lower = new Layer("lower", new Structure());
        Layer upper = new Layer("upper", new Structure());
        upper.setOffset(new BlockPos(10, 0, 0));
        upper.structure().addEntity(EntityTypes.create("sheep", 0.5, 0, 0.5, 0));
        editor.addLayer(lower);
        editor.addLayer(upper);
        editor.mergeDown(upper, lower);
        assertThat(lower.structure().entities()).singleElement().satisfies(e -> assertThat(e.x()).isEqualTo(10.5));
        editor.undoStack().undo();
        assertThat(lower.structure().entities()).isEmpty();
        assertThat(scene.layers()).contains(upper);
    }

    @Test
    void groupsWithAMergeKeyJoinOneStep() {
        Layer l = new Layer("a", new Structure());
        editor.addLayer(l);
        UndoStack undo = editor.undoStack();
        int before = undo.size();
        for (int i = 0; i < 3; i++) {
            undo.beginGroup("Place block", "hold-1");
            try (var s = editor.edit(l, "Place block")) {
                s.set(i, 0, 0, BlockState.of("stone"));
            }
            undo.endGroup();
        }
        assertThat(undo.size()).isEqualTo(before + 1);
        undo.sealTop();
        undo.beginGroup("Place block", "hold-1");
        try (var s = editor.edit(l, "Place block")) {
            s.set(5, 0, 0, BlockState.of("stone"));
        }
        undo.endGroup();
        assertThat(undo.size()).isEqualTo(before + 2);
        undo.undo();
        undo.undo();
        assertThat(l.structure().blockCount()).isZero();
    }

    @Test
    void toLocalIsTheInverseOfTheLayersTransform() {
        Layer l = new Layer("a", new Structure());
        l.setOffset(new BlockPos(5, 2, -3));
        l.setTransform(new Transform(1, Transform.Mirror.X));
        StructureEntity local = EntityTypes.create("villager", 3.25, 1, 0.75, 45);
        StructureEntity world = EntityTypes.transform(local, l.transform(), 5, 2, -3);
        StructureEntity back = SceneEditor.toLocal(l, l.transform().inverse(), world);
        assertThat(back.x()).isCloseTo(3.25, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(back.y()).isCloseTo(1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(back.z()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(back.yaw()).isCloseTo(45f, org.assertj.core.data.Offset.offset(1e-3f));
    }
}
