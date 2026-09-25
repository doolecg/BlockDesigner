package io.blockdesigner.app.ui;

import io.blockdesigner.app.Workspace;
import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.model.BlockState;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Minecraft-style nine-slot hotbar along the bottom of the viewport. It records middle-click picks and blocks dragged
 * in from the block palette; click a slot (or press 1–9) to hold its block, Alt+C clears it.
 */
final class Hotbar extends HBox {
    /** Drag-and-drop payload prefix for block states dragged from the palette. */
    static final String DRAG_PREFIX = "blockdesigner-block:";
    private static final double SLOT = 44;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DROP = PseudoClass.getPseudoClass("drop-target");
    private static final PseudoClass SHUFFLE = PseudoClass.getPseudoClass("shuffle");
    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");
    // Mode toggles at the left of the bar: always shown, dim when off and lit when on, with a small label underneath.
    private final Label replaceBadge = toggle("Replace (R)", org.kordamp.ikonli.feather.Feather.REFRESH_CW);
    private final Label shuffleBadge = toggle("Shuffle (Z)", org.kordamp.ikonli.feather.Feather.SHUFFLE);

    private final Workspace ws;
    private final StackPane[] slots = new StackPane[Workspace.HOTBAR_SIZE];

    Hotbar(Workspace ws) {
        this.ws = ws;
        getStyleClass().add("hotbar");
        setSpacing(2);
        setAlignment(Pos.CENTER);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        for (int i = 0; i < slots.length; i++) {
            int index = i;
            StackPane p = new StackPane();
            p.getStyleClass().add("hotbar-slot");
            p.setMinSize(SLOT, SLOT);
            p.setPrefSize(SLOT, SLOT);
            p.setOnMouseClicked(e -> ws.selectHotbarSlot(index));
            p.setOnDragOver(e -> accept(e, p));
            p.setOnDragExited(e -> p.pseudoClassStateChanged(DROP, false));
            p.setOnDragDropped(e -> {
                BlockState st = dragged(e);
                if (st != null) ws.putInHotbar(index, st);
                e.setDropCompleted(st != null);
                e.consume();
            });
            slots[i] = p;
            getChildren().add(p);
        }
        shuffleBadge.setTooltip(new Tooltip("Shuffle (Z): placing picks a random block from the hotbar"));
        shuffleBadge.setOnMouseClicked(e -> ws.shuffleProperty().set(!ws.shuffleProperty().get()));
        getChildren().addFirst(shuffleBadge);
        replaceBadge.setTooltip(new Tooltip("Replace mode (R): right-click swaps the block you aim at for the held block, "
                + "keeping its facing and shape. Hold and drag to paint."));
        replaceBadge.setOnMouseClicked(e -> ws.replaceProperty().set(!ws.replaceProperty().get()));
        getChildren().addFirst(replaceBadge);
        ws.shuffleProperty().addListener((o, a, b) -> refresh());
        ws.replaceProperty().addListener((o, a, b) -> refresh());
        ws.hotbar().addListener((javafx.collections.ListChangeListener<BlockState>) c -> refresh());
        ws.hotbarSlotProperty().addListener((o, a, b) -> refresh());
        ws.assetsProperty().addListener((o, a, b) -> refresh());
        refresh();
    }

    private static Label toggle(String text, org.kordamp.ikonli.feather.Feather icon) {
        Label l = new Label(text, new org.kordamp.ikonli.javafx.FontIcon(icon));
        l.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        l.getStyleClass().add("hotbar-toggle");
        return l;
    }

    private static void accept(DragEvent e, StackPane p) {
        if (dragged(e) != null) {
            e.acceptTransferModes(TransferMode.COPY);
            p.pseudoClassStateChanged(DROP, true);
        }
        e.consume();
    }

    static BlockState dragged(DragEvent e) {
        String s = e.getDragboard().hasString() ? e.getDragboard().getString() : null;
        if (s == null || !s.startsWith(DRAG_PREFIX)) return null;
        try {
            return BlockState.parse(s.substring(DRAG_PREFIX.length()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void refresh() {
        BlockAssets assets = ws.assets();
        int selected = ws.hotbarSlotProperty().get();
        boolean shuffle = ws.shuffleProperty().get();
        shuffleBadge.pseudoClassStateChanged(ON, shuffle);
        replaceBadge.pseudoClassStateChanged(ON, ws.replaceProperty().get());
        for (int i = 0; i < slots.length; i++) {
            StackPane p = slots[i];
            p.getChildren().clear();
            BlockState st = ws.hotbar().get(i);
            // In shuffle mode every filled slot is in play.
            p.pseudoClassStateChanged(SELECTED, !shuffle && i == selected);
            p.pseudoClassStateChanged(SHUFFLE, shuffle && st != null);
            if (st != null) {
                if (assets != null) {
                    ImageView iv = new ImageView(BlockIcons.icon(assets, st));
                    iv.setFitWidth(SLOT - 10);
                    iv.setFitHeight(SLOT - 10);
                    iv.setSmooth(false);
                    p.getChildren().add(iv);
                } else {
                    Label l = new Label(BlockInfoHud.pretty(st.path()));
                    l.getStyleClass().add("hotbar-fallback");
                    p.getChildren().add(l);
                }
                Tooltip.install(p, new Tooltip(BlockInfoHud.pretty(st.path()) + "\n" + st));
            } else {
                Tooltip.install(p, new Tooltip("Empty · middle-click a block or drag one here from the palette"));
            }
            Label n = new Label(Integer.toString(i + 1));
            n.getStyleClass().add("hotbar-number");
            StackPane.setAlignment(n, Pos.BOTTOM_RIGHT);
            p.getChildren().add(n);
        }
    }
}
