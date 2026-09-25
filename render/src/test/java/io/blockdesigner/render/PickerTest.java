package io.blockdesigner.render;

import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickerTest {
    static final BlockState SLAB = BlockState.parse("minecraft:oak_slab[type=bottom,waterlogged=false]");
    static final BlockState STONE = BlockState.of("stone");

    /** A bottom slab at x = 0 with stone behind it at x = 1. */
    Scene scene() {
        Structure s = new Structure();
        s.set(new BlockPos(0, 0, 0), SLAB);
        s.set(new BlockPos(1, 0, 0), STONE);
        Scene sc = new Scene();
        sc.add(new Layer("test", s));
        return sc;
    }

    java.util.Optional<Picker.Hit> pick(Scene sc, float y) {
        return Picker.pick(sc, new Vector3f(-3, y, 0.5f), new Vector3f(1, 0, 0), 100, l -> true, Integer.MIN_VALUE, Integer.MAX_VALUE, null,
                st -> st == SLAB ? List.<float[]>of(new float[]{0, 0, 0, 1, 0.5f, 1}) : null);
    }

    @Test
    void raysPassOverTheEmptyHalfOfASlab() {
        Scene sc = scene();
        // Low: hits the slab's west side.
        var low = pick(sc, 0.25f).orElseThrow();
        assertThat(low.world()).isEqualTo(new BlockPos(0, 0, 0));
        assertThat(low.normal()).isEqualTo(new BlockPos(-1, 0, 0));
        assertThat(low.distance()).isCloseTo(3f, org.assertj.core.data.Offset.offset(1e-3f));
        // High: goes over the slab and hits the stone behind.
        var high = pick(sc, 0.75f).orElseThrow();
        assertThat(high.world()).isEqualTo(new BlockPos(1, 0, 0));
    }

    @Test
    void withoutShapesEveryBlockIsACube() {
        var hit = Picker.pick(scene(), new Vector3f(-3, 0.75f, 0.5f), new Vector3f(1, 0, 0), 100, l -> true).orElseThrow();
        assertThat(hit.world()).isEqualTo(new BlockPos(0, 0, 0));
    }
}
