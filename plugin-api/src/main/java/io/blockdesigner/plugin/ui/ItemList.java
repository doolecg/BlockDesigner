package io.blockdesigner.plugin.ui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A list in the app's look whose rows fit the width (long names get an ellipsis, never a sideways scroll bar). Give it
 * a function from item to {@link ItemRow}, or your own cell factory for richer rows. It is virtualised, so thousands
 * of items are fine. Either make it a page's {@code grow} node (it takes the free height) or give it
 * {@link #visibleRows} so it is as tall as its rows. Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class ItemList<T> extends ListView<T> {
    private int minRows = -1, maxRows = -1;
    private double rowHeight = -1;
    private Consumer<T> open, delete;
    private Function<T, List<MenuItem>> menu;

    /** A list for your own cell factory (cells should set {@code setPrefWidth(0)} so they fit the width). */
    public ItemList() {
        Theme.attach(this);
        getStyleClass().add("bd-list");
        setMinWidth(0);
        setOnKeyPressed(e -> {
            T item = getSelectionModel().getSelectedItem();
            if (item == null) return;
            if (e.getCode() == KeyCode.ENTER && open != null) {
                open.accept(item);
                e.consume();
            } else if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && delete != null) {
                delete.accept(item);
                e.consume();
            }
        });
        getItems().addListener((ListChangeListener<T>) c -> fitRows());
        itemsProperty().addListener((o, a, b) -> {
            if (b != null) b.addListener((ListChangeListener<T>) c -> fitRows());
            fitRows();
        });
    }

    /** A list whose rows are drawn by {@code rows}. */
    public ItemList(Function<T, ItemRow> rows) {
        this();
        setCellFactory(v -> new ListCell<>() {
            {
                setPrefWidth(0);
                getStyleClass().add("bd-list-cell");
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : rows.apply(item).node());
                wire(this, empty ? null : item);
            }
        });
    }

    /**
     * Double-click, Enter and the context menu for a cell of your own factory: call from its {@code updateItem}
     * (the {@link #ItemList(Function) row} cells do it themselves).
     */
    public void wire(ListCell<T> cell, T item) {
        if (item == null) {
            cell.setOnMouseClicked(null);
            cell.setContextMenu(null);
            return;
        }
        cell.setOnMouseClicked(e -> {
            if (open != null && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) open.accept(item);
        });
        if (menu != null) {
            List<MenuItem> items = menu.apply(item);
            cell.setContextMenu(items == null || items.isEmpty() ? null : new ContextMenu(items.toArray(MenuItem[]::new)));
        }
    }

    /** What the list shows while it has no items. */
    public ItemList<T> empty(EmptyState placeholder) {
        setPlaceholder(placeholder);
        return this;
    }

    /**
     * Makes the list as tall as its rows, between {@code min} and {@code max} rows (it scrolls past that). The height
     * comes from the rows as drawn, so it fits rows of any size.
     */
    public ItemList<T> visibleRows(int min, int max) {
        if (min < 0 || max < Math.max(1, min)) throw new IllegalArgumentException("visibleRows(" + min + ", " + max + ")");
        minRows = min;
        maxRows = max;
        fitRows();
        return this;
    }

    /** Runs on Enter or a double-click on an item. */
    public ItemList<T> onOpen(Consumer<T> open) {
        this.open = open;
        return this;
    }

    /** Runs on the Delete key over the selected item (ask first for anything that can't be undone). */
    public ItemList<T> onDelete(Consumer<T> delete) {
        this.delete = delete;
        return this;
    }

    /** A right-click menu per item. */
    public ItemList<T> menu(Function<T, List<MenuItem>> menu) {
        this.menu = menu;
        return this;
    }

    private void fitRows() {
        if (minRows < 0) return;
        double row = rowHeight > 0 ? rowHeight : Theme.ROW + 8;
        int n = Math.clamp(getItems() == null ? 0 : getItems().size(), Math.max(1, minRows), maxRows);
        double h = snappedTopInset() + snappedBottomInset() + n * row + 2;
        if (Math.abs(getPrefHeight() - h) > 0.5) {
            setPrefHeight(h);
            setMinHeight(snappedTopInset() + snappedBottomInset() + Math.max(1, minRows) * row + 2);
            setMaxHeight(h);
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (minRows < 0) return;
        // Measure the rows as drawn (the tallest filled one) and fit the list to them.
        double tallest = 0;
        for (Node n : lookupAll(".list-cell")) {
            if (n instanceof ListCell<?> c && !c.isEmpty() && c.isVisible()) tallest = Math.max(tallest, c.getHeight());
        }
        if (tallest > 0 && Math.abs(tallest - rowHeight) > 0.5) {
            rowHeight = tallest;
            fitRows();
        }
    }
}
