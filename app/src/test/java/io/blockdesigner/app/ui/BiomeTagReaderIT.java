package io.blockdesigner.app.ui;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.worldgen.DatapackExporter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads real biome tags from an installed Minecraft jar (skipped when none is found). */
class BiomeTagReaderIT {
    @Test
    void overworldWithoutWater() throws Exception {
        var jar = new McInstallLocator().scan().jars().stream().findFirst();
        Assumptions.assumeTrue(jar.isPresent(), "no Minecraft install found");
        try (BlockAssets assets = BlockAssets.open(jar.get().jar(), List.of(), List.of(), null)) {
            var tags = BiomeTagReader.of(assets);
            List<String> all = DatapackExporter.resolveBiomes(List.of("#minecraft:is_overworld"), List.of("minecraft:the_void"), tags);
            List<String> dry = DatapackExporter.resolveBiomes(List.of("#minecraft:is_overworld"), DatapackExporter.WATER_BIOMES, tags);
            System.out.println(jar.get().version().id() + ": " + all.size() + " overworld biomes, " + dry.size() + " without water");
            assertThat(all).contains("minecraft:plains", "minecraft:ocean", "minecraft:river");
            assertThat(dry).contains("minecraft:plains", "minecraft:forest")
                    .doesNotContain("minecraft:ocean", "minecraft:deep_ocean", "minecraft:river", "minecraft:beach", "minecraft:swamp");
        }
    }
}
