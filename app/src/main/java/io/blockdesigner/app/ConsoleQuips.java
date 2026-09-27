package io.blockdesigner.app;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Now and then, after something happens, the console adds a line of its own: Minecraft, film and TV references and
 * some Gen Z slang, dressed up as log output (the jokes are for programmers). Never more than one every
 * {@value #GAP_MS} ms, and only on some events, so they stay rare. Logged at DEBUG level, so they are grey and a
 * "Warnings & errors" filter hides them.
 */
public final class ConsoleQuips {
    public enum Event {START, ASSETS, SAVE, OPEN, IMPORT, EXPORT, ERROR, UNDO, REDO, COMMAND, BIG_COMMAND, HELP, PLUGIN, CLEAR}

    /** Each line is "source|text". */
    private static final Map<Event, List<String>> LINES = Map.ofEntries(
            Map.entry(Event.START, List.of(
                    "worldgen|changelog: Removed Herobrine",
                    "matrix|Wake up, Neo… the JVM has you. Follow the white rabbit.",
                    "main|while (!hasWoodenPickaxe()) punch(tree);",
                    "server|[Server thread/INFO]: Herobrine joined the game",
                    "gc|heap looking healthy. no OutOfMemoryError today, bestie",
                    "boot|JIT warmed up. we are so back",
                    "init|public static void main(String[] args) // it's giving main character energy")),
            Map.entry(Event.ASSETS, List.of(
                    "textures|CSI: \"Enhance!\"  renderer: it's 16×16, chief",
                    "textures|atlas stitched. 16×16 is all you need",
                    "textures|mipmaps generated. the pixels are pixeling, no cap",
                    "assets|jar unzipped. Notch would be proud (probably)")),
            Map.entry(Event.SAVE, List.of(
                    "io|Saving chunks… do not turn off your Xbox 360",
                    "io|fsync() returned 0. saved, slay",
                    "io|serialized to disk. Ctrl+S muscle memory stays undefeated",
                    "io|written. \"I'll be back.\"  — T-800, and also this file")),
            Map.entry(Event.OPEN, List.of(
                    "io|project deserialized. It's alive! IT'S ALIVE!",
                    "io|readObject() ok. welcome back, the build ate while you were gone")),
            Map.entry(Event.IMPORT, List.of(
                    "nbt|NBT parsed. big-endian, as Notch intended",
                    "nbt|It's a UNIX system! I know this!",
                    "nbt|palette decoded. that schematic is lowkey fire",
                    "nbt|TAG_Compound unpacked. no gzip bombs detected, we good")),
            Map.entry(Event.EXPORT, List.of(
                    "nbt|Beam me up: structure serialized to NBT",
                    "nbt|exported. DataVersion checked twice, like Santa",
                    "nbt|one does not simply paste this in 1.12",
                    "nbt|shipped it. that build is lit, fr fr")),
            Map.entry(Event.ERROR, List.of(
                    "hal9000|I'm sorry, Dave. I'm afraid I can't do that.",
                    "apollo13|Houston, we have a problem.",
                    "itcrowd|Hello, IT. Have you tried turning it off and on again?",
                    "creeper|catch (Creeper c) { /* aww man */ }",
                    "jvm|stack trace above. it's so over. we're cooked",
                    "jvm|this exception is being extra rn",
                    "jvm|not a bug, it's a feature. (it's a bug) (ratio)",
                    "jedi|This is not the null you're looking for.")),
            Map.entry(Event.UNDO, List.of(
                    "undo|88 mph reached. rolling back one timeline",
                    "undo|Ctrl+Z: the real totem of undying",
                    "undo|git reset --hard HEAD~1, but make it cute",
                    "undo|that edit? it's giving mistake. reverted")),
            Map.entry(Event.REDO, List.of(
                    "redo|Doc, we have to go back… to the future!",
                    "redo|redo stack popped. and we're so back")),
            Map.entry(Event.COMMAND, List.of(
                    "blockedit|//wand not required. we're built different",
                    "blockedit|command ran in O(n). n is your blocks. sorry",
                    "blockedit|sudo make me a castle",
                    "blockedit|that command understood the assignment")),
            Map.entry(Event.BIG_COMMAND, List.of(
                    "blockedit|You're gonna need a bigger boat.",
                    "blockedit|vanilla /fill caps at 32768 blocks. couldn't be us",
                    "blockedit|that's a lot of blocks. GPU said \"bet\"",
                    "blockedit|I am inevitable. *snap*")),
            Map.entry(Event.HELP, List.of(
                    "advancements|Advancement made! [RTFM]",
                    "man|man blockedit: No manual entry. but we got you")),
            Map.entry(Event.PLUGIN, List.of(
                    "classloader|\"I know kung fu.\"",
                    "classloader|new jar just dropped",
                    "classloader|plugin loaded. the vibes are immaculate")),
            Map.entry(Event.CLEAR, List.of(
                    "mib|*neuralyzer flash* you never saw those logs",
                    "console|history cleared. no thoughts, head empty")));

    public static final long GAP_MS = 45_000;
    private static final double CHANCE = 0.3;
    private static final ThreadLocal<Boolean> emitting = ThreadLocal.withInitial(() -> false);
    private static ConsoleQuips instance;

    private final Random random;
    private final LongSupplier clock;
    private long last = Long.MIN_VALUE / 2;

    ConsoleQuips(Random random, LongSupplier clock) {
        this.random = random;
        this.clock = clock;
    }

    /** Starts listening to the log. Called once, after {@link ConsoleLog#install()}. */
    public static synchronized void install() {
        if (instance != null) return;
        instance = new ConsoleQuips(new Random(), System::currentTimeMillis);
        ConsoleLog.addListener(e -> {
            Event ev = classify(e);
            if (ev != null) event(ev);
        });
        event(Event.START);
    }

    /** Something happened that the log doesn't show by itself (undo, redo, clearing the console). */
    public static void event(Event ev) {
        ConsoleQuips q;
        synchronized (ConsoleQuips.class) {
            q = instance;
        }
        if (q == null || emitting.get()) return;
        String line = q.pick(ev);
        if (line == null) return;
        int bar = line.indexOf('|');
        emitting.set(true);
        try {
            ConsoleLog.add(ConsoleLog.Level.DEBUG, line.substring(0, bar), line.substring(bar + 1));
        } finally {
            emitting.set(false);
        }
    }

    /** A line for this event, or null when it's too soon since the last one or the dice say no. */
    synchronized String pick(Event ev) {
        List<String> lines = LINES.get(ev);
        if (lines == null) return null;
        long now = clock.getAsLong();
        if (now - last < GAP_MS) return null;
        // Errors and big commands are funnier more often; the rest stay rare.
        double chance = ev == Event.ERROR || ev == Event.BIG_COMMAND || ev == Event.START ? CHANCE * 2 : CHANCE;
        if (random.nextDouble() >= chance) return null;
        last = now;
        return lines.get(random.nextInt(lines.size()));
    }

    /** Which event a log line stands for, if any. */
    static Event classify(ConsoleLog.Entry e) {
        if (e.level() == ConsoleLog.Level.DEBUG) return null;
        if (e.level() == ConsoleLog.Level.ERROR) return Event.ERROR;
        String t = e.text();
        return switch (e.source()) {
            case "status" -> t.startsWith("Ready · ") ? Event.ASSETS
                    : t.startsWith("Saved ") ? Event.SAVE
                    : t.startsWith("Opened ") ? Event.OPEN
                    : t.startsWith("Imported ") || t.startsWith("Added ") ? Event.IMPORT
                    : t.startsWith("Exported ") ? Event.EXPORT : null;
            case "command" -> {
                if (t.startsWith("/")) yield t.toLowerCase(Locale.ROOT).startsWith("/help") ? Event.HELP : null;
                yield bigChange(t) ? Event.BIG_COMMAND : Event.COMMAND;
            }
            default -> t.startsWith("Enabled · ") ? Event.PLUGIN : null;
        };
    }

    /** A command result naming more than 100,000 blocks, e.g. "Changed 250,000 blocks". */
    private static boolean bigChange(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d[\\d,]*)\\s+blocks?").matcher(t);
        while (m.find()) {
            try {
                if (Long.parseLong(m.group(1).replace(",", "")) > 100_000) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }
}
