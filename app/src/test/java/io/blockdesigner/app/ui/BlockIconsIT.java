package io.blockdesigner.app.ui;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders a contact sheet of inventory icons against a real Minecraft install (skipped when none is found). */
class BlockIconsIT {
    @Test
    void slabsAndStairsRenderInThreeD() throws Exception {
        Optional<McInstallLocator.GameJar> jar = new McInstallLocator().scan().jars().stream().findFirst();
        Assumptions.assumeTrue(jar.isPresent(), "no Minecraft install found");
        BlockAssets assets = BlockAssets.open(jar.get().jar(), List.of(), List.of(), m -> {
        });
        List<String> ids = List.of("stone_bricks", "oak_slab", "oak_stairs[facing=east]", "grass_block", "oak_fence", "glass",
                "stone_brick_wall", "oak_leaves", "poppy", "torch", "oak_door", "white_carpet", "chest", "lantern");
        int s = BlockIcons.SIZE;
        BufferedImage sheet = new BufferedImage(s * ids.size(), s, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < ids.size(); i++) {
            BlockState st = assets.registry().complete(BlockState.parse(ids.get(i)));
            Optional<String> flat = BlockIcons.flatItemTexture(assets.assetStack(), st);
            System.out.println(ids.get(i) + " -> " + flat.map(t -> "flat " + t).orElse("3D"));
            if (flat.isPresent()) continue;
            int[] px = BlockIcons.isometricPixels(assets.atlas(), assets.model(st));
            sheet.setRGB(i * s, 0, s, s, px, 0, s);
        }
        Path out = Path.of("build", "icon-sheet.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(sheet, "png", out.toFile());
        System.out.println("wrote " + out.toAbsolutePath());
        assertThat(BlockIcons.flatItemTexture(assets.assetStack(), BlockState.parse("oak_slab"))).isEmpty();
        assertThat(BlockIcons.flatItemTexture(assets.assetStack(), BlockState.parse("poppy"))).isPresent();
    }
}
