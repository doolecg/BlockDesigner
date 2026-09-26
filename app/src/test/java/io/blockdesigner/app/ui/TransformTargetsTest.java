package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.AppBlockCatalog;
import io.blockdesigner.app.plugins.TransformRunner;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.transform.Transform;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.TransformContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Plugin transforms: what they work on, previews that change nothing, and Apply as one undo step. */
class TransformTargetsTest {
    private final Workspace ws = new Workspace(new Settings());
    private final AppBlockCatalog catalog = new AppBlockCatalog(() -> null);

    /** Cracks a share of the blocks, like the example plugin's weathering. */
    private final PluginTransform crack = new PluginTransform() {
        public String id() {
            return "crack";
        }

        public String name() {
            return "Crack";
        }

        public boolean randomized() {
            return true;
        }

        public Options options() {
            return Options.builder().decimal("amount", "Amount", 0.5, 0, 1).build();
        }

        public void apply(TransformContext c) {
            double amount = c.options().decimal("amount");
            for (BlockPos p : c.solidBlocks()) {
                if (c.random().nextDouble() >= amount) continue;
                c.blocks().variant(c.world().get(p), "cracked").ifPresent(v -> c.world().set(p, v));
            }
        }
    };

    private Layer wall(String name, int z) {
        Structure s = new Structure();
        for (int x = 0; x < 10; x++) for (int y = 0; y < 10; y++) s.set(x, y, z, BlockState.of("stone_bricks"));
        Layer l = new Layer(name, s);
        ws.editor().addLayer(l);
        return l;
    }

    private Map<BlockPos, BlockState> snapshot(Layer l) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        l.structure().forEachBlock((x, y, z, st) -> out.put(new BlockPos(x, y, z), st));
        return out;
    }

    @Test
    void previewChangesNothingAndApplyMatchesItAsOneUndoStep() throws Exception {
        Layer a = wall("a", 0);
        Map<BlockPos, BlockState> before = snapshot(a);
        var target = TransformTargets.resolve(ws, PluginTransform.Scope.SELECTION_OR_LAYER, Map.of(), null, ws.scene().layers(), a);
        assertThat(target.layer()).isSameAs(a);
        assertThat(target.blocks()).hasSize(100);
        OptionValues opts = crack.options().defaults();

        var preview = TransformRunner.run(crack, TransformTargets.readWorld(ws, target, ws.scene().layers()), target, opts, 7, catalog, true);
        assertThat(preview.size()).isBetween(30, 70);
        assertThat(preview.blocks().values()).containsOnly(BlockState.of("cracked_stone_bricks"));
        assertThat(snapshot(a)).as("a preview leaves the scene alone").isEqualTo(before);
        assertThat(ws.editor().undoStack().canUndo()).isTrue(); // only the "add layer" step
        int steps = ws.editor().undoStack().size();

        // Same seed, same options: Apply does exactly what the preview showed.
        var applied = TransformRunner.run(crack, TransformTargets.readWorld(ws, target, ws.scene().layers()), target, opts, 7, catalog, false);
        assertThat(applied).isEqualTo(preview);
        assertThat(TransformTargets.apply(ws, target, applied, "Crack", ws.scene().layers(), a)).isEqualTo(preview.size());
        assertThat(ws.editor().undoStack().size()).isEqualTo(steps + 1);
        assertThat(ws.editor().undoStack().undoLabel()).contains("Crack");
        preview.blocks().forEach((p, st) -> assertThat(a.structure().get(a.toLocal(p))).isEqualTo(st));

        ws.editor().undoStack().undo();
        assertThat(snapshot(a)).isEqualTo(before);

        // Another seed cracks other blocks.
        var other = TransformRunner.run(crack, TransformTargets.readWorld(ws, target, ws.scene().layers()), target, opts, 8, catalog, true);
        assertThat(other.blocks().keySet()).isNotEqualTo(preview.blocks().keySet());
    }

    @Test
    void selectionAcrossLayersSkipsLockedOnesAndWritesWhereBlocksAre() throws Exception {
        Layer a = wall("a", 0), b = wall("b", 1), locked = wall("locked", 2);
        b.setTransform(Transform.rotation(2));
        locked.setLocked(true);
        Map<String, Set<Long>> sel = new HashMap<>();
        for (Layer l : List.of(a, b, locked)) {
            Set<Long> cells = new HashSet<>();
            cells.add(BlockPos.pack(0, 0, l == a ? 0 : l == b ? 1 : 2));
            sel.put(l.id(), cells);
        }
        var target = TransformTargets.resolve(ws, PluginTransform.Scope.SELECTION, sel, null, ws.scene().layers(), a);
        assertThat(target.blocks()).hasSize(2);
        assertThat(target.layer()).isNull();
        var changes = TransformRunner.run(crack, TransformTargets.readWorld(ws, target, ws.scene().layers()), target,
                crack.options().defaults().with("amount", 1.0), 1, catalog, false);
        assertThat(changes.size()).isEqualTo(2);
        TransformTargets.apply(ws, target, changes, "Crack", ws.scene().layers(), a);
        assertThat(a.structure().get(0, 0, 0)).isEqualTo(BlockState.of("cracked_stone_bricks"));
        assertThat(b.structure().get(0, 0, 1)).as("written into the layer that holds it, even turned").isEqualTo(BlockState.of("cracked_stone_bricks"));
        assertThat(locked.structure().get(0, 0, 2)).isEqualTo(BlockState.of("stone_bricks"));
        assertThat(snapshot(a).values()).filteredOn(st -> st.path().startsWith("cracked")).hasSize(1);
    }

    @Test
    void scopesAndEmptyTargets() {
        Layer a = wall("a", 0);
        assertThat(TransformTargets.resolve(ws, PluginTransform.Scope.SELECTION, Map.of(), null, ws.scene().layers(), a)).isNull();
        var region = TransformTargets.resolve(ws, PluginTransform.Scope.SELECTION, Map.of(),
                new io.blockdesigner.core.model.Box(0, 0, 0, 2, 2, 5), ws.scene().layers(), a);
        assertThat(region.blocks()).hasSize(9);
        assertThat(region.label()).contains("region");
        a.setLocked(true);
        assertThat(TransformTargets.resolve(ws, PluginTransform.Scope.LAYER, Map.of(), null, ws.scene().layers(), a)).isNull();
        assertThat(TransformTargets.nothingMessage(PluginTransform.Scope.LAYER, a)).contains("locked");
    }
}
