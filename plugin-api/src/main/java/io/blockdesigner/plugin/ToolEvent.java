package io.blockdesigner.plugin;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.place.BlockPlacement;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Mouse input for a {@link ToolHandler}.
 *
 * @param hit       what the mouse points at, if anything (a block, or the ground grid)
 * @param button    the button pressed or released ({@link Button#NONE} for hover, scroll and drag without one)
 * @param modifiers the modifier keys held
 * @param rayOrigin where the mouse ray starts (the camera), world coordinates
 * @param rayDir    the mouse ray's direction, normalised
 */
public record ToolEvent(Optional<Hit> hit, Button button, Set<Modifier> modifiers, Vec3 rayOrigin, Vec3 rayDir) {

    public enum Button { NONE, PRIMARY, SECONDARY, MIDDLE }

    public enum Modifier { SHIFT, CTRL, ALT }

    /** A point or direction in world space. */
    public record Vec3(double x, double y, double z) {
    }

    /**
     * What the mouse points at.
     *
     * @param block    the block hit (for the ground: the cell just below the grid)
     * @param face     the face of {@code block} that was hit
     * @param adjacent the empty cell in front of that face, where a placed block would go
     * @param layer    the layer holding {@code block}; empty for the ground
     */
    public record Hit(BlockPos block, BlockPlacement.Dir face, BlockPos adjacent, Optional<Layer> layer) {
    }

    public ToolEvent {
        Objects.requireNonNull(hit, "hit");
        modifiers = Set.copyOf(modifiers);
    }

    public boolean shift() {
        return modifiers.contains(Modifier.SHIFT);
    }

    public boolean ctrl() {
        return modifiers.contains(Modifier.CTRL);
    }

    public boolean alt() {
        return modifiers.contains(Modifier.ALT);
    }
}
