package io.blockdesigner.core.edit;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.transform.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SceneEditorTest {
    static final BlockState STONE = BlockState.of("stone");
    static final BlockState DIRT = BlockState.of("dirt");

    static Layer layer(String name, BlockState fill, int size) {
        Structure s = new Structure();
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) s.set(x, 0, z, fill);
        return new Layer(name, s);
    }

    @Test
    void flattenHigherLayerWins() {
        Layer bottom = layer("bottom", STONE, 3);
        Layer top = layer("top", DIRT, 2);
        top.setOffset(new BlockPos(1, 0, 1));
        Structure flat = Scene.flatten(List.of(bottom, top));
        assertThat(flat.blockCount()).isEqualTo(9);
        assertThat(flat.get(0, 0, 0)).isSameAs(STONE);
        assertThat(flat.get(1, 0, 1)).isSameAs(DIRT);
        assertThat(flat.get(2, 0, 2)).isSameAs(DIRT);
    }

    @Test
    void flattenAppliesLayerTransformToStates() {
        Structure s = new Structure();
        s.set(1, 0, 0, BlockState.parse("oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"));
        Layer l = new Layer("stairs", s);
        l.setTransform(Transform.rotation(1));
        l.setOffset(new BlockPos(10, 0, 0));
        Structure flat = Scene.flatten(List.of(l));
        // (1,0,0) rotated clockwise -> (0,0,1), then offset.
        assertThat(flat.get(10, 0, 1).get("facing")).isEqualTo("east");
    }

    @Test
    void nudgeBurstUndoesInOneStep() {
        Scene scene = new Scene();
        SceneEditor ed = new SceneEditor(scene);
        Layer l = layer("a", STONE, 1);
        ed.addLayer(l);
        int before = ed.undoStack().size();
        for (int i = 0; i < 5; i++) ed.nudge(List.of(l), 0, 1, 0, "scroll");
        ed.nudge(List.of(l), 1, 0, 0, "scroll");
        assertThat(l.offset()).isEqualTo(new BlockPos(1, 5, 0));
        assertThat(ed.undoStack().size()).isEqualTo(before + 1);
        ed.undoStack().undo();
        assertThat(l.offset()).isEqualTo(BlockPos.ORIGIN);
        ed.undoStack().redo();
        assertThat(l.offset()).isEqualTo(new BlockPos(1, 5, 0));
    }

    @Test
    void sealedBurstStartsNewStep() {
        Scene scene = new Scene();
        SceneEditor ed = new SceneEditor(scene);
        Layer l = layer("a", STONE, 1);
        ed.addLayer(l);
        ed.nudge(List.of(l), 0, 1, 0, "scroll");
        ed.undoStack().sealTop();
        ed.nudge(List.of(l), 0, 1, 0, "scroll");
        ed.undoStack().undo();
        assertThat(l.offset()).isEqualTo(new BlockPos(0, 1, 0));
    }

    @Test
    void lockedLayersDoNotMove() {
        SceneEditor ed = new SceneEditor(new Scene());
        Layer l = layer("a", STONE, 1);
        l.setLocked(true);
        ed.addLayer(l);
        ed.nudge(List.of(l), 3, 0, 0, null);
        assertThat(l.offset()).isEqualTo(BlockPos.ORIGIN);
    }

    @Test
    void blockSessionUndoRedo() {
        Scene scene = new Scene();
        SceneEditor ed = new SceneEditor(scene);
        Layer l = layer("a", STONE, 2);
        ed.addLayer(l);
        int[] events = {0};
        scene.addListener(new Scene.Listener() {
            @Override
            public void blocksChanged(Layer layer, io.blockdesigner.core.model.Box localBox) {
                events[0]++;
            }
        });
        try (var s = ed.edit(l, "Paint")) {
            s.set(0, 0, 0, DIRT);
            s.set(0, 0, 0, BlockState.of("gravel"));
            s.set(5, 5, 5, DIRT);
        }
        assertThat(l.structure().get(0, 0, 0).path()).isEqualTo("gravel");
        assertThat(events[0]).isEqualTo(1);
        ed.undoStack().undo();
        assertThat(l.structure().get(0, 0, 0)).isSameAs(STONE);
        assertThat(l.structure().get(5, 5, 5).isAir()).isTrue();
        ed.undoStack().redo();
        assertThat(l.structure().get(5, 5, 5)).isSameAs(DIRT);
    }

    @Test
    void addRemoveAndMergeDownAreUndoable() {
        Scene scene = new Scene();
        SceneEditor ed = new SceneEditor(scene);
        Layer a = layer("a", STONE, 2);
        Layer b = layer("b", DIRT, 1);
        b.setOffset(new BlockPos(3, 0, 0));
        ed.addLayer(a);
        ed.addLayer(b);
        ed.mergeDown(b, a);
        assertThat(scene.layers()).containsExactly(a);
        assertThat(a.structure().get(3, 0, 0)).isSameAs(DIRT);
        ed.undoStack().undo();
        assertThat(scene.layers()).containsExactly(a, b);
        assertThat(a.structure().get(3, 0, 0).isAir()).isTrue();
        ed.removeLayer(a);
        assertThat(scene.layers()).containsExactly(b);
        ed.undoStack().undo();
        assertThat(scene.layers()).containsExactly(a, b);
    }

    @Test
    void groupedEditsUndoAsOneStep() {
        Scene scene = new Scene();
        SceneEditor ed = new SceneEditor(scene);
        Layer l = layer("a", STONE, 1);
        ed.addLayer(l);
        int before = ed.undoStack().size();
        ed.undoStack().beginGroup("AI turn");
        try (var s = ed.edit(l, "fill")) {
            s.set(1, 0, 0, DIRT);
        }
        ed.nudge(List.of(l), 0, 2, 0, null);
        Layer extra = layer("b", DIRT, 1);
        ed.addLayer(extra);
        assertThat(ed.undoStack().undo()).isFalse();
        ed.undoStack().endGroup();
        assertThat(ed.undoStack().size()).isEqualTo(before + 1);
        ed.undoStack().undo();
        assertThat(scene.layers()).containsExactly(l);
        assertThat(l.offset()).isEqualTo(BlockPos.ORIGIN);
        assertThat(l.structure().get(1, 0, 0).isAir()).isTrue();
    }
}
