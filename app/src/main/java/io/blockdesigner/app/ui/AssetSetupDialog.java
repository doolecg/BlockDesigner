package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.assets.McInstallLocator;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lets the user choose which Minecraft install supplies textures and models, and optionally a modded instance whose
 * mods and resource packs are layered on top (so Create blocks render properly).
 */
public final class AssetSetupDialog extends Dialog<AssetSetupDialog.Choice> {

    /** The chosen game jar plus mods and resource packs to layer on top. */
    public record Choice(Path gameJar, String instanceName, List<Path> mods, List<Path> resourcePacks) {
    }

    public AssetSetupDialog(Window owner, McInstallLocator.Result scan, Settings settings) {
        initOwner(owner);
        setTitle("Minecraft assets");
        setHeaderText("Pick the Minecraft version whose textures and models to use.\nNothing is copied; files are read from your install.");

        ComboBox<McInstallLocator.GameJar> jar = new ComboBox<>();
        jar.getItems().setAll(scan.jars());
        jar.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Object> instance = new ComboBox<>();
        instance.getItems().add("None (vanilla only)");
        instance.getItems().addAll(scan.instances());
        instance.setMaxWidth(Double.MAX_VALUE);
        instance.getSelectionModel().selectFirst();
        CheckBox packs = new CheckBox("Also use the instance's resource packs");

        Label info = new Label();
        info.getStyleClass().add("layer-meta");
        info.setWrapText(true);

        // Restore previous choice
        scan.jars().stream().filter(j -> j.jar().toString().equals(settings.gameJar)).findFirst().ifPresentOrElse(jar::setValue,
                () -> jar.getSelectionModel().selectFirst());
        scan.instances().stream().filter(i -> i.name().equals(settings.instanceName)).findFirst().ifPresent(instance::setValue);

        instance.valueProperty().addListener((o, a, v) -> {
            if (v instanceof McInstallLocator.Instance inst) {
                scan.jarFor(inst.mcVersion()).ifPresent(jar::setValue);
                info.setText(inst.mods().size() + " mods, " + inst.resourcePacks().size() + " resource packs from " + inst.gameDir());
            } else {
                info.setText("");
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, new Label("Game version"), jar);
        grid.addRow(1, new Label("Mods from instance"), instance);
        grid.add(packs, 1, 2);
        grid.add(info, 1, 3);
        GridPane.setHgrow(jar, Priority.ALWAYS);
        GridPane.setHgrow(instance, Priority.ALWAYS);

        ButtonType browse = new ButtonType("Browse for jar…", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(browse, ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(browse).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            FileChooser fc = new FileChooser();
            fc.setTitle("Minecraft client jar");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Jar files", "*.jar"));
            File f = fc.showOpenDialog(getDialogPane().getScene().getWindow());
            if (f != null) {
                try {
                    var v = io.blockdesigner.core.version.McVersion.fromClientJar(f.toPath());
                    var gj = new McInstallLocator.GameJar(v, f.toPath(), "Custom");
                    jar.getItems().addFirst(gj);
                    jar.setValue(gj);
                } catch (Exception ex) {
                    info.setText("Not a Minecraft client jar: " + ex.getMessage());
                }
            }
        });

        VBox content = new VBox(12, grid);
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(560);
        getDialogPane().setContent(content);
        getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(jar.valueProperty().isNull());

        setResultConverter(bt -> {
            if (bt != ButtonType.OK || jar.getValue() == null) return null;
            List<Path> mods = new ArrayList<>(), rps = new ArrayList<>();
            String instName = null;
            if (instance.getValue() instanceof McInstallLocator.Instance inst) {
                mods.addAll(inst.mods());
                if (packs.isSelected()) rps.addAll(inst.resourcePacks());
                instName = inst.name();
            }
            return new Choice(jar.getValue().jar(), instName, mods, rps);
        });
    }

    /** The saved choice, if its files still exist; otherwise empty. */
    public static Optional<Choice> fromSettings(Settings s, McInstallLocator.Result scan) {
        if (s.gameJar == null || !new File(s.gameJar).isFile()) return Optional.empty();
        List<Path> mods = new ArrayList<>(), rps = new ArrayList<>();
        if (s.instanceName != null) {
            scan.instances().stream().filter(i -> i.name().equals(s.instanceName)).findFirst().ifPresent(i -> mods.addAll(i.mods()));
        }
        for (String p : s.resourcePacks) if (new File(p).exists()) rps.add(Path.of(p));
        return Optional.of(new Choice(Path.of(s.gameJar), s.instanceName, mods, rps));
    }
}
