package io.blockdesigner.app.ui;

import io.blockdesigner.assets.BlockAssets;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Objects;

/**
 * Jade/WAILA-style tooltip for the block under the cursor (or crosshair): icon, name, properties, position,
 * layer and the mod it comes from.
 */
public final class BlockInfoHud extends HBox {
    private final ImageView icon = new ImageView();
    private final Label name = new Label();
    private final Label props = new Label();
    private final Label where = new Label();
    private final Label mod = new Label();
    private BlockState shownState;
    private BlockPos shownPos;
    private Layer shownLayer;

    public BlockInfoHud() {
        getStyleClass().add("block-hud");
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);
        setMouseTransparent(true);
        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        icon.setFitWidth(32);
        icon.setFitHeight(32);
        icon.setSmooth(false);
        name.getStyleClass().add("hud-name");
        props.getStyleClass().add("hud-props");
        props.setWrapText(true);
        props.setMaxWidth(300);
        where.getStyleClass().add("hud-where");
        mod.getStyleClass().add("hud-mod");
        VBox text = new VBox(1, name, props, where, mod);
        getChildren().addAll(icon, text);
        setVisible(false);
    }

    /** Shows a block, or hides the HUD when {@code state} is null. */
    public void show(BlockAssets assets, BlockState state, BlockPos world, Layer layer) {
        if (state == null || state.isAir()) {
            setVisible(false);
            shownState = null;
            return;
        }
        if (state == shownState && Objects.equals(world, shownPos) && layer == shownLayer && isVisible()) return;
        shownState = state;
        shownPos = world;
        shownLayer = layer;
        String display = assets != null ? assets.registry().get(state.name()).map(b -> b.displayName()).orElse(pretty(state.path())) : pretty(state.path());
        name.setText(display);
        props.setText(state.properties().isEmpty() ? "" : state.properties().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).reduce((a, b) -> a + "  ·  " + b).orElse(""));
        props.setManaged(!state.properties().isEmpty());
        props.setVisible(!state.properties().isEmpty());
        where.setText(world.x() + ", " + world.y() + ", " + world.z() + (layer != null ? "   ·   " + layer.name() : ""));
        mod.setText(modName(state.namespace()));
        icon.setImage(assets != null ? BlockIcons.icon(assets, state) : null);
        setVisible(true);
    }

    /** Shows an entity (world position): its egg icon, name, what it is and where. */
    public void showEntity(io.blockdesigner.core.model.StructureEntity e, Layer layer) {
        showEntity(null, e, layer);
    }

    public void showEntity(BlockAssets assets, io.blockdesigner.core.model.StructureEntity e, Layer layer) {
        var kind = io.blockdesigner.core.model.EntityTypes.kind(e.id());
        shownState = null;
        name.setText(io.blockdesigner.core.model.EntityTypes.displayName(e));
        String details = EntityIcons.describe(e);
        props.setText(details);
        props.setManaged(!details.isEmpty());
        props.setVisible(!details.isEmpty());
        where.setText(String.format(Locale.ROOT, "%.1f, %.1f, %.1f", e.x(), e.y(), e.z()) + (layer != null ? "   ·   " + layer.name() : ""));
        mod.setText(modName(kind.id().substring(0, kind.id().indexOf(':'))) + " · entity");
        icon.setImage(EntityIcons.icon(assets, kind.id()));
        setVisible(true);
    }

    static String modName(String namespace) {
        return switch (namespace) {
            case "minecraft" -> "Minecraft";
            case "create" -> "Create";
            default -> pretty(namespace);
        };
    }

    /** A block's in-game name (from Minecraft's language files once assets are loaded). */
    static String name(io.blockdesigner.assets.BlockAssets assets, BlockState s) {
        return assets != null ? assets.registry().displayName(s.name()) : pretty(s.path());
    }

    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("[_/]")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
