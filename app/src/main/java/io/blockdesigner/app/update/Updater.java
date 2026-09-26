package io.blockdesigner.app.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Checks the GitHub repo's latest release and installs it. The setup build downloads the release's installer .exe and
 * runs it over the current install; the portable build downloads the portable zip and copies it over its own folder
 * (keeping the {@code data} folder). Both wait for BlockDesigner to exit, then start the new version.
 */
public final class Updater {
    public static final String REPO = "doolecg/BlockDesigner";
    public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
    private static final URI LATEST = URI.create("https://api.github.com/repos/" + REPO + "/releases/latest");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** How this copy was started, which decides how it can update itself. */
    public enum Mode {
        /** Installed by the setup .exe: run the newer setup over it. */
        INSTALLED,
        /** The portable folder: copy the newer portable zip over it. */
        PORTABLE,
        /** Run from the IDE or Gradle: can only point at the releases page. */
        DEV
    }

    public record Asset(String name, URI url, long size, String sha256) {
    }

    /** A published release; {@code installer} and {@code portable} are null when the release lacks that file. */
    public record Release(String version, String name, String notes, URI page, Asset installer, Asset portable) {
        /** The file this copy of BlockDesigner updates from, or null when it can't update itself from this release. */
        public Asset assetFor(Mode mode) {
            return switch (mode) {
                case INSTALLED -> installer;
                case PORTABLE -> portable;
                case DEV -> null;
            };
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** The running version, from the build (e.g. {@code 0.4.0}), else the launcher's; {@code 0.0.0} if unknown. */
    public static String currentVersion() {
        try (InputStream in = Updater.class.getResourceAsStream("/io/blockdesigner/app/version.properties")) {
            if (in == null) return System.getProperty("jpackage.app-version", "0.0.0");
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "0.0.0").strip();
        } catch (IOException e) {
            return "0.0.0";
        }
    }

    public static Mode mode() {
        // jpackage's launcher sets jpackage.app-path; the portable build also points settings at its own folder.
        if (System.getProperty("jpackage.app-path") == null) return Mode.DEV;
        String dataDir = System.getProperty("blockdesigner.dataDir");
        return dataDir != null && !dataDir.isBlank() ? Mode.PORTABLE : Mode.INSTALLED;
    }

    /** The folder holding BlockDesigner.exe (the install or portable folder); null when not run from jpackage. */
    public static Path appRoot() {
        String exe = System.getProperty("jpackage.app-path");
        return exe == null ? null : Path.of(exe).toAbsolutePath().getParent();
    }

    /**
     * Compares dotted versions numerically ({@code 0.10.0 > 0.9.2}). A leading {@code v} and anything from the first
     * {@code -} or {@code +} on (such as {@code -SNAPSHOT}) are ignored; missing parts count as 0.
     */
    public static int compareVersions(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) return c;
        }
        return 0;
    }

    private static int[] parts(String v) {
        String s = v.strip();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        s = s.split("[-+]", 2)[0];
        String[] bits = s.split("\\.");
        int[] out = new int[bits.length];
        for (int i = 0; i < bits.length; i++) {
            String digits = bits[i].replaceAll("\\D.*", "");
            out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return out;
    }

    /** The latest published (non-draft, non-prerelease) release on GitHub. */
    public Release latest() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BlockDesigner/" + currentVersion())
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) throw new IOException("No releases have been published yet.");
        if (res.statusCode() != 200) throw new IOException("GitHub answered " + res.statusCode() + ".");
        return parseRelease(JSON.readTree(res.body()));
    }

    static Release parseRelease(JsonNode n) {
        String tag = n.path("tag_name").asText("");
        Asset installer = null, portable = null;
        for (JsonNode a : n.path("assets")) {
            String name = a.path("name").asText("");
            String digest = a.path("digest").asText("");
            Asset asset = new Asset(name, URI.create(a.path("browser_download_url").asText()), a.path("size").asLong(-1),
                    digest.startsWith("sha256:") ? digest.substring(7) : null);
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith("-portable.zip")) portable = asset;
            else if (lower.endsWith(".exe")) installer = asset;
        }
        return new Release(tag.replaceFirst("^[vV]", ""), n.path("name").asText(tag), n.path("body").asText(""),
                URI.create(n.path("html_url").asText(RELEASES_PAGE)), installer, portable);
    }

    /**
     * Downloads an asset into {@code dir}, reporting progress from 0 to 1, and checks its size and SHA-256 against
     * what GitHub published.
     */
    public Path download(Asset asset, Path dir, DoubleConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        Path target = dir.resolve(asset.name());
        Path part = dir.resolve(asset.name() + ".part");
        HttpRequest req = HttpRequest.newBuilder(asset.url())
                .header("User-Agent", "BlockDesigner/" + currentVersion())
                .build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) throw new IOException("Download failed: GitHub answered " + res.statusCode() + ".");
        long total = asset.size() > 0 ? asset.size() : res.headers().firstValueAsLong("Content-Length").orElse(-1);
        MessageDigest sha = sha256();
        long done = 0;
        try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[1 << 16];
            for (int r; (r = in.read(buf)) > 0; ) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                out.write(buf, 0, r);
                sha.update(buf, 0, r);
                done += r;
                if (total > 0) progress.accept(Math.min(1, done / (double) total));
            }
        } catch (IOException | InterruptedException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        if (asset.size() > 0 && done != asset.size()) {
            Files.deleteIfExists(part);
            throw new IOException("Download was incomplete (" + done + " of " + asset.size() + " bytes).");
        }
        if (asset.sha256() != null && !asset.sha256().equalsIgnoreCase(HexFormat.of().formatHex(sha.digest()))) {
            Files.deleteIfExists(part);
            throw new IOException("The download's checksum doesn't match the release. Nothing was installed.");
        }
        return Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Unzips {@code zip} into {@code dir}, refusing entries that would land outside it. */
    public static void unzip(Path zip, Path dir) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("Bad entry in update zip: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Where downloads are kept until the update is applied. */
    public static Path workDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "BlockDesigner-update");
    }

    /**
     * A fresh folder under {@link #workDir()} for one update attempt, so a file left locked by an earlier attempt
     * (a setup that is still running) can't stop this one. Older attempts are cleared where possible.
     */
    public static Path attemptDir() throws IOException {
        Path work = workDir();
        if (Files.isDirectory(work)) {
            try (var old = Files.list(work)) {
                for (Path p : old.toList()) deleteQuietly(p);
            }
        }
        return Files.createDirectories(work.resolve(Long.toString(System.currentTimeMillis())));
    }

    private static void deleteQuietly(Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (var in = Files.list(p)) {
                    for (Path c : in.toList()) deleteQuietly(c);
                }
            }
            Files.deleteIfExists(p);
        } catch (IOException | RuntimeException ignored) {
            // in use: left for the next attempt
        }
    }

    /** A sentence for an update error (a bare file-system exception message is just a path). */
    public static String describe(Exception e) {
        if (e instanceof java.nio.file.FileSystemException f) {
            String why = f.getReason() != null ? f.getReason() : e.getClass().getSimpleName().replace("Exception", "").replaceAll("(?<=[a-z])(?=[A-Z])", " ").toLowerCase(java.util.Locale.ROOT);
            return "couldn't write " + f.getFile() + " (" + why + "). An earlier update may still be running; restart Windows or try again later.";
        }
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    /**
     * Starts a hidden helper that waits for this process to exit, installs {@code downloaded} (the setup .exe, or the
     * folder the portable zip was unpacked into), then opens the new version. Call it right before quitting.
     */
    public static void launchInstaller(Mode mode, Path downloaded) throws IOException {
        Path root = appRoot();
        if (mode == Mode.DEV || root == null) throw new IOException("This copy wasn't started from an install.");
        String script = installScript(mode, downloaded, root);

        // The script lives outside WORK so it can delete that folder; UTF-8 with a BOM so Windows PowerShell 5.1
        // reads non-ASCII paths correctly.
        Path ps1 = Files.createTempFile("BlockDesigner-update-", ".ps1");
        script += "Remove-Item -LiteralPath " + ps(ps1.toString()) + " -Force -ErrorAction SilentlyContinue\n";
        Files.writeString(ps1, "\uFEFF" + script, StandardCharsets.UTF_8);
        new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", ps1.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /** How long the setup may run before the helper gives up on it and opens BlockDesigner again anyway. */
    static final int SETUP_TIMEOUT_MS = 15 * 60 * 1000;

    /** The helper script for {@link #launchInstaller}: wait for this process, install, then open BlockDesigner. */
    static String installScript(Mode mode, Path downloaded, Path root) {
        Map<String, String> vars = new HashMap<>();
        vars.put("PID", Long.toString(ProcessHandle.current().pid()));
        vars.put("ROOT", ps(root.toString()));
        vars.put("EXE", ps(root.resolve("BlockDesigner.exe").toString()));
        vars.put("WORK", ps(workDir().toString()));
        String script = switch (mode) {
            case INSTALLED -> {
                vars.put("SETUP", ps(downloaded.toString()));
                vars.put("TIMEOUT", Integer.toString(SETUP_TIMEOUT_MS));
                // The jpackage setup passes its arguments on to msiexec: /passive shows only a progress bar, and
                // INSTALLDIR keeps the upgrade in the folder it was installed to. The setup re-quotes any argument
                // with a space as a whole ("INSTALLDIR=C:\Users\A B\..."), which msiexec rejects (it then waits on
                // its usage box), so the folder goes in as its 8.3 short path; without one it is left out and the
                // upgrade goes to the default folder. The setup's window is shown on purpose: this script runs
                // hidden, and anything it starts would otherwise inherit that and ask its questions invisibly. If
                // the setup hangs anyway, it is stopped after a while so BlockDesigner still opens again.
                yield """
                        Wait-Process -Id {PID} -ErrorAction SilentlyContinue
                        $setupArgs = '/passive'
                        try {
                            $short = (New-Object -ComObject Scripting.FileSystemObject).GetFolder({ROOT}).ShortPath
                            if ($short -and $short -notmatch ' ') { $setupArgs += ' INSTALLDIR=' + $short }
                        } catch { }
                        $setup = Start-Process -FilePath {SETUP} -ArgumentList $setupArgs -WindowStyle Normal -PassThru
                        if (-not $setup.WaitForExit({TIMEOUT})) {
                            Get-CimInstance Win32_Process -Filter "ParentProcessId=$($setup.Id)" | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
                            Stop-Process -Id $setup.Id -Force -ErrorAction SilentlyContinue
                        }
                        Start-Process -FilePath {EXE}
                        Remove-Item -LiteralPath {WORK} -Recurse -Force -ErrorAction SilentlyContinue
                        """;
            }
            case PORTABLE -> {
                vars.put("NEW", ps(downloaded.resolve("BlockDesigner").toString()));
                vars.put("DATA", ps(io.blockdesigner.app.Settings.dir().toString()));
                // /MIR replaces the old files; /XD keeps the portable build's settings folder.
                yield """
                        Wait-Process -Id {PID} -ErrorAction SilentlyContinue
                        Start-Sleep -Milliseconds 500
                        & robocopy.exe {NEW} {ROOT} /MIR /XD {DATA} /R:5 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
                        Start-Process -FilePath {EXE}
                        Remove-Item -LiteralPath {WORK} -Recurse -Force -ErrorAction SilentlyContinue
                        """;
            }
            case DEV -> throw new IllegalStateException();
        };
        for (var e : vars.entrySet()) script = script.replace("{" + e.getKey() + "}", e.getValue());
        return script;
    }

    /** A PowerShell single-quoted string literal. */
    private static String ps(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
