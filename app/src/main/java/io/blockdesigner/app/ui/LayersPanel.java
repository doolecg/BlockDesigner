package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * The layer list: every loaded schematic and every new one. Shown top layer first (like image editors); supports
 * visibility, lock and ghost toggles, rename, drag reordering, multi-select and a context menu.
 */
public final class LayersPanel extends VBox {
    private final Workspace ws;
    private final ListView<Layer> list = new ListView<>();
    private boolean syncing;

    /** Callbacks the panel needs from the main window. */
    public record Actions(Runnable importSchematic, Consumer<Layer> exportLayer, Consumer<Layer> focusLayer) {
    }

    public LayersPanel(Workspace ws, Actions actions) {
        this.ws = ws;
        getStyleClass().add("side-panel");

        Label title = new Label("Layers");
        title.getStyleClass().add("panel-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = iconButton(Feather.PLUS, "New empty layer", () -> {
            Layer l = new Layer(uniqueName("Layer"), new Structure());
            ws.activeLayerProperty().get();
            ws.editor().addLayer(l);
            ws.selectedLayers().setAll(l);
        });
        Button imp = iconButton(Feather.DOWNLOAD, "Import schematic…", actions.importSchematic());
        HBox header = new HBox(6, title, spacer, imp, add);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");

        list.getStyleClass().add("layer-list");
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(v -> new LayerCell(actions));
        list.setPlaceholder(placeholder());
        VBox.setVgrow(list, Priority.ALWAYS);

        // Scene order is bottom-first; show top-first.
        ws.layers().addListener((ListChangeListener<Layer>) c -> refresh());
        ws.activeLayerProperty().addListener((o, a, b) -> syncSelectionFromWorkspace());
        ws.selectedLayers().addListener((ListChangeListener<Layer>) c -> syncSelectionFromWorkspace());
        list.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Layer>) c -> {
            if (syncing) return;
            List<Layer> sel = List.copyOf(list.getSelectionModel().getSelectedItems());
            syncing = true;
            ws.selectedLayers().setAll(sel);
            Layer focused = list.getSelectionModel().getSelectedItem();
            if (focused != null) ws.scene().setActive(focused);
            syncing = false;
        });
        ws.scene().addListener(new Scene.Listener() {
            @Override
            public void layerPropertiesChanged(Layer layer) {
                list.refresh();
            }

            @Override
            public void blocksChanged(Layer layer, Box localBox) {
                list.refresh();
            }
        });

        getChildren().addAll(header, list);
    }

    private Region placeholder() {
        Label l = new Label("No layers yet.\nImport a schematic, drop one here,\nor start building in Build mode (B).");
        l.getStyleClass().add("placeholder-text");
        l.setWrapText(true);
        return new VBox(l);
    }

    private void refresh() {
        syncing = true;
        List<Layer> reversed = new java.util.ArrayList<>(ws.layers());
        java.util.Collections.reverse(reversed);
        list.getItems().setAll(reversed);
        syncing = false;
        syncSelectionFromWorkspace();
    }

    private void syncSelectionFromWorkspace() {
        if (syncing) return;
        syncing = true;
        list.getSelectionModel().clearSelection();
        List<Layer> sel = ws.selectedLayers().isEmpty() && ws.activeLayerProperty().get() != null
                ? List.of(ws.activeLayerProperty().get()) : ws.selectedLayers();
        for (Layer l : sel) {
            int i = list.getItems().indexOf(l);
            if (i >= 0) list.getSelectionModel().select(i);
        }
        Layer active = ws.activeLayerProperty().get();
        if (active != null) {
            int i = list.getItems().indexOf(active);
            if (i >= 0) list.getFocusModel().focus(i);
        }
        syncing = false;
    }

    private String uniqueName(String base) {
        int n = ws.scene().layers().size() + 1;
        String name;
        do {
            name = base + " " + n++;
        } while (nameTaken(name));
        return name;
    }

    private boolean nameTaken(String name) {
        for (Layer l : ws.scene().layers()) if (l.name().equals(name)) return true;
        return false;
    }

    public static Button iconButton(Feather icon, String tip, Runnable action) {
        Button b = new Button(null, new FontIcon(icon));
        b.getStyleClass().addAll("flat", "icon-button");
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private final class LayerCell extends ListCell<Layer> {
        private final Rectangle swatch = new Rectangle(10, 28);
        private final Label name = new Label();
        private final Label meta = new Label();
        private final TextField editor = new TextField();
        private final ToggleButton eye = toggle(Feather.EYE, Feather.EYE_OFF, "Show / hide");
        private final ToggleButton lock = toggle(Feather.UNLOCK, Feather.LOCK, "Lock");
        private final ToggleButton ghost = toggle(Feather.SQUARE, Feather.LAYERS, "Ghost (translucent)");
        private final VBox text = new VBox(1, name, meta);
        private final HBox root;

        LayerCell(Actions actions) {
            swatch.setArcWidth(6);
            swatch.setArcHeight(6);
            name.getStyleClass().add("layer-name");
            meta.getStyleClass().add("layer-meta");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            root = new HBox(8, swatch, text, spacer, ghost, lock, eye);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getStyleClass().add("layer-cell");
            // Let the list's width decide the row width; long names/metadata truncate with an ellipsis.
            setPrefWidth(0);
            text.setMinWidth(0);
            name.setMinWidth(0);
            meta.setMinWidth(0);
            HBox.setHgrow(text, Priority.SOMETIMES);
            swatch.setManaged(true);

            eye.setOnAction(e -> modify("Toggle visibility", l -> l.setVisible(!eye.isSelected())));
            lock.setOnAction(e -> modify("Toggle lock", l -> l.setLocked(lock.isSelected())));
            ghost.setOnAction(e -> modify("Toggle ghost", l -> l.setGhost(ghost.isSelected())));

            editor.setOnAction(e -> commitRename());
            editor.focusedProperty().addListener((o, was, is) -> {
                if (!is) commitRename();
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && getItem() != null) startRename();
            });

            // Drag to reorder
            setOnDragDetected(e -> {
                if (getItem() == null) return;
                var db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString("layer:" + getItem().id());
                db.setContent(cc);
                e.consume();
            });
            setOnDragOver(e -> {
                if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith("layer:")) e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            });
            setOnDragDropped(e -> {
                String s = e.getDragboard().getString();
                if (s == null || !s.startsWith("layer:")) return;
                ws.scene().find(s.substring(6)).ifPresent(dragged -> {
                    int targetListIndex = getItem() == null ? list.getItems().size() - 1 : list.getItems().indexOf(getItem());
                    int sceneIndex = ws.scene().layers().size() - 1 - targetListIndex;
                    ws.editor().reorderLayer(dragged, sceneIndex);
                });
                e.setDropCompleted(true);
                e.consume();
            });

            MenuItem rename = new MenuItem("Rename", new FontIcon(Feather.EDIT_2));
            rename.setOnAction(e -> startRename());
            MenuItem focus = new MenuItem("Focus camera", new FontIcon(Feather.CROSSHAIR));
            focus.setOnAction(e -> actions.focusLayer().accept(getItem()));
            MenuItem dup = new MenuItem("Duplicate", new FontIcon(Feather.COPY));
            dup.setOnAction(e -> {
                Layer copy = getItem().duplicate(getItem().name() + " copy");
                ws.editor().addLayer(copy, ws.scene().indexOf(getItem()) + 1);
            });
            MenuItem mergeDown = new MenuItem("Merge down", new FontIcon(Feather.ARROW_DOWN));
            mergeDown.setOnAction(e -> {
                int idx = ws.scene().indexOf(getItem());
                if (idx > 0) ws.editor().mergeDown(getItem(), ws.scene().layers().get(idx - 1));
            });
            MenuItem mergeSel = new MenuItem("Merge selected", new FontIcon(Feather.GIT_MERGE));
            mergeSel.setOnAction(e -> mergeSelected());
            MenuItem export = new MenuItem("Export this layer…", new FontIcon(Feather.SHARE));
            export.setOnAction(e -> actions.exportLayer().accept(getItem()));
            MenuItem delete = new MenuItem("Delete", new FontIcon(Feather.TRASH_2));
            delete.setOnAction(e -> ws.editor().removeLayer(getItem()));
            ContextMenu menu = new ContextMenu(rename, focus, dup, new SeparatorMenuItem(), mergeDown, mergeSel,
                    new SeparatorMenuItem(), export, new SeparatorMenuItem(), delete);
            menu.setOnShowing(e -> {
                int idx = getItem() == null ? -1 : ws.scene().indexOf(getItem());
                mergeDown.setDisable(idx <= 0);
                mergeSel.setDisable(ws.selectedLayers().size() < 2);
            });
            setContextMenu(menu);
        }

        private ToggleButton toggle(Feather off, Feather on, String tip) {
            ToggleButton t = new ToggleButton(null, new FontIcon(off));
            t.getStyleClass().addAll("flat", "icon-toggle");
            t.setTooltip(new Tooltip(tip));
            t.selectedProperty().addListener((o, a, sel) -> t.setGraphic(new FontIcon(sel ? on : off)));
            return t;
        }

        private void modify(String label, Consumer<Layer> change) {
            Layer l = getItem();
            if (l != null) ws.editor().modifyLayer(l, label + " " + l.name(), null, change);
        }

        private void startRename() {
            editor.setText(getItem().name());
            text.getChildren().setAll(editor);
            editor.requestFocus();
            editor.selectAll();
        }

        private void commitRename() {
            if (!text.getChildren().contains(editor)) return;
            Layer l = getItem();
            String n = editor.getText().strip();
            text.getChildren().setAll(name, meta);
            if (l != null && !n.isEmpty() && !n.equals(l.name())) ws.editor().modifyLayer(l, "Rename layer", null, x -> x.setName(n));
        }

        private void mergeSelected() {
            List<Layer> sel = ws.scene().layers().stream().filter(ws.selectedLayers()::contains).toList();
            if (sel.size() < 2) return;
            Layer bottom = sel.getFirst();
            for (int i = sel.size() - 1; i >= 1; i--) ws.editor().mergeDown(sel.get(i), bottom);
        }

        @Override
        protected void updateItem(Layer l, boolean empty) {
            super.updateItem(l, empty);
            if (empty || l == null) {
                setGraphic(null);
                return;
            }
            name.setText(l.name());
            String size = l.structure().bounds().map(b -> b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ()).orElse("empty");
            meta.setText(String.format("%,d blocks · %s · @ %s", l.structure().blockCount(), size, l.offset()));
            swatch.setFill(javafx.scene.paint.Color.rgb((l.color() >> 16) & 255, (l.color() >> 8) & 255, l.color() & 255));
            eye.setSelected(!l.visible());
            lock.setSelected(l.locked());
            ghost.setSelected(l.ghost());
            root.setOpacity(l.visible() ? 1 : 0.55);
            setGraphic(root);
        }
    }
}
