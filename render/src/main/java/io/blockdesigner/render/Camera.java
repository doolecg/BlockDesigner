package io.blockdesigner.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * An orbit camera around a target point. Yaw 0 looks north (-Z); pitch is positive when looking down. It can be
 * orthographic (not while flying): the visible height then matches what perspective shows at the target, so switching
 * keeps the framing, and zooming changes that height.
 */
public final class Camera {
    /** How far behind the target an orthographic view is rendered from, so nothing in front of it is clipped. */
    private static final float ORTHO_BACK = 1000;
    private final Vector3f target = new Vector3f();
    private float yaw = (float) Math.toRadians(-135 + 180);
    private float pitch = (float) Math.toRadians(30);
    private float distance = 30;
    private float fovDeg = 50;
    private float clipEnd = 4000;
    /** In fly mode {@link #target} is the eye position and the camera looks along {@link #forward()}. */
    private boolean fly;
    private boolean ortho;

    public Vector3f target() {
        return new Vector3f(target);
    }

    public void setTarget(float x, float y, float z) {
        target.set(x, y, z);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float distance() {
        return distance;
    }

    public void setDistance(float d) {
        if (fly) return;
        distance = Math.clamp(d, 1.5f, 2000f);
    }

    /** Vertical field of view in degrees. */
    public float fov() {
        return fovDeg;
    }

    public void setFov(float degrees) {
        fovDeg = Math.clamp(degrees, 10f, 140f);
    }

    /** Far clipping distance in blocks (always kept past the orbit target). */
    public void setClipEnd(float blocks) {
        clipEnd = Math.max(16f, blocks);
    }

    /** Whether orthographic is chosen (it only applies while orbiting, not flying). */
    public boolean isOrtho() {
        return ortho;
    }

    public void setOrtho(boolean ortho) {
        this.ortho = ortho;
    }

    /** Orthographic right now. */
    public boolean orthoActive() {
        return ortho && !fly;
    }

    /** World units per screen pixel at {@code p}, for a viewport {@code heightPx} tall. */
    public float unitsPerPixel(Vector3f p, float heightPx) {
        float depth = orthoActive() ? distance : Math.max(0.05f, new Vector3f(p).sub(eye()).dot(forward()));
        return (float) (2 * depth * Math.tan(Math.toRadians(fovDeg / 2)) / heightPx);
    }

    public boolean isFly() {
        return fly;
    }

    /** Switches between orbiting a target and free (creative-style) flight, keeping the current view. */
    public void setFly(boolean enable) {
        if (enable == fly) return;
        if (enable) {
            target.set(eye());
        } else {
            target.add(forward().mul(10));
            distance = 10;
        }
        fly = enable;
    }

    /** Moves the camera (fly mode) or its target (orbit mode) by a world-space delta. */
    public void translate(float dx, float dy, float dz) {
        target.add(dx, dy, dz);
    }

    /** Horizontal forward direction (yaw only), for walking-style movement. */
    public Vector3f flatForward() {
        return new Vector3f((float) Math.sin(yaw), 0, -(float) Math.cos(yaw));
    }

    public void setAngles(float yawRad, float pitchRad) {
        yaw = yawRad;
        pitch = Math.clamp(pitchRad, (float) (-Math.PI / 2), (float) (Math.PI / 2));
    }

    public void orbit(float dYaw, float dPitch) {
        setAngles(yaw + dYaw, pitch + dPitch);
    }

    /**
     * Orbits around {@code pivot} instead of the target: the eye swings round the pivot with the view, so the point
     * under the cursor stays put on screen (Blender's "orbit around mouse").
     */
    public void orbitAround(Vector3f pivot, float dYaw, float dPitch) {
        Vector3f eye = eye(), v = new Vector3f(eye).sub(pivot);
        Vector3f r0 = right(), u0 = up(), f0 = forward();
        float cr = v.dot(r0), cu = v.dot(u0), cf = v.dot(f0);
        setAngles(yaw + dYaw, pitch + dPitch);
        Vector3f newEye = new Vector3f(pivot)
                .add(right().mul(cr)).add(up().mul(cu)).add(forward().mul(cf));
        // eye = target - forward * distance (orbit) or eye = target (fly)
        target.set(fly ? newEye : newEye.add(forward().mul(distance)));
    }

    public void zoom(float factor) {
        setDistance(distance * factor);
    }

    /** Pans in the view plane by screen-space fractions of the view height. */
    public void pan(float dx, float dy) {
        float scale = (fly ? 10 : distance) * (float) Math.tan(Math.toRadians(fovDeg / 2)) * 2;
        Vector3f right = right(), up = up();
        target.add(right.mul(-dx * scale)).add(up.mul(dy * scale));
    }

    /** Unit vector from the eye toward the target. */
    public Vector3f forward() {
        float cp = (float) Math.cos(pitch);
        return new Vector3f((float) Math.sin(yaw) * cp, -(float) Math.sin(pitch), -(float) Math.cos(yaw) * cp).normalize();
    }

    /** Screen right; from the yaw alone, so it stays defined looking straight up or down. */
    public Vector3f right() {
        return new Vector3f((float) Math.cos(yaw), 0, (float) Math.sin(yaw));
    }

    public Vector3f up() {
        return right().cross(forward(), new Vector3f()).normalize();
    }

    public Vector3f eye() {
        if (fly) return new Vector3f(target);
        return new Vector3f(target).sub(forward().mul(distance));
    }

    /** Where the view is rendered from: the eye, or for orthographic a point far behind the target. */
    public Vector3f viewEye() {
        return orthoActive() ? new Vector3f(target).sub(forward().mul(ORTHO_BACK)) : eye();
    }

    public Matrix4f view() {
        Vector3f eye = viewEye();
        return new Matrix4f().lookAt(eye, new Vector3f(eye).add(forward()), up());
    }

    public Matrix4f projection(float aspect) {
        if (orthoActive()) {
            float h = distance * (float) Math.tan(Math.toRadians(fovDeg / 2));
            return new Matrix4f().ortho(-h * aspect, h * aspect, -h, h, 1f, ORTHO_BACK + clipEnd);
        }
        float near = fly ? 0.05f : Math.max(0.05f, distance / 500f);
        return new Matrix4f().perspective((float) Math.toRadians(fovDeg), aspect, near, Math.max(clipEnd, fly ? 0 : distance * 2));
    }

    public Matrix4f viewProjection(float aspect) {
        return projection(aspect).mul(view());
    }

    /** Frames a box so it fills most of the view. */
    public void frame(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        fly = false;
        target.set((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        float radius = new Vector3f(maxX - minX, maxY - minY, maxZ - minZ).length() / 2;
        setDistance(Math.max(4, radius / (float) Math.sin(Math.toRadians(fovDeg / 2)) * 1.1f));
    }

    /**
     * World-space ray through a pixel. {@code sx, sy} are in 0..1 with the origin at the top-left.
     *
     * @return {origin, direction}
     */
    public Vector3f[] ray(float sx, float sy, float aspect) {
        Matrix4f inv = viewProjection(aspect).invert();
        float nx = sx * 2 - 1, ny = 1 - sy * 2;
        Vector4f near = new Vector4f(nx, ny, -1, 1).mul(inv);
        Vector4f far = new Vector4f(nx, ny, 1, 1).mul(inv);
        near.div(near.w);
        far.div(far.w);
        Vector3f o = new Vector3f(near.x, near.y, near.z);
        Vector3f d = new Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
        return new Vector3f[]{o, d};
    }

    /**
     * The horizontal world axis closest to the camera's screen-right direction, as a unit step {dx, dz}.
     * Used so "left/right" nudges always move left/right on screen.
     */
    public int[] screenRightAxis() {
        Vector3f r = right();
        if (Math.abs(r.x) >= Math.abs(r.z)) return new int[]{(int) Math.signum(r.x), 0};
        return new int[]{0, (int) Math.signum(r.z)};
    }

    /** The horizontal world axis closest to "forward" (away from the viewer), as {dx, dz}. */
    public int[] screenForwardAxis() {
        Vector3f f = flatForward();
        if (Math.abs(f.x) > Math.abs(f.z)) return new int[]{(int) Math.signum(f.x), 0};
        return new int[]{0, f.z == 0 ? -1 : (int) Math.signum(f.z)};
    }

    /** Copies every setting (position, angles, distance, fly mode) from {@code o}. */
    public void set(Camera o) {
        target.set(o.target);
        yaw = o.yaw;
        pitch = o.pitch;
        distance = o.distance;
        fovDeg = o.fovDeg;
        clipEnd = o.clipEnd;
        fly = o.fly;
        ortho = o.ortho;
    }

    public Camera copy() {
        Camera c = new Camera();
        c.target.set(target);
        c.yaw = yaw;
        c.pitch = pitch;
        c.distance = distance;
        c.fovDeg = fovDeg;
        c.clipEnd = clipEnd;
        c.fly = fly;
        c.ortho = ortho;
        return c;
    }
}
