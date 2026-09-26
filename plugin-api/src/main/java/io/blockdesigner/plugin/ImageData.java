package io.blockdesigner.plugin;

import java.util.Objects;

/**
 * Pixels for {@link Drawing#image}: {@code width × height} ARGB ints (not premultiplied), top row first. BlockDesigner
 * uploads each instance to the GPU once and reuses it while the same instance keeps being drawn, so keep one per
 * picture and make a new one when the picture changes; never change {@link #argb()} after drawing it. Since API 3.
 */
public final class ImageData {
    /** Larger images are drawn, but may be scaled down by the graphics driver; 4096 is safe everywhere. */
    public static final int MAX_SIZE = 4096;

    private final int width, height;
    private final int[] argb;

    /** Wraps (does not copy) {@code argb}, which must hold {@code width * height} pixels. */
    public ImageData(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Image size must be positive: " + width + "×" + height);
        Objects.requireNonNull(argb, "argb");
        if (argb.length != width * height) throw new IllegalArgumentException("Expected " + width * height + " pixels, got " + argb.length);
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The pixels themselves (not a copy). */
    public int[] argb() {
        return argb;
    }

    /** Width over height. */
    public double aspect() {
        return width / (double) height;
    }
}
