package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.Workspace.ToolKind;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.place.BlockPlacement;
import io.blockdesigner.core.transform.BlockTransformer;
import io.blockdesigner.core.transform.Tilt;
import io.blockdesigner.core.transform.Transform;
import io.blockdesigner.render.Camera;
import io.blockdesigner.render.FrameRequest;
import io.blockdesigner.render.Overlays;
import io.blockdesigner.render.Picker;
import io.blockdesigner.render.SceneRenderer;
import io.blockdesigner.render.ViewportRenderer;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The 3D view. Renders through {@link ViewportRenderer} into a {@link PixelBuffer}-backed image and turns mouse and
 * keyboard input into camera moves, tool actions and layer nudges.
 *
 * <p>Layer nudging: <b>Ctrl+scroll</b> over a layer moves along the axis of the face under the cursor (scrolling up
 * pulls it out of that face, towards you); elsewhere it moves left/right. <b>Ctrl+Shift+scroll</b> moves up/down and
 * <b>Shift+scroll</b> back/forward, relative to the camera. Arrow keys do the same horizontally; hold Tab for bigger steps.
 *
 * <p>Turning: <b>Alt+scroll</b> over a layer's top (or bottom) spins it about the vertical axis, clockwise from above
 * when scrolling up. Over a side it flips the layer a quarter turn about that side's horizontal edge: scrolling up
 * rolls the side you are looking at up to face the sky (bottom comes towards you, top goes away), scrolling down
 * rolls it down. Flipping rewrites the blocks. While placing, Alt+scroll spins the ghost.
 *
 * <p>Slice view: <b>PgUp/PgDn</b> step through Y levels. By default levels build up (everything at or below the
 * current level shows); <b>Insert</b> toggles single-level mode, which shows only the current level.
 */
public final class ViewportPane extends StackPane {

    private final Workspace ws;
    private final Camera camera = new Camera();
    private final ImageView view = new ImageView();
    private final Label toast = new Label();
    private final ShortcutsPanel shortcuts = new ShortcutsPanel();
    private atlantafx.base.controls.Popover shortcutsPopover, selectByTypePopover;
    private final ViewCube viewCube = new ViewCube(this::snapView, this::cubeOrbit, this::toggleOrtho);
    // Blender's auto perspective: an axis view switched to ortho by itself goes back to perspective on orbiting.
    private boolean autoOrtho;
    // Alt+middle-drag: mouse travel toward the next view step
    private boolean viewSwing;
    private double swingX, swingY;
    private static final double SWING_PX = 60;
    // Smooth view changes: from / to angles and when it started (-1 when idle)
    private float animYaw0, animPitch0, animYaw1, animPitch1;
    private long animStart = -1;
    private static final double VIEW_ANIM_MS = 220;
    private final Label sliceBadge = new Label();
    private final Hotbar hotbar;
    private final javafx.scene.control.Button settingsButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.SLIDERS));
    private atlantafx.base.controls.Popover settingsPopover;
    private final javafx.scene.control.Button keysButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.COMMAND));
    private final javafx.scene.control.Button filterButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.FILTER));
    private final FadeTransition toastFade = new FadeTransition(Duration.millis(900), toast);
    private final PauseTransition toastHold = new PauseTransition(Duration.millis(900));
    private final PauseTransition nudgeSeal = new PauseTransition(Duration.millis(650));

    private ViewportRenderer gpu;
    private SceneRenderer sceneRenderer;
    /**
     * Ghost blocks previewing a plugin transform, drawn translucent over the scene. They live in a scene of their own
     * so the preview never shows up in the layer list, undo history, saves or plugin events.
     */
    private final Scene previewScene = new Scene();
    private SceneRenderer previewRenderer;
    private Layer previewLayer;
    /** Cells the preview empties (outlined in red) and extra boxes to outline. */
    private List<BlockPos> previewRemoved = List.of();
    private List<Box> previewOutlines = List.of();
    /** Told when the block selection or the //pos region may have changed; and what was last reported. */
    private Runnable selectionListener = () -> {
    };
    private int lastSelCount = -1;
    private Box lastSelRegion;
    /** The active plugin tool (tool mode PLUGIN), and its options bar above the hotbar. */
    private PluginToolSession pluginTool;
    private final javafx.scene.layout.VBox pluginToolBar = new javafx.scene.layout.VBox(6);
    /** Plugin transforms for the right-click menu, given the viewport's current selection; set by the main window. */
    private java.util.function.Supplier<List<javafx.scene.control.MenuItem>> transformItems = List::of;
    private PixelBuffer<IntBuffer> pixels;
    private int pbW, pbH;
    private final AtomicReference<ViewportRenderer.Frame> latest = new AtomicReference<>();
    private final AtomicBoolean redraw = new AtomicBoolean(true);
    private long sequence;
    private boolean framedOnce;

    // input state
    private double lastX, lastY, pressX, pressY;

    // ---- build shapes (Effortless Building style) ----
    /** Shape wheel, opened by holding Alt in Build mode. */
    private final ShapeRadial shapeRadial = new ShapeRadial(this::pickShape);
    /** Waits a moment after Alt goes down so Alt+key shortcuts never flash the wheel. */
    private final PauseTransition radialDelay = new PauseTransition(Duration.millis(140));
    private boolean altAlone;
    private final Label shapeInfo = new Label();
    private ShapeDrag shapeDrag;
    private final SymmetryPopup symmetryPopup;
    private final javafx.scene.control.Button symmetryButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.COLUMNS));

    /** A right-drag in progress: where it started, where it ends now, the wheel-set height and the resulting cells. */
    private static final class ShapeDrag {
        final io.blockdesigner.core.place.ShapeTool.Shape shape;
        final BlockPos anchor;
        /** Normal of the face the drag started on: shapes that follow the face grow along it. */
        final BlockPos up;
        final boolean replace;
        BlockPos end;
        int height = 1;
        List<BlockPos> cells = List.of();
        boolean tooBig;

        ShapeDrag(io.blockdesigner.core.place.ShapeTool.Shape shape, BlockPos anchor, BlockPos up, boolean replace) {
            this.shape = shape;
            this.anchor = anchor;
            this.up = up;
            this.replace = replace;
            this.end = anchor;
        }
    }
    private MouseButton dragButton;
    private boolean fastNudge;
    private final int[] burstDelta = new int[3];
    private Picker.Hit hover;
    private BlockPos hoverGround;
    /** The entity under the cursor when it is nearer than any block (a mob, painting, armour stand…). */
    private EntityHit hoverEntity;
    /** Selected entities per layer id (layer-local entities, compared by value). */
    private final java.util.Map<String, java.util.Set<io.blockdesigner.core.model.StructureEntity>> entitySel = new java.util.LinkedHashMap<>();
    /** True during the first action of a held place / break, so a held right-click places one mob, not a crowd. */
    private boolean holdFirst;
    /** Chests and shulker boxes opening or closing in View mode: key layerId|packed local position. */
    private final java.util.Map<String, LidAnim> lids = new java.util.LinkedHashMap<>();

    /** An entity hit by the aim ray: its layer, the entity (layer-local) and the distance along the ray. */
    private record EntityHit(Layer layer, io.blockdesigner.core.model.StructureEntity entity, float distance) {
    }

    /** A container lid moving towards open (1) or shut (0). */
    private static final class LidAnim {
        final Layer layer;
        final BlockPos local;
        float amount;
        boolean opening;

        LidAnim(Layer layer, BlockPos local) {
            this.layer = layer;
            this.local = local;
        }
    }
    private double dragDistance;
    private static final double CLICK_SLOP = 5;
    /** Creative-mode block reach while flying, as in Minecraft (block_interaction_range 5; survival is 4.5). */
    private static final float CREATIVE_REACH = 5;

    // Minecraft-style hold-to-repeat place/break
    private Action holdAction;
    private MouseButton holdButton;
    private long holdNext;
    private String holdMergeKey;
    private int holdCounter;

    // creative flight
    private boolean fly;
    private final java.util.Set<KeyCode> flyKeys = java.util.EnumSet.noneOf(KeyCode.class);
    private javafx.scene.robot.Robot robot;
    private double centerX, centerY;
    private boolean ignoreNextMove;
    private long lastPulse;
    private final List<Runnable> flyChanged = new ArrayList<>();
    private final BlockInfoHud hud = new BlockInfoHud();
    /** Control prompts in the bottom-right corner for what you are doing now (Shift+F1 toggles). */
    private final KeyHints keyHints = new KeyHints();
    private final javafx.animation.Timeline keyHintsTick = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.millis(200), e -> updateKeyHints()));
    private final StackPane crosshair = new StackPane();

    // Select mode: selected blocks per layer id (packed layer-local positions) and the marquee being dragged
    private final java.util.Map<String, java.util.Set<Long>> blockSel = new java.util.LinkedHashMap<>();
    private final javafx.scene.shape.Rectangle marquee = new javafx.scene.shape.Rectangle();
    private static final int SEL_OUTLINE_LIMIT = 4000;
    private final javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();

    // Move / Rotate tools: the gizmo overlay, the handle under the mouse and the drag in progress
    private final Gizmo gizmo = new Gizmo();
    // Paint brush / eraser: the options bar and the stroke being painted (one undo step)
    private BrushBar brushBar;
    private BrushPopup brushPopup;
    private Stroke stroke;
    private long lastBrushSound;
    // WorldEdit: pos1 / pos2 region, clipboard and the "/" command bar
    private final io.blockdesigner.core.worldedit.WorldEdit worldEdit = new io.blockdesigner.core.worldedit.WorldEdit();
    private final CommandBar commandBar = new CommandBar(this::runCommand, this::blockIds);
    private final javafx.scene.control.Button commandButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.TERMINAL));
    // Place / break feel: thuds, break chips, and flight velocity for momentum
    private final BlockSounds sounds = new BlockSounds();
    private final BreakParticles particles = new BreakParticles();
    private boolean particlesDrawn;
    private final Vector3f flyVel = new Vector3f();
    private Gizmo.Handle gizmoHot;
    private GizmoDrag gizmoDrag;

    /** World point the middle-drag orbits around (the block or ground under the cursor when it was pressed). */
    private Vector3f orbitPivot;

    // placement (ghost follows the cursor until clicked)
    private final List<Layer> placing = new ArrayList<>();
    private final List<BlockPos> placingRelative = new ArrayList<>();
    private BlockPos placementNudge = BlockPos.ORIGIN;
    private Runnable placementDone;

    // slice view: null shows everything; otherwise the current Y level (alone, or with everything below it)
    private Integer sliceY;
    private boolean sliceSingle;

    public ViewportPane(Workspace ws) {
        this.ws = ws;
        getStyleClass().add("viewport");
        setFocusTraversable(true);
        view.setPreserveRatio(false);
        view.setManaged(false);
        getChildren().addAll(view, particles, gizmo);

        toast.getStyleClass().add("viewport-toast");
        toast.setOpacity(0);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setAlignment(viewCube, Pos.TOP_RIGHT);
        StackPane.setMargin(viewCube, new javafx.geometry.Insets(6, 56, 0, 0));
        sliceBadge.getStyleClass().add("viewport-badge");
        sliceBadge.setMouseTransparent(true);
        sliceBadge.setVisible(false);
        StackPane.setAlignment(sliceBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(sliceBadge, new javafx.geometry.Insets(14, 150, 0, 0));
        settingsButton.getStyleClass().addAll("flat", "viewport-settings-button");
        settingsButton.setTooltip(Keybinds.tooltip("Viewport settings", "Field of view, clipping, fog, overlays, controls", Keybinds.Action.VIEWPORT_SETTINGS));
        settingsButton.setFocusTraversable(false);
        settingsButton.setOnAction(e -> toggleSettings());
        StackPane.setAlignment(settingsButton, Pos.TOP_RIGHT);
        StackPane.setMargin(settingsButton, new javafx.geometry.Insets(10, 12, 0, 0));
        keysButton.getStyleClass().addAll("flat", "viewport-settings-button");
        keysButton.setTooltip(Keybinds.tooltip("Keyboard shortcuts", "Every key and mouse control (change keys in Settings › Keybinds)", Keybinds.Action.SHORTCUTS));
        keysButton.setFocusTraversable(false);
        keysButton.setOnAction(e -> toggleShortcuts());
        StackPane.setAlignment(keysButton, Pos.TOP_RIGHT);
        StackPane.setMargin(keysButton, new javafx.geometry.Insets(52, 12, 0, 0));
        filterButton.getStyleClass().addAll("flat", "viewport-settings-button");
        filterButton.setTooltip(Keybinds.tooltip("Select or replace by type", "Pick block types to select, or to swap for another block", Keybinds.Action.SELECT_BY_TYPE));
        filterButton.setFocusTraversable(false);
        filterButton.setOnAction(e -> openSelectByType(null));
        StackPane.setAlignment(filterButton, Pos.TOP_RIGHT);
        StackPane.setMargin(filterButton, new javafx.geometry.Insets(94, 12, 0, 0));
        commandButton.getStyleClass().addAll("flat", "viewport-settings-button");
        commandButton.setTooltip(Keybinds.tooltip("WorldEdit commands", "/set, /replace, /walls, /copy, /paste, /stack, /sphere…", Keybinds.Action.COMMAND_BAR));
        commandButton.setFocusTraversable(false);
        commandButton.setOnAction(e -> openCommandBar());
        StackPane.setAlignment(commandButton, Pos.TOP_RIGHT);
        StackPane.setMargin(commandButton, new javafx.geometry.Insets(136, 12, 0, 0));
        // Bottom-left, like Minecraft's chat (clear of the hotbar in the middle).
        StackPane.setAlignment(commandBar, Pos.BOTTOM_LEFT);
        StackPane.setMargin(commandBar, new javafx.geometry.Insets(0, 0, 74, 12));
        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new javafx.geometry.Insets(12, 0, 0, 12));
        javafx.scene.shape.Rectangle ch = new javafx.scene.shape.Rectangle(18, 2), cv = new javafx.scene.shape.Rectangle(2, 18);
        ch.getStyleClass().add("crosshair-bar");
        cv.getStyleClass().add("crosshair-bar");
        crosshair.getChildren().addAll(ch, cv);
        crosshair.setMouseTransparent(true);
        crosshair.setMaxSize(18, 18);
        crosshair.setVisible(false);
        marquee.getStyleClass().add("marquee");
        marquee.setManaged(false);
        marquee.setMouseTransparent(true);
        marquee.setVisible(false);
        hotbar = new Hotbar(ws);
        brushPopup = new BrushPopup(ws.settings(), () -> {
            brushBar.sync();
            requestRedraw();
        }, m -> {
            // The eraser only erases: choosing any other mode in its popup takes up the brush in that mode.
            if (ws.toolProperty().get() == ToolKind.ERASER && m != io.blockdesigner.core.edit.Sculpt.Mode.ERASE) {
                ws.toolProperty().set(ToolKind.BRUSH);
                showToast("Brush: " + m.label + " · " + m.description);
            }
        });
        brushBar = new BrushBar(ws.settings(), this::requestRedraw, () -> {
            javafx.geometry.Point2D p = brushBar.localToScreen(0, -8);
            if (p != null) {
                brushPopup.sync();
                brushPopup.show(getScene().getWindow(), p.getX(), p.getY() - 330);
            }
        });
        StackPane.setAlignment(brushBar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(brushBar, new javafx.geometry.Insets(0, 0, 76, 0));
        pluginToolBar.getStyleClass().add("brush-bar");
        pluginToolBar.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        pluginToolBar.setVisible(false);
        StackPane.setAlignment(pluginToolBar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(pluginToolBar, new javafx.geometry.Insets(0, 0, 76, 0));
        StackPane.setAlignment(hotbar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hotbar, new javafx.geometry.Insets(0, 0, 14, 0));
        shapeInfo.getStyleClass().add("viewport-toast");
        shapeInfo.setMouseTransparent(true);
        shapeInfo.setVisible(false);
        StackPane.setAlignment(shapeInfo, Pos.BOTTOM_CENTER);
        StackPane.setMargin(shapeInfo, new javafx.geometry.Insets(0, 0, 82, 0));
        radialDelay.setOnFinished(e -> openShapeRadial());
        symmetryPopup = new SymmetryPopup(ws.settings(), () -> {
            updateSymmetryButton();
            requestRedraw();
        }, this::centreSymmetryHere);
        symmetryButton.getStyleClass().addAll("flat", "viewport-settings-button");
        symmetryButton.setTooltip(Keybinds.tooltip("Symmetry", "Mirror X / Y / Z and radial copies for Build mode", Keybinds.Action.SYMMETRY));
        symmetryButton.setFocusTraversable(false);
        symmetryButton.setOnAction(e -> {
            javafx.geometry.Point2D p = symmetryButton.localToScreen(0, 0);
            if (p != null) showSymmetry(p.getX() - 330, p.getY());
        });
        StackPane.setAlignment(symmetryButton, Pos.TOP_RIGHT);
        StackPane.setMargin(symmetryButton, new javafx.geometry.Insets(178, 12, 0, 0));
        updateSymmetryButton();
        StackPane.setAlignment(keyHints, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(keyHints, new javafx.geometry.Insets(0, 14, 14, 0));
        getChildren().addAll(marquee, hud, crosshair, sliceBadge, toast, hotbar, brushBar, pluginToolBar, viewCube, settingsButton, keysButton, filterButton, commandButton, commandBar,
                shapeInfo, symmetryButton, keyHints, shapeRadial);
        keyHintsTick.setCycleCount(javafx.animation.Animation.INDEFINITE);
        keyHintsTick.play();
        updateKeyHints();
        updateHotbarVisibility();
        applyViewSettings();
        toastFade.setFromValue(1);
        toastFade.setToValue(0);
        toastHold.setOnFinished(e -> toastFade.playFromStart());
        nudgeSeal.setOnFinished(e -> {
            ws.editor().undoStack().sealTop();
            burstDelta[0] = burstDelta[1] = burstDelta[2] = 0;
        });

        widthProperty().addListener((o, a, b) -> requestRedraw());
        heightProperty().addListener((o, a, b) -> requestRedraw());
        ws.darkProperty().addListener((o, a, b) -> requestRedraw());
        ws.themeProperty().addListener((o, a, b) -> requestRedraw());
        ws.activeLayerProperty().addListener((o, a, b) -> requestRedraw());
        ws.selectedLayers().addListener((javafx.collections.ListChangeListener<Layer>) c -> requestRedraw());
        ws.heldEntityProperty().addListener((o, a, b) -> {
            if (b != null && a == null) {
                showToast("Holding " + io.blockdesigner.core.model.EntityTypes.kind(b.getString("id")).name()
                        + " · right-click to place · Alt+scroll over it turns it · left-click removes");
            }
        });
        ws.toolProperty().addListener((o, a, b) -> {
            updateHotbarVisibility();
            requestRedraw();
            showToast(switch (b) {
                case BUILD -> ws.replaceProperty().get() ? "Build mode · Replace · left break · right replace · middle pick" + keyNote(Keybinds.Action.REPLACE_MODE, "places again")
                        : shape() == io.blockdesigner.core.place.ShapeTool.Shape.SINGLE
                        ? "Build mode · left break · right place · middle pick" + keyNote(Keybinds.Action.SHAPE_WHEEL, "(hold) for shapes") + keyNote(Keybinds.Action.TOOL_BUILD, "to leave")
                        : "Build mode · " + shape().label + " · right-drag to place" + keyNote(Keybinds.Action.SHAPE_WHEEL, "(hold) to change shape") + keyNote(Keybinds.Action.TOOL_BUILD, "to leave");
                case SELECT -> "Select mode";
                case VIEW -> "View mode";
                case MOVE -> "Move · drag an arrow, square or the centre" + (keyText(Keybinds.Action.TOOL_MOVE).isEmpty() ? "" : " · " + keyText(Keybinds.Action.TOOL_MOVE));
                case ROTATE -> "Rotate · drag a ring to turn 90° · E";
                case BRUSH -> "Brush · drag to paint · right-drag smooths · Shift+right-click settings"
                        + keyNote(Keybinds.Action.BRUSH_SMALLER, "/ " + keyText(Keybinds.Action.BRUSH_BIGGER) + " size")
                        + keyNote(Keybinds.Action.BRUSH_WEAKER, "/ " + keyText(Keybinds.Action.BRUSH_STRONGER) + " strength");
                case ERASER -> "Eraser · drag to remove blocks · - / = size" + (keyText(Keybinds.Action.TOOL_ERASER).isEmpty() ? "" : " · " + keyText(Keybinds.Action.TOOL_ERASER));
                case PLUGIN -> pluginToolToast();
            });
            if (b != ToolKind.PLUGIN && pluginTool != null) setPluginTool(null, null);
        });
        ws.editor().undoStack().addListener(this::requestRedraw);
        ws.scene().addListener(new Scene.Listener() {
            @Override
            public void layerAdded(Layer layer, int index) {
                requestRedraw();
                if (!framedOnce && !placing.contains(layer)) frameAll();
            }

            @Override
            public void layerRemoved(Layer layer) {
                blockSel.remove(layer.id());
                entitySel.remove(layer.id());
                lids.values().removeIf(a -> a.layer == layer);
                requestRedraw();
            }

            @Override
            public void entitiesChanged(Layer layer) {
                // Selected entities that are gone (removed, or turned into a new value) drop out of the selection.
                var sel = entitySel.get(layer.id());
                if (sel != null) {
                    sel.retainAll(layer.structure().entities());
                    if (sel.isEmpty()) entitySel.remove(layer.id());
                }
                if (hoverEntity != null && hoverEntity.layer() == layer) updateHover(aimX(), aimY());
                requestRedraw();
            }

            @Override
            public void layerPropertiesChanged(Layer layer) {
                requestRedraw();
            }

            @Override
            public void blocksChanged(Layer layer, Box localBox) {
                requestRedraw();
            }
        });

        installInput();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                pulse();
            }
        }.start();
    }

    /** Attaches the renderer once assets are loaded; replaces any previous one. */
    public CompletableFuture<Void> attach(BlockAssets assets) {
        detach();
        gpu = new ViewportRenderer(assets.atlas(), f -> {
            ViewportRenderer.Frame old = latest.getAndSet(f);
            if (old != null) gpu.recycle(old);
        });
        sceneRenderer = new SceneRenderer(ws.scene(), assets, gpu, this::requestRedraw);
        sceneRenderer.setSlice(sliceMin(), sliceMax());
        previewRenderer = new SceneRenderer(previewScene, assets, gpu, this::requestRedraw, "preview-", 1);
        requestRedraw();
        return gpu.ready();
    }

    public void detach() {
        if (sceneRenderer != null) sceneRenderer.close();
        if (previewRenderer != null) previewRenderer.close();
        if (gpu != null) gpu.close();
        sceneRenderer = null;
        previewRenderer = null;
        gpu = null;
    }

    public Camera camera() {
        return camera;
    }

    public String glInfo() {
        return gpu == null ? "" : gpu.glInfo();
    }

    public void requestRedraw() {
        redraw.set(true);
    }

    // ---- frame loop ---------------------------------------------------------------------------------------------

    private void pulse() {
        long nowNs = System.nanoTime();
        double dt = lastPulse == 0 ? 0 : Math.min(0.1, (nowNs - lastPulse) / 1e9);
        lastPulse = nowNs;
        flyStep(dt);
        viewAnimStep();
        if (particles.active() || particlesDrawn) {
            particles.step(dt, camera, getWidth(), getHeight());
            particlesDrawn = particles.active();
        }
        holdStep();
        strokeStep();
        lidStep(dt);
        if (sceneRenderer != null) sceneRenderer.sync();
        if (previewRenderer != null) previewRenderer.sync();
        checkSelection();
        double scale = getScene() != null && getScene().getWindow() != null ? getScene().getWindow().getOutputScaleX() : 1;
        int w = (int) Math.max(1, Math.round(getWidth() * scale)), h = (int) Math.max(1, Math.round(getHeight() * scale));
        if (w != pbW || h != pbH) {
            IntBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
            pixels = new PixelBuffer<>(w, h, buf, PixelFormat.getIntArgbPreInstance());
            view.setImage(new WritableImage(pixels));
            pbW = w;
            pbH = h;
            redraw.set(true);
        }
        view.setFitWidth(getWidth());
        view.setFitHeight(getHeight());
        view.resizeRelocate(0, 0, getWidth(), getHeight());

        if (gpu != null && redraw.getAndSet(false)) gpu.requestFrame(buildFrame(w, h));

        ViewportRenderer.Frame f = latest.getAndSet(null);
        if (f != null) {
            if (f.width() == pbW && f.height() == pbH) {
                IntBuffer dst = pixels.getBuffer();
                dst.clear();
                f.pixels().clear();
                dst.put(f.pixels());
                dst.clear();
                pixels.updateBuffer(pb -> null);
            } else {
                redraw.set(true);
            }
            if (gpu != null) gpu.recycle(f);
        }
    }

    /** The theme's accent as ARGB, for the active layer's outline. */
    private int accent() {
        return 0xFF000000 | AppTheme.byId(ws.themeProperty().get()).accentFor(true);
    }

    private FrameRequest buildFrame(int w, int h) {
        camera.setFov((float) ws.settings().fovDeg);
        camera.setClipEnd((float) ws.settings().clipEnd);
        float aspect = w / (float) h;
        float[] vp = new float[16];
        camera.viewProjection(aspect).get(vp);
        Vector3f eye = camera.viewEye();
        Layer active = ws.activeLayerProperty().get();
        viewCube.update(camera);
        List<FrameRequest.LayerDraw> draws = sceneRenderer.layerDraws(active == null ? null : active.id());
        if (previewRenderer != null) draws.addAll(previewRenderer.layerDraws(null));
        List<FrameRequest.Line> lines = new ArrayList<>();

        for (Layer l : ws.scene().layers()) {
            if (!l.visible() || (!ws.settings().showOutlines && !placing.contains(l))) continue;
            Optional<Box> b = l.worldBounds();
            if (b.isEmpty()) continue;
            Box wb = b.get();
            if (placing.contains(l)) {
                Overlays.box(lines, wb.minX(), wb.minY(), wb.minZ(), wb.maxX() + 1, wb.maxY() + 1, wb.maxZ() + 1, 0xFF46C46E);
            } else if (l == active) {
                Overlays.box(lines, wb.minX(), wb.minY(), wb.minZ(), wb.maxX() + 1, wb.maxY() + 1, wb.maxZ() + 1, accent());
                Overlays.axes(lines, wb.minX(), wb.minY(), wb.minZ(), 2.5f);
            } else if (ws.selectedLayers().contains(l)) {
                Overlays.box(lines, wb.minX(), wb.minY(), wb.minZ(), wb.maxX() + 1, wb.maxY() + 1, wb.maxZ() + 1, (accent() & 0xFFFFFF) | 0xAA000000);
            }
        }
        drawBlockSelection(lines);
        drawEntitySelection(lines);
        drawRegion(lines);
        if (placing.isEmpty() && ws.toolProperty().get() != ToolKind.VIEW) {
            boolean build = ws.toolProperty().get() == ToolKind.BUILD;
            if (hoverEntity != null) {
                entityOutline(lines, hoverEntity.layer(), hoverEntity.entity(), build ? 0xFFFFC85A : 0xE6FFFFFF);
            } else if (hover != null) {
                BlockPos p = hover.world();
                drawShapeOutline(lines, hover, build && ws.replaceProperty().get() ? 0xFFFFC85A : 0xE6FFFFFF);
            } else if (hoverGround != null && build) {
                Overlays.block(lines, hoverGround.x(), hoverGround.y(), hoverGround.z(), 0xFFFFC85A);
            }
        }

        drawBrushPreview(lines);
        drawShapePreview(lines);
        drawPluginPreview(lines);
        drawSymmetry(lines);
        updateGizmo();

        Optional<Box> sb = ws.scene().worldBounds();
        float gridY = sliceY != null ? sliceY : sb.map(b -> (float) b.minY()).orElse(0f);
        float[] gc = sb.map(b -> new float[]{(b.minX() + b.maxX()) / 2f, (b.minZ() + b.maxZ()) / 2f}).orElse(new float[]{0, 0});
        return new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, draws, lines,
                AppTheme.byId(ws.themeProperty().get()).viewport(ws.darkProperty().get()),
                ws.settings().showGrid, gridY, gc, ++sequence,
                ws.settings().fog && !camera.orthoActive() ? (float) ws.settings().fogDistance : Float.POSITIVE_INFINITY);
    }

    /** Renders the current scene from the given camera, without overlays (screenshots). */
    public CompletableFuture<ViewportRenderer.Frame> capture(Camera cam, int w, int h) {
        if (gpu == null) return CompletableFuture.failedFuture(new IllegalStateException("Renderer not ready"));
        sceneRenderer.sync();
        Camera saved = camera.copy();
        copyCamera(cam, camera);
        FrameRequest req = buildFrame(w, h);
        copyCamera(saved, camera);
        // Drop overlays for clean captures.
        FrameRequest clean = new FrameRequest(req.width(), req.height(), req.viewProj(), req.eye(), req.layers(), List.of(), req.theme(),
                req.showGrid(), req.gridY(), req.gridCenter(), req.sequence(), req.fogDistance());
        return gpu.capture(clean);
    }

    private static void copyCamera(Camera from, Camera to) {
        to.set(from);
    }

    /**
     * Captures once all queued meshes are uploaded (edits made just before are visible). Call on the FX thread;
     * the future completes on the render thread.
     */
    public CompletableFuture<ViewportRenderer.Frame> captureWhenReady(Camera cam, int w, int h) {
        if (sceneRenderer == null) return CompletableFuture.failedFuture(new IllegalStateException("Renderer not ready"));
        sceneRenderer.sync();
        SceneRenderer sr = sceneRenderer;
        return CompletableFuture.runAsync(() -> {
            long deadline = System.currentTimeMillis() + 8000;
            while (sr.inFlight() > 0 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }).thenCompose(v -> {
            CompletableFuture<ViewportRenderer.Frame> f = new CompletableFuture<>();
            javafx.application.Platform.runLater(() -> capture(cam, w, h).whenComplete((fr, err) -> {
                if (err != null) f.completeExceptionally(err);
                else f.complete(fr);
            }));
            return f;
        });
    }

    /** A camera framing the whole scene from a named viewpoint (iso_se, iso_sw, iso_ne, iso_nw, top, north, south, east, west). */
    public Camera presetCamera(String view) {
        Camera c = new Camera();
        c.setFov((float) ws.settings().fovDeg);
        float yaw, pitch = 30;
        switch (view == null ? "iso_se" : view) {
            case "iso_sw" -> yaw = 45;
            case "iso_ne" -> yaw = -135;
            case "iso_nw" -> yaw = 135;
            case "top" -> {
                yaw = 0;
                pitch = 89;
            }
            case "north" -> {
                yaw = 180;
                pitch = 12;
            }
            case "south" -> {
                yaw = 0;
                pitch = 12;
            }
            case "east" -> {
                yaw = -90;
                pitch = 12;
            }
            case "west" -> {
                yaw = 90;
                pitch = 12;
            }
            default -> yaw = -45;
        }
        c.setAngles((float) Math.toRadians(yaw), (float) Math.toRadians(pitch));
        ws.scene().worldBounds().ifPresentOrElse(
                b -> c.frame(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1),
                () -> c.frame(-8, 0, -8, 8, 8, 8));
        return c;
    }

    public boolean meshesPending() {
        return sceneRenderer != null && sceneRenderer.pending() > 0;
    }

    public void frameAll() {
        ws.scene().worldBounds().ifPresent(b -> {
            camera.frame(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
            framedOnce = true;
            requestRedraw();
        });
    }

    /** Frames the selected blocks (world bounds across every layer they are in). */
    private void frameBlockSelection() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (var en : blockSel.entrySet()) {
            Layer l = ws.scene().find(en.getKey()).orElse(null);
            if (l == null) continue;
            for (long packed : en.getValue()) {
                BlockPos p = l.toWorld(BlockPos.unpack(packed));
                x0 = Math.min(x0, p.x());
                y0 = Math.min(y0, p.y());
                z0 = Math.min(z0, p.z());
                x1 = Math.max(x1, p.x());
                y1 = Math.max(y1, p.y());
                z1 = Math.max(z1, p.z());
            }
        }
        if (x0 > x1) return;
        camera.frame(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
        requestRedraw();
        int n = selectedBlockCount();
        showToast(String.format("Framed %,d selected block%s", n, n == 1 ? "" : "s"));
    }

    public void frameLayer(Layer l) {
        l.worldBounds().ifPresent(b -> {
            camera.frame(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
            requestRedraw();
        });
    }

    // ---- input --------------------------------------------------------------------------------------------------

    /** Repeatable block actions (hold the button to repeat after a delay, like Minecraft). */
    private enum Action { PLACE, BREAK }

    private void installInput() {
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPress);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDrag);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::onRelease);
        addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (fly) {
                flyLook(e);
                return;
            }
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            if (shapeRadial.isOpen()) {
                shapeRadial.pointAt(e.getX(), e.getY());
                return;
            }
            updateHover(e.getX(), e.getY());
            if (pluginToolActive()) pluginTool.hover(toolEvent(e.getX(), e.getY(), MouseButton.NONE, e.isShiftDown(), e.isShortcutDown(), e.isAltDown()));
            Gizmo.Handle h = gizmo.hit(e.getX(), e.getY());
            if (!java.util.Objects.equals(h, gizmoHot)) {
                gizmoHot = h;
                setCursor(h != null ? Cursor.HAND : Cursor.DEFAULT);
                requestRedraw();
            }
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (fly) return;
            hover = null;
            hoverGround = null;
            updateHud();
            requestRedraw();
        });
        addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            flyKeys.remove(e.getCode());
            if (keys != null && keys.holds(Keybinds.Action.FAST_NUDGE, e.getCode())) {
                fastNudge = false;
                e.consume();
            }
            if (fly && isFlyKey(e.getCode())) e.consume();
        });
        sceneProperty().addListener((o, a, sc) -> {
            if (sc == null) return;
            // The wheel key (Alt) held alone opens the shape wheel in Build mode; any other key while it is down
            // cancels it, so Alt+key shortcuts keep working. Scene-level so it sees keys the main window handles first.
            sc.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (isWheelKey(ev.getCode())) {
                    boolean typing = sc.getFocusOwner() instanceof javafx.scene.control.TextInputControl;
                    if (!altAlone && !ev.isShortcutDown() && !typing && ws.toolProperty().get() == ToolKind.BUILD && placing.isEmpty()
                            && !commandBar.isVisible()) {
                        altAlone = true;
                        radialDelay.playFromStart();
                    }
                } else if (altAlone || shapeRadial.isOpen()) {
                    cancelShapeRadial();
                }
            });
            sc.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
                if (!isWheelKey(ev.getCode())) return;
                radialDelay.stop();
                altAlone = false;
                if (shapeRadial.isOpen()) {
                    var pick = shapeRadial.highlighted();
                    shapeRadial.close();
                    if (pick != null) pickShape(pick);
                    ev.consume();
                }
            });
            sc.windowProperty().addListener((o2, w0, w) -> {
                if (w != null) w.focusedProperty().addListener((o3, was, is) -> {
                    if (!is) {
                        setFly(false);
                        stopHold();
                    }
                });
            });
        });
    }

    private Vector3f[] ray(double x, double y) {
        return camera.ray((float) (x / getWidth()), (float) (y / getHeight()), (float) (getWidth() / getHeight()));
    }

    private Optional<Picker.Hit> pick(double x, double y) {
        Vector3f[] r = ray(x, y);
        // During a draw or erase stroke the brush aims at the surface as it was when the stroke began: it looks
        // through its own new blocks and treats the cells it erased as still there, so it glides along the surface.
        java.util.function.Function<BlockPos, Boolean> override = null;
        if (stroke != null && stroke.shapeOnly() && !stroke.touched.isEmpty()) {
            Boolean forced = stroke.erase ? Boolean.TRUE : Boolean.FALSE;
            java.util.Set<BlockPos> touched = stroke.touched;
            override = p -> touched.contains(p) ? forced : null;
        }
        return Picker.pick(ws.scene(), r[0], r[1], fly ? CREATIVE_REACH : 10000, l -> !l.locked() && !placing.contains(l),
                sliceMin(), sliceMax(), override, this::hitboxes);
    }

    /** A block's hitbox boxes from its model, or null for a full cube (or without assets). */
    private List<float[]> hitboxes(BlockState st) {
        BlockAssets a = ws.assets();
        if (a == null) return null;
        var m = a.model(st);
        return m.fullHitbox() ? null : m.boxes();
    }

    /** The hover outline, around the block's actual shape (a slab's half, a torch, a fence's post and arms). */
    private void drawShapeOutline(List<FrameRequest.Line> lines, Picker.Hit h, int color) {
        BlockState st = h.layer().structure().get(h.local());
        List<float[]> boxes = hitboxes(st);
        BlockPos p = h.world();
        if (boxes == null) {
            Overlays.block(lines, p.x(), p.y(), p.z(), color);
            return;
        }
        // Boxes are in the layer's own space; turning them with the layer's transform keeps them axis-aligned.
        org.joml.Matrix4f m = new org.joml.Matrix4f().set(SceneRenderer.modelMatrix(h.layer().offset(), h.layer().transform()));
        BlockPos lp = h.local();
        float e = 0.004f;
        for (float[] b : boxes) {
            Vector3f a = m.transformPosition(new Vector3f(lp.x() + b[0], lp.y() + b[1], lp.z() + b[2]));
            Vector3f c = m.transformPosition(new Vector3f(lp.x() + b[3], lp.y() + b[4], lp.z() + b[5]));
            Overlays.box(lines, Math.min(a.x, c.x) - e, Math.min(a.y, c.y) - e, Math.min(a.z, c.z) - e,
                    Math.max(a.x, c.x) + e, Math.max(a.y, c.y) + e, Math.max(a.z, c.z) + e, color);
        }
    }

    /** Alt+1…Alt+0: brush modes in order (Draw, Erase, Smooth, Erode, Fill, Pinch, Raise, Lower, Flatten, Slope). */
    public void setBrushMode(int index) {
        var modes = io.blockdesigner.core.edit.Sculpt.Mode.values();
        if (index < 0 || index >= modes.length) return;
        ws.settings().brushMode = modes[index].name();
        if (ws.toolProperty().get() != ToolKind.BRUSH) ws.toolProperty().set(ToolKind.BRUSH);
        brushBar.sync();
        brushPopup.sync();
        showToast("Brush: " + modes[index].label + " · " + modes[index].description);
        requestRedraw();
    }

    private float groundY() {
        if (sliceY != null) return sliceY;
        return ws.scene().worldBounds().map(b -> (float) b.minY()).orElse(0f);
    }

    private Optional<BlockPos> pickGround(double x, double y, float planeY) {
        Vector3f[] r = ray(x, y);
        Optional<BlockPos> g = Picker.pickGround(r[0], r[1], planeY);
        if (fly && g.isPresent()) {
            // Reach is measured along the look ray to where it meets the ground, like Minecraft's ray cast.
            float t = Math.abs(r[1].y) < 1e-6f ? Float.MAX_VALUE : (planeY - r[0].y) / r[1].y;
            if (t > CREATIVE_REACH) return Optional.empty();
        }
        return g;
    }

    /** Point the camera aims with: the crosshair while flying, otherwise the mouse. */
    private double aimX() {
        return fly ? getWidth() / 2 : lastX;
    }

    private double aimY() {
        return fly ? getHeight() / 2 : lastY;
    }

    private void updateHover(double x, double y) {
        if (!placing.isEmpty()) {
            movePlacement(x, y);
            return;
        }
        if (!fly) {
            lastX = x;
            lastY = y;
        }
        hover = pick(x, y).orElse(null);
        hoverEntity = stroke == null ? pickEntity(x, y, hover == null ? Float.MAX_VALUE : hover.distance()) : null;
        // An entity in front of the blocks is what you aim at.
        if (hoverEntity != null) hover = null;
        hoverGround = hover == null && hoverEntity == null ? pickGround(x, y, groundY()).orElse(null) : null;
        updateHud();
        requestRedraw();
    }

    /** The world-space copy of a layer's entity (position, yaw and facing turned with the layer). */
    private static io.blockdesigner.core.model.StructureEntity worldEntity(Layer l, io.blockdesigner.core.model.StructureEntity e) {
        return io.blockdesigner.core.model.EntityTypes.transform(e, l.transform(), l.offset().x(), l.offset().y(), l.offset().z());
    }

    /** The nearest entity the ray hits before {@code before}, in visible, unlocked layers (within the slice view). */
    private EntityHit pickEntity(double x, double y, float before) {
        Vector3f[] r = ray(x, y);
        Vector3f o = r[0], d = new Vector3f(r[1]).normalize();
        float max = Math.min(before, fly ? CREATIVE_REACH : 10000);
        int lo = sliceMin(), hi = sliceMax();
        EntityHit best = null;
        for (Layer l : ws.scene().layers()) {
            if (!l.visible() || l.locked() || placing.contains(l) || l.structure().entities().isEmpty()) continue;
            for (var e : l.structure().entities()) {
                var w = worldEntity(l, e);
                if (w.y() < lo || w.y() >= (long) hi + 1) continue;
                double[] b = io.blockdesigner.core.model.EntityTypes.box(w);
                float t = rayBox(o, d, b);
                if (t >= 0 && t < max && (best == null || t < best.distance())) best = new EntityHit(l, e, t);
            }
        }
        return best;
    }

    /** Distance along a normalised ray to a box {minX, minY, minZ, maxX, maxY, maxZ}, or -1 when it misses. */
    private static float rayBox(Vector3f o, Vector3f d, double[] b) {
        double t0 = 0, t1 = Double.MAX_VALUE;
        double[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-9) {
                if (oo[a] < b[a] || oo[a] > b[a + 3]) return -1;
                continue;
            }
            double ta = (b[a] - oo[a]) / dd[a], tb = (b[a + 3] - oo[a]) / dd[a];
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
            if (t0 > t1) return -1;
        }
        return (float) t0;
    }

    /** The outline of an entity's box. */
    private static void entityOutline(List<FrameRequest.Line> lines, Layer l, io.blockdesigner.core.model.StructureEntity e, int color) {
        double[] b = io.blockdesigner.core.model.EntityTypes.box(worldEntity(l, e));
        float g = 0.02f;
        Overlays.box(lines, (float) b[0] - g, (float) b[1] - g, (float) b[2] - g, (float) b[3] + g, (float) b[4] + g, (float) b[5] + g, color);
    }

    private void drawEntitySelection(List<FrameRequest.Line> lines) {
        for (var en : entitySel.entrySet()) {
            Layer l = ws.scene().find(en.getKey()).orElse(null);
            if (l == null || !l.visible()) continue;
            for (var e : en.getValue()) entityOutline(lines, l, e, 0xFFFF9F2E);
        }
    }

    private void updateHud() {
        if (hoverEntity != null && ws.settings().showHud) {
            hud.showEntity(ws.assets(), worldEntity(hoverEntity.layer(), hoverEntity.entity()), hoverEntity.layer());
            return;
        }
        if (hover == null || !ws.settings().showHud) {
            hud.show(null, null, null, null);
            return;
        }
        BlockState local = hover.layer().structure().get(hover.local());
        hud.show(ws.assets(), BlockTransformer.defaults().apply(local, hover.layer().transform()), hover.world(), hover.layer());
    }

    private void onPress(MouseEvent e) {
        requestFocus();
        if (altAlone || shapeRadial.isOpen()) {
            // Clicking a sector picks it (the sector handles that); any other press closes the wheel and carries on,
            // so Alt+middle-drag still swings the view.
            if (e.getTarget() instanceof javafx.scene.Node n && isInside(n, shapeRadial)) return;
            cancelShapeRadial();
        }
        if (shapeDrag != null) {
            // Any other button while dragging a shape cancels it.
            if (e.getButton() != MouseButton.SECONDARY) cancelShape();
            e.consume();
            return;
        }
        pressX = e.getX();
        pressY = e.getY();
        if (!fly) {
            lastX = e.getX();
            lastY = e.getY();
        }
        dragButton = e.getButton();
        dragDistance = 0;

        ToolKind mode = ws.toolProperty().get();
        if (fly) {
            // Minecraft controls in Build mode: left break, right place, middle pick. Select mode selects the aimed block.
            if (mode == ToolKind.SELECT && e.getButton() == MouseButton.PRIMARY) {
                selectClick(e.isShiftDown(), e.isShortcutDown());
            } else if (mode == ToolKind.SELECT && e.getButton() == MouseButton.SECONDARY) {
                setCorner(false);
            } else if ((mode == ToolKind.BRUSH || mode == ToolKind.ERASER)
                    && (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY)) {
                startStroke(e.getButton() == MouseButton.SECONDARY ? io.blockdesigner.core.edit.Sculpt.Mode.SMOOTH : null, false, false);
            } else if (mode == ToolKind.BUILD) {
                switch (e.getButton()) {
                    case PRIMARY -> startHold(Action.BREAK);
                    case SECONDARY -> {
                        if (!beginShape()) startHold(Action.PLACE);
                    }
                    case MIDDLE -> pickBlock();
                    default -> {
                    }
                }
            }
            e.consume();
            return;
        }
        contextMenu.hide();
        if (gizmoDrag != null) {
            // Blender: right-click (or Esc) during a drag cancels it.
            if (e.getButton() == MouseButton.SECONDARY) cancelGizmoDrag();
            return;
        }
        if (e.getButton() == MouseButton.PRIMARY && isGizmoTool() && placing.isEmpty()) {
            Gizmo.Handle h = gizmo.hit(e.getX(), e.getY());
            if (h != null) {
                beginGizmoDrag(h, e.getX(), e.getY());
                return;
            }
        }
        if (e.getButton() == MouseButton.MIDDLE) {
            // Alt+middle-drag swings between axis views (see onDrag) instead of orbiting.
            viewSwing = e.isAltDown();
            swingX = swingY = 0;
            orbitPivot = viewSwing ? null : pivotUnder(e.getX(), e.getY());
            return;
        }
        if (pluginToolActive() && (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY)) {
            updateHover(e.getX(), e.getY());
            pluginTool.press(toolEvent(e.getX(), e.getY(), e.getButton(), e.isShiftDown(), e.isShortcutDown(), e.isAltDown()));
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY && (mode == ToolKind.BRUSH || mode == ToolKind.ERASER) && placing.isEmpty()) {
            // Right-drag smooths; Shift+right-click opens the brush settings instead (on release, see onRelease).
            if (!e.isShiftDown()) {
                updateHover(e.getX(), e.getY());
                startStroke(io.blockdesigner.core.edit.Sculpt.Mode.SMOOTH, false, false);
            }
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY && mode == ToolKind.BUILD && placing.isEmpty()) {
            updateHover(e.getX(), e.getY());
            if (!beginShape()) startHold(Action.PLACE);
            else updateShapeEnd(e.getX(), e.getY());
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) return;

        if (!placing.isEmpty()) {
            commitPlacement();
            return;
        }
        updateHover(e.getX(), e.getY());
        switch (mode) {
            case VIEW -> {
                // Looking only (the camera is on the middle button), but a chest or shulker box opens and shuts.
                toggleLid();
            }
            case SELECT -> {
                // Selection happens on release: a click selects one block, a drag draws a marquee.
            }
            case BUILD -> startHold(Action.BREAK);
            case MOVE, ROTATE -> {
                // A click on a layer selects it on release (see onRelease).
            }
            case BRUSH, ERASER -> startStroke(null, e.isShiftDown(), e.isShortcutDown());
            case PLUGIN -> {
                // Handled above, before the other buttons.
            }
        }
    }

    private void onDrag(MouseEvent e) {
        if (fly) {
            flyLook(e);
            return;
        }
        double dx = e.getX() - lastX, dy = e.getY() - lastY;
        dragDistance += Math.abs(dx) + Math.abs(dy);
        // Blender-style: middle-drag orbits, Shift+middle-drag pans. A middle click without dragging picks the block.
        if (dragButton == MouseButton.MIDDLE) {
            lastX = e.getX();
            lastY = e.getY();
            if (viewSwing) {
                swingView(dx, dy);
            } else if (dragDistance > CLICK_SLOP) {
                if (e.isShiftDown()) {
                    camera.pan((float) (dx / getHeight()), (float) (dy / getHeight()));
                } else {
                    leaveAutoOrtho();
                    float k = (float) (0.008 * ws.settings().orbitSensitivity);
                    if (orbitPivot != null) camera.orbitAround(orbitPivot, (float) dx * k, (float) dy * k);
                    else camera.orbit((float) dx * k, (float) dy * k);
                }
                requestRedraw();
            }
            return;
        }
        if (shapeDrag != null) {
            lastX = e.getX();
            lastY = e.getY();
            updateShapeEnd(e.getX(), e.getY());
            return;
        }
        if (stroke != null) {
            // Brush strokes follow the mouse with either button; the frame loop stamps.
            updateHover(e.getX(), e.getY());
            return;
        }
        if (pluginToolActive() && (dragButton == MouseButton.PRIMARY || dragButton == MouseButton.SECONDARY)) {
            updateHover(e.getX(), e.getY());
            pluginTool.drag(toolEvent(e.getX(), e.getY(), dragButton, e.isShiftDown(), e.isShortcutDown(), e.isAltDown()));
            return;
        }
        if (dragButton != MouseButton.PRIMARY) return;
        if (gizmoDrag != null) {
            dragGizmo(e.getX(), e.getY());
            return;
        }
        updateHover(e.getX(), e.getY());
        if (ws.toolProperty().get() == ToolKind.SELECT && placing.isEmpty() && dragDistance > CLICK_SLOP) {
            marquee.setX(Math.min(pressX, e.getX()));
            marquee.setY(Math.min(pressY, e.getY()));
            marquee.setWidth(Math.abs(e.getX() - pressX));
            marquee.setHeight(Math.abs(e.getY() - pressY));
            marquee.setVisible(true);
        }
        // Held place/break repeat from the frame loop, rate-limited by the configured delays.
    }

    private void onRelease(MouseEvent e) {
        if (shapeDrag != null && e.getButton() == MouseButton.SECONDARY) {
            commitShape();
            dragButton = null;
            if (fly) e.consume();
            return;
        }
        if (!fly && pluginToolActive() && (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY)) {
            updateHover(e.getX(), e.getY());
            pluginTool.release(toolEvent(e.getX(), e.getY(), e.getButton(), e.isShiftDown(), e.isShortcutDown(), e.isAltDown()));
            dragButton = null;
            requestRedraw();
            return;
        }
        boolean click = dragDistance <= CLICK_SLOP;
        if (stroke != null && e.getButton() != MouseButton.MIDDLE) endStroke();
        if (e.getButton() == MouseButton.SECONDARY && click && e.isShiftDown() && !fly && placing.isEmpty()
                && (ws.toolProperty().get() == ToolKind.BRUSH || ws.toolProperty().get() == ToolKind.ERASER)) {
            showBrushPopup(e.getScreenX(), e.getScreenY());
        }
        if (holdButton == e.getButton()) {
            stopHold();
        }
        if (fly) {
            e.consume();
            return;
        }
        if (gizmoDrag != null) {
            if (e.getButton() == MouseButton.PRIMARY) endGizmoDrag();
            dragButton = null;
            return;
        }
        if (e.getButton() == MouseButton.PRIMARY && click && isGizmoTool() && placing.isEmpty()) {
            // Move / Rotate tools: clicking a layer selects it (Shift adds or removes), so its gizmo appears.
            updateHover(e.getX(), e.getY());
            if (hover != null) {
                Layer l = hover.layer();
                if (e.isShiftDown()) {
                    if (ws.selectedLayers().contains(l) && ws.selectedLayers().size() > 1) ws.selectedLayers().remove(l);
                    else if (!ws.selectedLayers().contains(l)) ws.selectedLayers().add(l);
                    ws.scene().setActive(l);
                } else {
                    selectOnly(l);
                }
                requestRedraw();
            }
        }
        if (e.getButton() == MouseButton.MIDDLE && click) pickBlock();
        if (e.getButton() == MouseButton.SECONDARY && click && ws.toolProperty().get() == ToolKind.SELECT && placing.isEmpty()) {
            // WorldEdit: right-click sets pos2; Shift+right-click opens the menu.
            updateHover(e.getX(), e.getY());
            if (e.isShiftDown()) showContextMenu(e.getScreenX(), e.getScreenY());
            else setCorner(false);
        }
        if (e.getButton() == MouseButton.PRIMARY && ws.toolProperty().get() == ToolKind.SELECT && placing.isEmpty()) {
            if (marquee.isVisible()) {
                marqueeSelect(Math.min(pressX, e.getX()), Math.min(pressY, e.getY()), Math.max(pressX, e.getX()), Math.max(pressY, e.getY()),
                        e.isShiftDown(), e.isShortcutDown());
            } else if (click) {
                // WorldEdit: a plain click sets pos1; Shift / Ctrl still add or toggle single blocks.
                updateHover(e.getX(), e.getY());
                selectClick(e.isShiftDown(), e.isShortcutDown());
            }
        }
        marquee.setVisible(false);
        dragButton = null;
        requestRedraw();
    }

    private void onScroll(ScrollEvent e) {
        // While the shortcuts are open the wheel scrolls only them, wherever the mouse is.
        if (shortcutsShowing()) {
            shortcuts.scroll(e.getDeltaY());
            e.consume();
            return;
        }
        // Windows reports Shift+wheel as horizontal scrolling.
        double delta = e.getDeltaY() != 0 ? e.getDeltaY() : e.getDeltaX();
        // Alt+wheel moves layers, so scrolling closes the shape wheel rather than competing with that.
        if (altAlone || shapeRadial.isOpen()) cancelShapeRadial();
        if (shapeDrag != null && delta != 0) {
            // While dragging a shape the wheel sets its height (boxes, rooms, walls, cylinders).
            if (shapeDrag.shape.usesHeight()) {
                shapeDrag.height = Math.clamp(shapeDrag.height + (delta > 0 ? 1 : -1), 1, 384);
                refreshShape();
            }
            e.consume();
            return;
        }
        if (delta == 0) return;
        if (pluginToolActive() && pluginTool.scroll(toolEvent(e.getX(), e.getY(), MouseButton.NONE, e.isShiftDown(), e.isShortcutDown(), e.isAltDown()),
                delta > 0 ? 1 : -1)) {
            e.consume();
            return;
        }
        int sign = delta > 0 ? 1 : -1;
        if (e.isControlDown() && e.isShiftDown()) {
            nudge(0, sign, 0);
        } else if (e.isControlDown() && hover != null && placing.isEmpty()) {
            BlockPos n = hover.normal();
            nudge(n.x() * sign, n.y() * sign, n.z() * sign);
        } else if (e.isControlDown()) {
            int[] r = camera.screenRightAxis();
            nudge(r[0] * sign, 0, r[1] * sign);
        } else if (e.isAltDown()) {
            if (!placing.isEmpty()) rotatePlacement(sign);
            else if (hoverEntity != null) turnEntity(hoverEntity, sign);
            else if (hover != null) turnLayer(hover.layer(), sign, hover.normal());
            else showToast("Hover over a layer to turn it");
        } else if (e.isShiftDown()) {
            int[] f = camera.screenForwardAxis();
            nudge(f[0] * sign, 0, f[1] * sign);
        } else if (fly) {
            // Minecraft: wheel down moves to the next hotbar slot. Fly speed lives in the viewport settings.
            ws.scrollHotbar(-sign);
        } else {
            camera.zoom((float) Math.pow(1.0018, -delta * ws.settings().zoomSpeed));
            requestRedraw();
        }
        e.consume();
    }

    /** Whether {@code k} is one of the flying keys (forward, back, left, right, up, down, sprint). */
    public boolean isFlyKey(KeyCode k) {
        return keys != null && keys.isHeldKey(k, Keybinds.Action.FLY_FORWARD, Keybinds.Action.FLY_BACK, Keybinds.Action.FLY_LEFT,
                Keybinds.Action.FLY_RIGHT, Keybinds.Action.FLY_UP, Keybinds.Action.FLY_DOWN, Keybinds.Action.FLY_SPRINT);
    }

    /** Whether {@code k} is the key held for the shape wheel. */
    public boolean isWheelKey(KeyCode k) {
        return keys != null && keys.holds(Keybinds.Action.SHAPE_WHEEL, k);
    }

    /** Whether any key bound to a flying action is down. */
    private boolean flying(Keybinds.Action a) {
        for (KeyCode c : flyKeys) if (keys.holds(a, c)) return true;
        return false;
    }

    /** The keys the viewport itself handles, when it has focus (the rest are the main window's). */
    private static final Keybinds.Action[] VIEWPORT_KEYS = {Keybinds.Action.FLY, Keybinds.Action.NUDGE_LEFT, Keybinds.Action.NUDGE_RIGHT,
            Keybinds.Action.NUDGE_FORWARD, Keybinds.Action.NUDGE_BACK, Keybinds.Action.SLICE_UP, Keybinds.Action.SLICE_DOWN,
            Keybinds.Action.SLICE_SINGLE, Keybinds.Action.SYMMETRY, Keybinds.Action.SYMMETRY_CENTRE, Keybinds.Action.CANCEL,
            Keybinds.Action.DELETE, Keybinds.Action.ROTATE_PLACEMENT, Keybinds.Action.PLACE, Keybinds.Action.FRAME_ALL};

    private void onKey(KeyEvent e) {
        // Typing in the command bar is for the bar, not the viewport's keys.
        if (commandBar.isFocusWithin()) return;
        KeyCode k = e.getCode();
        if (fly && isFlyKey(k)) {
            flyKeys.add(k);
            e.consume();
            return;
        }
        if (k == KeyCode.CONTEXT_MENU) {
            // The keyboard's menu key opens the Select-mode menu, like a right-click.
            if (ws.toolProperty().get() != ToolKind.SELECT) return;
            javafx.geometry.Point2D c = localToScreen(lastX, lastY);
            if (c != null) showContextMenu(c.getX(), c.getY());
            e.consume();
            return;
        }
        if (keys == null) return;
        if (keys.holds(Keybinds.Action.FAST_NUDGE, k)) {
            fastNudge = true;
            e.consume();
            return;
        }
        for (Keybinds.Action a : VIEWPORT_KEYS) {
            if (keys.matches(a, e) && viewportKey(a)) {
                e.consume();
                return;
            }
        }
    }

    /** Runs one of the viewport's own key actions; false when it doesn't apply right now (the key is left alone). */
    private boolean viewportKey(Keybinds.Action a) {
        switch (a) {
            case FLY -> setFly(!fly);
            case NUDGE_LEFT, NUDGE_RIGHT -> {
                int[] r = camera.screenRightAxis();
                int s = a == Keybinds.Action.NUDGE_RIGHT ? 1 : -1;
                nudge(r[0] * s, 0, r[1] * s);
            }
            case NUDGE_FORWARD, NUDGE_BACK -> {
                int[] f = camera.screenForwardAxis();
                int s = a == Keybinds.Action.NUDGE_FORWARD ? 1 : -1;
                nudge(f[0] * s, 0, f[1] * s);
            }
            case SLICE_UP -> stepSlice(1);
            case SLICE_DOWN -> stepSlice(-1);
            case SLICE_SINGLE -> toggleSingleSlice();
            case SYMMETRY -> {
                if (fly) showSymmetry(-1, -1);
                else {
                    javafx.geometry.Point2D p = localToScreen(lastMouseX, lastMouseY);
                    if (p != null) showSymmetry(p.getX() + 12, p.getY() + 12);
                }
            }
            case SYMMETRY_CENTRE -> centreSymmetryHere();
            case CANCEL -> {
                if (shapeDrag != null) cancelShape();
                else if (shapeRadial.isOpen()) cancelShapeRadial();
                else if (gizmoDrag != null) cancelGizmoDrag();
                else if (shortcutsShowing()) showShortcuts(false);
                else if (fly) setFly(false);
                else if (!placing.isEmpty()) cancelPlacement();
                else if (!blockSel.isEmpty() || !entitySel.isEmpty() || worldEdit.region() != null) clearRegionAndSelection();
                else ws.toolProperty().set(ToolKind.SELECT);
            }
            case DELETE -> {
                if (ws.toolProperty().get() == ToolKind.BUILD) {
                    // Build mode: Delete empties the held hotbar slot.
                    BlockState removed = ws.clearHeldSlot();
                    showToast(removed == null ? "That hotbar slot is already empty"
                            : "Removed " + BlockInfoHud.name(ws.assets(), removed) + " from the hotbar");
                } else if (!blockSel.isEmpty() || !entitySel.isEmpty()) {
                    deleteSelectedBlocks();
                } else {
                    return false;
                }
            }
            case ROTATE_PLACEMENT -> {
                if (placing.isEmpty()) return false;
                rotatePlacement(1);
            }
            case PLACE -> {
                if (placing.isEmpty()) return false;
                commitPlacement();
            }
            case FRAME_ALL -> {
                setFly(false);
                frameAll();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---- creative flight ------------------------------------------------------------------------------------

    public boolean isFlying() {
        return fly;
    }

    /** Enters or leaves creative-style flight: mouse look, WASD, Space/Shift up/down, Ctrl to sprint. */
    public void setFly(boolean enable) {
        if (enable == fly) return;
        if (enable && !placing.isEmpty()) return;
        fly = enable;
        camera.setFly(enable);
        flyKeys.clear();
        stopHold();
        crosshair.setVisible(enable);
        setCursor(enable ? Cursor.NONE : Cursor.DEFAULT);
        if (enable) {
            requestFocus();
            recenterMouse();
            showToast("Flying · " + keyText(Keybinds.Action.FLY_FORWARD) + keyText(Keybinds.Action.FLY_LEFT) + keyText(Keybinds.Action.FLY_BACK)
                    + keyText(Keybinds.Action.FLY_RIGHT) + " move · " + keyText(Keybinds.Action.FLY_UP) + "/" + keyText(Keybinds.Action.FLY_DOWN)
                    + " up/down" + keyNote(Keybinds.Action.FLY_SPRINT, "sprint") + keyNote(Keybinds.Action.TOOL_BUILD, "toggles building")
                    + keyNote(Keybinds.Action.CANCEL, "exit"));
        } else {
            showToast("Orbit camera");
        }
        flyChanged.forEach(Runnable::run);
        updateHover(aimX(), aimY());
        requestRedraw();
    }

    /** Listeners told when flight turns on or off (the tool dock's toggle). */
    public void onFlyChanged(Runnable r) {
        flyChanged.add(r);
    }

    private void recenterMouse() {
        if (getScene() == null) return;
        javafx.geometry.Point2D c = localToScreen(getWidth() / 2, getHeight() / 2);
        if (c == null) return;
        if (robot == null) robot = new javafx.scene.robot.Robot();
        ignoreNextMove = true;
        robot.mouseMove(c.getX(), c.getY());
        centerX = c.getX();
        centerY = c.getY();
    }

    private void flyLook(MouseEvent e) {
        double dx = e.getScreenX() - centerX, dy = e.getScreenY() - centerY;
        if (ignoreNextMove && Math.abs(dx) < 1.5 && Math.abs(dy) < 1.5) {
            ignoreNextMove = false;
            return;
        }
        if (dx == 0 && dy == 0) return;
        if (shapeRadial.isOpen()) {
            // While the shape wheel is open the mouse points at shapes instead of turning the camera.
            shapeRadial.nudge(dx, dy);
            recenterMouse();
            return;
        }
        float k = (float) (0.0032 * ws.settings().lookSensitivity);
        camera.orbit((float) dx * k, (float) dy * k);
        recenterMouse();
        updateHover(aimX(), aimY());
        if (shapeDrag != null) updateShapeEnd(aimX(), aimY());
    }

    private void flyStep(double dt) {
        if (!fly) {
            flyVel.zero();
            return;
        }
        // Forward follows the look direction, pitch included (spectator / UE-style); strafing stays level.
        Vector3f flat = camera.flatForward(), f = camera.forward(), r = new Vector3f(-flat.z, 0, flat.x);
        Vector3f move = new Vector3f();
        if (flying(Keybinds.Action.FLY_FORWARD)) move.add(f);
        if (flying(Keybinds.Action.FLY_BACK)) move.sub(f);
        if (flying(Keybinds.Action.FLY_RIGHT)) move.add(r);
        if (flying(Keybinds.Action.FLY_LEFT)) move.sub(r);
        if (move.lengthSquared() > 0) move.normalize();
        if (flying(Keybinds.Action.FLY_UP)) move.y += 1;
        if (flying(Keybinds.Action.FLY_DOWN)) move.y -= 1;
        double speed = ws.settings().flySpeed * (flying(Keybinds.Action.FLY_SPRINT) ? 2 : 1);
        Vector3f want = move.mul((float) speed);
        if (ws.settings().flyMomentum) {
            // Eases up to speed quickly and glides to a stop in about half a second, a lighter version of Minecraft's.
            boolean pushing = want.lengthSquared() > 0;
            flyVel.lerp(want, (float) (1 - Math.exp(-(pushing ? 11 : 6) * dt)));
            if (!pushing && flyVel.lengthSquared() < 0.01f) flyVel.zero();
        } else {
            flyVel.set(want);
        }
        if (flyVel.lengthSquared() == 0) return;
        camera.translate(flyVel.x * (float) dt, flyVel.y * (float) dt, flyVel.z * (float) dt);
        updateHover(aimX(), aimY());
        requestRedraw();
    }

    // ---- place / break with repeat delays ---------------------------------------------------------------------

    /** Acts once now, then repeats from the frame loop while the button stays down. */
    private void startHold(Action a) {
        holdAction = a;
        holdButton = dragButton;
        holdMergeKey = "hold-" + (++holdCounter);
        holdFirst = true;
        try {
            doAction(a);
        } finally {
            holdFirst = false;
        }
        holdNext = System.nanoTime() + delayNanos(a);
    }

    private void stopHold() {
        holdAction = null;
        holdButton = null;
        if (holdMergeKey != null) ws.editor().undoStack().sealTop();
        holdMergeKey = null;
    }

    private long delayNanos(Action a) {
        int ms = a == Action.BREAK ? ws.settings().breakDelayMs : ws.settings().placeDelayMs;
        return Math.max(50, ms) * 1_000_000L;
    }

    /** Called every frame: repeats a held action once its delay has passed. */
    private void holdStep() {
        if (holdAction == null) return;
        long now = System.nanoTime();
        if (now < holdNext) return;
        updateHover(aimX(), aimY());
        doAction(holdAction);
        holdNext = now + delayNanos(holdAction);
    }

    /** One place / break / paint at the current aim, as its own edit (a held burst merges into one undo step). */
    private void doAction(Action a) {
        String key = holdMergeKey;
        switch (a) {
            case BREAK -> {
                if (hoverEntity != null) {
                    removeEntity(hoverEntity, key);
                    break;
                }
                if (hover == null) return;
                Layer l = hover.layer();
                BlockState broken = l.structure().get(hover.local());
                if (broken.isAir()) return;
                io.blockdesigner.core.place.Symmetry sym = symmetry();
                if (sym.active()) {
                    // Break the aimed block and its mirror images in whatever layers hold them, as one step.
                    var cells = sym.apply(hover.world(), null).keySet();
                    if (ws.settings().blockSounds) sounds.breakBlock(ws.settings().soundVolume);
                    editWorld("Break block", world -> {
                        for (BlockPos p : cells) if (!world.get(p).isAir()) world.set(p, BlockState.AIR);
                    });
                    break;
                }
                BlockPos bw = hover.world();
                if (ws.settings().breakParticles) {
                    BlockAssets assets = ws.assets();
                    BlockState shown = BlockTransformer.defaults().apply(broken, l.transform());
                    particles.burst(bw.x(), bw.y(), bw.z(), assets == null ? null : BlockIcons.icon(assets, shown));
                    particlesDrawn = true;
                }
                if (ws.settings().blockSounds) sounds.breakBlock(ws.settings().soundVolume);
                editLayers("Break block", key, l, world -> {
                    world.setIn(l, bw, BlockState.AIR);
                    // Neighbouring fences, walls, panes and stairs let go of the broken block, in any layer.
                    world.reconnect();
                });
            }
            case PLACE -> {
                if (ws.heldEntityProperty().get() != null) {
                    placeEntity(key);
                    break;
                }
                BlockState held = ws.blockToPlace();
                if (held == null) {
                    showToast("Empty hand · pick a block (middle-click), choose a hotbar slot or click one in the palette");
                    return;
                }
                if (ws.replaceProperty().get()) {
                    replaceAimed(key, held);
                    break;
                }
                BlockPos world = hover != null ? hover.adjacentWorld() : hoverGround;
                if (world == null) return;
                Layer l = ws.activeLayerProperty().get();
                if (l == null) {
                    l = new Layer("Layer " + (ws.scene().layers().size() + 1), new io.blockdesigner.core.model.Structure());
                    ws.editor().addLayer(l);
                }
                if (l.locked()) {
                    showToast("Active layer is locked");
                    return;
                }
                Layer target = l;
                editLayers("Place block", key, l, le -> {
                    // Placement sees every visible layer: a cell taken in any of them is taken, and fences, stairs and
                    // redstone join what's in the other layers.
                    java.util.Map<BlockPos, BlockState> edits = placementFor(le, world, held);
                    if (edits.isEmpty()) return;
                    io.blockdesigner.core.place.Symmetry sym = symmetry();
                    if (sym.active()) {
                        // Mirror images of what's placed; copies only fill empty cells.
                        java.util.Map<BlockPos, BlockState> all = new java.util.LinkedHashMap<>(edits);
                        for (var en : edits.entrySet()) {
                            for (var c : sym.apply(en.getKey(), en.getValue()).entrySet()) {
                                if (all.containsKey(c.getKey())) continue;
                                if (!le.get(c.getKey()).isAir()) continue;
                                if (fly && insideCamera(c.getKey())) continue;
                                all.put(c.getKey(), c.getValue());
                            }
                        }
                        edits = all;
                    }
                    if (fly && edits.keySet().stream().anyMatch(this::insideCamera)) return;
                    // Sound first: the edit's updates (meshes, panels) must not delay it.
                    if (ws.settings().blockSounds) sounds.place(ws.settings().soundVolume);
                    for (var en : edits.entrySet()) {
                        // New blocks go in the active layer; neighbours that change shape stay in their own.
                        if (le.get(en.getKey()).isAir()) le.setIn(target, en.getKey(), en.getValue());
                        else le.set(en.getKey(), en.getValue());
                    }
                    le.reconnect();
                });
            }
        }
        updateHover(aimX(), aimY());
    }

    /** Replace mode: swaps the aimed block (in its own layer) for the held one, keeping its facing and shape. */
    private void replaceAimed(String key, BlockState held) {
        if (hover == null) return;
        Layer l = hover.layer();
        if (l.locked()) {
            showToast("Layer is locked");
            return;
        }
        BlockAssets assets = ws.assets();
        BlockPlacement.Blocks blocks = assets == null ? BlockPlacement.Blocks.NONE
                : id -> assets.registry().get(id).map(i -> new BlockPlacement.Info(i.defaultState(), i.properties())).orElse(null);
        BlockPos at = hover.world();
        editLayers("Replace block", key, l, le -> {
            var edits = BlockPlacement.replace(held, at, le::get, blocks);
            if (edits.isEmpty()) return;
            if (ws.settings().blockSounds) sounds.place(ws.settings().soundVolume);
            edits.forEach(le::set);
            le.reconnect();
        });
    }

    /**
     * The world blocks to set for placing the held block at {@code world} in layer {@code l}, oriented the way
     * Minecraft would from the aimed face, the point on it and the look direction (see {@link BlockPlacement}).
     */
    private java.util.Map<BlockPos, BlockState> placementFor(LayeredEdit le, BlockPos world, BlockState held) {
        Vector3f[] r = ray(aimX(), aimY());
        Vector3f dir = new Vector3f(r[1]).normalize();
        BlockPlacement.Context ctx;
        if (hover != null) {
            Vector3f hit = new Vector3f(dir).mul(hover.distance()).add(r[0]);
            ctx = new BlockPlacement.Context(world, hover.world(), BlockPlacement.Dir.of(hover.normal()), hit.x, hit.y, hit.z, dir.x, dir.y, dir.z);
        } else {
            ctx = new BlockPlacement.Context(world, null, BlockPlacement.Dir.UP, world.x() + 0.5, world.y(), world.z() + 0.5, dir.x, dir.y, dir.z);
        }
        BlockAssets assets = ws.assets();
        BlockPlacement.Blocks blocks = assets == null ? BlockPlacement.Blocks.NONE
                : id -> assets.registry().get(id).map(i -> new BlockPlacement.Info(i.defaultState(), i.properties())).orElse(null);
        return BlockPlacement.place(held, ctx, le::get, blocks);
    }

    /** Visible layers (not the ones being placed), bottom first: what edits read, as the view shows them. */
    private List<Layer> worldLayers() {
        return ws.scene().layers().stream().filter(l -> l.visible() && !placing.contains(l)).toList();
    }

    private Layer newLayer() {
        Layer l = new Layer("Layer " + (ws.scene().layers().size() + 1), new io.blockdesigner.core.model.Structure());
        ws.editor().addLayer(l);
        return l;
    }

    /**
     * Runs a world-space edit across the layers (see {@link LayeredEdit}) as one undo step; edits sharing {@code mergeKey}
     * within the merge window join the same step (a held place or break).
     */
    private void editLayers(String label, String mergeKey, Layer target, java.util.function.Consumer<LayeredEdit> edit) {
        var undo = ws.editor().undoStack();
        undo.beginGroup(label, mergeKey);
        LayeredEdit le = new LayeredEdit(ws, worldLayers(), target, this::newLayer, label, null);
        try {
            edit.accept(le);
        } finally {
            le.close();
            undo.endGroup();
        }
        requestRedraw();
    }

    /**
     * Brushes never build into the camera: no blocks within two blocks of the eye, or level with or behind it, so a
     * stroke stops short of you instead of growing past the camera.
     */
    private boolean nearCamera(BlockPos p) {
        Vector3f eye = camera.eye(), f = camera.forward();
        float cx = p.x() + 0.5f - eye.x, cy = p.y() + 0.5f - eye.y, cz = p.z() + 0.5f - eye.z;
        if (cx * cx + cy * cy + cz * cz < 2.0f * 2.0f) return true;
        return cx * f.x + cy * f.y + cz * f.z < 1.0f;
    }

    private boolean insideCamera(BlockPos p) {
        Vector3f e = camera.eye();
        return (int) Math.floor(e.x) == p.x() && (int) Math.floor(e.y) == p.y() && (int) Math.floor(e.z) == p.z();
    }

    /** Minecraft's pick-block: select the highlighted block (with its properties) for placing. */
    private boolean pickBlock() {
        if (hoverEntity != null) {
            // Picking a mob holds one like it (its colour, profession, pose…), ready to place.
            var nbt = hoverEntity.entity().nbt().copy();
            ws.heldEntityProperty().set(nbt);
            if (ws.toolProperty().get() != ToolKind.BUILD) ws.toolProperty().set(ToolKind.BUILD);
            showToast("Picked " + io.blockdesigner.core.model.EntityTypes.displayName(hoverEntity.entity()) + " · right-click to place");
            return true;
        }
        if (hover == null) return false;
        BlockState s = BlockTransformer.defaults().apply(hover.layer().structure().get(hover.local()), hover.layer().transform());
        ws.recordInHotbar(s);
        showToast("Picked " + BlockInfoHud.name(ws.assets(), s));
        return true;
    }

    // ---- nudging --------------------------------------------------------------------------------------------

    private void nudge(int dx, int dy, int dz) {
        int step = fastNudge ? Math.max(1, ws.settings().fastNudgeStep) : 1;
        dx *= step;
        dy *= step;
        dz *= step;
        if (!placing.isEmpty()) {
            placementNudge = placementNudge.add(dx, dy, dz);
            for (Layer l : placing) {
                l.setOffset(l.offset().add(dx, dy, dz));
                ws.scene().firePropertiesChanged(l);
            }
            showToast("Δ " + fmt(placementNudge.x()) + ", " + fmt(placementNudge.y()) + ", " + fmt(placementNudge.z()));
            return;
        }
        if (!entitySel.isEmpty()) {
            moveSelectedEntities(dx, dy, dz);
            return;
        }
        List<Layer> targets = ws.nudgeTargets().stream().filter(l -> !l.locked()).toList();
        if (targets.isEmpty()) {
            showToast(ws.nudgeTargets().isEmpty() ? "Select a layer to move" : "Layer is locked");
            return;
        }
        ws.editor().nudge(targets, dx, dy, dz, "nudge");
        burstDelta[0] += dx;
        burstDelta[1] += dy;
        burstDelta[2] += dz;
        nudgeSeal.playFromStart();
        showToast("Δ " + fmt(burstDelta[0]) + ", " + fmt(burstDelta[1]) + ", " + fmt(burstDelta[2]));
    }

    /**
     * Alt+scroll over a layer, given the hovered face's world normal {@code face}: over the top or bottom, a spin
     * (clockwise from above when {@code sign} is 1); over a side, a flip that rolls that side up ({@code sign} 1) or
     * down. A quick burst undoes as one step.
     */
    private void turnLayer(Layer l, int sign, BlockPos face) {
        if (l.locked()) {
            showToast("Layer is locked");
            return;
        }
        Optional<Box> lb = l.structure().bounds();
        if (lb.isEmpty()) return;
        if (face.y() != 0) spinLayer(l, lb.get(), sign);
        else tipLayer(l, lb.get(), sign, face);
        nudgeSeal.playFromStart();
        updateHover(aimX(), aimY());
    }

    /** Horizontal quarter turn about the middle block, via the layer's transform (no blocks rewritten). */
    private void spinLayer(Layer l, Box b, int dir) {
        BlockPos pivotLocal = new BlockPos(Math.floorDiv(b.minX() + b.maxX(), 2), 0, Math.floorDiv(b.minZ() + b.maxZ(), 2));
        BlockPos pivotWorld = l.toWorld(pivotLocal);
        Transform t = l.transform().then(Transform.rotation(dir));
        BlockPos turned = t.apply(pivotLocal);
        BlockPos off = new BlockPos(pivotWorld.x() - turned.x(), l.offset().y(), pivotWorld.z() - turned.z());
        ws.editor().modifyLayer(l, "Rotate " + l.name(), "rotate-" + l.id(), x -> {
            x.setTransform(t);
            x.setOffset(off);
        });
        showToast("Rotated " + l.name() + " to " + t.rotation() * 90 + "°");
    }

    /**
     * Flips the layer a quarter turn about the horizontal edge of the side facing {@code face}, rewriting its blocks in
     * place. {@code sign} 1 turns that side to face up.
     */
    private void tipLayer(Layer l, Box b, int sign, BlockPos face) {
        // Edge axis = face × up, so a positive (right-hand) turn about it takes the face to +Y.
        BlockPos edge = new BlockPos(-face.z(), 0, face.x());
        // The axis in layer-local space; a mirrored layer turns the other way round it.
        BlockPos axis = l.transform().inverse().apply(edge);
        int handed = l.transform().mirror() == Transform.Mirror.NONE ? 1 : -1;
        Tilt tilt = new Tilt(axis, sign * handed);
        BlockPos pivot = new BlockPos(Math.floorDiv(b.minX() + b.maxX(), 2), Math.floorDiv(b.minY() + b.maxY(), 2),
                Math.floorDiv(b.minZ() + b.maxZ(), 2));
        java.util.function.Predicate<BlockState> valid = validStates();
        java.util.Map<BlockState, BlockState> turned = new java.util.HashMap<>();
        List<BlockPos> from = new ArrayList<>();
        List<BlockPos> to = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        l.structure().forEachBlock((x, y, z, st) -> {
            BlockPos p = new BlockPos(x, y, z);
            from.add(p);
            to.add(tilt.apply(p, pivot));
            states.add(turned.computeIfAbsent(st, k -> tilt.apply(k, valid)));
        });
        java.util.Map<BlockPos, io.blockdesigner.core.nbt.CompoundTag> entities = new java.util.HashMap<>();
        l.structure().blockEntities().forEach((p, nbt) -> entities.put(p, nbt.copy()));
        try (SceneEditor.BlockSession s = ws.editor().edit(l, "Tip " + l.name(), "tip-" + l.id())) {
            for (BlockPos p : from) s.set(p.x(), p.y(), p.z(), BlockState.AIR);
            for (int i = 0; i < from.size(); i++) {
                BlockPos p = to.get(i);
                s.set(p.x(), p.y(), p.z(), states.get(i), entities.get(from.get(i)));
            }
        }
        if (!l.structure().entities().isEmpty()) {
            // Entities keep their facing but move with the blocks round the pivot's centre.
            double px = pivot.x() + 0.5, py = pivot.y() + 0.5, pz = pivot.z() + 0.5;
            ws.editor().editEntities(l, "Tip " + l.name(), "tip-" + l.id(), list -> list.replaceAll(en -> {
                BlockPos ax = axis;
                int q = sign * handed;
                double vx = en.x() - px, vy = en.y() - py, vz = en.z() - pz;
                double dot = ax.x() * vx + ax.y() * vy + ax.z() * vz;
                double nx = dot * ax.x() + q * (ax.y() * vz - ax.z() * vy), ny = dot * ax.y() + q * (ax.z() * vx - ax.x() * vz),
                        nz = dot * ax.z() + q * (ax.x() * vy - ax.y() * vx);
                return en.at(nx + px, ny + py, nz + pz);
            }));
        }
        showToast("Flipped " + l.name() + (sign > 0 ? " up" : " down"));
    }

    /** Whether a state's property values all exist for its block (per the loaded assets; anything goes without them). */
    private java.util.function.Predicate<BlockState> validStates() {
        BlockAssets assets = ws.assets();
        if (assets == null) return s -> true;
        return s -> assets.registry().get(s.name()).map(info -> s.properties().entrySet().stream().allMatch(e -> {
            List<String> values = info.properties().get(e.getKey());
            return values == null || values.contains(e.getValue());
        })).orElse(true);
    }

    private static String fmt(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    // ---- slice view -----------------------------------------------------------------------------------------

    private int sliceMin() {
        return sliceY == null || !sliceSingle ? Integer.MIN_VALUE : sliceY;
    }

    private int sliceMax() {
        return sliceY == null ? Integer.MAX_VALUE : sliceY;
    }

    /** PgUp/PgDn: from the full view, up starts at the bottom level and down hides the top one. */
    private void stepSlice(int dir) {
        Optional<Box> sb = ws.scene().worldBounds();
        if (sb.isEmpty()) {
            showToast("Nothing to slice");
            return;
        }
        int min = sb.get().minY(), max = sb.get().maxY();
        int step = fastNudge ? Math.max(1, ws.settings().fastNudgeStep) : 1;
        if (sliceY == null) {
            sliceY = Math.clamp(dir > 0 ? min : sliceSingle ? max : max - 1, min, max);
        } else if (!sliceSingle && dir > 0 && sliceY + step > max) {
            sliceY = null;  // built all the way up: back to the full view
        } else {
            sliceY = Math.clamp((long) sliceY + (long) dir * step, min, max);
        }
        applySlice();
    }

    /** Insert: toggles between showing only the current level and building up everything below it. */
    private void toggleSingleSlice() {
        sliceSingle = !sliceSingle;
        if (sliceY == null) {
            if (hover != null) sliceY = hover.world().y();
            else sliceY = ws.scene().worldBounds().map(Box::minY).orElse(null);
        }
        applySlice();
    }

    private void applySlice() {
        if (sceneRenderer != null) sceneRenderer.setSlice(sliceMin(), sliceMax());
        String mode = sliceSingle ? "single layer" : "layers build up";
        if (sliceY == null) {
            sliceBadge.setVisible(false);
            showToast("All layers · " + mode);
        } else {
            sliceBadge.setText((sliceSingle ? "Y " : "Y ≤ ") + sliceY);
            sliceBadge.setVisible(true);
            showToast("Layer Y " + sliceY + " · " + mode);
        }
        updateHover(aimX(), aimY());
        requestRedraw();
    }

    // ---- orbit pivot ----------------------------------------------------------------------------------------

    /** The exact point on the block (or ground) under the cursor, or null over empty sky. */
    private Vector3f pivotUnder(double x, double y) {
        Vector3f[] r = ray(x, y);
        Optional<Picker.Hit> h = pick(x, y);
        if (h.isPresent()) return new Vector3f(r[1]).mul(h.get().distance()).add(r[0]);
        float gy = groundY();
        if (Math.abs(r[1].y) < 1e-4f) return null;
        float t = (gy - r[0].y) / r[1].y;
        return t > 0 && t < 5000 ? new Vector3f(r[1]).mul(t).add(r[0]) : null;
    }

    // ---- context menu (Select mode right-click) ---------------------------------------------------------

    private void showContextMenu(double screenX, double screenY) {
        // Right-clicking an unselected block selects it first, like most editors.
        if (hover != null) {
            java.util.Set<Long> set = blockSel.get(hover.layer().id());
            if (set == null || !set.contains(hover.local().pack())) clickSelect(false, false);
        }
        contextMenu.getItems().clear();
        int n = selectedBlockCount();
        BlockState held = ws.selectedBlockProperty().get();
        Layer layer = hover != null ? hover.layer() : ws.activeLayerProperty().get();

        if (worldEdit.region() != null) {
            var rb = worldEdit.region();
            contextMenu.getItems().addAll(
                    item(String.format("Fill region %d×%d×%d with held block", rb.sizeX(), rb.sizeY(), rb.sizeZ()), "/set hand", () -> runCommand("/set hand")),
                    item("Walls of region with held block", "/walls hand", () -> runCommand("/walls hand")),
                    item("Copy region", "/copy", () -> runCommand("/copy")),
                    item("Clear region", keyOrNull(Keybinds.Action.CANCEL), this::clearRegionAndSelection),
                    new javafx.scene.control.SeparatorMenuItem());
        }
        if (hoverEntity != null) {
            EntityHit eh = hoverEntity;
            String name = io.blockdesigner.core.model.EntityTypes.displayName(eh.entity());
            contextMenu.getItems().addAll(
                    item("Hold " + name + " to place more", "Middle-click", this::pickBlock),
                    item("Remove " + name, null, () -> removeEntity(eh, null)),
                    new javafx.scene.control.SeparatorMenuItem());
        }
        if (!entitySel.isEmpty()) {
            int ne = entitySel.values().stream().mapToInt(java.util.Set::size).sum();
            contextMenu.getItems().add(item(String.format("Remove %d selected entit%s", ne, ne == 1 ? "y" : "ies"), "Del", this::deleteSelectedBlocks));
        }
        if (hover != null) {
            contextMenu.getItems().add(item("Pick block to hotbar", "Middle-click", this::pickBlock));
            Layer hl = hover.layer();
            BlockState type = BlockTransformer.defaults().apply(hl.structure().get(hover.local()), hl.transform());
            String typeName = BlockInfoHud.name(ws.assets(), type);
            javafx.scene.control.Menu byType = new javafx.scene.control.Menu("Select or replace by type");
            byType.getItems().addAll(
                    item("All " + typeName + " in " + hl.name(), null, () -> quickSelectType(type, false, List.of(hl))),
                    item("All " + typeName + " in visible layers", null, () -> quickSelectType(type, false, typeLayers(SelectByTypePanel.Scope.VISIBLE))),
                    item("Same exact state in " + hl.name(), null, () -> quickSelectType(type, true, List.of(hl))),
                    new javafx.scene.control.SeparatorMenuItem());
            if (held != null && !held.name().equals(type.name())) {
                // Straight to replacing: every block of this type in the visible layers becomes the held block, facing kept.
                byType.getItems().add(item("Replace all " + typeName + " with " + BlockInfoHud.name(ws.assets(), held), null,
                        () -> applyReplaceByType(new SelectByTypePanel.Replace(new SelectByTypePanel.Query(SelectByTypePanel.Scope.VISIBLE,
                                false, java.util.Map.of(), sliceY != null), java.util.Set.of(type.name()), held, true))));
            }
            byType.getItems().addAll(
                    item("Select or replace…", keyOrNull(Keybinds.Action.SELECT_BY_TYPE), () -> openSelectByType(type)));
            contextMenu.getItems().add(byType);
        }
        List<javafx.scene.control.MenuItem> transforms = transformItems.get();
        if (!transforms.isEmpty() && (n > 0 || layer != null)) {
            javafx.scene.control.Menu tm = new javafx.scene.control.Menu(n > 0 ? "Transform selection" : "Transform " + layer.name());
            tm.getItems().setAll(transforms);
            contextMenu.getItems().add(tm);
        }
        if (n > 0) {
            contextMenu.getItems().addAll(
                    item(String.format("Delete %,d block%s", n, n == 1 ? "" : "s"), "Del", this::deleteSelectedBlocks),
                    item("Replace with " + (held == null ? "held block" : BlockInfoHud.name(ws.assets(), held)), null, this::replaceSelection),
                    item("Fix fence, wall, redstone and stair shapes", null, this::fixSelectionShapes),
                    item("Copy to new layer", keyOrNull(Keybinds.Action.COPY_TO_LAYER), this::copySelectionToLayer),
                    item("Move to new layer", keyOrNull(Keybinds.Action.MOVE_TO_LAYER), this::moveSelectionToNewLayer),
                    item("Move to active layer" + (ws.activeLayerProperty().get() == null ? "" : " (" + ws.activeLayerProperty().get().name() + ")"),
                            null, this::moveSelectionToActiveLayer),
                    item("Move or rotate", keyOrNull(Keybinds.Action.TOOL_MOVE), () -> ws.toolProperty().set(ToolKind.MOVE)),
                    item("Clear selection", keyOrNull(Keybinds.Action.CANCEL), this::clearBlockSelection));
        }
        if (layer != null) {
            if (!contextMenu.getItems().isEmpty()) contextMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            contextMenu.getItems().addAll(
                    item("Select all in " + layer.name(), null, () -> selectAllIn(layer)),
                    item("Select or replace by type…", keyOrNull(Keybinds.Action.SELECT_BY_TYPE), () -> openSelectByType(null)),
                    item("Frame " + layer.name(), keyOrNull(Keybinds.Action.FRAME_ACTIVE), () -> frameLayer(layer)),
                    item("Fix block shapes in " + layer.name(), null, () -> fixLayerShapes(layer)),
                    item("Hide " + layer.name(), null, () -> ws.editor().modifyLayer(layer, "Hide " + layer.name(), null, x -> x.setVisible(false))),
                    item(layer.locked() ? "Unlock " + layer.name() : "Lock " + layer.name(), null,
                            () -> ws.editor().modifyLayer(layer, (layer.locked() ? "Unlock " : "Lock ") + layer.name(), null, x -> x.setLocked(!x.locked()))));
        }
        if (contextMenu.getItems().isEmpty()) return;
        contextMenu.show(this, screenX, screenY);
    }

    private static javafx.scene.control.MenuItem item(String text, String accel, Runnable action) {
        javafx.scene.control.MenuItem mi = new javafx.scene.control.MenuItem(text);
        if (accel != null) mi.setText(text + "    (" + accel + ")");
        mi.setOnAction(e -> action.run());
        return mi;
    }

    /** Ctrl+A: every block of the active layer, in Select mode. */
    public void selectAllInActive() {
        Layer a = ws.activeLayerProperty().get();
        if (a == null) {
            showToast("No active layer");
            return;
        }
        ws.toolProperty().set(ToolKind.SELECT);
        selectAllIn(a);
    }

    /** Alt+A: drops the block selection. */
    public void deselectBlocks() {
        clearBlockSelection();
    }

    /** Ctrl+J: copies the selected blocks into a new layer. */
    public void copySelectionToNewLayer() {
        if (blockSel.isEmpty()) {
            showToast("Select some blocks first (Select mode, Q)");
            return;
        }
        copySelectionToLayer();
    }

    /** Ctrl+R: fills the selected blocks with the held block. */
    public void replaceSelectionWithHeld() {
        if (blockSel.isEmpty()) {
            showToast("Select some blocks first (Select mode, Q)");
            return;
        }
        if (ws.selectedBlockProperty().get() == null) {
            showToast("Empty hand · choose a block first");
            return;
        }
        replaceSelection();
    }

    /** Alt+G: ground grid on / off. */
    public void toggleGrid() {
        ws.settings().showGrid = !ws.settings().showGrid;
        showToast(ws.settings().showGrid ? "Grid on" : "Grid off");
        requestRedraw();
    }

    private void selectAllIn(Layer l) {
        java.util.Set<Long> set = new java.util.HashSet<>();
        int base = l.offset().y(), lo = sliceMin(), hi = sliceMax();
        l.structure().forEachBlock((x, y, z, st) -> {
            if (y + base >= lo && y + base <= hi) set.add(BlockPos.pack(x, y, z));
        });
        blockSel.clear();
        if (!set.isEmpty()) blockSel.put(l.id(), set);
        selectOnly(l);
        selectionChanged();
    }

    /** Replaces every selected block with the held block, as one undo step. */
    private void replaceSelection() {
        BlockState held = ws.selectedBlockProperty().get();
        if (held == null || held.isAir()) return;
        editLayers("Replace selection", null, null, le -> {
            for (var e : blockSel.entrySet()) {
                Layer l = ws.scene().find(e.getKey()).orElse(null);
                if (l == null || l.locked()) continue;
                for (long packed : e.getValue()) {
                    BlockPos p = BlockPos.unpack(packed);
                    if (!l.structure().get(p).isAir()) le.setIn(l, l.toWorld(p), held);
                }
            }
            // The new blocks and their neighbours (in any layer) join up.
            le.reconnect();
        });
        showToast("Replaced with " + BlockInfoHud.name(ws.assets(), held));
    }

    /** Recomputes connecting shapes (fences, walls, panes, dust, rails, stairs) of the selected blocks. */
    private void fixSelectionShapes() {
        List<BlockPos> cells = new ArrayList<>();
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            if (l == null) continue;
            for (long packed : e.getValue()) cells.add(l.toWorld(BlockPos.unpack(packed)));
        }
        fixShapes(cells, "Fix block shapes");
    }

    /** Recomputes the connecting shapes of every block in a layer, e.g. after importing a schematic saved without them. */
    public void fixLayerShapes(Layer l) {
        if (l.locked()) {
            showToast("Layer is locked");
            return;
        }
        List<BlockPos> cells = new ArrayList<>();
        l.structure().forEachBlock((x, y, z, st) -> {
            if (BlockPlacement.hasShape(st)) cells.add(l.toWorld(x, y, z));
        });
        fixShapes(cells, "Fix block shapes in " + l.name());
    }

    private void fixShapes(List<BlockPos> cells, String label) {
        int[] n = {0};
        editLayers(label, null, null, le -> {
            List<BlockPos> shaped = cells.stream().filter(p -> BlockPlacement.hasShape(le.get(p))).toList();
            var updates = BlockPlacement.refreshShapes(shaped, le::get);
            updates.forEach(le::set);
            n[0] = updates.size();
        });
        showToast(n[0] == 0 ? "Every shape was already right" : String.format("Fixed %,d block shape%s", n[0], n[0] == 1 ? "" : "s"));
    }

    /** Copies the selected blocks of each layer into a new layer at the same place. */
    private void copySelectionToLayer() {
        List<Layer> made = new ArrayList<>();
        for (var e : blockSel.entrySet()) {
            Layer src = ws.scene().find(e.getKey()).orElse(null);
            if (src == null) continue;
            io.blockdesigner.core.model.Structure s = new io.blockdesigner.core.model.Structure();
            for (long packed : e.getValue()) {
                BlockPos p = BlockPos.unpack(packed);
                BlockState st = src.structure().get(p);
                if (st.isAir()) continue;
                s.set(p, st);
                var nbt = src.structure().blockEntity(p);
                if (nbt != null) s.setBlockEntity(p, nbt.copy());
            }
            if (s.isEmpty()) continue;
            Layer l = new Layer(src.name() + " selection", s);
            l.setOffset(src.offset());
            l.setTransform(src.transform());
            ws.editor().addLayer(l);
            made.add(l);
        }
        if (made.isEmpty()) return;
        ws.scene().setActive(made.getLast());
        ws.selectedLayers().setAll(made);
        showToast(made.size() == 1 ? "Copied to " + made.getFirst().name() : "Copied to " + made.size() + " new layers");
    }

    // ---- view cube, axis views and projection ------------------------------------------------------------------

    /**
     * Turns to an axis view, Blender-style: going there switches to orthographic (auto perspective) and picking the
     * view you are already in flips to the opposite one.
     */
    public void snapView(ViewCube.View v) {
        if (fly) setFly(false);
        if (ViewCube.View.of(camera) == v && animStart < 0) v = v.opposite();
        if (!camera.isOrtho()) {
            camera.setOrtho(true);
            autoOrtho = true;
        }
        animateTo(v.yaw, v.pitch);
        showToast(v.label + " · " + (v == ViewCube.View.FRONT ? "looking north" : v == ViewCube.View.BACK ? "looking south"
                : v == ViewCube.View.RIGHT ? "looking west" : v == ViewCube.View.LEFT ? "looking east"
                : v == ViewCube.View.TOP ? "looking down, north up" : "looking up") + (camera.isOrtho() ? " · Ortho" : ""));
    }

    /**
     * Alt+middle-drag: each {@value #SWING_PX} px of travel swings the camera a quarter turn that way and snaps to the
     * nearest axis view in ortho. Dragging left brings you round to the view from the left, up to the view from above.
     */
    private void swingView(double dx, double dy) {
        swingX += dx;
        swingY += dy;
        if (Math.max(Math.abs(swingX), Math.abs(swingY)) < SWING_PX) return;
        Vector3f f;
        if (Math.abs(swingX) >= Math.abs(swingY)) f = swingX < 0 ? camera.right() : camera.right().negate();
        else f = swingY < 0 ? camera.up().negate() : camera.up();
        swingX = swingY = 0;
        ViewCube.View v = viewLookingAlong(f);
        if (fly) setFly(false);
        if (!camera.isOrtho()) {
            camera.setOrtho(true);
            autoOrtho = true;
        }
        animateTo(v.yaw, v.pitch);
        showToast(v.label + " · Ortho");
    }

    /** The axis view whose look direction is closest to {@code f}. */
    private static ViewCube.View viewLookingAlong(Vector3f f) {
        float ax = Math.abs(f.x), ay = Math.abs(f.y), az = Math.abs(f.z);
        if (ay >= ax && ay >= az) return f.y < 0 ? ViewCube.View.TOP : ViewCube.View.BOTTOM;
        if (ax >= az) return f.x < 0 ? ViewCube.View.RIGHT : ViewCube.View.LEFT;
        return f.z < 0 ? ViewCube.View.FRONT : ViewCube.View.BACK;
    }

    /** P / O: perspective or orthographic. */
    public void setOrtho(boolean ortho) {
        if (fly) setFly(false);
        autoOrtho = false;
        if (camera.isOrtho() == ortho) {
            showToast(ortho ? "Already orthographic" : "Already perspective");
            return;
        }
        camera.setOrtho(ortho);
        showToast(ortho ? "Orthographic" : "Perspective");
        requestRedraw();
    }

    /** Numpad 9: the opposite side of the current view. */
    private void oppositeView() {
        if (fly) setFly(false);
        animateTo(camera.yaw() + (float) Math.PI, -camera.pitch());
    }

    /** Numpad 5 or the cube's button: perspective ↔ orthographic. */
    public void toggleOrtho() {
        if (fly) setFly(false);
        camera.setOrtho(!camera.isOrtho());
        autoOrtho = false;
        showToast(camera.isOrtho() ? "Orthographic" : "Perspective");
        requestRedraw();
    }

    private void leaveAutoOrtho() {
        if (!autoOrtho) return;
        camera.setOrtho(false);
        autoOrtho = false;
    }

    /** Dragging the cube orbits the view. */
    private void cubeOrbit(double dx, double dy) {
        if (fly) setFly(false);
        leaveAutoOrtho();
        animStart = -1;
        float k = (float) (0.008 * ws.settings().orbitSensitivity);
        camera.orbit((float) dx * k, (float) dy * k);
        requestRedraw();
    }

    private void animateTo(float yaw, float pitch) {
        animYaw0 = camera.yaw();
        animPitch0 = camera.pitch();
        // Shortest way round.
        animYaw1 = animYaw0 + (float) Math.IEEEremainder(yaw - animYaw0, 2 * Math.PI);
        animPitch1 = pitch;
        animStart = System.nanoTime();
        requestRedraw();
    }

    private void viewAnimStep() {
        if (animStart < 0) return;
        double t = Math.min(1, (System.nanoTime() - animStart) / 1e6 / VIEW_ANIM_MS);
        double e = 1 - Math.pow(1 - t, 3);
        camera.setAngles((float) (animYaw0 + (animYaw1 - animYaw0) * e), (float) (animPitch0 + (animPitch1 - animPitch0) * e));
        if (t >= 1) {
            // Land exactly on the view so the cube recognises it (and keep the yaw tidy).
            camera.setAngles((float) Math.IEEEremainder(animYaw1, 2 * Math.PI), animPitch1);
            animStart = -1;
        }
        requestRedraw();
    }

    /**
     * The camera keys (Blender's numpad by default): front, right and top views and their opposites, the opposite of
     * the current view, perspective / orthographic, orbiting in 15° steps, and framing the active layer.
     */
    public void cameraKey(Keybinds.Action a) {
        float step = (float) Math.toRadians(15);
        switch (a) {
            case VIEW_FRONT -> snapView(ViewCube.View.FRONT);
            case VIEW_BACK -> snapView(ViewCube.View.BACK);
            case VIEW_RIGHT -> snapView(ViewCube.View.RIGHT);
            case VIEW_LEFT -> snapView(ViewCube.View.LEFT);
            case VIEW_TOP -> snapView(ViewCube.View.TOP);
            case VIEW_BOTTOM -> snapView(ViewCube.View.BOTTOM);
            case VIEW_OPPOSITE -> oppositeView();
            case VIEW_ORTHO_TOGGLE -> toggleOrtho();
            case ORBIT_LEFT, ORBIT_RIGHT, ORBIT_UP, ORBIT_DOWN -> {
                if (fly) setFly(false);
                leaveAutoOrtho();
                animStart = -1;
                camera.orbit(a == Keybinds.Action.ORBIT_LEFT ? -step : a == Keybinds.Action.ORBIT_RIGHT ? step : 0,
                        a == Keybinds.Action.ORBIT_UP ? -step : a == Keybinds.Action.ORBIT_DOWN ? step : 0);
                requestRedraw();
            }
            case FRAME_ACTIVE -> {
                Layer l = ws.activeLayerProperty().get();
                if (l != null) frameLayer(l);
                else frameAll();
            }
            default -> {
            }
        }
    }

    // ---- move / rotate gizmos --------------------------------------------------------------------------------

    /**
     * A gizmo drag: the layers it moves, the pivot it started from, and how much has been applied so far (whole blocks
     * per axis for Move, quarter turns for Rotate). The whole drag is one undo group.
     */
    private static final class GizmoDrag {
        Gizmo.Handle handle;
        List<Layer> layers;
        Vector3f pivot;
        // Move: axis parameter or plane point where the drag started, and the offset applied so far
        float startT;
        Vector3f startHit;
        final int[] applied = new int[3];
        // Rotate: unwrapped screen angle and quarter turns applied so far
        double lastAngle, angle;
        int turns;
        boolean changed;
        /** Dragging a block selection: each floating layer and the layer its blocks came from (null for layers). */
        java.util.Map<Layer, Layer> lifted;
        /** The selection before the drag, restored on cancel. */
        java.util.Map<String, java.util.Set<Long>> selBefore;
    }

    private boolean isGizmoTool() {
        ToolKind t = ws.toolProperty().get();
        return t == ToolKind.MOVE || t == ToolKind.ROTATE;
    }

    private List<Layer> gizmoTargets() {
        return ws.nudgeTargets().stream().filter(l -> l.visible() && !l.locked() && l.worldBounds().isPresent()).toList();
    }

    /** Centre of the layers' combined world bounds. */
    private static Vector3f boundsCenter(List<Layer> layers) {
        Box u = null;
        for (Layer l : layers) {
            Box b = l.worldBounds().orElse(null);
            if (b == null) continue;
            u = u == null ? b : new Box(Math.min(u.minX(), b.minX()), Math.min(u.minY(), b.minY()), Math.min(u.minZ(), b.minZ()),
                    Math.max(u.maxX(), b.maxX()), Math.max(u.maxY(), b.maxY()), Math.max(u.maxZ(), b.maxZ()));
        }
        if (u == null) return null;
        return new Vector3f((u.minX() + u.maxX() + 1) / 2f, (u.minY() + u.maxY() + 1) / 2f, (u.minZ() + u.maxZ() + 1) / 2f);
    }

    private void updateGizmo() {
        Gizmo.Mode mode = !isGizmoTool() || fly || !placing.isEmpty() ? null
                : ws.toolProperty().get() == ToolKind.MOVE ? Gizmo.Mode.MOVE : Gizmo.Mode.ROTATE;
        Vector3f pivot = null;
        if (mode != null) {
            // Rotation turns about a fixed point; a move carries the gizmo along with the layers.
            pivot = gizmoDrag != null && mode == Gizmo.Mode.ROTATE ? gizmoDrag.pivot
                    : gizmoDrag != null ? boundsCenter(gizmoDrag.layers)
                    : movableSelection() ? selectionCenter() : boundsCenter(gizmoTargets());
        }
        gizmo.update(mode, pivot, camera, getWidth(), getHeight(), gizmoHot, gizmoDrag == null ? null : gizmoDrag.handle);
    }

    private void beginGizmoDrag(Gizmo.Handle h, double x, double y) {
        boolean selection = movableSelection();
        List<Layer> layers = selection ? List.of() : gizmoTargets();
        Vector3f pivot = selection ? selectionCenter() : boundsCenter(layers);
        if (pivot == null) {
            showToast(ws.nudgeTargets().isEmpty() ? "Select a layer first" : "Layer is locked");
            return;
        }
        GizmoDrag d = new GizmoDrag();
        d.handle = h;
        d.pivot = pivot;
        Vector3f[] r = ray(x, y);
        switch (h.kind()) {
            case AXIS -> d.startT = axisParam(r, pivot, Gizmo.AXES[h.axis()]);
            case PLANE, FREE -> {
                d.startHit = planeHit(r, pivot, h.kind() == Gizmo.Kind.FREE ? camera.forward() : Gizmo.AXES[h.axis()]);
                if (d.startHit == null) return;
            }
            case RING -> d.lastAngle = screenAngle(x, y);
        }
        gizmoDrag = d;
        if (selection) {
            // The selected blocks float in their own layers while dragged, and go back into their layers on release.
            ws.editor().undoStack().beginGroup(h.kind() == Gizmo.Kind.RING ? "Rotate selection" : "Move selection");
            d.selBefore = copySelection();
            d.lifted = liftSelection();
            d.layers = List.copyOf(d.lifted.keySet());
        } else {
            d.layers = layers;
            ws.editor().undoStack().beginGroup(h.kind() == Gizmo.Kind.RING ? "Rotate layers" : layers.size() == 1 ? "Move " + layers.getFirst().name() : "Move layers");
        }
        requestRedraw();
    }

    private void dragGizmo(double x, double y) {
        GizmoDrag d = gizmoDrag;
        Vector3f[] r = ray(x, y);
        if (d.handle.kind() == Gizmo.Kind.RING) {
            double a = screenAngle(x, y), da = a - d.lastAngle;
            if (da > Math.PI) da -= 2 * Math.PI;
            if (da < -Math.PI) da += 2 * Math.PI;
            d.angle += da;
            d.lastAngle = a;
            // Anticlockwise on screen is a right-handed turn when the axis points at the viewer.
            Vector3f axis = Gizmo.AXES[d.handle.axis()];
            double signed = Math.toDegrees(d.angle) * (axis.dot(new Vector3f(camera.eye()).sub(d.pivot)) >= 0 ? 1 : -1);
            int turns = (int) Math.round(signed / 90);
            while (d.turns < turns) {
                turnGroup(d, 1);
                d.turns++;
            }
            while (d.turns > turns) {
                turnGroup(d, -1);
                d.turns--;
            }
            showToast("Rotate " + Gizmo.axisName(d.handle.axis()) + " " + Math.round(signed) + "° → " + (d.turns * 90) + "°");
            requestRedraw();
            return;
        }
        int[] want = new int[3];
        switch (d.handle.kind()) {
            case AXIS -> want[d.handle.axis()] = Math.round(axisParam(r, d.pivot, Gizmo.AXES[d.handle.axis()]) - d.startT);
            case PLANE, FREE -> {
                boolean free = d.handle.kind() == Gizmo.Kind.FREE;
                Vector3f hit = planeHit(r, d.pivot, free ? camera.forward() : Gizmo.AXES[d.handle.axis()]);
                if (hit == null) return;
                Vector3f diff = hit.sub(d.startHit);
                float[] c = {diff.x, diff.y, diff.z};
                for (int i = 0; i < 3; i++) if (free || i != d.handle.axis()) want[i] = Math.round(c[i]);
            }
            default -> {
            }
        }
        int dx = want[0] - d.applied[0], dy = want[1] - d.applied[1], dz = want[2] - d.applied[2];
        if (dx == 0 && dy == 0 && dz == 0) return;
        ws.editor().nudge(d.layers, dx, dy, dz, "gizmo-move");
        System.arraycopy(want, 0, d.applied, 0, 3);
        d.changed = true;
        showToast("Δ " + fmt(want[0]) + ", " + fmt(want[1]) + ", " + fmt(want[2]));
        requestRedraw();
    }

    /**
     * One right-handed quarter turn ({@code sign} ±1) of every dragged layer about the drag's pivot: each layer turns
     * about its own middle (Y through its transform, X/Z by rewriting blocks), then shifts so its middle orbits the pivot.
     */
    private void turnGroup(GizmoDrag d, int sign) {
        int axis = d.handle.axis();
        for (Layer l : d.layers) {
            Optional<Box> lb = l.structure().bounds(), wb0 = l.worldBounds();
            if (lb.isEmpty() || wb0.isEmpty()) continue;
            Vector3f c0 = boxCenter(wb0.get());
            // Transform.rotation(1) is clockwise from above, the opposite of right-handed about +Y.
            if (axis == 1) spinLayer(l, lb.get(), -sign);
            else tipLayer(l, lb.get(), sign, axis == 0 ? new BlockPos(0, 0, -1) : new BlockPos(1, 0, 0));
            Vector3f rel = new Vector3f(c0).sub(d.pivot), turned = quarterTurn(rel, axis, sign).add(d.pivot);
            Vector3f c1 = boxCenter(l.worldBounds().orElse(wb0.get()));
            int mx = Math.round(turned.x - c1.x), my = Math.round(turned.y - c1.y), mz = Math.round(turned.z - c1.z);
            if (mx != 0 || my != 0 || mz != 0) ws.editor().nudge(List.of(l), mx, my, mz, null);
        }
        d.changed = true;
    }

    /** Right-handed 90° turn about a world axis, {@code sign} times (±1). */
    private static Vector3f quarterTurn(Vector3f v, int axis, int sign) {
        return switch (axis) {
            case 0 -> sign > 0 ? new Vector3f(v.x, -v.z, v.y) : new Vector3f(v.x, v.z, -v.y);
            case 1 -> sign > 0 ? new Vector3f(v.z, v.y, -v.x) : new Vector3f(-v.z, v.y, v.x);
            default -> sign > 0 ? new Vector3f(-v.y, v.x, v.z) : new Vector3f(v.y, -v.x, v.z);
        };
    }

    private static Vector3f boxCenter(Box b) {
        return new Vector3f((b.minX() + b.maxX() + 1) / 2f, (b.minY() + b.maxY() + 1) / 2f, (b.minZ() + b.maxZ() + 1) / 2f);
    }

    private void endGizmoDrag() {
        GizmoDrag d = gizmoDrag;
        gizmoDrag = null;
        if (d.lifted != null) dropSelection(d.lifted);
        ws.editor().undoStack().endGroup();
        requestRedraw();
    }

    private void cancelGizmoDrag() {
        GizmoDrag d = gizmoDrag;
        gizmoDrag = null;
        // A lifted selection always changed the layers, so it is always undone.
        if (d.lifted != null) dropSelection(d.lifted);
        ws.editor().undoStack().endGroup();
        if (d.changed || d.lifted != null) ws.editor().undoStack().undo();
        if (d.selBefore != null) {
            blockSel.clear();
            blockSel.putAll(d.selBefore);
        }
        showToast("Cancelled");
        requestRedraw();
    }

    // ---- moving selected blocks --------------------------------------------------------------------------------

    /** Whether the Move / Rotate gizmo works on the selected blocks (there are some, in unlocked, visible layers). */
    private boolean movableSelection() {
        for (String id : blockSel.keySet()) {
            Layer l = ws.scene().find(id).orElse(null);
            if (l != null && !l.locked() && l.visible() && !blockSel.get(id).isEmpty()) return true;
        }
        return false;
    }

    private java.util.Map<String, java.util.Set<Long>> copySelection() {
        java.util.Map<String, java.util.Set<Long>> out = new java.util.LinkedHashMap<>();
        blockSel.forEach((k, v) -> out.put(k, new java.util.HashSet<>(v)));
        return out;
    }

    private List<Object> selCenterKey;
    private Vector3f selCenter;

    /** Centre of the selected blocks' world bounds, or null; cached while the selection and its layers stay put. */
    private Vector3f selectionCenter() {
        List<Object> key = new ArrayList<>();
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            key.add(List.of(e.getKey(), e.getValue().size(), System.identityHashCode(e.getValue()),
                    l == null ? "" : l.offset(), l == null ? "" : l.transform(), l != null && l.locked(), l != null && l.visible()));
        }
        if (key.equals(selCenterKey)) return selCenter == null ? null : new Vector3f(selCenter);
        selCenterKey = key;
        selCenter = computeSelectionCenter();
        return selCenter == null ? null : new Vector3f(selCenter);
    }

    private Vector3f computeSelectionCenter() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            if (l == null || l.locked() || !l.visible()) continue;
            for (long packed : e.getValue()) {
                BlockPos p = l.toWorld(BlockPos.unpack(packed));
                x0 = Math.min(x0, p.x());
                y0 = Math.min(y0, p.y());
                z0 = Math.min(z0, p.z());
                x1 = Math.max(x1, p.x());
                y1 = Math.max(y1, p.y());
                z1 = Math.max(z1, p.z());
            }
        }
        if (x0 > x1) return null;
        return new Vector3f((x0 + x1 + 1) / 2f, (y0 + y1 + 1) / 2f, (z0 + z1 + 1) / 2f);
    }

    /** Moves the selected blocks into {@code dest(source)} (see {@link SelectionTransfer}); returns the new selection. */
    private java.util.Map<String, java.util.Set<Long>> transferSelection(java.util.function.Function<Layer, Layer> dest, String label) {
        return SelectionTransfer.transfer(ws.editor(), blockSel, dest, label);
    }

    /** Lifts the selected blocks into a floating layer per source layer; returns floating layer → source. */
    private java.util.Map<Layer, Layer> liftSelection() {
        java.util.Map<Layer, Layer> lifted = new java.util.LinkedHashMap<>();
        Layer active = ws.activeLayerProperty().get();
        var sel = transferSelection(src -> {
            if (!src.visible()) return null;
            Layer f = new Layer(src.name() + " (moving)", new io.blockdesigner.core.model.Structure());
            f.setOffset(src.offset());
            f.setTransform(src.transform());
            f.setColor(src.color());
            ws.editor().addLayer(f, ws.scene().indexOf(src) + 1);
            lifted.put(f, src);
            return f;
        }, "Lift selection");
        // Only the floating blocks are selected (and so outlined) while they move.
        blockSel.clear();
        sel.forEach((id, cells) -> {
            if (lifted.keySet().stream().anyMatch(f -> f.id().equals(id))) blockSel.put(id, cells);
        });
        if (active != null) ws.scene().setActive(active);
        return lifted;
    }

    /** Puts lifted blocks back into the layers they came from, at their new place, and removes the floating layers. */
    private void dropSelection(java.util.Map<Layer, Layer> lifted) {
        blockSel.clear();
        for (Layer f : lifted.keySet()) {
            java.util.Set<Long> cells = new java.util.HashSet<>();
            f.structure().forEachBlock((x, y, z, st) -> cells.add(BlockPos.pack(x, y, z)));
            if (!cells.isEmpty()) blockSel.put(f.id(), cells);
        }
        var sel = transferSelection(lifted::get, "Place selection");
        for (Layer f : lifted.keySet()) ws.editor().removeLayer(f);
        blockSel.clear();
        sel.forEach((id, cells) -> {
            if (lifted.keySet().stream().noneMatch(f -> f.id().equals(id))) blockSel.put(id, cells);
        });
        Layer first = lifted.values().iterator().next();
        if (ws.scene().indexOf(first) >= 0) ws.scene().setActive(first);
        selectionChanged();
    }

    /** Ctrl+Shift+J: moves the selected blocks out of their layers into a new layer (one per layer) at the same place. */
    public void moveSelectionToNewLayer() {
        if (blockSel.isEmpty()) {
            showToast("Select some blocks first (Select mode, Q)");
            return;
        }
        List<Layer> made = new ArrayList<>();
        ws.editor().undoStack().beginGroup("Move selection to new layer");
        try {
            var sel = transferSelection(src -> {
                Layer l = new Layer(src.name() + " selection", new io.blockdesigner.core.model.Structure());
                l.setOffset(src.offset());
                l.setTransform(src.transform());
                ws.editor().addLayer(l, ws.scene().indexOf(src) + 1);
                made.add(l);
                return l;
            }, "Move selection to new layer");
            blockSel.clear();
            blockSel.putAll(sel);
        } finally {
            ws.editor().undoStack().endGroup();
        }
        if (made.isEmpty()) {
            showToast("Those blocks are in locked layers");
            return;
        }
        ws.scene().setActive(made.getLast());
        ws.selectedLayers().setAll(made);
        showToast(made.size() == 1 ? "Moved to " + made.getFirst().name() : "Moved to " + made.size() + " new layers");
        requestRedraw();
    }

    /** Moves the selected blocks from other layers into the active layer, at the same place. */
    public void moveSelectionToActiveLayer() {
        Layer target = ws.activeLayerProperty().get();
        if (blockSel.isEmpty()) {
            showToast("Select some blocks first (Select mode, Q)");
            return;
        }
        if (target == null || target.locked()) {
            showToast(target == null ? "No active layer" : "The active layer is locked");
            return;
        }
        long moving = blockSel.entrySet().stream().filter(e -> !e.getKey().equals(target.id())).mapToLong(e -> e.getValue().size()).sum();
        if (moving == 0) {
            showToast("Those blocks are already in " + target.name());
            return;
        }
        ws.editor().undoStack().beginGroup("Move selection to " + target.name());
        try {
            var sel = transferSelection(src -> target, "Move selection to " + target.name());
            blockSel.clear();
            blockSel.putAll(sel);
        } finally {
            ws.editor().undoStack().endGroup();
        }
        showToast(String.format("Moved %,d block%s into %s", moving, moving == 1 ? "" : "s", target.name()));
        requestRedraw();
    }

    /** Where along the axis line through {@code p} the mouse ray passes closest. */
    private static float axisParam(Vector3f[] ray, Vector3f p, Vector3f axis) {
        Vector3f w0 = new Vector3f(p).sub(ray[0]);
        float b = axis.dot(ray[1]), denom = 1 - b * b;
        if (Math.abs(denom) < 1e-4f) return 0;
        return (b * ray[1].dot(w0) - axis.dot(w0)) / denom;
    }

    /** The mouse ray's hit on the plane through {@code p} with normal {@code n}, or null when (nearly) parallel. */
    private static Vector3f planeHit(Vector3f[] ray, Vector3f p, Vector3f n) {
        float denom = n.dot(ray[1]);
        if (Math.abs(denom) < 1e-4f) return null;
        float t = n.dot(new Vector3f(p).sub(ray[0])) / denom;
        return t < 0 ? null : new Vector3f(ray[1]).mul(t).add(ray[0]);
    }

    /** Anticlockwise screen angle of the mouse around the gizmo centre. */
    private double screenAngle(double x, double y) {
        double[] c = gizmo.center();
        return c == null ? 0 : Math.atan2(-(y - c[1]), x - c[0]);
    }

    // ---- paint brush / eraser ----------------------------------------------------------------------------------

    /** A brush stroke: its mode, where it last stamped, and the undo group it builds. */
    private static final class Stroke {
        final io.blockdesigner.core.edit.Sculpt.Mode mode;
        final boolean invert, erase;
        /** Where a slope stroke started (its base). */
        BlockPos start;
        /** The aimed face at the last stamp (smoothing a top face smooths the heightmap, a side face in 3D). */
        BlockPos normal;
        BlockPos last;
        long lastStamp;
        int changed;
        /** Cells this stroke drew or erased (world): the brush won't stamp on its own paint or through its own hole. */
        final java.util.Set<BlockPos> touched = new java.util.HashSet<>();

        Stroke(io.blockdesigner.core.edit.Sculpt.Mode mode, boolean invert) {
            this.mode = mode;
            this.invert = invert;
            this.erase = mode == io.blockdesigner.core.edit.Sculpt.Mode.ERASE;
        }

        boolean shapeOnly() {
            return mode == io.blockdesigner.core.edit.Sculpt.Mode.DRAW || erase;
        }
    }

    /** - / =: one brush size smaller or bigger. */
    public void stepBrush(int d) {
        brushBar.step(d);
        brushPopup.sync();
        int n = ws.settings().brushSize, w = n * 2 - 1;
        showToast("Brush size " + n + (n == 1 ? " (1 block)" : " (" + w + "×" + w + "×" + w + ")"));
    }

    /** , / .: brush strength. */
    public void stepBrushStrength(int d) {
        ws.settings().brushStrength = Math.clamp(ws.settings().brushStrength + d, 1, 5);
        brushBar.sync();
        brushPopup.sync();
        showToast("Brush strength " + ws.settings().brushStrength);
    }

    /** Right-click in Brush mode: the brush settings at the cursor. */
    private void showBrushPopup(double screenX, double screenY) {
        if (getScene() == null) return;
        brushPopup.sync();
        brushPopup.show(getScene().getWindow(), screenX + 4, screenY + 4);
    }

    /** The popup's keys (mode letters, size, strength) while it is open; used by the window's key filter. */
    public boolean brushPopupKey(KeyEvent e) {
        return brushPopup.key(e);
    }

    /** Where the brush is centred: the empty cell in front of the aimed face (draw) or the aimed block itself. */
    private BlockPos brushCenter(io.blockdesigner.core.edit.Sculpt.Mode mode) {
        boolean draw = mode == io.blockdesigner.core.edit.Sculpt.Mode.DRAW;
        if (hover != null) return draw ? hover.adjacentWorld() : hover.world();
        return draw ? hoverGround : null;
    }

    private io.blockdesigner.core.edit.Sculpt.Brush brush() {
        return new io.blockdesigner.core.edit.Sculpt.Brush(ws.settings().brushSize, ws.settings().brushCube, ws.settings().brushStrength);
    }

    /** The cells of the brush shape around {@code c}, within the slice view. */
    private List<BlockPos> brushCells(BlockPos c) {
        int lo = sliceMin(), hi = sliceMax();
        return io.blockdesigner.core.edit.Sculpt.shape(brush(), c).stream().filter(p -> p.y() >= lo && p.y() <= hi).toList();
    }

    /**
     * Starts a stroke: {@code forced} (right-drag: smooth) or the chosen mode. Blender-style Shift smooths and Ctrl
     * inverts, but not while flying, where those keys fly.
     */
    private void startStroke(io.blockdesigner.core.edit.Sculpt.Mode forced, boolean shift, boolean ctrl) {
        if (!placing.isEmpty()) return;
        endStroke();
        var base = ws.toolProperty().get() == ToolKind.ERASER ? io.blockdesigner.core.edit.Sculpt.Mode.ERASE : BrushPopup.mode(ws.settings());
        boolean invert = !fly && ctrl && forced == null;
        var mode = forced != null ? forced : !fly && shift ? io.blockdesigner.core.edit.Sculpt.Mode.SMOOTH : invert ? base.inverse() : base;
        if (mode == io.blockdesigner.core.edit.Sculpt.Mode.DRAW && ws.blockToPlace() == null) {
            showToast("Empty hand · pick a block (middle-click), choose a hotbar slot or click one in the palette");
            return;
        }
        stroke = new Stroke(mode, invert);
        stroke.start = brushCenter(mode);
        stroke.normal = hover != null ? hover.normal() : new BlockPos(0, 1, 0);
        ws.editor().undoStack().beginGroup(mode.label);
        if (mode != base) showToast(mode.label + (invert ? " (inverted)" : ""));
        stamp();
    }

    /**
     * Called every frame while the button is held. Draw and erase stamp when the brush moves to a new cell (never on
     * their own work, so a still mouse can't build towards the camera or tunnel in); the sculpt modes also keep working
     * a few times a second while held in place, like an airbrush.
     */
    private void strokeStep() {
        if (stroke == null) return;
        if (fly) updateHover(aimX(), aimY());
        BlockPos c = brushCenter(stroke.mode);
        if (c == null) return;
        // Dabs are paced by the Brush speed setting; the path between them is filled in below, so strokes stay whole.
        long interval = (long) (1e9 / Math.clamp(ws.settings().brushRate, 1, 60));
        if (System.nanoTime() - stroke.lastStamp < interval) return;
        if (c.equals(stroke.last)) {
            // Held still: draw and erase wait for the mouse; the sculpt modes keep working, like an airbrush.
            if (stroke.shapeOnly()) return;
            stamp(c);
            return;
        }
        // Fast drags jump several cells a frame: fill in the path so the stroke stays continuous.
        BlockPos from = stroke.last;
        if (from != null) {
            int dx = c.x() - from.x(), dy = c.y() - from.y(), dz = c.z() - from.z();
            int dist = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            int spacing = Math.max(1, ws.settings().brushSize / 2);
            for (int i = spacing; i < dist; i += spacing) {
                double t = (double) i / dist;
                stamp(new BlockPos((int) Math.round(from.x() + dx * t), (int) Math.round(from.y() + dy * t), (int) Math.round(from.z() + dz * t)));
            }
        }
        stamp(c);
    }

    private void endStroke() {
        if (stroke == null) return;
        stroke = null;
        ws.editor().undoStack().endGroup();
        requestRedraw();
    }

    /** One dab of the brush at the current aim. */
    private void stamp() {
        BlockPos c = brushCenter(stroke.mode);
        if (c != null) stamp(c);
    }

    /** One dab of the brush at {@code c}. */
    private void stamp(BlockPos c) {
        if (hover != null) stroke.normal = hover.normal();
        stroke.last = c;
        stroke.lastStamp = System.nanoTime();
        int n = switch (stroke.mode) {
            case DRAW -> paintCells(brushCells(c));
            case ERASE -> eraseCells(c, brushCells(c));
            default -> sculptCells(c);
        };
        stroke.changed += n;
        if (n > 0 && ws.settings().blockSounds && System.nanoTime() - lastBrushSound > 70_000_000L) {
            lastBrushSound = System.nanoTime();
            boolean removing = switch (stroke.mode) {
                case ERASE, ERODE, LOWER -> true;
                case SLOPE -> stroke.invert;
                default -> false;
            };
            if (removing) sounds.breakBlock(ws.settings().soundVolume);
            else sounds.place(ws.settings().soundVolume);
        }
        updateHover(aimX(), aimY());
        requestRedraw();
    }

    /** The sculpt modes, on the aimed block's layer (or the active one), read and written in world coordinates. */
    private int sculptCells(BlockPos c) {
        Layer l = hover != null ? hover.layer() : ws.activeLayerProperty().get();
        if (l == null) return 0;
        if (l.locked()) {
            showToast("Layer is locked");
            return 0;
        }
        Transform t = l.transform();
        var mode = stroke.mode;
        // Smoothing, erosion, fill and pinch keep the terrain's own blocks; the terrain brushes add the held block.
        boolean natural = mode == io.blockdesigner.core.edit.Sculpt.Mode.SMOOTH || mode == io.blockdesigner.core.edit.Sculpt.Mode.FILL
                || mode == io.blockdesigner.core.edit.Sculpt.Mode.ERODE || mode == io.blockdesigner.core.edit.Sculpt.Mode.PINCH;
        java.util.function.Supplier<BlockState> material = natural ? null : ws::blockToPlace;
        int lo = sliceMin(), hi = sliceMax();
        int[] changed = {0};
        // The brush works on the terrain as shown, across every visible layer; new ground goes in the aimed layer.
        editLayers(mode.label, null, l, le -> {
            io.blockdesigner.core.edit.Sculpt.World w = new io.blockdesigner.core.edit.Sculpt.World() {
                @Override
                public BlockState get(BlockPos p) {
                    return le.get(p);
                }

                @Override
                public void set(BlockPos p, BlockState st) {
                    if (!st.isAir() && nearCamera(p)) return;
                    le.set(p, st);
                }
            };
            changed[0] = io.blockdesigner.core.edit.Sculpt.apply(mode, stroke.invert, brush(), c, stroke.normal, w, material, stroke.start,
                    y -> y >= lo && y <= hi).size();
            if (le.written().size() <= 4096) le.reconnect();
        });
        return changed[0];
    }

    /** Fills the empty cells with the held block (a random hotbar pick per block in shuffle mode), in the active layer. */
    private int paintCells(List<BlockPos> cells) {
        Layer l = ws.activeLayerProperty().get();
        if (l == null) {
            l = new Layer("Layer " + (ws.scene().layers().size() + 1), new io.blockdesigner.core.model.Structure());
            ws.editor().addLayer(l);
        }
        if (l.locked()) {
            showToast("Active layer is locked");
            return 0;
        }
        Layer target = l;
        int[] n = {0};
        editLayers("Paint", null, l, le -> {
            for (BlockPos w : cells) {
                if (nearCamera(w)) continue;
                // Only empty cells, in every visible layer: the brush doesn't paint inside other layers' blocks.
                if (!le.get(w).isAir()) continue;
                BlockState st = ws.blockToPlace();
                if (st == null || st.isAir()) continue;
                le.setIn(target, w, st);
                stroke.touched.add(w);
                n[0]++;
            }
            if (le.written().size() <= 4096) le.reconnect();
        });
        return n[0];
    }

    /** Removes every block in the cells from the visible, unlocked layers. */
    private int eraseCells(BlockPos center, List<BlockPos> cells) {
        int[] n = {0};
        BlockState[] first = {null};
        editLayers("Erase", null, null, le -> {
            for (BlockPos w : cells) {
                // Clear the cell in every visible, unlocked layer (a locked layer's block stays).
                for (int guard = 0; guard < 64; guard++) {
                    Layer holder = le.holder(w);
                    if (holder == null || holder.locked()) break;
                    if (first[0] == null) first[0] = le.get(w);
                    le.setIn(holder, w, BlockState.AIR);
                    n[0]++;
                }
                stroke.touched.add(w);
            }
            if (le.written().size() <= 4096) le.reconnect();
        });
        BlockState burst = first[0];
        if (burst != null && ws.settings().breakParticles) {
            BlockAssets assets = ws.assets();
            particles.burst(center.x(), center.y(), center.z(), assets == null ? null : BlockIcons.icon(assets, burst));
            particlesDrawn = true;
        }
        return n[0];
    }

    /** The brush outline under the cursor: green to paint, red to erase; spheres get three rings. */
    private void drawBrushPreview(List<FrameRequest.Line> lines) {
        ToolKind tool = ws.toolProperty().get();
        if (tool != ToolKind.BRUSH && tool != ToolKind.ERASER || !placing.isEmpty()) return;
        var mode = stroke != null ? stroke.mode : tool == ToolKind.ERASER ? io.blockdesigner.core.edit.Sculpt.Mode.ERASE : BrushPopup.mode(ws.settings());
        BlockPos c = brushCenter(mode);
        if (c == null) return;
        // Green adds, red removes, blue reshapes.
        int color = switch (mode) {
            case DRAW, FILL, RAISE -> 0xFF46C46E;
            case ERASE, ERODE, LOWER -> 0xFFE5484D;
            default -> 0xFF3E9BFF;
        };
        int r = ws.settings().brushSize - 1;
        float cx = c.x() + 0.5f, cy = c.y() + 0.5f, cz = c.z() + 0.5f, h = r + 0.5f + 0.02f;
        if (mode.terrain()) {
            // Terrain brushes work on columns: a ring on the surface.
            int seg = 48;
            for (int i = 0; i < seg; i++) {
                double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                lines.add(new FrameRequest.Line(cx + (float) Math.cos(a0) * h, cy + 0.55f, cz + (float) Math.sin(a0) * h,
                        cx + (float) Math.cos(a1) * h, cy + 0.55f, cz + (float) Math.sin(a1) * h, color));
            }
            Overlays.block(lines, c.x(), c.y(), c.z(), color);
            return;
        }
        if (ws.settings().brushCube || r == 0) {
            Overlays.box(lines, cx - h, cy - h, cz - h, cx + h, cy + h, cz + h, color);
            return;
        }
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            float c0 = (float) Math.cos(a0) * h, s0 = (float) Math.sin(a0) * h, c1 = (float) Math.cos(a1) * h, s1 = (float) Math.sin(a1) * h;
            lines.add(new FrameRequest.Line(cx + c0, cy, cz + s0, cx + c1, cy, cz + s1, color));
            lines.add(new FrameRequest.Line(cx + c0, cy + s0, cz, cx + c1, cy + s1, cz, color));
            lines.add(new FrameRequest.Line(cx, cy + c0, cz + s0, cx, cy + c1, cz + s1, color));
        }
    }

    // ---- select by type -------------------------------------------------------------------------------------

    private interface TypeVisitor {
        /** A non-air block of {@code l} (packed layer-local position) with its world-facing state. */
        void visit(Layer l, long packed, BlockState world);
    }

    /** Layers a scope searches: visible, unlocked and not being placed, like the marquee. */
    private List<Layer> typeLayers(SelectByTypePanel.Scope scope) {
        Layer active = ws.activeLayerProperty().get();
        List<Layer> from = switch (scope) {
            case ACTIVE -> active == null ? List.of() : List.of(active);
            case SELECTED -> ws.selectedLayers().isEmpty() && active != null ? List.of(active) : List.copyOf(ws.selectedLayers());
            case VISIBLE -> ws.scene().layers();
            case SELECTION -> ws.scene().layers().stream().filter(l -> blockSel.containsKey(l.id())).toList();
        };
        return from.stream().filter(l -> l.visible() && !l.locked() && !placing.contains(l)).toList();
    }

    /** Visits the blocks of the layers (only already-selected ones when {@code withinSelection}) inside the world Y range. */
    private void forEachTypeCandidate(List<Layer> layers, boolean withinSelection, int lo, int hi, TypeVisitor v) {
        for (Layer l : layers) {
            // States are interned, so each distinct state is turned to world-facing once per layer.
            java.util.Map<BlockState, BlockState> world = new java.util.HashMap<>();
            Transform t = l.transform();
            int base = l.offset().y();
            if (withinSelection) {
                java.util.Set<Long> set = blockSel.get(l.id());
                if (set == null) continue;
                for (long packed : set) {
                    BlockPos p = BlockPos.unpack(packed);
                    if (p.y() + base < lo || p.y() + base > hi) continue;
                    BlockState st = l.structure().get(p);
                    if (!st.isAir()) v.visit(l, packed, world.computeIfAbsent(st, s -> BlockTransformer.defaults().apply(s, t)));
                }
            } else {
                l.structure().forEachBlock((x, y, z, st) -> {
                    if (y + base < lo || y + base > hi || st.isAir()) return;
                    v.visit(l, BlockPos.pack(x, y, z), world.computeIfAbsent(st, s -> BlockTransformer.defaults().apply(s, t)));
                });
            }
        }
    }

    private void forEachTypeCandidate(SelectByTypePanel.Query q, TypeVisitor v) {
        forEachTypeCandidate(typeLayers(q.scope()), q.scope() == SelectByTypePanel.Scope.SELECTION,
                q.slice() ? sliceMin() : Integer.MIN_VALUE, q.slice() ? sliceMax() : Integer.MAX_VALUE, v);
    }

    /** Alt+T: Select by type, starting from the block under the cursor. */
    public void openSelectByTypeAtAim() {
        Layer hl = hover != null ? hover.layer() : null;
        openSelectByType(hl == null ? null : BlockTransformer.defaults().apply(hl.structure().get(hover.local()), hl.transform()));
    }

    /** Opens the Select by type dialog (Alt+T), with {@code preselect}'s block (world-facing) ticked if given. */
    public void openSelectByType(BlockState preselect) {
        if (!placing.isEmpty()) return;
        setFly(false);
        ws.toolProperty().set(ToolKind.SELECT);
        SelectByTypePanel.Scope scope = ws.selectedLayers().size() > 1 ? SelectByTypePanel.Scope.SELECTED : SelectByTypePanel.Scope.VISIBLE;
        if (selectByTypePopover != null && selectByTypePopover.isShowing()) selectByTypePopover.hide();
        SelectByTypePanel panel = new SelectByTypePanel(ws.assets(), scope, !blockSel.isEmpty(), sliceY != null, preselect, ws.blockToPlace(), q -> {
            java.util.Map<String, Long> counts = new java.util.HashMap<>();
            forEachTypeCandidate(q, (l, packed, st) -> {
                if (q.matches(st)) counts.merge(q.key(st), 1L, Long::sum);
            });
            return counts;
        }, this::applySelectByType, this::applyReplaceByType, () -> {
            if (selectByTypePopover != null) selectByTypePopover.hide();
            requestFocus();
        });
        selectByTypePopover = SidePopover.create("Select or replace by type", panel);
        SidePopover.show(selectByTypePopover, filterButton);
    }

    private void applySelectByType(SelectByTypePanel.Options o) {
        SelectByTypePanel.Query q = o.query();
        java.util.Map<String, java.util.Set<Long>> found = new java.util.LinkedHashMap<>();
        forEachTypeCandidate(q, (l, packed, st) -> {
            if (q.matches(st) && o.keys().contains(q.key(st))) found.computeIfAbsent(l.id(), k -> new java.util.HashSet<>()).add(packed);
        });
        applyTypeSelection(found, o.mode());
    }

    /** Replaces every ticked block with the chosen one (keeping facing and the like when asked) as one undo step. */
    private void applyReplaceByType(SelectByTypePanel.Replace r) {
        SelectByTypePanel.Query q = r.query();
        java.util.Map<BlockPos, BlockState> changes = new java.util.LinkedHashMap<>();
        java.util.Map<BlockState, BlockState> results = new java.util.HashMap<>();
        forEachTypeCandidate(q, (l, packed, st) -> {
            if (q.matches(st) && r.keys().contains(q.key(st)))
                changes.put(l.toWorld(BlockPos.unpack(packed)), results.computeIfAbsent(st, f -> r.result(f, ws.assets())));
        });
        if (changes.isEmpty()) return;
        int[] n = {0};
        String what = r.target().isAir() ? "Remove by type" : "Replace by type";
        boolean ok = editWorld(what, world -> changes.forEach((p, st) -> {
            if (!world.get(p).equals(st)) {
                world.set(p, st);
                n[0]++;
            }
        }));
        if (!ok) return;
        String name = r.target().isAir() ? null : BlockInfoHud.name(ws.assets(), r.target());
        showToast(n[0] == 0 ? "Nothing changed: those blocks already match"
                : name == null ? String.format("Removed %,d block%s", n[0], n[0] == 1 ? "" : "s")
                : String.format("Replaced %,d block%s with %s", n[0], n[0] == 1 ? "" : "s", name));
    }

    /** Selects every block with {@code type}'s id (or exactly its state) in the layers, replacing the selection. */
    private void quickSelectType(BlockState type, boolean exact, List<Layer> layers) {
        java.util.Map<String, java.util.Set<Long>> found = new java.util.LinkedHashMap<>();
        forEachTypeCandidate(layers.stream().filter(l -> l.visible() && !l.locked() && !placing.contains(l)).toList(), false,
                sliceMin(), sliceMax(), (l, packed, st) -> {
                    if (exact ? st == type : st.name().equals(type.name())) found.computeIfAbsent(l.id(), k -> new java.util.HashSet<>()).add(packed);
                });
        applyTypeSelection(found, SelectByTypePanel.Mode.REPLACE);
    }

    private void applyTypeSelection(java.util.Map<String, java.util.Set<Long>> found, SelectByTypePanel.Mode mode) {
        switch (mode) {
            case REPLACE -> {
                blockSel.clear();
                blockSel.putAll(found);
            }
            case ADD -> found.forEach((id, set) -> blockSel.computeIfAbsent(id, k -> new java.util.HashSet<>()).addAll(set));
            case REMOVE -> found.forEach((id, set) -> {
                java.util.Set<Long> cur = blockSel.get(id);
                if (cur == null) return;
                cur.removeAll(set);
                if (cur.isEmpty()) blockSel.remove(id);
            });
        }
        List<Layer> touched = ws.scene().layers().stream().filter(l -> blockSel.containsKey(l.id())).toList();
        if (!touched.isEmpty()) {
            ws.selectedLayers().setAll(touched);
            Layer active = ws.activeLayerProperty().get();
            if (active == null || !touched.contains(active)) ws.scene().setActive(touched.getLast());
        }
        selectionChanged();
    }

    // ---- selection ------------------------------------------------------------------------------------------

    /**
     * A click in Select mode: on an entity it selects the entity (Shift adds, Ctrl toggles); otherwise Shift / Ctrl add or
     * toggle single blocks and a plain click sets WorldEdit's pos1.
     */
    private void selectClick(boolean add, boolean toggle) {
        if (hoverEntity != null) {
            Layer l = hoverEntity.layer();
            var e = hoverEntity.entity();
            if (!add && !toggle) {
                entitySel.clear();
                blockSel.clear();
            }
            var set = entitySel.computeIfAbsent(l.id(), k -> new java.util.LinkedHashSet<>());
            if (toggle && set.contains(e)) set.remove(e);
            else set.add(e);
            if (set.isEmpty()) entitySel.remove(l.id());
            if (!ws.selectedLayers().contains(l)) {
                if (add || toggle) ws.selectedLayers().add(l);
                else ws.selectedLayers().setAll(l);
            }
            ws.scene().setActive(l);
            selectionChanged();
            return;
        }
        if (add || toggle) clickSelect(add, toggle);
        else {
            if (!entitySel.isEmpty()) {
                entitySel.clear();
                requestRedraw();
            }
            setCorner(true);
        }
    }

    // ---- entities -------------------------------------------------------------------------------------------

    /**
     * Right-click in Build mode while holding a mob: it stands on the aimed block's top (at the exact height, so slabs and
     * carpets work), or in the cell in front of a side, facing you. Frames and paintings hang on the aimed wall.
     */
    private void placeEntity(String key) {
        if (!holdFirst) return;
        io.blockdesigner.core.nbt.CompoundTag tpl = ws.heldEntityProperty().get();
        String id = tpl.getString("id");
        var kind = io.blockdesigner.core.model.EntityTypes.kind(id);
        boolean hangs = io.blockdesigner.core.model.EntityTypes.hanging(kind.id());
        Vector3f[] r = ray(aimX(), aimY());
        Vector3f dir = new Vector3f(r[1]).normalize();
        double x, y, z;
        io.blockdesigner.core.nbt.CompoundTag nbt = tpl.copy();
        nbt.remove("Pos");
        if (hover != null) {
            BlockPos n = hover.normal(), cell = hover.adjacentWorld();
            if (hangs) {
                if (kind.id().endsWith(":painting") && n.y() != 0) {
                    showToast("Paintings hang on walls: aim at the side of a block");
                    return;
                }
                int facing = io.blockdesigner.core.model.EntityTypes.facingOf(n.x(), n.y(), n.z());
                nbt.putString("id", kind.id());
                io.blockdesigner.core.model.EntityTypes.setFacing(nbt, facing);
                nbt.putInt("TileX", cell.x()).putInt("TileY", cell.y()).putInt("TileZ", cell.z());
                double[] c = io.blockdesigner.core.model.EntityTypes.hangingPosition(cell.x(), cell.y(), cell.z(), facing, nbt);
                x = c[0];
                y = c[1];
                z = c[2];
            } else if (n.y() > 0) {
                Vector3f hit = new Vector3f(dir).mul(hover.distance()).add(r[0]);
                x = cell.x() + 0.5;
                y = hit.y;
                z = cell.z() + 0.5;
            } else {
                x = cell.x() + 0.5;
                y = cell.y();
                z = cell.z() + 0.5;
            }
        } else if (hoverGround != null && !hangs) {
            x = hoverGround.x() + 0.5;
            y = hoverGround.y();
            z = hoverGround.z() + 0.5;
        } else {
            showToast(hangs ? "Aim at the side of a block to hang it" : "Aim at a block or the ground to place it");
            return;
        }
        if (!hangs) {
            // Face the camera, snapped to 22.5° like a standing sign.
            float yaw = (float) Math.toDegrees(Math.atan2(dir.x, -dir.z));
            yaw = Math.round(yaw / 22.5f) * 22.5f;
            nbt.put("Rotation", io.blockdesigner.core.nbt.ListTag.of(new io.blockdesigner.core.nbt.FloatTag(Math.floorMod((int) yaw, 360)),
                    new io.blockdesigner.core.nbt.FloatTag(0)));
        }
        Layer l = ws.activeLayerProperty().get();
        if (l == null) l = newLayer();
        if (l.locked()) {
            showToast("Active layer is locked");
            return;
        }
        var local = io.blockdesigner.core.edit.SceneEditor.toLocal(l, l.transform().inverse(), new io.blockdesigner.core.model.StructureEntity(x, y, z, nbt));
        if (ws.settings().blockSounds) sounds.place(ws.settings().soundVolume);
        ws.editor().editEntities(l, "Place " + kind.name(), key, list -> list.add(local));
    }

    /** Left-click in Build mode (or the menu): removes the entity. */
    private void removeEntity(EntityHit h, String key) {
        Layer l = h.layer();
        if (l.locked()) {
            showToast("Layer is locked");
            return;
        }
        var w = worldEntity(l, h.entity());
        if (ws.settings().blockSounds) sounds.breakBlock(ws.settings().soundVolume);
        if (ws.settings().breakParticles) {
            particles.burst((int) Math.floor(w.x()), (int) Math.floor(w.y()), (int) Math.floor(w.z()), null);
            particlesDrawn = true;
        }
        ws.editor().editEntities(l, "Remove " + io.blockdesigner.core.model.EntityTypes.displayName(h.entity()), key, list -> list.remove(h.entity()));
        updateHover(aimX(), aimY());
    }

    /**
     * Alt+scroll over an entity: a mob or armour stand turns 22.5° (clockwise from above when scrolling up); a painting
     * shows the next picture that fits the same wall.
     */
    private void turnEntity(EntityHit h, int sign) {
        Layer l = h.layer();
        if (l.locked()) {
            showToast("Layer is locked");
            return;
        }
        var e = h.entity();
        String id = io.blockdesigner.core.model.EntityTypes.kind(e.id()).id();
        io.blockdesigner.core.model.StructureEntity turned;
        String what;
        if (id.endsWith(":painting")) {
            List<String> variants = io.blockdesigner.core.model.EntityTypes.paintingVariants();
            int i = variants.indexOf(io.blockdesigner.core.model.EntityTypes.paintingVariant(e.nbt()));
            String next = variants.get(Math.floorMod(i + sign, variants.size()));
            var nbt = e.nbt().copy();
            nbt.putString("variant", "minecraft:" + next);
            nbt.remove("Motive");
            int facing = io.blockdesigner.core.model.EntityTypes.facing(nbt);
            double[] c = nbt.contains("TileX")
                    ? io.blockdesigner.core.model.EntityTypes.hangingPosition(nbt.getInt("TileX"), nbt.getInt("TileY"), nbt.getInt("TileZ"), facing, nbt)
                    : new double[]{e.x(), e.y(), e.z()};
            turned = new io.blockdesigner.core.model.StructureEntity(c[0], c[1], c[2], nbt);
            int[] size = io.blockdesigner.core.model.EntityTypes.paintingSize(nbt);
            what = "Painting: " + BlockInfoHud.pretty(next) + " (" + size[0] + "×" + size[1] + ")";
        } else if (io.blockdesigner.core.model.EntityTypes.hanging(id)) {
            showToast("Item frames face their wall · aim at another wall to hang one there");
            return;
        } else {
            boolean mirrored = l.transform().mirror() != Transform.Mirror.NONE;
            float yaw = e.yaw() + 22.5f * sign * (mirrored ? -1 : 1);
            yaw = ((yaw % 360) + 360) % 360;
            turned = e.withYaw(yaw);
            what = io.blockdesigner.core.model.EntityTypes.displayName(e) + " faces " + Math.round(worldEntity(l, turned).yaw()) + "°";
        }
        var target = turned;
        ws.editor().editEntities(l, "Turn " + io.blockdesigner.core.model.EntityTypes.displayName(e), "turn-entity-" + l.id(), list -> {
            int i = list.indexOf(e);
            if (i >= 0) list.set(i, target);
        });
        var sel = entitySel.get(l.id());
        if (sel != null && sel.remove(e)) sel.add(target);
        nudgeSeal.playFromStart();
        showToast(what);
        updateHover(aimX(), aimY());
    }

    /** Arrow keys and Ctrl+scroll with entities selected: moves them (half a block with Shift held on the keys). */
    private void moveSelectedEntities(int dx, int dy, int dz) {
        int n = 0;
        for (var en : List.copyOf(entitySel.entrySet())) {
            Layer l = ws.scene().find(en.getKey()).orElse(null);
            if (l == null || l.locked()) continue;
            // World deltas into the layer's frame.
            BlockPos d = l.transform().inverse().apply(dx, dy, dz);
            var moved = new java.util.LinkedHashSet<io.blockdesigner.core.model.StructureEntity>();
            var old = java.util.Set.copyOf(en.getValue());
            ws.editor().editEntities(l, "Move entities", "move-entities", list -> list.replaceAll(e -> {
                if (!old.contains(e)) return e;
                var m = e.translated(d.x(), d.y(), d.z());
                moved.add(m);
                return m;
            }));
            en.getValue().clear();
            en.getValue().addAll(moved);
            n += moved.size();
        }
        burstDelta[0] += dx;
        burstDelta[1] += dy;
        burstDelta[2] += dz;
        nudgeSeal.playFromStart();
        showToast(n == 0 ? "The selected entities are in a locked layer"
                : "Δ " + fmt(burstDelta[0]) + ", " + fmt(burstDelta[1]) + ", " + fmt(burstDelta[2]));
        requestRedraw();
    }

    // ---- chests and shulker boxes opening (View mode) ----------------------------------------------------------

    /** A click in View mode on a chest or shulker box opens it, or shuts it again, with the game's lid animation. */
    private void toggleLid() {
        if (hover == null || sceneRenderer == null) return;
        Layer l = hover.layer();
        BlockState st = l.structure().get(hover.local());
        if (!BlockAssets.opens(st)) return;
        java.util.List<BlockPos> cells = new java.util.ArrayList<>(List.of(hover.local()));
        // A double chest opens both halves together.
        String type = st.get("type");
        if (("left".equals(type) || "right".equals(type)) && st.get("facing") != null) {
            BlockPlacement.Dir f = BlockPlacement.Dir.parse(st.get("facing"));
            if (f != null) {
                BlockPos other = ("left".equals(type) ? f.clockWise() : f.counterClockWise()).offset(hover.local());
                if (l.structure().get(other).name().equals(st.name())) cells.add(other);
            }
        }
        boolean open = sceneRenderer.openAmount(l, hover.local()) < 0.5f && !isOpening(l, hover.local());
        for (BlockPos c : cells) {
            LidAnim a = lids.computeIfAbsent(l.id() + "|" + c.pack(), k -> new LidAnim(l, c));
            a.amount = sceneRenderer.openAmount(l, c);
            a.opening = open;
        }
        if (ws.settings().blockSounds) sounds.place(ws.settings().soundVolume * 0.6);
    }

    private boolean isOpening(Layer l, BlockPos local) {
        LidAnim a = lids.get(l.id() + "|" + local.pack());
        return a != null && a.opening;
    }

    /** Moves opening and closing lids along (half a second end to end, like Minecraft's). */
    private void lidStep(double dt) {
        if (lids.isEmpty() || sceneRenderer == null) return;
        for (var it = lids.values().iterator(); it.hasNext(); ) {
            LidAnim a = it.next();
            a.amount = (float) Math.clamp(a.amount + (a.opening ? dt : -dt) * 2, 0, 1);
            sceneRenderer.setOpen(a.layer, a.local, a.amount);
            if (a.opening ? a.amount >= 1 : a.amount <= 0) it.remove();
        }
        requestRedraw();
    }

    // ---- saving the selection with the project --------------------------------------------------------------------

    /** Project file entry holding the block selection, the selected entities and WorldEdit's region. */
    public static final String SELECTION_ENTRY = "selection.json";

    /** The selection and region as a project extra (see {@link io.blockdesigner.core.project.ProjectFile}). */
    public java.util.Map<String, byte[]> selectionExtras() {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = json.createObjectNode();
        root.put("format", 1);
        if (worldEdit.pos1() != null) root.putArray("pos1").add(worldEdit.pos1().x()).add(worldEdit.pos1().y()).add(worldEdit.pos1().z());
        if (worldEdit.pos2() != null) root.putArray("pos2").add(worldEdit.pos2().x()).add(worldEdit.pos2().y()).add(worldEdit.pos2().z());
        var blocks = root.putObject("blocks");
        blockSel.forEach((id, set) -> {
            var arr = blocks.putArray(id);
            for (long packed : set) arr.add(packed);
        });
        var ents = root.putObject("entities");
        entitySel.forEach((id, set) -> ws.scene().find(id).ifPresent(l -> {
            var arr = ents.putArray(id);
            var list = l.structure().entities();
            for (var e : set) {
                int i = list.indexOf(e);
                if (i >= 0) arr.add(i);
            }
        }));
        if (worldEdit.pos1() == null && worldEdit.pos2() == null && blockSel.isEmpty() && entitySel.isEmpty()) return java.util.Map.of();
        try {
            return java.util.Map.of(SELECTION_ENTRY, json.writeValueAsBytes(root));
        } catch (java.io.IOException e) {
            return java.util.Map.of();
        }
    }

    /** Restores what {@link #selectionExtras()} saved (after the project's layers are in the scene). */
    public void loadSelectionExtras(java.util.Map<String, byte[]> extras) {
        blockSel.clear();
        entitySel.clear();
        worldEdit.clear();
        byte[] data = extras.get(SELECTION_ENTRY);
        if (data == null) {
            requestRedraw();
            return;
        }
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(data);
            var p1 = root.path("pos1");
            if (p1.size() == 3) worldEdit.setPos1(new BlockPos(p1.get(0).asInt(), p1.get(1).asInt(), p1.get(2).asInt()));
            var p2 = root.path("pos2");
            if (p2.size() == 3) worldEdit.setPos2(new BlockPos(p2.get(0).asInt(), p2.get(1).asInt(), p2.get(2).asInt()));
            root.path("blocks").properties().forEach(en -> ws.scene().find(en.getKey()).ifPresent(l -> {
                java.util.Set<Long> set = new java.util.HashSet<>();
                for (var v : en.getValue()) {
                    long packed = v.asLong();
                    if (!l.structure().get(BlockPos.unpack(packed)).isAir()) set.add(packed);
                }
                if (!set.isEmpty()) blockSel.put(l.id(), set);
            }));
            root.path("entities").properties().forEach(en -> ws.scene().find(en.getKey()).ifPresent(l -> {
                var list = l.structure().entities();
                var set = new java.util.LinkedHashSet<io.blockdesigner.core.model.StructureEntity>();
                for (var v : en.getValue()) {
                    int i = v.asInt(-1);
                    if (i >= 0 && i < list.size()) set.add(list.get(i));
                }
                if (!set.isEmpty()) entitySel.put(l.id(), set);
            }));
        } catch (java.io.IOException | RuntimeException e) {
            // An unreadable selection just isn't restored.
        }
        requestRedraw();
    }

    /** Click in Select mode: plain replaces the block selection, Shift adds, Ctrl toggles. Empty space clears. */
    private void clickSelect(boolean add, boolean toggle) {
        if (hover == null) {
            if (!add && !toggle) clearBlockSelection();
            return;
        }
        Layer l = hover.layer();
        long key = hover.local().pack();
        if (!add && !toggle) blockSel.clear();
        java.util.Set<Long> set = blockSel.computeIfAbsent(l.id(), k -> new java.util.HashSet<>());
        if (toggle && set.contains(key)) set.remove(key);
        else set.add(key);
        if (set.isEmpty()) blockSel.remove(l.id());
        if (add || toggle) {
            if (!ws.selectedLayers().contains(l)) ws.selectedLayers().add(l);
            ws.scene().setActive(l);
        } else {
            selectOnly(l);
        }
        regionFromSelection();
        selectionChanged();
    }

    /**
     * After a drag selection (or Shift / Ctrl adding or removing blocks), the box around the selected blocks becomes
     * the WorldEdit region: pos1 its low corner, pos2 its high one. The selection itself stays as it is.
     */
    private void regionFromSelection() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (var en : blockSel.entrySet()) {
            Layer l = ws.scene().find(en.getKey()).orElse(null);
            if (l == null) continue;
            for (long packed : en.getValue()) {
                BlockPos p = l.toWorld(BlockPos.unpack(packed));
                x0 = Math.min(x0, p.x());
                y0 = Math.min(y0, p.y());
                z0 = Math.min(z0, p.z());
                x1 = Math.max(x1, p.x());
                y1 = Math.max(y1, p.y());
                z1 = Math.max(z1, p.z());
            }
        }
        if (x0 > x1) {
            worldEdit.clear();
            return;
        }
        worldEdit.setPos1(new BlockPos(x0, y0, z0));
        worldEdit.setPos2(new BlockPos(x1, y1, z1));
    }

    /**
     * Selects every block of the visible, unlocked layers whose centre falls inside the screen rectangle (x-ray: hidden
     * blocks behind others count too). Shift adds, Ctrl removes; blocks hidden by the slice view are skipped.
     */
    private void marqueeSelect(double x0, double y0, double x1, double y1, boolean add, boolean subtract) {
        if (!add && !subtract) {
            blockSel.clear();
            entitySel.clear();
        }
        marqueeEntities(x0, y0, x1, y1, subtract);
        float w = (float) getWidth(), h = (float) getHeight();
        org.joml.Matrix4f vp = camera.viewProjection(w / h);
        // Screen rectangle in normalised device coordinates (y up).
        float nx0 = (float) (x0 / w * 2 - 1), nx1 = (float) (x1 / w * 2 - 1);
        float ny0 = (float) (1 - y1 / h * 2), ny1 = (float) (1 - y0 / h * 2);
        int lo = sliceMin(), hi = sliceMax();
        List<Layer> touched = new ArrayList<>();
        for (Layer l : ws.scene().layers()) {
            if (!l.visible() || l.locked() || placing.contains(l)) continue;
            org.joml.Matrix4f m = new org.joml.Matrix4f(vp).mul(new org.joml.Matrix4f().set(SceneRenderer.modelMatrix(l.offset(), l.transform())));
            int base = l.offset().y();
            java.util.Set<Long> set = blockSel.computeIfAbsent(l.id(), k -> new java.util.HashSet<>());
            int before = set.size();
            boolean[] hit = {false};
            org.joml.Vector4f v = new org.joml.Vector4f();
            l.structure().forEachBlock((x, y, z, st) -> {
                if (y + base < lo || y + base > hi) return;
                v.set(x + 0.5f, y + 0.5f, z + 0.5f, 1).mul(m);
                if (v.w <= 0) return;
                float sx = v.x / v.w, sy = v.y / v.w;
                if (sx < nx0 || sx > nx1 || sy < ny0 || sy > ny1) return;
                long key = BlockPos.pack(x, y, z);
                if (subtract) set.remove(key);
                else set.add(key);
                hit[0] = true;
            });
            if (set.isEmpty()) blockSel.remove(l.id());
            if (hit[0] && !subtract && set.size() != before) touched.add(l);
        }
        if (!touched.isEmpty()) {
            if (add) {
                for (Layer l : touched) if (!ws.selectedLayers().contains(l)) ws.selectedLayers().add(l);
            } else {
                ws.selectedLayers().setAll(touched);
            }
            ws.scene().setActive(touched.getLast());
        }
        regionFromSelection();
        selectionChanged();
    }

    /** Entities whose middle falls inside the screen rectangle join the selection (or leave it, with {@code subtract}). */
    private void marqueeEntities(double x0, double y0, double x1, double y1, boolean subtract) {
        float w = (float) getWidth(), h = (float) getHeight();
        org.joml.Matrix4f vp = camera.viewProjection(w / h);
        int lo = sliceMin(), hi = sliceMax();
        org.joml.Vector4f v = new org.joml.Vector4f();
        for (Layer l : ws.scene().layers()) {
            if (!l.visible() || l.locked() || placing.contains(l) || l.structure().entities().isEmpty()) continue;
            var set = entitySel.computeIfAbsent(l.id(), k -> new java.util.LinkedHashSet<>());
            for (var e : l.structure().entities()) {
                var we = worldEntity(l, e);
                if (we.y() < lo || we.y() >= (long) hi + 1) continue;
                double[] b = io.blockdesigner.core.model.EntityTypes.box(we);
                v.set((float) (b[0] + b[3]) / 2, (float) (b[1] + b[4]) / 2, (float) (b[2] + b[5]) / 2, 1).mul(vp);
                if (v.w <= 0) continue;
                double sx = (v.x / v.w + 1) / 2 * w, sy = (1 - v.y / v.w) / 2 * h;
                if (sx < x0 || sx > x1 || sy < y0 || sy > y1) continue;
                if (subtract) set.remove(e);
                else set.add(e);
            }
            if (set.isEmpty()) entitySel.remove(l.id());
        }
    }

    // ---- WorldEdit region and commands ---------------------------------------------------------------------------

    /** Left-click (pos1) / right-click (pos2) in Select mode: the aimed block, or the ground cell under the cursor. */
    private void setCorner(boolean first) {
        BlockPos p = hover != null ? hover.world() : hoverGround != null ? hoverGround : pickGround(aimX(), aimY(), groundY()).orElse(null);
        if (p == null) {
            showToast("Aim at a block or the ground to set " + (first ? "pos1" : "pos2"));
            return;
        }
        if (first) worldEdit.setPos1(p);
        else worldEdit.setPos2(p);
        Box r = worldEdit.region();
        regionChanged();
        boolean both = worldEdit.pos1() != null && worldEdit.pos2() != null;
        showToast((first ? "pos1 " : "pos2 ") + p + (both ? String.format(" · %d×%d×%d = %,d blocks", r.sizeX(), r.sizeY(), r.sizeZ(), r.volume()) + keyNote(Keybinds.Action.COMMAND_BAR, "for commands")
                : first ? " · right-click pos2" : " · left-click pos1"));
    }

    /** The region's blocks (in visible, unlocked layers) become the block selection, so Delete, F, T and the menu act on it. */
    private void regionChanged() {
        Box r = worldEdit.region();
        blockSel.clear();
        if (r != null) {
            List<Layer> touched = new ArrayList<>();
            for (Layer l : ws.scene().layers()) {
                if (!l.visible() || l.locked() || placing.contains(l)) continue;
                java.util.Set<Long> set = new java.util.HashSet<>();
                l.structure().forEachBlock((x, y, z, st) -> {
                    BlockPos w = l.toWorld(x, y, z);
                    if (r.contains(w.x(), w.y(), w.z())) set.add(BlockPos.pack(x, y, z));
                });
                if (!set.isEmpty()) {
                    blockSel.put(l.id(), set);
                    touched.add(l);
                }
            }
            if (!touched.isEmpty() && touched.stream().noneMatch(l -> l == ws.activeLayerProperty().get())) {
                ws.scene().setActive(touched.getLast());
            }
        }
        selectionListener.run();
        requestRedraw();
    }

    private void clearRegionAndSelection() {
        worldEdit.clear();
        blockSel.clear();
        entitySel.clear();
        showToast("Selection cleared");
        requestRedraw();
    }

    /** The region box, with pos1 in red and pos2 in blue (like WorldEdit CUI). */
    private void drawRegion(List<FrameRequest.Line> lines) {
        Box r = worldEdit.region();
        if (r == null) return;
        float e = 0.03f;
        Overlays.box(lines, r.minX() - e, r.minY() - e, r.minZ() - e, r.maxX() + 1 + e, r.maxY() + 1 + e, r.maxZ() + 1 + e, 0xFFFFC85A);
        BlockPos a = worldEdit.pos1(), b = worldEdit.pos2();
        if (a != null) Overlays.box(lines, a.x() - 0.06f, a.y() - 0.06f, a.z() - 0.06f, a.x() + 1.06f, a.y() + 1.06f, a.z() + 1.06f, 0xFFE5484D);
        if (b != null) Overlays.box(lines, b.x() - 0.06f, b.y() - 0.06f, b.z() - 0.06f, b.x() + 1.06f, b.y() + 1.06f, b.z() + 1.06f, 0xFF3E9BFF);
    }

    /** Block ids for completing command patterns (vanilla ones without "minecraft:"). */
    private List<String> blockIds() {
        BlockAssets a = ws.assets();
        return a == null ? List.of() : a.registry().all().stream().map(i -> i.id().startsWith("minecraft:") ? i.path() : i.id()).toList();
    }

    /** T, "/" or the terminal button: the WorldEdit command bar, like Minecraft's chat. */
    public void openCommandBar() {
        if (fly) setFly(false);
        commandBar.open();
    }

    /**
     * Runs a WorldEdit command (built-in or from a plugin) as one undo step, through {@link #editWorld}.
     */
    private void runCommand(String line) {
        BlockAssets assets = ws.assets();
        java.util.function.Function<String, BlockState> resolve = name -> {
            try {
                BlockState st = BlockState.parse(name.contains(":") ? name : "minecraft:" + name);
                if (assets == null) return st;
                if (assets.registry().get(st.name()).isEmpty()) return null;
                return assets.registry().complete(st);
            } catch (RuntimeException ex) {
                return null;
            }
        };
        Vector3f f = camera.forward();
        float ax = Math.abs(f.x), ay = Math.abs(f.y), az = Math.abs(f.z);
        BlockPlacement.Dir look = ay > ax && ay > az ? (f.y > 0 ? BlockPlacement.Dir.UP : BlockPlacement.Dir.DOWN)
                : ax > az ? (f.x > 0 ? BlockPlacement.Dir.EAST : BlockPlacement.Dir.WEST) : (f.z > 0 ? BlockPlacement.Dir.SOUTH : BlockPlacement.Dir.NORTH);
        BlockPos aim = hover != null ? hover.world() : hoverGround;
        io.blockdesigner.core.worldedit.WorldEdit.Result[] result = {null};
        boolean ran = editWorld(line.strip(), world -> result[0] = worldEdit.run(line,
                new io.blockdesigner.core.worldedit.WorldEdit.Context(world, look, aim, ws.selectedBlockProperty().get(), resolve)));
        if (!ran || result[0] == null) return;
        var r = result[0];
        var undo = ws.editor().undoStack();
        switch (r.special()) {
            case UNDO -> undo.undo();
            case REDO -> undo.redo();
            default -> {
            }
        }
        if (r.region() || r.changed() > 0 || r.special() != io.blockdesigner.core.worldedit.WorldEdit.Special.NONE) regionChanged();
        showToast((r.ok() ? "" : "✖ ") + r.message());
        commandBar.showResult(r.ok(), r.message());
    }

    /**
     * Edits blocks in world coordinates as one undo step named {@code label}. The edit sees every visible, unlocked
     * layer merged, like the view does. Changing a cell changes it in the layer that holds a block there (and clears it
     * from any other layer), so /set really replaces what was there; empty cells are filled in the active layer (a new
     * one when there is none). Rotated layers work as expected. Returns false when the active layer is locked.
     */
    public boolean editWorld(String label, java.util.function.Consumer<io.blockdesigner.core.worldedit.WorldEdit.World> edit) {
        Layer active = ws.activeLayerProperty().get();
        if (active != null && active.locked()) {
            showToast("✖ The active layer is locked");
            return false;
        }
        editLayers(label, null, active, le -> {
            edit.accept(le);
            // Fences, walls, panes, redstone, rails and stairs join up with what was built, as when placing by hand.
            le.reconnect();
        });
        return true;
    }

    // ---- plugin tools ---------------------------------------------------------------------------------------------

    /** A plugin tool is the active tool and nothing is being placed: mouse input goes to it. */
    private boolean pluginToolActive() {
        return pluginTool != null && ws.toolProperty().get() == ToolKind.PLUGIN && placing.isEmpty();
    }

    /**
     * Makes a plugin tool the viewport's tool (the caller then sets the PLUGIN tool mode); null puts the current one
     * down. Returns false when the plugin failed to activate it.
     */
    public boolean setPluginTool(io.blockdesigner.app.plugins.PluginManager.Tool tool, io.blockdesigner.app.plugins.PluginManager plugins) {
        if (pluginTool != null) {
            PluginToolSession old = pluginTool;
            pluginTool = null;
            old.deactivate();
        }
        pluginToolBar.getChildren().clear();
        if (tool == null) {
            updateHotbarVisibility();
            return true;
        }
        PluginToolSession s = new PluginToolSession(ws, plugins, tool, new PluginToolSession.Viewport() {
            @Override
            public void showPreview(java.util.Map<BlockPos, BlockState> ghosts, List<BlockPos> removed, List<Box> outlines) {
                ViewportPane.this.showPreview(ghosts, removed, outlines);
            }

            @Override
            public LayeredEdit newEdit(String label) {
                Layer active = ws.activeLayerProperty().get();
                return new LayeredEdit(ws, worldLayers(), active != null && !active.locked() ? active : null, ViewportPane.this::newLayer, label, null);
            }
        });
        if (!s.activate()) {
            updateHotbarVisibility();
            return false;
        }
        pluginTool = s;
        var opts = tool.tool().options();
        if (!opts.isEmpty()) {
            Label title = new Label(tool.tool().name());
            title.getStyleClass().add("brush-title");
            OptionsEditor editor = new OptionsEditor(s.options(), plugins.blocks(), () -> ws.selectedBlockProperty().get(), s::setOptions);
            editor.setPrefWidth(320);
            pluginToolBar.getChildren().addAll(title, editor);
        }
        updateHotbarVisibility();
        if (ws.toolProperty().get() == ToolKind.PLUGIN) showToast(pluginToolToast());
        requestFocus();
        return true;
    }

    /** The active plugin tool, if one is picked. */
    public Optional<io.blockdesigner.app.plugins.PluginManager.Tool> pluginTool() {
        return Optional.ofNullable(pluginTool).map(PluginToolSession::tool);
    }

    private String pluginToolToast() {
        if (pluginTool == null) return "Plugin tool";
        var t = pluginTool.tool().tool();
        return t.name() + (t.description().isBlank() ? "" : " · " + t.description());
    }

    /** Offers a key press to the active plugin tool; true when it used it. */
    boolean pluginToolKey(KeyEvent e) {
        if (!pluginToolActive() || e.getCode().isModifierKey()) return false;
        String name = switch (e.getCode()) {
            case ESCAPE -> "Esc";
            case ENTER -> "Enter";
            default -> e.getCode().getName();
        };
        String key = (e.isShortcutDown() ? "Ctrl+" : "") + (e.isAltDown() ? "Alt+" : "") + (e.isShiftDown() ? "Shift+" : "") + name;
        return pluginTool.key(key);
    }

    /** What the mouse points at, for a plugin tool. */
    private io.blockdesigner.plugin.ToolEvent toolEvent(double x, double y, MouseButton button, boolean shift, boolean ctrl, boolean alt) {
        Optional<io.blockdesigner.plugin.ToolEvent.Hit> hit = Optional.empty();
        if (hover != null) {
            hit = Optional.of(new io.blockdesigner.plugin.ToolEvent.Hit(hover.world(), dirOf(hover.normal()), hover.adjacentWorld(), Optional.of(hover.layer())));
        } else if (hoverGround != null) {
            hit = Optional.of(new io.blockdesigner.plugin.ToolEvent.Hit(hoverGround.add(0, -1, 0), BlockPlacement.Dir.UP, hoverGround, Optional.empty()));
        }
        java.util.EnumSet<io.blockdesigner.plugin.ToolEvent.Modifier> mods = java.util.EnumSet.noneOf(io.blockdesigner.plugin.ToolEvent.Modifier.class);
        if (shift) mods.add(io.blockdesigner.plugin.ToolEvent.Modifier.SHIFT);
        if (ctrl) mods.add(io.blockdesigner.plugin.ToolEvent.Modifier.CTRL);
        if (alt) mods.add(io.blockdesigner.plugin.ToolEvent.Modifier.ALT);
        var b = switch (button) {
            case PRIMARY -> io.blockdesigner.plugin.ToolEvent.Button.PRIMARY;
            case SECONDARY -> io.blockdesigner.plugin.ToolEvent.Button.SECONDARY;
            case MIDDLE -> io.blockdesigner.plugin.ToolEvent.Button.MIDDLE;
            default -> io.blockdesigner.plugin.ToolEvent.Button.NONE;
        };
        Vector3f[] r = ray(x, y);
        return new io.blockdesigner.plugin.ToolEvent(hit, b, mods, new io.blockdesigner.plugin.ToolEvent.Vec3(r[0].x, r[0].y, r[0].z),
                new io.blockdesigner.plugin.ToolEvent.Vec3(r[1].x, r[1].y, r[1].z));
    }

    private static BlockPlacement.Dir dirOf(BlockPos normal) {
        for (BlockPlacement.Dir d : BlockPlacement.Dir.values()) {
            if (d.x == normal.x() && d.y == normal.y() && d.z == normal.z()) return d;
        }
        return BlockPlacement.Dir.UP;
    }

    // ---- plugin transforms and previews ------------------------------------------------------------------------

    /** Tells {@code listener} when the block selection or the //pos1 //pos2 region may have changed. */
    public void onSelectionChanged(Runnable listener) {
        this.selectionListener = listener;
    }

    /**
     * Catches selection changes made without going through {@link #selectionChanged()} (there are many paths that
     * edit the selection): a change in the count or the region is enough to notice nearly all of them.
     */
    private void checkSelection() {
        int n = selectedBlockCount();
        Box r = worldEdit.region();
        if (n != lastSelCount || !java.util.Objects.equals(r, lastSelRegion)) {
            lastSelCount = n;
            lastSelRegion = r;
            selectionListener.run();
        }
    }

    /** World box around the selected blocks, or the //pos region when none are selected; empty when neither exists. */
    public Optional<Box> selectionBounds() {
        Box b = null;
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            if (l == null) continue;
            for (long packed : e.getValue()) {
                BlockPos w = l.toWorld(BlockPos.unpack(packed));
                Box one = new Box(w.x(), w.y(), w.z(), w.x(), w.y(), w.z());
                b = b == null ? one : b.union(one);
            }
        }
        return Optional.ofNullable(b != null ? b : worldEdit.region());
    }

    public void setTransformItems(java.util.function.Supplier<List<javafx.scene.control.MenuItem>> items) {
        this.transformItems = items;
    }

    /** The blocks a plugin transform of this scope works on now, or null when there are none. */
    public io.blockdesigner.app.plugins.TransformRunner.Target transformTarget(io.blockdesigner.plugin.PluginTransform.Scope scope) {
        return TransformTargets.resolve(ws, scope, blockSel, worldEdit.region(), worldLayers(), ws.activeLayerProperty().get());
    }

    /** Why {@link #transformTarget} found nothing, for a toast. */
    public String transformTargetMissing(io.blockdesigner.plugin.PluginTransform.Scope scope) {
        return TransformTargets.nothingMessage(scope, ws.activeLayerProperty().get());
    }

    /** The blocks as a transform on {@code target} reads them (nothing written through it is kept). */
    public io.blockdesigner.core.worldedit.WorldEdit.World transformWorld(io.blockdesigner.app.plugins.TransformRunner.Target target) {
        return TransformTargets.readWorld(ws, target, worldLayers());
    }

    /** Writes a transform's changes as one undo step; returns how many cells changed. */
    public int applyTransform(String label, io.blockdesigner.app.plugins.TransformRunner.Target target,
                              io.blockdesigner.app.plugins.TransformRunner.Changes changes) {
        int n = TransformTargets.apply(ws, target, changes, label, worldLayers(), ws.activeLayerProperty().get());
        requestRedraw();
        return n;
    }

    /**
     * Shows ghost blocks (world positions) over the scene, red outlines around {@code removed} cells and white ones
     * around {@code outlines}, replacing any earlier preview.
     */
    public void showPreview(java.util.Map<BlockPos, BlockState> ghosts, List<BlockPos> removed, List<Box> outlines) {
        if (previewLayer != null) previewScene.remove(previewLayer);
        previewLayer = null;
        if (!ghosts.isEmpty()) {
            io.blockdesigner.core.model.Structure s = new io.blockdesigner.core.model.Structure();
            ghosts.forEach(s::set);
            previewLayer = new Layer("Preview", s);
            previewLayer.setGhost(true);
            previewScene.add(previewLayer);
        }
        previewRemoved = List.copyOf(removed);
        previewOutlines = List.copyOf(outlines);
        requestRedraw();
    }

    public void clearPreview() {
        showPreview(java.util.Map.of(), List.of(), List.of());
    }

    private void drawPluginPreview(List<FrameRequest.Line> lines) {
        float e = 0.03f;
        for (Box b : previewOutlines) {
            Overlays.box(lines, b.minX() - e, b.minY() - e, b.minZ() - e, b.maxX() + 1 + e, b.maxY() + 1 + e, b.maxZ() + 1 + e, 0xE6FFFFFF);
        }
        if (previewRemoved.size() <= SEL_OUTLINE_LIMIT) {
            for (BlockPos p : previewRemoved) Overlays.block(lines, p.x(), p.y(), p.z(), 0xFFE5484D);
        } else {
            Box b = io.blockdesigner.core.place.ShapeTool.bounds(previewRemoved);
            if (b != null) Overlays.box(lines, b.minX() - e, b.minY() - e, b.minZ() - e, b.maxX() + 1 + e, b.maxY() + 1 + e, b.maxZ() + 1 + e, 0xFFE5484D);
        }
    }

    // ---- build shapes ---------------------------------------------------------------------------------------

    // ---- symmetry ---------------------------------------------------------------------------------------------

    /** The active symmetry: only in Build mode, and only when switched on. */
    private io.blockdesigner.core.place.Symmetry symmetry() {
        var s = ws.settings();
        if (!s.symOn || ws.toolProperty().get() != ToolKind.BUILD) return io.blockdesigner.core.place.Symmetry.OFF;
        int[] c = s.symCenter == null || s.symCenter.length < 3 ? new int[]{1, 129, 1} : s.symCenter;
        return new io.blockdesigner.core.place.Symmetry(s.symX, s.symY, s.symZ, s.symRadial, c[0], c[1], c[2], s.symFlip);
    }

    private void showSymmetry(double screenX, double screenY) {
        BlockPos aimed = hover != null ? hover.world() : hoverGround;
        if (!ws.settings().symOn && aimed != null) {
            // Starting fresh: centre on what you're looking at, so the planes appear where you build.
            boolean blockCentre = ws.settings().symCenter == null || Math.floorMod(ws.settings().symCenter[0], 2) == 1;
            int off = blockCentre ? 1 : 0;
            ws.settings().symCenter = new int[]{2 * aimed.x() + off, 2 * aimed.y() + off, 2 * aimed.z() + off};
        }
        symmetryPopup.sync();
        if (keys != null) symmetryPopup.setKeys(this::keyText);
        if (screenX < 0) {
            javafx.geometry.Point2D p = localToScreen(getWidth() / 2 - 160, getHeight() / 2 - 180);
            if (p == null) return;
            screenX = p.getX();
            screenY = p.getY();
        }
        symmetryPopup.show(getScene().getWindow(), screenX, screenY);
    }

    /** Shift+M: the symmetry centre moves to the aimed block (keeping block-centre or block-edge), and switches on. */
    private void centreSymmetryHere() {
        BlockPos p = hover != null ? hover.world() : hoverGround;
        if (p == null) {
            showToast("Aim at a block to put the symmetry centre there");
            return;
        }
        var s = ws.settings();
        boolean blockCentre = s.symCenter == null || Math.floorMod(s.symCenter[0], 2) == 1;
        int off = blockCentre ? 1 : 0;
        s.symCenter = new int[]{2 * p.x() + off, 2 * p.y() + off, 2 * p.z() + off};
        s.symOn = true;
        if (!s.symX && !s.symY && !s.symZ && s.symRadial <= 1) s.symX = true;
        symmetryPopup.sync();
        updateSymmetryButton();
        showToast("Symmetry centre: " + p.x() + ", " + p.y() + ", " + p.z());
        requestRedraw();
    }

    private void updateSymmetryButton() {
        symmetryButton.getStyleClass().remove("active");
        if (ws.settings().symOn) symmetryButton.getStyleClass().add("active");
    }

    /** Mirror planes (X red, Y green, Z blue), radial spokes, and ghost outlines where the aimed block's copies go. */
    private void drawSymmetry(List<FrameRequest.Line> lines) {
        var sym = symmetry();
        if (!sym.active()) return;
        float cx = (float) sym.centerX(), cy = (float) sym.centerY(), cz = (float) sym.centerZ();
        if (ws.settings().symShowPlanes) {
            float r = 20;
            if (sym.mirrorX()) planeX(lines, cx, cy, cz, r, 0xAAE5484D);
            if (sym.mirrorZ()) planeZ(lines, cx, cy, cz, r, 0xAA3E9BFF);
            if (sym.mirrorY()) {
                for (float t = -r; t <= r + 0.01f; t += r / 4) {
                    lines.add(new FrameRequest.Line(cx - r, cy, cz + t, cx + r, cy, cz + t, 0x8846C46E));
                    lines.add(new FrameRequest.Line(cx + t, cy, cz - r, cx + t, cy, cz + r, 0x8846C46E));
                }
            }
            if (sym.radial() > 1) {
                float y = cy + 0.02f;
                for (int k = 0; k < sym.radial(); k++) {
                    double a = 2 * Math.PI * k / sym.radial();
                    lines.add(new FrameRequest.Line(cx, y, cz, cx + (float) Math.cos(a) * r, y, cz + (float) Math.sin(a) * r, 0xCCFFC85A));
                }
                // The axis the copies turn about.
                lines.add(new FrameRequest.Line(cx, cy - 6, cz, cx, cy + 12, cz, 0xCCFFC85A));
            }
        }
        // Where the next block (and its copies) will go.
        if (shapeDrag == null && placing.isEmpty()) {
            BlockPos target = ws.replaceProperty().get() ? (hover != null ? hover.world() : null) : hover != null ? hover.adjacentWorld() : hoverGround;
            if (target != null) {
                boolean first = true;
                for (BlockPos q : sym.apply(target, null).keySet()) {
                    if (!first) Overlays.block(lines, q.x(), q.y(), q.z(), 0x88FFFFFF);
                    first = false;
                }
            }
        }
    }

    private static void planeX(List<FrameRequest.Line> lines, float x, float cy, float cz, float r, int argb) {
        for (float t = -r; t <= r + 0.01f; t += r / 4) {
            lines.add(new FrameRequest.Line(x, cy - r, cz + t, x, cy + r, cz + t, argb));
            lines.add(new FrameRequest.Line(x, cy + t, cz - r, x, cy + t, cz + r, argb));
        }
    }

    private static void planeZ(List<FrameRequest.Line> lines, float cx, float cy, float z, float r, int argb) {
        for (float t = -r; t <= r + 0.01f; t += r / 4) {
            lines.add(new FrameRequest.Line(cx + t, cy - r, z, cx + t, cy + r, z, argb));
            lines.add(new FrameRequest.Line(cx - r, cy + t, z, cx + r, cy + t, z, argb));
        }
    }

    private io.blockdesigner.core.place.ShapeTool.Shape shape() {
        try {
            return io.blockdesigner.core.place.ShapeTool.Shape.valueOf(ws.settings().buildShape);
        } catch (RuntimeException e) {
            return io.blockdesigner.core.place.ShapeTool.Shape.SINGLE;
        }
    }

    private double lastMouseX, lastMouseY;

    private static boolean isInside(javafx.scene.Node n, javafx.scene.Node ancestor) {
        for (javafx.scene.Node p = n; p != null; p = p.getParent()) if (p == ancestor) return true;
        return false;
    }

    private void openShapeRadial() {
        if (!altAlone || ws.toolProperty().get() != ToolKind.BUILD || shapeDrag != null) return;
        if (fly) shapeRadial.open(getWidth() / 2, getHeight() / 2, shape());
        else shapeRadial.open(lastMouseX, lastMouseY, shape());
    }

    private void cancelShapeRadial() {
        radialDelay.stop();
        altAlone = false;
        shapeRadial.close();
    }

    /** Picked on the wheel (or clicked): later right-clicks place this shape; SINGLE is normal placing. */
    public void pickShape(io.blockdesigner.core.place.ShapeTool.Shape s) {
        shapeRadial.close();
        ws.settings().buildShape = s.name();
        if (ws.toolProperty().get() != ToolKind.BUILD) ws.toolProperty().set(ToolKind.BUILD);
        showToast(s == io.blockdesigner.core.place.ShapeTool.Shape.SINGLE ? "No shape · right-click places single blocks"
                : "Shape: " + s.label + " · right-drag to place · " + s.description);
        requestRedraw();
    }

    /** Starts a shape at the aimed cell; returns false when no shape is chosen (single placing) or nothing is aimed at. */
    private boolean beginShape() {
        var s = shape();
        // Shapes are for blocks: holding a mob places just the one.
        if (s == io.blockdesigner.core.place.ShapeTool.Shape.SINGLE || !placing.isEmpty() || ws.heldEntityProperty().get() != null) return false;
        if (ws.blockToPlace() == null) {
            showToast("Empty hand · pick a block (middle-click), choose a hotbar slot or click one in the palette");
            return true;
        }
        boolean replace = ws.replaceProperty().get();
        BlockPos start = replace ? (hover != null ? hover.world() : null) : hover != null ? hover.adjacentWorld() : hoverGround;
        if (start == null) return true;
        // Started on a block, the shape grows out of the aimed face (up, down or sideways); on the ground it grows up.
        BlockPos up = hover != null && hover.normal() != null ? hover.normal() : new BlockPos(0, 1, 0);
        shapeDrag = new ShapeDrag(s, start, up, replace);
        refreshShape();
        return true;
    }

    /** Where the drag ends for the mouse at (x, y): along an axis, on a vertical plane, or on the start's floor. */
    private void updateShapeEnd(double x, double y) {
        if (shapeDrag == null) return;
        BlockPos end = shapeEndAt(x, y);
        if (end != null && !end.equals(shapeDrag.end)) {
            shapeDrag.end = end;
            refreshShape();
        }
    }

    private BlockPos shapeEndAt(double x, double y) {
        Vector3f[] r = ray(x, y);
        Vector3f o = r[0], d = new Vector3f(r[1]).normalize();
        BlockPos a = shapeDrag.anchor;
        float ax = a.x() + 0.5f, ay = a.y() + 0.5f, az = a.z() + 0.5f;
        final int reach = 256;
        return switch (shapeDrag.shape.plane) {
            case NONE -> a;
            case HORIZONTAL -> {
                // On the plane through the start across the face it grew from (the start's floor for upright shapes).
                BlockPos n = shapeDrag.shape.followsFace() ? shapeDrag.up : new BlockPos(0, 1, 0);
                int k = n.x() != 0 ? 0 : n.z() != 0 ? 2 : 1;
                float[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z}, as = {ax, ay, az};
                int[] ai = {a.x(), a.y(), a.z()};
                if (Math.abs(ds[k]) < 1e-4f) yield null;
                float t = (as[k] - os[k]) / ds[k];
                if (t < 0) yield null;
                int[] c = new int[3];
                for (int i = 0; i < 3; i++)
                    c[i] = i == k ? ai[i] : Math.clamp((int) Math.floor(os[i] + ds[i] * t), ai[i] - reach, ai[i] + reach);
                yield new BlockPos(c[0], c[1], c[2]);
            }
            case VERTICAL -> {
                // The wall stands across the view: its plane faces the camera's main horizontal direction.
                Vector3f f = camera.forward();
                boolean facesX = Math.abs(f.x) > Math.abs(f.z);
                float denom = facesX ? d.x : d.z;
                if (Math.abs(denom) < 1e-4f) yield null;
                float t = ((facesX ? ax : az) - (facesX ? o.x : o.z)) / denom;
                if (t < 0) yield null;
                int hy = Math.clamp((int) Math.floor(o.y + d.y * t), a.y() - reach, a.y() + reach);
                yield facesX ? new BlockPos(a.x(), hy, Math.clamp((int) Math.floor(o.z + d.z * t), a.z() - reach, a.z() + reach))
                        : new BlockPos(Math.clamp((int) Math.floor(o.x + d.x * t), a.x() - reach, a.x() + reach), hy, a.z());
            }
            case AXIS -> {
                // The axis through the start that passes closest to the mouse ray wins, like Effortless Building.
                float best = Float.MAX_VALUE;
                BlockPos out = null;
                float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
                for (float[] u : axes) {
                    float b = u[0] * d.x + u[1] * d.y + u[2] * d.z;
                    float wx = ax - o.x, wy = ay - o.y, wz = az - o.z;
                    float dd = u[0] * wx + u[1] * wy + u[2] * wz, e = d.x * wx + d.y * wy + d.z * wz;
                    float denom = 1 - b * b;
                    if (denom < 1e-5f) continue;
                    float s = (b * e - dd) / denom, t = (e - b * dd) / denom;
                    if (t < 0) continue;
                    float px = ax + u[0] * s - (o.x + d.x * t), py = ay + u[1] * s - (o.y + d.y * t), pz = az + u[2] * s - (o.z + d.z * t);
                    float dist = px * px + py * py + pz * pz;
                    if (dist < best) {
                        best = dist;
                        int k = Math.clamp(Math.round(s), -reach, reach);
                        out = new BlockPos(a.x() + (int) u[0] * k, a.y() + (int) u[1] * k, a.z() + (int) u[2] * k);
                    }
                }
                yield out;
            }
        };
    }

    private void refreshShape() {
        ShapeDrag sd = shapeDrag;
        if (sd == null) return;
        long est = io.blockdesigner.core.place.ShapeTool.estimate(sd.shape, sd.anchor, sd.end, sd.height, sd.up);
        sd.tooBig = est > io.blockdesigner.core.place.ShapeTool.MAX_BLOCKS;
        sd.cells = sd.tooBig ? List.of() : io.blockdesigner.core.place.ShapeTool.cells(sd.shape, sd.anchor, sd.end, sd.height, sd.up);
        Box b = io.blockdesigner.core.place.ShapeTool.bounds(sd.cells);
        String size = b == null ? "" : " · " + b.sizeX() + " × " + b.sizeY() + " × " + b.sizeZ();
        String text = sd.tooBig ? sd.shape.label + " · too big (over " + String.format("%,d", io.blockdesigner.core.place.ShapeTool.MAX_BLOCKS) + " blocks)"
                : sd.shape.label + size + " · " + String.format("%,d", sd.cells.size()) + " block" + (sd.cells.size() == 1 ? "" : "s")
                + (sd.shape.usesHeight() ? " · wheel: height " + sd.height : "") + (sd.replace ? " · replacing" : "")
                + " · release to place" + keyNote(Keybinds.Action.CANCEL, "cancels");
        shapeInfo.setText(text);
        shapeInfo.setOpacity(1);
        shapeInfo.setVisible(true);
        requestRedraw();
    }

    private void cancelShape() {
        shapeDrag = null;
        shapeInfo.setVisible(false);
        requestRedraw();
    }

    /** Places the shape with the held block (a random hotbar pick per block in shuffle mode) as one undo step. */
    private void commitShape() {
        ShapeDrag sd = shapeDrag;
        cancelShape();
        if (sd == null || sd.cells.isEmpty()) {
            if (sd != null && sd.tooBig) showToast("✖ That shape is too big to place in one go");
            return;
        }
        List<BlockPos> cells = fly ? sd.cells.stream().filter(p -> !insideCamera(p)).toList() : sd.cells;
        io.blockdesigner.core.place.Symmetry sym = symmetry();
        int[] placed = {0};
        boolean ok = editWorld(sd.shape.label, world -> {
            java.util.Set<BlockPos> done = new java.util.HashSet<>();
            for (BlockPos p : cells) {
                BlockState b = ws.blockToPlace();
                if (b == null) return;
                // Each cell and (with symmetry on) its mirror images, blocks turned to match.
                for (var c : sym.apply(p, b).entrySet()) {
                    BlockPos q = c.getKey();
                    if (!done.add(q) || (fly && insideCamera(q))) continue;
                    if (!sd.replace && !world.get(q).isAir()) continue;
                    world.set(q, c.getValue());
                    placed[0]++;
                }
            }
        });
        if (!ok) return;
        if (placed[0] > 0 && ws.settings().blockSounds) sounds.place(ws.settings().soundVolume);
        showToast(placed[0] == 0 ? "Nothing to place: every cell of the " + sd.shape.label.toLowerCase(java.util.Locale.ROOT) + " is filled"
                : String.format("%s · placed %,d block%s", sd.shape.label, placed[0], placed[0] == 1 ? "" : "s"));
        updateHover(aimX(), aimY());
    }

    /** Outlines each block of the shape (just its box when very large) while dragging. */
    private void drawShapePreview(List<FrameRequest.Line> lines) {
        ShapeDrag sd = shapeDrag;
        if (sd == null) return;
        int color = sd.replace ? 0xFFFFC85A : 0xFF46C46E;
        Box b = io.blockdesigner.core.place.ShapeTool.bounds(sd.cells);
        if (b == null) {
            BlockPos a = sd.anchor;
            Overlays.block(lines, a.x(), a.y(), a.z(), 0xFFE5484D);
            return;
        }
        io.blockdesigner.core.place.Symmetry sym = symmetry();
        int copies = sym.active() ? sym.copies() : 1;
        if (sd.cells.size() * copies <= 1500) {
            for (BlockPos p : sd.cells) {
                boolean first = true;
                for (BlockPos q : sym.apply(p, null).keySet()) {
                    Overlays.block(lines, q.x(), q.y(), q.z(), first ? color : (color & 0xFFFFFF) | 0x99000000);
                    first = false;
                }
            }
        }
        float e = 0.04f;
        Overlays.box(lines, b.minX() - e, b.minY() - e, b.minZ() - e, b.maxX() + 1 + e, b.maxY() + 1 + e, b.maxZ() + 1 + e, 0xFFFFFFFF);
        if (sym.active()) {
            // Outline each mirrored copy's box too.
            java.util.Set<BlockPos> corners = new java.util.LinkedHashSet<>();
            BlockPos lo = new BlockPos(b.minX(), b.minY(), b.minZ()), hi = new BlockPos(b.maxX(), b.maxY(), b.maxZ());
            var los = new java.util.ArrayList<>(sym.apply(lo, null).keySet());
            var his = new java.util.ArrayList<>(sym.apply(hi, null).keySet());
            for (int i = 1; i < Math.min(los.size(), his.size()); i++) {
                Box mb = Box.of(los.get(i), his.get(i));
                if (corners.add(mb.min())) Overlays.box(lines, mb.minX() - e, mb.minY() - e, mb.minZ() - e, mb.maxX() + 1 + e, mb.maxY() + 1 + e, mb.maxZ() + 1 + e, 0x99FFFFFF);
            }
        }
    }

    private void clearBlockSelection() {
        if (blockSel.isEmpty() && entitySel.isEmpty()) return;
        blockSel.clear();
        entitySel.clear();
        selectionChanged();
    }

    private int selectedBlockCount() {
        int n = 0;
        for (java.util.Set<Long> set : blockSel.values()) n += set.size();
        return n;
    }

    private void selectionChanged() {
        selectionListener.run();
        int n = selectedBlockCount(), ne = entitySel.values().stream().mapToInt(java.util.Set::size).sum();
        Box r = worldEdit.region();
        String box = r == null ? "" : String.format(" · pos1 %s, pos2 %s (%d×%d×%d)", worldEdit.pos1(), worldEdit.pos2(), r.sizeX(), r.sizeY(), r.sizeZ());
        String mobs = ne == 0 ? "" : String.format("%d entit%s", ne, ne == 1 ? "y" : "ies");
        String blocks = n == 0 ? "" : String.format("%,d block%s", n, n == 1 ? "" : "s");
        String what = blocks.isEmpty() ? mobs : mobs.isEmpty() ? blocks : blocks + " and " + mobs;
        showToast(what.isEmpty() ? "Selection cleared"
                : what + " selected" + box + (ne > 0 ? keyNote(Keybinds.Action.NUDGE_FORWARD, "/ " + Keybinds.keysOf(Keybinds.Action.NUDGE_BACK, Keybinds.Action.NUDGE_LEFT, Keybinds.Action.NUDGE_RIGHT) + " move")
                + " · Alt+scroll turns" + keyNote(Keybinds.Action.DELETE, "removes")
                : keyNote(Keybinds.Action.TOOL_MOVE, "moves") + keyNote(Keybinds.Action.TOOL_ROTATE, "rotates") + keyNote(Keybinds.Action.COMMAND_BAR, "for commands")) + keyNote(Keybinds.Action.CANCEL, "clears"));
        requestRedraw();
    }

    /** Deletes the selected blocks as one undo step. */
    private void deleteSelectedBlocks() {
        int[] n = {0, 0};
        editLayers("Delete selection", null, null, le -> {
            for (var e : blockSel.entrySet()) {
                Layer l = ws.scene().find(e.getKey()).orElse(null);
                if (l == null || l.locked()) continue;
                for (long packed : e.getValue()) {
                    BlockPos p = BlockPos.unpack(packed);
                    if (!l.structure().get(p).isAir()) {
                        le.setIn(l, l.toWorld(p), BlockState.AIR);
                        n[0]++;
                    }
                }
            }
            // Fences, walls, panes, redstone and stairs next to the hole let go of it, in every layer.
            le.reconnect();
            for (var e : List.copyOf(entitySel.entrySet())) {
                Layer l = ws.scene().find(e.getKey()).orElse(null);
                if (l == null || l.locked()) continue;
                var gone = java.util.Set.copyOf(e.getValue());
                n[1] += gone.size();
                ws.editor().editEntities(l, "Delete selection", null, list -> list.removeAll(gone));
            }
        });
        blockSel.clear();
        entitySel.clear();
        showToast(n[1] == 0 ? String.format("Deleted %,d blocks", n[0])
                : n[0] == 0 ? String.format("Removed %d entit%s", n[1], n[1] == 1 ? "y" : "ies")
                : String.format("Deleted %,d blocks and %d entit%s", n[0], n[1], n[1] == 1 ? "y" : "ies"));
        requestRedraw();
    }

    /** Outlines selected blocks (or, for big selections, their bounds per layer). */
    private void drawBlockSelection(List<FrameRequest.Line> lines) {
        if (blockSel.isEmpty()) return;
        int color = 0xFFFF9F2E;
        boolean each = selectedBlockCount() <= SEL_OUTLINE_LIMIT;
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            if (l == null || !l.visible()) continue;
            Box bounds = null;
            for (long packed : e.getValue()) {
                BlockPos local = BlockPos.unpack(packed);
                if (l.structure().get(local).isAir()) continue;
                BlockPos w = l.toWorld(local);
                if (each) Overlays.block(lines, w.x(), w.y(), w.z(), color);
                else bounds = bounds == null ? new Box(w.x(), w.y(), w.z(), w.x(), w.y(), w.z()) : bounds.union(new Box(w.x(), w.y(), w.z(), w.x(), w.y(), w.z()));
            }
            if (bounds != null) {
                Overlays.box(lines, bounds.minX() - 0.02f, bounds.minY() - 0.02f, bounds.minZ() - 0.02f,
                        bounds.maxX() + 1.02f, bounds.maxY() + 1.02f, bounds.maxZ() + 1.02f, color);
            }
        }
    }

    private void selectOnly(Layer l) {
        ws.scene().setActive(l);
        ws.selectedLayers().setAll(l);
    }


    // ---- placement ------------------------------------------------------------------------------------------

    /**
     * Starts interactive placement: the layers appear as ghosts that follow the cursor until clicked (or Enter),
     * Esc cancels. Layers keep their positions relative to each other.
     */
    public void beginPlacement(List<Layer> layers, Runnable onDone) {
        cancelPlacement();
        placing.addAll(layers);
        placementDone = onDone;
        placementNudge = BlockPos.ORIGIN;
        BlockPos anchor = layers.getFirst().offset();
        for (Layer l : layers) {
            placingRelative.add(l.offset().subtract(anchor));
            l.setGhost(true);
            ws.scene().add(l);
        }
        if (ws.scene().layers().size() == layers.size()) {
            // Empty scene: drop it at the origin and frame it right away.
            commitPlacement();
            frameAll();
            return;
        }
        showToast("Click to place" + keyNote(Keybinds.Action.PLACE, "places") + " · " + (keyText(Keybinds.Action.ROTATE_PLACEMENT).isEmpty() ? "" : keyText(Keybinds.Action.ROTATE_PLACEMENT) + " or ")
                + "Alt+scroll rotate · Ctrl/Shift+scroll adjust" + keyNote(Keybinds.Action.CANCEL, "cancels"));
        requestFocus();
    }

    private void movePlacement(double x, double y) {
        Optional<Picker.Hit> h = pick(x, y);
        BlockPos target = h.map(Picker.Hit::adjacentWorld).orElseGet(() -> pickGround(x, y, groundY()).orElse(null));
        if (target == null) return;
        Layer first = placing.getFirst();
        Box b = first.structure().bounds().orElse(new Box(0, 0, 0, 0, 0, 0));
        // Centre the ghost's footprint on the cursor with its base on the target surface.
        BlockPos localCentre = new BlockPos((b.minX() + b.maxX()) / 2, b.minY(), (b.minZ() + b.maxZ()) / 2);
        BlockPos rotated = first.transform().apply(localCentre);
        BlockPos anchor = target.subtract(rotated).add(placementNudge);
        for (int i = 0; i < placing.size(); i++) {
            Layer l = placing.get(i);
            BlockPos off = anchor.add(placingRelative.get(i));
            if (!off.equals(l.offset())) {
                l.setOffset(off);
                ws.scene().firePropertiesChanged(l);
            }
        }
    }

    /** Turns the ghosts a quarter turn: {@code dir} 1 is clockwise from above, -1 anticlockwise. */
    private void rotatePlacement(int dir) {
        for (int i = 0; i < placing.size(); i++) {
            Layer l = placing.get(i);
            l.setTransform(l.transform().then(Transform.rotation(dir)));
            placingRelative.set(i, Transform.rotation(dir).apply(placingRelative.get(i)));
            ws.scene().firePropertiesChanged(l);
        }
        movePlacement(lastX, lastY);
    }

    private void commitPlacement() {
        if (placing.isEmpty()) return;
        for (Layer l : placing) {
            l.setGhost(false);
            ws.scene().firePropertiesChanged(l);
            ws.editor().recordAdded(l, "Place " + l.name());
        }
        ws.scene().setActive(placing.getLast());
        ws.selectedLayers().setAll(placing);
        placing.clear();
        placingRelative.clear();
        if (placementDone != null) placementDone.run();
        placementDone = null;
        showToast("Placed");
    }

    private void cancelPlacement() {
        if (placing.isEmpty()) return;
        for (Layer l : placing) ws.scene().remove(l);
        placing.clear();
        placingRelative.clear();
        placementDone = null;
        showToast("Placement cancelled");
    }

    public boolean isPlacing() {
        return !placing.isEmpty();
    }

    // ---- viewport settings ------------------------------------------------------------------------------------

    public void toggleSettings() {
        if (settingsPopover != null && settingsPopover.isShowing()) {
            settingsPopover.hide();
            return;
        }
        settingsPopover = ViewportSettings.popover(ws.settings(), () -> {
            applyViewSettings();
            requestRedraw();
        }, () -> {
            settingsPopover.hide();
            showShortcuts(true);
        });
        SidePopover.show(settingsPopover, settingsButton);
    }

    /** The hotbar shows in Build mode. */
    private void updateHotbarVisibility() {
        ToolKind t = ws.toolProperty().get();
        hotbar.setVisible(t == ToolKind.BUILD || t == ToolKind.BRUSH || t == ToolKind.PLUGIN);
        pluginToolBar.setVisible(t == ToolKind.PLUGIN && pluginTool != null && !pluginToolBar.getChildren().isEmpty());
        boolean brush = t == ToolKind.BRUSH || t == ToolKind.ERASER;
        brushBar.setVisible(brush);
        if (brush) brushBar.show(t == ToolKind.ERASER);
        brushPopup.setEraser(t == ToolKind.ERASER);
        if (!brush) endStroke();
    }

    private Keybinds keys;

    /** The user's key binds, so the key hints show the keys actually bound. */
    void setKeybinds(Keybinds keys) {
        this.keys = keys;
        brushPopup.setKeys(this::keyText);
        symmetryPopup.setKeys(this::keyText);
        hotbar.setKeys(this::keyText);
    }

    /** Frames the selected blocks if there are any, otherwise the active layer (or everything when there is none). */
    public void frameSelectionOrLayer() {
        setFly(false);
        Layer a = ws.activeLayerProperty().get();
        if (!blockSel.isEmpty()) frameBlockSelection();
        else if (a != null) frameLayer(a);
        else frameAll();
    }

    /** The key bound to an action as text ("Shift+Z"), or "" when it has none; for toasts and tooltips. */
    public String keyText(Keybinds.Action a) {
        if (keys == null) return "";
        var k = keys.get(a);
        return Keybinds.text(k[0] != null ? k[0] : k[1]);
    }

    private String keyOrNull(Keybinds.Action a) {
        String k = keyText(a);
        return k.isEmpty() ? null : k;
    }

    /** " · <key> <what>" for a toast, or "" when the action has no key. */
    private String keyNote(Keybinds.Action a, String what) {
        String k = keyText(a);
        return k.isEmpty() ? "" : " · " + k + " " + what;
    }

    /** A key other than Alt went down (possibly with Alt): an Alt+key shortcut, so the shape wheel must not open. */
    public void altComboPressed() {
        if (altAlone || shapeRadial.isOpen()) cancelShapeRadial();
    }

    /** A hint for a rebindable action, showing its current main key (none when it is unbound). */
    private void hint(List<KeyHints.Hint> h, String text, Keybinds.Action a) {
        if (keys == null) return;
        List<String> caps = keys.caps(a);
        if (!caps.isEmpty()) h.add(new KeyHints.Hint(caps, text));
    }

    /** Shift+F1: shows or hides the key hints in the corner. */
    public void toggleKeyHints() {
        ws.settings().showKeyHints = !ws.settings().showKeyHints;
        ws.settings().save();
        updateKeyHints();
        showToast(ws.settings().showKeyHints ? "Key hints on" + keyNote(Keybinds.Action.KEY_HINTS, "hides them") : "Key hints off" + keyNote(Keybinds.Action.KEY_HINTS, "shows them"));
    }

    /** Picks the key hints for what is going on now: placing an import, dragging a shape, flying, or the tool. */
    private void updateKeyHints() {
        boolean on = ws.settings().showKeyHints;
        keyHints.setVisible(on);
        if (!on) return;
        List<KeyHints.Hint> h = new ArrayList<>();
        ToolKind tool = ws.toolProperty().get();
        if (!placing.isEmpty()) {
            h.add(KeyHints.Hint.of("Place", "LMB"));
            hint(h, "Place", Keybinds.Action.PLACE);
            hint(h, "Rotate", Keybinds.Action.ROTATE_PLACEMENT);
            h.add(KeyHints.Hint.of("Rotate", "Alt", "Wheel"));
            hint(h, "Cancel", Keybinds.Action.CANCEL);
        } else if (shapeDrag != null) {
            h.add(KeyHints.Hint.of("Place the " + shapeDrag.shape.label.toLowerCase(java.util.Locale.ROOT), "RMB"));
            if (shapeDrag.shape.usesHeight()) h.add(KeyHints.Hint.of("Height", "Wheel"));
            hint(h, "Cancel", Keybinds.Action.CANCEL);
        } else {
            if (fly) {
                List<String> move = new ArrayList<>();
                for (Keybinds.Action a : new Keybinds.Action[]{Keybinds.Action.FLY_FORWARD, Keybinds.Action.FLY_LEFT, Keybinds.Action.FLY_BACK, Keybinds.Action.FLY_RIGHT})
                    move.addAll(keys == null ? List.of() : keys.caps(a));
                if (!move.isEmpty()) h.add(new KeyHints.Hint(move, "Fly"));
                List<String> updown = new ArrayList<>();
                if (keys != null) {
                    updown.addAll(keys.caps(Keybinds.Action.FLY_UP));
                    updown.addAll(keys.caps(Keybinds.Action.FLY_DOWN));
                }
                if (!updown.isEmpty()) h.add(new KeyHints.Hint(updown, "Up / down"));
                hint(h, "Sprint", Keybinds.Action.FLY_SPRINT);
            }
            switch (tool) {
                case BUILD -> {
                    var s = shape();
                    boolean single = s == io.blockdesigner.core.place.ShapeTool.Shape.SINGLE;
                    h.add(KeyHints.Hint.of("Break", "LMB"));
                    h.add(KeyHints.Hint.of(ws.replaceProperty().get() ? "Replace block" : single ? "Place" : "Drag out " + s.label.toLowerCase(java.util.Locale.ROOT), "RMB"));
                    h.add(KeyHints.Hint.of("Pick block", "MMB"));
                    hint(h, single ? "Shapes" : "Change shape", Keybinds.Action.SHAPE_WHEEL);
                    hint(h, ws.replaceProperty().get() ? "Stop replacing" : "Replace mode", Keybinds.Action.REPLACE_MODE);
                    hint(h, ws.shuffleProperty().get() ? "Stop shuffling" : "Shuffle hotbar", Keybinds.Action.SHUFFLE);
                    hint(h, "Symmetry", Keybinds.Action.SYMMETRY);
                    // The nine slots as "1-9" while they are on the number keys, else the first slot's key.
                    boolean digits = keys != null && java.util.stream.IntStream.range(0, 9).allMatch(i ->
                            keys.caps(Keybinds.Action.values()[Keybinds.Action.HOTBAR_1.ordinal() + i]).equals(List.of(Integer.toString(i + 1))));
                    if (digits) h.add(KeyHints.Hint.of("Hotbar", "1-9"));
                    else hint(h, "Hotbar slot 1", Keybinds.Action.HOTBAR_1);
                }
                case SELECT -> {
                    h.add(KeyHints.Hint.of("Select area", "LMB"));
                    h.add(KeyHints.Hint.of("Add / remove", "Shift", "Ctrl"));
                    hint(h, "Select or replace by type", Keybinds.Action.SELECT_BY_TYPE);
                    hint(h, "WorldEdit command", Keybinds.Action.COMMAND_BAR);
                    if (!blockSel.isEmpty()) {
                        hint(h, "Move / rotate them", Keybinds.Action.TOOL_MOVE);
                        hint(h, "Move to new layer", Keybinds.Action.MOVE_TO_LAYER);
                        hint(h, "Delete", Keybinds.Action.DELETE);
                        hint(h, "Clear selection", Keybinds.Action.CANCEL);
                    }
                }
                case MOVE -> {
                    h.add(KeyHints.Hint.of(movableSelection() ? "Move the selected blocks" : "Drag arrow / square", "LMB"));
                    h.add(KeyHints.Hint.of("Nudge", "Ctrl", "Wheel"));
                    hint(h, "Rotate tool", Keybinds.Action.TOOL_ROTATE);
                }
                case ROTATE -> {
                    h.add(KeyHints.Hint.of(movableSelection() ? "Turn the selected blocks" : "Drag a ring", "LMB"));
                    hint(h, "Move tool", Keybinds.Action.TOOL_MOVE);
                }
                case BRUSH -> {
                    h.add(KeyHints.Hint.of("Paint", "LMB"));
                    h.add(KeyHints.Hint.of("Smooth", "RMB"));
                    hint(h, "Smaller brush", Keybinds.Action.BRUSH_SMALLER);
                    hint(h, "Bigger brush", Keybinds.Action.BRUSH_BIGGER);
                    hint(h, "Weaker / stronger", Keybinds.Action.BRUSH_WEAKER);
                    hint(h, "Brush mode: Draw", Keybinds.Action.BRUSH_MODE_1);
                }
                case ERASER -> {
                    h.add(KeyHints.Hint.of("Erase", "LMB"));
                    hint(h, "Smaller eraser", Keybinds.Action.BRUSH_SMALLER);
                    hint(h, "Bigger eraser", Keybinds.Action.BRUSH_BIGGER);
                }
                case PLUGIN -> {
                    h.add(KeyHints.Hint.of(pluginTool == null ? "Use the tool" : pluginTool.tool().tool().name(), "LMB"));
                    h.add(KeyHints.Hint.of("Other action", "RMB"));
                }
                case VIEW -> {
                }
            }
            if (!fly) {
                h.add(KeyHints.Hint.of("Orbit", "MMB"));
                h.add(KeyHints.Hint.of("Pan", "Shift", "MMB"));
                hint(h, "Fly", Keybinds.Action.FLY);
            } else {
                hint(h, "Stop flying", Keybinds.Action.FLY);
            }
        }
        hint(h, "All shortcuts", Keybinds.Action.SHORTCUTS);
        keyHints.show(h);
    }

    /** Alt+K: the keyboard shortcuts card over the viewport. */
    public void toggleShortcuts() {
        showShortcuts(!shortcutsShowing());
    }

    private boolean shortcutsShowing() {
        return shortcutsPopover != null && shortcutsPopover.isShowing();
    }

    private void showShortcuts(boolean show) {
        if (!show) {
            if (shortcutsPopover != null) shortcutsPopover.hide();
            return;
        }
        setFly(false);
        shortcuts.refresh();
        if (shortcutsPopover == null) shortcutsPopover = SidePopover.create("Keyboard shortcuts", shortcuts);
        SidePopover.show(shortcutsPopover, keysButton);
    }

    /** Applies overlay visibility from the settings (called when they change). */
    private void applyViewSettings() {
        updateHud();
    }

    // ---- feedback -------------------------------------------------------------------------------------------

    public void showToast(String text) {
        toast.setText(text);
        toastFade.stop();
        toast.setOpacity(1);
        toastHold.playFromStart();
    }
}
