package io.blockdesigner.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** User preferences persisted as JSON under {@code %APPDATA%/BlockDesigner}. Public fields keep Jackson simple. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Settings {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Path of the game jar to load assets from. */
    public String gameJar;
    /** Launcher instance name whose mods/resource packs are layered on top, if any. */
    public String instanceName;
    public List<String> extraMods = new ArrayList<>();
    public List<String> resourcePacks = new ArrayList<>();
    public boolean darkTheme = true;
    /** Colour theme (an AppTheme name: CLAUDE, BLUE, GREEN, RED, ORANGE, ZEN). */
    public String theme = "CLAUDE";
    /** DARK, LIGHT or SYSTEM (follow Windows); null in settings saved before themes, where darkTheme decides. */
    public String themeMode;
    /** Show the start screen (recent projects, new, jar, download sites) when the app opens. */
    public boolean showStartScreen = true;
    /** The window as it was last closed: normal (not maximised) bounds x, y, width, height, or null the first time. */
    public double[] windowBounds;
    public boolean windowMaximized;
    /** Widths of the left (layers and palette) and right panels, and the layers / palette split, as last left. */
    public double leftPanelWidth, rightPanelWidth, leftSplit;
    /** Look for a newer release on GitHub when the app opens. */
    public boolean checkForUpdates = true;
    /** A release version the user chose to skip; not offered again at startup. */
    public String skippedVersion;
    /** Whether the Resource Tracker tab is open in the right panel. */
    public boolean showResources = true;
    public boolean showGrid = true;
    public List<String> recentFiles = new ArrayList<>();
    public String author = System.getProperty("user.name", "");
    /** Blocks moved per nudge while the fast-nudge key is held. */
    public int fastNudgeStep = 8;
    /** Repeat delay while holding place (Minecraft uses 4 ticks = 200 ms). */
    public int placeDelayMs = 200;
    /** Repeat delay while holding break (Minecraft creative uses 5 ticks = 250 ms). */
    public int breakDelayMs = 250;
    /** Creative flight speed in blocks per second (Minecraft: ~10.9, sprinting ~21.6). */
    public double flySpeed = 10.9;
    /** Paint brush / eraser size (1 = one block, n = a (2n-1)-block wide shape) and shape (cube, else sphere). */
    public int brushSize = 2;
    public boolean brushCube;
    /** Brush mode (a Sculpt.Mode name) and strength 1–5. */
    public String brushMode = "DRAW";
    public int brushStrength = 2;
    /** Brush dabs per second while painting (the path between dabs is still filled in). */
    public double brushRate = 8;
    /** Flight glides to a stop (and eases up to speed) instead of stopping dead. */
    public boolean flyMomentum = true;
    /** Place / break thuds and their volume (0..1). */
    public boolean blockSounds = true;
    public double soundVolume = 0.6;
    /** Clicks for buttons and menus, pops for picking up and putting down blocks, a tick for hotbar slots. */
    public boolean uiSounds = true;
    /** Chips fly out of broken blocks. */
    public boolean breakParticles = true;
    /** Hotbar block states (nine slots, "" for empty). */
    public List<String> hotbar = new ArrayList<>();
    /** Build-mode shape (a ShapeTool.Shape name) placed by right-dragging; SINGLE places single blocks. */
    public String buildShape = "SINGLE";
    /** Build-mode symmetry: on/off, mirror planes, radial copies, centre (in half blocks), turn blocks to match, show planes. */
    public boolean symOn;
    public boolean symX = true, symY, symZ;
    public int symRadial = 1;
    public int[] symCenter = {1, 129, 1};
    public boolean symFlip = true;
    public boolean symShowPlanes = true;
    /** Ids of plugins the user switched off in the Plugins window. */
    public List<String> disabledPlugins = new ArrayList<>();
    /** Last values of plugin options, per feature ("plugin/kind/id"), as text; see OptionStore. */
    public java.util.Map<String, java.util.Map<String, String>> pluginOptions = new java.util.LinkedHashMap<>();
    /** Plugin panels ("plugin/panel") the user closed; every other plugin panel opens with its plugin. */
    public List<String> closedPluginPanels = new ArrayList<>();
    /** Keys for plugin tools ("plugin/tool" to a key such as "Shift+K", "" for none), overriding the plugin's default. */
    public java.util.Map<String, String> pluginToolKeys = new java.util.LinkedHashMap<>();

    // ---- Export window (remembered between exports) ----
    /** Card chosen last: a format id, "datapack", or "plugin:<plugin>/<exporter>". */
    public String exportChoice = "litematica";
    /** Last folder written to, per card. */
    public java.util.Map<String, String> exportFolders = new java.util.LinkedHashMap<>();
    public boolean exportIncludeAir = true;
    public int exportSpongeVersion = 3;
    /** Which layers: ACTIVE, SELECTED, VISIBLE or EACH. */
    public String exportSource = "VISIBLE";
    /** Loot tables made in the data pack window, offered for every pack. */
    public List<io.blockdesigner.worldgen.LootTables.CustomTable> lootTables = new ArrayList<>();
    /** Worldgen presets saved in the data pack window: name, then setting to value. */
    public java.util.Map<String, java.util.Map<String, String>> worldgenPresets = new java.util.LinkedHashMap<>();

    // ---- Viewport (the sliders popover over the 3D view) ----
    /** Vertical field of view in degrees. */
    public double fovDeg = 50;
    /** Far clipping distance in blocks. */
    public double clipEnd = 4000;
    public boolean fog = true;
    /** Distance at which fog starts, in blocks. */
    public double fogDistance = 900;
    public double orbitSensitivity = 1;
    public double lookSensitivity = 1;
    public double zoomSpeed = 1;
    public boolean showOutlines = true;
    public boolean showHud = true;
    /** Control prompts in the viewport's bottom-right corner (Shift+F1). */
    public boolean showKeyHints = true;
    /** Changed key binds by action name: "main|alternative" key combinations ("" for none); see Keybinds. */
    public java.util.Map<String, String> keybinds = new java.util.LinkedHashMap<>();
    /** The version that last saved these settings (an automatic backup is made when it changes). */
    public String lastVersion;

    /** Puts every viewport setting (view, overlays and controls) back to its default. */
    public void resetViewport() {
        Settings d = new Settings();
        showGrid = d.showGrid;
        fastNudgeStep = d.fastNudgeStep;
        placeDelayMs = d.placeDelayMs;
        breakDelayMs = d.breakDelayMs;
        flySpeed = d.flySpeed;
        flyMomentum = d.flyMomentum;
        brushRate = d.brushRate;
        blockSounds = d.blockSounds;
        soundVolume = d.soundVolume;
        uiSounds = d.uiSounds;
        breakParticles = d.breakParticles;
        fovDeg = d.fovDeg;
        clipEnd = d.clipEnd;
        fog = d.fog;
        fogDistance = d.fogDistance;
        orbitSensitivity = d.orbitSensitivity;
        lookSensitivity = d.lookSensitivity;
        zoomSpeed = d.zoomSpeed;
        showOutlines = d.showOutlines;
        showHud = d.showHud;
        showKeyHints = d.showKeyHints;
    }

    /**
     * Where settings live: {@code %APPDATA%/BlockDesigner}, or the folder named by {@code -Dblockdesigner.dataDir}
     * (the portable build points it next to its exe so it leaves nothing behind on the PC).
     */
    public static Path dir() {
        String override = System.getProperty("blockdesigner.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String appData = System.getenv("APPDATA");
        return Path.of(appData != null ? appData : System.getProperty("user.home"), "BlockDesigner");
    }

    /** Where automatic backups (one per version change) are kept. */
    public static Path backupDir() {
        return dir().resolve("backups");
    }

    /**
     * The saved settings, or defaults. A file that can't be read is set aside (settings-broken-….json) rather than
     * overwritten, and the first start of a new version copies the file into {@link #backupDir()} first.
     */
    public static Settings load() {
        Path f = dir().resolve("settings.json");
        if (Files.isRegularFile(f)) {
            try {
                Settings s = JSON.readValue(f.toFile(), Settings.class);
                String now = io.blockdesigner.app.update.Updater.currentVersion();
                if (!now.equals(s.lastVersion)) {
                    backupBeforeUpgrade(f, s.lastVersion == null ? "older" : s.lastVersion);
                    s.lastVersion = now;
                }
                return s;
            } catch (IOException e) {
                System.err.println("Could not read settings, using defaults: " + e.getMessage());
                try {
                    Files.move(f, dir().resolve("settings-broken-" + System.currentTimeMillis() + ".json"));
                } catch (IOException ignored) {
                    // leave it; it will be overwritten on the next save
                }
            }
        }
        Settings s = new Settings();
        s.lastVersion = io.blockdesigner.app.update.Updater.currentVersion();
        return s;
    }

    private static void backupBeforeUpgrade(Path file, String from) {
        try {
            Files.createDirectories(backupDir());
            Files.copy(file, backupDir().resolve("settings-" + from.replaceAll("[^0-9A-Za-z.-]", "_") + ".json"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Keep the ten newest.
            try (var files = Files.list(backupDir())) {
                var old = files.filter(p -> p.getFileName().toString().startsWith("settings-"))
                        .sorted(java.util.Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed()).skip(10).toList();
                for (Path p : old) Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            System.err.println("Could not back up settings: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            saveTo(dir().resolve("settings.json"));
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    /** Writes the settings to {@code file} safely: to a temporary file first, then swapped in. */
    public void saveTo(Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        JSON.writeValue(tmp.toFile(), this);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Reads a settings backup; fails (with Jackson's message) if it isn't one. */
    public static Settings readFrom(Path file) throws IOException {
        var tree = JSON.readTree(file.toFile());
        if (tree == null || !tree.isObject() || !(tree.has("theme") || tree.has("keybinds") || tree.has("hotbar")))
            throw new IOException("That file isn't a BlockDesigner settings backup.");
        return JSON.treeToValue(tree, Settings.class);
    }

    /** Takes every setting from {@code other}, in place (the app holds on to this object). */
    public void copyFrom(Settings other) {
        try {
            String keep = lastVersion;
            JSON.readerForUpdating(this).readValue(JSON.writeValueAsBytes(other));
            lastVersion = keep;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Back to the defaults, keeping what the app needs to work and nothing personal to lose: the Minecraft jar,
     * instance, mods and resource packs, and the recent files.
     */
    public void resetToDefaults() {
        Settings d = new Settings();
        d.gameJar = gameJar;
        d.instanceName = instanceName;
        d.extraMods = new ArrayList<>(extraMods);
        d.resourcePacks = new ArrayList<>(resourcePacks);
        d.recentFiles = new ArrayList<>(recentFiles);
        // Maps and lists are replaced rather than merged when copying, so clear them first.
        keybinds.clear();
        hotbar.clear();
        pluginOptions.clear();
        pluginToolKeys.clear();
        closedPluginPanels.clear();
        disabledPlugins.clear();
        exportFolders.clear();
        worldgenPresets.clear();
        lootTables.clear();
        copyFrom(d);
    }

    public void addRecent(Path p) {
        String s = p.toAbsolutePath().toString();
        recentFiles.remove(s);
        recentFiles.addFirst(s);
        while (recentFiles.size() > 12) recentFiles.removeLast();
    }
}
