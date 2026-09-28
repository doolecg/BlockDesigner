package io.blockdesigner.app.plugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static io.blockdesigner.app.plugins.DefaultPlugins.BLOCKCOMPANION;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultPluginsTest {
    private static final List<DefaultPlugins.Default> ALL = List.of(BLOCKCOMPANION);

    private static List<DefaultPlugins.Default> toInstall(Set<String> installed, Set<String> handled, Set<String> disabled, boolean dataFolder) {
        return DefaultPlugins.toInstall(ALL, installed, handled, disabled, id -> dataFolder);
    }

    @Test
    void installsOnAFirstStart() {
        assertThat(toInstall(Set.of(), Set.of(), Set.of(), false)).containsExactly(BLOCKCOMPANION);
        assertThat(toInstall(Set.of("terrain-generator"), Set.of("something-else"), Set.of("terrain-generator"), false))
                .containsExactly(BLOCKCOMPANION);
    }

    @Test
    void leavesAnInstalledCopyAlone() {
        assertThat(toInstall(Set.of("resource-tracker"), Set.of(), Set.of(), true)).isEmpty();
        // Switched off: still installed, stays off.
        assertThat(toInstall(Set.of("resource-tracker"), Set.of("resource-tracker"), Set.of("resource-tracker"), true)).isEmpty();
    }

    @Test
    void neverBringsBackAnUninstalledPlugin() {
        // Installed by BlockDesigner (or found installed) once, then uninstalled.
        assertThat(toInstall(Set.of(), Set.of("resource-tracker"), Set.of(), false)).isEmpty();
        // Uninstalled before BlockDesigner remembered anything: its data folder is left behind.
        assertThat(toInstall(Set.of(), Set.of(), Set.of(), true)).isEmpty();
        // Switched off, then its jar deleted by hand.
        assertThat(toInstall(Set.of(), Set.of(), Set.of("resource-tracker"), false)).isEmpty();
    }

    @Test
    void remembersCopiesItFindsInstalled() {
        assertThat(DefaultPlugins.alreadyInstalled(ALL, Set.of("resource-tracker"), Set.of())).containsExactly("resource-tracker");
        assertThat(DefaultPlugins.alreadyInstalled(ALL, Set.of("resource-tracker"), Set.of("resource-tracker"))).isEmpty();
        assertThat(DefaultPlugins.alreadyInstalled(ALL, Set.of(), Set.of())).isEmpty();
    }

    @Test
    void asksForAnyRelease() {
        var q = BLOCKCOMPANION.releaseQuery();
        assertThat(q.id()).isEqualTo("resource-tracker");
        assertThat(q.version()).isEmpty();
        assertThat(PluginUpdater.repoOf(q.updates())).isEqualTo("doolecg/BlockDesigner-ResourceTracker");
    }

    @Test
    void findsDataFolders(@TempDir Path plugins) throws Exception {
        assertThat(DefaultPlugins.dataFolderIn(plugins).test("resource-tracker")).isFalse();
        Files.createFile(plugins.resolve("resource-tracker-1.6.0.jar"));
        assertThat(DefaultPlugins.dataFolderIn(plugins).test("resource-tracker")).isFalse();
        Files.createDirectories(plugins.resolve("resource-tracker"));
        assertThat(DefaultPlugins.dataFolderIn(plugins).test("resource-tracker")).isTrue();
    }
}
