package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.TransformRunner;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.PluginTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a plugin transform works and how its result is written: the selected blocks (across layers, like any world
 * edit) or the whole active layer (edits stay in that layer). Kept apart from the viewport so it can be tested.
 */
final class TransformTargets {
    private TransformTargets() {
    }

    /**
     * The blocks a transform of {@code scope} works on, or null when there are none.
     *
     * @param selection the selected blocks: layer id to packed layer-local positions
     * @param region    the //pos1 //pos2 region, used when no blocks are selected (may be null)
     * @param layers    the layers edits see (visible ones), bottom first
     * @param active    the active layer (may be null)
     */
    static TransformRunner.Target resolve(Workspace ws, PluginTransform.Scope scope, Map<String, Set<Long>> selection, Box region,
                                          List<Layer> layers, Layer active) {
        TransformRunner.Target t = null;
        if (scope != PluginTransform.Scope.LAYER) t = selection(ws, selection, region, layers);
        if (t == null && scope != PluginTransform.Scope.SELECTION && active != null && !active.locked()) t = layer(active);
        return t;
    }

    /** What to say when {@link #resolve} finds nothing. */
    static String nothingMessage(PluginTransform.Scope scope, Layer active) {
        return switch (scope) {
            case SELECTION -> "Select some blocks first (Select mode, or //pos1 and //pos2)";
            case LAYER -> active == null ? "No active layer" : active.locked() ? "The active layer is locked" : "The active layer is empty";
            case SELECTION_OR_LAYER -> active != null && active.locked() ? "Select some blocks first, or unlock the active layer"
                    : "Select some blocks first, or pick a layer with blocks in it";
        };
    }

    private static TransformRunner.Target selection(Workspace ws, Map<String, Set<Long>> selection, Box region, List<Layer> layers) {
        List<BlockPos> blocks = new ArrayList<>();
        for (var e : selection.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            // Locked layers are never written, so their blocks aren't part of what a transform changes.
            if (l == null || l.locked() || !layers.contains(l)) continue;
            for (long packed : e.getValue()) {
                BlockPos local = BlockPos.unpack(packed);
                if (!l.structure().get(local).isAir()) blocks.add(l.toWorld(local));
            }
        }
        if (!blocks.isEmpty()) {
            blocks.sort(null);
            return TransformRunner.Target.of(blocks, String.format("%,d selected block%s", blocks.size(), blocks.size() == 1 ? "" : "s"), null);
        }
        if (region == null || region.volume() > WorldEdit.MAX_VOLUME) return null;
        WorldEdit.World world = readWorld(ws, null, layers);
        for (int y = region.minY(); y <= region.maxY(); y++)
            for (int z = region.minZ(); z <= region.maxZ(); z++)
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!world.get(p).isAir()) blocks.add(p);
                }
        return TransformRunner.Target.of(blocks, String.format("%,d block%s in the region", blocks.size(), blocks.size() == 1 ? "" : "s"), null);
    }

    private static TransformRunner.Target layer(Layer l) {
        List<BlockPos> blocks = new ArrayList<>();
        l.structure().forEachBlock((x, y, z, st) -> blocks.add(l.toWorld(x, y, z)));
        blocks.sort(null);
        return TransformRunner.Target.of(blocks, "layer " + l.name(), l);
    }

    /**
     * The blocks as a transform reads them: the target layer alone, or every edit layer merged (top-most wins). Never
     * written to, so it records nothing.
     */
    static WorldEdit.World readWorld(Workspace ws, TransformRunner.Target target, List<Layer> layers) {
        return target != null && target.layer() != null ? new LayeredEdit(ws, List.of(target.layer()), target.layer(), null, "", null)
                : new LayeredEdit(ws, layers, null, null, "", null);
    }

    /**
     * Writes a transform's changes as one undo step. A layer target is written into that layer only; a selection
     * changes each cell in the layer showing a block there (new blocks in empty cells go into the active layer).
     *
     * @return how many cells changed
     */
    static int apply(Workspace ws, TransformRunner.Target target, TransformRunner.Changes changes, String label, List<Layer> layers, Layer active) {
        if (changes.isEmpty()) return 0;
        var undo = ws.editor().undoStack();
        undo.beginGroup(label);
        LayeredEdit le = target.layer() != null ? new LayeredEdit(ws, List.of(target.layer()), target.layer(), null, label, null)
                : new LayeredEdit(ws, layers, active != null && !active.locked() ? active : null, null, label, null);
        try {
            changes.writeTo(le);
        } finally {
            le.close();
            undo.endGroup();
        }
        return changes.size();
    }

    /** Cells a preview should outline in red: those the transform empties. */
    static List<BlockPos> removed(TransformRunner.Changes changes) {
        List<BlockPos> out = new ArrayList<>();
        changes.blocks().forEach((p, st) -> {
            if (st.isAir()) out.add(p);
        });
        return out;
    }

    /** Ghost blocks for a preview: the new state of every cell that doesn't become air. */
    static Map<BlockPos, BlockState> ghosts(TransformRunner.Changes changes) {
        Map<BlockPos, BlockState> out = new java.util.LinkedHashMap<>();
        changes.blocks().forEach((p, st) -> {
            if (!st.isAir()) out.put(p, st);
        });
        return out;
    }
}
