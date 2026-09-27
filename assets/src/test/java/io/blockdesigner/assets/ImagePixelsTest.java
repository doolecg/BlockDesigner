package io.blockdesigner.assets;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePixelsTest {
    @Test
    void greyscaleKeepsItsValues() throws Exception {
        // A greyscale PNG like Minecraft's stone texture: its grey must not come out brighter.
        BufferedImage gray = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
        gray.getRaster().setSample(0, 0, 0, 125);
        gray.getRaster().setSample(1, 0, 0, 30);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(gray, "png", png);
        BufferedImage img = ImagePixels.read(png.toByteArray());
        assertThat(img.getRGB(0, 0)).isEqualTo(0xFF7D7D7D);
        assertThat(img.getRGB(1, 0)).isEqualTo(0xFF1E1E1E);
    }

    @Test
    void colourImagesAreUnchanged() throws Exception {
        BufferedImage rgb = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        rgb.setRGB(0, 0, 0xFF336699);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(rgb, "png", png);
        assertThat(ImagePixels.read(png.toByteArray()).getRGB(0, 0)).isEqualTo(0xFF336699);
    }
}
