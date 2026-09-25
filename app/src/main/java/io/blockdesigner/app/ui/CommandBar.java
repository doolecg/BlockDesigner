package io.blockdesigner.app.ui;

import io.blockdesigner.core.worldedit.WorldEdit;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Minecraft-chat-style WorldEdit command line along the bottom of the viewport (T or "/" opens it, like Minecraft's chat). Shows matching commands
 * with their usage as you type; Tab completes, ↑ ↓ walk the history, Enter runs, Esc closes. A failed command keeps
 * the bar open with the error so it can be fixed.
 */
final class CommandBar extends VBox {
    private final TextField field = new TextField();
    private final Label hint = new Label(), result = new Label();
    private final Consumer<String> onRun;
    private final List<String> history = new ArrayList<>();
    private int historyAt = -1;
    private boolean lastOk = true;

    private final java.util.function.Supplier<List<String>> blocks;
    /** Chat-style scrollback: the last few commands and what they said. */
    private final VBox log = new VBox(1);
    private static final int LOG_LINES = 8;
    /** The key that opened the bar also sends a typed character; it must not land in the field. */
    private boolean swallowTyped;
    private final javafx.animation.PauseTransition swallowTimeout = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));

    /** @param blocks block ids for completing patterns (vanilla ones without "minecraft:") */
    CommandBar(Consumer<String> onRun, java.util.function.Supplier<List<String>> blocks) {
        this.onRun = onRun;
        this.blocks = blocks;
        getStyleClass().add("command-bar");
        setSpacing(4);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setPrefWidth(600);
        hint.getStyleClass().add("command-hint");
        hint.setWrapText(true);
        result.getStyleClass().add("command-result");
        result.setWrapText(true);
        result.setManaged(false);
        result.setVisible(false);
        field.getStyleClass().add("command-field");
        field.setPromptText("/set stone · /help");
        log.getStyleClass().add("command-log");
        getChildren().addAll(log, hint, result, field);
        setVisible(false);
        swallowTimeout.setOnFinished(e -> swallowTyped = false);
        field.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (swallowTyped) {
                swallowTyped = false;
                e.consume();
            }
        });

        field.textProperty().addListener((o, a, b) -> updateHint());
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        field.focusedProperty().addListener((o, a, focused) -> {
            if (!focused && isVisible() && field.getText().replace("/", "").isBlank()) close();
        });
        // Clicks on the bar stay here instead of reaching the viewport underneath.
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
    }

    /** Opens like Minecraft's chat: with "/" already typed, and without the opening key's own character. */
    void open() {
        swallowTyped = true;
        swallowTimeout.playFromStart();
        setVisible(true);
        result.setVisible(false);
        result.setManaged(false);
        if (field.getText().isBlank()) field.setText("/");
        field.requestFocus();
        field.end();
        historyAt = -1;
        updateHint();
    }

    void close() {
        setVisible(false);
        field.clear();
        if (getParent() != null) getParent().requestFocus();
    }

    /** Called by the runner with the command's outcome. */
    void showResult(boolean ok, String message) {
        lastOk = ok;
        addLog(ok, message);
        result.setText(message);
        result.getStyleClass().removeAll("error");
        if (!ok) result.getStyleClass().add("error");
        result.setVisible(!ok);
        result.setManaged(!ok);
    }

    /** Adds a line to the scrollback (the command itself is echoed first, like chat). */
    private void addLog(boolean ok, String message) {
        String cmd = history.isEmpty() ? "" : history.getFirst();
        if (!cmd.isEmpty()) {
            Label c = new Label(cmd);
            c.getStyleClass().add("command-log-command");
            log.getChildren().add(c);
        }
        Label l = new Label(message);
        l.setWrapText(true);
        l.getStyleClass().add(ok ? "command-log-line" : "command-log-error");
        log.getChildren().add(l);
        while (log.getChildren().size() > LOG_LINES * 2) log.getChildren().removeFirst();
    }

    private void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case ENTER -> {
                String line = field.getText().strip();
                if (line.replace("/", "").isBlank()) {
                    close();
                } else {
                    history.remove(line);
                    history.addFirst(line);
                    if (history.size() > 100) history.removeLast();
                    lastOk = true;
                    onRun.accept(line);
                    if (lastOk) close();
                    else field.selectAll();
                }
            }
            case ESCAPE -> close();
            case TAB -> complete();
            case UP -> walkHistory(1);
            case DOWN -> walkHistory(-1);
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void walkHistory(int step) {
        if (history.isEmpty()) return;
        historyAt = Math.clamp(historyAt + step, -1, history.size() - 1);
        field.setText(historyAt < 0 ? "/" : history.get(historyAt));
        field.end();
    }

    private String typedName() {
        String t = field.getText().stripLeading();
        while (t.startsWith("/")) t = t.substring(1);
        int sp = t.indexOf(' ');
        return (sp < 0 ? t : t.substring(0, sp)).toLowerCase(Locale.ROOT);
    }

    private List<WorldEdit.Command> matches() {
        String n = typedName();
        return WorldEdit.commands().stream().filter(c -> c.name().startsWith(n)).toList();
    }

    /** The block name being typed: the end of the text after the last space, comma or percent sign. */
    private String blockPrefix() {
        String t = field.getText();
        int cut = Math.max(t.lastIndexOf(' '), Math.max(t.lastIndexOf(','), t.lastIndexOf('%')));
        return t.substring(cut + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> blockMatches(String prefix) {
        if (prefix.isEmpty() || prefix.contains("[") || Character.isDigit(prefix.charAt(0)) || prefix.startsWith("-")) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : blocks.get()) if (id.startsWith(prefix)) out.add(id);
        java.util.Collections.sort(out);
        return out;
    }

    /** Tab: completes the command name, or (in the arguments) the block name, as far as the matches agree. */
    private void complete() {
        String t = field.getText().stripLeading();
        if (t.replace("/", "").contains(" ")) {
            String prefix = blockPrefix();
            List<String> m = blockMatches(prefix);
            if (m.isEmpty()) return;
            String common = m.getFirst();
            for (String id : m) {
                int i = 0;
                while (i < common.length() && i < id.length() && common.charAt(i) == id.charAt(i)) i++;
                common = common.substring(0, i);
            }
            String text = field.getText();
            field.setText(text.substring(0, text.length() - prefix.length()) + common);
            field.end();
            return;
        }
        List<WorldEdit.Command> m = matches();
        if (m.isEmpty()) return;
        String common = m.getFirst().name();
        for (WorldEdit.Command c : m) {
            int i = 0;
            while (i < common.length() && i < c.name().length() && common.charAt(i) == c.name().charAt(i)) i++;
            common = common.substring(0, i);
        }
        field.setText("/" + common + (m.size() == 1 ? " " : ""));
        field.end();
    }

    private void updateHint() {
        String n = typedName();
        boolean typingArgs = field.getText().replace("/", "").contains(" ");
        List<WorldEdit.Command> m = matches();
        if (m.isEmpty()) {
            hint.setText("No command starts with \"" + n + "\" · /help lists them");
            return;
        }
        WorldEdit.Command exact = m.stream().filter(c -> c.name().equals(n)).findFirst().orElse(null);
        if (exact != null && (typingArgs || m.size() == 1)) {
            // While typing a block name, list the blocks it could be.
            List<String> bm = typingArgs ? blockMatches(blockPrefix()) : List.of();
            if (!bm.isEmpty() && !(bm.size() == 1 && bm.getFirst().equals(blockPrefix()))) {
                hint.setText(String.join("   ", bm.subList(0, Math.min(8, bm.size()))) + (bm.size() > 8 ? "   … (" + bm.size() + ")" : "") + "   · Tab completes");
                return;
            }
            hint.setText(exact.usage() + "  —  " + exact.description());
            return;
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (WorldEdit.Command c : m) {
            if (shown++ == 8) {
                sb.append("  …");
                break;
            }
            if (!sb.isEmpty()) sb.append("   ");
            sb.append(c.usage());
        }
        hint.setText(sb + (n.isEmpty() ? "" : "   · Tab completes"));
    }
}
