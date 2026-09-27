package io.blockdesigner.plugin.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The frame of a plugin page: an optional sticky {@link #top} (a scope row, a preview), the content column (sections,
 * scrolling as one), and an optional sticky {@link #footer} (a banner, progress, the {@link ActionBar}). It owns the
 * page's padding and spacing, so a panel that returns one from {@code PluginPanel.create} lines up with every other
 * plugin; the app adds no padding of its own around it.
 *
 * <pre>{@code
 * PanelScaffold page = new PanelScaffold()
 *         .add(new Section("Count", scopeForm), new Section("Progress", bar, totals))
 *         .grow(new Section("Items").grow(list))          // a list takes the free height instead of scrolling
 *         .footer(new ActionBar(copy, save));
 * }</pre>
 *
 * Below {@link Theme#NARROW} px it sets {@code :narrow} on itself (see {@link #narrowProperty}). Since API 6.
 */
@SuppressWarnings("this-escape") // components set themselves up in their constructors, like JavaFX controls
public class PanelScaffold extends BorderPane {
    private static final PseudoClass NARROW = PseudoClass.getPseudoClass("narrow");

    private final VBox top = new VBox();
    private final VBox content = new VBox();
    private final VBox footer = new VBox();
    private final ScrollPane scroll = new ScrollPane(content);
    private final BooleanProperty showEmpty = new SimpleBooleanProperty(this, "showEmpty");
    private final ReadOnlyBooleanWrapper narrow = new ReadOnlyBooleanWrapper(this, "narrow");
    private EmptyState empty;
    private boolean scrolling = true;

    public PanelScaffold() {
        Theme.attach(this);
        getStyleClass().add("bd-scaffold");
        top.getStyleClass().add("bd-top");
        content.getStyleClass().add("bd-content");
        footer.getStyleClass().add("bd-footer");
        scroll.getStyleClass().addAll("bd-scroll", "edge-to-edge");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Controls.show(top, false);
        Controls.show(footer, false);
        setTop(top);
        setBottom(footer);
        setCenter(scroll);
        setMinWidth(0);
        widthProperty().addListener((o, a, w) -> narrow.set(w.doubleValue() > 0 && w.doubleValue() < Theme.NARROW));
        narrow.addListener((o, a, b) -> pseudoClassStateChanged(NARROW, b));
        showEmpty.addListener((o, a, b) -> updateCenter());
    }

    /** Adds nodes above the content that stay put while it scrolls: a scope row, a preview. */
    public PanelScaffold top(Node... nodes) {
        top.getChildren().addAll(nodes);
        Controls.show(top, !top.getChildren().isEmpty());
        return this;
    }

    /** Adds sections (or any nodes) to the content column, 16 px apart. */
    public PanelScaffold add(Node... sections) {
        content.getChildren().addAll(sections);
        return this;
    }

    /**
     * Adds a node that takes the page's free height, such as a list or a chat transcript: the content then stops
     * scrolling as a whole (the node scrolls itself). Call it once per page.
     */
    public PanelScaffold grow(Node node) {
        VBox.setVgrow(node, Priority.ALWAYS);
        content.getChildren().add(node);
        scrolling = false;
        scroll.setContent(null);
        updateCenter();
        return this;
    }

    /** Adds nodes below the content that stay put: a {@link Banner}, progress, an {@link ActionBar}. */
    public PanelScaffold footer(Node... nodes) {
        footer.getChildren().addAll(nodes);
        Controls.show(footer, !footer.getChildren().isEmpty());
        return this;
    }

    /** What the page shows instead of its content while {@link #showEmptyProperty} is true. */
    public PanelScaffold empty(EmptyState state) {
        this.empty = state;
        updateCenter();
        return this;
    }

    /** True shows the {@link #empty} state in place of the content (the top and footer stay). */
    public BooleanProperty showEmptyProperty() {
        return showEmpty;
    }

    /** True while the page is narrower than {@link Theme#NARROW} px. */
    public ReadOnlyBooleanProperty narrowProperty() {
        return narrow.getReadOnlyProperty();
    }

    public boolean isNarrow() {
        return narrow.get();
    }

    private void updateCenter() {
        Node want;
        if (showEmpty.get() && empty != null) want = empty;
        else if (scrolling) {
            if (scroll.getContent() != content) scroll.setContent(content);
            want = scroll;
        } else want = content;
        if (getCenter() != want) setCenter(want);
    }
}
