package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.worldedit.WorldEdit;

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
