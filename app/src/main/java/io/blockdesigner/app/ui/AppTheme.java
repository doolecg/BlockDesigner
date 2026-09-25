package io.blockdesigner.app.ui;

import io.blockdesigner.render.FrameRequest;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colour themes. Each one recolours AtlantaFX Primer (dark or light) by overriding its looked-up colours, and the
 * result is installed as the application's user-agent stylesheet, so every window, menu, tooltip and popup follows
 * it. The 3D view's sky and grid and the accent of outlines follow the theme too.
 */
public enum AppTheme {
    CLAUDE("Claude", "Professional warmth: ivory paper, warm slate, terracotta",
            0xD97757, 0xC6613F,
            // Dark: warm charcoal. Light: Claude's ivory.
            new Neutrals(0x1F1E1D, 0x262624, 0x171615, 0x3A3935, 0x2E2D2A, 0xE8E6DC, 0xB7B5A9, 0x8A887E),
            new Neutrals(0xFAF9F5, 0xF0EEE6, 0xE8E6DC, 0xD6D3C8, 0xE3E0D6, 0x1F1E1D, 0x5E5D59, 0x87867F)),
    BLUE("Blue", "Cool and crisp, the classic BlockDesigner look",
            0x7C9CFF, 0x3D6CF0,
            null, null),
    GREEN("Green", "Forest greens over mossy slate",
            0x46C46E, 0x238A4A,
            new Neutrals(0x0E1511, 0x151E18, 0x08100B, 0x2A382F, 0x1E2922, 0xD2DDD5, 0x92A399, 0x6A7B70),
            new Neutrals(0xFBFDFB, 0xF1F6F2, 0xE6EEE8, 0xCCD9CF, 0xDEE7E0, 0x16221A, 0x4F6155, 0x75877B)),
    RED("Red", "Bold crimson on deep graphite",
            0xF2555A, 0xC8363B,
            new Neutrals(0x151012, 0x1D1618, 0x0D090A, 0x3A2C30, 0x2A2023, 0xE2D6D8, 0xA8969A, 0x7D6C70),
            new Neutrals(0xFDFBFB, 0xF8F1F2, 0xF0E6E7, 0xDDCDD0, 0xE9DDDF, 0x241719, 0x654D51, 0x8C7478)),
    ORANGE("Orange", "Energetic amber and copper",
            0xFF8A3D, 0xD9661C,
            new Neutrals(0x15120F, 0x1D1915, 0x0D0B09, 0x3A3129, 0x2A241E, 0xE4DBD2, 0xAA9D90, 0x7F7266),
            new Neutrals(0xFDFBF9, 0xF8F2EC, 0xF0E8DF, 0xDFD1C3, 0xEADFD4, 0x241C15, 0x66574A, 0x8C7D6F)),
    ZEN("Zen", "Calm stone and sage, low contrast for long sessions",
            0x8FAE8B, 0x5F7F5B,
            new Neutrals(0x1A1B19, 0x20221F, 0x141513, 0x353832, 0x292B27, 0xD9DBD2, 0xA3A69B, 0x7B7E74),
            new Neutrals(0xF4F3EE, 0xECEBE4, 0xE3E2DA, 0xD2D1C7, 0xDEDDD5, 0x2D2F2A, 0x62655D, 0x898B82));

    /** How dark / light is chosen. */
    public enum Mode {
        DARK("Dark"), LIGHT("Light"), SYSTEM("Match Windows");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /**
     * A theme's greys for one mode.
     *
     * @param bg      main background
     * @param subtle  panels, cards, hover
     * @param inset   wells, text fields
     * @param border  default borders
     * @param muted   quiet borders and dividers
     * @param fg      body text
     * @param fgMuted secondary text
     * @param fgSubtle hints
     */
    public record Neutrals(int bg, int subtle, int inset, int border, int muted, int fg, int fgMuted, int fgSubtle) {
    }

    public final String label;
    public final String description;
    /** Accent as shown on dark backgrounds (highlights, outlines) and the stronger shade for filled buttons. */
    public final int accent, accentStrong;
    private final Neutrals dark, light;

    AppTheme(String label, String description, int accent, int accentStrong, Neutrals dark, Neutrals light) {
        this.label = label;
        this.description = description;
        this.accent = accent;
        this.accentStrong = accentStrong;
        this.dark = dark;
        this.light = light;
    }

    public static AppTheme byId(String id) {
        if (id != null) {
            for (AppTheme t : values()) if (t.name().equalsIgnoreCase(id)) return t;
        }
        return BLUE;
    }

    public static Mode mode(String id) {
        if (id != null) {
            for (Mode m : Mode.values()) if (m.name().equalsIgnoreCase(id)) return m;
        }
        return Mode.DARK;
    }

    /** Whether the mode resolves to dark right now (SYSTEM reads Windows' app setting). */
    public static boolean isDark(Mode m) {
        return switch (m) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> windowsPrefersDark();
        };
    }

    /** Windows' "Choose your app mode" (HKCU ...\Themes\Personalize\AppsUseLightTheme = 0 means dark). */
    public static boolean windowsPrefersDark() {
        try {
            return com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                    "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme") == 0;
        } catch (Throwable t) {
            return true;
        }
    }

    /** The greys for a mode; null for BLUE, which keeps Primer's own. */
    public Neutrals neutrals(boolean darkMode) {
        return darkMode ? dark : light;
    }

    // ---- colours as the UI and the 3D view use them ----------------------------------------------------------

    /** Accent for the current mode: bright on dark, deeper on light so it stays readable. */
    public int accentFor(boolean darkMode) {
        return darkMode ? accent : mix(accent, 0x000000, 0.18);
    }

    /** Swatches for the settings preview: background, panel, accent, text. */
    public List<Color> swatches(boolean darkMode) {
        Neutrals n = neutrals(darkMode);
        int bg = n != null ? n.bg() : darkMode ? 0x0D1117 : 0xFFFFFF;
        int panel = n != null ? n.subtle() : darkMode ? 0x161B22 : 0xF6F8FA;
        int fg = n != null ? n.fg() : darkMode ? 0xC9D1D9 : 0x24292F;
        int muted = n != null ? n.fgMuted() : darkMode ? 0x8B949E : 0x57606A;
        return List.of(rgb(bg), rgb(panel), rgb(accentFor(darkMode)), rgb(fg), rgb(muted));
    }

    /** Sky, ground and grid colours for the 3D view, tinted to the theme. */
    public FrameRequest.Theme viewport(boolean darkMode) {
        FrameRequest.Theme base = darkMode ? FrameRequest.Theme.DARK : FrameRequest.Theme.LIGHT;
        Neutrals n = neutrals(darkMode);
        if (n == null) return base;
        if (darkMode) {
            return new FrameRequest.Theme(mix(n.inset(), base.zenith(), 0.35), mix(n.border(), base.horizon(), 0.25), mix(n.bg(), base.ground(), 0.3),
                    mix(n.fgSubtle(), base.gridMinor(), 0.3), mix(n.fgMuted(), base.gridMajor(), 0.3), base.axisX(), base.axisZ());
        }
        return new FrameRequest.Theme(mix(n.border(), base.zenith(), 0.45), mix(n.bg(), base.horizon(), 0.4), mix(n.inset(), base.ground(), 0.4),
                mix(n.fgSubtle(), base.gridMinor(), 0.35), mix(n.fgMuted(), base.gridMajor(), 0.35), base.axisX(), base.axisZ());
    }

    // ---- stylesheet ------------------------------------------------------------------------------------------

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /** A user-agent stylesheet (data: URL): Primer dark or light plus this theme's colours. */
    public String userAgentStylesheet(boolean darkMode) {
        return CACHE.computeIfAbsent(name() + (darkMode ? "-dark" : "-light"), k -> {
            String primer = darkMode ? new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
                    : new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet();
            String css = read(primer) + "\n" + overrides(darkMode);
            return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        });
    }

    /** The {@code .root} block that recolours Primer (and sets BlockDesigner's own accent variables). */
    String overrides(boolean darkMode) {
        StringBuilder sb = new StringBuilder(".root {\n");
        int acc = accentFor(darkMode);
        // Accent scale 0 (lightest) … 9 (darkest), around the theme's accent.
        for (int i = 0; i <= 9; i++) {
            double t = (i - 4.5) / 4.5;
            int c = t < 0 ? mix(accent, 0xFFFFFF, -t * 0.82) : mix(accentStrong, 0x000000, t * 0.78);
            var(sb, "-color-accent-" + i, hex(c));
        }
        var(sb, "-color-accent-fg", hex(acc));
        var(sb, "-color-accent-emphasis", hex(accentStrong));
        var(sb, "-color-accent-muted", rgba(accent, 0.4));
        var(sb, "-color-accent-subtle", rgba(accent, darkMode ? 0.15 : 0.12));
        var(sb, "-bd-accent", hex(acc));
        var(sb, "-bd-accent-soft", rgba(accent, darkMode ? 0.16 : 0.14));
        Neutrals n = neutrals(darkMode);
        if (n != null) {
            var(sb, "-color-bg-default", hex(n.bg()));
            var(sb, "-color-bg-overlay", hex(darkMode ? n.subtle() : n.bg()));
            var(sb, "-color-bg-subtle", hex(n.subtle()));
            var(sb, "-color-bg-inset", hex(n.inset()));
            var(sb, "-color-border-default", hex(n.border()));
            var(sb, "-color-border-muted", hex(n.muted()));
            var(sb, "-color-border-subtle", hex(mix(n.muted(), n.bg(), 0.4)));
            var(sb, "-color-fg-default", hex(n.fg()));
            var(sb, "-color-fg-muted", hex(n.fgMuted()));
            var(sb, "-color-fg-subtle", hex(n.fgSubtle()));
            var(sb, "-color-shadow-default", hex(darkMode ? n.inset() : mix(n.border(), 0x000000, 0.2)));
            var(sb, "-color-neutral-muted", rgba(n.fgSubtle(), 0.4));
            var(sb, "-color-neutral-subtle", rgba(n.fgSubtle(), 0.1));
            // Base scale 0 (lightest) … 9 (darkest), from the theme's text and background.
            int light = darkMode ? mix(n.fg(), 0xFFFFFF, 0.5) : 0xFFFFFF;
            int darkest = darkMode ? n.bg() : n.fg();
            for (int i = 0; i <= 9; i++) var(sb, "-color-base-" + i, hex(mix(light, darkest, i / 9.0)));
        }
        return sb.append("}\n").toString();
    }

    private static void var(StringBuilder sb, String name, String value) {
        sb.append("  ").append(name).append(": ").append(value).append(";\n");
    }

    private static String read(String resource) {
        String path = resource.startsWith("/") ? resource : "/" + resource;
        try (InputStream in = AppTheme.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing theme stylesheet " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int mix(int a, int b, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    private static String rgba(int rgb, double a) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)", (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    private static Color rgb(int rgb) {
        return Color.rgb((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }
}
