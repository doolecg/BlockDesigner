package io.blockdesigner.app.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginsDialogTest {
    @TempDir
    Path dir;

    @Test
    void onlyDroppedJarFilesInstall() throws Exception {
        Path a = Files.createFile(dir.resolve("palette-tools-1.2.0.jar"));
        Path b = Files.createFile(dir.resolve("Upper.JAR"));
        Path png = Files.createFile(dir.resolve("logo.png"));
        Path folder = Files.createDirectory(dir.resolve("folder.jar"));
        assertThat(PluginsDialog.jars(List.of(a.toFile(), png.toFile(), b.toFile(), folder.toFile()))).containsExactly(a, b);
        assertThat(PluginsDialog.jars(List.<File>of())).isEmpty();
        assertThat(PluginsDialog.jars(null)).isEmpty();
    }
}
