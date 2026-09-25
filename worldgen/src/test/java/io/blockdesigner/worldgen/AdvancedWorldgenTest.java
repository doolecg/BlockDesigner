package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.NbtIO;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.worldgen.DatapackExporter.Advanced;
import io.blockdesigner.worldgen.DatapackExporter.HeightMode;
import io.blockdesigner.worldgen.DatapackExporter.Spread;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Height modes, foundations, weathering processors, spread and keep-away options. */
class AdvancedWorldgenTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final McVersion V = McVersion.byId("1.21.1").orElseThrow();

    static Structure hut() {
        Structure s = new Structure();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) s.set(x, 0, z, BlockState.of("stone_bricks"));
        s.set(0, 0, 0, BlockState.of("grass_block"));
        s.set(2, 1, 2, BlockState.of("crafting_table"));
        return s;
    }

    static Map<String, byte[]> build(Advanced a) throws Exception {
        var o = DatapackExporter.Options.defaults("p", "hut", V).withAdvanced(a);
        return DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", hut(), 1)));
    }

    static JsonNode json(Map<String, byte[]> files, String path) throws Exception {
        return JSON.readTree(files.get(path));
    }

    @Test
    void defaultsMatchTheOldOutput() throws Exception {
        var files = build(Advanced.defaults());
        JsonNode st = json(files, "data/p/worldgen/structure/hut.json");
        assertThat(st.path("start_height").path("absolute").asInt()).isZero();
        assertThat(st.path("project_start_to_heightmap").asText()).isEqualTo("WORLD_SURFACE_WG");
        assertThat(files.keySet()).noneMatch(k -> k.contains("processor_list"));
        JsonNode set = json(files, "data/p/worldgen/structure_set/hut.json").path("placement");
        assertThat(set.has("spread_type")).isFalse();
        assertThat(set.has("exclusion_zone")).isFalse();
    }

    @Test
    void heightModes() throws Exception {
        var d = Advanced.defaults();
        JsonNode ocean = json(build(new Advanced(HeightMode.OCEAN_FLOOR, 0, 0, 0, "match", 1, 0, Spread.LINEAR, null, 4)),
                "data/p/worldgen/structure/hut.json");
        assertThat(ocean.path("project_start_to_heightmap").asText()).isEqualTo("OCEAN_FLOOR_WG");

        JsonNode range = json(build(new Advanced(HeightMode.RANGE, -40, 10, 0, "match", 1, 0, Spread.LINEAR, null, 4)),
                "data/p/worldgen/structure/hut.json");
        assertThat(range.has("project_start_to_heightmap")).isFalse();
        assertThat(range.path("start_height").path("type").asText()).isEqualTo("minecraft:uniform");
        assertThat(range.path("start_height").path("min_inclusive").path("absolute").asInt()).isEqualTo(-40);
        assertThat(range.path("start_height").path("max_inclusive").path("absolute").asInt()).isEqualTo(10);

        var bad = DatapackExporter.validate(DatapackExporter.Options.defaults("p", "hut", V)
                        .withAdvanced(new Advanced(HeightMode.RANGE, 20, -20, 0, "match", 1, 0, Spread.LINEAR, null, 4)),
                List.of(new DatapackExporter.Piece("main", hut(), 1)));
        assertThat(bad).anyMatch(e -> e.contains("lowest height"));
        assertThat(d.height()).isEqualTo(HeightMode.SURFACE);
    }

    @Test
    void foundationExtendsTheBottomLayerAndSinksTheStart() throws Exception {
        var files = build(new Advanced(HeightMode.SURFACE, 0, 0, 3, "match", 1, 0, Spread.LINEAR, null, 4));
        var nbt = NbtIO.read(new ByteArrayInputStream(files.get("data/p/structure/hut/main.nbt"))).tag();
        Structure t = Schematics.VANILLA.read(nbt).merged();
        // 25 bottom blocks + 1 table + 3 layers of 25 footings.
        assertThat(t.blockCount()).isEqualTo(26 + 75);
        assertThat(t.get(3, 0, 3)).isEqualTo(BlockState.of("stone_bricks"));
        assertThat(t.get(0, 0, 0)).isEqualTo(BlockState.of("dirt")); // under grass
        assertThat(t.get(2, 4, 2)).isEqualTo(BlockState.of("crafting_table"));
        JsonNode st = json(files, "data/p/worldgen/structure/hut.json");
        assertThat(st.path("start_height").path("absolute").asInt()).isEqualTo(-3);

        var fixed = build(new Advanced(HeightMode.SURFACE, 0, 0, 2, "minecraft:cobbled_deepslate", 1, 0, Spread.LINEAR, null, 4));
        Structure t2 = Schematics.VANILLA.read(NbtIO.read(new ByteArrayInputStream(fixed.get("data/p/structure/hut/main.nbt"))).tag()).merged();
        assertThat(t2.get(1, 0, 1)).isEqualTo(BlockState.of("cobbled_deepslate"));
    }

    @Test
    void weatheringWritesAProcessorListUsedByThePool() throws Exception {
        var files = build(new Advanced(HeightMode.SURFACE, 0, 0, 0, "match", 0.85, 0.4, Spread.LINEAR, null, 4));
        JsonNode list = json(files, "data/p/worldgen/processor_list/hut.json").path("processors");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("processor_type").asText()).isEqualTo("minecraft:block_rot");
        assertThat(list.get(0).path("integrity").asDouble()).isEqualTo(0.85);
        assertThat(list.get(1).path("processor_type").asText()).isEqualTo("minecraft:block_age");
        assertThat(list.get(1).path("mossiness").asDouble()).isEqualTo(0.4);
        JsonNode el = json(files, "data/p/worldgen/template_pool/hut/start.json").path("elements").get(0).path("element");
        assertThat(el.path("processors").asText()).isEqualTo("p:hut");
    }

    @Test
    void spreadAndKeepAway() throws Exception {
        var files = build(new Advanced(HeightMode.SURFACE, 0, 0, 0, "match", 1, 0, Spread.TRIANGULAR, "minecraft:villages", 6));
        JsonNode pl = json(files, "data/p/worldgen/structure_set/hut.json").path("placement");
        assertThat(pl.path("spread_type").asText()).isEqualTo("triangular");
        assertThat(pl.path("exclusion_zone").path("other_set").asText()).isEqualTo("minecraft:villages");
        assertThat(pl.path("exclusion_zone").path("chunk_count").asInt()).isEqualTo(6);
    }

    @Test
    void villageSizeLimitFollowsVersion() {
        assertThat(DatapackExporter.maxVillageSize(McVersion.byId("1.20.1").orElseThrow())).isEqualTo(7);
        assertThat(DatapackExporter.maxVillageSize(V)).isEqualTo(20);
        var o = DatapackExporter.Options.defaults("ns", "town", V);
        var ok = DatapackExporter.validateVillage(o, new DatapackExporter.Village(15, "minecraft:dirt_path", true, 3),
                List.of(new DatapackExporter.VillagePiece("house", VillageExportTest.house(), DatapackExporter.Role.BUILDING, 1, null)));
        assertThat(ok).isEmpty();
    }

    @Test
    void villageCentreFoundationKeepsStreetConnectorsAtFloorLevel() throws Exception {
        var o = DatapackExporter.Options.defaults("ns", "town", V)
                .withAdvanced(new Advanced(HeightMode.SURFACE, 0, 0, 2, "match", 1, 0, Spread.LINEAR, null, 4));
        var files = DatapackExporter.buildVillage(o, DatapackExporter.Village.defaults(), List.of(
                new DatapackExporter.VillagePiece("well", hut(), DatapackExporter.Role.START, 1, null),
                new DatapackExporter.VillagePiece("house", VillageExportTest.house(), DatapackExporter.Role.BUILDING, 1, null)));
        Structure well = VillageExportTest.template(files, "well");
        assertThat(VillageExportTest.jigsaws(well)).allMatch(j -> j.pos().y() == 2);
        assertThat(json(files, "data/ns/worldgen/structure/town.json").path("start_height").path("absolute").asInt()).isEqualTo(-2);
    }
}
