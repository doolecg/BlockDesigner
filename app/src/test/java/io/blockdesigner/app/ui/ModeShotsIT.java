package io.blockdesigner.app.ui;

import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Whole-window shots of the editing modes (Build, Brush, Eraser, Select) with the Pinecrest Watchtower from testdata,
 * for checking the mode badge and the brush toolbar by eye. Uses a copy of the user's settings (for the Minecraft
 * assets), never the real file. Runs only with MODE_SHOTS=1; PNGs go to app/build/mode-shots.
 */
class ModeShotsIT {
    @Test
    void modes() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("MODE_SHOTS")), "set MODE_SHOTS=1 to render mode shots");
        Path data = Files.createTempDirectory("bd-mode-shots");
        Path real = Path.of(System.getenv("APPDATA"), "BlockDesigner", "settings.json");
        if (Files.isRegularFile(real)) Files.copy(real, data.resolve("settings.json"));
        System.setProperty("blockdesigner.dataDir", data.toString());
        CompletableFuture<Void> started = new CompletableFuture<>();
        try {
            Platform.startup(() -> started.complete(null));
        } catch (IllegalStateException alreadyRunning) {
            started.complete(null);
        }
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        Path dir = Path.of("build", "mode-shots");
        Files.createDirectories(dir);

        Settings settings = fx(Settings::load);
        settings.showStartScreen = false;
        Workspace ws = fx(() -> new Workspace(settings));
        MainWindow window = fx(() -> {
            Stage stage = new Stage();
            MainWindow w = new MainWindow(stage, ws);
            w.setRightPanel();
            stage.setWidth(1500);
            stage.setHeight(880);
            w.show();
            w.closeStartScreen();
            w.importFile(Path.of("../testdata/Pinecrest Watchtower.litematic"));
            return w;
        });
        // The import arrives as a ghost to place: wait for it, then place it.
        for (int i = 0; i < 60 && !fx(() -> window.viewport().isPlacing()); i++) Thread.sleep(500);
        fx(() -> {
            var m = ViewportPane.class.getDeclaredMethod("commitPlacement");
            m.setAccessible(true);
            m.invoke(window.viewport());
            window.viewport().frameAll();
            return null;
        });
        // The Minecraft assets (textures) load in the background: wait for them, then for the meshes.
        for (int i = 0; i < 120 && fx(() -> ws.assets() == null); i++) Thread.sleep(500);
        for (int i = 0; i < 60 && fx(() -> window.viewport().meshesPending()); i++) Thread.sleep(500);
        Thread.sleep(6000);

        for (Workspace.ToolKind tool : new Workspace.ToolKind[]{Workspace.ToolKind.BUILD, Workspace.ToolKind.BRUSH,
                Workspace.ToolKind.ERASER, Workspace.ToolKind.SELECT}) {
            fx(() -> {
                ws.toolProperty().set(tool);
                return null;
            });
            Thread.sleep(1500);
            save(fx(() -> window.stage().getScene().snapshot(null)), dir.resolve("mode-" + tool.name().toLowerCase() + ".png"));
        }
        fx(() -> {
            window.stage().close();
            return null;
        });
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
        return f.get(60, TimeUnit.SECONDS);
    }

    private static void save(WritableImage img, Path out) throws Exception {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        bi.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(bi, "png", out.toFile());
    }
}
