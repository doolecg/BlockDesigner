package io.blockdesigner.plugin;

import io.blockdesigner.plugin.ToolEvent.Vec3;

/**
 * What a {@link SceneObject} draws into the 3D view, in the object's own space: BlockDesigner applies the object's
 * {@link Pose} (so the Move, Rotate and Scale tools just work), draws it, and uses the images to find the object under
 * the mouse. Valid only during {@link SceneObject#draw}. Since API 3.
 */
public interface Drawing {

    /** How an image mixes with the blocks. */
    enum Depth {
        /** Behind every block, like a backdrop: blocks always cover it. */
        BEHIND_BLOCKS,
        /** Where it is in the scene: blocks in front of it hide it, it hides blocks behind it. */
        IN_SCENE,
        /** Over every block, like Blender's "In Front". */
        IN_FRONT
    }

    /**
     * A picture on a flat four-cornered patch, seen from both sides.
     *
     * @param image   the pixels (keep drawing the same instance while the picture doesn't change)
     * @param corners four corners in the object's own space, in order around the patch: bottom-left, bottom-right,
     *                top-right, top-left as seen from the front
     * @param uv      the image position at each corner, {@code {u0, v0, u1, v1, u2, v2, u3, v3}}: 0..1 across the
     *                image, v from the top row down; outside 0..1 is transparent (so an offset crops rather than repeats)
     * @param opacity 0 (invisible) to 1
     * @param depth   how it mixes with blocks
     */
    void image(ImageData image, Vec3[] corners, double[] uv, double opacity, Depth depth);

    /** A line in the object's own space, {@code argb} coloured (alpha included), like the outlines BlockDesigner draws. */
    void line(Vec3 from, Vec3 to, int argb);
}
