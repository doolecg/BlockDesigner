package io.blockdesigner.app;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.version.McVersion;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;

/**
 * Shared, observable application state: the scene being edited, loaded assets, the selected block and tool.
 * Touch only from the JavaFX thread.
 */
public final class Workspace {
    public enum ToolKind { SELECT, BUILD, PLACE, ERASE, PAINT, PICK, BOX, MOVE }

    private final Settings settings;
    private final Scene scene = new Scene();
    private final SceneEditor editor = new SceneEditor(scene);
    private final ObjectProperty<BlockAssets> assets = new SimpleObjectProperty<>();
    private final ObjectProperty<McVersion> targetVersion = new SimpleObjectProperty<>(McVersion.latestKnown());
    private final ObjectProperty<BlockState> selectedBlock = new SimpleObjectProperty<>(BlockState.of("stone_bricks"));
    private final ObjectProperty<ToolKind> tool = new SimpleObjectProperty<>(ToolKind.SELECT);
    private final ObjectProperty<Layer> activeLayer = new SimpleObjectProperty<>();
    /** Layers selected in the layer list (for multi-layer moves); always includes the active layer when non-empty. */
    private final ObservableList<Layer> selectedLayers = FXCollections.observableArrayList();
    private final ObservableList<Layer> layers = FXCollections.observableArrayList();
    private final BooleanProperty dark = new SimpleBooleanProperty(true);
    private final StringProperty projectName = new SimpleStringProperty("Untitled");
    private final ObjectProperty<Path> projectFile = new SimpleObjectProperty<>();
    private final StringProperty status = new SimpleStringProperty("");

    public Workspace(Settings settings) {
        this.settings = settings;
        dark.set(settings.darkTheme);
        scene.addListener(new Scene.Listener() {
            @Override
            public void layerAdded(Layer layer, int index) {
                layers.setAll(scene.layers());
            }

            @Override
            public void layerRemoved(Layer layer) {
                layers.setAll(scene.layers());
                selectedLayers.remove(layer);
            }

            @Override
            public void layersReordered() {
                layers.setAll(scene.layers());
            }

            @Override
            public void activeLayerChanged(Layer layer) {
                activeLayer.set(layer);
            }
        });
    }

    public Settings settings() {
        return settings;
    }

    public Scene scene() {
        return scene;
    }

    public SceneEditor editor() {
        return editor;
    }

    public ObjectProperty<BlockAssets> assetsProperty() {
        return assets;
    }

    public BlockAssets assets() {
        return assets.get();
    }

    public ObjectProperty<McVersion> targetVersionProperty() {
        return targetVersion;
    }

    public ObjectProperty<BlockState> selectedBlockProperty() {
        return selectedBlock;
    }

    public ObjectProperty<ToolKind> toolProperty() {
        return tool;
    }

    public ObjectProperty<Layer> activeLayerProperty() {
        return activeLayer;
    }

    public ObservableList<Layer> selectedLayers() {
        return selectedLayers;
    }

    /** Scene layers, bottom first (mirrors {@link Scene#layers()}). */
    public ObservableList<Layer> layers() {
        return layers;
    }

    public BooleanProperty darkProperty() {
        return dark;
    }

    public StringProperty projectNameProperty() {
        return projectName;
    }

    public ObjectProperty<Path> projectFileProperty() {
        return projectFile;
    }

    public StringProperty statusProperty() {
        return status;
    }

    /** Layers that a nudge should move: the multi-selection, or just the active layer. */
    public java.util.List<Layer> nudgeTargets() {
        if (selectedLayers.size() > 1) return java.util.List.copyOf(selectedLayers);
        Layer a = activeLayer.get();
        return a == null ? java.util.List.of() : java.util.List.of(a);
    }
}
