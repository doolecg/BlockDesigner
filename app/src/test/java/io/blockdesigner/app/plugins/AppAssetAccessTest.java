package io.blockdesigner.app.plugins;

import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AppAssetAccessTest {

    @Test
    void pngEncoderWritesWhatImageReadersRead() throws IOException {
        int[] px = {0xFFFF0000, 0x8000FF00, 0x000000FF, 0xFFFFFFFF, 0xFF123456, 0x00000000};
        byte[] png = AppAssetAccess.png(3, 2, px);
        var img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(3);
        assertThat(img.getHeight()).isEqualTo(2);
        for (int i = 0; i < px.length; i++) {
            int got = img.getRGB(i % 3, i / 3);
            // Fully transparent pixels may come back with any colour.
            if ((px[i] >>> 24) == 0) assertThat(got >>> 24).isZero();
            else assertThat(got).isEqualTo(px[i]);
        }
    }

    @Test
    void withoutAssetsEverythingIsEmpty() {
        AppAssetAccess a = new AppAssetAccess(() -> null);
        assertThat(a.available()).isFalse();
        assertThat(a.quads(BlockState.of("stone"), d -> false)).isEmpty();
        assertThat(a.atlas()).isEmpty();
        assertThat(a.texture("minecraft:block/stone")).isEmpty();
    }
}
