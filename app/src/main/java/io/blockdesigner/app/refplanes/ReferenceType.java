package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.ObjectHandle;
import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.SceneObject;
import io.blockdesigner.plugin.SceneObjectType;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reference images: made from picture files (Layers › Add reference image…, Import, or dropping a picture on the window). */
final class ReferenceType implements SceneObjectType {
    static final String ID = "reference";
    static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp");

    private final ReferencePlanes feature;

    ReferenceType(ReferencePlanes feature) {
        this.feature = feature;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Reference image";
    }

    @Override
    public String badge() {
        return "REFERENCE";
    }

    @Override
    public SceneObject create() {
        return new ReferenceImage(feature, this);
    }

    @Override
    public List<String> extensions() {
        return EXTENSIONS;
    }

    @Override
    public void open(Path file, ViewInfo view) throws IOException {
        add(file, view);
    }

    /** A picture read from a file and kept in the project. */
    record Loaded(String blob, String fileName, Images.Decoded decoded) {
    }

    /** Reads a picture file and stores it in the project; fails with a message for the user if it isn't one. */
    Loaded load(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Images.Decoded d = Images.decode(bytes);
        return new Loaded(feature.store().storeBlob(bytes), file.getFileName().toString(), d);
    }

    /**
     * Adds a reference image of {@code file} at the view's target, facing the camera, set up as the settings say; in an
     * orthographic axis view it shows only in that view (unless the settings say otherwise), like Blender's "align to
     * view" references.
     */
    ObjectHandle add(Path file, ViewInfo view) throws IOException {
        Loaded l = load(file);
        NewPictures n = feature.newPictures();
        ReferenceImage ref = new ReferenceImage(feature, this);
        ref.set(ReferenceSettings.DEFAULT.withImage(l.blob(), l.fileName(), l.decoded().width(), l.decoded().height())
                .withShowIn(n.onlyInItsView() ? ReferenceSettings.showInFor(view) : ReferenceSettings.ShowIn.ALL)
                .withOpacity(n.opacity()).withDepth(n.depth()), l.decoded().image());
        String name = l.fileName().replaceFirst("\\.[^.]*$", "");
        Pose at = ReferenceSettings.placement(view);
        at = new Pose(at.position(), at.rotation(), new Vec3(n.height(), n.height(), n.height()));
        ObjectHandle h = feature.store().add(ReferencePlanes.ID + "/" + ID, name, at, ref);
        feature.toast("Added " + name + " · G / R move and turn it · right-click for its options");
        return h;
    }

    ViewInfo view() {
        return feature.store().view();
    }

    void toast(String message) {
        feature.toast(message);
    }

    ReferencePlanes.Ui ui() {
        return feature.ui();
    }

    /** Add reference image…: pick pictures and add each. */
    void chooseAndAdd() {
        List<File> files = chooser("Add reference images").showOpenMultipleDialog(feature.ui().owner());
        if (files == null) return;
        for (File f : files) {
            try {
                add(f.toPath(), view());
            } catch (IOException e) {
                feature.toast("✖ " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    static FileChooser chooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures (PNG, JPEG, GIF, BMP)",
                EXTENSIONS.stream().map(e -> "*." + e).toList()));
        return fc;
    }
}
