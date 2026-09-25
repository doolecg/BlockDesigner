package io.blockdesigner.assets;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Layered asset sources, highest priority first (resource packs, then mods, then the game jar), mirroring how the
 * game resolves resources.
 */
public final class AssetStack implements Closeable {
    private final List<AssetSource> sources;

    public AssetStack(List<AssetSource> highestPriorityFirst) {
        this.sources = List.copyOf(highestPriorityFirst);
    }

    public List<AssetSource> sources() {
        return sources;
    }

    public Optional<byte[]> read(String path) {
        for (AssetSource s : sources) {
            try {
                Optional<byte[]> b = s.read(path);
                if (b.isPresent()) return b;
            } catch (IOException ignored) {
                // A broken source shouldn't hide the same file in lower-priority sources.
            }
        }
        return Optional.empty();
    }

    /** Union of paths under {@code prefix} across all sources. */
    public Set<String> list(String prefix) {
        Set<String> out = new LinkedHashSet<>();
        for (AssetSource s : sources) {
            try {
                out.addAll(s.list(prefix));
            } catch (IOException ignored) {
                // skip unreadable source
            }
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        List<IOException> errors = new ArrayList<>();
        for (AssetSource s : sources) {
            try {
                s.close();
            } catch (IOException e) {
                errors.add(e);
            }
        }
        if (!errors.isEmpty()) throw errors.getFirst();
    }
}
