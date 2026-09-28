package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.ImageData;
import io.blockdesigner.plugin.ObjectHandle;
import io.blockdesigner.plugin.SceneObject;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;
import javafx.scene.control.MenuItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/** One reference image in the scene: its {@link ReferenceSettings} and the decoded picture. */
final class ReferenceImage implements SceneObject {
    /** Drawn while the picture is missing, so the plane can still be found, moved and replaced. */
    private static final ImageData MISSING = new ImageData(1, 1, new int[]{0xFF8A8F99});

    private final ReferencePlanes feature;
    private final ReferenceType type;
    private ReferenceSettings settings = ReferenceSettings.DEFAULT;
    /** The picture decoded from {@link #decodedBlob}; re-read when the settings point at another blob (replace, undo). */
    private ImageData image;
    private String decodedBlob;
    private boolean unreadable;

    ReferenceImage(ReferencePlanes feature, ReferenceType type) {
        this.feature = feature;
        this.type = type;
    }

    ReferenceSettings settings() {
        return settings;
    }

    /** Sets the state straight away (no undo), with the picture if it is already decoded. */
    void set(ReferenceSettings s, ImageData decoded) {
        settings = s;
        if (decoded != null) {
            image = decoded;
            decodedBlob = s.blob();
            unreadable = false;
        }
    }

    /** Changes the settings as one undo step. */
    void change(ObjectHandle self, String label, UnaryOperator<ReferenceSettings> f) {
        self.edit(label, () -> settings = f.apply(settings));
    }

    /** The picture, decoded from the project's copy of the file the first time it is needed; null if it can't be. */
    ImageData image() {
        if (!Objects.equals(decodedBlob, settings.blob())) {
            image = null;
            decodedBlob = settings.blob();
            unreadable = false;
        }
        if (image == null && !unreadable && !settings.blob().isEmpty()) {
            try {
                byte[] file = feature.store().blob(settings.blob()).orElseThrow(() -> new IOException("the picture is missing from the project"));
                image = Images.decode(file).image();
            } catch (IOException e) {
                unreadable = true;
                feature.log("Couldn't show " + settings.fileName() + ": " + e.getMessage());
            }
        }
        return image;
    }

    @Override
    public void draw(ViewInfo view, Drawing out) {
        if (!settings.visibleIn(view)) return;
        ImageData img = image();
        if (img != null) {
            out.image(img, settings.corners(), settings.uv(), settings.opacity(), settings.depth());
            return;
        }
        // No picture: a grey card with a cross, so there is still something to click.
        Vec3[] c = settings.corners();
        out.image(MISSING, c, new double[]{0, 1, 1, 1, 1, 0, 0, 0}, 0.35, settings.depth());
        out.line(c[0], c[2], 0xFFE5484D);
        out.line(c[1], c[3], 0xFFE5484D);
    }

    @Override
    public String description(ObjectHandle self) {
        ReferenceSettings s = settings;
        List<String> parts = new ArrayList<>();
        if (!s.fileName().isEmpty()) parts.add(s.fileName());
        parts.add(s.width() + "×" + s.height());
        if (s.opacity() < 1) parts.add(Math.round(s.opacity() * 100) + "%");
        if (s.showIn() != ReferenceSettings.ShowIn.ALL) parts.add(s.showIn().label.toLowerCase(Locale.ROOT));
        if (image() == null && !s.blob().isEmpty()) parts.add("picture missing");
        return String.join(" · ", parts);
    }

    @Override
    public List<MenuItem> menu(ObjectHandle self) {
        return ReferenceMenu.items(self, this, type);
    }

    @Override
    public byte[] save() {
        return settings.encode();
    }

    @Override
    public void load(byte[] data) throws IOException {
        settings = ReferenceSettings.decode(data);
    }

    @Override
    public Set<String> blobs() {
        return settings.blob().isEmpty() ? Set.of() : Set.of(settings.blob());
    }
}
