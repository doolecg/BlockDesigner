package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Everything a reference image is besides its pose (the scene object store keeps that): which picture, its UV offset and scale,
 * opacity, the views it shows in, how it mixes with blocks and flips. Immutable; no UI, so it is tested on its own.
 *
 * <p>The plane is drawn in its own XY plane, facing +Z: one block tall and {@link #aspect()} wide, centred on the
 * origin, so the picture keeps its proportions and the pose's scale is its size in blocks.
 *
 * @param blob      key of the picture's file in the project ({@code SceneObjectStore.storeBlob}); empty for none
 * @param fileName  the picture's file name, for the Layers panel
 * @param width     the picture's size in pixels (its aspect ratio sets the plane's)
 * @param uOffset   where the picture starts across the plane, in picture widths (0 = its left edge at the plane's)
 * @param uScale    how much of the picture's width the plane shows (1 = all of it)
 * @param opacity   0 to 1
 * @param showIn    the views it shows in
 * @param depth     how blocks and the picture cover each other
 */
record ReferenceSettings(String blob, String fileName, int width, int height, double uOffset, double vOffset, double uScale,
                         double vScale, double opacity, ShowIn showIn, Drawing.Depth depth, boolean flipX, boolean flipY) {

    /** The size new reference images get: this many blocks tall. */
    static final double DEFAULT_SIZE = 16;
    static final ReferenceSettings DEFAULT = new ReferenceSettings("", "", 1, 1, 0, 0, 1, 1, 1, ShowIn.ALL, Drawing.Depth.IN_SCENE, false, false);
    private static final int FORMAT = 1;

    /** Which views the image shows in, like Blender's "align to view" references. */
    enum ShowIn {
        ALL("All views"), ORTHO("Orthographic views"), FRONT("Front only"), BACK("Back only"), LEFT("Left only"),
        RIGHT("Right only"), TOP("Top only"), BOTTOM("Bottom only");

        final String label;

        ShowIn(String label) {
            this.label = label;
        }

        /** The axis view it is limited to, if it is one of the six. */
        Optional<ViewInfo.Side> side() {
            return ordinal() >= FRONT.ordinal() ? Optional.of(ViewInfo.Side.valueOf(name())) : Optional.empty();
        }

        static ShowIn of(ViewInfo.Side side) {
            return valueOf(side.name());
        }
    }

    ReferenceSettings {
        blob = blob == null ? "" : blob;
        fileName = fileName == null ? "" : fileName;
        width = Math.max(1, width);
        height = Math.max(1, height);
        opacity = Math.clamp(opacity, 0, 1);
        showIn = showIn == null ? ShowIn.ALL : showIn;
        depth = depth == null ? Drawing.Depth.IN_SCENE : depth;
        // A zero UV scale would squash the picture to a line.
        if (Math.abs(uScale) < 1e-3) uScale = 1e-3;
        if (Math.abs(vScale) < 1e-3) vScale = 1e-3;
    }

    // ---- changes ---------------------------------------------------------------------------------------------

    ReferenceSettings withImage(String blob, String fileName, int width, int height) {
        return new ReferenceSettings(blob, fileName, width, height, uOffset, vOffset, uScale, vScale, opacity, showIn, depth, flipX, flipY);
    }

    ReferenceSettings withUv(double uOffset, double vOffset, double uScale, double vScale) {
        return new ReferenceSettings(blob, fileName, width, height, uOffset, vOffset, uScale, vScale, opacity, showIn, depth, flipX, flipY);
    }

    ReferenceSettings withOpacity(double opacity) {
        return new ReferenceSettings(blob, fileName, width, height, uOffset, vOffset, uScale, vScale, opacity, showIn, depth, flipX, flipY);
    }

    ReferenceSettings withShowIn(ShowIn showIn) {
        return new ReferenceSettings(blob, fileName, width, height, uOffset, vOffset, uScale, vScale, opacity, showIn, depth, flipX, flipY);
    }

    ReferenceSettings withDepth(Drawing.Depth depth) {
        return new ReferenceSettings(blob, fileName, width, height, uOffset, vOffset, uScale, vScale, opacity, showIn, depth, flipX, flipY);
    }

    ReferenceSettings withFlip(boolean flipX, boolean flipY) {
        return new ReferenceSettings(blob, fileName, width, height, uOffset, vOffset, uScale, vScale, opacity, showIn, depth, flipX, flipY);
    }

    // ---- geometry --------------------------------------------------------------------------------------------

    /** The picture's width over its height: the plane's too. */
    double aspect() {
        return width / (double) height;
    }

    /** The plane's corners in its own space: bottom-left, bottom-right, top-right, top-left, one block tall. */
    Vec3[] corners() {
        double hw = aspect() / 2;
        return new Vec3[]{new Vec3(-hw, -0.5, 0), new Vec3(hw, -0.5, 0), new Vec3(hw, 0.5, 0), new Vec3(-hw, 0.5, 0)};
    }

    /** The picture's u v at each corner (v from the top down), with the UV offset, scale and flips applied. */
    double[] uv() {
        double left = uOffset, right = uOffset + uScale, top = vOffset, bottom = vOffset + vScale;
        if (flipX) {
            double t = left;
            left = right;
            right = t;
        }
        if (flipY) {
            double t = top;
            top = bottom;
            bottom = t;
        }
        return new double[]{left, bottom, right, bottom, right, top, left, top};
    }

    /** Whether it shows in this view. */
    boolean visibleIn(ViewInfo view) {
        return switch (showIn) {
            case ALL -> true;
            case ORTHO -> view.ortho();
            default -> view.isOrthoSide(showIn.side().orElseThrow());
        };
    }

    /**
     * Where a new reference image goes: at the view's target, facing the camera — straight on in an axis view (and then
     * shown only in that view, like Blender's "align to view"), otherwise turned about Y towards the camera.
     */
    static Pose placement(ViewInfo view) {
        Vec3 rotation = view.side().map(ViewInfo.Side::facing).orElseGet(() -> facingYaw(view.forward()));
        return new Pose(view.target(), rotation, new Vec3(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE));
    }

    /** The views a new image shows in: only the axis view it was added in, when that is orthographic; else all. */
    static ShowIn showInFor(ViewInfo view) {
        return view.ortho() && view.side().isPresent() ? ShowIn.of(view.side().get()) : ShowIn.ALL;
    }

    /** A turn about Y that points the plane's front (+Z) back along a look direction. */
    static Vec3 facingYaw(Vec3 forward) {
        if (Math.abs(forward.x()) < 1e-9 && Math.abs(forward.z()) < 1e-9) return new Vec3(0, 0, 0);
        double yaw = Math.toDegrees(Math.atan2(-forward.x(), -forward.z()));
        return new Vec3(0, Math.rint(yaw * 1e6) / 1e6, 0);
    }

    /** The scale that gives the plane the picture's own proportions again: X made equal to Y. */
    static Vec3 resetAspect(Vec3 scale) {
        return new Vec3(scale.y(), scale.y(), scale.z());
    }

    // ---- saving ----------------------------------------------------------------------------------------------

    byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeUTF(blob);
            out.writeUTF(fileName);
            out.writeInt(width);
            out.writeInt(height);
            out.writeDouble(uOffset);
            out.writeDouble(vOffset);
            out.writeDouble(uScale);
            out.writeDouble(vScale);
            out.writeDouble(opacity);
            out.writeUTF(showIn.name());
            out.writeUTF(depth.name());
            out.writeBoolean(flipX);
            out.writeBoolean(flipY);
        } catch (IOException e) {
            throw new IllegalStateException(e); // a byte array doesn't fail
        }
        return bytes.toByteArray();
    }

    /** Reads {@link #encode()}'s bytes; empty data gives the defaults. Unknown view or depth names fall back too. */
    static ReferenceSettings decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) return DEFAULT;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int format = in.readInt();
            if (format > FORMAT) throw new IOException("This reference image was saved by a newer BlockDesigner");
            return new ReferenceSettings(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble(),
                    in.readDouble(), in.readDouble(), in.readDouble(), parse(ShowIn.class, in.readUTF(), ShowIn.ALL),
                    parse(Drawing.Depth.class, in.readUTF(), Drawing.Depth.IN_SCENE), in.readBoolean(), in.readBoolean());
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
