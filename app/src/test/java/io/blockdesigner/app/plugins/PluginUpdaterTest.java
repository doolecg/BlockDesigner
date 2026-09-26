package io.blockdesigner.app.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.plugin.PluginInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginUpdaterTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static PluginInfo info(String version, String updates) {
        return new PluginInfo("reference-planes", "Reference Planes", version, "", "", "x.Main", 4, updates);
    }

    private static com.fasterxml.jackson.databind.JsonNode release(String tag, String... jars) throws Exception {
        StringBuilder a = new StringBuilder();
        for (String j : jars) {
            if (!a.isEmpty()) a.append(',');
            a.append("{\"name\":\"").append(j).append("\",\"browser_download_url\":\"https://github.com/o/r/releases/download/")
                    .append(tag).append('/').append(j).append("\",\"size\":10,\"digest\":\"sha256:ab\"}");
        }
        return JSON.readTree("{\"tag_name\":\"" + tag + "\",\"html_url\":\"https://github.com/o/r/releases/tag/" + tag + "\",\"assets\":[" + a + "]}");
    }

    @Test
    void readsGithubRepositoryLinks() {
        assertThat(PluginUpdater.repoOf("https://github.com/doolecg/BlockDesigner-ReferencePlanes")).isEqualTo("doolecg/BlockDesigner-ReferencePlanes");
        assertThat(PluginUpdater.repoOf("https://github.com/o/r.git")).isEqualTo("o/r");
        assertThat(PluginUpdater.repoOf("https://github.com/o/r/releases")).isEqualTo("o/r");
        assertThat(PluginUpdater.repoOf("http://github.com/o/r")).isNull();
        assertThat(PluginUpdater.repoOf("https://example.com/o/r")).isNull();
        assertThat(PluginUpdater.repoOf(null)).isNull();
        assertThat(PluginUpdater.updatable(info("1.0.0", null))).isFalse();
        assertThat(PluginUpdater.describe(info("1.0.0", null))).contains("by hand");
    }

    @Test
    void findsANewerJar() throws Exception {
        var f = PluginUpdater.newer(info("1.1.1", "https://github.com/o/r"), release("v1.2.0", "reference-planes-1.2.0.jar", "sources.jar"));
        assertThat(f).isNotNull();
        assertThat(f.version()).isEqualTo("1.2.0");
        assertThat(f.jar().name()).isEqualTo("reference-planes-1.2.0.jar");
        assertThat(f.jar().sha256()).isEqualTo("ab");
        assertThat(PluginUpdater.newer(info("1.2.0", "https://github.com/o/r"), release("1.2.0", "reference-planes-1.2.0.jar"))).isNull();
        assertThat(PluginUpdater.newer(info("1.10.0", "https://github.com/o/r"), release("1.9.0", "reference-planes-1.9.0.jar"))).isNull();
        // Its only jar, whatever it's called.
        assertThat(PluginUpdater.newer(info("1.0.0", "x"), release("2.0.0", "planes.jar")).jar().name()).isEqualTo("planes.jar");
        assertThatThrownBy(() -> PluginUpdater.newer(info("1.0.0", "x"), release("2.0.0", "a.jar", "b.jar"))).hasMessageContaining("several jars");
        assertThatThrownBy(() -> PluginUpdater.newer(info("1.0.0", "x"), release("2.0.0"))).hasMessageContaining("no .jar");
    }
}
