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
    /** Whether the Assistant tab is open in the right panel. */
    public boolean showAssistant = true;
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
    /** Hotbar block states (nine slots, "" for empty). */
    public List<String> hotbar = new ArrayList<>();

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

    /** Puts every viewport setting (view, overlays and controls) back to its default. */
    public void resetViewport() {
        Settings d = new Settings();
        showGrid = d.showGrid;
        fastNudgeStep = d.fastNudgeStep;
        placeDelayMs = d.placeDelayMs;
        breakDelayMs = d.breakDelayMs;
        flySpeed = d.flySpeed;
        fovDeg = d.fovDeg;
        clipEnd = d.clipEnd;
        fog = d.fog;
        fogDistance = d.fogDistance;
        orbitSensitivity = d.orbitSensitivity;
        lookSensitivity = d.lookSensitivity;
        zoomSpeed = d.zoomSpeed;
        showOutlines = d.showOutlines;
        showHud = d.showHud;
    }

    // ---- AI assistant ----
    public String aiProvider = "anthropic";
    public String aiModel = "claude-opus-5";
    public String aiEffort = "high";
    /** Anthropic API key, encrypted for the current Windows user (DPAPI), base64. */
    public String anthropicKeyProtected;
    /** Opt in to Anthropic's server-side refusal fallback. */
    public boolean aiRefusalFallback = true;
    public String openAiName = "OpenAI-compatible";
    public String openAiBaseUrl = "http://localhost:11434/v1";
    public String openAiKeyProtected;
    public String openAiModels = "";

    public static Path dir() {
        String appData = System.getenv("APPDATA");
        return Path.of(appData != null ? appData : System.getProperty("user.home"), "BlockDesigner");
    }

    public static Settings load() {
        Path f = dir().resolve("settings.json");
        if (Files.isRegularFile(f)) {
            try {
                return JSON.readValue(f.toFile(), Settings.class);
            } catch (IOException e) {
                System.err.println("Could not read settings, using defaults: " + e.getMessage());
            }
        }
        return new Settings();
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            JSON.writeValue(dir().resolve("settings.json").toFile(), this);
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    public void addRecent(Path p) {
        String s = p.toAbsolutePath().toString();
        recentFiles.remove(s);
        recentFiles.addFirst(s);
        while (recentFiles.size() > 12) recentFiles.removeLast();
    }
}
