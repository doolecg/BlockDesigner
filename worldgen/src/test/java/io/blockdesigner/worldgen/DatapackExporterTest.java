package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.NbtIO;
import io.blockdesigner.core.version.McVersion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatapackExporterTest {
    static final ObjectMapper JSON = new ObjectMapper();

    static Structure hut() {
        Structure s = new Structure();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) s.set(x, 0, z, BlockState.of("cobblestone"));
        s.set(2, 1, 2, BlockState.of("crafting_table"));
        return s;
    }

    @Test
    void writesAllFilesForModernVersion() throws Exception {
        McVersion v = McVersion.byId("1.21.1").orElseThrow();
        var o = DatapackExporter.Options.defaults("mypack", "hut", v);
        Map<String, byte[]> files = DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", hut(), 1)));
        assertThat(files).containsKeys(
                "pack.mcmeta",
                "data/mypack/structure/hut/main.nbt",
                "data/mypack/worldgen/template_pool/hut/start.json",
                "data/mypack/worldgen/structure/hut.json",
                "data/mypack/worldgen/structure_set/hut.json",
                "data/mypack/tags/worldgen/biome/has_structure/hut.json");
        assertThat(JSON.readTree(files.get("pack.mcmeta")).path("pack").path("pack_format").asInt()).isEqualTo(48);
        JsonNode st = JSON.readTree(files.get("data/mypack/worldgen/structure/hut.json"));
        assertThat(st.path("type").asText()).isEqualTo("minecraft:jigsaw");
        assertThat(st.path("start_pool").asText()).isEqualTo("mypack:hut/start");
        assertThat(st.path("biomes").asText()).isEqualTo("#mypack:has_structure/hut");
        JsonNode set = JSON.readTree(files.get("data/mypack/worldgen/structure_set/hut.json"));
        assertThat(set.path("placement").path("spacing").asInt()).isGreaterThan(set.path("placement").path("separation").asInt());
        JsonNode pool = JSON.readTree(files.get("data/mypack/worldgen/template_pool/hut/start.json"));
        assertThat(pool.path("elements").get(0).path("element").path("location").asText()).isEqualTo("mypack:hut/main");

        // Template is a valid structure file without explicit air (terrain shows through).
        var nbt = NbtIO.read(new ByteArrayInputStream(files.get("data/mypack/structure/hut/main.nbt"))).tag();
        assertThat(nbt.getInt("DataVersion")).isEqualTo(3955);
        assertThat(Schematics.VANILLA.read(nbt).totalBlocks()).isEqualTo(26);
        assertThat(nbt.getList("blocks").size()).isEqualTo(26);
    }

    @Test
    void legacyAndNewestLayouts() throws Exception {
        var old = DatapackExporter.build(DatapackExporter.Options.defaults("p", "hut", McVersion.byId("1.20.1").orElseThrow()),
                List.of(new DatapackExporter.Piece("main", hut(), 1)));
        assertThat(old).containsKey("data/p/structures/hut/main.nbt");
        assertThat(JSON.readTree(old.get("pack.mcmeta")).path("pack").path("pack_format").asInt()).isEqualTo(15);

        var neu = DatapackExporter.build(DatapackExporter.Options.defaults("p", "hut", McVersion.byId("26.2").orElseThrow()),
                List.of(new DatapackExporter.Piece("main", hut(), 1)));
        JsonNode pack = JSON.readTree(neu.get("pack.mcmeta")).path("pack");
        assertThat(pack.has("pack_format")).isFalse();
        assertThat(pack.path("min_format").get(0).asInt()).isEqualTo(107);
        assertThat(pack.path("min_format").get(1).asInt()).isEqualTo(1);
    }

    @Test
    void variantsBecomeWeightedPoolElements() throws Exception {
        var files = DatapackExporter.build(DatapackExporter.Options.defaults("p", "huts", McVersion.latestKnown()),
                List.of(new DatapackExporter.Piece("a", hut(), 3), new DatapackExporter.Piece("b", hut(), 1)));
        JsonNode pool = JSON.readTree(files.get("data/p/worldgen/template_pool/huts/start.json"));
        assertThat(pool.path("elements")).hasSize(2);
        assertThat(pool.path("elements").get(0).path("weight").asInt()).isEqualTo(3);
    }

    @Test
    void validationCatchesMistakes() {
        var o = new DatapackExporter.Options("My Pack", "hut", "", McVersion.latestKnown(), List.of(), DatapackExporter.Step.SURFACE_STRUCTURES,
                DatapackExporter.TerrainAdaptation.NONE, true, 0, 8, 8, 1, 1, false);
        List<String> errors = DatapackExporter.validate(o, List.of(new DatapackExporter.Piece("main", new Structure(), 1)));
        assertThat(errors).anyMatch(e -> e.contains("Namespace")).anyMatch(e -> e.contains("Separation"))
                .anyMatch(e -> e.contains("biome")).anyMatch(e -> e.contains("empty"));
    }
}
