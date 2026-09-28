package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.ImageData;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Reads picture files (PNG, JPEG, GIF, BMP) into {@link ImageData}, at most {@link ImageData#MAX_SIZE} a side. */
final class Images {
    private Images() {
    }

    /** A picture and the size of the file's own pixels (before any shrinking). */
    record Decoded(ImageData image, int width, int height) {
    }

    static Decoded decode(byte[] file) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(file));
        if (src == null) throw new IOException("Not a picture BlockDesigner can read (use PNG, JPEG, GIF or BMP)");
        int w = src.getWidth(), h = src.getHeight();
        double shrink = Math.min(1, ImageData.MAX_SIZE / (double) Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * shrink)), th = Math.max(1, (int) Math.round(h * shrink));
        BufferedImage argb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return new Decoded(new ImageData(tw, th, argb.getRGB(0, 0, tw, th, null, 0, tw)), w, h);
    }
}
