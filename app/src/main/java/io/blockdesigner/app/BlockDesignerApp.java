package io.blockdesigner.app;

import io.blockdesigner.app.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

/** JavaFX entry point: builds the workspace and main window. */
public final class BlockDesignerApp extends Application {
    @Override
    public void start(Stage stage) {
        Settings settings = Settings.load();
        Workspace ws = new Workspace(settings);
        MainWindow window = new MainWindow(stage, ws);
        window.setRightPanel();
        window.setBrowser(url -> getHostServices().showDocument(url));

        window.show();

        // Files passed on the command line (e.g. "Open with")
        for (String arg : getParameters().getRaw()) {
            Path p = Path.of(arg);
            if (!Files.isRegularFile(p)) continue;
            window.closeStartScreen();
            if (arg.endsWith(".bdproj")) window.openProject(p);
            else window.importFile(p);
        }
    }
}
