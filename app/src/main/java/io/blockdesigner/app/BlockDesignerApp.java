package io.blockdesigner.app;

import io.blockdesigner.app.ui.MainWindow;
import javafx.application.Application;
import io.blockdesigner.app.ai.AssistantController;
import io.blockdesigner.app.ai.ChatPanel;
import io.blockdesigner.app.ui.IterationTimeline;
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

        AssistantController assistant = new AssistantController(ws, window.viewport());
        ChatPanel chat = new ChatPanel(ws, assistant);
        IterationTimeline timeline = new IterationTimeline(ws, window.viewport());
        chat.setOnTurnStarting(() -> {
            if (!timeline.hasSnapshots()) timeline.snapshot("Before assistant");
        });
        chat.setOnTurnFinished(timeline::snapshot);
        window.setRightPanel(chat);
        window.setCenterBottom(timeline);
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
