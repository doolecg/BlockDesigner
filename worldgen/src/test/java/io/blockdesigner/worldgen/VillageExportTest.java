package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.NbtIO;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.worldgen.DatapackExporter.Role;
import io.blockdesigner.worldgen.DatapackExporter.Side;
import io.blockdesigner.worldgen.DatapackExporter.VillagePiece;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VillageExportTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final McVersion V = McVersion.byId("1.21.1").orElseThrow();

    /** 5x4x5 house: planks floor, door in the middle of the north wall one block up. */
    static Structure house() {
        Structure s = new Structure();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) s.set(x, 0, z, BlockState.of("oak_planks"));
        for (int y = 1; y < 4; y++) for (int x = 0; x < 5; x++) {
            s.set(x, y, 0, BlockState.of("cobblestone"));
            s.set(x, y, 4, BlockState.of("cobblestone"));
        }
        s.set(2, 1, 0, BlockState.parse("oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]"));
        s.set(2, 2, 0, BlockState.parse("oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]"));
        return s;
    }

    record Jig(BlockPos pos, String orientation, CompoundTag nbt) {
    }

    static Structure template(Map<String, byte[]> files, String piece) throws Exception {
        var nbt = NbtIO.read(new ByteArrayInputStream(files.get("data/ns/structure/town/" + piece + ".nbt"))).tag();
        return Schematics.VANILLA.read(nbt).merged();
    }

    static List<Jig> jigsaws(Structure s) {
        List<Jig> out = new ArrayList<>();
        s.forEachBlock((x, y, z, st) -> {
            if (st.name().equals("minecraft:jigsaw")) out.add(new Jig(new BlockPos(x, y, z), st.get("orientation"), s.blockEntity(new BlockPos(x, y, z))));
        });
        return out;
    }

    @Test
    void writesPoolsTemplatesAndJigsaws() throws Exception {
        var o = DatapackExporter.Options.defaults("ns", "town", V);
        var files = DatapackExporter.buildVillage(o, DatapackExporter.Village.defaults(),
                List.of(new VillagePiece("house", house(), Role.BUILDING, 1, null)));
        assertThat(files).containsKeys(
                "data/ns/worldgen/template_pool/town/start.json",
                "data/ns/worldgen/template_pool/town/streets.json",
                "data/ns/worldgen/template_pool/town/houses.json",
                "data/ns/structure/town/plaza.nbt",
                "data/ns/structure/town/street_long.nbt",
                "data/ns/structure/town/crossroad.nbt",
                "data/ns/structure/town/house.nbt");
        JsonNode st = JSON.readTree(files.get("data/ns/worldgen/structure/town.json"));
        assertThat(st.path("size").asInt()).isEqualTo(6);
        JsonNode streets = JSON.readTree(files.get("data/ns/worldgen/template_pool/town/streets.json"));
        assertThat(streets.path("elements").get(0).path("element").path("projection").asText()).isEqualTo("terrain_matching");
        JsonNode houses = JSON.readTree(files.get("data/ns/worldgen/template_pool/town/houses.json"));
        assertThat(houses.path("elements").get(0).path("element").path("projection").asText()).isEqualTo("rigid");

        // The house gets one entrance jigsaw under its door, facing out of the north wall; it keeps the floor block.
        List<Jig> hj = jigsaws(template(files, "house"));
        assertThat(hj).hasSize(1);
        assertThat(hj.getFirst().pos()).isEqualTo(new BlockPos(2, 0, 0));
        assertThat(hj.getFirst().orientation()).isEqualTo("north_up");
        assertThat(hj.getFirst().nbt().getString("name")).isEqualTo("ns:building_entrance");
        assertThat(hj.getFirst().nbt().getString("final_state")).isEqualTo("minecraft:oak_planks");
        assertThat(hj.getFirst().nbt().contains("selection_priority")).isTrue();

        // Streets: two street connectors (ends) and pairs of house connectors whose target is the house entrance.
        List<Jig> sj = jigsaws(template(files, "street_long"));
        assertThat(sj).filteredOn(j -> j.nbt().getString("target").equals("ns:street")).extracting(Jig::orientation)
                .containsExactlyInAnyOrder("west_up", "east_up");
        assertThat(sj).filteredOn(j -> j.nbt().getString("target").equals("ns:building_entrance"))
                .allMatch(j -> j.nbt().getString("pool").equals("ns:town/houses")).hasSizeGreaterThanOrEqualTo(2);
        // The start plaza sends streets out on all four sides.
        assertThat(jigsaws(template(files, "plaza"))).extracting(Jig::orientation)
                .containsExactlyInAnyOrder("north_up", "east_up", "south_up", "west_up");
    }

    @Test
    void doorOnTheBottomLayerGetsADoorstepAndAChosenSideWins() {
        Structure s = new Structure();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 3; y++) s.set(x, y, 3, BlockState.of("stone_bricks"));
        s.set(1, 0, 3, BlockState.parse("spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]"));
        s.set(1, 1, 3, BlockState.parse("spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]"));
        s.set(0, 0, 0, BlockState.of("stone_bricks"));
        var j = new DatapackExporter.Jigsaw("e", "minecraft:empty", "minecraft:empty");
        Structure out = DatapackExporter.withEntrance(s, null, j, BlockState.of("dirt_path"), V);
        List<Jig> found = jigsaws(out);
        assertThat(found).hasSize(1);
        // Lifted by one: door now at y=1, jigsaw (a path doorstep) below it on the south edge.
        assertThat(found.getFirst().pos()).isEqualTo(new BlockPos(1, 0, 3));
        assertThat(found.getFirst().orientation()).isEqualTo("south_up");
        assertThat(found.getFirst().nbt().getString("final_state")).isEqualTo("minecraft:dirt_path");
        assertThat(out.get(1, 1, 3).name()).isEqualTo("minecraft:spruce_door");

        Structure west = DatapackExporter.withEntrance(house(), Side.WEST, j, BlockState.of("dirt_path"), V);
        assertThat(jigsaws(west).getFirst().orientation()).isEqualTo("west_up");
        assertThat(jigsaws(west).getFirst().pos().x()).isEqualTo(0);
    }

    @Test
    void userJigsawsAreKeptAndOldVersionsSkipPriorities() throws Exception {
        Structure s = house();
        var j = new DatapackExporter.Jigsaw("custom:door", "minecraft:empty", "minecraft:empty");
        DatapackExporter.placeJigsaw(s, 4, 0, 2, Side.EAST, j, BlockState.AIR, McVersion.byId("1.20.1").orElseThrow());
        assertThat(s.blockEntity(new BlockPos(4, 0, 2)).contains("selection_priority")).isFalse();
        var o = DatapackExporter.Options.defaults("ns", "town", V);
        var files = DatapackExporter.buildVillage(o, DatapackExporter.Village.defaults(),
                List.of(new VillagePiece("house", s, Role.BUILDING, 1, null)));
        List<Jig> hj = jigsaws(template(files, "house"));
        assertThat(hj).hasSize(1);
        assertThat(hj.getFirst().nbt().getString("name")).isEqualTo("custom:door");
    }

    @Test
    void validation() {
        var o = DatapackExporter.Options.defaults("ns", "town", V);
        var noHouses = DatapackExporter.validateVillage(o, DatapackExporter.Village.defaults(), List.of());
        assertThat(noHouses).anyMatch(e -> e.contains("building"));
        var noStreets = DatapackExporter.validateVillage(o, new DatapackExporter.Village(6, "minecraft:dirt_path", false, 3),
                List.of(new VillagePiece("house", house(), Role.BUILDING, 1, null)));
        assertThat(noStreets).anyMatch(e -> e.contains("street"));
        var bad = DatapackExporter.validateVillage(o, new DatapackExporter.Village(25, "minecraft:dirt_path", true, 4),
                List.of(new VillagePiece("house", house(), Role.BUILDING, 1, null)));
        assertThat(bad).anyMatch(e -> e.contains("1–20")).anyMatch(e -> e.contains("odd"));
    }
}
