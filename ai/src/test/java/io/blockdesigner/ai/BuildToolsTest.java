package io.blockdesigner.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockdesigner.ai.provider.AiProvider;
import io.blockdesigner.ai.tools.BuildContext;
import io.blockdesigner.ai.tools.BuildTools;
import io.blockdesigner.assets.BlockRegistry;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.BlockPos;
import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.core.transform.Transform;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class BuildToolsTest {
    static final ObjectMapper JSON = new ObjectMapper();

    final Scene scene = new Scene();
    final SceneEditor editor = new SceneEditor(scene);
    final BuildContext ctx = new BuildContext() {
        @Override
        public Scene scene() {
            return scene;
        }

        @Override
        public SceneEditor editor() {
            return editor;
        }

        @Override
        public BlockRegistry registry() {
            return null;
        }

        @Override
        public String targetVersion() {
            return "1.21.1";
        }

        @Override
        public CompletableFuture<byte[]> renderView(String view, int w, int h) {
            return CompletableFuture.completedFuture(new byte[]{1, 2, 3});
        }
    };
    final BuildTools tools = new BuildTools(ctx);

    JsonNode j(String s) {
        try {
            return JSON.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    BlockState at(int x, int y, int z) {
        Layer l = scene.active().orElseThrow();
        return l.structure().get(l.toLocal(new BlockPos(x, y, z)));
    }

    @Test
    void specsHaveValidSchemas() {
        for (AiProvider.ToolSpec s : BuildTools.specs()) {
            assertThat(s.inputSchema().path("type").asText()).isEqualTo("object");
            assertThat(s.description()).isNotBlank();
        }
    }

    @Test
    void fillModesAndAutoLayer() {
        var o = tools.execute("fill", j("{'from':[0,0,0],'to':[4,3,4],'block':'stone_bricks','mode':'hollow'}"));
        assertThat(o.error()).isFalse();
        assertThat(scene.layers()).hasSize(1);
        assertThat(at(0, 0, 0).path()).isEqualTo("stone_bricks");
        assertThat(at(2, 1, 2).isAir()).isTrue();
        tools.execute("fill", j("{'from':[10,0,10],'to':[12,2,12],'block':'oak_planks','mode':'walls'}"));
        assertThat(at(10, 1, 10).path()).isEqualTo("oak_planks");
        assertThat(at(11, 1, 11).isAir()).isTrue();
        assertThat(at(11, 0, 11).isAir()).isTrue();
    }

    @Test
    void writesRespectLayerTransform() {
        Layer l = new Layer("rotated", new Structure());
        l.setOffset(new BlockPos(100, 0, 0));
        l.setTransform(Transform.rotation(1));
        editor.addLayer(l);
        tools.execute("set_blocks", j("{'blocks':[{'pos':[105,2,3],'block':'oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'}]}"));
        // World state reads back exactly as written despite the layer's rotation.
        BlockState world = io.blockdesigner.core.transform.BlockTransformer.defaults().apply(at(105, 2, 3), l.transform());
        assertThat(world.get("facing")).isEqualTo("north");
        assertThat(Scene.flatten(List.of(l)).get(105, 2, 3).get("facing")).isEqualTo("north");
    }

    @Test
    void gableRoofRowsFaceTheRidge() {
        var o = tools.execute("roof", j("{'from':[0,5,0],'to':[6,5,4],'stairs':'oak_stairs','style':'gable','overhang':0}"));
        assertThat(o.error()).as(o.text()).isFalse();
        // Ridge along X (longer side): north edge stairs climb south, south edge climb north.
        assertThat(at(3, 5, 0).get("facing")).isEqualTo("south");
        assertThat(at(3, 5, 4).get("facing")).isEqualTo("north");
        assertThat(at(3, 6, 1).get("facing")).isEqualTo("south");
        assertThat(at(3, 7, 2).isAir()).isFalse(); // ridge cap
    }

    @Test
    void hipRoofCorners() {
        tools.execute("roof", j("{'from':[0,0,0],'to':[4,0,4],'stairs':'oak_stairs','style':'hip','overhang':0}"));
        assertThat(at(0, 0, 0).get("shape")).isEqualTo("outer_left");
        assertThat(at(2, 0, 0).get("facing")).isEqualTo("south");
        assertThat(at(0, 0, 2).get("facing")).isEqualTo("east");
        assertThat(at(4, 0, 2).get("facing")).isEqualTo("west");
        assertThat(at(2, 2, 2).isAir()).isFalse();
    }

    @Test
    void copyRotatesPositionsAndStates() {
        tools.execute("set_blocks", j("{'blocks':[{'pos':[0,0,0],'block':'stone'},{'pos':[2,0,0],'block':'oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'}]}"));
        var o = tools.execute("copy", j("{'from':[0,0,0],'to':[2,0,0],'dest':[10,0,10],'rotate':1}"));
        assertThat(o.error()).as(o.text()).isFalse();
        // A 3×1 row turned a quarter clockwise becomes 1×3 along Z.
        assertThat(at(10, 0, 10).isAir() && at(10, 0, 12).isAir()).isFalse();
        BlockState stair = at(10, 0, 12).path().equals("oak_stairs") ? at(10, 0, 12) : at(10, 0, 10);
        assertThat(stair.get("facing")).isEqualTo("south");
    }

    @Test
    void badArgumentsBecomeErrors() {
        assertThat(tools.execute("fill", j("{'from':[0,0],'to':[1,1,1],'block':'stone'}")).error()).isTrue();
        assertThat(tools.execute("fill", j("{'from':[0,0,0],'to':[1000,1000,1000],'block':'stone'}")).text()).contains("limit");
        assertThat(tools.execute("nope", j("{}")).error()).isTrue();
        assertThat(tools.execute("sphere", j("{'center':[0,0,0],'radius':500,'block':'stone'}")).error()).isTrue();
    }

    @Test
    void lockedActiveLayerIsRespected() {
        Layer l = new Layer("locked", new Structure());
        l.setLocked(true);
        editor.addLayer(l);
        assertThat(tools.execute("fill", j("{'from':[0,0,0],'to':[1,1,1],'block':'stone'}")).text()).contains("locked");
        assertThat(l.structure().blockCount()).isZero();
    }

    @Test
    void sliceShowsLegend() {
        tools.execute("fill", j("{'from':[0,0,0],'to':[2,0,2],'block':'stone'}"));
        String s = tools.execute("get_layer_slice", j("{'y':0}")).text();
        assertThat(s).contains("###").contains("# = stone");
    }

    // ---- agent loop with a scripted provider ---------------------------------------------------------------

    @Test
    void agentRunsToolsFeedsResultsAndGroupsUndo() {
        Deque<AiProvider.Turn> script = new ArrayDeque<>(List.of(
                new AiProvider.Turn("Building.", List.of(
                        new AiProvider.ToolCall("1", "fill", j("{'from':[0,0,0],'to':[2,2,2],'block':'stone'}")),
                        new AiProvider.ToolCall("2", "render_view", j("{}"))), "tool_use", 10, 10),
                new AiProvider.Turn("Done.", List.of(), "end_turn", 10, 10)));
        List<List<AiProvider.ToolResult>> seen = new ArrayList<>();
        AiProvider fake = new AiProvider() {
            public String id() {
                return "fake";
            }

            public String displayName() {
                return "Fake";
            }

            public List<ModelInfo> models() {
                return List.of(new ModelInfo("m", "m", true));
            }

            public boolean needsSetup() {
                return false;
            }

            public AiSession open(SessionConfig config) {
                return new AiSession() {
                    public Turn send(List<Part> userContent, Listener listener) {
                        return script.poll();
                    }

                    public Turn sendToolResults(List<ToolResult> results, List<Part> extra, Listener listener) {
                        seen.add(results);
                        return script.poll();
                    }

                    public void cancel() {
                    }
                };
            }
        };
        BuildAgent.Host host = new BuildAgent.Host() {
            public <T> T onOwner(Callable<T> task) throws Exception {
                return task.call();
            }

            public String sceneSummary() {
                return "empty";
            }

            public void beginUndoGroup(String label) {
                editor.undoStack().beginGroup(label);
            }

            public void endUndoGroup() {
                editor.undoStack().endGroup();
            }
        };
        BuildAgent agent = new BuildAgent(fake, tools, host, BuildAgent.Options.defaults("m"));
        String[] finished = {null};
        agent.run("build a cube", List.of(), new BuildAgent.Events() {
            @Override
            public void onFinished(String stopReason) {
                finished[0] = stopReason;
            }

            @Override
            public void onError(Throwable t) {
                throw new AssertionError(t);
            }
        });
        assertThat(finished[0]).isEqualTo("end_turn");
        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst()).hasSize(2);
        assertThat(seen.getFirst().get(1).image()).isNotNull();
        assertThat(scene.active().orElseThrow().structure().blockCount()).isEqualTo(27);
        // Layer creation + fill undo together.
        assertThat(editor.undoStack().size()).isEqualTo(1);
        editor.undoStack().undo();
        assertThat(scene.layers()).isEmpty();
    }
}
