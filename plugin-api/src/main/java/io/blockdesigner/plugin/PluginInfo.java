package io.blockdesigner.plugin;

/**
 * What a plugin's {@code blockdesigner-plugin.json} says about it.
 *
 * @param api the {@link PluginApi#VERSION} the plugin was built against
 */
public record PluginInfo(String id, String name, String version, String author, String description, String mainClass, int api) {

    public PluginInfo {
        name = name == null || name.isBlank() ? id : name;
        version = version == null ? "" : version;
        author = author == null ? "" : author;
        description = description == null ? "" : description;
    }
}
