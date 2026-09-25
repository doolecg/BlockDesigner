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
    private final ShortcutsPanel shortcuts = new ShortcutsPanel(() -> showShortcuts(false));
    private final Label sliceBadge = new Label();
    private final Hotbar hotbar;
    private final javafx.scene.control.Button settingsButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.SLIDERS));
    private atlantafx.base.controls.Popover settingsPopover;
    private final javafx.scene.control.Button keysButton = new javafx.scene.control.Button(null, new org.kordamp.ikonli.javafx.FontIcon(org.kordamp.ikonli.feather.Feather.COMMAND));
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
    private static final float FLY_REACH = 256;

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
        getChildren().add(view);

        toast.getStyleClass().add("viewport-toast");
        toast.setOpacity(0);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        shortcuts.setVisible(false);
        StackPane.setAlignment(shortcuts, Pos.CENTER);
        sliceBadge.getStyleClass().add("viewport-badge");
        sliceBadge.setMouseTransparent(true);
        sliceBadge.setVisible(false);
        StackPane.setAlignment(sliceBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(sliceBadge, new javafx.geometry.Insets(14, 58, 0, 0));
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
        getChildren().addAll(marquee, hud, crosshair, sliceBadge, toast, hotbar, settingsButton, keysButton, shortcuts);
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
                case BUILD -> "Build mode · left break · right place · middle pick · B to leave";
                case SELECT -> "Select mode";
                case VIEW -> "View mode";
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
        Vector3f eye = camera.eye();
        Layer active = ws.activeLayerProperty().get();
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
                Overlays.block(lines, p.x(), p.y(), p.z(), 0xE6FFFFFF);
            } else if (hoverGround != null && build) {
                Overlays.block(lines, hoverGround.x(), hoverGround.y(), hoverGround.z(), 0xFFFFC85A);
            }
        }

        Optional<Box> sb = ws.scene().worldBounds();
        float gridY = sliceY != null ? sliceY : sb.map(b -> (float) b.minY()).orElse(0f);
        float[] gc = sb.map(b -> new float[]{(b.minX() + b.maxX()) / 2f, (b.minZ() + b.maxZ()) / 2f}).orElse(new float[]{0, 0});
        return new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, draws, lines,
                ws.darkProperty().get() ? FrameRequest.Theme.DARK : FrameRequest.Theme.LIGHT,
                ws.settings().showGrid, gridY, gc, ++sequence,
                ws.settings().fog ? (float) ws.settings().fogDistance : Float.POSITIVE_INFINITY);
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
            if (fly) flyLook(e);
            else updateHover(e.getX(), e.getY());
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
        return Picker.pick(ws.scene(), r[0], r[1], fly ? FLY_REACH : 2000, l -> !l.locked() && !placing.contains(l),
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
            BlockPos p = g.get();
            Vector3f eye = camera.eye();
            if (eye.distance(p.x() + 0.5f, p.y() + 0.5f, p.z() + 0.5f) > FLY_REACH) return Optional.empty();
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
        if (e.getButton() == MouseButton.MIDDLE) {
            orbitPivot = pivotUnder(e.getX(), e.getY());
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
            if (dragDistance > CLICK_SLOP) {
                if (e.isShiftDown()) {
                    camera.pan((float) (dx / getHeight()), (float) (dy / getHeight()));
                } else {
                    float k = (float) (0.008 * ws.settings().orbitSensitivity);
                    if (orbitPivot != null) camera.orbitAround(orbitPivot, (float) dx * k, (float) dy * k);
                    else camera.orbit((float) dx * k, (float) dy * k);
                }
                requestRedraw();
            }
            return;
        }
        if (dragButton != MouseButton.PRIMARY) return;
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
            case ESCAPE -> {
                if (shortcuts.isVisible()) showShortcuts(false);
                else if (fly) setFly(false);
                else if (!placing.isEmpty()) cancelPlacement();
                else if (!blockSel.isEmpty()) clearBlockSelection();
                else ws.toolProperty().set(ToolKind.SELECT);
            }
            case DELETE, BACK_SPACE -> {
                if (blockSel.isEmpty()) return;
                deleteSelectedBlocks();
            }
            case R -> {
                if (!placing.isEmpty()) rotatePlacement(1);
                else return;
            }
            case F -> {
                setFly(false);
                Layer a = ws.activeLayerProperty().get();
                if (a != null && !e.isShiftDown()) frameLayer(a);
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
        if (!fly || flyKeys.isEmpty()) return;
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
        if (move.lengthSquared() == 0) return;
        double speed = ws.settings().flySpeed * (flyKeys.contains(KeyCode.CONTROL) ? 2 : 1);
        move.mul((float) (speed * dt));
        camera.translate(move.x, move.y, move.z);
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
                try (SceneEditor.BlockSession s = ws.editor().edit(l, "Break block", key)) {
                    s.set(hover.local().x(), hover.local().y(), hover.local().z(), BlockState.AIR);
                }
            }
            case PLACE -> {
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
                if (fly && insideCamera(world)) return;
                BlockPos local = l.toLocal(world);
                if (!l.structure().get(local).isAir()) return;
                BlockState st = BlockTransformer.defaults().apply(ws.selectedBlockProperty().get(), l.transform().inverse());
                try (SceneEditor.BlockSession s = ws.editor().edit(l, "Place block", key)) {
                    s.set(local.x(), local.y(), local.z(), st);
                }
            }
        }
        updateHover(aimX(), aimY());
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
        settingsPopover.show(settingsButton);
    }

    /** The hotbar shows in Build mode. */
    private void updateHotbarVisibility() {
        hotbar.setVisible(ws.toolProperty().get() == ToolKind.BUILD);
    }

    /** Alt+K: the keyboard shortcuts card over the viewport. */
    public void toggleShortcuts() {
        showShortcuts(!shortcuts.isVisible());
    }

    private void showShortcuts(boolean show) {
        if (show) setFly(false);
        shortcuts.setVisible(show);
        requestFocus();
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
