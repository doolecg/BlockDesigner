package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.worldgen.DatapackExporter;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Configures and writes a worldgen data pack from the current layers. */
public final class DatapackDialog extends Dialog<ButtonType> {
    private static final Map<String, List<String>> BIOME_PRESETS = new LinkedHashMap<>();

    static {
        BIOME_PRESETS.put("Anywhere in the Overworld", List.of("#minecraft:is_overworld"));
        BIOME_PRESETS.put("Plains & meadows", List.of("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"));
        BIOME_PRESETS.put("Forests", List.of("#minecraft:is_forest"));
        BIOME_PRESETS.put("Taiga & snowy", List.of("#minecraft:is_taiga", "minecraft:snowy_plains", "minecraft:snowy_taiga"));
        BIOME_PRESETS.put("Desert & badlands", List.of("minecraft:desert", "#minecraft:is_badlands"));
        BIOME_PRESETS.put("Savanna & jungle", List.of("#minecraft:is_savanna", "#minecraft:is_jungle"));
        BIOME_PRESETS.put("Mountains", List.of("#minecraft:is_mountain"));
        BIOME_PRESETS.put("Beaches & oceans", List.of("#minecraft:is_beach", "#minecraft:is_ocean"));
        BIOME_PRESETS.put("Nether", List.of("#minecraft:is_nether"));
        BIOME_PRESETS.put("Custom…", List.of());
    }

    private enum Source {
        VISIBLE("All visible layers, merged"), VARIANTS("Each visible layer is a random variant"), ACTIVE("Active layer only");

        final String label;

        Source(String l) {
            label = l;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Where to write: a zip file, or straight into a world's datapacks folder. */
    private record Target(String label, Path worldDatapacks) {
        @Override
        public String toString() {
            return label;
        }
    }

    public DatapackDialog(Window owner, Workspace ws, McInstallLocator.Result scan) {
        initOwner(owner);
        setTitle("Worldgen data pack");
        setHeaderText("Make this build generate naturally in new chunks");

        String baseName = slug(ws.projectNameProperty().get());
        TextField namespace = new TextField(slug(ws.settings().author.isBlank() ? "blockdesigner" : ws.settings().author));
        TextField name = new TextField(baseName);
        ComboBox<McVersion> version = new ComboBox<>();
        version.getItems().setAll(McVersion.builtIn().reversed());
        McVersion target = ws.targetVersionProperty().get();
        if (!version.getItems().contains(target)) version.getItems().addFirst(target);
        version.setValue(target);
        ComboBox<Source> source = new ComboBox<>();
        source.getItems().setAll(Source.values());
        source.setValue(Source.VISIBLE);

        ComboBox<String> biomes = new ComboBox<>();
        biomes.getItems().setAll(BIOME_PRESETS.keySet());
        biomes.setValue("Plains & meadows");
        TextField customBiomes = new TextField();
        customBiomes.setPromptText("comma-separated ids or #tags, e.g. minecraft:cherry_grove, #minecraft:is_hill");
        customBiomes.visibleProperty().bind(biomes.valueProperty().isEqualTo("Custom…"));
        customBiomes.managedProperty().bind(customBiomes.visibleProperty());

        ComboBox<DatapackExporter.TerrainAdaptation> terrain = new ComboBox<>();
        terrain.getItems().setAll(DatapackExporter.TerrainAdaptation.values());
        terrain.setValue(DatapackExporter.TerrainAdaptation.BEARD_THIN);
        Spinner<Integer> sink = new Spinner<>(-64, 64, 0);
        sink.setEditable(true);
        Spinner<Integer> spacing = new Spinner<>(2, 4096, 34);
        spacing.setEditable(true);
        Spinner<Integer> separation = new Spinner<>(1, 4095, 8);
        separation.setEditable(true);
        CheckBox air = new CheckBox("Include air (clears terrain inside the structure's box)");
        CheckBox surface = new CheckBox("Place on the terrain surface");
        surface.setSelected(true);

        ComboBox<Target> output = new ComboBox<>();
        output.getItems().add(new Target("Save as .zip…", null));
        for (Target t : worlds(scan)) output.getItems().add(t);
        output.getSelectionModel().selectFirst();

        Label terrainHint = new Label("beard_thin blends the base into terrain (villages); bury sinks it (ancient cities); none places it as-is.");
        terrainHint.setWrapText(true);
        terrainHint.getStyleClass().add("layer-meta");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(9);
        g.setPadding(new Insets(6, 2, 2, 2));
        int r = 0;
        g.addRow(r++, new Label("Namespace"), namespace);
        g.addRow(r++, new Label("Structure name"), name);
        g.addRow(r++, new Label("Minecraft version"), version);
        g.addRow(r++, new Label("Layers"), source);
        g.addRow(r++, new Label("Biomes"), biomes);
        g.add(customBiomes, 1, r++);
        g.addRow(r++, new Label("Terrain"), terrain);
        g.add(terrainHint, 1, r++);
        g.addRow(r++, new Label("Height offset"), sink);
        g.add(surface, 1, r++);
        g.addRow(r++, new Label("Spacing (chunks)"), spacing);
        g.addRow(r++, new Label("Min separation"), separation);
        g.add(air, 1, r++);
        g.addRow(r, new Label("Output"), output);
        GridPane.setHgrow(name, Priority.ALWAYS);
        g.setPrefWidth(600);
        getDialogPane().setContent(g);
        ButtonType exportBtn = new ButtonType("Export", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, exportBtn);

        getDialogPane().lookupButton(exportBtn).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            List<String> biomeList = biomes.getValue().equals("Custom…")
                    ? Arrays.stream(customBiomes.getText().split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList()
                    : BIOME_PRESETS.get(biomes.getValue());
            String ns = namespace.getText().strip().toLowerCase(Locale.ROOT), nm = name.getText().strip().toLowerCase(Locale.ROOT);
            var defaults = DatapackExporter.Options.defaults(ns, nm, version.getValue());
            var opts = new DatapackExporter.Options(ns, nm, "Structures made with BlockDesigner", version.getValue(), biomeList,
                    DatapackExporter.Step.SURFACE_STRUCTURES, terrain.getValue(), surface.isSelected(), sink.getValue(),
                    spacing.getValue(), separation.getValue(), defaults.salt(), 1.0, air.isSelected());
            List<DatapackExporter.Piece> pieces = pieces(ws, source.getValue());
            List<String> errors = DatapackExporter.validate(opts, pieces);
            if (!errors.isEmpty()) {
                e.consume();
                alert(Alert.AlertType.WARNING, "Please fix", String.join("\n", errors));
                return;
            }
            try {
                String where;
                Target t = output.getValue();
                if (t.worldDatapacks() == null) {
                    FileChooser fc = new FileChooser();
                    fc.setTitle("Save data pack");
                    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Data pack (*.zip)", "*.zip"));
                    fc.setInitialFileName(ns + "_" + nm + ".zip");
                    File f = fc.showSaveDialog(getDialogPane().getScene().getWindow());
                    if (f == null) {
                        e.consume();
                        return;
                    }
                    DatapackExporter.writeZip(opts, pieces, f.toPath());
                    where = f.getAbsolutePath() + "\n\nCopy it into <world>/datapacks, then run /reload (new chunks only).";
                } else {
                    Files.createDirectories(t.worldDatapacks());
                    Path zip = t.worldDatapacks().resolve(ns + "_" + nm + ".zip");
                    DatapackExporter.writeZip(opts, pieces, zip);
                    where = zip + "\n\nOpen the world and run /reload; new chunks will generate it.";
                }
                ws.statusProperty().set("Data pack written: " + ns + ":" + nm);
                TextArea cmds = new TextArea(DatapackExporter.testCommands(opts));
                cmds.setEditable(false);
                cmds.setPrefRowCount(2);
                Alert done = new Alert(Alert.AlertType.INFORMATION);
                done.initOwner(getDialogPane().getScene().getWindow());
                done.setHeaderText("Data pack exported");
                done.setContentText("Written to " + where + "\n\nTest it in-game with:");
                done.getDialogPane().setExpandableContent(cmds);
                done.getDialogPane().setExpanded(true);
                done.showAndWait();
            } catch (IOException | RuntimeException ex) {
                e.consume();
                alert(Alert.AlertType.ERROR, "Export failed", ex.getMessage());
            }
        });
    }

    private static List<DatapackExporter.Piece> pieces(Workspace ws, Source src) {
        List<Layer> visible = ws.scene().layers().stream().filter(Layer::visible).toList();
        return switch (src) {
            case VISIBLE -> List.of(new DatapackExporter.Piece("main", Scene.flatten(visible), 1));
            case ACTIVE -> ws.activeLayerProperty().get() == null ? List.of()
                    : List.of(new DatapackExporter.Piece(slug(ws.activeLayerProperty().get().name()), Scene.flatten(List.of(ws.activeLayerProperty().get())), 1));
            case VARIANTS -> {
                List<DatapackExporter.Piece> out = new ArrayList<>();
                int i = 1;
                for (Layer l : visible) {
                    // Each variant is normalised on its own so they all sit on the same origin.
                    var s = Scene.flatten(List.of(l));
                    s.normalizeToOrigin();
                    out.add(new DatapackExporter.Piece(slug(l.name()) + "_" + i++, s, 1));
                }
                yield out;
            }
        };
    }

    /** Worlds found in launcher instances and the official launcher. */
    private static List<Target> worlds(McInstallLocator.Result scan) {
        List<Target> out = new ArrayList<>();
        List<Path> gameDirs = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        if (appData != null) gameDirs.add(Path.of(appData, ".minecraft"));
        if (scan != null) scan.instances().forEach(i -> gameDirs.add(i.gameDir()));
        for (Path gd : gameDirs) {
            Path saves = gd.resolve("saves");
            if (!Files.isDirectory(saves)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(saves, Files::isDirectory)) {
                for (Path w : ds) {
                    String inst = gd.getFileName().toString().equals(".minecraft") && gd.getParent() != null
                            ? gd.getParent().getFileName().toString() : gd.getFileName().toString();
                    out.add(new Target("World “" + w.getFileName() + "” (" + inst + ")", w.resolve("datapacks")));
                }
            } catch (IOException ignored) {
                // unreadable saves folder
            }
        }
        return out;
    }

    static String slug(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
        return r.isEmpty() ? "structure" : r;
    }

    private void alert(Alert.AlertType type, String header, String msg) {
        Alert a = new Alert(type);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setHeaderText(header);
        a.setContentText(msg);
        a.showAndWait();
    }
}
