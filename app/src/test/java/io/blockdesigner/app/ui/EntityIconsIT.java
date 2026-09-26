package io.blockdesigner.app.ui;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.EntityTypes;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Draws every mob's palette icon (its model, fitted to the tile) into build/mob-icons.png, against a real Minecraft install. */
class EntityIconsIT {
    @Test
    void mobsGetModelIcons() throws Exception {
        Optional<McInstallLocator.GameJar> jar = new McInstallLocator().scan().jars().stream()
                .filter(j -> j.version().id().startsWith("26.")).findFirst();
        Assumptions.assumeTrue(jar.isPresent(), "no Minecraft 26.x install found");
        BlockAssets assets = BlockAssets.open(jar.get().jar(), List.of(), List.of(), m -> {
        });
        List<EntityTypes.Kind> kinds = EntityTypes.all();
        int s = BlockIcons.SIZE, cols = 12, rows = (kinds.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(s * cols, s * rows, BufferedImage.TYPE_INT_ARGB);
        int drawn = 0;
        for (int i = 0; i < kinds.size(); i++) {
            int[] px = EntityIcons.modelPixels(assets, kinds.get(i).id());
            if (px == null) continue;
            drawn++;
            sheet.setRGB((i % cols) * s, (i / cols) * s, s, s, px, 0, s);
        }
        Path out = Path.of("build", "mob-icons.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(sheet, "png", out.toFile());
        System.out.println("wrote " + out.toAbsolutePath());
        // Paintings and item frames are flat boxes: every other type has a model to show.
        assertThat(drawn).isGreaterThanOrEqualTo(kinds.size() - 3);
    }
}
