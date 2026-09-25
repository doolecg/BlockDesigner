package io.blockdesigner.assets;

import io.blockdesigner.assets.model.BakedModel;
import io.blockdesigner.assets.model.BakedQuad;
import io.blockdesigner.assets.model.RenderLayer;
import io.blockdesigner.core.model.BlockState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against real Minecraft installs on this machine; skipped when none are found. */
class BlockAssetsIT {
    static BlockAssets assets;
    static McInstallLocator.Result scan;

    @BeforeAll
    static void load() throws Exception {
        scan = new McInstallLocator().scan();
        Optional<McInstallLocator.GameJar> jar = scan.jars().stream().findFirst();
        Assumptions.assumeTrue(jar.isPresent(), "no Minecraft install found");
        long t0 = System.nanoTime();
        assets = BlockAssets.open(jar.get().jar(), List.of(), List.of(), System.out::println);
        System.out.printf("Loaded %s in %d ms%n", jar.get(), (System.nanoTime() - t0) / 1_000_000);
    }

    @Test
    void locatorFindsInstalls() {
        scan.jars().forEach(j -> System.out.println("jar: " + j + " -> " + j.jar()));
        scan.instances().forEach(i -> System.out.println("instance: " + i));
        assertThat(scan.jars()).isNotEmpty();
    }

    @Test
    void registryHasVanillaBlocks() {
        assertThat(assets.registry().size()).isGreaterThan(900);
        var stairs = assets.registry().get("oak_stairs").orElseThrow();
        assertThat(stairs.properties()).containsKeys("facing", "half", "shape");
        assertThat(assets.registry().search("stone brick", 5)).extracting(BlockRegistry.BlockInfo::id).contains("minecraft:stone_bricks");
    }

    @Test
    void fullCubesAreOpaqueAndStairsAreNot() {
        BakedModel stone = assets.model(BlockState.of("stone"));
        assertThat(stone.quads()).hasSize(6);
        assertThat(stone.isFullCube()).isTrue();
        BakedModel stairs = assets.model(assets.registry().get("oak_stairs").orElseThrow().defaultState());
        assertThat(stairs.isFullCube()).isFalse();
        assertThat(stairs.isOpaque(Dir.DOWN)).isTrue();
        BakedModel glass = assets.model(BlockState.of("glass"));
        assertThat(glass.quads()).allMatch(q -> q.layer() == RenderLayer.CUTOUT);
        assertThat(assets.model(BlockState.of("white_stained_glass")).quads()).allMatch(q -> q.layer() == RenderLayer.TRANSLUCENT);
    }

    @Test
    void grassTopIsTinted() {
        BakedModel grass = assets.model(BlockState.parse("grass_block[snowy=false]"));
        BakedQuad top = grass.quads().stream().filter(q -> q.face() == Dir.UP).findFirst().orElseThrow();
        assertThat(top.tint()).isNotEqualTo(0xFFFFFF);
    }

    @Test
    void variantRotationTurnsStairs() {
        BlockState north = BlockState.parse("oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        BlockState east = north.with("facing", "east");
        // Stairs ascend toward their facing, so the full-height back is on the facing side.
        assertThat(assets.model(north).isOpaque(Dir.NORTH)).isTrue();
        assertThat(assets.model(north).isOpaque(Dir.SOUTH)).isFalse();
        assertThat(assets.model(east).isOpaque(Dir.EAST)).isTrue();
        assertThat(assets.model(east).isOpaque(Dir.WEST)).isFalse();
    }

    @Test
    void everyDefaultStateBakes() throws Exception {
        List<String> missing = new ArrayList<>();
        for (var b : assets.registry().all()) {
            BakedModel m = assets.model(b.defaultState());
            if (m.missing()) missing.add(b.id());
        }
        System.out.println("Missing models (" + missing.size() + "): " + missing);
        assertThat(missing.size()).isLessThan(assets.registry().size() / 50);

        Path out = Path.of(System.getProperty("java.io.tmpdir"), "blockdesigner-atlas.png");
        TextureAtlas a = assets.atlas();
        BufferedImage img = new BufferedImage(a.width(), a.height(), BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, a.width(), a.height(), a.pixels(), 0, a.width());
        ImageIO.write(img, "png", out.toFile());
        System.out.println("Atlas written to " + out + " (" + Files.size(out) / 1024 + " KiB)");
    }
}
