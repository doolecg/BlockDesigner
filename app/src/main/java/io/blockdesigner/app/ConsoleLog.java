package io.blockdesigner.app;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Everything the app says, for the console (bottom left): standard out and error, java.util.logging, uncaught
 * exceptions, status-bar messages, plugin logs and BlockEdit commands. Keeps the last {@value #MAX} lines. Thread safe;
 * listeners are called on the thread that logged.
 */
public final class ConsoleLog {
    public enum Level {DEBUG, INFO, WARN, ERROR}

    public record Entry(LocalTime time, Level level, String source, String text) {
    }

    public static final int MAX = 5000;
    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private static final List<Consumer<Entry>> listeners = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private ConsoleLog() {
    }

    /** Starts capturing System.out / System.err, java.util.logging and uncaught exceptions. Safe to call twice. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        PrintStream realErr = System.err;
        System.setOut(tee(System.out, Level.INFO, "stdout"));
        System.setErr(tee(System.err, Level.WARN, "stderr"));
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        // Java's own console handler prints every log record to stderr, which the console also reads: the record
        // would show twice (and as an error). It writes to the real stderr instead; the handler below logs it once.
        for (Handler h : root.getHandlers()) {
            if (!(h instanceof java.util.logging.ConsoleHandler)) continue;
            root.removeHandler(h);
            java.util.logging.StreamHandler direct = new java.util.logging.StreamHandler(realErr, new java.util.logging.SimpleFormatter()) {
                @Override
                public synchronized void publish(LogRecord r) {
                    super.publish(r);
                    flush();
                }
            };
            direct.setLevel(h.getLevel());
            root.addHandler(direct);
        }
        root.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r == null) return;
                int v = r.getLevel().intValue();
                Level level = v >= java.util.logging.Level.SEVERE.intValue() ? Level.ERROR
                        : v >= java.util.logging.Level.WARNING.intValue() ? Level.WARN
                        : v >= java.util.logging.Level.INFO.intValue() ? Level.INFO : Level.DEBUG;
                String msg = r.getMessage();
                try {
                    if (msg != null && r.getParameters() != null) msg = java.text.MessageFormat.format(msg, r.getParameters());
                } catch (IllegalArgumentException ignored) {
                }
                if (r.getThrown() != null) msg = (msg == null ? "" : msg + "\n") + stackTrace(r.getThrown());
                String src = r.getLoggerName();
                // JavaFX warns about running from the class path on every start; it works fine this way.
                if (msg != null && msg.startsWith("Unsupported JavaFX configuration")) level = Level.DEBUG;
                add(level, src == null || src.isEmpty() ? "log" : src.substring(src.lastIndexOf('.') + 1), msg);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            add(Level.ERROR, t.getName(), "Uncaught: " + stackTrace(ex));
            if (previous != null) previous.uncaughtException(t, ex);
        });
        info("app", "BlockDesigner " + io.blockdesigner.app.update.Updater.currentVersion() + " · Java " + System.getProperty("java.version") + " · " + System.getProperty("os.name"));
    }

    public static void info(String source, String text) {
        add(Level.INFO, source, text);
    }

    public static void warn(String source, String text) {
        add(Level.WARN, source, text);
    }

    public static void error(String source, String text) {
        add(Level.ERROR, source, text);
    }

    public static void error(String source, String text, Throwable t) {
        add(Level.ERROR, source, text + "\n" + stackTrace(t));
    }

    public static void add(Level level, String source, String text) {
        if (text == null || text.isBlank()) return;
        Entry e = new Entry(LocalTime.now(), level, source, text.stripTrailing());
        synchronized (ConsoleLog.class) {
            entries.addLast(e);
            if (entries.size() > MAX) entries.removeFirst();
        }
        for (Consumer<Entry> l : listeners) {
            try {
                l.accept(e);
            } catch (RuntimeException ignored) {
                // A broken listener must not break logging (or loop back into it).
            }
        }
    }

    public static synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    public static synchronized void clear() {
        entries.clear();
    }

    public static void addListener(Consumer<Entry> l) {
        listeners.add(l);
    }

    public static void removeListener(Consumer<Entry> l) {
        listeners.remove(l);
    }

    public static String stackTrace(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString().stripTrailing();
    }

    /** Still writes to the real stream, and hands each whole line to the log. */
    private static PrintStream tee(PrintStream original, Level level, String source) {
        OutputStream line = new OutputStream() {
            private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) {
                original.write(b);
                if (b == '\n') flushLine();
                else if (b != '\r') buf.write(b);
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                original.write(b, off, len);
                for (int i = off; i < off + len; i++) {
                    if (b[i] == '\n') flushLine();
                    else if (b[i] != '\r') buf.write(b[i]);
                }
            }

            @Override
            public void flush() {
                original.flush();
            }

            private void flushLine() {
                String s = buf.toString(StandardCharsets.UTF_8);
                buf.reset();
                // Stack-trace lines join the entry above them, so one exception is one entry.
                if (s.startsWith("\tat ") || s.startsWith("\t... ") || s.startsWith("Caused by: ")) appendToLast(source, s);
                // Plain stderr lines are warnings; an exception or error line is an error.
                else add(level == Level.WARN && looksLikeError(s) ? Level.ERROR : level, source, s);
            }
        };
        return new PrintStream(line, true, StandardCharsets.UTF_8);
    }

    static boolean looksLikeError(String s) {
        return s.contains("Exception") || s.startsWith("Error") || s.contains("Error:") || s.startsWith("SEVERE");
    }

    private static void appendToLast(String source, String s) {
        Entry merged;
        synchronized (ConsoleLog.class) {
            Entry last = entries.peekLast();
            if (last == null || !last.source().equals(source)) {
                merged = null;
            } else {
                entries.removeLast();
                merged = new Entry(last.time(), last.level(), source, last.text() + "\n" + s);
                entries.addLast(merged);
            }
        }
        if (merged == null) add(Level.ERROR, source, s);
        else for (Consumer<Entry> l : listeners) {
            try {
                l.accept(merged);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
