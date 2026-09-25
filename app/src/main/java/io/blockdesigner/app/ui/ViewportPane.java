package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.Workspace.ToolKind;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.edit.LayerChange;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.transform.BlockTransformer;
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
 * <p>Layer nudging: <b>Ctrl+scroll</b> moves up/down, <b>Alt+scroll</b> left/right and <b>Shift+scroll</b>
 * back/forward, relative to the camera. Arrow keys and PgUp/PgDn do the same; hold Tab for bigger steps.
 */
public final class ViewportPane extends StackPane {
    private static final int ACCENT = 0xFF7C9CFF;

    private final Workspace ws;
    private final Camera camera = new Camera();
    private final ImageView view = new ImageView();
    private final Label toast = new Label();
    private final Label hint = new Label();
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
    private boolean holdActed;
    private long holdNext;
    private String holdMergeKey;
    private int holdCounter;

    // creative flight
    private boolean fly;
    private final java.util.Set<KeyCode> flyKeys = java.util.EnumSet.noneOf(KeyCode.class);
    private double flySpeedFactor = 1;
    private javafx.scene.robot.Robot robot;
    private double centerX, centerY;
    private boolean ignoreNextMove;
    private long lastPulse;
    private final List<Runnable> flyChanged = new ArrayList<>();
    private final BlockInfoHud hud = new BlockInfoHud();
    private final StackPane crosshair = new StackPane();
    private BlockPos boxStart, boxEnd;
    private Box flashBox;
    private final PauseTransition flashTimer = new PauseTransition(Duration.millis(1400));
    private LayerChange.Props moveBefore;
    private BlockPos moveStartGround, moveStartOffset;

    // placement (ghost follows the cursor until clicked)
    private final List<Layer> placing = new ArrayList<>();
    private final List<BlockPos> placingRelative = new ArrayList<>();
    private BlockPos placementNudge = BlockPos.ORIGIN;
    private Runnable placementDone;

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
        hint.getStyleClass().add("viewport-hint");
        hint.setMouseTransparent(true);
        StackPane.setAlignment(hint, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setMargin(hud, new javafx.geometry.Insets(12, 0, 0, 12));
        javafx.scene.shape.Rectangle ch = new javafx.scene.shape.Rectangle(18, 2), cv = new javafx.scene.shape.Rectangle(2, 18);
        ch.getStyleClass().add("crosshair-bar");
        cv.getStyleClass().add("crosshair-bar");
        crosshair.getChildren().addAll(ch, cv);
        crosshair.setMouseTransparent(true);
        crosshair.setMaxSize(18, 18);
        crosshair.setVisible(false);
        getChildren().addAll(hud, crosshair, toast, hint);
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
            updateHint();
            requestRedraw();
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
        updateHint();

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
        float aspect = w / (float) h;
        float[] vp = new float[16];
        camera.viewProjection(aspect).get(vp);
        Vector3f eye = camera.eye();
        Layer active = ws.activeLayerProperty().get();
        List<FrameRequest.LayerDraw> draws = sceneRenderer.layerDraws(active == null ? null : active.id());
        List<FrameRequest.Line> lines = new ArrayList<>();

        for (Layer l : ws.scene().layers()) {
            if (!l.visible()) continue;
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
        if (placing.isEmpty()) {
            ToolKind tool = ws.toolProperty().get();
            if (tool == ToolKind.BOX && boxStart != null && boxEnd != null) {
                Box b = Box.of(boxStart, boxEnd);
                Overlays.box(lines, b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1, 0xFFFFC85A);
            } else if (hover != null) {
                boolean adjacent = !fly && (tool == ToolKind.PLACE || tool == ToolKind.BOX);
                BlockPos p = adjacent ? hover.adjacentWorld() : hover.world();
                Overlays.block(lines, p.x(), p.y(), p.z(), adjacent ? 0xFFFFC85A : 0xE6FFFFFF);
            } else if (hoverGround != null && (fly || tool == ToolKind.PLACE || tool == ToolKind.BOX || tool == ToolKind.BUILD)) {
                Overlays.block(lines, hoverGround.x(), hoverGround.y(), hoverGround.z(), 0xFFFFC85A);
            }
        }

        Optional<Box> sb = ws.scene().worldBounds();
        float gridY = sb.map(b -> (float) b.minY()).orElse(0f);
        float[] gc = sb.map(b -> new float[]{(b.minX() + b.maxX()) / 2f, (b.minZ() + b.maxZ()) / 2f}).orElse(new float[]{0, 0});
        return new FrameRequest(w, h, vp, new float[]{eye.x, eye.y, eye.z}, draws, lines,
                ws.darkProperty().get() ? FrameRequest.Theme.DARK : FrameRequest.Theme.LIGHT,
                ws.settings().showGrid, gridY, gc, ++sequence);
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
                req.showGrid(), req.gridY(), req.gridCenter(), req.sequence());
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
    private enum Action { PLACE, BREAK, PAINT }

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
        return Picker.pick(ws.scene(), r[0], r[1], fly ? FLY_REACH : 2000, l -> !l.locked() && !placing.contains(l));
    }

    private float groundY() {
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

        if (fly) {
            // Minecraft controls: left break, right place, middle pick.
            switch (e.getButton()) {
                case PRIMARY -> startHold(Action.BREAK, true);
                case SECONDARY -> startHold(Action.PLACE, true);
                case MIDDLE -> pickBlock();
                default -> {
                }
            }
            e.consume();
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY && ws.toolProperty().get() == ToolKind.BUILD && placing.isEmpty()) {
            // Right button also orbits: place on a quick click or after holding still; dragging orbits instead.
            startHold(Action.PLACE, false);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY || e.isAltDown()) return;

        if (!placing.isEmpty()) {
            commitPlacement();
            return;
        }
        updateHover(e.getX(), e.getY());
        switch (ws.toolProperty().get()) {
            case SELECT -> {
                if (hover != null) {
                    if (e.isShiftDown() || e.isShortcutDown()) toggleSelected(hover.layer());
                    else selectOnly(hover.layer());
                }
            }
            case BUILD, ERASE -> startHold(Action.BREAK, true);
            case PLACE -> startHold(Action.PLACE, true);
            case PAINT -> startHold(Action.PAINT, true);
            case PICK -> {
                if (pickBlock()) ws.toolProperty().set(ToolKind.PLACE);
            }
            case BOX -> {
                boxStart = hover != null ? hover.adjacentWorld() : hoverGround;
                boxEnd = boxStart;
            }
            case MOVE -> {
                Layer target = hover != null ? hover.layer() : ws.activeLayerProperty().get();
                if (target != null && !target.locked()) {
                    ws.scene().setActive(target);
                    moveBefore = LayerChange.Props.of(target);
                    moveStartOffset = target.offset();
                    moveStartGround = pickGround(e.getX(), e.getY(), target.worldBounds().map(b -> (float) b.minY()).orElse(0f)).orElse(null);
                }
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
        boolean orbit = dragButton == MouseButton.SECONDARY || (dragButton == MouseButton.PRIMARY && e.isAltDown());
        if (orbit) {
            lastX = e.getX();
            lastY = e.getY();
            if (dragDistance > CLICK_SLOP) {
                if (holdAction == Action.PLACE && holdButton == MouseButton.SECONDARY) stopHold();
                camera.orbit((float) (dx * 0.008), (float) (dy * 0.008));
                requestRedraw();
            }
            return;
        }
        if (dragButton == MouseButton.MIDDLE) {
            lastX = e.getX();
            lastY = e.getY();
            if (dragDistance > CLICK_SLOP) {
                camera.pan((float) (dx / getHeight()), (float) (dy / getHeight()));
                requestRedraw();
            }
            return;
        }
        if (dragButton != MouseButton.PRIMARY) return;
        updateHover(e.getX(), e.getY());
        switch (ws.toolProperty().get()) {
            case BOX -> {
                if (boxStart != null) {
                    // Extend on the start block's horizontal plane; hold Shift to extend vertically instead.
                    if (e.isShiftDown()) {
                        Vector3f[] r = ray(e.getX(), e.getY());
                        float t = (boxStart.z() + 0.5f - r[0].z) / (Math.abs(r[1].z) < 1e-5 ? 1e-5f : r[1].z);
                        int y = (int) Math.floor(r[0].y + r[1].y * t);
                        boxEnd = new BlockPos(boxEnd.x(), y, boxEnd.z());
                    } else {
                        pickGround(e.getX(), e.getY(), boxStart.y()).ifPresent(g -> boxEnd = new BlockPos(g.x(), boxEnd.y(), g.z()));
                    }
                    showToast(Box.of(boxStart, boxEnd).sizeX() + " × " + Box.of(boxStart, boxEnd).sizeY() + " × " + Box.of(boxStart, boxEnd).sizeZ());
                    requestRedraw();
                }
            }
            case MOVE -> {
                Layer l = ws.activeLayerProperty().get();
                if (l != null && moveStartGround != null) {
                    pickGround(e.getX(), e.getY(), moveStartGround.y()).ifPresent(g -> {
                        BlockPos off = moveStartOffset.add(g.x() - moveStartGround.x(), 0, g.z() - moveStartGround.z());
                        if (!off.equals(l.offset())) {
                            l.setOffset(off);
                            ws.scene().firePropertiesChanged(l);
                            showToast("Offset " + off);
                        }
                    });
                }
            }
            default -> {
                // Held place/break/paint repeat from the frame loop, rate-limited by the configured delays.
            }
        }
    }

    private void onRelease(MouseEvent e) {
        boolean click = dragDistance <= CLICK_SLOP;
        if (holdButton == e.getButton()) {
            // A quick right-click with the Build tool places once (the hold never got past its initial delay).
            if (holdAction == Action.PLACE && !holdActed && click && !fly) doAction(Action.PLACE);
            stopHold();
        }
        if (fly) {
            e.consume();
            return;
        }
        if (e.getButton() == MouseButton.MIDDLE && click) pickBlock();
        if (ws.toolProperty().get() == ToolKind.BOX && boxStart != null && boxEnd != null && e.getButton() == MouseButton.PRIMARY) {
            fillBox(Box.of(boxStart, boxEnd), e.isShortcutDown() ? BlockState.AIR : ws.selectedBlockProperty().get());
            boxStart = boxEnd = null;
        }
        if (moveBefore != null) {
            Layer l = ws.activeLayerProperty().get();
            if (l != null) {
                LayerChange.Props after = LayerChange.Props.of(l);
                LayerChange.Props before = moveBefore;
                if (!after.equals(before)) {
                    // Revert to "before", then re-apply through the editor so the move lands on the undo stack.
                    l.setOffset(before.offset());
                    ws.editor().modifyLayer(l, "Move " + l.name(), null, x -> x.setOffset(after.offset()));
                }
            }
            moveBefore = null;
            moveStartGround = null;
        }
        dragButton = null;
        requestRedraw();
    }

    private void onScroll(ScrollEvent e) {
        // Windows reports Shift+wheel as horizontal scrolling.
        double delta = e.getDeltaY() != 0 ? e.getDeltaY() : e.getDeltaX();
        if (delta == 0) return;
        int sign = delta > 0 ? 1 : -1;
        if (e.isControlDown()) {
            nudge(0, sign, 0);
        } else if (e.isAltDown()) {
            int[] r = camera.screenRightAxis();
            nudge(r[0] * sign, 0, r[1] * sign);
        } else if (e.isShiftDown()) {
            int[] f = camera.screenForwardAxis();
            nudge(f[0] * sign, 0, f[1] * sign);
        } else if (fly) {
            flySpeedFactor = Math.clamp(flySpeedFactor * (sign > 0 ? 1.25 : 0.8), 0.1, 16);
            showToast(String.format("Flying speed %.1f×", flySpeedFactor));
        } else {
            camera.zoom((float) Math.pow(1.0018, -delta));
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
            case PAGE_UP -> nudge(0, 1, 0);
            case PAGE_DOWN -> nudge(0, -1, 0);
            case ESCAPE -> {
                if (fly) setFly(false);
                else if (!placing.isEmpty()) cancelPlacement();
                else {
                    boxStart = boxEnd = null;
                    ws.toolProperty().set(ToolKind.SELECT);
                }
            }
            case R -> {
                if (!placing.isEmpty()) rotatePlacement();
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
            showToast("Flying · WASD move · Space/Shift up/down · Ctrl sprint · left break · right place · middle pick · Esc exit");
        } else {
            showToast("Orbit camera");
        }
        flyChanged.forEach(Runnable::run);
        updateHint();
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
        camera.orbit((float) (dx * 0.0032), (float) (dy * 0.0032));
        recenterMouse();
        updateHover(aimX(), aimY());
    }

    private void flyStep(double dt) {
        if (!fly || flyKeys.isEmpty()) return;
        Vector3f f = camera.flatForward(), r = new Vector3f(-f.z, 0, f.x);
        Vector3f move = new Vector3f();
        if (flyKeys.contains(KeyCode.W)) move.add(f);
        if (flyKeys.contains(KeyCode.S)) move.sub(f);
        if (flyKeys.contains(KeyCode.D)) move.add(r);
        if (flyKeys.contains(KeyCode.A)) move.sub(r);
        if (move.lengthSquared() > 0) move.normalize();
        if (flyKeys.contains(KeyCode.SPACE)) move.y += 1;
        if (flyKeys.contains(KeyCode.SHIFT)) move.y -= 1;
        if (move.lengthSquared() == 0) return;
        double speed = ws.settings().flySpeed * flySpeedFactor * (flyKeys.contains(KeyCode.CONTROL) ? 2 : 1);
        move.mul((float) (speed * dt));
        camera.translate(move.x, move.y, move.z);
        updateHover(aimX(), aimY());
        requestRedraw();
    }

    // ---- place / break with repeat delays ---------------------------------------------------------------------

    private void startHold(Action a, boolean immediate) {
        holdAction = a;
        holdButton = dragButton;
        holdActed = false;
        holdMergeKey = "hold-" + (++holdCounter);
        if (immediate) {
            doAction(a);
            holdActed = true;
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
        holdActed = true;
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
            case PAINT -> {
                if (hover == null) return;
                Layer l = hover.layer();
                BlockState st = BlockTransformer.defaults().apply(ws.selectedBlockProperty().get(), l.transform().inverse());
                if (l.structure().get(hover.local()) == st) return;
                try (SceneEditor.BlockSession s = ws.editor().edit(l, "Paint block", key)) {
                    s.set(hover.local().x(), hover.local().y(), hover.local().z(), st);
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
        ws.selectedBlockProperty().set(s);
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

    private static String fmt(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    // ---- block tools ----------------------------------------------------------------------------------------

    private void fillBox(Box worldBox, BlockState state) {
        Layer layer = ws.activeLayerProperty().get();
        if (layer == null) {
            layer = new Layer("Layer " + (ws.scene().layers().size() + 1), new io.blockdesigner.core.model.Structure());
            ws.editor().addLayer(layer);
        }
        if (layer.locked()) {
            showToast("Layer is locked");
            return;
        }
        BlockState s = BlockTransformer.defaults().apply(state, layer.transform().inverse());
        try (SceneEditor.BlockSession session = ws.editor().edit(layer, state.isAir() ? "Clear box" : "Fill box")) {
            for (int y = worldBox.minY(); y <= worldBox.maxY(); y++)
                for (int z = worldBox.minZ(); z <= worldBox.maxZ(); z++)
                    for (int x = worldBox.minX(); x <= worldBox.maxX(); x++) {
                        BlockPos l = layer.toLocal(new BlockPos(x, y, z));
                        session.set(l.x(), l.y(), l.z(), s);
                    }
        }
        showToast((state.isAir() ? "Cleared " : "Filled ") + worldBox.volume() + " blocks");
    }

    private void selectOnly(Layer l) {
        ws.scene().setActive(l);
        ws.selectedLayers().setAll(l);
    }

    private void toggleSelected(Layer l) {
        if (ws.selectedLayers().contains(l)) ws.selectedLayers().remove(l);
        else ws.selectedLayers().add(l);
        ws.scene().setActive(l);
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
        showToast("Click to place · R rotate · Ctrl/Alt/Shift+scroll adjust · Esc cancel");
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

    private void rotatePlacement() {
        for (int i = 0; i < placing.size(); i++) {
            Layer l = placing.get(i);
            l.setTransform(l.transform().then(Transform.rotation(1)));
            placingRelative.set(i, Transform.rotation(1).apply(placingRelative.get(i)));
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

    private void updateHint() {
        if (fly) {
            hint.setText("Flying    ·    WASD move · Space/Shift up/down · Ctrl sprint · wheel speed · left break · right place · middle pick · C/Esc stop flying");
            return;
        }
        String tool = switch (ws.toolProperty().get()) {
            case SELECT -> "Click a layer to select · Shift-click to multi-select";
            case BUILD -> "Left-click break · right-click place (hold to repeat) · middle-click pick";
            case PLACE -> "Click to place · drag to paint";
            case ERASE -> "Click or drag to erase";
            case PAINT -> "Click or drag to repaint blocks";
            case PICK -> "Click a block to pick it";
            case BOX -> "Drag to fill a box · Shift = height · Ctrl = clear";
            case MOVE -> "Drag a layer across the ground";
        };
        hint.setText(tool + "    ·    Right-drag orbit · Middle-drag pan · Middle-click pick · Wheel zoom · Ctrl/Alt/Shift+wheel move layer · C fly · F frame");
    }
}
