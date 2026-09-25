package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.render.ViewportRenderer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A strip of build snapshots, one per assistant turn plus manual checkpoints. Clicking a snapshot restores it
 * (as one undoable step), so the user can step back through iterations or branch from an earlier one.
 */
public final class IterationTimeline extends HBox {
    private static final int THUMB_W = 176, THUMB_H = 110;

    private record Snapshot(String label, List<Layer> layers, String time) {
    }

    private final Workspace ws;
    private final ViewportPane viewport;
    private final HBox strip = new HBox(8);
    private final List<Snapshot> snapshots = new ArrayList<>();

    public IterationTimeline(Workspace ws, ViewportPane viewport) {
        this.ws = ws;
        this.viewport = viewport;
        getStyleClass().add("timeline");
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);

        Button checkpoint = new Button(null, new FontIcon(Feather.BOOKMARK));
        checkpoint.getStyleClass().addAll("flat", "icon-button");
        checkpoint.setTooltip(new Tooltip("Save a checkpoint of the current build"));
        checkpoint.setOnAction(e -> snapshot("Checkpoint"));
        Label title = new Label("Iterations");
        title.getStyleClass().add("panel-title");
        VBox head = new VBox(4, title, checkpoint);
        head.setAlignment(Pos.CENTER_LEFT);

        strip.setAlignment(Pos.CENTER_LEFT);
        Label empty = new Label("Snapshots of each assistant turn appear here.");
        empty.getStyleClass().add("layer-meta");
        strip.getChildren().add(empty);
        ScrollPane sp = new ScrollPane(strip);
        sp.setFitToHeight(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("edge-to-edge");
        HBox.setHgrow(sp, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(head, sp);
    }

    public boolean hasSnapshots() {
        return !snapshots.isEmpty();
    }

    /** Records the current build with a thumbnail. Call on the FX thread. */
    public void snapshot(String label) {
        if (ws.scene().layers().isEmpty()) return;
        List<Layer> copy = new ArrayList<>();
        for (Layer l : ws.scene().layers()) {
            if (viewport.isPlacing() && l.ghost()) continue;
            copy.add(exactCopy(l));
        }
        Snapshot s = new Snapshot(label, copy, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        snapshots.add(s);
        if (strip.getChildren().size() == 1 && strip.getChildren().getFirst() instanceof Label) strip.getChildren().clear();

        ImageView iv = new ImageView();
        iv.setFitWidth(THUMB_W);
        iv.setFitHeight(THUMB_H);
        iv.setSmooth(true);
        Label caption = new Label((snapshots.size()) + ". " + label);
        caption.getStyleClass().add("timeline-caption");
        caption.setMaxWidth(THUMB_W);
        Label time = new Label(s.time());
        time.getStyleClass().add("layer-meta");
        StackPane frame = new StackPane(iv);
        frame.getStyleClass().add("timeline-thumb");
        VBox item = new VBox(3, frame, caption, time);
        item.getStyleClass().add("timeline-item");
        Tooltip.install(item, new Tooltip("Click to restore this version (Ctrl+Z undoes the restore)"));
        item.setOnMouseClicked(e -> restore(s));
        strip.getChildren().add(item);

        viewport.captureWhenReady(viewport.presetCamera("iso_se"), THUMB_W * 2, THUMB_H * 2).thenAccept(f -> {
            Image img = toImage(f);
            Platform.runLater(() -> iv.setImage(img));
        });
    }

    private void restore(Snapshot s) {
        if (ws.editor().undoStack().inGroup()) {
            viewport.showToast("Wait for the assistant to finish");
            return;
        }
        List<Layer> fresh = new ArrayList<>();
        for (Layer l : s.layers()) fresh.add(exactCopy(l));
        ws.editor().replaceAll(fresh, "Restore “" + s.label() + "”");
        ws.selectedLayers().clear();
        viewport.showToast("Restored “" + s.label() + "” · Ctrl+Z to undo");
    }

    /** Copy that keeps the layer id so the assistant's references stay valid. */
    private static Layer exactCopy(Layer l) {
        Layer c = new Layer(l.id(), l.name(), l.structure().copy());
        c.setOffset(l.offset());
        c.setTransform(l.transform());
        c.setVisible(l.visible());
        c.setLocked(l.locked());
        c.setGhost(l.ghost());
        c.setColor(l.color());
        return c;
    }

    private static Image toImage(ViewportRenderer.Frame f) {
        WritableImage img = new WritableImage(f.width(), f.height());
        int[] px = new int[f.width() * f.height()];
        f.pixels().clear();
        f.pixels().get(px);
        img.getPixelWriter().setPixels(0, 0, f.width(), f.height(), PixelFormat.getIntArgbInstance(), px, 0, f.width());
        return img;
    }
}
