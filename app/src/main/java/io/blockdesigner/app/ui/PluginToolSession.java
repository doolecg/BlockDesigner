package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.OptionStore;
import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.app.plugins.TransformRunner;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.BlockCatalog;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginTransform;
import io.blockdesigner.plugin.ToolContext;
import io.blockdesigner.plugin.ToolEvent;
import io.blockdesigner.plugin.ToolHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An active plugin tool: its handler, the context it was given, its option values and any stroke it has open. The
 * viewport forwards input here; every call into the plugin is guarded so a failing tool can't break the viewport.
 */
final class PluginToolSession implements ToolContext {

    /** What the session needs from the viewport. */
    interface Viewport {
        void showPreview(Map<BlockPos, BlockState> ghosts, List<BlockPos> removed, List<Box> outlines);

        /** A world edit across the visible layers (new blocks into the active layer), labelled for undo. */
        LayeredEdit newEdit(String label);

        /** The selected blocks as a transform target, or null when nothing is selected. */
        TransformRunner.Target selection();

        /** The blocks as a transform on {@code target} reads them. */
        WorldEdit.World readWorld(TransformRunner.Target target);

        /** Writes a transform's changes as one undo step; returns how many cells changed. */
        int apply(String label, TransformRunner.Target target, TransformRunner.Changes changes);
    }

    private final Workspace ws;
    private final PluginManager plugins;
    private final PluginManager.Tool tool;
    private final Viewport viewport;
    private final String optionsKey;
    private OptionValues options;
    private ToolHandler handler;
    private final List<StrokeImpl> open = new ArrayList<>();
    private Map<BlockPos, BlockState> ghosts = Map.of();
    private Box outline;

    PluginToolSession(Workspace ws, PluginManager plugins, PluginManager.Tool tool, Viewport viewport) {
        this.ws = ws;
        this.plugins = plugins;
        this.tool = tool;
        this.viewport = viewport;
        this.optionsKey = OptionStore.key(tool.plugin().info().id(), "tool", tool.tool().id());
        this.options = plugins.optionStore().load(optionsKey, tool.tool().options(), plugins.blocks());
    }

    PluginManager.Tool tool() {
        return tool;
    }

    PluginManager plugins() {
        return plugins;
    }

    /** Whether the tool leaves the left button to block selection (see {@link io.blockdesigner.plugin.PluginTool#selects}). */
    boolean selects() {
        try {
            return tool.tool().selects();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Calls the plugin's {@code activate}; false (and a toast) when it fails. */
    boolean activate() {
        try {
            handler = tool.tool().activate(this);
            if (handler == null) handler = new ToolHandler() {
            };
            return true;
        } catch (Throwable t) {
            plugins.report(tool.plugin(), "Tool '" + tool.tool().name() + "'", t);
            return false;
        }
    }

    /** The options bar changed a value. */
    void setOptions(OptionValues values) {
        options = values;
        plugins.optionStore().save(optionsKey, values);
        call("optionsChanged", ToolHandler::optionsChanged);
    }

    void hover(ToolEvent e) {
        call("hover", h -> h.hover(e));
    }

    void press(ToolEvent e) {
        call("press", h -> h.press(e));
    }

    void drag(ToolEvent e) {
        call("drag", h -> h.drag(e));
    }

    void release(ToolEvent e) {
        call("release", h -> h.release(e));
    }

    boolean scroll(ToolEvent e, double delta) {
        return ask("scroll", h -> h.scroll(e, delta));
    }

    boolean key(String key) {
        return ask("key", h -> h.key(key));
    }

    /** Another tool was picked: the handler finishes, open strokes are committed and the preview goes. */
    void deactivate() {
        call("deactivate", ToolHandler::deactivate);
        for (StrokeImpl s : List.copyOf(open)) s.commit();
        preview().clear();
        handler = null;
    }

    private void call(String what, Consumer<ToolHandler> action) {
        ask(what, h -> {
            action.accept(h);
            return true;
        });
    }

    private boolean ask(String what, Function<ToolHandler, Boolean> action) {
        if (handler == null) return false;
        try {
            return action.apply(handler);
        } catch (Throwable t) {
            plugins.report(tool.plugin(), "Tool '" + tool.tool().name() + "' (" + what + ")", t);
            return false;
        } finally {
            // Show what open strokes wrote during this event.
            for (StrokeImpl s : open) s.edit.flush();
        }
    }

    // ---- ToolContext --------------------------------------------------------------------------------------------

    @Override
    public PluginContext plugin() {
        return tool.plugin().context();
    }

    @Override
    public OptionValues options() {
        return options;
    }

    @Override
    public Optional<BlockState> hand() {
        return Optional.ofNullable(ws.blockToPlace()).filter(b -> !b.isAir());
    }

    @Override
    public BlockCatalog blocks() {
        return plugins.blocks();
    }

    @Override
    public Preview preview() {
        return new Preview() {
            @Override
            public void ghost(Map<BlockPos, BlockState> blocks) {
                ghosts = new LinkedHashMap<>(blocks);
                show();
            }

            @Override
            public void outline(Box box) {
                outline = box;
                show();
            }

            @Override
            public void clear() {
                ghosts = Map.of();
                outline = null;
                show();
            }
        };
    }

    private void show() {
        Map<BlockPos, BlockState> solid = new LinkedHashMap<>();
        List<BlockPos> removed = new ArrayList<>();
        ghosts.forEach((p, st) -> {
            if (st.isAir()) removed.add(p);
            else solid.put(p, st);
        });
        viewport.showPreview(solid, removed, outline == null ? List.of() : List.of(outline));
    }

    @Override
    public Optional<Selection> selection() {
        TransformRunner.Target t = viewport.selection();
        return t == null ? Optional.empty() : Optional.of(new Selection(t.bounds(), t.blocks()));
    }

    @Override
    public int previewTransform(PluginTransform transform, OptionValues values, long seed) {
        TransformRunner.Target t = viewport.selection();
        if (t == null) {
            preview().clear();
            return 0;
        }
        TransformRunner.Changes c = run(transform, t, values, seed, true);
        // Air in the changes shows as a red outline, as with the tool's own ghosts.
        ghosts = new LinkedHashMap<>(c.blocks());
        outline = t.bounds();
        show();
        return c.size();
    }

    @Override
    public int applyTransform(PluginTransform transform, OptionValues values, long seed) {
        TransformRunner.Target t = viewport.selection();
        preview().clear();
        if (t == null) return 0;
        return viewport.apply(transform.name(), t, run(transform, t, values, seed, false));
    }

    private TransformRunner.Changes run(PluginTransform transform, TransformRunner.Target t, OptionValues values, long seed, boolean preview) {
        try {
            return TransformRunner.run(transform, viewport.readWorld(t), t, values, seed, plugins.blocks(), preview);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(transform.name() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Stroke beginStroke(String label) {
        StrokeImpl s = new StrokeImpl(label == null || label.isBlank() ? tool.tool().name() : label);
        open.add(s);
        return s;
    }

    /** An undo group plus a layered edit, from press to release. */
    private final class StrokeImpl implements Stroke {
        final LayeredEdit edit;
        private boolean done;

        StrokeImpl(String label) {
            ws.editor().undoStack().beginGroup(label);
            edit = viewport.newEdit(label);
        }

        @Override
        public WorldEdit.World world() {
            if (done) throw new IllegalStateException("The stroke is over");
            return edit;
        }

        @Override
        public void commit() {
            if (done) return;
            done = true;
            open.remove(this);
            try {
                // Fences, walls, stairs and the like join up with what was built, as with any world edit.
                edit.reconnect();
                edit.close();
            } finally {
                ws.editor().undoStack().endGroup();
            }
        }

        @Override
        public void cancel() {
            if (done) return;
            done = true;
            open.remove(this);
            try {
                edit.rollback();
            } finally {
                ws.editor().undoStack().endGroup();
            }
        }
    }
}
