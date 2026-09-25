package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.render.Camera;
import javafx.scene.control.Dialog;
import io.blockdesigner.render.ViewportRenderer;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Opens a project in the real main window and writes promotional PNGs to build/promo: full window shots in dark and
 * light mode plus clean high-resolution viewport renders. Runs only with PROMO_PROJECT=path/to/project.bdproj.
 */
class PromoShotsIT {

    private static final int HERO_W = 2560, HERO_H = 1440;

    @Test
    void promo() throws Exception {
        String project = System.getenv("PROMO_PROJECT");
        Assumptions.assumeTrue(project != null && !project.isBlank(), "set PROMO_PROJECT to a .bdproj to render promo shots");
        CompletableFuture<Void> started = new CompletableFuture<>();
        try {
            Platform.startup(() -> started.complete(null));
        } catch (IllegalStateException alreadyRunning) {
            started.complete(null);
        }
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        Path dir = Path.of("build", "promo");
        Files.createDirectories(dir);

        Workspace ws = fx(() -> new Workspace(Settings.load()));
        MainWindow window = fx(() -> {
            Stage stage = new Stage();
            MainWindow w = new MainWindow(stage, ws);
            w.setRightPanel();
            stage.setWidth(1600);
            stage.setHeight(900);
            w.show();
            w.closeStartScreen();
            w.openProject(Path.of(project));
            return w;
        });
        waitForScene(window, ws);
        fx(() -> {
            // Untitled layers read as "Unnamed" in the layers list; name them after the project.
            ws.scene().layers().stream().filter(l -> l.name() == null || l.name().isBlank() || l.name().equals("Unnamed"))
                    .forEach(l -> l.setName(ws.projectNameProperty().get()));
            return null;
        });

        // Every theme in dark and light.
        for (AppTheme t : AppTheme.values()) {
            for (boolean dark : new boolean[]{true, false}) {
                fx(() -> {
                    ws.themeProperty().set(t.name());
                    ws.darkProperty().set(dark);
                    frame(window);
                    return null;
                });
                Thread.sleep(2000);
                shootWindow(window, dir.resolve("theme-" + t.name().toLowerCase() + "-" + (dark ? "dark" : "light") + ".png"));
            }
        }

        for (boolean dark : new boolean[]{true, false}) {
            String mode = dark ? "dark" : "light";
            fx(() -> {
                ws.themeProperty().set(AppTheme.CLAUDE.name());
                ws.darkProperty().set(dark);
                return null;
            });
            Thread.sleep(2000);

            // Tool modes (the dock highlights the active one; move and rotate show the gizmo).
            for (Workspace.ToolKind tool : Workspace.ToolKind.values()) {
                fx(() -> {
                    ws.toolProperty().set(tool);
                    return null;
                });
                Thread.sleep(1200);
                shootWindow(window, dir.resolve("tool-" + tool.name().toLowerCase() + "-" + mode + ".png"));
            }
            fx(() -> {
                ws.toolProperty().set(Workspace.ToolKind.VIEW);
                return null;
            });

            // Litematica-style slice view: step down a few levels from the top.
            Method step = ViewportPane.class.getDeclaredMethod("stepSlice", int.class);
            step.setAccessible(true);
            fx(() -> {
                for (int i = 0; i < 6; i++) step.invoke(window.viewport(), -1);
                return null;
            });
            Thread.sleep(1500);
            shootWindow(window, dir.resolve("slice-" + mode + ".png"));
            fx(() -> {
                Field slice = ViewportPane.class.getDeclaredField("sliceY");
                slice.setAccessible(true);
                slice.set(window.viewport(), null);
                window.viewport().requestRedraw();
                return null;
            });

            // Export windows on each card, plus the data pack window.
            for (String card : new String[]{"litematica", "vanilla", "sponge", "datapack"}) {
                fx(() -> {
                    shootDialog(new ExportDialog(window.stage(), ws, null, java.util.List.of(), null, card),
                            dir.resolve("export-" + card + "-" + mode + ".png"));
                    return null;
                });
            }
            try {
                fx(() -> {
                    DatapackDialog pack = new DatapackDialog(window.stage(), ws, null);
                    shootDialog(pack, dir.resolve("datapack-" + mode + ".png"));
                    DatapackDialog village = new DatapackDialog(window.stage(), ws, null);
                    village.getDialogPane().lookupAll(".toggle-button").stream()
                            .filter(n -> n instanceof javafx.scene.control.ToggleButton tb && tb.getText() != null && tb.getText().startsWith("Village"))
                            .findFirst().ifPresent(n -> ((javafx.scene.control.ToggleButton) n).setSelected(true));
                    shootDialog(village, dir.resolve("datapack-village-" + mode + ".png"));
                    return null;
                });
            } catch (Exception | LinkageError e) {
                System.out.println("skipped data pack window: " + e);
            }

            // Clean high-resolution renders.
            for (String view : new String[]{"iso_se", "iso_sw", "iso_ne", "iso_nw"}) {
                hero(window, view, 0.62f, dir.resolve("hero-" + view + "-" + mode + ".png"));
            }
            hero(window, "iso_se", 0.46f, dir.resolve("closeup-" + mode + ".png"));
        }
        fx(() -> {
            window.stage().hide();
            return null;
        });
    }

    /** Frames the scene in the live viewport, a little tighter than the default fit. */
    private static void frame(MainWindow window) throws Exception {
        window.viewport().frameAll();
        Field f = ViewportPane.class.getDeclaredField("camera");
        f.setAccessible(true);
        ((Camera) f.get(window.viewport())).zoom(0.72f);
        window.viewport().requestRedraw();
    }

    private static void waitForScene(MainWindow window, Workspace ws) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1000);
            boolean ready = fx(() -> !ws.scene().layers().isEmpty() && !window.viewport().meshesPending());
            if (ready) break;
        }
        // Give block textures time to load and meshes to rebuild with them.
        Thread.sleep(10_000);
    }

    private static void hero(MainWindow window, String view, float zoom, Path out) throws Exception {
        CompletableFuture<ViewportRenderer.Frame> f = fx(() -> {
            Camera c = window.viewport().presetCamera(view);
            if (zoom != 1f) c.zoom(zoom);
            return window.viewport().captureWhenReady(c, HERO_W, HERO_H);
        });
        ViewportRenderer.Frame frame = f.get(30, TimeUnit.SECONDS);
        int[] px = new int[frame.width() * frame.height()];
        frame.pixels().clear();
        frame.pixels().get(px);
        BufferedImage bi = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, frame.width(), frame.height(), px, 0, frame.width());
        ImageIO.write(bi, "png", out.toFile());
        System.out.println("wrote " + out.toAbsolutePath());
    }

    private static <T> T fx(Callable<T> task) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(task.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(30, TimeUnit.SECONDS);
    }

    private static void shootWindow(MainWindow window, Path out) throws Exception {
        save(fx(() -> window.stage().getScene().snapshot(null)), out);
    }

    private static void shootDialog(Dialog<?> d, Path out) throws Exception {
        d.show();
        d.getDialogPane().applyCss();
        d.getDialogPane().layout();
        save(d.getDialogPane().snapshot(null, null), out);
        d.setResult(null);
        d.close();
    }

    private static void save(WritableImage img, Path out) throws Exception {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        bi.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(bi, "png", out.toFile());
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
