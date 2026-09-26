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

    @Test
    void installerNeverGetsAQuotedFolderWithSpaces() {
        // msiexec rejects "INSTALLDIR=C:\Users\A B\..." (the setup re-quotes it like that) and waits on a hidden usage box.
        Path root = Path.of("C:\\Users\\Dayle Frost\\AppData\\Local\\BlockDesigner");
        String script = Updater.installScript(Updater.Mode.INSTALLED, Path.of("C:\\Temp\\BlockDesigner-9.9.9.exe"), root);
        assertThat(script).doesNotContain("INSTALLDIR=\"").doesNotContain("-Wait\n");
        assertThat(script).contains("ShortPath").contains("-WindowStyle Normal").contains("WaitForExit(" + Updater.SETUP_TIMEOUT_MS + ")");
        assertThat(script).contains("Start-Process -FilePath 'C:\\Users\\Dayle Frost\\AppData\\Local\\BlockDesigner\\BlockDesigner.exe'");
    }

    @Test
    void shortPathOfAFolderWithSpacesHasNone(@TempDir Path dir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path spaced = java.nio.file.Files.createDirectories(dir.resolve("Dayle Frost"));
        Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "(New-Object -ComObject Scripting.FileSystemObject).GetFolder('" + spaced + "').ShortPath").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor();
        // 8.3 names can be switched off per volume; then the script leaves INSTALLDIR out, which is also fine.
        org.junit.jupiter.api.Assumptions.assumeFalse(out.contains(" "), "8.3 short names are off on this volume");
        assertThat(java.nio.file.Files.isDirectory(Path.of(out))).isTrue();
    }

    @Test
    void theMsiWinsOverASetupExe() throws IOException {
        var json = new ObjectMapper().readTree("""
                {"tag_name":"0.4.7","assets":[
                  {"name":"BlockDesigner-0.4.7.msi","size":30,"browser_download_url":"https://example.invalid/i.msi"},
                  {"name":"BlockDesigner-0.4.7.exe","size":20,"browser_download_url":"https://example.invalid/s.exe"}]}
                """);
        assertThat(Updater.parseRelease(json).assetFor(Updater.Mode.INSTALLED).name()).isEqualTo("BlockDesigner-0.4.7.msi");
    }

    @Test
    void msiInstallsKeepTheFolderQuotedForMsiexec() {
        Path root = Path.of("C:\\Program Files\\BlockDesigner");
        String script = Updater.installScript(Updater.Mode.INSTALLED, Path.of("C:\\Temp\\x\\BlockDesigner-9.9.9.msi"), root);
        // msiexec is run directly, so a quoted value is fine: INSTALLDIR="C:\Program Files\BlockDesigner".
        assertThat(script).contains("'msiexec.exe'")
                .contains("/i \"C:\\Temp\\x\\BlockDesigner-9.9.9.msi\" /passive INSTALLDIR=\"C:\\Program Files\\BlockDesigner\"");
        assertThat(script).contains("Start-Process -FilePath 'C:\\Program Files\\BlockDesigner\\BlockDesigner.exe'");
    }

    @Test
    void onlyOtherBlockDesignerInstallsAreLeftovers(@TempDir Path dir) throws IOException {
        Path current = Files.createDirectories(dir.resolve("Program Files/BlockDesigner"));
        Path old = Files.createDirectories(dir.resolve("AppData/Local/BlockDesigner"));
        String code = "{EBB9311C-BB0E-3248-AF88-50975699C073}";
        assertThat(Updater.isLeftover(code, "BlockDesigner", old.toString(), current)).isTrue();
        assertThat(Updater.isLeftover(code, "BlockDesigner", current.toString(), current)).as("ourselves").isFalse();
        assertThat(Updater.isLeftover(code, "Something Else", old.toString(), current)).isFalse();
        assertThat(Updater.isLeftover("NotAGuid", "BlockDesigner", old.toString(), current)).isFalse();
        assertThat(Updater.isLeftover(code, "BlockDesigner", null, current)).isFalse();
    }
}
