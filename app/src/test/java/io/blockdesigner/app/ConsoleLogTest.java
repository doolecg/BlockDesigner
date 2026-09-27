package io.blockdesigner.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleLogTest {
    private static List<ConsoleLog.Entry> containing(String text) {
        return ConsoleLog.entries().stream().filter(e -> e.text().contains(text)).toList();
    }

    @Test
    void javaWarningsShowOnceAsWarnings() {
        ConsoleLog.install();
        java.util.logging.Logger.getLogger("io.example.Thing").warning("careful-7f3a");
        var hits = containing("careful-7f3a");
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().level()).isEqualTo(ConsoleLog.Level.WARN);
        assertThat(hits.getFirst().source()).isEqualTo("Thing");
    }

    @Test
    void stderrLinesAreWarningsUnlessTheyAreErrors() {
        ConsoleLog.install();
        System.err.println("plain-note-19c2");
        System.err.println("java.lang.IllegalStateException: broken-19c2");
        assertThat(containing("plain-note-19c2").getFirst().level()).isEqualTo(ConsoleLog.Level.WARN);
        assertThat(containing("broken-19c2").getFirst().level()).isEqualTo(ConsoleLog.Level.ERROR);
    }

    @Test
    void theJavaFxClassPathWarningIsQuiet() {
        ConsoleLog.install();
        java.util.logging.Logger.getLogger("javafx").warning("Unsupported JavaFX configuration: classes were loaded from 'unnamed module @1'");
        assertThat(containing("Unsupported JavaFX configuration: classes were loaded from 'unnamed module @1'"))
                .singleElement().extracting(ConsoleLog.Entry::level).isEqualTo(ConsoleLog.Level.DEBUG);
    }
}
