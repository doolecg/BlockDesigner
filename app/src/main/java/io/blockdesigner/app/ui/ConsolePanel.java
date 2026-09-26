package io.blockdesigner.app.ui;

import io.blockdesigner.app.ConsoleLog;
import io.blockdesigner.app.Settings;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The console, docked above the status bar and opened from the button at the very bottom left: everything in
 * {@link ConsoleLog} (app output, errors, status messages, plugin logs, WorldEdit commands), filterable by level and text.
 * The line at the bottom runs WorldEdit commands, plus a few of its own (help, clear, copy).
 */
final class ConsolePanel extends VBox {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final double MIN_HEIGHT = 110;

    private final Settings settings;
    private final Consumer<String> runCommand;
    private final ObservableList<ConsoleLog.Entry> shown = FXCollections.observableArrayList();
    private final ListView<ConsoleLog.Entry> list = new ListView<>(shown);
    private final ComboBox<String> level = new ComboBox<>(FXCollections.observableArrayList("Everything", "Warnings & errors", "Errors"));
    private final TextField search = new TextField();
    private final TextField input = new TextField();
    private final Label count = new Label();
    private final List<String> history = new ArrayList<>();
    private int historyAt = -1;
    /** Errors logged while the console was closed, for the badge on its button. */
    private final IntegerProperty unseenErrors = new SimpleIntegerProperty();
    private boolean refreshQueued;
    private double dragStartY, dragStartHeight;

    ConsolePanel(Settings settings, Consumer<String> runCommand) {
        this.settings = settings;
        this.runCommand = runCommand;
        getStyleClass().add("console-panel");
        setPrefHeight(Math.max(MIN_HEIGHT, settings.consoleHeight));
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        // Drag the top edge to resize.
        Region grip = new Region();
        grip.getStyleClass().add("console-grip");
        grip.setMinHeight(5);
        grip.setCursor(Cursor.N_RESIZE);
        grip.setOnMousePressed(e -> {
            dragStartY = e.getScreenY();
            dragStartHeight = getPrefHeight();
        });
        grip.setOnMouseDragged(e -> {
            double max = getScene() == null ? 900 : getScene().getHeight() - 200;
            setPrefHeight(Math.max(MIN_HEIGHT, Math.min(max, dragStartHeight + dragStartY - e.getScreenY())));
        });
        grip.setOnMouseReleased(e -> settings.consoleHeight = getPrefHeight());

        Label title = new Label("Console", new FontIcon(Feather.TERMINAL));
        title.getStyleClass().add("console-title");
        level.getSelectionModel().select(0);
        level.valueProperty().addListener((o, a, b) -> refresh());
        search.setPromptText("Filter");
        search.getStyleClass().add("console-search");
        search.setPrefWidth(200);
        search.textProperty().addListener((o, a, b) -> refresh());
        count.getStyleClass().add("console-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button copy = iconButton(Feather.COPY, "Copy the lines shown (or the selected ones)", this::copy);
        Button clear = iconButton(Feather.TRASH_2, "Clear the console", this::clearLog);
        Button close = iconButton(Feather.X, "Close the console", this::close);
        HBox header = new HBox(8, title, level, search, count, spacer, copy, clear, close);
        header.getStyleClass().add("console-header");
        header.setAlignment(Pos.CENTER_LEFT);

        list.getStyleClass().add("console-list");
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(v -> new EntryCell());
        list.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
                copy();
                e.consume();
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        input.getStyleClass().addAll("console-input", "command-field");
        input.setPromptText("/set stone · /help · clear");
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::inputKey);
        Label prompt = new Label(">");
        prompt.getStyleClass().add("console-prompt");
        HBox inputRow = new HBox(6, prompt, input);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.getStyleClass().add("console-input-row");
        HBox.setHgrow(input, Priority.ALWAYS);

        getChildren().addAll(grip, header, list, inputRow);
        setVisible(false);
        setManaged(false);

        ConsoleLog.addListener(e -> {
            if (e.level() == ConsoleLog.Level.ERROR) Platform.runLater(() -> {
                if (!isVisible()) unseenErrors.set(unseenErrors.get() + 1);
            });
            queueRefresh();
        });
        refresh();
    }

    IntegerProperty unseenErrorsProperty() {
        return unseenErrors;
    }

    boolean isOpen() {
        return isVisible();
    }

    void toggle() {
        if (isVisible()) close();
        else open();
    }

    void open() {
        setVisible(true);
        setManaged(true);
        unseenErrors.set(0);
        refresh();
        scrollToEnd();
        input.requestFocus();
    }

    void close() {
        setVisible(false);
        setManaged(false);
        settings.consoleHeight = getPrefHeight();
    }

    private static Button iconButton(Feather icon, String tip, Runnable action) {
        Button b = new Button(null, new FontIcon(icon));
        b.getStyleClass().addAll("flat", "console-tool");
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** Log lines arrive in bursts and from any thread: redraw at most once per pulse. */
    private void queueRefresh() {
        synchronized (this) {
            if (refreshQueued) return;
            refreshQueued = true;
        }
        Platform.runLater(() -> {
            synchronized (this) {
                refreshQueued = false;
            }
            if (isVisible()) refresh();
        });
    }

    private void refresh() {
        boolean atEnd = isAtEnd();
        List<ConsoleLog.Entry> all = ConsoleLog.entries();
        int min = switch (level.getSelectionModel().getSelectedIndex()) {
            case 1 -> ConsoleLog.Level.WARN.ordinal();
            case 2 -> ConsoleLog.Level.ERROR.ordinal();
            default -> 0;
        };
        String q = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        List<ConsoleLog.Entry> keep = new ArrayList<>();
        for (ConsoleLog.Entry e : all) {
            if (e.level().ordinal() < min) continue;
            if (!q.isEmpty() && !e.text().toLowerCase(Locale.ROOT).contains(q) && !e.source().toLowerCase(Locale.ROOT).contains(q)) continue;
            keep.add(e);
        }
        shown.setAll(keep);
        count.setText(keep.size() == all.size() ? String.format("%,d lines", all.size()) : String.format("%,d of %,d lines", keep.size(), all.size()));
        if (atEnd) scrollToEnd();
    }

    /** Follow new lines only while the view is already at the bottom, so scrolling up to read isn't interrupted. */
    private boolean isAtEnd() {
        if (shown.isEmpty()) return true;
        var bar = list.lookupAll(".scroll-bar").stream()
                .filter(n -> n instanceof javafx.scene.control.ScrollBar s && s.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                .map(n -> (javafx.scene.control.ScrollBar) n).filter(javafx.scene.Node::isVisible).findFirst();
        return bar.isEmpty() || bar.get().getValue() >= bar.get().getMax() - 1e-3;
    }

    private void scrollToEnd() {
        if (!shown.isEmpty()) list.scrollTo(shown.size() - 1);
    }

    private void copy() {
        List<ConsoleLog.Entry> which = list.getSelectionModel().getSelectedItems().isEmpty() ? shown : list.getSelectionModel().getSelectedItems();
        StringBuilder sb = new StringBuilder();
        for (ConsoleLog.Entry e : which) sb.append(format(e)).append('\n');
        ClipboardContent c = new ClipboardContent();
        c.putString(sb.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
        ConsoleLog.info("console", "Copied " + which.size() + (which.size() == 1 ? " line" : " lines"));
    }

    private void clearLog() {
        ConsoleLog.clear();
        unseenErrors.set(0);
        io.blockdesigner.app.ConsoleQuips.event(io.blockdesigner.app.ConsoleQuips.Event.CLEAR);
        refresh();
    }

    static String format(ConsoleLog.Entry e) {
        return TIME.format(e.time()) + "  " + e.level() + "  [" + e.source() + "]  " + e.text();
    }

    private void inputKey(KeyEvent e) {
        switch (e.getCode()) {
            case ENTER -> {
                String line = input.getText().strip();
                input.clear();
                historyAt = -1;
                if (!line.isEmpty()) {
                    if (history.isEmpty() || !history.getLast().equals(line)) history.add(line);
                    run(line);
                }
                e.consume();
            }
            case UP, DOWN -> {
                if (history.isEmpty()) return;
                if (e.getCode() == KeyCode.UP) historyAt = historyAt < 0 ? history.size() - 1 : Math.max(0, historyAt - 1);
                else if (historyAt >= 0) historyAt = historyAt + 1 >= history.size() ? -1 : historyAt + 1;
                input.setText(historyAt < 0 ? "" : history.get(historyAt));
                input.end();
                e.consume();
            }
            case ESCAPE -> {
                close();
                e.consume();
            }
            case BACK_QUOTE -> {
                if (e.isShortcutDown()) {
                    close();
                    e.consume();
                }
            }
            default -> {
            }
        }
    }

    private void run(String line) {
        String cmd = line.startsWith("/") ? line : line.toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "clear", "cls" -> clearLog();
            case "copy" -> copy();
            case "help", "?" -> ConsoleLog.info("console", """
                    Commands: any WorldEdit command (/set, /replace, /walls, /copy, /paste… · /help lists them), \
                    clear, copy, errors (show only errors), all (show everything). \
                    ↑ ↓ walk the history, Esc closes, Ctrl+C copies the selected lines.""");
            case "errors" -> level.getSelectionModel().select(2);
            case "warnings" -> level.getSelectionModel().select(1);
            case "all" -> level.getSelectionModel().select(0);
            default -> runCommand.accept(line.startsWith("/") ? line : "/" + line);
        }
    }

    private static final class EntryCell extends ListCell<ConsoleLog.Entry> {
        private final javafx.scene.text.Text time = new javafx.scene.text.Text(), src = new javafx.scene.text.Text(), text = new javafx.scene.text.Text();
        private final javafx.scene.text.TextFlow row = new javafx.scene.text.TextFlow(time, src, text);

        EntryCell() {
            getStyleClass().add("console-cell");
            time.getStyleClass().addAll("console-mono", "console-time");
            src.getStyleClass().addAll("console-mono", "console-source");
            text.getStyleClass().addAll("console-mono", "console-text");
            // Wrap to the list's width rather than growing it sideways.
            listViewProperty().addListener((o, a, lv) -> {
                if (lv == null) return;
                prefWidthProperty().bind(lv.widthProperty().subtract(20));
                setMaxWidth(Region.USE_PREF_SIZE);
                row.prefWidthProperty().bind(lv.widthProperty().subtract(40));
            });
        }

        /** The text's height at the width it really gets, so wrapped lines don't leave gaps. */
        @Override
        protected double computePrefHeight(double width) {
            if (getGraphic() == null || getListView() == null) return super.computePrefHeight(width);
            double w = getListView().getWidth() - 40;
            return row.prefHeight(w) + snappedTopInset() + snappedBottomInset();
        }

        @Override
        protected void updateItem(ConsoleLog.Entry e, boolean empty) {
            super.updateItem(e, empty);
            text.getStyleClass().removeAll("debug", "info", "warn", "error");
            setText(null);
            if (empty || e == null) {
                setGraphic(null);
                return;
            }
            time.setText(TIME.format(e.time()) + "  ");
            src.setText(e.source() + "  ");
            text.setText(e.text());
            text.getStyleClass().add(e.level().name().toLowerCase(Locale.ROOT));
            setGraphic(row);
        }
    }
}
