package io.blockdesigner.app;

import io.blockdesigner.app.ui.MainWindow;
import javafx.application.Application;
import io.blockdesigner.app.ai.AssistantController;
import io.blockdesigner.app.ai.ChatPanel;
import io.blockdesigner.app.ui.ComingSoonPanel;
import io.blockdesigner.app.ui.IterationTimeline;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

/** JavaFX entry point: builds the workspace and main window. */
public final class BlockDesignerApp extends Application {
    /**
     * The AI assistant is a future feature: while false, its tab shows a "Coming soon" card and the iteration timeline
     * (which records assistant turns) is hidden. The assistant code is kept; flip this to bring it back.
     */
    static final boolean AI_ASSISTANT = false;

    @Override
    public void start(Stage stage) {
        Settings settings = Settings.load();
        Workspace ws = new Workspace(settings);
        MainWindow window = new MainWindow(stage, ws);

        if (AI_ASSISTANT) {
            AssistantController assistant = new AssistantController(ws, window.viewport());
            ChatPanel chat = new ChatPanel(ws, assistant);
            IterationTimeline timeline = new IterationTimeline(ws, window.viewport());
            chat.setOnTurnStarting(() -> {
                if (!timeline.hasSnapshots()) timeline.snapshot("Before assistant");
            });
            chat.setOnTurnFinished(timeline::snapshot);
            window.setRightPanel(chat);
            window.setCenterBottom(timeline);
        } else {
            window.setRightPanel(ComingSoonPanel.assistant());
        }
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
