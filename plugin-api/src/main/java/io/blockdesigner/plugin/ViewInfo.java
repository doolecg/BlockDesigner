package io.blockdesigner.plugin;

import io.blockdesigner.plugin.ToolEvent.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * How the 3D view looks at the scene right now, for {@link SceneObject#draw} (an object can show only in some views)
 * and for placing new objects. Since API 3.
 *
 * @param ortho   whether the view is orthographic
 * @param side    the axis view the camera is at (numpad 1 / 3 / 7 and their opposites, or a view cube face), or empty
 *                for a free view
 * @param eye     where the camera is (for orthographic views, a point far behind the target)
 * @param forward the direction the camera looks, unit length
 * @param target  the point the camera orbits around (a good place for something new)
 */
public record ViewInfo(boolean ortho, Optional<Side> side, Vec3 eye, Vec3 forward, Vec3 target) {

    /** The six axis views. Front looks north (towards -Z), Right looks west, Top looks down with north up. */
    public enum Side {
        FRONT(0, 0, 0), BACK(0, 180, 0), RIGHT(0, 90, 0), LEFT(0, -90, 0), TOP(-90, 0, 0), BOTTOM(90, 0, 0);

        private final Vec3 facing;

        Side(double rx, double ry, double rz) {
            this.facing = new Vec3(rx, ry, rz);
        }

        /**
         * The {@link Pose#rotation() rotation} that turns something drawn in its own XY plane (facing +Z, up +Y) to
         * face a camera on this side, upright on screen: what Blender calls "align to view".
         */
        public Vec3 facing() {
            return facing;
        }
    }

    public ViewInfo {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(target, "target");
    }

    /** Whether this is the orthographic axis view {@code s} (e.g. Front ortho, numpad 1). */
    public boolean isOrthoSide(Side s) {
        return ortho && side.orElse(null) == s;
    }
}
