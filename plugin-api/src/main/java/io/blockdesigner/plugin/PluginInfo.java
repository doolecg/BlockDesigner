package io.blockdesigner.plugin;

/**
 * What a plugin's {@code blockdesigner-plugin.json} says about it.
 *
 * @param api     the {@link PluginApi#VERSION} the plugin was built against
 * @param updates where new releases are published (a GitHub repository link, {@code "updates"} in the manifest), so
 *                BlockDesigner can update the plugin by itself; null when it is updated by hand
 */
public record PluginInfo(String id, String name, String version, String author, String description, String mainClass, int api,
                         String updates) {
    public PluginInfo {
        name = name == null || name.isBlank() ? id : name;
        version = version == null ? "" : version;
        author = author == null ? "" : author;
        description = description == null ? "" : description;
        updates = updates == null || updates.isBlank() ? null : updates.strip();
    }

    /** Without a release source (as before the {@code updates} field). */
    public PluginInfo(String id, String name, String version, String author, String description, String mainClass, int api) {
        this(id, name, version, author, description, mainClass, api, null);
    }
}
