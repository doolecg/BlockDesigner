package io.blockdesigner.app.plugins;

import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.ToolEvent.Vec3;

/**
 * A Move, Rotate or Scale gizmo drag on a scene object: each call sets the pose from where the drag started (so the
 * mouse can go back and forth without drift), live and without undo; {@link #commit()} records the whole drag as one
 * undo step, {@link #cancel()} puts the object back. Unlike layers, objects move freely rather than by whole blocks.
 */
public final class ObjectDrag {
    private final SceneObjectStore.Entry entry;
    private final Pose start;
    private String label;

    public ObjectDrag(SceneObjectStore.Entry entry) {
        this.entry = entry;
        this.start = entry.pose();
        this.label = "Move " + entry.name();
    }

    public SceneObjectStore.Entry entry() {
        return entry;
    }

    /** The pose when the drag began. */
    public Pose start() {
        return start;
    }

    /** Where the object turns and scales about: its origin when the drag began. */
    public Vec3 pivot() {
        return start.position();
    }

    /** Moved {@code dx, dy, dz} blocks from the start (to 1/100 block, so fields don't show float noise). */
    public void move(double dx, double dy, double dz) {
        label = "Move " + entry.name();
        entry.setPoseLive(start.translated(round(dx), round(dy), round(dz)));
    }

    /** Turned {@code degrees} about world axis {@code axis} (0 X, 1 Y, 2 Z) through the pivot, from the start. */
    public void rotate(int axis, double degrees) {
        label = "Rotate " + entry.name();
        entry.setPoseLive(start.rotatedAbout(axis, degrees, pivot()));
    }

    /**
     * Scaled by {@code fx, fy, fz} along the object's own axes from the start (1 = unchanged); for the Scale tool.
     * Factors are kept away from 0 so the object can't collapse and vanish.
     */
    public void scale(double fx, double fy, double fz) {
        label = "Scale " + entry.name();
        entry.setPoseLive(start.scaledBy(safe(fx), safe(fy), safe(fz)));
    }

    /** Ends the drag as one undo step (nothing when the pose didn't change). */
    public void commit() {
        entry.commitPose(start, label);
    }

    /** Ends the drag with the object back where it was. */
    public void cancel() {
        entry.setPoseLive(start);
    }

    private static double round(double v) {
        return Math.rint(v * 100) / 100;
    }

    private static double safe(double f) {
        return Math.abs(f) < 1e-3 ? Math.copySign(1e-3, f == 0 ? 1 : f) : f;
    }
}
