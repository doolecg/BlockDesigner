package io.blockdesigner.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AssetSourceTest {
    @TempDir
    Path dir;

    private Path jar(String name) throws Exception {
        Path jar = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String e : new String[]{"assets/mod/textures/block/a.png", "assets/mod/models/block/a.json", "fabric.mod.json"}) {
                out.putNextEntry(new ZipEntry(e));
                out.write(e.getBytes());
                out.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void readsAndLists() throws Exception {
        try (AssetSource s = AssetSource.open(jar("mod.jar"))) {
            assertThat(s.read("assets/mod/models/block/a.json")).hasValueSatisfying(b -> assertThat(new String(b)).isEqualTo("assets/mod/models/block/a.json"));
            assertThat(s.read("assets/mod/missing.json")).isEmpty();
            assertThat(s.read("assets/mod")).isEmpty();
            assertThat(s.list("assets/")).containsExactlyInAnyOrder("assets/mod/textures/block/a.png", "assets/mod/models/block/a.json");
            assertThat(s.list("assets/mod/textures/")).containsExactly("assets/mod/textures/block/a.png");
            assertThat(s.list("assets/mod/models/block/a")).containsExactly("assets/mod/models/block/a.json");
            assertThat(s.list("")).hasSize(3);
            assertThat(s.list("data/")).isEmpty();
        }
    }

    @Test
    void anOpenJarCanBeDeletedOrReplaced() throws Exception {
        // A game's mods folder: installing a mod update must not fail because BlockDesigner shows its textures.
        Path jar = jar("mod.jar");
        try (AssetSource s = AssetSource.open(jar)) {
            Files.delete(jar);
            assertThat(jar).doesNotExist();
            Files.writeString(jar, "new");
            assertThat(s.read("fabric.mod.json")).isPresent();
        }
    }
}
