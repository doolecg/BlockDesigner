package io.blockdesigner.app.refplanes;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.plugins.SceneObjectStore;
import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reference images built into the app: projects saved with the Reference Planes plugin open with their pictures, a
 * picture added starts out as the settings say, and the plugin's settings and key carry over.
 */
class ReferencePlanesTest {
    @TempDir
    Path dir;

    private final List<String> toasts = new ArrayList<>();
    private final ReferencePlanes.Ui ui = new ReferencePlanes.Ui() {
        @Override
        public Window owner() {
            return null;
        }

        @Override
        public void toast(String message) {
            toasts.add(message);
        }

        @Override
        public void style(Dialog<?> dialog) {
        }
    };

    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /** A project's object entries as the Reference Planes plugin saved them: one picture, 2:1, shown only from the front. */
    private static Map<String, byte[]> savedByThePlugin() throws Exception {
        byte[] picture = png(40, 20);
        String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(picture));
        ReferenceSettings s = ReferenceSettings.DEFAULT.withImage(key, "sketch.png", 40, 20).withOpacity(0.5)
                .withShowIn(ReferenceSettings.ShowIn.FRONT);
        String index = """
                {"format":1,"objects":[{"id":"a1","type":"reference-planes/reference","name":"sketch","visible":true,"locked":false,
                "position":[1,2,3],"rotation":[0,90,0],"scale":[16,16,16],"data":"objects/a1.bin","blobs":["%s"]}]}""".formatted(key);
        Map<String, byte[]> extras = new LinkedHashMap<>();
        extras.put("objects/objects.json", index.getBytes(StandardCharsets.UTF_8));
        extras.put("objects/a1.bin", s.encode());
        extras.put("objects/blobs/" + key, picture);
        return extras;
    }

    @Test
    void projectsSavedWithThePluginOpenWithTheirPictures() throws Exception {
        // Opened before the type is there (as at startup) and after it.
        for (boolean registerFirst : new boolean[]{false, true}) {
            SceneObjectStore store = new SceneObjectStore(null);
            if (registerFirst) new ReferencePlanes(store, new Settings(), ui);
            store.load(savedByThePlugin());
            if (!registerFirst) new ReferencePlanes(store, new Settings(), ui);
            assertThat(store.list()).hasSize(1);
            SceneObjectStore.Entry e = store.list().getFirst();
            assertThat(e.typeKey()).isEqualTo("reference-planes/reference");
            assertThat(e.badge()).isEqualTo("REFERENCE");
            assertThat(e.pose().position()).isEqualTo(new Vec3(1, 2, 3));
            ReferenceImage ref = (ReferenceImage) e.object();
            assertThat(ref.settings().opacity()).isEqualTo(0.5);
            assertThat(ref.settings().showIn()).isEqualTo(ReferenceSettings.ShowIn.FRONT);
            assertThat(ref.image()).as("the picture is read from the project").isNotNull();
            assertThat(e.description()).isEqualTo("sketch.png · 40×20 · 50% · front only");
            // And saved again the same way, picture included.
            Map<String, byte[]> saved = store.save();
            assertThat(new String(saved.get("objects/objects.json"), StandardCharsets.UTF_8)).contains("\"reference-planes/reference\"");
            assertThat(saved.keySet()).anyMatch(k -> k.startsWith("objects/blobs/"));
        }
    }

    @Test
    void aPictureStartsOutAsTheSettingsSay() throws Exception {
        Settings settings = new Settings();
        settings.newReferenceImages = new LinkedHashMap<>(Map.of("height", "8", "opacity", "0.5", "draw", "In front of blocks"));
        SceneObjectStore store = new SceneObjectStore(null);
        new ReferencePlanes(store, settings, ui);
        ViewInfo front = new ViewInfo(true, Optional.of(ViewInfo.Side.FRONT), new Vec3(0, 0, 50), new Vec3(0, 0, -1), new Vec3(3, 4, 5));
        store.setView(() -> front);
        Path file = dir.resolve("castle.png");
        Files.write(file, png(30, 10));

        var types = store.typesFor(file);
        assertThat(types).hasSize(1);
        types.getFirst().type().open(file, front);

        SceneObjectStore.Entry e = store.list().getFirst();
        assertThat(e.name()).isEqualTo("castle");
        assertThat(e.pose().scale()).isEqualTo(new Vec3(8, 8, 8));
        assertThat(e.pose().position()).isEqualTo(new Vec3(3, 4, 5));
        ReferenceSettings s = ((ReferenceImage) e.object()).settings();
        assertThat(s.opacity()).isEqualTo(0.5);
        assertThat(s.depth()).isEqualTo(Drawing.Depth.IN_FRONT);
        assertThat(s.showIn()).as("added in the front view: shown only there").isEqualTo(ReferenceSettings.ShowIn.FRONT);
        assertThat(s.aspect()).isEqualTo(3);
        assertThat(toasts).singleElement().asString().startsWith("Added castle");

        // A change in Settings applies to the next picture straight away.
        ReferencePlanes.saveSettings(settings, ReferencePlanes.settingsValues(settings).with("onlyInItsView", false));
        types.getFirst().type().open(file, front);
        assertThat(((ReferenceImage) store.list().getFirst().object()).settings().showIn()).isEqualTo(ReferenceSettings.ShowIn.ALL);
    }

    @Test
    void thePluginsSettingsAndKeyCarryOver() {
        Settings s = new Settings();
        s.pluginOptions.put("reference-planes/settings/tab", new LinkedHashMap<>(Map.of("height", "24", "draw", "Behind blocks")));
        s.pluginOptions.put("palette-tools/settings/tab", new LinkedHashMap<>(Map.of("x", "1")));
        s.pluginActionKeys.put("reference-planes/Add reference image…", "Shift+F6");
        s.pluginActionKeys.put("palette-tools/Something", "F9");
        s.disabledPlugins.add("reference-planes");
        s.pluginPages.put("reference-planes", "overview");

        assertThat(ReferencePlanes.importPluginSettings(s, k -> false)).isTrue();
        assertThat(s.newReferenceImages).containsEntry("height", "24").containsEntry("draw", "Behind blocks");
        NewPictures n = NewPictures.of(ReferencePlanes.settingsValues(s));
        assertThat(n.height()).isEqualTo(24);
        assertThat(n.depth()).isEqualTo(Drawing.Depth.BEHIND_BLOCKS);
        assertThat(s.keybinds).containsEntry(ReferencePlanes.KEYBIND, "Shift+F6|");
        assertThat(s.pluginOptions).containsOnlyKeys("palette-tools/settings/tab");
        assertThat(s.pluginActionKeys).containsOnlyKeys("palette-tools/Something");
        assertThat(s.disabledPlugins).isEmpty();
        assertThat(s.pluginPages).isEmpty();
        // Nothing left to do the next time.
        assertThat(ReferencePlanes.importPluginSettings(s, k -> false)).isFalse();
    }

    @Test
    void keysTheAppUsesOrAKeyAlreadyGivenAreNotTaken() {
        Settings s = new Settings();
        s.pluginActionKeys.put("reference-planes/Add reference image…", "H");
        ReferencePlanes.importPluginSettings(s, "H"::equals);
        assertThat(s.keybinds).as("H hides layers; the plugin's H never ran").doesNotContainKey(ReferencePlanes.KEYBIND);
        assertThat(s.pluginActionKeys).isEmpty();

        Settings t = new Settings();
        t.keybinds.put(ReferencePlanes.KEYBIND, "F7|");
        t.newReferenceImages.put("height", "4");
        t.pluginActionKeys.put("reference-planes/Add reference image…", "F8");
        t.pluginOptions.put("reference-planes/settings/tab", new LinkedHashMap<>(Map.of("height", "30")));
        ReferencePlanes.importPluginSettings(t, k -> false);
        assertThat(t.keybinds).containsEntry(ReferencePlanes.KEYBIND, "F7|");
        assertThat(t.newReferenceImages).containsEntry("height", "4");

        Settings none = new Settings();
        none.pluginActionKeys.put("reference-planes/Add reference image…", "");
        ReferencePlanes.importPluginSettings(none, k -> false);
        assertThat(none.keybinds).doesNotContainKey(ReferencePlanes.KEYBIND);
    }
}
