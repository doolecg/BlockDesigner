package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.core.transform.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LayeredEditTest {
    final Workspace ws = new Workspace(new Settings());

    static final BlockState POST = BlockState.of("oak_fence").withProperties(Map.of("north", "false", "east", "false", "south", "false",
            "west", "false", "waterlogged", "false"));

    Layer layer(String name) {
        Layer l = new Layer(name, new Structure());
        ws.editor().addLayer(l);
        return l;
    }

    LayeredEdit edit(Layer target) {
        return new LayeredEdit(ws, ws.scene().layers(), target, null, "test", null);
    }

    @Test
    void fencesJoinAcrossLayers() {
        Layer a = layer("a"), b = layer("b");
        a.structure().set(0, 0, 0, POST);
        try (LayeredEdit le = edit(b)) {
            le.setIn(b, new BlockPos(1, 0, 0), POST);
            le.reconnect();
        }
        assertThat(a.structure().get(0, 0, 0).get("east")).isEqualTo("true");
        assertThat(b.structure().get(1, 0, 0).get("west")).isEqualTo("true");
        assertThat(b.structure().get(0, 0, 0).isAir()).as("the neighbour stays in its own layer").isTrue();
        // One undo step takes back both.
        ws.editor().undoStack().undo();
        assertThat(a.structure().get(0, 0, 0).get("east")).isEqualTo("false");
    }

    @Test
    void deletingLetsGoOfNeighboursInOtherLayers() {
        Layer a = layer("a"), b = layer("b");
        a.structure().set(0, 0, 0, POST.with("east", "true"));
        b.structure().set(1, 0, 0, POST.with("west", "true"));
        try (LayeredEdit le = edit(null)) {
            le.setIn(b, new BlockPos(1, 0, 0), BlockState.AIR);
            le.reconnect();
        }
        assertThat(a.structure().get(0, 0, 0).get("east")).isEqualTo("false");
    }

    @Test
    void rotatedLayersReadAndWriteTheWorldsWay() {
        Layer a = layer("a");
        a.setTransform(Transform.rotation(1));
        a.setOffset(new BlockPos(10, 0, 0));
        BlockState stairs = BlockState.parse("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        try (LayeredEdit le = edit(a)) {
            le.set(new BlockPos(10, 0, 0), stairs);
            assertThat(le.get(new BlockPos(10, 0, 0))).isEqualTo(stairs);
        }
        // Stored in the layer's own frame: a quarter turn back, facing west.
        assertThat(a.structure().get(0, 0, 0).get("facing")).isEqualTo("west");
    }

    @Test
    void setReplacesTheTopBlockAndClearsBelow() {
        Layer a = layer("a"), b = layer("b");
        a.structure().set(0, 0, 0, BlockState.of("dirt"));
        b.structure().set(0, 0, 0, BlockState.of("stone"));
        try (LayeredEdit le = edit(a)) {
            assertThat(le.get(BlockPos.ORIGIN)).isEqualTo(BlockState.of("stone"));
            le.set(BlockPos.ORIGIN, BlockState.of("gold_block"));
        }
        assertThat(b.structure().get(0, 0, 0)).isEqualTo(BlockState.of("gold_block"));
        assertThat(a.structure().get(0, 0, 0).isAir()).isTrue();
    }

    @Test
    void lockedLayersAreReadButNotWritten() {
        Layer a = layer("a"), locked = layer("locked");
        locked.structure().set(0, 0, 0, POST);
        locked.setLocked(true);
        try (LayeredEdit le = edit(a)) {
            le.setIn(a, new BlockPos(1, 0, 0), POST);
            le.reconnect();
            le.set(BlockPos.ORIGIN, BlockState.AIR);
        }
        assertThat(a.structure().get(1, 0, 0).get("west")).as("joins the locked fence").isEqualTo("true");
        assertThat(locked.structure().get(0, 0, 0)).isEqualTo(POST);
    }

    @Test
    void placementSeesOtherLayersAsTaken() {
        Layer a = layer("a"), b = layer("b");
        a.structure().set(0, 0, 0, BlockState.of("stone"));
        try (LayeredEdit le = edit(b)) {
            var ctx = new BlockPlacement.Context(BlockPos.ORIGIN, null, BlockPlacement.Dir.UP, 0.5, 0, 0.5, 0, -1, 1);
            assertThat(BlockPlacement.place(BlockState.of("dirt"), ctx, le::get, BlockPlacement.Blocks.NONE)).isEmpty();
        }
        assertThat(List.of(b.structure().blockCount())).containsExactly(0L);
    }

    @Test
    void entitiesGoToTheTargetInItsFrame() {
        Layer a = layer("a");
        a.setOffset(new BlockPos(5, 0, 0));
        try (LayeredEdit le = edit(a)) {
            le.addEntity(io.blockdesigner.core.model.EntityTypes.create("pig", 6.5, 1, 0.5, 0));
            assertThat(le.entities(new io.blockdesigner.core.model.Box(6, 1, 0, 6, 1, 0))).hasSize(1);
        }
        assertThat(a.structure().entities()).singleElement().satisfies(e -> assertThat(e.x()).isEqualTo(1.5));
        try (LayeredEdit le = edit(a)) {
            assertThat(le.removeEntities(new io.blockdesigner.core.model.Box(0, 0, 0, 10, 5, 5))).isEqualTo(1);
        }
        assertThat(a.structure().entities()).isEmpty();
    }
}
