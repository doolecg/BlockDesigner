package io.blockdesigner.plugin;

import java.util.List;
import java.util.Optional;

/** The plugin's {@link SceneObject}s in the open project, from {@link PluginContext#objects()}. Since API 3. */
public interface SceneObjects {

    /** The plugin's objects in the scene, in Layers panel order (top first). */
    List<ObjectHandle> list();

    /**
     * Adds an object of one of the plugin's registered types as one undo step and selects it.
     *
     * @param type   the {@link SceneObjectType#id()} it was made by
     * @param name   shown in the Layers panel
     * @param pose   where it goes
     * @param object the object ({@code type.create()}, set up)
     */
    ObjectHandle add(String type, String name, Pose pose, SceneObject object);

    /** The selected object, if it is one of the plugin's. */
    Optional<ObjectHandle> selected();

    /** How the 3D view looks at the scene now (for placing something new in front of the camera). */
    ViewInfo view();

    /**
     * Keeps a piece of data (an image file, say) with the project and returns its key, the same for the same bytes. An
     * object lists the keys it uses in {@link SceneObject#blobs()}; blobs no object uses are left out of the next save.
     */
    String storeBlob(byte[] data);

    /** The data stored under {@code key}, if the project has it. */
    Optional<byte[]> blob(String key);
}
