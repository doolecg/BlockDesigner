package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.OptionValues;
import io.blockdesigner.plugin.Options;

import java.util.List;
import java.util.Map;

/**
 * How new pictures start out, from the Reference images page of the Settings window: their height, opacity, whether
 * blocks cover them, and whether one added in an axis view shows only in that view. Each picture can still be changed
 * afterwards (its right-click menu and Properties). Kept as text in {@code Settings.newReferenceImages}, the same
 * strings the Reference Planes plugin kept.
 */
record NewPictures(double height, double opacity, Drawing.Depth depth, boolean onlyInItsView) {
    /** The Draw values, as stored and as the right-click menu words them. */
    static final List<String> DRAW = List.of("Behind blocks", "In the scene", "In front of blocks");

    static final Options OPTIONS = Options.builder()
            .group("New pictures")
            .decimal("height", "Height", ReferenceSettings.DEFAULT_SIZE, 1, 512).unit("blocks")
            .help("How tall a new picture is. Scale each one afterwards with the Scale tool or Properties.")
            .decimal("opacity", "Opacity", 1, 0.05, 1).unit("%")
            .choice("draw", "Draw", DRAW, DRAW.get(1))
            .help("In the scene: blocks in front of the picture hide it.")
            .toggle("onlyInItsView", "Show only in the view it was added in", true)
            .help("For pictures added in a front, side or top view; in the 3D view they always show everywhere.")
            .build();

    static final NewPictures DEFAULT = of(OPTIONS.defaults());

    static NewPictures of(OptionValues v) {
        return new NewPictures(v.decimal("height"), v.decimal("opacity"), depth(v.choice("draw")), v.toggle("onlyInItsView"));
    }

    /** The values as the settings keep them (anything missing or no longer valid is its default). */
    static OptionValues values(Map<String, String> saved) {
        return saved == null || saved.isEmpty() ? OPTIONS.defaults() : OptionValues.fromStrings(OPTIONS, saved, null);
    }

    /** A Draw value as a depth; anything unknown is "In the scene". */
    static Drawing.Depth depth(String draw) {
        return switch (DRAW.indexOf(draw)) {
            case 0 -> Drawing.Depth.BEHIND_BLOCKS;
            case 2 -> Drawing.Depth.IN_FRONT;
            default -> Drawing.Depth.IN_SCENE;
        };
    }

    /** The Draw wording for a depth (the right-click menu uses the same words). */
    static String draw(Drawing.Depth d) {
        return switch (d) {
            case BEHIND_BLOCKS -> DRAW.get(0);
            case IN_FRONT -> DRAW.get(2);
            default -> DRAW.get(1);
        };
    }
}
