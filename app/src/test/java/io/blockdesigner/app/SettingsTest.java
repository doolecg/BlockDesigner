package io.blockdesigner.app;

import io.blockdesigner.worldgen.LootTables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTest {
    @Test
    void savedPresetsAndLootTablesSurviveARestart(@TempDir Path dir) {
        String old = System.getProperty("blockdesigner.dataDir");
        System.setProperty("blockdesigner.dataDir", dir.toString());
        try {
            Settings s = new Settings();
            var table = new LootTables.CustomTable("Pirate gold", 2, 5, List.of(new LootTables.Item("minecraft:gold_ingot", 10, 1, 4, true)));
            s.lootTables.add(table);
            s.worldgenPresets.put("Flat houses", Map.of("terrain", "BEARD_BOX", "loot.CHEST", "custom:Pirate gold"));
            s.save();

            Settings back = Settings.load();
            assertThat(back.lootTables).containsExactly(table);
            assertThat(back.worldgenPresets.get("Flat houses")).containsEntry("terrain", "BEARD_BOX").containsEntry("loot.CHEST", "custom:Pirate gold");
        } finally {
            if (old == null) System.clearProperty("blockdesigner.dataDir");
            else System.setProperty("blockdesigner.dataDir", old);
        }
    }
}
