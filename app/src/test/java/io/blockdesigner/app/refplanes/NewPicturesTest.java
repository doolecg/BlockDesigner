package io.blockdesigner.app.refplanes;

import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.OptionValues;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The settings for new pictures, as shown on the Settings window's page and as stored by the plugin before. */
class NewPicturesTest {
    @Test
    void settingsAreOneGroupWithUnitsAndHelp() {
        var o = NewPictures.OPTIONS;
        assertThat(o.groups()).hasSize(1);
        assertThat(o.groups().getFirst().title()).isEqualTo("New pictures");
        assertThat(o.groups().getFirst().keys()).containsExactly("height", "opacity", "draw", "onlyInItsView");
        assertThat(o.unit("height")).contains("blocks");
        assertThat(o.unit("opacity")).contains("%");
        assertThat(o.help("draw")).isPresent();
        assertThat(o.help("onlyInItsView")).isPresent();
    }

    @Test
    void storedDrawValuesFromOlderVersionsStillRead() {
        for (var e : Map.of("Behind blocks", Drawing.Depth.BEHIND_BLOCKS, "In the scene", Drawing.Depth.IN_SCENE,
                "In front of blocks", Drawing.Depth.IN_FRONT).entrySet()) {
            OptionValues v = OptionValues.fromStrings(NewPictures.OPTIONS, Map.of("draw", e.getKey()), null);
            assertThat(NewPictures.of(v).depth()).isEqualTo(e.getValue());
            assertThat(NewPictures.draw(e.getValue())).isEqualTo(e.getKey());
        }
        assertThat(NewPictures.depth("Something else")).isEqualTo(Drawing.Depth.IN_SCENE);
    }

    @Test
    void defaults() {
        assertThat(NewPictures.DEFAULT.opacity()).isEqualTo(1.0);
        assertThat(NewPictures.DEFAULT.depth()).isEqualTo(Drawing.Depth.IN_SCENE);
        assertThat(NewPictures.DEFAULT.onlyInItsView()).isTrue();
    }

    @Test
    void settingsKeptAsTextComeBack() {
        var v = NewPictures.values(Map.of("height", "8", "opacity", "0.5", "draw", "Behind blocks", "onlyInItsView", "false"));
        NewPictures n = NewPictures.of(v);
        assertThat(n.height()).isEqualTo(8);
        assertThat(n.opacity()).isEqualTo(0.5);
        assertThat(n.depth()).isEqualTo(Drawing.Depth.BEHIND_BLOCKS);
        assertThat(n.onlyInItsView()).isFalse();
        assertThat(NewPictures.of(NewPictures.values(v.toStrings()))).isEqualTo(n);
        assertThat(NewPictures.of(NewPictures.values(Map.of()))).isEqualTo(NewPictures.DEFAULT);
        assertThat(NewPictures.of(NewPictures.values(null))).isEqualTo(NewPictures.DEFAULT);
    }
}
