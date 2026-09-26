package io.blockdesigner.plugin;

/**
 * BlockDesigner's side of one {@link SceneObject} in the scene: what every object has (name, pose, visibility, lock)
 * and undoable changes. The setters each make one undo step. Use from the JavaFX thread. Since API 3.
 */
public interface ObjectHandle {

    /** Stable id, unique in the project (saved with it). */
    String id();

    /** The object's {@link SceneObjectType#id() type id}. */
    String type();

    /** The plugin's object. */
    SceneObject object();

    /** Whether it is still in the scene (false once deleted, or while its plugin is off). */
    boolean exists();

    String name();

    void setName(String name);

    Pose pose();

    /** Moves, turns or scales the object as one undo step named {@code label}. */
    void setPose(Pose pose, String label);

    boolean visible();

    void setVisible(boolean visible);

    /** A locked object is drawn but can't be picked, moved, turned or scaled in the view. */
    boolean locked();

    void setLocked(boolean locked);

    /**
     * Changes the plugin's own state as one undo step: {@code change} runs now, and undo puts back what
     * {@link SceneObject#save()} returned before it. The view and the Layers panel update afterwards.
     */
    void edit(String label, Runnable change);

    /** Redraws after a change that needs no undo step (e.g. a picture finished loading). */
    void refresh();

    /** Whether it is the selected object (the Move, Rotate and Scale tools work on it). */
    boolean selected();

    /** Makes it the selected object. */
    void select();

    /** Deletes it, as one undo step. */
    void remove();
}
