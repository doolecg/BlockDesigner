package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.worldedit.WorldEdit;

import java.util.Random;

/** What a {@link PluginTransform} sees while it runs. */
public interface TransformContext {

    /**
     * The blocks in world coordinates. Reads see the scene (plus what this run already wrote); writes are recorded.
     * During a preview nothing in the project changes: the difference is shown as ghost blocks. On Apply the same
     * writes become one undo step.
     */
    WorldEdit.World world();

    /** World-space box around the blocks being transformed. */
    Box bounds();

    /** The non-air blocks being transformed (the selected blocks, or the active layer's), in world coordinates. */
    Iterable<BlockPos> solidBlocks();

    /** How many {@link #solidBlocks()} there are. */
    int blockCount();

    /** The values chosen in the dialog. */
    OptionValues options();

    /**
     * Random numbers seeded from the dialog's seed, so the preview and Apply give the same result. Draw from it in a
     * fixed order (e.g. while walking {@link #solidBlocks()}).
     */
    Random random();

    /** Block registry, families and variants. */
    BlockCatalog blocks();

    /** True while previewing, false when applying for real. */
    boolean preview();
}
