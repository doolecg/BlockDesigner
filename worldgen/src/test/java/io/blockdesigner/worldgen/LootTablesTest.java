package io.blockdesigner.worldgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.nbt.CompoundTag;
import io.blockdesigner.core.nbt.ListTag;
import io.blockdesigner.core.nbt.NbtIO;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.worldgen.LootTables.Choice;
import io.blockdesigner.worldgen.LootTables.Container;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LootTablesTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final McVersion V = McVersion.byId("1.21.1").orElseThrow();

    static Structure vault() {
        Structure s = DatapackExporterTest.hut();
        s.set(0, 1, 0, BlockState.of("chest"));
        s.set(4, 1, 0, BlockState.of("barrel"));
        s.set(4, 1, 4, BlockState.of("chest"));
        // A chest built with items in it.
        CompoundTag items = new CompoundTag().putString("id", "minecraft:chest");
        ListTag list = new ListTag(io.blockdesigner.core.nbt.Tag.COMPOUND);
        list.add(new CompoundTag().putString("id", "minecraft:dirt").putByte("Slot", (byte) 0).putInt("count", 1));
        items.put("Items", list);
        s.setBlockEntity(new BlockPos(4, 1, 4), items);
        return s;
    }

    static Structure template(Map<String, byte[]> files, String path) throws Exception {
        var nbt = NbtIO.read(new ByteArrayInputStream(files.get(path))).tag();
        return Schematics.VANILLA.read(nbt).regions().getFirst().structure();
    }

    @Test
    void countsContainers() {
        assertThat(LootTables.count(vault())).containsEntry(Container.CHEST, 2).containsEntry(Container.BARREL, 1).hasSize(2);
        assertThat(Container.of(BlockState.of("red_shulker_box"))).isEqualTo(Container.SHULKER_BOX);
        assertThat(Container.of(BlockState.of("oxidized_copper_chest"))).isEqualTo(Container.CHEST);
        assertThat(Container.of(BlockState.of("furnace"))).isNull();
    }

    @Test
    void presetTableReplacesBuiltContents() throws Exception {
        var o = DatapackExporter.Options.defaults("mypack", "vault", V)
                .withLoot(Map.of(Container.CHEST, new Choice.Table("minecraft:chests/simple_dungeon")));
        var files = DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", vault(), 1)));
        Structure s = template(files, "data/mypack/structure/vault/main.nbt");
        for (BlockPos p : List.of(new BlockPos(0, 1, 0), new BlockPos(4, 1, 4))) {
            CompoundTag be = s.blockEntity(p);
            assertThat(be.getString("LootTable")).isEqualTo("minecraft:chests/simple_dungeon");
            assertThat(be.contains("Items")).isFalse();
        }
        // Barrels were left as built (no block entity).
        assertThat(s.blockEntity(new BlockPos(4, 1, 0))).isNull();
        assertThat(files.keySet()).noneMatch(k -> k.contains("loot_table"));
    }

    @Test
    void customTableIsWrittenIntoThePack() throws Exception {
        var table = new LootTables.CustomTable("Pirate Gold", 2, 5, List.of(
                new LootTables.Item("minecraft:gold_ingot", 10, 1, 4, false),
                new LootTables.Item("minecraft:diamond_sword", 1, 1, 1, true)));
        var o = DatapackExporter.Options.defaults("mypack", "vault", V)
                .withLoot(Map.of(Container.BARREL, new Choice.Custom(table), Container.CHEST, Choice.EMPTY));
        var files = DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", vault(), 1)));
        JsonNode lt = JSON.readTree(files.get("data/mypack/loot_table/vault/pirate_gold.json"));
        assertThat(lt.path("type").asText()).isEqualTo("minecraft:chest");
        JsonNode pool = lt.path("pools").get(0);
        assertThat(pool.path("rolls").path("min").asInt()).isEqualTo(2);
        assertThat(pool.path("rolls").path("max").asInt()).isEqualTo(5);
        assertThat(pool.path("entries").get(0).path("functions").get(0).path("count").path("max").asInt()).isEqualTo(4);
        assertThat(pool.path("entries").get(1).path("functions").get(0).path("function").asText()).isEqualTo("minecraft:enchant_randomly");

        Structure s = template(files, "data/mypack/structure/vault/main.nbt");
        assertThat(s.blockEntity(new BlockPos(4, 1, 0)).getString("id")).isEqualTo("minecraft:barrel");
        assertThat(s.blockEntity(new BlockPos(4, 1, 0)).getString("LootTable")).isEqualTo("mypack:vault/pirate_gold");
        // "Empty" clears the chest that was built with dirt in it.
        assertThat(s.blockEntity(new BlockPos(4, 1, 4)).contains("Items")).isFalse();
        assertThat(s.blockEntity(new BlockPos(4, 1, 4)).contains("LootTable")).isFalse();
    }

    @Test
    void legacyFolderAndVersionChecks() throws Exception {
        var old = McVersion.byId("1.20.1").orElseThrow();
        var table = new LootTables.CustomTable("stash", 1, 1, List.of(new LootTables.Item("minecraft:bread", 1, 1, 3, false)));
        var o = DatapackExporter.Options.defaults("p", "hut", old).withLoot(Map.of(Container.CHEST, new Choice.Custom(table)));
        assertThat(DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", vault(), 1))))
                .containsKey("data/p/loot_tables/hut/stash.json");

        var trial = o.withLoot(Map.of(Container.CHEST, new Choice.Table("minecraft:chests/trial_chambers/reward")));
        assertThat(DatapackExporter.validate(trial, List.of(new DatapackExporter.Piece("main", vault(), 1))))
                .anyMatch(e -> e.contains("newer Minecraft"));
        var bad = o.withLoot(Map.of(Container.CHEST, new Choice.Custom(new LootTables.CustomTable("x", 3, 1,
                List.of(new LootTables.Item("not an id", 0, 1, 1, false))))));
        assertThat(DatapackExporter.validate(bad, List.of(new DatapackExporter.Piece("main", vault(), 1)))).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void brushedOnlyTablesUseTheArchaeologyType() throws Exception {
        Structure s = DatapackExporterTest.hut();
        s.set(1, 1, 1, BlockState.of("suspicious_sand"));
        var table = new LootTables.CustomTable("relics", 1, 1, List.of(new LootTables.Item("minecraft:emerald", 1, 1, 1, false)));
        var o = DatapackExporter.Options.defaults("p", "dig", V).withLoot(Map.of(Container.SUSPICIOUS_SAND, new Choice.Custom(table)));
        var files = DatapackExporter.build(o, List.of(new DatapackExporter.Piece("main", s, 1)));
        assertThat(JSON.readTree(files.get("data/p/loot_table/dig/relics.json")).path("type").asText()).isEqualTo("minecraft:archaeology");
        assertThat(template(files, "data/p/structure/dig/main.nbt").blockEntity(new BlockPos(1, 1, 1)).getString("id"))
                .isEqualTo("minecraft:brushable_block");
    }
}
