package io.blockdesigner.assets;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** A read-only tree of resource files: a game/mod jar, a resource-pack zip, or a folder. Paths use '/' separators. */
public interface AssetSource extends Closeable {

    String label();

    Optional<byte[]> read(String path) throws IOException;

    /** All file paths under {@code prefix} (e.g. {@code assets/}). */
    List<String> list(String prefix) throws IOException;

    static AssetSource open(Path path) throws IOException {
        return Files.isDirectory(path) ? new Directory(path) : new Zip(path);
    }

    /**
     * Opens a mod jar plus any jars bundled inside it (NeoForge/Forge {@code META-INF/jarjar/}, Fabric/Quilt
     * {@code META-INF/jars/}), since "bundled" mods keep their real assets in those nested jars. Nested jars are
     * extracted once into a cache folder keyed by size and timestamp.
     */
    static List<AssetSource> openWithNested(Path jar) throws IOException {
        List<AssetSource> out = new ArrayList<>();
        openNested(jar, out, 0);
        return out;
    }

    private static void openNested(Path jar, List<AssetSource> out, int depth) throws IOException {
        if (Files.isDirectory(jar)) {
            out.add(new Directory(jar));
            return;
        }
        Zip zip = new Zip(jar);
        out.add(zip);
        if (depth >= 2) return;
        Path cache = Path.of(System.getProperty("java.io.tmpdir"), "blockdesigner-jarjar");
        for (String name : zip.list("META-INF/")) {
            if (!name.endsWith(".jar") || !(name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/"))) continue;
            Path entry = zip.zip.getPath(name);
            String file = name.substring(name.lastIndexOf('/') + 1);
            Path target = cache.resolve(Files.size(entry) + "-" + Files.getLastModifiedTime(entry).toMillis() + "-" + file);
            if (!Files.isRegularFile(target)) {
                Files.createDirectories(cache);
                Path tmp = Files.createTempFile(cache, "nested", ".tmp");
                Files.copy(entry, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // Only nested jars that carry assets matter for rendering; libraries are skipped cheaply.
            try (ZipFile probe = new ZipFile(target.toFile())) {
                boolean hasAssets = probe.stream().anyMatch(z -> z.getName().startsWith("assets/"));
                boolean hasNested = probe.stream().anyMatch(z -> z.getName().endsWith(".jar"));
                if (!hasAssets && !hasNested) continue;
            } catch (IOException bad) {
                continue;
            }
            openNested(target, out, depth + 1);
        }
    }

    /**
     * A jar or zip, read through a zip file system: unlike {@link ZipFile}, it doesn't lock the file on Windows, so a
     * game's mods and packs can be deleted, renamed or updated while BlockDesigner shows their textures.
     */
    final class Zip implements AssetSource {
        private final Path path;
        private final FileSystem zip;

        public Zip(Path path) throws IOException {
            this.path = path;
            try {
                this.zip = FileSystems.newFileSystem(path);
            } catch (ProviderNotFoundException e) {
                throw new IOException("not a zip: " + path.getFileName(), e);
            }
        }

        @Override
        public String label() {
            return path.getFileName().toString();
        }

        @Override
        public Optional<byte[]> read(String p) throws IOException {
            Path f = zip.getPath(p);
            if (!Files.isRegularFile(f)) return Optional.empty();
            return Optional.of(Files.readAllBytes(f));
        }

        @Override
        public List<String> list(String prefix) throws IOException {
            Path base = zip.getPath("/" + prefix.substring(0, prefix.lastIndexOf('/') + 1));
            if (!Files.isDirectory(base)) return List.of();
            try (Stream<Path> s = Files.walk(base)) {
                return s.filter(Files::isRegularFile).map(f -> f.toString().substring(1)).filter(n -> n.startsWith(prefix)).toList();
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    final class Directory implements AssetSource {
        private final Path root;

        public Directory(Path root) {
            this.root = root;
        }

        @Override
        public String label() {
            return root.getFileName().toString();
        }

        @Override
        public Optional<byte[]> read(String p) throws IOException {
            Path f = root.resolve(p);
            if (!f.normalize().startsWith(root.normalize()) || !Files.isRegularFile(f)) return Optional.empty();
            return Optional.of(Files.readAllBytes(f));
        }

        @Override
        public List<String> list(String prefix) throws IOException {
            Path base = root.resolve(prefix);
            if (!Files.isDirectory(base)) return List.of();
            try (Stream<Path> s = Files.walk(base)) {
                return s.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/')).toList();
            }
        }

        @Override
        public void close() {
        }
    }
}
