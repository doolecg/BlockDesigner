package io.blockdesigner.assets;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Reads PNGs the way Minecraft does. Java treats greyscale PNGs (stone, andesite, many mod textures) as linear light,
 * so {@link BufferedImage#getRGB} brightens them on the way to sRGB: stone's grey 125 came out near 185. Greyscale
 * images are copied sample by sample instead, with no colour conversion.
 */
public final class ImagePixels {
    private ImagePixels() {
    }

    /** The image, as ARGB with the file's own values; null when it can't be decoded. */
    public static BufferedImage read(byte[] png) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        return img == null ? null : fixGray(img);
    }

    /** A greyscale image copied into ARGB without the linear-to-sRGB brightening; any other image as it is. */
    public static BufferedImage fixGray(BufferedImage img) {
        if (img.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) return img;
        Raster r = img.getRaster();
        int w = img.getWidth(), h = img.getHeight(), bands = r.getNumBands();
        int bits = img.getColorModel().getComponentSize(0);
        int max = (1 << bits) - 1;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int g = r.getSample(x, y, 0) * 255 / max;
                int a = bands > 1 ? r.getSample(x, y, 1) * 255 / ((1 << img.getColorModel().getComponentSize(1)) - 1) : 255;
                px[y * w + x] = a << 24 | g << 16 | g << 8 | g;
            }
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }
}
