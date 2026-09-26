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

    /** Runs with the settings folder pointed at {@code dir}. */
    private static void inDir(Path dir, org.junit.jupiter.api.function.Executable body) throws Throwable {
        String old = System.getProperty("blockdesigner.dataDir");
        System.setProperty("blockdesigner.dataDir", dir.toString());
        try {
            body.execute();
        } finally {
            if (old == null) System.clearProperty("blockdesigner.dataDir");
            else System.setProperty("blockdesigner.dataDir", old);
        }
    }

    @Test
    void aNewVersionBacksUpTheSettingsFirst(@TempDir Path dir) throws Throwable {
        inDir(dir, () -> {
            Settings s = new Settings();
            s.theme = "GREEN";
            s.keybinds.put("TOOL_MOVE", "F6|");
            s.lastVersion = "0.0.1";
            s.save();
            Settings back = Settings.load();
            // Everything carried over, and the old file is in backups/ under the version that wrote it.
            assertThat(back.theme).isEqualTo("GREEN");
            assertThat(back.keybinds).containsEntry("TOOL_MOVE", "F6|");
            assertThat(back.lastVersion).isEqualTo(io.blockdesigner.app.update.Updater.currentVersion());
            assertThat(java.nio.file.Files.exists(Settings.backupDir().resolve("settings-0.0.1.json"))).isTrue();
            // Same version again: no new backup.
            back.save();
            Settings.load();
            try (var files = java.nio.file.Files.list(Settings.backupDir())) {
                assertThat(files.count()).isEqualTo(1);
            }
        });
    }

    @Test
    void backupsLoadInPlaceAndOtherFilesAreRefused(@TempDir Path dir) throws Throwable {
        Settings mine = new Settings();
        mine.theme = "RED";
        mine.hotbar.add("minecraft:stone");
        mine.keybinds.put("SHUFFLE", "F8|");
        Path backup = dir.resolve("backup.json");
        mine.saveTo(backup);
        assertThat(java.nio.file.Files.exists(dir.resolve("backup.json.tmp"))).isFalse();

        Settings live = new Settings();
        live.gameJar = "C:/mc.jar";
        live.copyFrom(Settings.readFrom(backup));
        assertThat(live.theme).isEqualTo("RED");
        assertThat(live.hotbar).containsExactly("minecraft:stone");
        assertThat(live.keybinds).containsEntry("SHUFFLE", "F8|");

        Path notSettings = dir.resolve("other.json");
        java.nio.file.Files.writeString(notSettings, "{\"name\": \"something else\"}");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Settings.readFrom(notSettings)).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void resetKeepsMinecraftAndRecentFiles() {
        Settings s = new Settings();
        s.gameJar = "C:/mc.jar";
        s.recentFiles.add("C:/castle.bdproj");
        s.theme = "RED";
        s.keybinds.put("SHUFFLE", "F8|");
        s.hotbar.add("minecraft:stone");
        s.resetToDefaults();
        assertThat(s.gameJar).isEqualTo("C:/mc.jar");
        assertThat(s.recentFiles).containsExactly("C:/castle.bdproj");
        assertThat(s.theme).isEqualTo(new Settings().theme);
        assertThat(s.keybinds).isEmpty();
        assertThat(s.hotbar).isEmpty();
    }

    @Test
    void anUnreadableFileIsSetAsideNotLost(@TempDir Path dir) throws Throwable {
        inDir(dir, () -> {
            java.nio.file.Files.writeString(dir.resolve("settings.json"), "{ this is not json");
            Settings s = Settings.load();
            assertThat(s.theme).isEqualTo(new Settings().theme);
            try (var files = java.nio.file.Files.list(dir)) {
                assertThat(files.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("settings-broken-"));
            }
        });
    }
}
