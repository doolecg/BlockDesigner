package io.blockdesigner.app.plugins;

import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.transform.Transform;
import io.blockdesigner.plugin.SceneEvent;
import io.blockdesigner.plugin.Subscription;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class SceneEventBusTest {
    private final Scene scene = new Scene();
    private final SceneEditor editor = new SceneEditor(scene);
    /** Stands in for Platform.runLater: tasks wait here until the test runs the "next pulse". */
    private final Queue<Runnable> pulse = new ArrayDeque<>();
    private Optional<Box> selection = Optional.empty();
    private final List<Throwable> errors = new ArrayList<>();
    private final SceneEventBus bus = new SceneEventBus(scene, () -> selection, pulse::add, (owner, t) -> errors.add(t));
    private final Object owner = new Object();
    private final List<SceneEvent> got = new ArrayList<>();

    private void nextPulse() {
        while (!pulse.isEmpty()) pulse.poll().run();
    }

    @Test
    void aBurstOfEditsIsOneEventWithAMergedWorldBox() {
        Layer a = new Layer("a", new Structure());
        a.setOffset(new BlockPos(100, 0, 0));
        editor.addLayer(a);
        bus.subscribe(owner, SceneEvent.BlocksChanged.class, got::add);
        try (var s = editor.edit(a, "paint")) {
            for (int x = 0; x < 50; x++) {
                s.set(x, 0, 0, BlockState.of("stone"));
                s.flush(); // like a brush stroke: an event per dab
            }
        }
        assertThat(got).isEmpty();
        assertThat(pulse).hasSize(1);
        nextPulse();
        assertThat(got).hasSize(1);
        var e = (SceneEvent.BlocksChanged) got.getFirst();
        assertThat(e.layers()).containsExactly(a);
        assertThat(e.dirty()).isEqualTo(new Box(100, 0, 0, 149, 0, 0));
    }

    @Test
    void dirtyBoxesAreInWorldSpaceForTurnedLayers() {
        Layer a = new Layer("a", new Structure());
        a.setTransform(Transform.rotation(1));
        editor.addLayer(a);
        bus.subscribe(owner, SceneEvent.BlocksChanged.class, got::add);
        try (var s = editor.edit(a, "set")) {
            s.set(3, 1, 0, BlockState.of("stone"));
        }
        nextPulse();
        BlockPos w = a.toWorld(3, 1, 0);
        assertThat(((SceneEvent.BlocksChanged) got.getFirst()).dirty()).isEqualTo(new Box(w.x(), w.y(), w.z(), w.x(), w.y(), w.z()));
    }

    @Test
    void eachKindOnceInAFixedOrderAndFilteredByType() {
        List<SceneEvent> all = new ArrayList<>();
        bus.subscribe(owner, SceneEvent.class, all::add);
        bus.subscribe(owner, SceneEvent.LayersChanged.class, got::add);
        Layer a = new Layer("a", new Structure()), b = new Layer("b", new Structure());
        editor.addLayer(a);
        editor.addLayer(b);
        try (var s = editor.edit(b, "x")) {
            s.set(0, 0, 0, BlockState.of("dirt"));
        }
        try (var s = editor.edit(a, "x")) {
            s.set(0, 0, 0, BlockState.of("dirt"));
        }
        selection = Optional.of(new Box(0, 0, 0, 1, 1, 1));
        bus.selectionChanged();
        bus.projectOpened(Optional.of(Path.of("house.bdproj")));
        nextPulse();
        assertThat(all).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("ProjectOpened", "LayersChanged", "ActiveLayerChanged", "BlocksChanged", "SelectionChanged");
        assertThat(((SceneEvent.BlocksChanged) all.get(3)).layers()).as("bottom first").containsExactly(a, b);
        assertThat(((SceneEvent.ActiveLayerChanged) all.get(2)).layer()).contains(b);
        assertThat(((SceneEvent.SelectionChanged) all.get(4)).region()).isEqualTo(selection);
        assertThat(got).hasSize(1).first().isInstanceOf(SceneEvent.LayersChanged.class);
    }

    @Test
    void cancelledAndRemovedListenersHearNothingAndErrorsAreContained() {
        Subscription sub = bus.subscribe(owner, SceneEvent.class, got::add);
        Object other = new Object();
        bus.subscribe(other, SceneEvent.class, e -> {
            throw new IllegalStateException("boom");
        });
        List<SceneEvent> third = new ArrayList<>();
        bus.subscribe(new Object(), SceneEvent.class, third::add);
        sub.cancel();
        bus.removeAll(other);
        editor.addLayer(new Layer("a", new Structure()));
        nextPulse();
        assertThat(got).isEmpty();
        assertThat(third).isNotEmpty();
        assertThat(errors).isEmpty();

        bus.subscribe(other, SceneEvent.class, e -> {
            throw new IllegalStateException("boom");
        });
        editor.addLayer(new Layer("b", new Structure()));
        nextPulse();
        assertThat(errors).hasSize(2).allMatch(t -> t.getMessage().equals("boom")); // LayersChanged and ActiveLayerChanged
        assertThat(third).hasSizeGreaterThan(2);
    }

    @Test
    void nothingQueuedWithoutListeners() {
        editor.addLayer(new Layer("a", new Structure()));
        assertThat(pulse).isEmpty();
        // What happened before subscribing isn't delivered later.
        bus.subscribe(owner, SceneEvent.class, got::add);
        bus.selectionChanged();
        nextPulse();
        assertThat(got).hasSize(1).first().isInstanceOf(SceneEvent.SelectionChanged.class);
    }
}
