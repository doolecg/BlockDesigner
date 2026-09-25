package io.blockdesigner.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.core.version.McVersion;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds Minecraft client jars and modded instances from the official launcher, Prism/MultiMC, CurseForge and Modrinth.
 * Nothing is copied: the app reads textures and models from these files in place.
 */
public final class McInstallLocator {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A vanilla client jar. */
    public record GameJar(McVersion version, Path jar, String launcher) {
        @Override
        public String toString() {
            return version.id() + " (" + launcher + ")";
        }
    }

    /** A launcher instance / profile with its own mods and resource packs. */
    public record Instance(String name, String launcher, String mcVersion, Path gameDir, List<Path> mods, List<Path> resourcePacks) {
        public Path schematicsDir() {
            return gameDir.resolve("schematics");
        }

        @Override
        public String toString() {
            return name + " — " + mcVersion + " (" + launcher + ", " + mods.size() + " mods)";
        }
    }

    public record Result(List<GameJar> jars, List<Instance> instances) {
        /** Newest jar for an exact version id. */
        public Optional<GameJar> jarFor(String versionId) {
            return jars.stream().filter(j -> j.version().id().equals(versionId)).findFirst();
        }
    }

    private final Path appData;
    private final Path userHome;

    public McInstallLocator() {
        this(Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home"))), Path.of(System.getProperty("user.home")));
    }

    public McInstallLocator(Path appData, Path userHome) {
        this.appData = appData;
        this.userHome = userHome;
    }

    public Result scan() {
        Map<String, GameJar> jars = new LinkedHashMap<>();
        List<Instance> instances = new ArrayList<>();

        // Official launcher
        Path dotMc = appData.resolve(".minecraft");
        eachDir(dotMc.resolve("versions"), v -> addJar(jars, v.resolve(v.getFileName() + ".jar"), "Minecraft Launcher"));

        // Prism / MultiMC keep jars as libraries and instances with mmc-pack.json
        for (String launcher : List.of("PrismLauncher", "MultiMC")) {
            Path root = appData.resolve(launcher);
            eachDir(root.resolve("libraries/com/mojang/minecraft"), v ->
                    addJar(jars, v.resolve("minecraft-" + v.getFileName() + "-client.jar"), launcher));
            eachDir(root.resolve("instances"), inst -> {
                Path pack = inst.resolve("mmc-pack.json");
                if (!Files.isRegularFile(pack)) return;
                String mc = null;
                try {
                    for (JsonNode c : JSON.readTree(pack.toFile()).path("components")) {
                        if (c.path("uid").asText().equals("net.minecraft")) mc = c.path("version").asText();
                    }
                } catch (IOException ignored) {
                    return;
                }
                Path game = Files.isDirectory(inst.resolve("minecraft")) ? inst.resolve("minecraft") : inst.resolve(".minecraft");
                if (mc != null) instances.add(instance(inst.getFileName().toString(), launcher, mc, game));
            });
        }

        // CurseForge
        Path cf = userHome.resolve("curseforge/minecraft");
        eachDir(cf.resolve("Install/versions"), v -> addJar(jars, v.resolve(v.getFileName() + ".jar"), "CurseForge"));
        eachDir(cf.resolve("Instances"), inst -> {
            Path meta = inst.resolve("minecraftinstance.json");
            if (!Files.isRegularFile(meta)) return;
            try {
                String mc = JSON.readTree(meta.toFile()).path("gameVersion").asText(null);
                if (mc != null) instances.add(instance(inst.getFileName().toString(), "CurseForge", mc, inst));
            } catch (IOException ignored) {
                // unreadable instance metadata
            }
        });

        // Modrinth App
        Path mr = appData.resolve("ModrinthApp");
        eachDir(mr.resolve("meta/versions"), v -> addJar(jars, v.resolve(v.getFileName() + ".jar"), "Modrinth"));
        eachDir(mr.resolve("profiles"), inst -> {
            Path meta = inst.resolve("profile.json");
            String mc = null;
            try {
                if (Files.isRegularFile(meta)) mc = JSON.readTree(meta.toFile()).path("metadata").path("game_version").asText(null);
            } catch (IOException ignored) {
                // unreadable
            }
            if (mc != null) instances.add(instance(inst.getFileName().toString(), "Modrinth", mc, inst));
        });

        List<GameJar> sorted = new ArrayList<>(jars.values());
        sorted.sort(Comparator.comparing(GameJar::version).reversed());
        instances.sort(Comparator.comparing(Instance::name, String.CASE_INSENSITIVE_ORDER));
        return new Result(List.copyOf(sorted), List.copyOf(instances));
    }

    private static Instance instance(String name, String launcher, String mc, Path game) {
        return new Instance(name, launcher, mc, game, files(game.resolve("mods"), ".jar"), files(game.resolve("resourcepacks"), ".zip"));
    }

    private static void addJar(Map<String, GameJar> jars, Path jar, String launcher) {
        if (!Files.isRegularFile(jar)) return;
        try {
            McVersion v = McVersion.fromClientJar(jar);
            jars.putIfAbsent(v.id(), new GameJar(v, jar, launcher));
        } catch (IOException ignored) {
            // not a client jar (e.g. a modloader stub version folder)
        }
    }

    private static List<Path> files(Path dir, String ext) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext) && Files.isRegularFile(p)).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private interface PathConsumer {
        void accept(Path p);
    }

    private static void eachDir(Path dir, PathConsumer c) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path p : ds) c.accept(p);
        } catch (IOException ignored) {
            // unreadable directory
        }
    }
}
