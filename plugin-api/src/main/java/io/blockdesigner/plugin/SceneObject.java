package io.blockdesigner.plugin;

import javafx.scene.control.MenuItem;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Something a plugin puts in the scene that isn't blocks: a reference image, a guide, a marker. BlockDesigner keeps
 * what every object has — its name, {@link Pose}, visibility and lock (see {@link ObjectHandle}) — lists it in the
 * Layers panel with its type's {@link SceneObjectType#badge() badge}, lets the Move, Rotate and Scale tools drive its
 * pose, saves it in the project and makes every change undoable. The object keeps the rest and draws itself.
 *
 * <p>Created by its {@link SceneObjectType} and added with {@link SceneObjects#add}. Every call runs on the JavaFX
 * thread. Since API 3.
 */
public interface SceneObject {

    /**
     * Draws the object in its own space (the pose is applied for you). Called for every frame of the 3D view while the
     * object is visible, so keep it quick; draw nothing to hide it in this view (an object shown only in the Front
     * orthographic view checks {@link ViewInfo#isOrthoSide}).
     */
    void draw(ViewInfo view, Drawing out);

    /**
     * One line under the name in the Layers panel, e.g. {@code "sketch.png · 1920×1080 · 60%"}; empty for none.
     */
    default String description(ObjectHandle self) {
        return "";
    }

    /**
     * The object's own entries for its right-click menu, in the view and in the Layers panel. BlockDesigner adds rename,
     * hide, lock, focus and delete after them. Built each time the menu opens; JavaFX, like panels.
     */
    default List<MenuItem> menu(ObjectHandle self) {
        return List.of();
    }

    /**
     * The object's own state (everything but what {@link ObjectHandle} keeps), for saving and for undo. Keep it small:
     * store big data such as image files with {@link SceneObjects#storeBlob} and save the key instead.
     */
    byte[] save();

    /** Puts back a state from {@link #save()} (opening a project, undo, redo). */
    void load(byte[] data) throws IOException;

    /** Keys of the blobs ({@link SceneObjects#storeBlob}) the object uses now; they are saved with the project. */
    default Set<String> blobs() {
        return Set.of();
    }
}
