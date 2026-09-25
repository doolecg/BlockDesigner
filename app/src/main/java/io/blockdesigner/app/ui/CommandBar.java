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
 * Minecraft-chat-style WorldEdit command line along the bottom of the viewport ("/" opens it). Shows matching commands
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

    CommandBar(Consumer<String> onRun) {
        this.onRun = onRun;
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
        field.setPromptText("//set stone · //help");
        getChildren().addAll(hint, result, field);
        setVisible(false);

        field.textProperty().addListener((o, a, b) -> updateHint());
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        field.focusedProperty().addListener((o, a, focused) -> {
            if (!focused && isVisible() && field.getText().replace("/", "").isBlank()) close();
        });
        // Clicks on the bar stay here instead of reaching the viewport underneath.
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
    }

    void open() {
        setVisible(true);
        result.setVisible(false);
        result.setManaged(false);
        if (field.getText().isBlank()) field.setText("//");
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
        result.setText(message);
        result.getStyleClass().removeAll("error");
        if (!ok) result.getStyleClass().add("error");
        result.setVisible(!ok);
        result.setManaged(!ok);
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
        field.setText(historyAt < 0 ? "//" : history.get(historyAt));
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
        return WorldEdit.COMMANDS.stream().filter(c -> c.name().startsWith(n)).toList();
    }

    /** Tab: completes the command name when only one fits (or their common start). */
    private void complete() {
        String t = field.getText().stripLeading();
        if (t.replace("/", "").contains(" ")) return;
        List<WorldEdit.Command> m = matches();
        if (m.isEmpty()) return;
        String common = m.getFirst().name();
        for (WorldEdit.Command c : m) {
            int i = 0;
            while (i < common.length() && i < c.name().length() && common.charAt(i) == c.name().charAt(i)) i++;
            common = common.substring(0, i);
        }
        field.setText("//" + common + (m.size() == 1 ? " " : ""));
        field.end();
    }

    private void updateHint() {
        String n = typedName();
        boolean typingArgs = field.getText().replace("/", "").contains(" ");
        List<WorldEdit.Command> m = matches();
        if (m.isEmpty()) {
            hint.setText("No command starts with \"" + n + "\" · //help lists them");
            return;
        }
        WorldEdit.Command exact = m.stream().filter(c -> c.name().equals(n)).findFirst().orElse(null);
        if (exact != null && (typingArgs || m.size() == 1)) {
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
