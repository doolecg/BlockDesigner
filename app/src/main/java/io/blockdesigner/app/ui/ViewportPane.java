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
    private static final int ACCENT = 0xFF7C9CFF;

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
    private PixelBuffer<IntBuffer> pixels;
    private int pbW, pbH;
    private final AtomicReference<ViewportRenderer.Frame> latest = new AtomicReference<>();
    private final AtomicBoolean redraw = new AtomicBoolean(true);
    private long sequence;
    private boolean framedOnce;

    // input state
    private double lastX, lastY, pressX, pressY;
    private MouseButton dragButton;
    private boolean fastNudge;
    private final int[] burstDelta = new int[3];
    private Picker.Hit hover;
    private BlockPos hoverGround;
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
    private final StackPane crosshair = new StackPane();
    private Box flashBox;
    private final PauseTransition flashTimer = new PauseTransition(Duration.millis(1400));

    // Select mode: selected blocks per layer id (packed layer-local positions) and the marquee being dragged
    private final java.util.Map<String, java.util.Set<Long>> blockSel = new java.util.LinkedHashMap<>();
    private final javafx.scene.shape.Rectangle marquee = new javafx.scene.shape.Rectangle();
    private static final int SEL_OUTLINE_LIMIT = 4000;
    private final javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();

    // Move / Rotate tools: the gizmo overlay, the handle under the mouse and the drag in progress
    private final Gizmo gizmo = new Gizmo();
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
        StackPane.setMargin(sliceBadge, new javafx.geometry.Insets(14, 176, 0, 0));
        settingsButton.getStyleClass().addAll("flat", "viewport-settings-button");
        settingsButton.setTooltip(new javafx.scene.control.Tooltip("Viewport settings: field of view, clipping, fog, overlays, controls"));
        settingsButton.setFocusTraversable(false);
        settingsButton.setOnAction(e -> toggleSettings());
        StackPane.setAlignment(settingsButton, Pos.TOP_RIGHT);
        StackPane.setMargin(settingsButton, new javafx.geometry.Insets(10, 12, 0, 0));
        keysButton.getStyleClass().addAll("flat", "viewport-settings-button");
        keysButton.setTooltip(new javafx.scene.control.Tooltip("Keyboard shortcuts (Alt+K)"));
        keysButton.setFocusTraversable(false);
        keysButton.setOnAction(e -> toggleShortcuts());
        StackPane.setAlignment(keysButton, Pos.TOP_RIGHT);
        StackPane.setMargin(keysButton, new javafx.geometry.Insets(52, 12, 0, 0));
        filterButton.getStyleClass().addAll("flat", "viewport-settings-button");
        filterButton.setTooltip(new javafx.scene.control.Tooltip("Select by type (T): select blocks by type, layer and properties"));
        filterButton.setFocusTraversable(false);
        filterButton.setOnAction(e -> openSelectByType(null));
        StackPane.setAlignment(filterButton, Pos.TOP_RIGHT);
        StackPane.setMargin(filterButton, new javafx.geometry.Insets(94, 12, 0, 0));
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
        StackPane.setAlignment(hotbar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hotbar, new javafx.geometry.Insets(0, 0, 14, 0));
        getChildren().addAll(marquee, hud, crosshair, sliceBadge, toast, hotbar, viewCube, settingsButton, keysButton, filterButton);
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
        ws.activeLayerProperty().addListener((o, a, b) -> requestRedraw());
        ws.selectedLayers().addListener((javafx.collections.ListChangeListener<Layer>) c -> requestRedraw());
        ws.toolProperty().addListener((o, a, b) -> {
            updateHotbarVisibility();
            requestRedraw();
            showToast(switch (b) {
                case BUILD -> ws.replaceProperty().get() ? "Build mode · Replace · left break · right replace · middle pick · R places again"
                        : "Build mode · left break · right place · middle pick · B to leave";
                case SELECT -> "Select mode";
                case VIEW -> "View mode";
                case MOVE -> "Move · drag an arrow, square or the centre · G";
                case ROTATE -> "Rotate · drag a ring to turn 90° · E";
            });
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
        requestRedraw();
        return gpu.ready();
    }

    public void detach() {
        if (sceneRenderer != null) sceneRenderer.close();
        if (gpu != null) gpu.close();
        sceneRenderer = null;
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
        if (sceneRenderer != null) sceneRenderer.sync();
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
        List<FrameRequest.Line> lines = new ArrayList<>();

        for (Layer l : ws.scene().layers()) {
            if (!l.visible() || (!ws.settings().showOutlines && !placing.contains(l))) continue;
            Optional<Box> b = l.worldBounds();
            if (b.isEmpty()) continue;
            Box wb = b.get();
            if (placing.contains(l)) {
                Overlays.box(lines, wb.minX(), wb.minY(), wb.minZ(), wb.maxX() + 1, wb.maxY() + 1, wb.maxZ() + 1, 0xFF46C46E);
            } else if (l == active) {
                Overlays.box(lines, wb.minX(), wb.minY(), wb.minZ(), wb.maxX() + 1, wb.maxY() + 1, wb.maxZ() + 1, ACCENT);
                Overlays.axes(lines, wb.minX(), wb.minY(), wb.minZ(), 2.5f);
            } else if (ws.selectedLayers().contains(l)) {
                Overlays.box(lines, wb.minX(), wb.minY(), wb.minZ(), wb.maxX() + 1, wb.maxY() + 1, wb.maxZ() + 1, 0xAA7C9CFF);
            }
        }
        if (flashBox != null) {
            Box f = flashBox;
            Overlays.box(lines, f.minX() - 0.02f, f.minY() - 0.02f, f.minZ() - 0.02f, f.maxX() + 1.02f, f.maxY() + 1.02f, f.maxZ() + 1.02f, 0xFFFFC85A);
        }
        drawBlockSelection(lines);
        if (placing.isEmpty() && ws.toolProperty().get() != ToolKind.VIEW) {
            boolean build = ws.toolProperty().get() == ToolKind.BUILD;
            if (hover != null) {
                BlockPos p = hover.world();
                Overlays.block(lines, p.x(), p.y(), p.z(), build && ws.replaceProperty().get() ? 0xFFFFC85A : 0xE6FFFFFF);
            } else if (hoverGround != null && build) {
                Overlays.block(lines, hoverGround.x(), hoverGround.y(), hoverGround.z(), 0xFFFFC85A);
            }
        }

        updateGizmo();

        Optional<Box> sb = ws.scene().worldBounds();
        float gridY = sliceY != null ? sliceY : sb.map(b -> (float) b.minY()).orElse(0f);
        float[] gc = sb.map(b -> new float[]{(b.minX() + b.maxX()) / 2f, (b.minZ() + b.maxZ()) / 2f}).orElse(new float[]{0, 0});
        return new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, draws, lines,
                ws.darkProperty().get() ? FrameRequest.Theme.DARK : FrameRequest.Theme.LIGHT,
                ws.settings().showGrid, gridY, gc, ++sequence,
                ws.settings().fog && !camera.orthoActive() ? (float) ws.settings().fogDistance : Float.POSITIVE_INFINITY);
    }

    /** Renders the current scene from the given camera for screenshots (AI vision, thumbnails). */
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
            updateHover(e.getX(), e.getY());
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
            if (e.getCode() == KeyCode.TAB) {
                fastNudge = false;
                e.consume();
            }
            if (fly && isFlyKey(e.getCode())) e.consume();
        });
        sceneProperty().addListener((o, a, sc) -> {
            if (sc == null) return;
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
        return Picker.pick(ws.scene(), r[0], r[1], fly ? CREATIVE_REACH : 10000, l -> !l.locked() && !placing.contains(l),
                sliceMin(), sliceMax());
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
        hoverGround = hover == null ? pickGround(x, y, groundY()).orElse(null) : null;
        updateHud();
        requestRedraw();
    }

    private void updateHud() {
        if (hover == null) {
            hud.show(null, null, null, null);
            return;
        }
        BlockState local = hover.layer().structure().get(hover.local());
        hud.show(ws.assets(), BlockTransformer.defaults().apply(local, hover.layer().transform()), hover.world(), hover.layer());
    }

    private void onPress(MouseEvent e) {
        requestFocus();
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
                clickSelect(e.isShiftDown(), e.isShortcutDown());
            } else if (mode == ToolKind.BUILD) {
                switch (e.getButton()) {
                    case PRIMARY -> startHold(Action.BREAK);
                    case SECONDARY -> startHold(Action.PLACE);
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
        if (e.getButton() == MouseButton.SECONDARY && mode == ToolKind.BUILD && placing.isEmpty()) {
            startHold(Action.PLACE);
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
                // Looking only: the camera is on the middle button (see onDrag).
            }
            case SELECT -> {
                // Selection happens on release: a click selects one block, a drag draws a marquee.
            }
            case BUILD -> startHold(Action.BREAK);
            case MOVE, ROTATE -> {
                // A click on a layer selects it on release (see onRelease).
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
        boolean click = dragDistance <= CLICK_SLOP;
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
            updateHover(e.getX(), e.getY());
            showContextMenu(e.getScreenX(), e.getScreenY());
        }
        if (e.getButton() == MouseButton.PRIMARY && ws.toolProperty().get() == ToolKind.SELECT && placing.isEmpty()) {
            if (marquee.isVisible()) {
                marqueeSelect(Math.min(pressX, e.getX()), Math.min(pressY, e.getY()), Math.max(pressX, e.getX()), Math.max(pressY, e.getY()),
                        e.isShiftDown(), e.isShortcutDown());
            } else if (click) {
                updateHover(e.getX(), e.getY());
                clickSelect(e.isShiftDown(), e.isShortcutDown());
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
        if (delta == 0) return;
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

    private static boolean isFlyKey(KeyCode k) {
        return k == KeyCode.W || k == KeyCode.A || k == KeyCode.S || k == KeyCode.D || k == KeyCode.SPACE
                || k == KeyCode.SHIFT || k == KeyCode.CONTROL;
    }

    private void onKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (fly && isFlyKey(k)) {
            flyKeys.add(k);
            e.consume();
            return;
        }
        switch (k) {
            case TAB -> fastNudge = true;
            case C -> {
                if (e.isShortcutDown() || e.isAltDown()) return;
                setFly(!fly);
            }
            case LEFT, RIGHT -> {
                int[] r = camera.screenRightAxis();
                int s = k == KeyCode.RIGHT ? 1 : -1;
                nudge(r[0] * s, 0, r[1] * s);
            }
            case UP, DOWN -> {
                int[] f = camera.screenForwardAxis();
                int s = k == KeyCode.UP ? 1 : -1;
                nudge(f[0] * s, 0, f[1] * s);
            }
            case PAGE_UP -> stepSlice(1);
            case PAGE_DOWN -> stepSlice(-1);
            case INSERT -> toggleSingleSlice();
            case T -> {
                if (e.isShortcutDown() || e.isAltDown() || !placing.isEmpty()) return;
                Layer hl = hover != null ? hover.layer() : null;
                openSelectByType(hl == null ? null : BlockTransformer.defaults().apply(hl.structure().get(hover.local()), hl.transform()));
            }
            case ESCAPE -> {
                if (gizmoDrag != null) cancelGizmoDrag();
                else if (shortcutsShowing()) showShortcuts(false);
                else if (fly) setFly(false);
                else if (!placing.isEmpty()) cancelPlacement();
                else if (!blockSel.isEmpty()) clearBlockSelection();
                else ws.toolProperty().set(ToolKind.SELECT);
            }
            case DELETE, BACK_SPACE -> {
                if (ws.toolProperty().get() == ToolKind.BUILD) {
                    // Build mode: Delete empties the held hotbar slot.
                    BlockState removed = ws.clearHeldSlot();
                    showToast(removed == null ? "That hotbar slot is already empty"
                            : "Removed " + BlockInfoHud.pretty(removed.path()) + " from the hotbar");
                } else if (!blockSel.isEmpty()) {
                    deleteSelectedBlocks();
                } else {
                    return;
                }
            }
            case R -> {
                if (!placing.isEmpty()) rotatePlacement(1);
                else return;
            }
            case F -> {
                // F frames the selected blocks if there are any, otherwise the active layer; Shift+F frames everything.
                setFly(false);
                Layer a = ws.activeLayerProperty().get();
                if (e.isShiftDown()) frameAll();
                else if (!blockSel.isEmpty()) frameBlockSelection();
                else if (a != null) frameLayer(a);
                else frameAll();
            }
            case ENTER -> {
                if (!placing.isEmpty()) commitPlacement();
                else return;
            }
            default -> {
                return;
            }
        }
        e.consume();
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
            showToast("Flying · WASD move (W/S follow the view) · Space/Shift up/down · Ctrl sprint · B toggles building · Esc exit");
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
        float k = (float) (0.0032 * ws.settings().lookSensitivity);
        camera.orbit((float) dx * k, (float) dy * k);
        recenterMouse();
        updateHover(aimX(), aimY());
    }

    private void flyStep(double dt) {
        if (!fly) {
            flyVel.zero();
            return;
        }
        // Forward follows the look direction, pitch included (spectator / UE-style); strafing stays level.
        Vector3f flat = camera.flatForward(), f = camera.forward(), r = new Vector3f(-flat.z, 0, flat.x);
        Vector3f move = new Vector3f();
        if (flyKeys.contains(KeyCode.W)) move.add(f);
        if (flyKeys.contains(KeyCode.S)) move.sub(f);
        if (flyKeys.contains(KeyCode.D)) move.add(r);
        if (flyKeys.contains(KeyCode.A)) move.sub(r);
        if (move.lengthSquared() > 0) move.normalize();
        if (flyKeys.contains(KeyCode.SPACE)) move.y += 1;
        if (flyKeys.contains(KeyCode.SHIFT)) move.y -= 1;
        double speed = ws.settings().flySpeed * (flyKeys.contains(KeyCode.CONTROL) ? 2 : 1);
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
        doAction(a);
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
                if (hover == null) return;
                Layer l = hover.layer();
                BlockState broken = l.structure().get(hover.local());
                if (broken.isAir()) return;
                BlockPos bw = hover.world();
                if (ws.settings().breakParticles) {
                    BlockAssets assets = ws.assets();
                    BlockState shown = BlockTransformer.defaults().apply(broken, l.transform());
                    particles.burst(bw.x(), bw.y(), bw.z(), assets == null ? null : BlockIcons.icon(assets, shown));
                    particlesDrawn = true;
                }
                if (ws.settings().blockSounds) sounds.breakBlock(ws.settings().soundVolume);
                try (SceneEditor.BlockSession s = ws.editor().edit(l, "Break block", key)) {
                    s.set(hover.local().x(), hover.local().y(), hover.local().z(), BlockState.AIR);
                    // Neighbouring fences, walls, panes and stairs let go of the broken block.
                    Transform t = l.transform();
                    var updates = BlockPlacement.reconnect(List.of(hover.world()), p -> {
                        BlockPos lp = l.toLocal(p);
                        return BlockTransformer.defaults().apply(s.get(lp.x(), lp.y(), lp.z()), t);
                    });
                    for (var en : updates.entrySet()) {
                        BlockPos lp = l.toLocal(en.getKey());
                        s.set(lp.x(), lp.y(), lp.z(), BlockTransformer.defaults().apply(en.getValue(), t.inverse()));
                    }
                }
            }
            case PLACE -> {
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
                java.util.Map<BlockPos, BlockState> edits = placementFor(l, world, held);
                if (edits.isEmpty()) return;
                if (fly && edits.keySet().stream().anyMatch(this::insideCamera)) return;
                try (SceneEditor.BlockSession s = ws.editor().edit(l, "Place block", key)) {
                    for (var en : edits.entrySet()) {
                        BlockPos local = l.toLocal(en.getKey());
                        s.set(local.x(), local.y(), local.z(), BlockTransformer.defaults().apply(en.getValue(), l.transform().inverse()));
                    }
                }
                if (ws.settings().blockSounds) sounds.place(ws.settings().soundVolume);
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
        Transform t = l.transform();
        var edits = BlockPlacement.replace(held, hover.world(),
                p -> BlockTransformer.defaults().apply(l.structure().get(l.toLocal(p)), t), blocks);
        if (edits.isEmpty()) return;
        try (SceneEditor.BlockSession s = ws.editor().edit(l, "Replace block", key)) {
            for (var en : edits.entrySet()) {
                BlockPos local = l.toLocal(en.getKey());
                s.set(local.x(), local.y(), local.z(), BlockTransformer.defaults().apply(en.getValue(), t.inverse()));
            }
        }
        if (ws.settings().blockSounds) sounds.place(ws.settings().soundVolume);
    }

    /**
     * The world blocks to set for placing the held block at {@code world} in layer {@code l}, oriented the way
     * Minecraft would from the aimed face, the point on it and the look direction (see {@link BlockPlacement}).
     */
    private java.util.Map<BlockPos, BlockState> placementFor(Layer l, BlockPos world, BlockState held) {
        Vector3f[] r = ray(aimX(), aimY());
        Vector3f dir = new Vector3f(r[1]).normalize();
        BlockPlacement.Context ctx;
        if (hover != null) {
            Vector3f hit = new Vector3f(dir).mul(hover.distance()).add(r[0]);
            // Only a block of the same layer can be clicked into (a slab doubling up).
            BlockPos clicked = hover.layer() == l ? hover.world() : null;
            ctx = new BlockPlacement.Context(world, clicked, BlockPlacement.Dir.of(hover.normal()), hit.x, hit.y, hit.z, dir.x, dir.y, dir.z);
        } else {
            ctx = new BlockPlacement.Context(world, null, BlockPlacement.Dir.UP, world.x() + 0.5, world.y(), world.z() + 0.5, dir.x, dir.y, dir.z);
        }
        BlockAssets assets = ws.assets();
        BlockPlacement.Blocks blocks = assets == null ? BlockPlacement.Blocks.NONE
                : id -> assets.registry().get(id).map(i -> new BlockPlacement.Info(i.defaultState(), i.properties())).orElse(null);
        Transform t = l.transform();
        return BlockPlacement.place(held, ctx,
                p -> BlockTransformer.defaults().apply(l.structure().get(l.toLocal(p)), t), blocks);
    }

    private boolean insideCamera(BlockPos p) {
        Vector3f e = camera.eye();
        return (int) Math.floor(e.x) == p.x() && (int) Math.floor(e.y) == p.y() && (int) Math.floor(e.z) == p.z();
    }

    /** Minecraft's pick-block: select the highlighted block (with its properties) for placing. */
    private boolean pickBlock() {
        if (hover == null) return false;
        BlockState s = BlockTransformer.defaults().apply(hover.layer().structure().get(hover.local()), hover.layer().transform());
        ws.recordInHotbar(s);
        showToast("Picked " + BlockInfoHud.pretty(s.path()));
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

        if (hover != null) {
            contextMenu.getItems().add(item("Pick block to hotbar", "Middle-click", this::pickBlock));
            Layer hl = hover.layer();
            BlockState type = BlockTransformer.defaults().apply(hl.structure().get(hover.local()), hl.transform());
            String typeName = BlockInfoHud.pretty(type.path());
            javafx.scene.control.Menu byType = new javafx.scene.control.Menu("Select by type");
            byType.getItems().addAll(
                    item("All " + typeName + " in " + hl.name(), null, () -> quickSelectType(type, false, List.of(hl))),
                    item("All " + typeName + " in visible layers", null, () -> quickSelectType(type, false, typeLayers(SelectByTypePanel.Scope.VISIBLE))),
                    item("Same exact state in " + hl.name(), null, () -> quickSelectType(type, true, List.of(hl))),
                    new javafx.scene.control.SeparatorMenuItem(),
                    item("More options…", "T", () -> openSelectByType(type)));
            contextMenu.getItems().add(byType);
        }
        if (n > 0) {
            contextMenu.getItems().addAll(
                    item(String.format("Delete %,d block%s", n, n == 1 ? "" : "s"), "Del", this::deleteSelectedBlocks),
                    item("Replace with " + (held == null ? "held block" : BlockInfoHud.pretty(held.path())), null, this::replaceSelection),
                    item("Copy to new layer", null, this::copySelectionToLayer),
                    item("Clear selection", "Esc", this::clearBlockSelection));
        }
        if (layer != null) {
            if (!contextMenu.getItems().isEmpty()) contextMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            contextMenu.getItems().addAll(
                    item("Select all in " + layer.name(), null, () -> selectAllIn(layer)),
                    item("Select by type…", "T", () -> openSelectByType(null)),
                    item("Frame " + layer.name(), "F", () -> frameLayer(layer)),
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
        String key = "replace-selection-" + (++holdCounter);
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            if (l == null || l.locked()) continue;
            BlockState st = BlockTransformer.defaults().apply(held, l.transform().inverse());
            try (SceneEditor.BlockSession s = ws.editor().edit(l, "Replace selection", key)) {
                for (long packed : e.getValue()) {
                    BlockPos p = BlockPos.unpack(packed);
                    if (!s.get(p.x(), p.y(), p.z()).isAir()) s.set(p.x(), p.y(), p.z(), st);
                }
            }
        }
        ws.editor().undoStack().sealTop();
        showToast("Replaced with " + BlockInfoHud.pretty(held.path()));
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
     * Blender's numpad views: 1 front, 3 right, 7 top (Ctrl for back, left, bottom), 9 the opposite side, 5
     * perspective / orthographic, 2 4 6 8 orbit in 15° steps, . frames the active layer. Returns whether it was used.
     */
    public boolean numpad(KeyEvent e) {
        boolean ctrl = e.isShortcutDown();
        float step = (float) Math.toRadians(15);
        switch (e.getCode()) {
            case NUMPAD1 -> snapView(ctrl ? ViewCube.View.BACK : ViewCube.View.FRONT);
            case NUMPAD3 -> snapView(ctrl ? ViewCube.View.LEFT : ViewCube.View.RIGHT);
            case NUMPAD7 -> snapView(ctrl ? ViewCube.View.BOTTOM : ViewCube.View.TOP);
            case NUMPAD9 -> oppositeView();
            case NUMPAD5 -> toggleOrtho();
            case NUMPAD4, NUMPAD6, NUMPAD8, NUMPAD2 -> {
                if (fly) setFly(false);
                leaveAutoOrtho();
                animStart = -1;
                KeyCode k = e.getCode();
                camera.orbit(k == KeyCode.NUMPAD4 ? -step : k == KeyCode.NUMPAD6 ? step : 0,
                        k == KeyCode.NUMPAD8 ? -step : k == KeyCode.NUMPAD2 ? step : 0);
                requestRedraw();
            }
            case DECIMAL -> {
                Layer a = ws.activeLayerProperty().get();
                if (a != null) frameLayer(a);
                else frameAll();
            }
            default -> {
                return false;
            }
        }
        return true;
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
            pivot = gizmoDrag != null && mode == Gizmo.Mode.ROTATE ? gizmoDrag.pivot : boundsCenter(gizmoTargets());
        }
        gizmo.update(mode, pivot, camera, getWidth(), getHeight(), gizmoHot, gizmoDrag == null ? null : gizmoDrag.handle);
    }

    private void beginGizmoDrag(Gizmo.Handle h, double x, double y) {
        List<Layer> layers = gizmoTargets();
        Vector3f pivot = boundsCenter(layers);
        if (pivot == null) {
            showToast(ws.nudgeTargets().isEmpty() ? "Select a layer first" : "Layer is locked");
            return;
        }
        GizmoDrag d = new GizmoDrag();
        d.handle = h;
        d.layers = layers;
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
        ws.editor().undoStack().beginGroup(h.kind() == Gizmo.Kind.RING ? "Rotate layers" : layers.size() == 1 ? "Move " + layers.getFirst().name() : "Move layers");
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
        gizmoDrag = null;
        ws.editor().undoStack().endGroup();
        requestRedraw();
    }

    private void cancelGizmoDrag() {
        boolean changed = gizmoDrag.changed;
        gizmoDrag = null;
        ws.editor().undoStack().endGroup();
        if (changed) ws.editor().undoStack().undo();
        showToast("Cancelled");
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

    /** Opens the Select by type dialog (T), with {@code preselect}'s block (world-facing) ticked if given. */
    public void openSelectByType(BlockState preselect) {
        if (!placing.isEmpty()) return;
        setFly(false);
        ws.toolProperty().set(ToolKind.SELECT);
        SelectByTypePanel.Scope scope = ws.selectedLayers().size() > 1 ? SelectByTypePanel.Scope.SELECTED : SelectByTypePanel.Scope.VISIBLE;
        if (selectByTypePopover != null && selectByTypePopover.isShowing()) selectByTypePopover.hide();
        SelectByTypePanel panel = new SelectByTypePanel(ws.assets(), scope, !blockSel.isEmpty(), sliceY != null, preselect, q -> {
            java.util.Map<String, Long> counts = new java.util.HashMap<>();
            forEachTypeCandidate(q, (l, packed, st) -> {
                if (q.matches(st)) counts.merge(q.key(st), 1L, Long::sum);
            });
            return counts;
        }, this::applySelectByType, () -> {
            if (selectByTypePopover != null) selectByTypePopover.hide();
            requestFocus();
        });
        selectByTypePopover = SidePopover.create("Select by type", panel);
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
        selectionChanged();
    }

    /**
     * Selects every block of the visible, unlocked layers whose centre falls inside the screen rectangle (x-ray: hidden
     * blocks behind others count too). Shift adds, Ctrl removes; blocks hidden by the slice view are skipped.
     */
    private void marqueeSelect(double x0, double y0, double x1, double y1, boolean add, boolean subtract) {
        if (!add && !subtract) blockSel.clear();
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
        selectionChanged();
    }

    private void clearBlockSelection() {
        if (blockSel.isEmpty()) return;
        blockSel.clear();
        selectionChanged();
    }

    private int selectedBlockCount() {
        int n = 0;
        for (java.util.Set<Long> set : blockSel.values()) n += set.size();
        return n;
    }

    private void selectionChanged() {
        int n = selectedBlockCount();
        showToast(n == 0 ? "Selection cleared" : String.format("%,d block%s selected · Delete removes · Esc clears", n, n == 1 ? "" : "s"));
        requestRedraw();
    }

    /** Deletes the selected blocks as one undo step. */
    private void deleteSelectedBlocks() {
        String key = "delete-selection-" + (++holdCounter);
        int n = 0;
        for (var e : blockSel.entrySet()) {
            Layer l = ws.scene().find(e.getKey()).orElse(null);
            if (l == null || l.locked()) continue;
            try (SceneEditor.BlockSession s = ws.editor().edit(l, "Delete selection", key)) {
                for (long packed : e.getValue()) {
                    BlockPos p = BlockPos.unpack(packed);
                    if (!s.get(p.x(), p.y(), p.z()).isAir()) {
                        s.set(p.x(), p.y(), p.z(), BlockState.AIR);
                        n++;
                    }
                }
            }
        }
        ws.editor().undoStack().sealTop();
        blockSel.clear();
        showToast(String.format("Deleted %,d blocks", n));
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
        showToast("Click to place · R or Alt+scroll rotate · Ctrl/Shift+scroll adjust · Esc cancel");
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

    private void toggleSettings() {
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
        hotbar.setVisible(ws.toolProperty().get() == ToolKind.BUILD);
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
        if (shortcutsPopover == null) shortcutsPopover = SidePopover.create("Keyboard shortcuts", shortcuts);
        SidePopover.show(shortcutsPopover, keysButton);
    }

    /** Applies overlay visibility from the settings (called when they change). */
    private void applyViewSettings() {
        hud.setVisible(ws.settings().showHud);
    }

    // ---- feedback -------------------------------------------------------------------------------------------

    /** Briefly outlines a world-space box (e.g. where the assistant just built). */
    public void flash(Box worldBox) {
        flashBox = worldBox;
        flashTimer.setOnFinished(e -> {
            flashBox = null;
            requestRedraw();
        });
        flashTimer.playFromStart();
        requestRedraw();
    }

    public void showToast(String text) {
        toast.setText(text);
        toastFade.stop();
        toast.setOpacity(1);
        toastHold.playFromStart();
    }
}
