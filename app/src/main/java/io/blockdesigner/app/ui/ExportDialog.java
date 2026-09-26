package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.plugins.PluginManager;
import io.blockdesigner.assets.McInstallLocator;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.SchematicFormat;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.plugin.PluginExporter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Export window: pick what to make from cards on the left (Litematica, WorldEdit, Create / structure .nbt, a
 * worldgen data pack, and anything plugins add), see what will be exported, and save it straight into a Minecraft
 * instance's schematics folder or anywhere else. Remembers the last card, options and folders.
 */
public final class ExportDialog extends Dialog<ExportDialog.Outcome> {

    public enum Source {
        ACTIVE("Active layer"), SELECTED("Selected layers, merged"), VISIBLE("All visible layers, merged"),
        EACH("Each layer separately");

        final String label;

        Source(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * What happened: files were written, or the user asked for the data pack setup.
     *
     * @param written  files (or folders) written; empty when {@code datapack}
     * @param blocks   blocks exported
     */
    public record Outcome(List<Path> written, long blocks, boolean datapack) {
    }

    /** One card on the left. */
    private sealed interface Choice permits FormatChoice, PluginChoice, DatapackChoice {
        String key();

        String title();

        String subtitle();

        FormatIcons.Kind kind();
    }

    private record FormatChoice(SchematicFormat format) implements Choice {
        public String key() {
            return format.id();
        }

        public String title() {
            if (format == Schematics.VANILLA) return "Create / Structure";
            if (format == Schematics.SPONGE) return "WorldEdit";
            return format.displayName();
        }

        public String subtitle() {
            String ext = "." + format.extensions().getFirst();
            if (format == Schematics.VANILLA) return ext + " · schematicannon";
            if (format == Schematics.LITEMATICA) return ext + " · overlay & printer";
            if (format == Schematics.SPONGE) return ext + " · WorldEdit, FAWE";
            return ext + " · plugin format";
        }

        public FormatIcons.Kind kind() {
            return FormatIcons.kindOf(format);
        }
    }

    private record PluginChoice(PluginManager.Export export) implements Choice {
        public String key() {
            return "plugin:" + export.plugin().info().id() + "/" + export.exporter().id();
        }

        public String title() {
            return export.exporter().displayName();
        }

        public String subtitle() {
            String d = export.exporter().description();
            String what = export.exporter().writesFolder() ? "folder" : "." + export.exporter().extension();
            return what + " · " + (d.isBlank() ? export.plugin().info().name() : d);
        }

        public FormatIcons.Kind kind() {
            return FormatIcons.Kind.PLUGIN;
        }
    }

    private record DatapackChoice() implements Choice {
        public String key() {
            return "datapack";
        }

        public String title() {
            return "Worldgen data pack";
        }

        public String subtitle() {
            return ".zip · villages & structures";
        }

        public FormatIcons.Kind kind() {
            return FormatIcons.Kind.DATAPACK;
        }
    }

    /** A folder offered under "Save to". */
    private record Folder(String label, Path path) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Workspace ws;
    private final McInstallLocator.Result scan;
    private final List<Layer> fixedLayers;
    private final List<Choice> choices = new ArrayList<>();
    private final ToggleGroup cardGroup = new ToggleGroup();
    private final VBox detail = new VBox(12);
    private final Label error = new Label();
    private final ProgressIndicator busy = new ProgressIndicator();
    /** What a plugin exporter says it is doing (its Progress messages). */
    private final Label progressText = new Label();
    private Choice current;
    /** Last-used plugin exporter options (kept in the settings) and the values on the open plugin card. */
    private final io.blockdesigner.app.plugins.OptionStore pluginOptions;
    private final io.blockdesigner.plugin.BlockCatalog blocks;
    private OptionsEditor pluginEditor;
    private final Label pluginSummary = new Label();

    // Controls that survive switching cards (their values carry over).
    private final ComboBox<McVersion> version = new ComboBox<>();
    private final ToggleGroup sourceGroup = new ToggleGroup();
    private final CheckBox includeAir = new CheckBox("Include air (clears terrain when placed)");
    private final ComboBox<Integer> sponge = new ComboBox<>();
    private final TextField name = new TextField();
    private final ComboBox<Folder> folder = new ComboBox<>();
    private final Label pathPreview = new Label();
    private final Label summary = new Label();

    /**
     * @param layers   export exactly these layers (e.g. from a layer's menu), or null to let the user choose
     * @param preselect card key to open on (a format id, "datapack"), or null for the last one used
     */
    public ExportDialog(Window owner, Workspace ws, McInstallLocator.Result scan, List<PluginManager.Export> pluginExporters,
                        List<Layer> layers, String preselect) {
        this.ws = ws;
        this.scan = scan;
        this.fixedLayers = layers;
        this.pluginOptions = new io.blockdesigner.app.plugins.OptionStore(ws.settings().pluginOptions);
        this.blocks = new io.blockdesigner.app.plugins.AppBlockCatalog(ws::assets);
        initOwner(owner);
        setTitle("Export");
        setResizable(true);

        for (SchematicFormat f : List.of(Schematics.LITEMATICA, Schematics.SPONGE, Schematics.VANILLA)) choices.add(new FormatChoice(f));
        if (layers == null) choices.add(new DatapackChoice());
        for (SchematicFormat f : Schematics.formats()) if (!Schematics.isBuiltIn(f) && f.canWrite()) choices.add(new FormatChoice(f));
        for (PluginManager.Export e : pluginExporters) choices.add(new PluginChoice(e));

        var dp = getDialogPane();
        dp.getStylesheets().add(ExportDialog.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        dp.getStyleClass().addAll("app-root", ws.darkProperty().get() ? "dark" : "light", "export-dialog");

        // ---- shared controls
        version.getItems().setAll(McVersion.builtIn().reversed());
        McVersion target = ws.targetVersionProperty().get();
        if (target != null && !version.getItems().contains(target)) version.getItems().addFirst(target);
        version.setValue(target != null ? target : version.getItems().getFirst());
        for (Source s : Source.values()) {
            RadioButton rb = new RadioButton(s.label);
            rb.setUserData(s);
            rb.setToggleGroup(sourceGroup);
        }
        Source savedSource;
        try {
            savedSource = Source.valueOf(ws.settings().exportSource);
        } catch (IllegalArgumentException e) {
            savedSource = Source.VISIBLE;
        }
        selectSource(savedSource);
        sourceGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null && a != null) a.setSelected(true);
            refresh();
        });
        includeAir.setSelected(ws.settings().exportIncludeAir);
        sponge.getItems().setAll(3, 2);
        sponge.setValue(ws.settings().exportSpongeVersion == 2 ? 2 : 3);
        sponge.setCellFactory(v -> spongeCell());
        sponge.setButtonCell(spongeCell());
        String base = layers != null && layers.size() == 1 ? layers.getFirst().name() : ws.projectNameProperty().get();
        name.setText(MainWindow.safeName(base));
        name.textProperty().addListener((o, a, b) -> updatePath());
        folder.valueProperty().addListener((o, a, b) -> updatePath());
        folder.setMaxWidth(Double.MAX_VALUE);
        pathPreview.getStyleClass().add("export-path");
        pathPreview.setWrapText(true);
        pathPreview.setMinHeight(Region.USE_PREF_SIZE);
        summary.getStyleClass().add("export-summary");
        summary.setWrapText(true);
        summary.setMinHeight(Region.USE_PREF_SIZE);
        error.getStyleClass().add("export-error");
        error.setWrapText(true);
        error.setMinHeight(Region.USE_PREF_SIZE);
        busy.setMaxSize(22, 22);
        busy.setVisible(false);
        progressText.getStyleClass().add("export-hint");
        pluginSummary.getStyleClass().add("export-summary");
        pluginSummary.setWrapText(true);
        pluginSummary.setMinHeight(Region.USE_PREF_SIZE);

        // ---- cards
        VBox cards = new VBox(4);
        cards.getStyleClass().add("export-cards");
        boolean pluginHeading = false;
        for (Choice c : choices) {
            if ((c instanceof PluginChoice || (c instanceof FormatChoice fc && !Schematics.isBuiltIn(fc.format()))) && !pluginHeading) {
                Label h = new Label("FROM PLUGINS");
                h.getStyleClass().add("export-cards-heading");
                cards.getChildren().add(h);
                pluginHeading = true;
            }
            cards.getChildren().add(card(c));
        }
        ScrollPane cardScroll = new ScrollPane(cards);
        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.getStyleClass().add("export-cards-scroll");
        cardScroll.setPrefWidth(270);
        cardScroll.setMinWidth(240);

        detail.setPadding(new Insets(4, 12, 4, 18));
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detailScroll.setFitToHeight(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailScroll.getStyleClass().addAll("export-cards-scroll", "export-detail");
        HBox.setHgrow(detailScroll, Priority.ALWAYS);
        HBox body = new HBox(cardScroll, detailScroll);
        dp.setContent(body);
        dp.setPrefSize(920, 640);
        dp.setMinSize(780, 600);

        ButtonType exportBtn = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);
        dp.getButtonTypes().addAll(ButtonType.CANCEL, exportBtn);
        Button exportButton = (Button) dp.lookupButton(exportBtn);
        exportButton.setGraphic(new FontIcon(Feather.UPLOAD));
        exportButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            runExport();
        });
        setResultConverter(bt -> null);

        String want = preselect != null ? preselect : ws.settings().exportChoice;
        Choice first = choices.stream().filter(c -> c.key().equals(want)).findFirst().orElse(choices.getFirst());
        cardGroup.getToggles().stream().filter(t -> t.getUserData() == first).findFirst().ifPresent(t -> t.setSelected(true));
        cardGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                if (a != null) a.setSelected(true);
                return;
            }
            show((Choice) b.getUserData());
        });
        show(first);
    }

    private javafx.scene.control.ListCell<Integer> spongeCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v == 3 ? "Sponge v3 · WorldEdit 7.3+, FAWE" : "Sponge v2 · WorldEdit 7.2 and older");
            }
        };
    }

    private ToggleButton card(Choice c) {
        Label title = new Label(c.title());
        title.getStyleClass().add("export-card-title");
        Label sub = new Label(c.subtitle());
        sub.getStyleClass().add("export-card-sub");
        VBox text = new VBox(1, title, sub);
        text.setMinWidth(0);
        HBox row = new HBox(10, FormatIcons.tile(c.kind(), 34), text);
        row.setAlignment(Pos.CENTER_LEFT);
        ToggleButton b = new ToggleButton(null, row);
        b.getStyleClass().add("export-card");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMinHeight(Region.USE_PREF_SIZE);
        b.setMaxHeight(Region.USE_PREF_SIZE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setUserData(c);
        b.setToggleGroup(cardGroup);
        b.setTooltip(new Tooltip(c.title() + "\n" + c.subtitle()));
        return b;
    }

    private void selectSource(Source s) {
        for (var t : sourceGroup.getToggles()) if (t.getUserData() == s) t.setSelected(true);
    }

    private Source source() {
        var t = sourceGroup.getSelectedToggle();
        return t == null ? Source.VISIBLE : (Source) t.getUserData();
    }

    // ---- right-hand side -------------------------------------------------------------------------------------

    private void show(Choice c) {
        current = c;
        error.setText("");
        Label title = new Label(c.title());
        title.getStyleClass().add("export-title");
        Label sub = new Label(c.subtitle());
        sub.getStyleClass().add("export-card-sub");
        VBox titles = new VBox(2, title, sub);
        HBox header = new HBox(12, FormatIcons.tile(c.kind(), 46), titles);
        header.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label(hintFor(c));
        hint.getStyleClass().add("export-hint");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        detail.getChildren().setAll(header, hint);

        if (c instanceof DatapackChoice) {
            Label more = new Label("Pick biomes, spacing and terrain fitting, and either one structure (with random variants) or a "
                    + "village-style layout: a centre, streets and houses joined by jigsaw blocks, like vanilla villages.");
            more.setWrapText(true);
            more.setMinHeight(Region.USE_PREF_SIZE);
            Button open = new Button("Set up data pack…", new FontIcon(Feather.GLOBE));
            open.getStyleClass().add("accent");
            open.setOnAction(e -> {
                remember();
                setResult(new Outcome(List.of(), 0, true));
                close();
            });
            detail.getChildren().addAll(more, open);
            return;
        }

        // What
        VBox what = new VBox(6);
        if (fixedLayers == null) {
            for (var t : sourceGroup.getToggles()) {
                RadioButton rb = (RadioButton) t;
                Source s = (Source) rb.getUserData();
                rb.setDisable(s == Source.EACH && c instanceof PluginChoice);
                what.getChildren().add(rb);
            }
            if (c instanceof PluginChoice && source() == Source.EACH) selectSource(Source.VISIBLE);
        } else {
            Label fixed = new Label(fixedLayers.size() == 1 ? "Layer “" + fixedLayers.getFirst().name() + "”" : fixedLayers.size() + " layers");
            what.getChildren().add(fixed);
        }
        what.getChildren().add(summary);
        pluginEditor = null;
        if (c instanceof PluginChoice pc) {
            what.getChildren().add(pluginSummary);
            PluginExporter ex = pc.export().exporter();
            if (!ex.options().isEmpty()) {
                pluginEditor = new OptionsEditor(pluginOptions.load(optionsKey(pc), ex.options(), blocks), blocks,
                        () -> ws.selectedBlockProperty().get(), v -> {
                }).icons(ws);
            }
        }

        // Options
        GridPane opts = new GridPane();
        opts.setHgap(12);
        opts.setVgap(8);
        int r = 0;
        opts.addRow(r++, fieldLabel("Minecraft version"), version);
        if (c instanceof FormatChoice fc && fc.format() == Schematics.VANILLA) opts.add(includeAir, 1, r++);
        if (c instanceof FormatChoice fc && fc.format() == Schematics.SPONGE) opts.addRow(r++, fieldLabel("WorldEdit format"), sponge);
        if (pluginEditor != null) opts.add(pluginEditor, 0, r++, 2, 1);

        // Where
        rebuildFolders(c);
        Button browse = new Button("Browse…", new FontIcon(Feather.FOLDER));
        browse.setMinWidth(Region.USE_PREF_SIZE);
        browse.setOnAction(e -> browse());
        HBox folderRow = new HBox(8, folder, browse);
        HBox.setHgrow(folder, Priority.ALWAYS);
        Label ext = new Label();
        ext.getStyleClass().add("export-ext");
        ext.setText(extensionLabel(c));
        HBox nameRow = new HBox(6, name, ext);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(name, Priority.ALWAYS);
        GridPane where = new GridPane();
        where.setHgap(12);
        where.setVgap(8);
        where.addRow(0, fieldLabel("Name"), nameRow);
        where.addRow(1, fieldLabel("Folder"), folderRow);
        where.add(pathPreview, 1, 2);
        GridPane.setHgrow(nameRow, Priority.ALWAYS);
        GridPane.setHgrow(folderRow, Priority.ALWAYS);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        HBox status = new HBox(8, busy, progressText, error);
        status.setAlignment(Pos.CENTER_LEFT);
        detail.getChildren().addAll(section("What"), what, section("Options"), opts, section("Save to"), where, grow, status);
        refresh();
    }

    /** A form label that never truncates to "…" when space is tight. */
    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    private static Label section(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("viewport-settings-section");
        return l;
    }

    private static String hintFor(Choice c) {
        if (c instanceof DatapackChoice) return "Makes your build generate naturally in new chunks, like villages and temples.";
        if (c instanceof PluginChoice pc) return "Added by the plugin “" + pc.export().plugin().info().name() + "”.";
        SchematicFormat f = ((FormatChoice) c).format();
        if (f == Schematics.VANILLA) return "Put it in .minecraft/schematics and load it at a Create Schematic Table; also works with structure blocks and worldgen.";
        if (f == Schematics.LITEMATICA) return "Shows as a ghost overlay in Litematica. “Each layer separately” keeps layers as separate regions in one file.";
        if (f == Schematics.SPONGE) return "Goes in config/worldedit/schematics; load with //schem load <name>, then //paste.";
        return "Added by a plugin.";
    }

    private String extensionLabel(Choice c) {
        if (c instanceof PluginChoice pc) return pc.export().exporter().writesFolder() ? "(folder)" : "." + pc.export().exporter().extension();
        if (c instanceof FormatChoice fc) {
            String e = "." + fc.format().extensions().getFirst();
            return source() == Source.EACH && fc.format() != Schematics.LITEMATICA ? "_<layer>" + e : e;
        }
        return "";
    }

    /** Folders to offer for a card: last used, then each Minecraft instance's folder for this kind of file. */
    private void rebuildFolders(Choice c) {
        Folder keep = folder.getValue();
        List<Folder> out = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        String last = ws.settings().exportFolders.get(c.key());
        if (last != null && !last.isBlank()) add(out, seen, "Last used · " + last, Path.of(last));
        if (scan != null) {
            for (McInstallLocator.Instance i : scan.instances()) {
                Path dir = c.kind() == FormatIcons.Kind.WORLDEDIT
                        ? i.gameDir().resolve("config").resolve("worldedit").resolve("schematics")
                        : i.schematicsDir();
                if (c instanceof PluginChoice) continue;
                String what = c.kind() == FormatIcons.Kind.WORLDEDIT ? "WorldEdit schematics" : "schematics";
                add(out, seen, i.name() + " (" + i.mcVersion() + ") · " + what + (Files.isDirectory(dir) ? "" : " (will be created)"), dir);
            }
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !(c instanceof PluginChoice)) {
            Path mc = Path.of(appData, ".minecraft");
            if (Files.isDirectory(mc)) {
                Path dir = c.kind() == FormatIcons.Kind.WORLDEDIT ? mc.resolve("config/worldedit/schematics") : mc.resolve("schematics");
                add(out, seen, "Official launcher · " + (c.kind() == FormatIcons.Kind.WORLDEDIT ? "WorldEdit schematics" : "schematics"), dir);
            }
        }
        Path docs = Path.of(System.getProperty("user.home"), "Documents");
        add(out, seen, "Documents", Files.isDirectory(docs) ? docs : Path.of(System.getProperty("user.home")));
        if (keep != null && keep.label().startsWith("Chosen") && !seen.contains(keep.path())) out.addFirst(keep);
        folder.getItems().setAll(out);
        folder.setValue(keep != null && out.contains(keep) ? keep : out.getFirst());
    }

    private static void add(List<Folder> out, Set<Path> seen, String label, Path p) {
        Path n = p.toAbsolutePath().normalize();
        if (seen.add(n)) out.add(new Folder(label, n));
    }

    private void browse() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Export into…");
        Folder f = folder.getValue();
        if (f != null && Files.isDirectory(f.path())) dc.setInitialDirectory(f.path().toFile());
        File dir = dc.showDialog(getDialogPane().getScene().getWindow());
        if (dir == null) return;
        Folder chosen = new Folder("Chosen · " + dir.getAbsolutePath(), dir.toPath().toAbsolutePath().normalize());
        folder.getItems().removeIf(x -> x.label().startsWith("Chosen"));
        folder.getItems().addFirst(chosen);
        folder.setValue(chosen);
    }

    // ---- summary & path --------------------------------------------------------------------------------------

    private List<Layer> chosenLayers() {
        if (fixedLayers != null) return fixedLayers;
        return switch (source()) {
            case ACTIVE -> ws.activeLayerProperty().get() == null ? List.of() : List.of(ws.activeLayerProperty().get());
            case SELECTED -> {
                List<Layer> sel = ws.scene().layers().stream().filter(ws.selectedLayers()::contains).toList();
                yield sel.isEmpty() && ws.activeLayerProperty().get() != null ? List.of(ws.activeLayerProperty().get()) : sel;
            }
            case VISIBLE, EACH -> ws.scene().layers().stream().filter(Layer::visible).toList();
        };
    }

    private void refresh() {
        if (current == null || current instanceof DatapackChoice) return;
        List<Layer> ls = chosenLayers();
        long blocks = 0;
        Box box = null;
        Set<BlockState> kinds = new HashSet<>();
        for (Layer l : ls) {
            blocks += l.structure().blockCount();
            Box b = l.worldBounds().orElse(null);
            if (b != null) box = box == null ? b : box.union(b);
            kinds.addAll(l.structure().usedStates());
        }
        Set<String> ids = new HashSet<>();
        for (BlockState k : kinds) ids.add(k.name());
        summary.setText(ls.isEmpty() ? "Nothing to export for this choice."
                : String.format("%d layer%s · %,d block%s · %s · %d block type%s", ls.size(), ls.size() == 1 ? "" : "s", blocks, blocks == 1 ? "" : "s",
                box == null ? "empty" : box.sizeX() + " × " + box.sizeY() + " × " + box.sizeZ(), ids.size(), ids.size() == 1 ? "" : "s"));
        summary.getStyleClass().removeAll("export-summary-empty");
        if (ls.isEmpty() || blocks == 0) summary.getStyleClass().add("export-summary-empty");
        refreshPluginSummary(ls, blocks);
        updatePath();
    }

    private static String optionsKey(PluginChoice pc) {
        return io.blockdesigner.app.plugins.OptionStore.key(pc.export().plugin().info().id(), "exporter", pc.export().exporter().id());
    }

    /** The plugin exporter's own line about what it would write (skipped for huge builds: it needs them merged). */
    private void refreshPluginSummary(List<Layer> ls, long blocks) {
        String text = null;
        if (current instanceof PluginChoice pc && !ls.isEmpty() && blocks > 0 && blocks <= 2_000_000) {
            try {
                text = pc.export().exporter().summary(Scene.flatten(ls));
            } catch (RuntimeException e) {
                text = "✖ " + e.getMessage();
            }
        }
        boolean show = text != null && !text.isBlank();
        pluginSummary.setText(show ? text : "");
        pluginSummary.setVisible(show);
        pluginSummary.setManaged(show);
    }

    private void updatePath() {
        if (current == null || current instanceof DatapackChoice || folder.getValue() == null) return;
        Path dir = folder.getValue().path();
        String n = MainWindow.safeName(name.getText());
        if (current instanceof FormatChoice fc && source() == Source.EACH && fc.format() != Schematics.LITEMATICA && fixedLayers == null) {
            pathPreview.setText("One file per layer in " + dir + ", named " + n + "_<layer name>." + fc.format().extensions().getFirst());
            return;
        }
        Path p = target(dir, n);
        pathPreview.setText(p + (Files.exists(p) ? "   · exists, will be replaced" : ""));
    }

    private Path target(Path dir, String n) {
        if (current instanceof PluginChoice pc) {
            PluginExporter ex = pc.export().exporter();
            return ex.writesFolder() ? dir.resolve(n) : dir.resolve(n + "." + ex.extension());
        }
        return dir.resolve(n + "." + ((FormatChoice) current).format().extensions().getFirst());
    }

    // ---- writing ---------------------------------------------------------------------------------------------

    private void remember() {
        ws.settings().exportChoice = current.key();
        ws.settings().exportSource = source().name();
        ws.settings().exportIncludeAir = includeAir.isSelected();
        ws.settings().exportSpongeVersion = sponge.getValue();
    }

    private void runExport() {
        if (current instanceof DatapackChoice) return;
        List<Layer> ls = chosenLayers();
        if (ls.isEmpty() || ls.stream().allMatch(l -> l.structure().blockCount() == 0)) {
            error.setText("Nothing to export: the chosen layers are empty.");
            return;
        }
        Folder f = folder.getValue();
        if (f == null) {
            error.setText("Choose a folder.");
            return;
        }
        String n = MainWindow.safeName(name.getText());
        Path dir = f.path();
        remember();
        ws.settings().exportFolders.put(current.key(), dir.toString());
        String author = ws.settings().author;
        McVersion v = version.getValue();
        Choice c = current;
        Source src = fixedLayers != null && fixedLayers.size() > 1 ? Source.VISIBLE : fixedLayers != null ? Source.ACTIVE : source();
        WriteOptions wo = new WriteOptions(v, includeAir.isSelected(), sponge.getValue());
        // Flatten on the FX thread (the scene isn't thread-safe); encoding and writing happen in the background.
        // Plugin exporters get their own copies of the layers, so they can read them safely off the FX thread.
        List<Layer> copies = c instanceof PluginChoice ? ls.stream().map(l -> l.duplicate(l.name())).toList() : List.copyOf(ls);
        Map<String, Structure> perLayer = new LinkedHashMap<>();
        Structure merged = null;
        if (src == Source.EACH) {
            for (Layer l : copies) perLayer.put(l.name(), Scene.flatten(List.of(l)));
        } else {
            merged = Scene.flatten(copies);
        }
        Structure mergedFinal = merged;
        // The assets as they are now (the property belongs to the FX thread; the export runs off it).
        io.blockdesigner.assets.BlockAssets loaded = ws.assets();
        io.blockdesigner.plugin.AssetAccess assetAccess = new io.blockdesigner.app.plugins.AppAssetAccess(() -> loaded);
        io.blockdesigner.plugin.OptionValues optionValues = null;
        if (c instanceof PluginChoice pc) {
            optionValues = pluginEditor != null ? pluginEditor.values() : pc.export().exporter().options().defaults();
            pluginOptions.save(optionsKey(pc), optionValues);
        }
        io.blockdesigner.plugin.OptionValues optionsFinal = optionValues;
        // Progress messages come from the export thread; the latest one is shown on the next pulse.
        io.blockdesigner.plugin.Progress progress = (fraction, message) -> Platform.runLater(() -> {
            busy.setProgress(fraction < 0 ? ProgressIndicator.INDETERMINATE_PROGRESS : Math.min(1, fraction));
            if (message != null) progressText.setText(message);
        });
        busy.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressText.setText("");
        busy.setVisible(true);
        error.setText("");
        getDialogPane().lookupAll(".button").forEach(b -> b.setDisable(true));
        CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(dir);
                return write(c, src, dir, n, author, v, wo, mergedFinal, perLayer, copies, optionsFinal, progress,
                        assetAccess);
            } catch (IOException | RuntimeException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((out, t) -> Platform.runLater(() -> {
            busy.setVisible(false);
            progressText.setText("");
            getDialogPane().lookupAll(".button").forEach(b -> b.setDisable(false));
            if (t != null) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                error.setText("Export failed: " + cause.getMessage());
                return;
            }
            setResult(out);
            close();
        }));
    }

    private static Outcome write(Choice c, Source src, Path dir, String n, String author, McVersion v, WriteOptions wo, Structure merged,
                                 Map<String, Structure> perLayer, List<Layer> layers, io.blockdesigner.plugin.OptionValues options,
                                 io.blockdesigner.plugin.Progress progress, io.blockdesigner.plugin.AssetAccess assets) throws IOException {
        List<Path> written = new ArrayList<>();
        long blocks = 0;
        if (c instanceof PluginChoice pc) {
            PluginExporter ex = pc.export().exporter();
            Path t = ex.writesFolder() ? dir.resolve(n) : dir.resolve(n + "." + ex.extension());
            if (ex.writesFolder()) Files.createDirectories(t);
            ex.export(new PluginExporter.Request(n, author, v, merged, List.copyOf(layers), t, options, progress, assets));
            return new Outcome(List.of(t), merged.blockCount(), false);
        }
        SchematicFormat format = ((FormatChoice) c).format();
        String ext = format.extensions().getFirst();
        if (src == Source.EACH && format == Schematics.LITEMATICA) {
            List<SchematicFile.Region> regions = new ArrayList<>();
            for (var e : perLayer.entrySet()) {
                regions.add(new SchematicFile.Region(e.getKey(), e.getValue(), BlockPos.ORIGIN));
                blocks += e.getValue().blockCount();
            }
            Path t = dir.resolve(n + "." + ext);
            Schematics.write(new SchematicFile(n, author, "", v.dataVersion(), regions), format, wo, t);
            written.add(t);
        } else if (src == Source.EACH) {
            Set<String> used = new HashSet<>();
            for (var e : perLayer.entrySet()) {
                String base = n + "_" + MainWindow.safeName(e.getKey());
                String fn = base;
                for (int i = 2; !used.add(fn); i++) fn = base + "_" + i;
                Path t = dir.resolve(fn + "." + ext);
                Schematics.write(new SchematicFile(e.getKey(), author, "", v.dataVersion(),
                        List.of(new SchematicFile.Region(e.getKey(), e.getValue(), BlockPos.ORIGIN))), format, wo, t);
                written.add(t);
                blocks += e.getValue().blockCount();
            }
        } else {
            Path t = dir.resolve(n + "." + ext);
            Schematics.write(new SchematicFile(n, author, "", v.dataVersion(), List.of(new SchematicFile.Region(n, merged, BlockPos.ORIGIN))), format, wo, t);
            written.add(t);
            blocks = merged.blockCount();
        }
        return new Outcome(written, blocks, false);
    }

    /** A small card icon for menus: the kind of a format. */
    public static Node menuIcon(SchematicFormat f) {
        return FormatIcons.icon(FormatIcons.kindOf(f), 16);
    }
}
