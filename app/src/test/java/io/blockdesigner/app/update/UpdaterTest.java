package io.blockdesigner.app.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdaterTest {
    @Test
    void comparesVersionsNumerically() {
        assertThat(Updater.compareVersions("0.4.0", "0.3.0")).isPositive();
        assertThat(Updater.compareVersions("0.10.0", "0.9.9")).isPositive();
        assertThat(Updater.compareVersions("v0.3.0", "0.3.0")).isZero();
        assertThat(Updater.compareVersions("0.3", "0.3.0")).isZero();
        assertThat(Updater.compareVersions("0.3.0-SNAPSHOT", "0.3.0")).isZero();
        assertThat(Updater.compareVersions("0.3.0", "0.3.1")).isNegative();
    }

    @Test
    void picksInstallerAndPortableAssets() throws IOException {
        var json = new ObjectMapper().readTree("""
                {"tag_name":"v0.4.0","name":"BlockDesigner 0.4.0","body":"Notes",
                 "html_url":"https://github.com/doolecg/BlockDesigner/releases/tag/0.4.0",
                 "assets":[
                  {"name":"BlockDesigner-0.4.0-portable.zip","size":10,"digest":"sha256:ab",
                   "browser_download_url":"https://example.invalid/p.zip"},
                  {"name":"BlockDesigner-0.4.0.exe","size":20,
                   "browser_download_url":"https://example.invalid/s.exe"}]}
                """);
        Updater.Release r = Updater.parseRelease(json);
        assertThat(r.version()).isEqualTo("0.4.0");
        assertThat(r.assetFor(Updater.Mode.PORTABLE).sha256()).isEqualTo("ab");
        assertThat(r.assetFor(Updater.Mode.INSTALLED).name()).isEqualTo("BlockDesigner-0.4.0.exe");
        assertThat(r.assetFor(Updater.Mode.INSTALLED).sha256()).isNull();
        assertThat(r.assetFor(Updater.Mode.DEV)).isNull();
    }

    @Test
    void unzipRefusesEntriesOutsideTheFolder(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("bad.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../escape.txt"));
            out.write(1);
        }
        assertThatThrownBy(() -> Updater.unzip(zip, dir.resolve("out"))).isInstanceOf(IOException.class);
        assertThat(dir.resolve("escape.txt")).doesNotExist();
    }

    @Test
    void knowsItsOwnVersion() {
        assertThat(Updater.currentVersion()).matches("\\d+\\.\\d+\\.\\d+");
    }
}
