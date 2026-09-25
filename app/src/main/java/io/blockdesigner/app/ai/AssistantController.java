package io.blockdesigner.app.ai;

import io.blockdesigner.ai.BuildAgent;
import io.blockdesigner.ai.provider.AiProvider;
import io.blockdesigner.ai.provider.ClaudeProvider;
import io.blockdesigner.ai.provider.OpenAiCompatibleProvider;
import io.blockdesigner.ai.tools.BuildContext;
import io.blockdesigner.ai.tools.BuildTools;
import io.blockdesigner.app.SecretStore;
import io.blockdesigner.app.Settings;
import io.blockdesigner.app.Workspace;
import io.blockdesigner.app.ui.ViewportPane;
import io.blockdesigner.assets.BlockRegistry;
import io.blockdesigner.core.edit.SceneEditor;
import io.blockdesigner.core.model.Box;
import io.blockdesigner.core.model.Layer;
import io.blockdesigner.core.model.Scene;
import io.blockdesigner.core.model.Structure;
import io.blockdesigner.render.ViewportRenderer;
import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * Wires the AI module into the app: providers configured from settings, the build-tool context backed by the live
 * scene and viewport, and the agent that runs on a background thread while tools execute on the FX thread.
 */
public final class AssistantController implements BuildContext, BuildAgent.Host {
    private final Workspace ws;
    private final ViewportPane viewport;
    private final List<AiProvider> providers;
    private final BuildTools tools = new BuildTools(this);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("blockdesigner-agent").daemon().factory());
    private BuildAgent agent;

    public AssistantController(Workspace ws, ViewportPane viewport) {
        this.ws = ws;
        this.viewport = viewport;
        Settings s = ws.settings();
        this.providers = List.of(
                new ClaudeProvider(() -> SecretStore.reveal(s.anthropicKeyProtected), () -> s.aiRefusalFallback),
                new OpenAiCompatibleProvider("openai-compatible", () -> new OpenAiCompatibleProvider.Endpoint(
                        s.openAiName == null || s.openAiName.isBlank() ? "OpenAI-compatible" : s.openAiName,
                        s.openAiBaseUrl, SecretStore.reveal(s.openAiKeyProtected),
                        Arrays.stream((s.openAiModels == null ? "" : s.openAiModels).split(",")).map(String::strip).filter(m -> !m.isEmpty()).toList())));
    }

    public List<AiProvider> providers() {
        return providers;
    }

    public AiProvider provider(String id) {
        return providers.stream().filter(p -> p.id().equals(id)).findFirst().orElse(providers.getFirst());
    }

    /** (Re)creates the agent for the chosen provider/model; keeps the conversation when nothing changed. */
    public BuildAgent agent(String providerId, String model, String effort) {
        AiProvider p = provider(providerId);
        BuildAgent.Options opts = new BuildAgent.Options(model, effort, 60, 64000);
        if (agent == null || agent.provider() != p) agent = new BuildAgent(p, tools, this, opts);
        else agent.setOptions(opts);
        return agent;
    }

    public void newConversation() {
        if (agent != null) agent.reset();
    }

    public void submit(Runnable task) {
        worker.submit(task);
    }

    public void cancel() {
        if (agent != null) agent.cancel();
    }

    // ---- BuildContext -------------------------------------------------------------------------------------

    @Override
    public Scene scene() {
        return ws.scene();
    }

    @Override
    public SceneEditor editor() {
        return ws.editor();
    }

    @Override
    public BlockRegistry registry() {
        return ws.assets() == null ? null : ws.assets().registry();
    }

    @Override
    public String targetVersion() {
        return ws.targetVersionProperty().get().id();
    }

    @Override
    public CompletableFuture<byte[]> renderView(String view, int width, int height) {
        return viewport.captureWhenReady(viewport.presetCamera(view), width, height).thenApply(AssistantController::png);
    }

    @Override
    public void highlight(Box worldBox) {
        viewport.flash(worldBox);
    }

    @Override
    public Layer createLayer(String name) {
        Layer l = new Layer(name, new Structure());
        ws.editor().addLayer(l);
        ws.selectedLayers().setAll(l);
        return l;
    }

    // ---- BuildAgent.Host ----------------------------------------------------------------------------------

    @Override
    public <T> T onOwner(Callable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) return task.call();
        FutureTask<T> f = new FutureTask<>(task);
        Platform.runLater(f);
        return f.get();
    }

    @Override
    public String sceneSummary() {
        Scene s = ws.scene();
        if (s.layers().isEmpty()) return "empty scene, no layers";
        long blocks = s.layers().stream().mapToLong(l -> l.structure().blockCount()).sum();
        String active = s.active().map(l -> "'" + l.name() + "'").orElse("none");
        return s.layers().size() + " layer(s), " + blocks + " blocks, bounds " + s.worldBounds().map(Box::toString).orElse("empty")
                + ", active layer " + active + ", target MC " + targetVersion();
    }

    @Override
    public void beginUndoGroup(String label) {
        ws.editor().undoStack().beginGroup(label);
    }

    @Override
    public void endUndoGroup() {
        ws.editor().undoStack().endGroup();
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    static byte[] png(ViewportRenderer.Frame f) {
        try {
            BufferedImage img = new BufferedImage(f.width(), f.height(), BufferedImage.TYPE_INT_RGB);
            int[] px = new int[f.width() * f.height()];
            f.pixels().clear();
            f.pixels().get(px);
            img.setRGB(0, 0, f.width(), f.height(), px, 0, f.width());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PNG encoding failed", e);
        }
    }
}
