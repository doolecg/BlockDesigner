package io.blockdesigner.ai.tools;

import io.blockdesigner.assets.BlockRegistry;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;

import java.util.concurrent.CompletableFuture;

/** What the build tools need from the host application. Called on the thread that owns the scene. */
public interface BuildContext {
    Scene scene();

    SceneEditor editor();

    /** Known blocks for validation and search; null when no assets are loaded. */
    BlockRegistry registry();

    /** Minecraft version the build targets, for the model's information. */
    String targetVersion();

    /**
     * Renders the scene from a named viewpoint (iso_ne, iso_nw, iso_se, iso_sw, top, north, south, east, west) and
     * completes with PNG bytes. May complete on any thread.
     */
    CompletableFuture<byte[]> renderView(String view, int width, int height);

    /** Visual feedback: briefly highlight a world-space box in the viewport. */
    default void highlight(io.blockdesigner.core.model.Box worldBox) {
    }

    /** Called before layer-creating tools so the host can pick names/colours; returns the created layer. */
    default Layer createLayer(String name) {
        Layer l = new Layer(name, new io.blockdesigner.core.model.Structure());
        editor().addLayer(l);
        return l;
    }
}
