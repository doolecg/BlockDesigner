package io.blockdesigner.plugin;

/**
 * An operation on existing blocks: weathering, a palette swap, a gradient fill… Registered with
 * {@link PluginContext#registerTransform}, it shows in the Plugins menu under Transform, in the right-click menu of a
 * selection and as {@code /transform <id>} in the command bar.
 *
 * <p>Choosing it opens a dialog with the transform's {@link #options()}. While the user changes them BlockDesigner
 * runs {@link #apply} again (debounced) in <em>preview</em> mode and shows the result as ghost blocks; Apply runs it
 * for real as one undo step. Write only through {@link TransformContext#world()}.
 *
 * <pre>{@code
 * public void apply(TransformContext c) {
 *     double amount = c.options().decimal("amount");
 *     for (BlockPos p : c.solidBlocks()) {
 *         if (c.random().nextDouble() < amount) {
 *             BlockState st = c.world().get(p);
 *             c.blocks().variant(st, "cracked").ifPresent(v -> c.world().set(p, v));
 *         }
 *     }
 * }
 * }</pre>
 */
public interface PluginTransform {

    /** Which blocks a transform works on. */
    enum Scope {
        /** The selected blocks (or the //pos1 //pos2 region). */
        SELECTION,
        /** Every block of the active layer. */
        LAYER,
        /** The selection when there is one, otherwise the active layer. */
        SELECTION_OR_LAYER
    }

    /** Stable id, unique within the plugin; also the word after {@code /transform}. Use a-z, 0-9 and _ only. */
    String id();

    /** Menu and dialog title, e.g. "Weathering". */
    String name();

    /** One line shown under the title and as the menu tooltip. */
    default String description() {
        return "";
    }

    /**
     * A 16×16 icon as SVG path data, drawn as 1.5px rounded strokes like BlockDesigner's own tool icons (for
     * example {@code "M2 14 L14 2 M5 2 H14 V11"}); {@code null} for none.
     */
    default String icon() {
        return null;
    }

    default Scope scope() {
        return Scope.SELECTION_OR_LAYER;
    }

    /** The parameters shown in the dialog. */
    default Options options() {
        return Options.none();
    }

    /**
     * True when the result depends on {@link TransformContext#random()}: the dialog then shows the seed with a
     * Reroll button, and the same seed always gives the same result.
     */
    default boolean randomized() {
        return false;
    }

    /**
     * Changes the blocks. Runs on the JavaFX thread, once per preview and once more on Apply; keep it quick (a few
     * hundred milliseconds for a large selection at most).
     *
     * @throws IllegalArgumentException with a friendly message when the options don't make sense (shown in the dialog)
     */
    void apply(TransformContext context) throws Exception;
}
