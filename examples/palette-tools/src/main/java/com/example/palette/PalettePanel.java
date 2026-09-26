package com.example.palette;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.plugin.PanelContext;
import io.blockdesigner.plugin.PluginContext;
import io.blockdesigner.plugin.PluginPanel;
import io.blockdesigner.plugin.SceneEvent;
import io.blockdesigner.plugin.Subscription;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lists the blocks of the selection (or of every visible layer when nothing is selected) with a colour swatch and a
 * count, most used first. Listens for scene events and only recounts while it is on screen.
 */
final class PalettePanel implements PluginPanel {
    private final List<Subscription> subscriptions = new ArrayList<>();
    // JavaFX nodes are only made in create(): the plugin is enabled before the panel is ever shown.
    private VBox rows;
    private Label heading;
    private PanelContext panel;
    private boolean stale = true;

    @Override
    public String id() {
        return "palette";
    }

    @Override
    public String title() {
        return "Palette";
    }

    @Override
    public String icon() {
        return "M8 2 C4.5 2 2 4.5 2 8 C2 11.5 4.5 14 8 14 C9 14 9.5 13.3 9.5 12.5 C9.5 11.5 10 11 11 11 H12 C13.2 11 14 10 14 8.5 "
                + "C14 4.8 11.3 2 8 2 Z M5 7 H5.01 M7.5 4.5 H7.51 M10.5 5.5 H10.51";
    }

    @Override
    public Node create(PanelContext context) {
        this.panel = context;
        rows = new VBox(2);
        heading = new Label();
        PluginContext ctx = context.plugin();
        // Any of these can change what the panel shows; events come at most once per frame.
        subscriptions.add(ctx.on(SceneEvent.BlocksChanged.class, e -> changed()));
        subscriptions.add(ctx.on(SceneEvent.SelectionChanged.class, e -> changed()));
        subscriptions.add(ctx.on(SceneEvent.LayersChanged.class, e -> changed()));
        subscriptions.add(ctx.on(SceneEvent.ProjectOpened.class, e -> changed()));
        context.onShown(() -> {
            if (stale) refresh();
        });

        // Looked-up colours from the theme (-color-fg-muted…) keep the panel right in every theme, light or dark.
        heading.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox root = new VBox(8, heading, scroll);
        root.setPadding(new Insets(10));
        refresh();
        return root;
    }

    private void changed() {
        stale = true;
        if (panel != null && panel.isShowing()) refresh();
    }

    private void refresh() {
        stale = false;
        PluginContext ctx = panel.plugin();
        Optional<Box> sel = ctx.selection();
        Map<String, Long> counts = new HashMap<>();
        for (Layer l : ctx.scene().layers()) {
            if (!l.visible()) continue;
            l.structure().forEachBlock((x, y, z, st) -> {
                if (sel.isPresent()) {
                    BlockPos w = l.toWorld(x, y, z);
                    if (!sel.get().contains(w.x(), w.y(), w.z())) return;
                }
                counts.merge(st.name(), 1L, Long::sum);
            });
        }
        heading.setText((sel.isPresent() ? "In the selection" : "In the visible layers") + " · " + counts.size() + " kinds of block");
        panel.setBadge(counts.isEmpty() ? null : String.valueOf(counts.size()));
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        List<Node> out = new ArrayList<>();
        for (var e : sorted.subList(0, Math.min(sorted.size(), 200))) out.add(row(ctx, e.getKey(), e.getValue()));
        rows.getChildren().setAll(out);
    }

    private static Node row(PluginContext ctx, String id, long count) {
        var st = io.blockdesigner.core.model.BlockState.of(id);
        Region swatch = new Region();
        swatch.setMinSize(14, 14);
        swatch.setMaxSize(14, 14);
        swatch.setStyle(String.format("-fx-background-color: #%06X; -fx-background-radius: 3; -fx-border-color: -color-border-default; -fx-border-radius: 3;",
                ctx.blocks().averageColor(st) & 0xFFFFFF));
        Label name = new Label(ctx.blocks().displayName(st));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label n = new Label(String.format("%,d", count));
        n.setStyle("-fx-text-fill: -color-fg-muted;");
        HBox row = new HBox(8, swatch, name, sp, n);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 4, 3, 4));
        return row;
    }

    @Override
    public void dispose() {
        subscriptions.forEach(Subscription::cancel);
        subscriptions.clear();
        panel = null;
    }
}
