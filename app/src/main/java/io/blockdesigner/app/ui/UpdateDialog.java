package io.blockdesigner.app.ui;

import io.blockdesigner.app.update.Updater;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Offers a newer release: its notes, then Install and restart (download, check, hand over to the installer and quit),
 * Skip this version, or Later. Copies not started from an install only get a link to the download page.
 */
final class UpdateDialog extends Dialog<Void> {
    private static final ButtonType INSTALL = new ButtonType("Install and restart", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType PAGE = new ButtonType("Open download page", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType SKIP = new ButtonType("Skip this version", ButtonBar.ButtonData.LEFT);
    private static final ButtonType LATER = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Thread worker;

    /**
     * @param readyToQuit asked before quitting (e.g. to save the project); false cancels the install
     * @param quit        closes BlockDesigner once the installer is waiting for it
     * @param skip        remembers that the user doesn't want this version
     */
    UpdateDialog(Window owner, boolean dark, Updater updater, Updater.Release release, BooleanSupplier readyToQuit,
                 Runnable quit, Consumer<String> skip, Consumer<String> browser) {
        initOwner(owner);
        setTitle("Update available");
        setResizable(true);
        var dp = getDialogPane();
        dp.getStylesheets().add(UpdateDialog.class.getResource("/io/blockdesigner/app/app.css").toExternalForm());
        dp.getStyleClass().addAll("app-root", dark ? "dark" : "light");

        Label title = new Label("BlockDesigner " + release.version() + " is available", new FontIcon(Feather.DOWNLOAD_CLOUD));
        title.getStyleClass().add("export-title");
        title.setGraphicTextGap(12);
        Updater.Mode mode = Updater.mode();
        Updater.Asset asset = release.assetFor(mode);
        Label sub = new Label("You have " + Updater.currentVersion() + ". " + switch (mode) {
            case INSTALLED -> "BlockDesigner will close, install the update and open again.";
            case PORTABLE -> "BlockDesigner will close, replace this folder's files (your data folder is kept) and open again.";
            case DEV -> "This copy isn't an installed build, so it can't update itself.";
        });
        sub.getStyleClass().add("plugin-meta");
        sub.setWrapText(true);

        TextArea notes = new TextArea(release.notes().isBlank() ? "(No release notes.)" : release.notes().strip());
        notes.setEditable(false);
        notes.setWrapText(true);
        VBox.setVgrow(notes, Priority.ALWAYS);

        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setVisible(false);
        bar.setManaged(false);
        Label status = new Label();
        status.setWrapText(true);
        status.getStyleClass().add("plugin-meta");

        VBox body = new VBox(10, title, sub, notes, bar, status);
        body.setPadding(new Insets(4));
        body.setPrefSize(620, 460);
        dp.setContent(body);

        boolean canInstall = asset != null;
        dp.getButtonTypes().setAll(canInstall ? INSTALL : PAGE, SKIP, LATER);
        if (mode != Updater.Mode.DEV && asset == null)
            status.setText("This release has no " + (mode == Updater.Mode.PORTABLE ? "portable zip" : "setup .exe")
                    + " to install from; download it from the release page.");

        ((Button) dp.lookupButton(SKIP)).setOnAction(e -> skip.accept(release.version()));
        if (!canInstall) {
            ((Button) dp.lookupButton(PAGE)).setOnAction(e -> browser.accept(release.page().toString()));
            return;
        }

        Button install = (Button) dp.lookupButton(INSTALL);
        install.getStyleClass().add("accent");
        install.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume(); // stay open while downloading
            if (!readyToQuit.getAsBoolean()) return;
            install.setDisable(true);
            dp.lookupButton(SKIP).setDisable(true);
            bar.setVisible(true);
            bar.setManaged(true);
            bar.setProgress(0);
            status.setText("Downloading " + asset.name() + "…");
            worker = Thread.ofVirtual().name("update-download").start(() -> {
                try {
                    Path dir = Updater.workDir();
                    Path file = updater.download(asset, dir, p -> Platform.runLater(() -> bar.setProgress(p)));
                    Path ready = file;
                    if (mode == Updater.Mode.PORTABLE) {
                        Platform.runLater(() -> {
                            bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                            status.setText("Unpacking…");
                        });
                        ready = dir.resolve("unpacked");
                        Updater.unzip(file, ready);
                        Files.deleteIfExists(file);
                        if (!Files.isRegularFile(ready.resolve("BlockDesigner/BlockDesigner.exe")))
                            throw new java.io.IOException("The portable zip doesn't hold BlockDesigner/BlockDesigner.exe.");
                    }
                    Updater.launchInstaller(mode, ready);
                    Platform.runLater(() -> {
                        status.setText("Closing to install…");
                        close();
                        quit.run();
                    });
                } catch (InterruptedException ex) {
                    // dialog closed
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        bar.setVisible(false);
                        bar.setManaged(false);
                        status.setText("Update failed: " + ex.getMessage());
                        install.setDisable(false);
                        dp.lookupButton(SKIP).setDisable(false);
                    });
                }
            });
        });
        setOnHidden(e -> {
            if (worker != null) worker.interrupt();
        });
    }
}
