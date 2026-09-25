package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.worldgen.DatapackExporter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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

    /** How a layer takes part in a village; SKIP leaves it out. */
    private enum VillageRole {
        BUILDING("Building"), STREET("Street"), START("Centre (start)"), SKIP("Leave out");

        final String label;

        VillageRole(String l) {
            label = l;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String AUTO = "Auto (door side)";

    /** One layer row in the village table. */
    private record Row(Layer layer, ComboBox<VillageRole> role, Spinner<Integer> weight, ComboBox<String> entrance) {
    }

    private static final List<String> STREET_BLOCKS = List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:cobblestone",
            "minecraft:mossy_cobblestone", "minecraft:stone_bricks", "minecraft:packed_mud", "minecraft:smooth_sandstone",
            "minecraft:deepslate_tiles", "minecraft:spruce_planks");

    /** Where to write: a zip file, or straight into a world's datapacks folder. */
    private record Target(String label, Path worldDatapacks) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Vanilla structure sets a structure can keep its distance from. */
    private static final Map<String, String> AVOID = new LinkedHashMap<>();

    static {
        AVOID.put("Nothing", null);
        AVOID.put("Villages", "minecraft:villages");
        AVOID.put("Pillager outposts", "minecraft:pillager_outposts");
        AVOID.put("Desert pyramids", "minecraft:desert_pyramids");
        AVOID.put("Jungle temples", "minecraft:jungle_temples");
        AVOID.put("Swamp huts", "minecraft:swamp_huts");
        AVOID.put("Igloos", "minecraft:igloos");
        AVOID.put("Ocean monuments", "minecraft:ocean_monuments");
        AVOID.put("Woodland mansions", "minecraft:woodland_mansions");
        AVOID.put("Ancient cities", "minecraft:ancient_cities");
        AVOID.put("Trail ruins", "minecraft:trail_ruins");
        AVOID.put("Shipwrecks", "minecraft:shipwrecks");
        AVOID.put("Ruined portals", "minecraft:ruined_portals");
    }

    /** Biome groups offered as "never spawn in" checkboxes. */
    private static final Map<String, List<String>> EXCLUDE_PRESETS = new LinkedHashMap<>();

    static {
        EXCLUDE_PRESETS.put("Oceans", List.of("#minecraft:is_ocean"));
        EXCLUDE_PRESETS.put("Rivers", List.of("#minecraft:is_river"));
        EXCLUDE_PRESETS.put("Beaches & shores", List.of("#minecraft:is_beach", "minecraft:stony_shore"));
        EXCLUDE_PRESETS.put("Swamps", List.of("minecraft:swamp", "minecraft:mangrove_swamp"));
        EXCLUDE_PRESETS.put("Hills & mountains", List.of("#minecraft:is_mountain", "#minecraft:is_hill", "minecraft:savanna_plateau",
                "minecraft:windswept_savanna", "minecraft:wooded_badlands", "minecraft:eroded_badlands"));
        EXCLUDE_PRESETS.put("Mountain peaks", List.of("minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks", "minecraft:snowy_slopes"));
        EXCLUDE_PRESETS.put("Snowy & icy", List.of("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:snowy_beach",
                "minecraft:frozen_river", "minecraft:grove"));
        EXCLUDE_PRESETS.put("Mushroom fields", List.of("minecraft:mushroom_fields"));
        EXCLUDE_PRESETS.put("Deep dark", List.of("minecraft:deep_dark"));
    }

    /** The groups "Stay away from water" switches on. */
    private static final List<String> WATER_GROUPS = List.of("Oceans", "Rivers", "Beaches & shores", "Swamps");

    private static final List<String> FOUNDATION_BLOCKS = List.of("Match the bottom layer", "minecraft:cobblestone", "minecraft:stone",
            "minecraft:stone_bricks", "minecraft:dirt", "minecraft:cobbled_deepslate", "minecraft:sandstone", "minecraft:mud_bricks");

    // Controls the presets set.
    private final ComboBox<DatapackExporter.HeightMode> heightMode = new ComboBox<>();
    private final Spinner<Integer> offset = new Spinner<>(-128, 320, 0);
    private final Spinner<Integer> minY = new Spinner<>(-64, 320, -40);
    private final Spinner<Integer> maxY = new Spinner<>(-64, 320, 10);
    private final ToggleGroup terrainGroup = new ToggleGroup();
    private final Spinner<Integer> foundation = new Spinner<>(0, 32, 0);
    private final ComboBox<String> foundationBlock = new ComboBox<>();
    private final CheckBox air = new CheckBox("Clear the ground inside the structure");
    private final Slider integrity = new Slider(10, 100, 100);
    private final Slider mossiness = new Slider(0, 100, 0);
    private final ComboBox<DatapackExporter.Step> step = new ComboBox<>();
    private final ComboBox<String> biomes = new ComboBox<>();
    private final Map<String, CheckBox> excludeBoxes = new LinkedHashMap<>();

    public DatapackDialog(Window owner, Workspace ws, McInstallLocator.Result scan) {
        initOwner(owner);
        setTitle("Worldgen data pack");
        setResizable(true);
        getDialogPane().getStylesheets().add(DatapackDialog.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        getDialogPane().getStyleClass().addAll("app-root", ws.darkProperty().get() ? "dark" : "light", "datapack-dialog");
        boolean dark = ws.darkProperty().get();

        // ---- Pack
        String baseName = slug(ws.projectNameProperty().get());
        TextField namespace = new TextField(slug(ws.settings().author.isBlank() ? "blockdesigner" : ws.settings().author));
        TextField name = new TextField(baseName);
        ComboBox<McVersion> version = new ComboBox<>();
        version.getItems().setAll(McVersion.builtIn().reversed());
        McVersion target = ws.targetVersionProperty().get();
        if (!version.getItems().contains(target)) version.getItems().addFirst(target);
        version.setValue(target);

        // ---- Layout: one structure (random variants), or a village grown from a centre along streets.
        ToggleGroup layout = new ToggleGroup();
        ToggleButton single = new ToggleButton("Single structure");
        ToggleButton village = new ToggleButton("Village (jigsaw)");
        single.setToggleGroup(layout);
        village.setToggleGroup(layout);
        single.setSelected(true);
        layout.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
        });
        single.setTooltip(new Tooltip("One template (or random variants) placed as a whole, like a temple or igloo"));
        village.setTooltip(new Tooltip("A centre with streets growing out of it and buildings along them, joined by jigsaw blocks, like vanilla villages"));
        single.getStyleClass().add("left-pill");
        village.getStyleClass().add("right-pill");
        HBox layoutBox = new HBox(0, single, village);
        BooleanBinding isVillage = Bindings.createBooleanBinding(village::isSelected, village.selectedProperty());
        ComboBox<Source> source = new ComboBox<>();
        source.getItems().setAll(Source.values());
        source.setValue(Source.VISIBLE);

        List<Row> rows = new ArrayList<>();
        GridPane table = new GridPane();
        table.setHgap(10);
        table.setVgap(6);
        table.addRow(0, bold("Layer"), bold("Role"), bold("Weight"), bold("Entrance"));
        int ri = 1;
        for (Layer l : ws.scene().layers()) {
            if (!l.visible() || l.structure().blockCount() == 0) continue;
            ComboBox<VillageRole> role = new ComboBox<>();
            role.getItems().setAll(VillageRole.values());
            role.setValue(guessRole(l.name()));
            Spinner<Integer> weight = new Spinner<>(1, 100, 1);
            weight.setEditable(true);
            weight.setPrefWidth(80);
            ComboBox<String> entrance = new ComboBox<>();
            entrance.getItems().setAll(AUTO, "North", "East", "South", "West");
            entrance.setValue(AUTO);
            entrance.disableProperty().bind(role.valueProperty().isNotEqualTo(VillageRole.BUILDING));
            weight.disableProperty().bind(role.valueProperty().isEqualTo(VillageRole.SKIP));
            Label ln = new Label(l.name());
            ln.setMaxWidth(200);
            table.addRow(ri++, ln, role, weight, entrance);
            rows.add(new Row(l, role, weight, entrance));
        }
        ScrollPane tableScroll = new ScrollPane(table);
        tableScroll.setFitToWidth(true);
        tableScroll.setPrefViewportHeight(Math.min(180, 34 * (rows.size() + 1)));
        Spinner<Integer> depth = new Spinner<>(1, DatapackExporter.maxVillageSize(version.getValue()), 6);
        depth.setEditable(true);
        Label depthHint = hint("");
        Runnable depthLimits = () -> {
            int max = DatapackExporter.maxVillageSize(version.getValue());
            ((SpinnerValueFactory.IntegerSpinnerValueFactory) depth.getValueFactory()).setMax(max);
            depthHint.setText("How many pieces (streets, then houses) the village grows away from its centre. Vanilla villages use 6; "
                    + "this version allows up to " + max + ". Larger villages still have to fit within about 116 blocks of the centre.");
        };
        version.valueProperty().addListener((o, a, b) -> depthLimits.run());
        depthLimits.run();
        ComboBox<String> streetBlock = new ComboBox<>();
        streetBlock.getItems().setAll(STREET_BLOCKS);
        streetBlock.setEditable(true);
        streetBlock.setValue(STREET_BLOCKS.getFirst());
        CheckBox genStreets = new CheckBox("Generate streets (straight roads and crossroads in the street block)");
        genStreets.setSelected(true);
        ComboBox<Integer> streetWidth = new ComboBox<>();
        streetWidth.getItems().setAll(3, 5, 7);
        streetWidth.setValue(3);
        HBox streetRow = new HBox(8, streetBlock, new Label("width"), streetWidth);
        streetRow.setAlignment(Pos.CENTER_LEFT);
        GridPane villageGrid = form();
        villageGrid.addRow(0, label("Village size"), depth);
        villageGrid.add(depthHint, 1, 1);
        villageGrid.addRow(2, label("Streets"), streetRow);
        villageGrid.add(genStreets, 1, 3);
        VBox villageBox = new VBox(8, tableScroll, hint("Buildings are turned so their door faces the street; a building's entrance is "
                + "found from its door, or pick a side. Layers that already contain jigsaw blocks are used as they are. With no centre "
                + "layer, a small square in the street block is the centre."), villageGrid);
        villageBox.visibleProperty().bind(isVillage);
        villageBox.managedProperty().bind(isVillage);
        GridPane layoutGrid = form();
        layoutGrid.addRow(0, label("Layout"), layoutBox);
        Label sourceLabel = label("Layers");
        layoutGrid.addRow(1, sourceLabel, source);
        sourceLabel.visibleProperty().bind(isVillage.not());
        sourceLabel.managedProperty().bind(sourceLabel.visibleProperty());
        source.visibleProperty().bind(isVillage.not());
        source.managedProperty().bind(source.visibleProperty());

        // ---- Where it spawns
        ComboBox<String> preset = new ComboBox<>();
        Runnable listPresets = () -> {
            List<String> all = new ArrayList<>(PRESETS.keySet());
            ws.settings().worldgenPresets.keySet().forEach(n -> all.add(SAVED + n));
            preset.getItems().setAll(all);
        };
        listPresets.run();
        preset.setValue("Custom");
        biomes.getItems().setAll(BIOME_PRESETS.keySet());
        biomes.setValue("Plains & meadows");
        TextField customBiomes = new TextField();
        customBiomes.setPromptText("comma-separated ids or #tags, e.g. minecraft:cherry_grove, #minecraft:is_hill");
        customBiomes.visibleProperty().bind(biomes.valueProperty().isEqualTo("Custom…"));
        customBiomes.managedProperty().bind(customBiomes.visibleProperty());
        step.getItems().setAll(DatapackExporter.Step.values());
        step.setValue(DatapackExporter.Step.SURFACE_STRUCTURES);
        step.setConverter(converter(st -> st.label));
        Label stepHint = hint("");
        step.valueProperty().addListener((o, a, b) -> stepHint.setText(b.description));
        stepHint.setText(step.getValue().description);

        heightMode.getItems().setAll(DatapackExporter.HeightMode.values());
        heightMode.setValue(DatapackExporter.HeightMode.SURFACE);
        heightMode.setConverter(converter(h -> h.label));
        for (Spinner<Integer> sp : List.of(offset, minY, maxY, foundation)) {
            sp.setEditable(true);
            sp.setPrefWidth(96);
        }
        Label offsetLabel = label("Raise / sink by");
        Label heightHint = hint("");
        HBox rangeRow = new HBox(8, minY, new Label("to"), maxY);
        rangeRow.setAlignment(Pos.CENTER_LEFT);
        Label rangeLabel = label("Between Y");
        Runnable heightChanged = () -> {
            DatapackExporter.HeightMode m = heightMode.getValue();
            boolean range = m == DatapackExporter.HeightMode.RANGE;
            for (var n : List.of(offset, offsetLabel)) {
                n.setVisible(!range);
                n.setManaged(!range);
            }
            for (var n : List.<javafx.scene.Node>of(rangeRow, rangeLabel)) {
                n.setVisible(range);
                n.setManaged(range);
            }
            offsetLabel.setText(m == DatapackExporter.HeightMode.FIXED ? "Y level" : "Raise / sink by");
            heightHint.setText(m.description + (m == DatapackExporter.HeightMode.FIXED ? "." : range ? "."
                    : ". Negative numbers sink it into the ground, e.g. -3 for a half-buried ruin."));
        };
        heightMode.valueProperty().addListener((o, a, b) -> heightChanged.run());
        heightChanged.run();

        // Blacklist: groups, extra ids, and a one-click "stay away from water".
        DatapackExporter.BiomeTags biomeTags = BiomeTagReader.of(ws.assets());
        javafx.scene.layout.FlowPane excludeRow = new javafx.scene.layout.FlowPane(10, 6);
        excludeRow.setPrefWrapLength(520);
        for (String group : EXCLUDE_PRESETS.keySet()) {
            CheckBox cb = new CheckBox(group);
            cb.setTooltip(new Tooltip(String.join(", ", EXCLUDE_PRESETS.get(group))));
            excludeBoxes.put(group, cb);
            excludeRow.getChildren().add(cb);
        }
        TextField customExclude = new TextField();
        customExclude.setPromptText("more biomes or #tags to avoid, comma-separated, e.g. minecraft:cherry_grove, #minecraft:is_badlands");
        CheckBox dryWater = new CheckBox("Stay away from water");
        CheckBox keepDry = new CheckBox("Keep blocks dry when generated into water (no waterlogging)");
        dryWater.setOnAction(e -> {
            for (String g : WATER_GROUPS) excludeBoxes.get(g).setSelected(dryWater.isSelected());
            keepDry.setSelected(dryWater.isSelected());
        });
        Label biomeSummary = hint("");
        java.util.function.Supplier<List<String>> includeList = () -> biomes.getValue().equals("Custom…")
                ? Arrays.stream(customBiomes.getText().split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList()
                : BIOME_PRESETS.get(biomes.getValue());
        java.util.function.Supplier<List<String>> excludeList = () -> {
            List<String> ex = new ArrayList<>();
            excludeBoxes.forEach((g, cb) -> {
                if (cb.isSelected()) ex.addAll(EXCLUDE_PRESETS.get(g));
            });
            Arrays.stream(customExclude.getText().split(",")).map(String::strip).filter(x -> !x.isEmpty()).forEach(ex::add);
            return ex;
        };
        Runnable biomesChanged = () -> {
            boolean water = WATER_GROUPS.stream().allMatch(g -> excludeBoxes.get(g).isSelected());
            dryWater.setSelected(water);
            List<String> ex = excludeList.get();
            if (ex.isEmpty()) {
                biomeSummary.setText("No biomes excluded.");
                return;
            }
            try {
                List<String> left = DatapackExporter.resolveBiomes(includeList.get(), ex, biomeTags);
                biomeSummary.setText("Spawns in " + left.size() + " biome" + (left.size() == 1 ? "" : "s") + " after exclusions: "
                        + String.join(", ", left.stream().limit(8).map(b -> b.replace("minecraft:", "")).toList())
                        + (left.size() > 8 ? ", …" : "") + ".");
            } catch (IllegalArgumentException ex2) {
                biomeSummary.setText("⚠ " + ex2.getMessage());
            }
        };
        excludeBoxes.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> biomesChanged.run()));
        customExclude.textProperty().addListener((o, a, b) -> biomesChanged.run());
        biomes.valueProperty().addListener((o, a, b) -> biomesChanged.run());
        customBiomes.textProperty().addListener((o, a, b) -> biomesChanged.run());
        biomesChanged.run();

        GridPane where = form();
        int r = 0;
        where.addRow(r++, label("Biomes"), biomes);
        where.add(customBiomes, 1, r++);
        where.addRow(r++, label("Never in"), excludeRow);
        where.add(customExclude, 1, r++);
        where.add(biomeSummary, 1, r++);
        where.addRow(r++, label("Water"), dryWater);
        where.add(keepDry, 1, r++);
        where.add(hint("Minecraft checks the biome at the structure's centre, so avoiding oceans, rivers, beaches and swamps keeps "
                + "it off water; a structure near a coast can still reach over the edge (use a smaller build or \"Keep away from\" "
                + "shipwrecks and monuments). Lakes and ponds inside land biomes aren't biomes, so no worldgen setting can avoid "
                + "those. Keeping blocks dry needs 1.21 or newer."), 1, r++);
        where.addRow(r++, label("Height"), heightMode);
        where.addRow(r++, offsetLabel, offset);
        where.addRow(r++, rangeLabel, rangeRow);
        where.add(heightHint, 1, r++);
        where.addRow(r++, label("Generation step"), step);
        where.add(stepHint, 1, r);

        // ---- Blending with the terrain
        javafx.scene.layout.FlowPane terrainCards = new javafx.scene.layout.FlowPane(8, 8);
        Label terrainTitle = new Label();
        terrainTitle.getStyleClass().add("export-card-title");
        Label terrainDesc = hint("");
        Label terrainUsed = hint("");
        for (DatapackExporter.TerrainAdaptation t : DatapackExporter.TerrainAdaptation.values()) {
            Label l = new Label(t.label);
            l.getStyleClass().add("export-card-sub");
            VBox box = new VBox(4, TerrainDiagrams.draw(t, dark, 0.8), l);
            box.setAlignment(Pos.CENTER);
            ToggleButton card = new ToggleButton(null, box);
            card.getStyleClass().addAll("export-card", "terrain-card");
            card.setUserData(t);
            card.setToggleGroup(terrainGroup);
            card.setTooltip(new Tooltip(t.label + ": " + t.description));
            terrainCards.getChildren().add(card);
        }
        terrainGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                if (a != null) a.setSelected(true);
                return;
            }
            var t = (DatapackExporter.TerrainAdaptation) b.getUserData();
            terrainTitle.setText(t.label + (t == DatapackExporter.TerrainAdaptation.NONE ? "" : "  ·  " + t.name().toLowerCase(Locale.ROOT)));
            terrainDesc.setText(t.description + (t == DatapackExporter.TerrainAdaptation.NONE ? ""
                    : " Blending needs a 12-block margin, so the structure can reach at most 116 blocks from its centre."));
            terrainUsed.setText("Used by: " + t.usedBy);
        });
        selectTerrain(DatapackExporter.TerrainAdaptation.BEARD_THIN);

        foundationBlock.getItems().setAll(FOUNDATION_BLOCKS);
        foundationBlock.setEditable(true);
        foundationBlock.setValue(FOUNDATION_BLOCKS.getFirst());
        foundationBlock.disableProperty().bind(foundation.valueProperty().isEqualTo(0));
        HBox foundationRow = new HBox(8, foundation, new Label("blocks of"), foundationBlock);
        foundationRow.setAlignment(Pos.CENTER_LEFT);
        GridPane blend = form();
        blend.addRow(0, label("Foundation"), foundationRow);
        blend.add(hint("Extends the bottom layer downwards so the build stands on footings instead of floating over dips. "
                + "The build is lowered by the same amount, so its floor stays at the chosen height. \"Match\" continues each "
                + "bottom block (dirt under grass and paths)."), 1, 1);
        blend.add(air, 1, 2);
        blend.add(hint("On: hills, trees and caves inside the structure's box are removed, so rooms are hollow. "
                + "Off: the ground shows through anywhere the build has air."), 1, 3);

        // ---- How often
        Spinner<Integer> spacing = new Spinner<>(2, 4096, 34);
        spacing.setEditable(true);
        Spinner<Integer> separation = new Spinner<>(1, 4095, 8);
        separation.setEditable(true);
        Slider chance = new Slider(5, 100, 100);
        Label chanceValue = new Label();
        chance.valueProperty().addListener((o, a, b) -> chanceValue.setText(Math.round(b.doubleValue()) + "%"));
        chanceValue.setText("100%");
        HBox chanceRow = new HBox(8, chance, chanceValue);
        chanceRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(chance, Priority.ALWAYS);
        ToggleGroup spreadGroup = new ToggleGroup();
        HBox spreadRow = new HBox(0);
        Label spreadHint = hint("");
        DatapackExporter.Spread[] spreads = DatapackExporter.Spread.values();
        for (int i = 0; i < spreads.length; i++) {
            ToggleButton b = new ToggleButton(spreads[i].label);
            b.setUserData(spreads[i]);
            b.setToggleGroup(spreadGroup);
            b.getStyleClass().add(i == 0 ? "left-pill" : "right-pill");
            spreadRow.getChildren().add(b);
        }
        spreadGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                if (a != null) a.setSelected(true);
                return;
            }
            spreadHint.setText(((DatapackExporter.Spread) b.getUserData()).description);
        });
        spreadGroup.getToggles().getFirst().setSelected(true);
        ComboBox<String> avoid = new ComboBox<>();
        avoid.getItems().setAll(AVOID.keySet());
        avoid.setValue("Nothing");
        Spinner<Integer> avoidChunks = new Spinner<>(1, 16, 4);
        avoidChunks.setEditable(true);
        avoidChunks.setPrefWidth(80);
        avoidChunks.disableProperty().bind(avoid.valueProperty().isEqualTo("Nothing"));
        HBox avoidRow = new HBox(8, avoid, new Label("by"), avoidChunks, new Label("chunks"));
        avoidRow.setAlignment(Pos.CENTER_LEFT);
        Label spacingHint = hint("");
        Runnable spacingChanged = () -> {
            int sp = spacing.getValue(), se = separation.getValue();
            spacingHint.setText(String.format("About one every %d blocks (a %d-chunk grid), never closer than %d blocks%s. Vanilla villages use 34 / 8.",
                    sp * 16, sp, se * 16, se >= sp ? " (separation must be smaller than spacing)" : ""));
        };
        spacing.valueProperty().addListener((o, a, b) -> spacingChanged.run());
        separation.valueProperty().addListener((o, a, b) -> spacingChanged.run());
        spacingChanged.run();
        GridPane often = form();
        often.addRow(0, label("Spacing (chunks)"), spacing);
        often.addRow(1, label("Min separation"), separation);
        often.add(spacingHint, 1, 2);
        often.addRow(3, label("Spread"), spreadRow);
        often.add(spreadHint, 1, 4);
        often.addRow(5, label("Chance"), chanceRow);
        often.add(hint("Share of possible spots that actually get one; lower makes it rarer without changing the grid."), 1, 6);
        often.addRow(7, label("Keep away from"), avoidRow);

        // ---- Weathering
        for (Slider sl : List.of(integrity, mossiness)) {
            sl.setMajorTickUnit(25);
            sl.setShowTickMarks(true);
            sl.setPrefWidth(260);
        }
        Label integrityValue = new Label(), mossValue = new Label();
        integrity.valueProperty().addListener((o, a, b) -> integrityValue.setText(b.intValue() >= 100 ? "Intact" : b.intValue() + "% of blocks kept"));
        mossiness.valueProperty().addListener((o, a, b) -> mossValue.setText(b.intValue() == 0 ? "New" : b.intValue() + "% aged"));
        integrityValue.setText("Intact");
        mossValue.setText("New");
        HBox integrityRow = new HBox(10, integrity, integrityValue);
        HBox mossRow = new HBox(10, mossiness, mossValue);
        integrityRow.setAlignment(Pos.CENTER_LEFT);
        mossRow.setAlignment(Pos.CENTER_LEFT);
        GridPane weather = form();
        weather.addRow(0, label("Integrity"), integrityRow);
        weather.add(hint("Below 100% blocks are removed at random each time it generates, for crumbling ruins (Minecraft's block_rot)."), 1, 1);
        weather.addRow(2, label("Age"), mossRow);
        weather.add(hint("Turns that share of stone bricks, cobblestone and their stairs and slabs mossy or cracked (block_age)."), 1, 3);

        // ---- Loot: a table for each kind of container in the exported layers.
        Map<io.blockdesigner.worldgen.LootTables.Container, Integer> containers = new java.util.EnumMap<>(io.blockdesigner.worldgen.LootTables.Container.class);
        for (Layer l : ws.scene().layers()) {
            if (l.visible()) io.blockdesigner.worldgen.LootTables.count(l.structure()).forEach((c, n) -> containers.merge(c, n, Integer::sum));
        }
        LootPanel loot = new LootPanel(ws, version.valueProperty(), containers);

        // ---- Output
        ComboBox<Target> output = new ComboBox<>();
        output.getItems().add(new Target("Save as .zip…", null));
        for (Target t : worlds(scan)) output.getItems().add(t);
        output.getSelectionModel().selectFirst();
        GridPane out = form();
        out.addRow(0, label("Output"), output);

        GridPane pack = form();
        pack.addRow(0, label("Namespace"), namespace);
        pack.addRow(1, label("Structure name"), name);
        pack.addRow(2, label("Minecraft version"), version);
        GridPane.setHgrow(name, Priority.ALWAYS);

        // Everything a saved preset remembers (not the pack's name, version or per-layer village roles).
        track("layout", () -> village.isSelected() ? "village" : "single", v -> (v.equals("village") ? village : single).setSelected(true));
        track("source", () -> source.getValue().name(), v -> source.setValue(Source.valueOf(v)));
        track("villageSize", depth);
        track("streetBlock", streetBlock::getValue, streetBlock::setValue);
        track("streetWidth", () -> String.valueOf(streetWidth.getValue()), v -> streetWidth.setValue(Integer.parseInt(v)));
        track("generateStreets", genStreets);
        track("biomes", biomes::getValue, biomes::setValue);
        track("customBiomes", customBiomes::getText, customBiomes::setText);
        excludeBoxes.forEach((g, cb) -> track("exclude." + g, cb));
        track("customExclude", customExclude::getText, customExclude::setText);
        track("keepDry", keepDry);
        track("height", () -> heightMode.getValue().name(), v -> heightMode.setValue(DatapackExporter.HeightMode.valueOf(v)));
        track("offset", offset);
        track("minY", minY);
        track("maxY", maxY);
        track("step", () -> step.getValue().name(), v -> step.setValue(DatapackExporter.Step.valueOf(v)));
        track("terrain", () -> terrain().name(), v -> selectTerrain(DatapackExporter.TerrainAdaptation.valueOf(v)));
        track("foundation", foundation);
        track("foundationBlock", foundationBlock::getValue, foundationBlock::setValue);
        track("clearGround", air);
        track("spacing", spacing);
        track("separation", separation);
        track("chance", chance);
        track("spread", () -> ((DatapackExporter.Spread) spreadGroup.getSelectedToggle().getUserData()).name(), v -> {
            for (var tg : spreadGroup.getToggles()) if (((DatapackExporter.Spread) tg.getUserData()).name().equals(v)) tg.setSelected(true);
        });
        track("avoid", avoid::getValue, avoid::setValue);
        track("avoidChunks", avoidChunks);
        track("integrity", integrity);
        track("age", mossiness);
        for (var c : io.blockdesigner.worldgen.LootTables.Container.values()) track("loot." + c.name(), () -> loot.encode(c), v -> loot.decode(c, v));

        Button savePreset = new Button("Save…");
        savePreset.setTooltip(new Tooltip("Save the settings below as a preset for later packs"));
        Button deletePreset = new Button("Delete");
        deletePreset.disableProperty().bind(Bindings.createBooleanBinding(
                () -> preset.getValue() == null || !preset.getValue().startsWith(SAVED), preset.valueProperty()));
        preset.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            var apply = PRESETS.get(b);
            if (apply != null) apply.accept(this);
            var saved = b.startsWith(SAVED) ? ws.settings().worldgenPresets.get(b.substring(SAVED.length())) : null;
            if (saved != null) restore(saved);
        });
        savePreset.setOnAction(e -> {
            javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(
                    preset.getValue() != null && preset.getValue().startsWith(SAVED) ? preset.getValue().substring(SAVED.length()) : "");
            d.initOwner(getDialogPane().getScene().getWindow());
            d.setTitle("Save preset");
            d.setHeaderText("Save these worldgen settings as a preset");
            d.setContentText("Name");
            d.showAndWait().map(String::strip).filter(n -> !n.isEmpty()).ifPresent(n -> {
                if (ws.settings().worldgenPresets.containsKey(n) && !confirm("Replace the preset '" + n + "'?")) return;
                ws.settings().worldgenPresets.put(n, capture());
                ws.settings().save();
                listPresets.run();
                preset.setValue(SAVED + n);
            });
        });
        deletePreset.setOnAction(e -> {
            String n = preset.getValue().substring(SAVED.length());
            if (!confirm("Delete the preset '" + n + "'?")) return;
            ws.settings().worldgenPresets.remove(n);
            ws.settings().save();
            preset.setValue("Custom");
            listPresets.run();
        });
        preset.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(preset, Priority.ALWAYS);
        HBox presetRow = new HBox(8, preset, savePreset, deletePreset);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        GridPane presets = form();
        presets.addRow(0, label("Preset"), presetRow);
        presets.add(hint("Built-in presets fill in placement, blending and weathering for a common kind of structure. "
                + "Save… keeps everything below (except the pack's name and version) to use again."), 1, 1);

        Label title = new Label("Worldgen data pack", FormatIcons.tile(FormatIcons.Kind.DATAPACK, 40));
        title.getStyleClass().add("export-title");
        title.setGraphicTextGap(12);
        VBox body = new VBox(10, title, hint("Makes this build generate naturally in new chunks of your worlds."),
                presets,
                section("Pack"), pack,
                section("Layout"), layoutGrid, villageBox,
                section("Where it spawns"), where,
                section("Blending with the terrain"), terrainCards, terrainTitle, terrainDesc, terrainUsed, blend,
                section("How often"), often,
                section("Weathering"), weather,
                section("Loot"), loot,
                section("Save"), out);
        body.setPadding(new Insets(4, 14, 8, 6));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("export-cards-scroll");
        getDialogPane().setContent(scroll);
        getDialogPane().setPrefSize(860, 780);
        getDialogPane().setMinSize(720, 520);
        ButtonType exportBtn = new ButtonType("Export", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, exportBtn);

        getDialogPane().lookupButton(exportBtn).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            List<String> biomeList = includeList.get();
            String ns = namespace.getText().strip().toLowerCase(Locale.ROOT), nm = name.getText().strip().toLowerCase(Locale.ROOT);
            var defaults = DatapackExporter.Options.defaults(ns, nm, version.getValue());
            String fb = foundationBlock.getValue() == null || foundationBlock.getValue().startsWith("Match") ? "match"
                    : normalizeBlock(foundationBlock.getValue());
            var advanced = new DatapackExporter.Advanced(heightMode.getValue(), minY.getValue(), maxY.getValue(), foundation.getValue(), fb,
                    integrity.getValue() / 100.0, mossiness.getValue() / 100.0, (DatapackExporter.Spread) spreadGroup.getSelectedToggle().getUserData(),
                    AVOID.get(avoid.getValue()), avoidChunks.getValue()).withBiomes(excludeList.get(), keepDry.isSelected(), biomeTags);
            var opts = new DatapackExporter.Options(ns, nm, "Structures made with BlockDesigner", version.getValue(), biomeList,
                    step.getValue(), terrain(), true, offset.getValue(), spacing.getValue(), separation.getValue(), defaults.salt(),
                    chance.getValue() / 100.0, air.isSelected(), advanced, loot.choices());
            boolean villageMode = village.isSelected();
            List<DatapackExporter.Piece> pieces = villageMode ? List.of() : pieces(ws, source.getValue());
            List<DatapackExporter.VillagePiece> vpieces = villageMode ? villagePieces(rows) : List.of();
            var vopts = new DatapackExporter.Village(depth.getValue(), normalizeBlock(streetBlock.getValue()), genStreets.isSelected(),
                    streetWidth.getValue());
            List<String> errors = villageMode ? DatapackExporter.validateVillage(opts, vopts, vpieces) : DatapackExporter.validate(opts, pieces);
            if (!errors.isEmpty()) {
                e.consume();
                alert(Alert.AlertType.WARNING, "Please fix", String.join("\n", errors));
                return;
            }
            try {
                String whereText;
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
                    if (villageMode) DatapackExporter.writeVillageZip(opts, vopts, vpieces, f.toPath());
                    else DatapackExporter.writeZip(opts, pieces, f.toPath());
                    whereText = f.getAbsolutePath() + "\n\nCopy it into <world>/datapacks, then run /reload (new chunks only).";
                } else {
                    Files.createDirectories(t.worldDatapacks());
                    Path zip = t.worldDatapacks().resolve(ns + "_" + nm + ".zip");
                    if (villageMode) DatapackExporter.writeVillageZip(opts, vopts, vpieces, zip);
                    else DatapackExporter.writeZip(opts, pieces, zip);
                    whereText = zip + "\n\nOpen the world and run /reload; new chunks will generate it.";
                }
                ws.statusProperty().set("Data pack written: " + ns + ":" + nm);
                TextArea cmds = new TextArea(DatapackExporter.testCommands(opts));
                cmds.setEditable(false);
                cmds.setPrefRowCount(2);
                Alert done = new Alert(Alert.AlertType.INFORMATION);
                done.initOwner(getDialogPane().getScene().getWindow());
                done.setHeaderText("Data pack exported");
                done.setContentText("Written to " + whereText + "\n\nTest it in-game with:");
                done.getDialogPane().setExpandableContent(cmds);
                done.getDialogPane().setExpanded(true);
                done.showAndWait();
            } catch (IOException | RuntimeException ex) {
                e.consume();
                alert(Alert.AlertType.ERROR, "Export failed", ex.getMessage());
            }
        });
    }

    // ---- presets ---------------------------------------------------------------------------------------------

    /** Common kinds of structure; each sets height, blending, foundation, weathering and generation step together. */
    private static final Map<String, java.util.function.Consumer<DatapackDialog>> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Custom", null);
        PRESETS.put("House on the surface (like villages)", d -> d.apply(DatapackExporter.HeightMode.SURFACE, 0, DatapackExporter.TerrainAdaptation.BEARD_THIN,
                3, 100, 0, false, DatapackExporter.Step.SURFACE_STRUCTURES));
        PRESETS.put("On flat ground, never on hills", d -> {
            d.apply(DatapackExporter.HeightMode.SURFACE, 0, DatapackExporter.TerrainAdaptation.BEARD_BOX, 2, 100, 0, true,
                    DatapackExporter.Step.SURFACE_STRUCTURES);
            d.foundationBlock.setValue(FOUNDATION_BLOCKS.getFirst());
            d.biomes.setValue("Plains & meadows");
            d.excludeBoxes.get("Hills & mountains").setSelected(true);
        });
        PRESETS.put("Big build on rough ground", d -> d.apply(DatapackExporter.HeightMode.SURFACE, 0, DatapackExporter.TerrainAdaptation.BEARD_BOX,
                0, 100, 0, true, DatapackExporter.Step.SURFACE_STRUCTURES));
        PRESETS.put("Half-buried ruin (like trail ruins)", d -> d.apply(DatapackExporter.HeightMode.SURFACE, -3, DatapackExporter.TerrainAdaptation.BURY,
                0, 80, 45, false, DatapackExporter.Step.SURFACE_STRUCTURES));
        PRESETS.put("Underground vault (like trial chambers)", d -> {
            d.apply(DatapackExporter.HeightMode.RANGE, 0, DatapackExporter.TerrainAdaptation.ENCAPSULATE, 0, 100, 0, true,
                    DatapackExporter.Step.UNDERGROUND_STRUCTURES);
            d.minY.getValueFactory().setValue(-40);
            d.maxY.getValueFactory().setValue(0);
        });
        PRESETS.put("Sunken wreck on the sea floor", d -> d.apply(DatapackExporter.HeightMode.OCEAN_FLOOR, -1, DatapackExporter.TerrainAdaptation.NONE,
                0, 90, 50, false, DatapackExporter.Step.SURFACE_STRUCTURES));
        PRESETS.put("Floating sky island", d -> {
            d.apply(DatapackExporter.HeightMode.FIXED, 160, DatapackExporter.TerrainAdaptation.NONE, 0, 100, 0, false,
                    DatapackExporter.Step.SURFACE_STRUCTURES);
        });
    }

    /** Prefix of saved presets in the preset list. */
    private static final String SAVED = "Saved · ";

    private record Tracked(java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
    }

    private final Map<String, Tracked> tracked = new LinkedHashMap<>();

    private void track(String key, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        tracked.put(key, new Tracked(get, set));
    }

    private void track(String key, Spinner<Integer> sp) {
        track(key, () -> String.valueOf(sp.getValue()), v -> sp.getValueFactory().setValue(Integer.parseInt(v)));
    }

    private void track(String key, CheckBox cb) {
        track(key, () -> String.valueOf(cb.isSelected()), v -> cb.setSelected(Boolean.parseBoolean(v)));
    }

    private void track(String key, Slider sl) {
        track(key, () -> String.valueOf(sl.getValue()), v -> sl.setValue(Double.parseDouble(v)));
    }

    /** The current settings as a saved preset. */
    private Map<String, String> capture() {
        Map<String, String> out = new LinkedHashMap<>();
        tracked.forEach((k, t) -> {
            String v = t.get().get();
            if (v != null) out.put(k, v);
        });
        return out;
    }

    /** Applies a saved preset; values that no longer fit (a removed option, a version's limits) are skipped. */
    private void restore(Map<String, String> saved) {
        saved.forEach((k, v) -> {
            Tracked t = tracked.get(k);
            if (t == null) return;
            try {
                t.set().accept(v);
            } catch (RuntimeException ignored) {
                // left as it is
            }
        });
    }

    private boolean confirm(String question) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(getDialogPane().getScene().getWindow());
        a.setHeaderText(null);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void apply(DatapackExporter.HeightMode h, int off, DatapackExporter.TerrainAdaptation t, int foundationDepth,
                       int integrityPct, int mossPct, boolean clearAir, DatapackExporter.Step st) {
        heightMode.setValue(h);
        offset.getValueFactory().setValue(off);
        selectTerrain(t);
        foundation.getValueFactory().setValue(foundationDepth);
        integrity.setValue(integrityPct);
        mossiness.setValue(mossPct);
        air.setSelected(clearAir);
        step.setValue(st);
    }

    private void selectTerrain(DatapackExporter.TerrainAdaptation t) {
        for (var tg : terrainGroup.getToggles()) if (tg.getUserData() == t) tg.setSelected(true);
    }

    private DatapackExporter.TerrainAdaptation terrain() {
        var sel = terrainGroup.getSelectedToggle();
        return sel == null ? DatapackExporter.TerrainAdaptation.BEARD_THIN : (DatapackExporter.TerrainAdaptation) sel.getUserData();
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    static GridPane form() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints();
        c0.setMinWidth(130);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    static Label label(String text) {
        Label l = new Label(text);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("plugin-meta");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    static Label section(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("viewport-settings-section");
        return l;
    }

    private static <T> javafx.util.StringConverter<T> converter(java.util.function.Function<T, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : f.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
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

    private static Label bold(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    /** A first guess from the layer name: roads are streets, a well or plaza is the centre, the rest are buildings. */
    private static VillageRole guessRole(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches(".*\\b(street|road|path|lane|avenue)s?\\b.*")) return VillageRole.STREET;
        if (n.matches(".*\\b(centre|center|plaza|square|well|start|town ?hall|meeting)\\b.*")) return VillageRole.START;
        return VillageRole.BUILDING;
    }

    private static String normalizeBlock(String s) {
        String t = s == null ? "" : s.strip();
        return t.isEmpty() ? STREET_BLOCKS.getFirst() : t.contains(":") ? t : "minecraft:" + t;
    }

    private static List<DatapackExporter.VillagePiece> villagePieces(List<Row> rows) {
        List<DatapackExporter.VillagePiece> out = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>(List.of("plaza", "street_long", "street_short", "crossroad"));
        for (Row r : rows) {
            VillageRole role = r.role().getValue();
            if (role == VillageRole.SKIP) continue;
            String base = slug(r.layer().name()), name = base;
            for (int i = 2; !used.add(name); i++) name = base + "_" + i;
            DatapackExporter.Side side = switch (r.entrance().getValue()) {
                case "North" -> DatapackExporter.Side.NORTH;
                case "East" -> DatapackExporter.Side.EAST;
                case "South" -> DatapackExporter.Side.SOUTH;
                case "West" -> DatapackExporter.Side.WEST;
                default -> null;
            };
            DatapackExporter.Role er = switch (role) {
                case STREET -> DatapackExporter.Role.STREET;
                case START -> DatapackExporter.Role.START;
                default -> DatapackExporter.Role.BUILDING;
            };
            out.add(new DatapackExporter.VillagePiece(name, Scene.flatten(List.of(r.layer())), er, r.weight().getValue(), side));
        }
        return out;
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
