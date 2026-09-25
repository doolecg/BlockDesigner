package io.blockdesigner.render;

import java.util.List;

/**
 * Everything the render thread needs to draw one frame, captured immutably on the UI thread.
 *
 * @param width   framebuffer width in physical pixels
 * @param height  framebuffer height in physical pixels
 * @param viewProj column-major view-projection matrix
 * @param eye     camera position (x, y, z)
 * @param layers  layers to draw, bottom first
 * @param lines   overlay line segments
 * @param theme   colours for background and grid
 * @param gridY   height of the ground grid
 * @param gridCenter grid centre (x, z)
 * @param fogDistance distance where fog starts; {@link Float#POSITIVE_INFINITY} for none
 */
public record FrameRequest(int width, int height, float[] viewProj, float[] eye, List<LayerDraw> layers, List<Line> lines,
                           Theme theme, boolean showGrid, float gridY, float[] gridCenter, long sequence, float fogDistance) {

    /**
     * @param model    column-major model matrix (offset + rotation/mirror)
     * @param mirrored the transform flips handedness, so front faces wind the other way
     * @param opacity  1 for normal layers, lower for ghosts
     * @param tintRgb  highlight colour mixed in with {@code tintStrength}
     */
    public record LayerDraw(String layerId, float[] model, boolean mirrored, float opacity, int tintRgb, float tintStrength) {
    }

    /** A world-space line segment with an ARGB colour. */
    public record Line(float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
    }

    public record Theme(int zenith, int horizon, int ground, int gridMinor, int gridMajor, int axisX, int axisZ) {
        public static final Theme DARK = new Theme(0x0F1522, 0x2A3348, 0x141925, 0x5A6782, 0x8D9BBA, 0xE5484D, 0x3E9BFF);
        public static final Theme LIGHT = new Theme(0xB9CCEB, 0xF1F4FA, 0xDDE3EE, 0x9AA6BF, 0x6E7C99, 0xE5484D, 0x2F7FE0);
    }
}
