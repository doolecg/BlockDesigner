package io.blockdesigner.app.ui;

import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.transform.BlockTransformer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Moves selected blocks from their layers into other layers, keeping where they stand in the world and their facing. */
final class SelectionTransfer {
    private SelectionTransfer() {
    }

    private record Moved(BlockPos world, BlockState state, CompoundTag nbt) {
    }

    /**
     * Moves the selected blocks ({@code selection}: layer id → packed layer-local positions) of each unlocked layer
     * into {@code dest(source)}, with their block data (chest contents, sign text…). Blocks already in the destination
     * are replaced. A null destination, or the source itself, leaves those blocks where they are. Every change is
     * undoable (call inside an undo group for one step). Returns the selection afterwards.
     */
    static Map<String, Set<Long>> transfer(SceneEditor editor, Map<String, Set<Long>> selection, Function<Layer, Layer> dest, String label) {
        BlockTransformer bt = BlockTransformer.defaults();
        Map<Layer, List<Moved>> into = new LinkedHashMap<>();
        Map<Layer, Set<Long>> from = new LinkedHashMap<>();
        Map<String, Set<Long>> out = new LinkedHashMap<>();
        for (var e : selection.entrySet()) {
            Layer src = editor.scene().find(e.getKey()).orElse(null);
            if (src == null) continue;
            Layer d = src.locked() ? null : dest.apply(src);
            if (d == null || d == src || d.locked()) {
                out.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
                continue;
            }
            List<Moved> list = into.computeIfAbsent(d, k -> new ArrayList<>());
            for (long packed : e.getValue()) {
                BlockPos p = BlockPos.unpack(packed);
                BlockState st = src.structure().get(p);
                if (st.isAir()) continue;
                CompoundTag nbt = src.structure().blockEntity(p);
                list.add(new Moved(src.toWorld(p), bt.apply(st, src.transform()), nbt == null ? null : nbt.copy()));
            }
            from.put(src, e.getValue());
        }
        // Clear the sources first, so blocks moved within one layer can land on each other's old places.
        from.forEach((src, cells) -> {
            try (var s = editor.edit(src, label)) {
                for (long packed : cells) {
                    BlockPos p = BlockPos.unpack(packed);
                    if (!src.structure().get(p).isAir()) s.setExact(p.x(), p.y(), p.z(), BlockState.AIR, null);
                }
            }
        });
        into.forEach((d, list) -> {
            Set<Long> sel = out.computeIfAbsent(d.id(), k -> new HashSet<>());
            var inv = d.transform().inverse();
            try (var s = editor.edit(d, label)) {
                for (Moved m : list) {
                    BlockPos lp = d.toLocal(m.world());
                    s.setExact(lp.x(), lp.y(), lp.z(), bt.apply(m.state(), inv), m.nbt());
                    sel.add(lp.pack());
                }
            }
        });
        return out;
    }
}
