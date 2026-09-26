package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.transform.Transform;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTransferTest {
    final Workspace ws = new Workspace(new Settings());
    static final BlockState STAIRS = BlockState.of("oak_stairs").withProperties(Map.of("facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false"));
    static final BlockState CHEST = BlockState.of("chest").withProperties(Map.of("facing", "north", "type", "single", "waterlogged", "false"));
    static final BlockState STONE = BlockState.of("stone");

    Layer layer(String name) {
        Layer l = new Layer(name, new Structure());
        ws.editor().addLayer(l);
        return l;
    }

    static Map<String, Set<Long>> sel(Layer l, BlockPos... cells) {
        Map<String, Set<Long>> m = new HashMap<>();
        for (BlockPos p : cells) m.computeIfAbsent(l.id(), k -> new java.util.HashSet<>()).add(p.pack());
        return m;
    }

    @Test
    void liftMoveAndDropWithinALayerIsOneUndoStep() {
        Layer a = layer("a");
        a.structure().set(0, 0, 0, STAIRS);
        a.structure().set(1, 0, 0, CHEST);
        a.structure().setBlockEntity(new BlockPos(1, 0, 0), new CompoundTag().putString("CustomName", "loot"));
        a.structure().set(3, 0, 0, STONE);
        var undo = ws.editor().undoStack();

        undo.beginGroup("Move selection");
        Layer f = new Layer("floating", new Structure());
        var lifted = SelectionTransfer.transfer(ws.editor(), sel(a, new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), src -> {
            ws.editor().addLayer(f);
            return f;
        }, "lift");
        assertThat(a.structure().get(0, 0, 0).isAir()).isTrue();
        assertThat(lifted.get(f.id())).hasSize(2);
        ws.editor().nudge(List.of(f), 2, 0, 0, null);
        var dropped = SelectionTransfer.transfer(ws.editor(), lifted, x -> a, "drop");
        ws.editor().removeLayer(f);
        undo.endGroup();

        // The pair moved two blocks east, over the stone, with the chest's data.
        assertThat(a.structure().get(0, 0, 0).isAir()).isTrue();
        assertThat(a.structure().get(1, 0, 0).isAir()).isTrue();
        assertThat(a.structure().get(2, 0, 0)).isEqualTo(STAIRS);
        assertThat(a.structure().get(3, 0, 0)).isEqualTo(CHEST);
        assertThat(a.structure().blockEntity(new BlockPos(3, 0, 0)).getString("CustomName")).isEqualTo("loot");
        assertThat(dropped.get(a.id())).containsExactlyInAnyOrder(new BlockPos(2, 0, 0).pack(), new BlockPos(3, 0, 0).pack());
        assertThat(ws.scene().layers()).containsExactly(a);

        undo.undo();
        assertThat(a.structure().get(0, 0, 0)).isEqualTo(STAIRS);
        assertThat(a.structure().get(1, 0, 0)).isEqualTo(CHEST);
        assertThat(a.structure().get(3, 0, 0)).isEqualTo(STONE);
        assertThat(ws.scene().layers()).containsExactly(a);
    }

    @Test
    void movingIntoAnotherLayerKeepsWorldPlaceAndFacing() {
        Layer turned = layer("turned");
        turned.setTransform(Transform.rotation(1));
        turned.setOffset(new BlockPos(10, 0, 5));
        turned.structure().set(1, 2, 3, STAIRS);
        BlockPos world = turned.toWorld(new BlockPos(1, 2, 3));
        BlockState seen = BlockTransformer.defaults().apply(STAIRS, turned.transform());
        Layer active = layer("active");

        var out = SelectionTransfer.transfer(ws.editor(), sel(turned, new BlockPos(1, 2, 3)), src -> active, "move");

        assertThat(turned.structure().get(1, 2, 3).isAir()).isTrue();
        assertThat(active.structure().get(world)).isEqualTo(seen);
        assertThat(out.get(active.id())).containsExactly(world.pack());
    }

    @Test
    void lockedLayersKeepTheirBlocks() {
        Layer a = layer("a"), b = layer("b");
        a.structure().set(0, 0, 0, STONE);
        a.setLocked(true);
        var out = SelectionTransfer.transfer(ws.editor(), sel(a, new BlockPos(0, 0, 0)), src -> b, "move");
        assertThat(a.structure().get(0, 0, 0)).isEqualTo(STONE);
        assertThat(out).containsOnlyKeys(a.id());
    }
}
