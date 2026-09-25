package io.blockdesigner.core.formats;

import io.blockdesigner.core.version.McVersion;

/**
 * Export settings shared by all formats.
 *
 * @param version     target Minecraft version (sets DataVersion and format revisions)
 * @param includeAir  write air inside the bounding box explicitly, so placing the schematic clears whatever is there;
 *                    when false, air is left out and existing terrain shows through
 * @param spongeVersion Sponge schematic revision for {@code .schem} (2 = WorldEdit 7.2, 3 = WorldEdit 7.3+)
 */
public record WriteOptions(McVersion version, boolean includeAir, int spongeVersion) {

    public static WriteOptions defaults(McVersion version) {
        return new WriteOptions(version, true, 3);
    }

    public WriteOptions withIncludeAir(boolean includeAir) {
        return new WriteOptions(version, includeAir, spongeVersion);
    }

    public WriteOptions withSpongeVersion(int v) {
        return new WriteOptions(version, includeAir, v);
    }
}
