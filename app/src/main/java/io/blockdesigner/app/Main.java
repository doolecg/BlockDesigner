package io.blockdesigner.app;

import javafx.application.Application;

/** Launcher; kept separate from the {@link Application} subclass so JavaFX works from the classpath. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Application.launch(BlockDesignerApp.class, args);
    }
}
