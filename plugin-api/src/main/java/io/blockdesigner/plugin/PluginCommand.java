package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.worldedit.WorldEdit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A WorldEdit-style command for the command bar (T or /), e.g. {@code /hello 5 stone}.
 *
 * @param name        without the slash; lower case letters, digits and _
 * @param usage       shown while typing, e.g. {@code /hello <radius> [pattern]}
 * @param description one line for {@code /help}
 */
public record PluginCommand(String name, String usage, String description, Handler handler) {

    public PluginCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (!name.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Command names use a-z, 0-9 and _ only: " + name);
        usage = usage == null || usage.isBlank() ? "/" + name : usage;
        description = description == null ? "" : description;
    }

    /** Runs the command. Edits made through {@link Context#world()} become one undo step. */
    @FunctionalInterface
    public interface Handler {
        /**
         * @return the message shown in the command bar
         * @throws IllegalArgumentException with a friendly message for bad input (shown as an error)
         */
        String run(Context context) throws Exception;
    }

    /**
     * What a command sees.
     *
     * @param args   the words after the command name (flags removed)
     * @param flags  single letters given as {@code -x}
     * @param world  every visible, unlocked layer merged in world coordinates; reads and writes behave like WorldEdit's
     * @param region the region selected with //pos1 //pos2 (or the region tool), if any
     * @param aim    the block under the crosshair, if any
     * @param hand   the held block, if any
     */
    public record Context(List<String> args, List<String> flags, WorldEdit.World world, Optional<Box> region,
                          Optional<BlockPos> aim, Optional<BlockState> hand, BlockResolver blocks) {

        /** The region, or a friendly error when none is selected. */
        public Box requireRegion() {
            return region.orElseThrow(() -> new IllegalArgumentException("Select a region first (//pos1 and //pos2)"));
        }
    }

    /** Turns user text like {@code stone} or {@code oak_stairs[facing=east]} into a block state. */
    @FunctionalInterface
    public interface BlockResolver {
        /** @throws IllegalArgumentException when the block is unknown */
        BlockState resolve(String text);
    }
}
