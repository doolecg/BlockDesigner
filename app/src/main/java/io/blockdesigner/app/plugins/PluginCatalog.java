package io.blockdesigner.app.plugins;

import io.blockdesigner.plugin.PluginInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The plugins the Plugins window suggests: the BlockDesigner team's own, each released on GitHub. Install downloads a
 * suggestion's newest release through {@link PluginUpdater}, the same way plugins update.
 */
public final class PluginCatalog {
    /** A plugin to suggest: its id (as in its manifest), name, a line or two about it and its GitHub repository. */
    public record Entry(String id, String name, String description, String repo) {
        /** The repository's page, also the {@code "updates"} link in the plugin's manifest. */
        public String page() {
            return "https://github.com/" + repo;
        }

        /** What {@link PluginUpdater#fetch} needs to find the newest release: no version yet, so any release is newer. */
        public PluginInfo info() {
            return new PluginInfo(id, name, "", "BlockDesigner", description, "", 0, page());
        }
    }

    /**
     * The suggestions, in the order the window lists them. The BlockCompanion Plugin isn't one: BlockDesigner installs it by
     * itself (DefaultPlugins).
     */
    public static final List<Entry> SUGGESTED = List.of(
            new Entry("palette-tools", "Palette Tools",
                    "Repaints a selection with palette swaps, weathering and gradients, with a live preview. Also a Palette panel, "
                            + "a colour palette exporter and a pixel art importer.",
                    "doolecg/BlockDesigner-PaletteTools"),
            new Entry("terrain-generator", "Terrain Generator",
                    "Minecraft-style terrain: pick a generator and a seed, tune it while a map redraws, and bake a region into a "
                            + "layer of ordinary blocks.",
                    "doolecg/BlockDesigner-TerrainGenerator"),
            new Entry("pixel-art-generator", "Pixel Art Generator",
                    "Turns a picture into blocks: pixel art, a relief, an extruded shape or a rounded 3D guess, matched to the "
                            + "closest-looking blocks. Works offline.",
                    "doolecg/BlockDesigner-PixelArtGenerator"),
            new Entry("ai-builder", "AI Builder",
                    "Builds and edits structures from a chat or a reference picture, such as \"build a small castle\". Runs a model "
                            + "on your own PC by default, or uses your own Claude or OpenAI key.",
                    "doolecg/BlockDesigner-AIBuilder"));

    private PluginCatalog() {
    }

    /** The entries of {@code catalog} whose plugin isn't among {@code installedIds}, in catalogue order. */
    public static List<Entry> notInstalled(List<Entry> catalog, Collection<String> installedIds) {
        Set<String> have = Set.copyOf(installedIds);
        return catalog.stream().filter(e -> !have.contains(e.id())).toList();
    }

    /** The suggestions the user doesn't have yet. */
    public static List<Entry> suggestions(PluginManager plugins) {
        return notInstalled(SUGGESTED, plugins.plugins().stream().map(p -> p.info().id()).collect(Collectors.toSet()));
    }
}
