package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Biome blacklists (tags expanded, then exclusions taken away) and keeping blocks dry. */
class BiomeFilterTest {
    static final ObjectMapper JSON = new ObjectMapper();

    /** A tiny stand-in for the game's biome tags, with a nested tag and a modded biome. */
    static final Map<String, List<String>> TAGS = Map.of(
            "minecraft:is_overworld", List.of("minecraft:plains", "minecraft:forest", "#minecraft:is_ocean", "#minecraft:is_river",
                    "minecraft:swamp", "terralith:alpine_grove"),
            "minecraft:is_ocean", List.of("minecraft:ocean", "#minecraft:is_deep_ocean"),
            "minecraft:is_deep_ocean", List.of("minecraft:deep_ocean"),
            "minecraft:is_river", List.of("minecraft:river", "minecraft:frozen_river"),
            "minecraft:is_beach", List.of("minecraft:beach"));
    static final DatapackExporter.BiomeTags READER = id -> Optional.ofNullable(TAGS.get(id));

    @Test
    void noExclusionsKeepsEntriesAsTheyAre() {
        assertThat(DatapackExporter.resolveBiomes(List.of("#minecraft:is_overworld"), List.of(), null)).containsExactly("#minecraft:is_overworld");
    }

    @Test
    void exclusionsExpandTagsAndSubtract() {
        List<String> out = DatapackExporter.resolveBiomes(List.of("#minecraft:is_overworld"), DatapackExporter.WATER_BIOMES, READER);
        assertThat(out).containsExactly("minecraft:plains", "minecraft:forest", "terralith:alpine_grove");
        assertThat(DatapackExporter.resolveBiomes(List.of("#is_overworld"), List.of("forest", "#is_ocean"), READER))
                .containsExactly("minecraft:plains", "minecraft:river", "minecraft:frozen_river", "minecraft:swamp", "terralith:alpine_grove");
    }

    @Test
    void unknownTagsAndEmptyResultsAreReported() {
        assertThatThrownBy(() -> DatapackExporter.resolveBiomes(List.of("#minecraft:is_nether"), List.of("minecraft:plains"), READER))
                .hasMessageContaining("is_nether");
        assertThatThrownBy(() -> DatapackExporter.resolveBiomes(List.of("minecraft:ocean"), List.of("#minecraft:is_ocean"), READER))
                .hasMessageContaining("Every chosen biome");
        assertThatThrownBy(() -> DatapackExporter.resolveBiomes(List.of("#minecraft:is_overworld"), List.of("minecraft:plains"), null))
                .hasMessageContaining("Load a Minecraft version");
    }

    @Test
    void packWritesTheFilteredListAndDrySetting() throws Exception {
        Structure s = new Structure();
        for (int x = 0; x < 3; x++) s.set(x, 0, 0, BlockState.of("stone"));
        var a = DatapackExporter.Advanced.defaults().withBiomes(DatapackExporter.WATER_BIOMES, true, READER);
        var o = DatapackExporter.Options.defaults("p", "hut", McVersion.byId("1.21.1").orElseThrow()).withAdvanced(a);
        var files = DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", s, 1)));
        JsonNode values = JSON.readTree(files.get("data/p/tags/worldgen/biome/has_structure/hut.json")).path("values");
        assertThat(values.get(0).asText()).isEqualTo("minecraft:plains");
        assertThat(values.get(2).path("id").asText()).isEqualTo("terralith:alpine_grove");
        assertThat(values.get(2).path("required").asBoolean()).isFalse();
        assertThat(values).hasSize(3);
        JsonNode st = JSON.readTree(files.get("data/p/worldgen/structure/hut.json"));
        assertThat(st.path("liquid_settings").asText()).isEqualTo("ignore_waterlogging");

        // Versions before 1.21 don't know liquid_settings, so it is left out there.
        var old = DatapackExporter.Options.defaults("p", "hut", McVersion.byId("1.20.4").orElseThrow()).withAdvanced(a);
        var oldFiles = DatapackExporter.build(old, List.of(new DatapackExporter.Piece("main", s, 1)));
        assertThat(JSON.readTree(oldFiles.get("data/p/worldgen/structure/hut.json")).has("liquid_settings")).isFalse();
    }

    @Test
    void validationSurfacesBiomeProblems() {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.of("stone"));
        var a = DatapackExporter.Advanced.defaults().withBiomes(List.of("#minecraft:is_ocean"), false, READER);
        var o = new DatapackExporter.Options("p", "hut", "", McVersion.latestKnown(), List.of("minecraft:ocean"),
                DatapackExporter.Step.SURFACE_STRUCTURES, DatapackExporter.TerrainAdaptation.NONE, true, 0, 34, 8, 1, 1, false, a);
        assertThat(DatapackExporter.validate(o, List.of(new DatapackExporter.Piece("main", s, 1)))).anyMatch(e -> e.contains("Every chosen biome"));
    }
}
