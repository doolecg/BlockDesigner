package io.blockdesigner.app.ui;

import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.version.McVersion;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Export options: format, target version, which layers, and air handling. */
public final class ExportDialog extends Dialog<ExportDialog.Options> {

    public enum Source {
        ACTIVE("Active layer"), SELECTED("Selected layers (merged)"), VISIBLE("All visible layers (merged)"), EACH("Each layer as its own file / region");

        final String label;

        Source(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Options(SchematicFormat format, McVersion version, Source source, boolean includeAir, int spongeVersion) {
    }

    public ExportDialog(Window owner, McVersion defaultVersion, SchematicFormat preselect) {
        initOwner(owner);
        setTitle("Export");
        setHeaderText("Export schematic");

        ComboBox<SchematicFormat> format = new ComboBox<>();
        format.getItems().setAll(Schematics.VANILLA, Schematics.LITEMATICA, Schematics.SPONGE);
        format.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SchematicFormat f, boolean empty) {
                super.updateItem(f, empty);
                setText(empty || f == null ? null : f.displayName());
            }
        });
        format.setButtonCell(format.getCellFactory().call(null));
        format.setValue(preselect != null ? preselect : Schematics.LITEMATICA);

        ComboBox<McVersion> version = new ComboBox<>();
        version.getItems().setAll(McVersion.builtIn().reversed());
        if (!version.getItems().contains(defaultVersion)) version.getItems().addFirst(defaultVersion);
        version.setValue(defaultVersion);

        ComboBox<Source> source = new ComboBox<>();
        source.getItems().setAll(Source.values());
        source.setValue(Source.VISIBLE);

        CheckBox air = new CheckBox("Include air (clears terrain when placed)");
        air.setSelected(true);
        ComboBox<Integer> sponge = new ComboBox<>();
        sponge.getItems().setAll(3, 2);
        sponge.setValue(3);
        Label spongeLabel = new Label("WorldEdit format");
        sponge.visibleProperty().bind(format.valueProperty().isEqualTo(Schematics.SPONGE));
        spongeLabel.visibleProperty().bind(sponge.visibleProperty());
        air.visibleProperty().bind(format.valueProperty().isEqualTo(Schematics.VANILLA));
        Label hint = new Label();
        hint.getStyleClass().add("layer-meta");
        hint.setWrapText(true);
        format.valueProperty().addListener((o, a, f) -> hint.setText(hintFor(f)));
        hint.setText(hintFor(format.getValue()));

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(6, 2, 2, 2));
        g.addRow(0, new Label("Format"), format);
        g.addRow(1, new Label("Minecraft version"), version);
        g.addRow(2, new Label("Layers"), source);
        g.addRow(3, spongeLabel, sponge);
        g.add(air, 1, 4);
        g.add(hint, 1, 5);
        GridPane.setHgrow(format, Priority.ALWAYS);
        g.setPrefWidth(520);
        getDialogPane().setContent(g);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        setResultConverter(bt -> bt == ButtonType.OK
                ? new Options(format.getValue(), version.getValue(), source.getValue(), air.isSelected(), sponge.getValue())
                : null);
    }

    private static String hintFor(SchematicFormat f) {
        if (f == Schematics.VANILLA) return "Works with Create's schematicannon (put it in .minecraft/schematics), structure blocks and worldgen.";
        if (f == Schematics.LITEMATICA) return "Opens in Litematica. 'Each layer' keeps layers as separate regions in one file.";
        return "Load with //schem load in WorldEdit or FAWE.";
    }
}
