package io.blockdesigner.app.plugins;

import io.blockdesigner.core.edit.UndoStack;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.plugin.Drawing;
import io.blockdesigner.plugin.ImageData;
import io.blockdesigner.plugin.Pose;
import io.blockdesigner.plugin.SceneObject;
import io.blockdesigner.plugin.SceneObjectType;
import io.blockdesigner.plugin.ToolEvent.Vec3;
import io.blockdesigner.plugin.ViewInfo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SceneObjectStoreTest {
    private final UndoStack undo = new UndoStack(new Scene());
    private final SceneObjectStore store = new SceneObjectStore(undo);
    private final Object plugin = new Object();
    private final List<Throwable> errors = new ArrayList<>();

    private static final ViewInfo PERSP = new ViewInfo(false, Optional.empty(), new Vec3(0, 0, 10), new Vec3(0, 0, -1), new Vec3(0, 0, 0));
    private static final ViewInfo FRONT = new ViewInfo(true, Optional.of(ViewInfo.Side.FRONT), new Vec3(0, 0, 10), new Vec3(0, 0, -1), new Vec3(0, 0, 0));
    private static final ImageData PIXEL = new ImageData(1, 1, new int[]{0xFFFFFFFF});

    /** A test object: a 2×1 card whose text state is its "colour"; shown only in the Front ortho view when {@code front}. */
    static class Card implements SceneObject {
        String colour = "red";
        boolean frontOnly;
        String blob = "";

        @Override
        public void draw(ViewInfo view, Drawing out) {
            if (frontOnly && !view.isOrthoSide(ViewInfo.Side.FRONT)) return;
            out.image(PIXEL, new Vec3[]{new Vec3(-1, -0.5, 0), new Vec3(1, -0.5, 0), new Vec3(1, 0.5, 0), new Vec3(-1, 0.5, 0)},
                    new double[]{0, 1, 1, 1, 1, 0, 0, 0}, 1, Drawing.Depth.IN_SCENE);
        }

        @Override
        public byte[] save() {
            return (colour + "|" + frontOnly + "|" + blob).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void load(byte[] data) {
            String[] p = new String(data, StandardCharsets.UTF_8).split("\\|", -1);
            colour = p[0];
            frontOnly = p.length > 1 && Boolean.parseBoolean(p[1]);
            blob = p.length > 2 ? p[2] : "";
        }

        @Override
        public Set<String> blobs() {
            return blob.isEmpty() ? Set.of() : Set.of(blob);
        }
    }

    static final SceneObjectType CARDS = new SceneObjectType() {
        public String id() {
            return "card";
        }

        public String name() {
            return "Card";
        }

        public String badge() {
            return "CARD";
        }

        public SceneObject create() {
            return new Card();
        }

        public List<String> extensions() {
            return List.of("png", "JPG");
        }
    };

    SceneObjectStoreTest() {
        store.setErrors((owner, t) -> errors.add(t));
    }

    @Test
    void addEditUndoRedo() {
        store.register(plugin, "demo", CARDS);
        var e = store.add("demo/card", "Sketch", Pose.at(1, 2, 3), new Card());
        assertThat(store.list()).containsExactly(e);
        assertThat(e.selected()).isTrue();
        assertThat(e.badge()).isEqualTo("CARD");
        assertThat(e.type()).isEqualTo("card");

        e.setPose(Pose.at(5, 5, 5), "Move");
        e.edit("Paint blue", () -> ((Card) e.object()).colour = "blue");
        e.setVisible(false);
        undo.undo();
        assertThat(e.visible()).isTrue();
        undo.undo();
        assertThat(((Card) e.object()).colour).isEqualTo("red");
        undo.undo();
        assertThat(e.pose()).isEqualTo(Pose.at(1, 2, 3));
        undo.redo();
        undo.redo();
        assertThat(e.pose()).isEqualTo(Pose.at(5, 5, 5));
        assertThat(((Card) e.object()).colour).isEqualTo("blue");

        e.remove();
        assertThat(store.list()).isEmpty();
        assertThat(e.exists()).isFalse();
        assertThat(store.selected()).isEmpty();
        undo.undo();
        assertThat(store.list()).containsExactly(e);
    }

    @Test
    void onlyRegisteredTypesCanBeAdded() {
        assertThatThrownBy(() -> store.add("demo/card", "x", Pose.IDENTITY, new Card())).isInstanceOf(IllegalArgumentException.class);
        store.register(plugin, "demo", CARDS);
        assertThatThrownBy(() -> store.register(plugin, "demo", CARDS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDragIsOneUndoStepAndCancelPutsItBack() {
        store.register(plugin, "demo", CARDS);
        var e = store.add("demo/card", "Sketch", Pose.at(0, 0, 0), new Card());
        int steps = undo.size();
        ObjectDrag d = new ObjectDrag(e);
        d.move(1, 0, 0);
        d.move(2.004, 0, -1);
        assertThat(e.pose().position()).isEqualTo(new Vec3(2, 0, -1));
        d.commit();
        assertThat(undo.size()).isEqualTo(steps + 1);
        assertThat(undo.undoLabel()).contains("Move Sketch");

        ObjectDrag r = new ObjectDrag(e);
        r.rotate(1, 90);
        assertThat(e.pose().rotation().y()).isCloseTo(90, within(1e-9));
        // Turned about its own origin: it stays put.
        assertThat(e.pose().position()).isEqualTo(new Vec3(2, 0, -1));
        r.cancel();
        assertThat(e.pose().rotation()).isEqualTo(new Vec3(0, 0, 0));
        assertThat(undo.size()).isEqualTo(steps + 1);

        ObjectDrag s = new ObjectDrag(e);
        s.scale(2, 0, 1);
        assertThat(e.pose().scale().x()).isEqualTo(2);
        assertThat(e.pose().scale().y()).isPositive();
        s.commit();
        assertThat(undo.undoLabel()).contains("Scale Sketch");
        undo.undo();
        assertThat(e.pose().scale()).isEqualTo(new Vec3(1, 1, 1));
    }

    @Test
    void saveAndLoadRoundTrip() {
        store.register(plugin, "demo", CARDS);
        Card c = new Card();
        c.colour = "green";
        c.blob = store.storeBlob(new byte[]{9, 8, 7});
        store.storeBlob(new byte[]{1}); // not used by anything: left out
        var e = store.add("demo/card", "Plan", new Pose(new Vec3(1, 2, 3), new Vec3(10, 20, 30), new Vec3(4, 5, 6)), c);
        e.setLocked(true);
        Map<String, byte[]> saved = store.save();
        assertThat(saved).containsKeys(SceneObjectStore.INDEX, "objects/" + e.id() + ".bin", "objects/blobs/" + c.blob);
        assertThat(saved.keySet().stream().filter(k -> k.startsWith(SceneObjectStore.BLOBS))).hasSize(1);

        SceneObjectStore other = new SceneObjectStore(null);
        other.register(plugin, "demo", CARDS);
        other.load(saved);
        var back = other.list().getFirst();
        assertThat(back.id()).isEqualTo(e.id());
        assertThat(back.name()).isEqualTo("Plan");
        assertThat(back.locked()).isTrue();
        assertThat(back.pose()).isEqualTo(e.pose());
        assertThat(((Card) back.object()).colour).isEqualTo("green");
        assertThat(other.blob(c.blob)).hasValueSatisfying(b -> assertThat(b).containsExactly(9, 8, 7));

        other.load(Map.of());
        assertThat(other.list()).isEmpty();
        assertThat(other.save()).isEmpty();
    }

    @Test
    void objectsOfAMissingPluginAreKeptAndComeBack() {
        store.register(plugin, "demo", CARDS);
        Card c = new Card();
        c.colour = "gold";
        store.add("demo/card", "Kept", Pose.at(7, 0, 0), c);
        Map<String, byte[]> saved = store.save();

        // Opened without the plugin: nothing shown, but saving again keeps the object as it was.
        SceneObjectStore without = new SceneObjectStore(null);
        without.load(saved);
        assertThat(without.list()).isEmpty();
        Map<String, byte[]> resaved = without.save();
        assertThat(resaved.keySet()).isEqualTo(saved.keySet());

        // The plugin turns up: the object is back.
        without.register(plugin, "demo", CARDS);
        assertThat(without.list()).singleElement().satisfies(e -> {
            assertThat(e.name()).isEqualTo("Kept");
            assertThat(((Card) e.object()).colour).isEqualTo("gold");
        });

        // Disabled again: parked with its latest state, then back once more.
        ((Card) without.list().getFirst().object()).colour = "silver";
        without.unregister(plugin);
        assertThat(without.list()).isEmpty();
        without.register(plugin, "demo", CARDS);
        assertThat(((Card) without.list().getFirst().object()).colour).isEqualTo("silver");
    }

    @Test
    void drawingAppliesThePoseAndFiltersViews() {
        store.register(plugin, "demo", CARDS);
        Card c = new Card();
        c.frontOnly = true;
        var e = store.add("demo/card", "Front", new Pose(new Vec3(10, 0, 0), new Vec3(0, 90, 0), new Vec3(2, 2, 2)), c);
        assertThat(store.draw(PERSP).images()).isEmpty();
        var drawn = store.draw(FRONT).images();
        assertThat(drawn).hasSize(1);
        double[] w = drawn.getFirst().corners();
        // (-1, -0.5, 0) scaled 2, turned 90° about Y (x → -z), moved to x = 10.
        assertThat(w[0]).isCloseTo(10, within(1e-9));
        assertThat(w[1]).isCloseTo(-1, within(1e-9));
        assertThat(w[2]).isCloseTo(2, within(1e-9));

        e.setVisible(false);
        assertThat(store.draw(FRONT).images()).isEmpty();
    }

    @Test
    void pickingFindsTheNearestUnhiddenImage() {
        store.register(plugin, "demo", CARDS);
        var near = store.add("demo/card", "Near", Pose.at(0, 0, 2), new Card());
        var far = store.add("demo/card", "Far", Pose.at(0, 0, -2), new Card());
        var drawn = store.draw(PERSP);
        Vec3 eye = new Vec3(0, 0, 10), down = new Vec3(0, 0, -1);
        assertThat(drawn.pick(eye, down, Double.MAX_VALUE, false)).contains(near);
        // A block at distance 5 hides the far card but not the near one (at 8).
        assertThat(drawn.pick(eye, down, 9, false)).contains(near);
        assertThat(drawn.pick(eye, down, 5, false)).isEmpty();
        // Missing to the side.
        assertThat(drawn.pick(new Vec3(5, 0, 10), down, Double.MAX_VALUE, false)).isEmpty();
        // Locked objects only count when asked (the right-click menu).
        near.setLocked(true);
        assertThat(store.draw(PERSP).pick(eye, down, Double.MAX_VALUE, false)).contains(far);
        assertThat(store.draw(PERSP).pick(eye, down, Double.MAX_VALUE, true)).contains(near);
    }

    @Test
    void aFailingPluginIsReportedNotThrown() {
        store.register(plugin, "demo", new SceneObjectType() {
            public String id() {
                return "bad";
            }

            public String name() {
                return "Bad";
            }

            public String badge() {
                return "BAD";
            }

            public SceneObject create() {
                return new Card() {
                    @Override
                    public void draw(ViewInfo view, Drawing out) {
                        throw new IllegalStateException("boom");
                    }
                };
            }
        });
        store.add("demo/bad", "Bad", Pose.IDENTITY, store.type("demo/bad").orElseThrow().type().create());
        assertThat(store.draw(PERSP).images()).isEmpty();
        assertThat(errors).singleElement().satisfies(t -> assertThat(t).hasMessage("boom"));
    }

    @Test
    void typesAreFoundByExtension() {
        store.register(plugin, "demo", CARDS);
        assertThat(store.typesFor(Path.of("a/b/Sketch.PNG"))).hasSize(1);
        assertThat(store.typesFor(Path.of("photo.jpg"))).hasSize(1);
        assertThat(store.typesFor(Path.of("house.litematic"))).isEmpty();
        assertThat(store.extensions()).containsExactly("png", "JPG");
    }

    @Test
    void aPluginSeesOnlyItsOwnObjects() {
        store.register(plugin, "demo", CARDS);
        Object other = new Object();
        store.register(other, "other", CARDS);
        var mine = store.forPlugin("demo");
        var h = mine.add("card", "Mine", Pose.IDENTITY, new Card());
        store.add("other/card", "Theirs", Pose.IDENTITY, new Card());
        assertThat(mine.list()).containsExactly(h);
        assertThat(mine.selected()).isEmpty(); // "Theirs" was added last, so it is selected
        h.select();
        assertThat(mine.selected()).contains(h);
        assertThat(mine.storeBlob(new byte[]{1, 2})).isEqualTo(store.storeBlob(new byte[]{1, 2}));
    }
}
