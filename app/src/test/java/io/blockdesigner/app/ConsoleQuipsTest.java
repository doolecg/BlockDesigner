package io.blockdesigner.app;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsoleQuipsTest {
    private static ConsoleLog.Entry entry(ConsoleLog.Level level, String source, String text) {
        return new ConsoleLog.Entry(LocalTime.NOON, level, source, text);
    }

    @Test
    void classifiesWhatHappened() {
        assertEquals(ConsoleQuips.Event.SAVE, ConsoleQuips.classify(entry(ConsoleLog.Level.INFO, "status", "Saved castle.bdproj")));
        assertEquals(ConsoleQuips.Event.ERROR, ConsoleQuips.classify(entry(ConsoleLog.Level.ERROR, "stderr", "boom")));
        assertEquals(ConsoleQuips.Event.HELP, ConsoleQuips.classify(entry(ConsoleLog.Level.INFO, "command", "/help")));
        assertNull(ConsoleQuips.classify(entry(ConsoleLog.Level.INFO, "command", "/set stone")));
        assertEquals(ConsoleQuips.Event.COMMAND, ConsoleQuips.classify(entry(ConsoleLog.Level.INFO, "command", "Changed 12 blocks")));
        assertEquals(ConsoleQuips.Event.BIG_COMMAND, ConsoleQuips.classify(entry(ConsoleLog.Level.INFO, "command", "Changed 250,000 blocks")));
        assertEquals(ConsoleQuips.Event.PLUGIN, ConsoleQuips.classify(entry(ConsoleLog.Level.INFO, "Resource Tracker", "Enabled · 1 panel")));
        // Its own lines never set it off again.
        assertNull(ConsoleQuips.classify(entry(ConsoleLog.Level.DEBUG, "jvm", "we're cooked")));
    }

    @Test
    void staysRare() {
        long[] now = {1_000_000};
        // A Random that always says yes: only the gap holds it back.
        ConsoleQuips q = new ConsoleQuips(new Random() {
            @Override
            public double nextDouble() {
                return 0;
            }
        }, () -> now[0]);
        assertNotNull(q.pick(ConsoleQuips.Event.SAVE));
        assertNull(q.pick(ConsoleQuips.Event.ERROR), "too soon after the last one");
        now[0] += ConsoleQuips.GAP_MS;
        assertNotNull(q.pick(ConsoleQuips.Event.ERROR));
        // And when the dice say no, nothing.
        ConsoleQuips never = new ConsoleQuips(new Random() {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        }, () -> now[0]);
        assertNull(never.pick(ConsoleQuips.Event.ERROR));
    }
}
