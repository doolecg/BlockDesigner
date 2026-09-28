package io.blockdesigner.app.plugins;

import io.blockdesigner.plugin.PluginInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Plugins BlockDesigner installs by itself, once: at startup, a default plugin that isn't installed is downloaded from
 * its latest GitHub release (through {@link PluginUpdater}) and installed switched on. Each is installed at most once
 * per user; after that it is the user's to keep, switch off or uninstall, and an uninstalled one never comes back by
 * itself. See {@link #toInstall}.
 */
public final class DefaultPlugins {
    /** A plugin installed by default: its manifest id, its name for messages and its release source. */
    public record Default(String id, String name, String updates) {
        /** What the updater needs to find the latest release: no version installed, so any release is newer. */
        public PluginInfo releaseQuery() {
            return new PluginInfo(id, name, "", "", "", "", 1, updates);
        }
    }

    /**
     * The BlockCompanion Plugin: links BlockDesigner to BlockCompanion games. Its id stays {@code resource-tracker}
     * (its name before 1.6.0), so installed copies keep updating and keep their data folder.
     */
    public static final Default BLOCKCOMPANION = new Default("resource-tracker", "BlockCompanion Plugin",
            "https://github.com/doolecg/BlockDesigner-ResourceTracker");

    public static final List<Default> ALL = List.of(BLOCKCOMPANION);

    private DefaultPlugins() {
    }

    /**
     * The default plugins to download and install now. One is skipped when:
     * <ul>
     *   <li>it is installed (any jar providing its id, on, off or failed);</li>
     *   <li>BlockDesigner has handled it before ({@code handled}: installed it, or found it installed), so an
     *       uninstall is remembered;</li>
     *   <li>the user switched it off ({@code disabled}), even if its jar is gone since;</li>
     *   <li>it has a data folder ({@code hasDataFolder}) without being installed: the user had it before this rule
     *       existed and removed it.</li>
     * </ul>
     */
    public static List<Default> toInstall(Collection<Default> defaults, Collection<String> installed, Collection<String> handled,
                                          Collection<String> disabled, Predicate<String> hasDataFolder) {
        List<Default> out = new ArrayList<>();
        for (Default d : defaults) {
            if (installed.contains(d.id()) || handled.contains(d.id()) || disabled.contains(d.id())) continue;
            if (hasDataFolder.test(d.id())) continue;
            out.add(d);
        }
        return out;
    }

    /** The ids to remember as handled: those already installed, which the user now owns like any other plugin. */
    public static List<String> alreadyInstalled(Collection<Default> defaults, Collection<String> installed, Collection<String> handled) {
        List<String> out = new ArrayList<>();
        for (Default d : defaults) if (installed.contains(d.id()) && !handled.contains(d.id())) out.add(d.id());
        return out;
    }

    /** Whether the plugins folder has a data folder for this id (plugins keep their data in {@code plugins/<id>/}). */
    public static Predicate<String> dataFolderIn(Path pluginsFolder) {
        return id -> Files.isDirectory(pluginsFolder.resolve(id));
    }
}
