package io.blockdesigner.app.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.app.update.Updater;
import io.blockdesigner.plugin.PluginInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps plugins up to date from their release source: the {@code "updates"} link in a plugin's manifest, a GitHub
 * repository whose latest release has the plugin's jar. Plugins without one are updated by hand. This class finds and
 * downloads updates; the caller installs them (on the JavaFX thread, with {@link PluginManager#install}).
 */
public final class PluginUpdater {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern GITHUB = Pattern.compile("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?(?:/.*)?$");

    /** A newer release of a plugin: its version, the jar to download and the release page. */
    public record Found(String pluginId, String version, Updater.Asset jar, URI page) {
    }

    private final Updater downloads = new Updater();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** {@code owner/repo} for a GitHub repository link, or null when the link isn't one. */
    public static String repoOf(String url) {
        if (url == null) return null;
        Matcher m = GITHUB.matcher(url.strip());
        return m.matches() ? m.group(1) + "/" + m.group(2) : null;
    }

    /** Whether the plugin can update itself: its manifest links a release source BlockDesigner can read. */
    public static boolean updatable(PluginInfo info) {
        return repoOf(info.updates()) != null;
    }

    /** How the plugin is updated, for the Plugins window and the plugin's tab. */
    public static String describe(PluginInfo info) {
        String repo = repoOf(info.updates());
        if (repo != null) return "Updates automatically from github.com/" + repo;
        if (info.updates() != null && !info.updates().isBlank()) return "Updates by hand: its release source isn't a GitHub repository";
        return "Updates by hand: it doesn't link a release source";
    }

    /** The newer release of this plugin, or null when it's up to date or can't update itself. */
    public Found check(PluginInfo info) throws IOException, InterruptedException {
        String repo = repoOf(info.updates());
        if (repo == null) return null;
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BlockDesigner/" + Updater.currentVersion())
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) throw new IOException("github.com/" + repo + " has no releases");
        if (res.statusCode() != 200) throw new IOException("GitHub answered " + res.statusCode() + " for github.com/" + repo);
        return newer(info, JSON.readTree(res.body()));
    }

    /**
     * The update in a GitHub release, if its version is newer than the installed one: the jar named after the plugin
     * ({@code <id>-<version>.jar}), else the release's only jar.
     */
    static Found newer(PluginInfo info, JsonNode release) throws IOException {
        String version = release.path("tag_name").asText("").replaceFirst("^[vV]", "");
        if (version.isBlank() || Updater.compareVersions(version, info.version().isBlank() ? "0" : info.version()) <= 0) return null;
        JsonNode pick = null;
        int jars = 0;
        for (JsonNode a : release.path("assets")) {
            String name = a.path("name").asText("").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jar")) continue;
            jars++;
            if (name.startsWith(info.id().toLowerCase(Locale.ROOT) + "-") || pick == null) pick = a;
        }
        if (pick == null) throw new IOException("its " + version + " release has no .jar");
        if (jars > 1 && !pick.path("name").asText("").toLowerCase(Locale.ROOT).startsWith(info.id().toLowerCase(Locale.ROOT) + "-")) {
            throw new IOException("its " + version + " release has several jars and none is named " + info.id() + "-<version>.jar");
        }
        String digest = pick.path("digest").asText("");
        Updater.Asset jar = new Updater.Asset(pick.path("name").asText(), URI.create(pick.path("browser_download_url").asText()),
                pick.path("size").asLong(-1), digest.startsWith("sha256:") ? digest.substring(7) : null);
        return new Found(info.id(), version, jar, URI.create(release.path("html_url").asText("https://github.com")));
    }

    /** Downloads the update's jar (size and checksum checked against the release) into a temporary folder. */
    public Path download(Found f) throws IOException, InterruptedException {
        return downloads.download(f.jar(), Updater.workDir().resolve("plugins").resolve(f.pluginId()), p -> {
        });
    }
}
