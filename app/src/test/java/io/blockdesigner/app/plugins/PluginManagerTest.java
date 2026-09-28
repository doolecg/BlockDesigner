package io.blockdesigner.app.plugins;

import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.formats.SchematicFile;
import io.blockdesigner.core.formats.Schematics;
import io.blockdesigner.core.formats.WriteOptions;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.core.version.McVersion;
import io.blockdesigner.core.worldedit.WorldEdit;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.PluginExporter;
import io.blockdesigner.plugin.SceneEvent;
import io.blockdesigner.plugin.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real example plugins and exercises every extension point: examples/hello-plugin (API 1: formats,
 * exporters, actions, commands) and examples/palette-tools (API 2: transforms, a panel, scene events, the catalog).
 */
class PluginManagerTest {
    @TempDir
    Path dir;

    private final Scene scene = new Scene();
    private final SceneEditor editor = new SceneEditor(scene);
    private final Map<BlockPos, BlockState> world = new HashMap<>();
    private final Set<String> disabled = new LinkedHashSet<>();
    private PluginManager pm;
    /** Transforms the host was asked to open (by /transform). */
    private final List<String> opened = new ArrayList<>();
    /** What plugins asked of the host through the API 6 UI calls, in order. */
    private final List<String> uiCalls = new ArrayList<>();
    /** The forms the host made (API 6), last one last. */
    private final List<FakeForm> forms = new ArrayList<>();

    /** A form without JavaFX: remembers what it was given, and lets the test play the user. */
    static final class FakeForm implements io.blockdesigner.plugin.ui.OptionsForm {
        OptionValues values;
        final Consumer<OptionValues> onChange;

        FakeForm(OptionValues initial, Consumer<OptionValues> onChange) {
            this.values = initial;
            this.onChange = onChange;
        }

        void userSets(OptionValues v) {
            values = v;
            onChange.accept(v);
        }

        public javafx.scene.Node node() {
            return null;
        }

        public OptionValues values() {
            return values;
        }

        public void setValues(OptionValues v) {
            values = v;
        }

        public void reset() {
            userSets(values.options().defaults());
        }
    }

    private final PluginHost host = new PluginHost() {
        public Scene scene() {
            return scene;
        }

        public SceneEditor editor() {
            return editor;
        }

        public Optional<Layer> activeLayer() {
            return scene.active();
        }

        public List<Layer> selectedLayers() {
            return scene.active().map(List::of).orElse(List.of());
        }

        public McVersion targetVersion() {
            return McVersion.latestKnown();
        }

        public void editWorld(String label, Consumer<WorldEdit.World> edit) {
            edit.accept(worldView());
        }

        public Layer addLayer(String name, Structure blocks) {
            Layer l = new Layer(name, blocks);
            editor.addLayer(l);
            return l;
        }

        public void status(String message) {
        }

        public void toast(String message) {
        }

        public void runOnUiThread(Runnable task) {
            task.run();
        }

        public void pluginsChanged() {
        }

        public void openTransform(PluginManager.Transform t) {
            opened.add(t.transform().id());
        }

        public io.blockdesigner.plugin.ui.OptionsForm optionsForm(PluginManager.Plugin plugin, io.blockdesigner.plugin.Options options,
                                                                  OptionValues initial, Consumer<OptionValues> onChange) {
            FakeForm f = new FakeForm(initial, onChange);
            forms.add(f);
            return f;
        }

        public void showPanel(PluginManager.Plugin plugin, String panelId) {
            uiCalls.add("showPanel " + plugin.info().id() + " " + panelId);
        }

        public void panelStatusChanged(PluginManager.Plugin plugin) {
            uiCalls.add("status " + plugin.info().id());
        }

        public void openSettings(PluginManager.Plugin plugin) {
            uiCalls.add("openSettings " + plugin.info().id());
        }

        public void pluginSettingsChanged(PluginManager.Plugin plugin) {
            uiCalls.add("settingsChanged " + plugin.info().id());
        }

        public void openFile(PluginManager.Plugin plugin, Path file, Consumer<io.blockdesigner.plugin.OpenResult> done) {
            uiCalls.add("openFile " + plugin.info().id() + " " + file.getFileName());
            done.accept(file.getFileName().toString().startsWith("bad") ? io.blockdesigner.plugin.OpenResult.failed("Could not read " + file.getFileName())
                    : io.blockdesigner.plugin.OpenResult.opened());
            done.accept(io.blockdesigner.plugin.OpenResult.cancelled());   // a second answer must not reach the plugin
        }

        public void toFront() {
            uiCalls.add("toFront");
        }
    };

    private WorldEdit.World worldView() {
        return new WorldEdit.World() {
            public BlockState get(BlockPos p) {
                return world.getOrDefault(p, BlockState.AIR);
            }

            public void set(BlockPos p, BlockState s) {
                world.put(p, s);
            }
        };
    }

    private static Path exampleJar(String property, String fallbackDir, String glob) throws IOException {
        Path libs = Path.of(System.getProperty(property, fallbackDir));
        Path jar = null;
        // The newest one: builds of earlier versions stay in build/libs next to it.
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(libs, glob)) {
            for (Path p : ds) if (jar == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(jar)) > 0) jar = p;
        }
        assertThat(jar).as("example plugin jar in " + libs).isNotNull();
        return jar;
    }

    @BeforeEach
    void install() throws IOException {
        Path plugins = dir.resolve("plugins");
        Files.createDirectories(plugins);
        Files.copy(exampleJar("blockdesigner.examplePluginDir", "../examples/hello-plugin/build/libs", "hello-plugin*.jar"), plugins.resolve("hello.jar"));
        Files.copy(exampleJar("blockdesigner.paletteToolsDir", "../examples/palette-tools/build/libs", "palette-tools*.jar"), plugins.resolve("palette-tools.jar"));
        pm = new PluginManager(plugins, host, disabled);
        pm.loadAll();
    }

    @AfterEach
    void shutdown() {
        pm.shutdown();
    }

    @Test
    void loadsAndRegistersEverything() {
        var p = pm.find("hello").orElseThrow();
        assertThat(p.state()).as(String.valueOf(p.error())).isEqualTo(PluginManager.State.ENABLED);
        assertThat(p.info().name()).isEqualTo("Hello Plugin");
        assertThat(WorldEdit.commands()).anyMatch(c -> c.name().equals("pillar"));
        assertThat(Schematics.byId("hello-text")).isPresent();
        assertThat(pm.exporters()).extracting(e -> e.exporter().id()).containsExactly("bom", "gpl");
        assertThat(pm.actions()).extracting(a -> a.action().label()).containsExactly("Add test platform");
        assertThat(p.log()).anyMatch(l -> l.contains("Hello from Hello Plugin"));
    }

    @Test
    void commandEditsTheWorldAndValidatesInput() {
        WorldEdit we = new WorldEdit();
        var ctx = new WorldEdit.Context(worldView(), BlockPlacement.Dir.NORTH, new BlockPos(1, 64, 1), BlockState.of("oak_planks"),
                s -> BlockState.parse(s.contains(":") ? s : "minecraft:" + s));
        var r = we.run("/pillar 3", ctx);
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(world).containsEntry(new BlockPos(1, 65, 1), BlockState.of("oak_planks")).containsEntry(new BlockPos(1, 67, 1), BlockState.of("oak_planks"));
        assertThat(we.run("/pillar tall", ctx).ok()).isFalse();
        assertThat(we.run("/help pillar", ctx).message()).contains("/pillar <height>");
    }

    @Test
    void actionAddsALayer() {
        pm.run(pm.actions().getFirst());
        assertThat(scene.layers()).hasSize(1);
        assertThat(scene.layers().getFirst().structure().blockCount()).isEqualTo(81);
    }

    @Test
    void pluginFormatAndExporterWriteFiles() throws IOException {
        Structure s = new Structure();
        s.set(0, 0, 0, BlockState.of("stone"));
        s.set(2, 1, 0, BlockState.parse("oak_stairs[facing=east]"));
        s.set(3, 1, 0, BlockState.of("stone"));
        var fmt = Schematics.byId("hello-text").orElseThrow();
        Path f = dir.resolve("test.bdtxt");
        Schematics.write(SchematicFile.single("test", s), fmt, WriteOptions.defaults(McVersion.latestKnown()), f);
        var back = Schematics.readDetailed(f);
        assertThat(back.format()).isSameAs(fmt);
        assertThat(back.file().merged().contentEquals(s)).isTrue();

        PluginExporter bom = pm.exporters().getFirst().exporter();
        Path csv = dir.resolve("bom.csv");
        bom.export(new PluginExporter.Request("x", "", McVersion.latestKnown(), s, List.of(), csv));
        assertThat(Files.readString(csv)).startsWith("block,count,stacks\nminecraft:stone,2,");
    }

    @Test
    void disablingRemovesRegistrationsAndIsRemembered() {
        var p = pm.find("hello").orElseThrow();
        pm.setEnabled(p, false);
        assertThat(p.state()).isEqualTo(PluginManager.State.DISABLED);
        assertThat(disabled).contains("hello");
        assertThat(WorldEdit.commands()).noneMatch(c -> c.name().equals("pillar"));
        assertThat(Schematics.byId("hello-text")).isEmpty();
        assertThat(pm.exporters()).noneMatch(e -> e.plugin() == p);
        pm.loadAll();
        assertThat(pm.find("hello").orElseThrow().state()).isEqualTo(PluginManager.State.DISABLED);
        pm.setEnabled(pm.find("hello").orElseThrow(), true);
        assertThat(Schematics.byId("hello-text")).isPresent();
    }

    // ---- API 2 (palette-tools) ------------------------------------------------------------------------------------

    @Test
    void apiTwoPluginRegistersTransformsAndAPanel() {
        var p = pm.find("palette-tools").orElseThrow();
        assertThat(p.state()).as(String.valueOf(p.error())).isEqualTo(PluginManager.State.ENABLED);
        assertThat(p.info().api()).isEqualTo(2);
        assertThat(p.contributions()).isEqualTo("1 exporter · 1 importer · 1 tool · 3 transforms · 1 panel");
        assertThat(pm.transforms()).extracting(t -> t.key()).containsExactly("palette-tools/weather", "palette-tools/palette_swap", "palette-tools/gradient");
        assertThat(pm.panels()).extracting(x -> x.key()).containsExactly("palette-tools/palette");
        assertThat(pm.findTransform("weather")).isPresent();
        assertThat(pm.findTransform("palette-tools/gradient")).isPresent();

        // /transform lists them, and opens one by id (the host shows the dialog).
        WorldEdit we = new WorldEdit();
        var ctx = new WorldEdit.Context(worldView(), BlockPlacement.Dir.NORTH, null, null, s -> BlockState.parse(s));
        assertThat(we.run("/transform", ctx).message()).contains("weather", "palette_swap", "gradient");
        assertThat(we.run("/transform weather", ctx).ok()).isTrue();
        assertThat(opened).containsExactly("weather");
        assertThat(we.run("/transform nope", ctx).ok()).isFalse();

        // Disabling removes them all, including /transform.
        pm.setEnabled(p, false);
        assertThat(pm.transforms()).isEmpty();
        assertThat(pm.panels()).isEmpty();
        assertThat(WorldEdit.commands()).noneMatch(c -> c.name().equals("transform"));
    }

    private TransformRunner.Target wallTarget(BlockState block) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
            BlockPos pos = new BlockPos(x, y, 0);
            world.put(pos, block);
            cells.add(pos);
        }
        return TransformRunner.Target.of(cells, "wall", null);
    }

    @Test
    void weatheringPreviewsWithoutEditingAndAppliesTheSameResult() throws Exception {
        var weather = pm.findTransform("weather").orElseThrow().transform();
        var target = wallTarget(BlockState.of("stone_bricks"));
        Map<BlockPos, BlockState> before = new HashMap<>(world);
        OptionValues all = weather.options().defaults().with("amount", 1.0);
        var preview = TransformRunner.run(weather, worldView(), target, all, 3, pm.blocks(), true);
        assertThat(world).as("a preview writes nothing").isEqualTo(before);
        assertThat(preview.size()).isEqualTo(64);
        assertThat(preview.blocks().values()).allMatch(s -> s.path().equals("cracked_stone_bricks") || s.path().equals("mossy_stone_bricks"))
                .anyMatch(s -> s.path().startsWith("mossy")).anyMatch(s -> s.path().startsWith("cracked"));

        var applied = TransformRunner.run(weather, worldView(), target, all, 3, pm.blocks(), false);
        assertThat(applied).isEqualTo(preview);
        applied.writeTo(worldView());
        assertThat(world).containsAllEntriesOf(preview.blocks());

        var none = TransformRunner.run(weather, worldView(), target, weather.options().defaults().with("amount", 0.0), 3, pm.blocks(), true);
        assertThat(none.isEmpty()).isTrue();
    }

    @Test
    void paletteSwapKeepsShapesAndFacing() throws Exception {
        var swap = pm.findTransform("palette_swap").orElseThrow().transform();
        BlockState stairs = BlockState.parse("oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
        List<BlockPos> cells = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        world.put(cells.get(0), stairs);
        world.put(cells.get(1), BlockState.of("oak_planks"));
        world.put(cells.get(2), BlockState.of("stone"));
        var changes = TransformRunner.run(swap, worldView(), TransformRunner.Target.of(cells, "three", null), swap.options().defaults(), 0, pm.blocks(), false);
        assertThat(changes.blocks()).containsOnlyKeys(cells.get(0), cells.get(1))
                .containsEntry(cells.get(0), stairs.withName("minecraft:spruce_stairs"))
                .containsEntry(cells.get(1), BlockState.of("spruce_planks"));
    }

    @Test
    void gradientRepaintsBottomToTop() throws Exception {
        var gradient = pm.findTransform("gradient").orElseThrow().transform();
        var target = wallTarget(BlockState.of("stone"));
        var c = TransformRunner.run(gradient, worldView(), target, gradient.options().defaults().with("blend", 0.0), 1, pm.blocks(), false);
        assertThat(c.blocks().get(new BlockPos(0, 0, 0))).isEqualTo(BlockState.of("deepslate_bricks"));
        assertThat(c.blocks().get(new BlockPos(0, 7, 0))).isEqualTo(BlockState.of("diorite"));
    }

    @Test
    void sceneEventsReachPluginsUntilTheyAreDisabled() {
        var p = pm.find("palette-tools").orElseThrow();
        List<SceneEvent> got = new ArrayList<>();
        Subscription sub = p.context().on(SceneEvent.class, got::add);
        Layer l = new Layer("a", new Structure());
        editor.addLayer(l);
        try (var s = editor.edit(l, "x")) {
            s.set(1, 2, 3, BlockState.of("stone"));
        }
        assertThat(got).anyMatch(e -> e instanceof SceneEvent.LayersChanged)
                .anyMatch(e -> e instanceof SceneEvent.BlocksChanged b && b.dirty().equals(new io.blockdesigner.core.model.Box(1, 2, 3, 1, 2, 3)));
        pm.selectionChanged();
        assertThat(got.getLast()).isEqualTo(new SceneEvent.SelectionChanged(Optional.empty()));
        pm.projectOpened(Optional.empty());
        assertThat(got.getLast()).isInstanceOf(SceneEvent.ProjectOpened.class);

        sub.cancel();
        int n = got.size();
        pm.selectionChanged();
        assertThat(got).hasSize(n);
        p.context().on(SceneEvent.class, got::add);
        pm.setEnabled(p, false);
        pm.selectionChanged();
        assertThat(got).as("a disabled plugin hears nothing").hasSize(n);
    }

    @Test
    void exporterOptionsSummaryAndProgress() throws IOException {
        PluginExporter gpl = pm.exporters().stream().filter(e -> e.exporter().id().equals("gpl")).findFirst().orElseThrow().exporter();
        Structure s = new Structure();
        for (int x = 0; x < 5; x++) s.set(x, 0, 0, BlockState.of("stone"));
        s.set(0, 1, 0, BlockState.of("dirt"));
        assertThat(gpl.summary(s)).isEqualTo("2 kinds of block");
        List<Double> progress = new ArrayList<>();
        Path out = dir.resolve("p.gpl");
        OptionValues opts = gpl.options().defaults().with("min", 2);
        gpl.export(new PluginExporter.Request("pal", "", McVersion.latestKnown(), s, List.of(), out, opts, (f, m) -> progress.add(f), null));
        String text = Files.readString(out);
        assertThat(text).startsWith("GIMP Palette").contains("Stone (5)").doesNotContain("Dirt");
        assertThat(progress).containsExactly(1.0);
        gpl.export(new PluginExporter.Request("pal", "", McVersion.latestKnown(), s, List.of(), out, gpl.options().defaults(), null, null));
        assertThat(Files.readString(out)).as("defaults keep every block").contains("Dirt (1)");
    }

    @Test
    void importerTurnsAnImageIntoBlocks() throws IOException {
        Path png = dir.resolve("heart.png");
        var img = new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 3; x++) img.setRGB(x, 0, 0xFFFF0000);
        img.setRGB(1, 1, 0xFFFF0000); // the rest of the bottom row stays transparent
        javax.imageio.ImageIO.write(img, "png", png.toFile());
        var imp = pm.importerFor(png).orElseThrow();
        assertThat(imp.key()).isEqualTo("palette-tools/pixel_art");
        assertThat(pm.importerFor(dir.resolve("x.litematic"))).isEmpty();
        List<Double> progress = new ArrayList<>();
        var layers = imp.importer().importFile(png, imp.importer().options().defaults(), (f, m) -> progress.add(f), pm.blocks());
        assertThat(layers).hasSize(1);
        Structure s = layers.getFirst().blocks();
        assertThat(layers.getFirst().name()).isEqualTo("heart");
        assertThat(s.blockCount()).isEqualTo(4);
        assertThat(s.get(1, 0, 0).path()).endsWith("_concrete");
        assertThat(s.get(0, 1, 0)).as("top row of the image is the top of the art").isEqualTo(s.get(1, 0, 0));
        assertThat(progress).containsExactly(0.5, 1.0);
    }

    // ---- API 6: plugin UI ------------------------------------------------------------------------------------------

    private PluginManager.Plugin palette() {
        return pm.find("palette-tools").orElseThrow();
    }

    @Test
    void updateSettingsKeepsTheValuesTellsThePluginAndTheHost() {
        var ctx = palette().context();
        io.blockdesigner.plugin.Options o = io.blockdesigner.plugin.Options.builder()
                .group("Counting").toggle("mobs", "Count mobs", false).integer("rows", "Rows", 4, 1, 9).build();
        List<OptionValues> heard = new ArrayList<>();
        ctx.registerSettings(o, heard::add);
        assertThat(heard).hasSize(1);
        ctx.updateSettings(v -> v.with("mobs", true));
        assertThat(heard).hasSize(2);
        assertThat(heard.getLast().toggle("mobs")).isTrue();
        assertThat(ctx.settings().toggle("mobs")).isTrue();
        assertThat(pm.optionStore().load("palette-tools/settings/tab", o, pm.blocks()).toggle("mobs")).as("kept").isTrue();
        assertThat(uiCalls).containsExactly("settingsChanged palette-tools");
        ctx.updateSettings(v -> v.with("mobs", true));
        assertThat(heard).as("no change, no call").hasSize(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pm.find("hello").orElseThrow().context().updateSettings(v -> v))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rememberedFormsShareTheImportersValues() {
        var ctx = palette().context();
        var importer = pm.importers().getFirst().importer();
        String key = OptionStore.key("palette-tools", "importer", importer.id());
        OptionValues saved = importer.options().defaults();
        String someInt = importer.options().all().stream().filter(x -> x instanceof io.blockdesigner.plugin.Options.IntegerOption)
                .findFirst().orElseThrow().key();
        var opt = (io.blockdesigner.plugin.Options.IntegerOption) importer.options().get(someInt).orElseThrow();
        saved = saved.with(someInt, opt.max());
        pm.optionStore().save(key, saved);

        List<OptionValues> heard = new ArrayList<>();
        var form = ctx.ui().optionsForm(importer.options(), "importer/" + importer.id(), heard::add);
        assertThat(form.values().integer(someInt)).as("starts from the Import window's values").isEqualTo(opt.max());
        forms.getLast().userSets(form.values().with(someInt, opt.min()));
        assertThat(heard).hasSize(1);
        assertThat(pm.optionStore().load(key, importer.options(), pm.blocks()).integer(someInt)).as("saved for the importer").isEqualTo(opt.min());
        form.setValues(form.values().with(someInt, opt.max()));
        assertThat(heard).as("setValues doesn't call onChange").hasSize(1);
        assertThat(pm.optionStore().load(key, importer.options(), pm.blocks()).integer(someInt)).isEqualTo(opt.max());

        var unremembered = ctx.ui().optionsForm(importer.options(), null, v -> {
        });
        assertThat(unremembered.values()).isEqualTo(importer.options().defaults());
        for (String bad : List.of("", "/x", "a//b", "a\\b", "sp ace", "x/")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.ui().optionsForm(importer.options(), bad, v -> {
            })).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(PluginManager.formKey(palette(), "panel/v2")).isEqualTo("palette-tools/panel/v2");
    }

    @Test
    void panelStatusWorksBeforeThePanelIsBuiltAndCanBeCleared() {
        var ctx = palette().context();
        ctx.setPanelStatus("not-built-yet", io.blockdesigner.plugin.ui.Tone.SUCCESS, "Connected to 2 games");
        assertThat(pm.panelStatus(palette(), "not-built-yet")).contains(
                new PluginManager.PanelStatus(io.blockdesigner.plugin.ui.Tone.SUCCESS, "Connected to 2 games"));
        ctx.setPanelStatus("not-built-yet", io.blockdesigner.plugin.ui.Tone.SUCCESS, "Connected to 2 games");
        assertThat(uiCalls).as("the same status twice tells the host once").containsExactly("status palette-tools");
        ctx.setPanelStatus("not-built-yet", null, null);
        assertThat(pm.panelStatus(palette(), "not-built-yet")).isEmpty();
        assertThat(uiCalls).hasSize(2);
        ctx.setPanelStatus("x", io.blockdesigner.plugin.ui.Tone.DANGER, "Failed");
        pm.setEnabled(palette(), false);
        assertThat(pm.panelStatus(palette(), "x")).as("gone with the plugin").isEmpty();
    }

    @Test
    void showPanelAndOpenSettingsReachTheHost() {
        var ctx = palette().context();
        ctx.showPanel("palette");
        ctx.openSettings();
        assertThat(uiCalls).containsExactly("showPanel palette-tools palette", "openSettings palette-tools");
    }

    // ---- API 7: opening files -------------------------------------------------------------------------------------

    @Test
    void openFileReachesTheHostAndAnswersOnce() {
        var ctx = palette().context();
        List<io.blockdesigner.plugin.OpenResult> results = new ArrayList<>();
        ctx.openFile(dir.resolve("Castle.litematic"), results::add);
        ctx.openFile(dir.resolve("bad.schem"), results::add);
        assertThat(uiCalls).containsExactly("openFile palette-tools Castle.litematic", "openFile palette-tools bad.schem");
        assertThat(results).extracting(io.blockdesigner.plugin.OpenResult::status)
                .containsExactly(io.blockdesigner.plugin.OpenResult.Status.OPENED, io.blockdesigner.plugin.OpenResult.Status.FAILED);
        assertThat(results.get(0).isOpened()).isTrue();
        assertThat(results.get(1).message()).isEqualTo("Could not read bad.schem");
    }

    @Test
    void aThrowingOpenFileCallbackIsLoggedNotThrown() {
        var ctx = palette().context();
        ctx.openFile(dir.resolve("Castle.bdproj"), r -> {
            throw new IllegalStateException("boom");
        });
        assertThat(palette().log()).anyMatch(l -> l.contains("Opening Castle.bdproj failed") && l.contains("boom"));
    }

    @Test
    void toFrontReachesTheHost() {
        palette().context().ui().toFront();
        assertThat(uiCalls).containsExactly("toFront");
    }

    @Test
    void importersForListsEveryImporterOfAnExtension() {
        Path png = dir.resolve("a.PNG");
        assertThat(pm.importersFor(png)).hasSize(1);
        pm.find("hello").orElseThrow().context().registerImporter(new io.blockdesigner.plugin.PluginImporter() {
            public String id() {
                return "pictures";
            }

            public String displayName() {
                return "Pictures as blocks";
            }

            public List<String> extensions() {
                return List.of("jpg", "png");
            }

            public List<ImportedLayer> importFile(Path file, OptionValues options, io.blockdesigner.plugin.Progress progress,
                                                  io.blockdesigner.plugin.BlockCatalog blocks) {
                return List.of();
            }
        });
        assertThat(pm.importersFor(png)).extracting(PluginManager.Import::key)
                .containsExactlyInAnyOrder("palette-tools/pixel_art", "hello/pictures");
        assertThat(pm.importersFor(dir.resolve("b.jpg"))).extracting(PluginManager.Import::key).containsExactly("hello/pictures");
        assertThat(pm.importersFor(dir.resolve("c.litematic"))).isEmpty();
        assertThat(pm.importerFor(png)).isPresent();
    }

    @Test
    void pluginsNeedingANewerApiAreRefused() throws IOException {
        Path jar = pm.folder().resolve("future.jar");
        try (var z = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new java.util.zip.ZipEntry("blockdesigner-plugin.json"));
            z.write("{\"id\":\"future\",\"main\":\"x.Y\",\"api\":99}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        pm.loadAll();
        var p = pm.find("future").orElseThrow();
        assertThat(p.state()).isEqualTo(PluginManager.State.INCOMPATIBLE);
        assertThat(p.error()).contains("API 99");
        assertThat(pm.find("hello").orElseThrow().state()).as("API 1 plugins keep working").isEqualTo(PluginManager.State.ENABLED);
    }

    @Test
    void jarsWithoutDescriptorAreReportedNotLoaded() throws IOException {
        Path bad = pm.folder().resolve("junk.jar");
        try (var z = new java.util.zip.ZipOutputStream(Files.newOutputStream(bad))) {
            z.putNextEntry(new java.util.zip.ZipEntry("x.txt"));
            z.write(1);
        }
        pm.loadAll();
        assertThat(pm.brokenJars()).containsKey("junk.jar");
        assertThat(pm.find("hello").orElseThrow().state()).isEqualTo(PluginManager.State.ENABLED);
    }

    /** A plugin jar with only a descriptor (its main class is never loaded in these tests). */
    private static Path descriptorOnly(Path jar, String id) throws IOException {
        try (var z = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            z.putNextEntry(new java.util.zip.ZipEntry("blockdesigner-plugin.json"));
            z.write(("{\"id\":\"" + id + "\",\"name\":\"Reference Planes\",\"version\":\"1.2.0\",\"main\":\"x.Y\",\"api\":6}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return jar;
    }

    @Test
    void builtInPluginsAreSetAsideOnceNotLoaded() throws IOException {
        Path jar = descriptorOnly(pm.folder().resolve("reference-planes-1.2.0.jar"), "reference-planes");
        pm.loadAll();
        assertThat(pm.find("reference-planes")).as("never loaded next to the built-in feature").isEmpty();
        assertThat(pm.retired()).extracting(io.blockdesigner.plugin.PluginInfo::id).containsExactly("reference-planes");
        assertThat(jar).doesNotExist();
        assertThat(pm.folder().resolve("reference-planes-1.2.0.jar.retired")).exists();
        assertThat(pm.brokenJars()).isEmpty();
        assertThat(pm.find("hello").orElseThrow().state()).isEqualTo(PluginManager.State.ENABLED);
        // The next start finds nothing to tell.
        pm.loadAll();
        assertThat(pm.retired()).isEmpty();
        assertThat(pm.find("reference-planes")).isEmpty();
    }

    @Test
    void builtInPluginsAreNotInstalled() throws IOException {
        Path jar = descriptorOnly(dir.resolve("reference-planes-1.2.0.jar"), "reference-planes");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pm.install(jar)).isInstanceOf(IOException.class)
                .hasMessageContaining("built into BlockDesigner");
        assertThat(pm.find("reference-planes")).isEmpty();
        assertThat(pm.folder().resolve("reference-planes-1.2.0.jar")).doesNotExist();
    }
}
