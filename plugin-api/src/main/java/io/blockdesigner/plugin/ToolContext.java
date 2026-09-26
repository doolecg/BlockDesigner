package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.worldedit.WorldEdit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What an active {@link PluginTool} can use. Valid until {@link ToolHandler#deactivate()}. */
public interface ToolContext {

    PluginContext plugin();

    /** The current values of the tool's options bar (they change while the tool is active: read them each time). */
    OptionValues options();

    /** The block in hand (the selected hotbar slot), if any. */
    Optional<BlockState> hand();

    BlockCatalog blocks();

    /** Ghost blocks and outlines drawn over the scene while the tool works. */
    Preview preview();

    /**
     * Starts an edit that becomes one undo step when committed: open it on press, write while dragging, commit on
     * release. Like {@link PluginContext#editWorld}, it sees every visible layer merged and changes blocks where they
     * are (new blocks go into the active layer). A stroke still open when the tool is deactivated is committed.
     */
    Stroke beginStroke(String label);

    /**
     * The selected blocks (Select mode, or the //pos1 //pos2 region), in world coordinates; empty when nothing is
     * selected. Blocks on locked or hidden layers are left out. API 5.
     */
    Optional<Selection> selection();

    /**
     * Runs {@code transform} on the {@link #selection()} with these options and seed (the transform's own options are
     * ignored), and shows what it would change as ghosts with the selection outlined, replacing the preview. Nothing
     * in the project changes. Returns how many blocks would change; 0 (and a cleared preview) when nothing is
     * selected. Exceptions from the transform are passed on. API 5.
     */
    int previewTransform(PluginTransform transform, OptionValues options, long seed);

    /**
     * Runs {@code transform} on the {@link #selection()} for real as one undo step labelled with its name, the same
     * way as {@link #previewTransform} (same options and seed, same result), and clears the preview. Returns how many
     * blocks changed. API 5.
     */
    int applyTransform(PluginTransform transform, OptionValues options, long seed);

    /**
     * The selected blocks.
     *
     * @param bounds world box around {@code blocks}
     * @param blocks the non-air selected blocks, world positions
     */
    record Selection(Box bounds, List<BlockPos> blocks) {
        public Selection {
            blocks = List.copyOf(blocks);
        }
    }

    /** Ghost blocks and outlines drawn over the scene. Cleared when the tool is deactivated. */
    interface Preview {
        /** Shows these blocks (world positions) as ghosts, replacing the ghosts shown before; air entries are outlined in red. */
        void ghost(Map<BlockPos, BlockState> blocks);

        /** Outlines a box (world coordinates), replacing the outline shown before; null removes it. */
        void outline(Box box);

        /** Removes the ghosts and the outline. */
        void clear();
    }

    /** An open edit (see {@link #beginStroke}). Closing it commits. */
    interface Stroke extends AutoCloseable {
        /** Reads and writes blocks in world coordinates; reads see this stroke's own writes. */
        WorldEdit.World world();

        /** Ends the stroke as one undo step (nothing is recorded when nothing changed). */
        void commit();

        /** Ends the stroke and puts every block it changed back. */
        void cancel();

        @Override
        default void close() {
            commit();
        }
    }
}
