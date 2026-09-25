package io.blockdesigner.ai.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Claude via the official Anthropic Java SDK: streaming, adaptive thinking (summaries shown in the chat), client-side
 * build tools with eager input streaming, prompt caching, and the server-side refusal fallback.
 */
public final class ClaudeProvider implements AiProvider {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Supplier<String> apiKey;
    private final Supplier<Boolean> useFallbacks;

    /**
     * @param apiKey       key from the app's settings (may return blank; the SDK then falls back to
     *                     {@code ANTHROPIC_API_KEY} or an {@code ant auth login} profile)
     * @param useFallbacks whether to opt in to the server-side refusal fallback
     */
    public ClaudeProvider(Supplier<String> apiKey, Supplier<Boolean> useFallbacks) {
        this.apiKey = apiKey;
        this.useFallbacks = useFallbacks;
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public String displayName() {
        return "Claude (Anthropic)";
    }

    @Override
    public List<ModelInfo> models() {
        return List.of(
                new ModelInfo("claude-opus-5", "Claude Opus 5", true),
                new ModelInfo("claude-opus-5-5", "Claude Opus 5.5", true),
                new ModelInfo("claude-fable-5-1", "Claude Fable 5.1 — most capable", true),
                new ModelInfo("claude-sonnet-5", "Claude Sonnet 5 — faster", true),
                new ModelInfo("claude-haiku-4-5", "Claude Haiku 4.5 — fastest", true));
    }

    @Override
    public boolean needsSetup() {
        String k = apiKey.get();
        return (k == null || k.isBlank()) && System.getenv("ANTHROPIC_API_KEY") == null && System.getenv("ANTHROPIC_AUTH_TOKEN") == null;
    }

    private AnthropicClient client() {
        String k = apiKey.get();
        if (k != null && !k.isBlank()) return AnthropicOkHttpClient.builder().apiKey(k.strip()).build();
        return AnthropicOkHttpClient.fromEnv();
    }

    @Override
    public AiSession open(SessionConfig config) {
        return new Session(client(), config);
    }

    private final class Session implements AiSession {
        private final AnthropicClient client;
        private final SessionConfig config;
        private final List<Tool> tools = new ArrayList<>();
        private final List<MessageParam> history = new ArrayList<>();
        private volatile StreamResponse<RawMessageStreamEvent> active;
        private volatile boolean cancelled;

        Session(AnthropicClient client, SessionConfig config) {
            this.client = client;
            this.config = config;
            for (ToolSpec t : config.tools()) tools.add(tool(t));
        }

        @Override
        public Turn send(List<Part> userContent, Listener listener) throws Exception {
            List<ContentBlockParam> blocks = new ArrayList<>();
            for (Part p : userContent) blocks.add(block(p));
            history.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build());
            return call(listener);
        }

        @Override
        public Turn sendToolResults(List<ToolResult> results, List<Part> extra, Listener listener) throws Exception {
            List<ContentBlockParam> blocks = new ArrayList<>();
            // All results for one assistant turn go back in a single user message.
            for (ToolResult r : results) {
                List<ToolResultBlockParam.Content.Block> content = new ArrayList<>();
                content.add(ToolResultBlockParam.Content.Block.ofText(r.text() == null || r.text().isEmpty() ? "(no output)" : r.text()));
                if (r.image() != null) content.add(ToolResultBlockParam.Content.Block.ofImage(image(r.image())));
                blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.callId())
                        .contentOfBlocks(content)
                        .isError(r.error())
                        .build()));
            }
            for (Part p : extra) blocks.add(block(p));
            history.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build());
            return call(listener);
        }

        @Override
        public void cancel() {
            cancelled = true;
            StreamResponse<RawMessageStreamEvent> s = active;
            if (s != null) {
                try {
                    s.close();
                } catch (Exception ignored) {
                    // closing aborts the HTTP stream
                }
            }
        }

        private Turn call(Listener listener) throws Exception {
            cancelled = false;
            String model = config.model();
            boolean haiku = model.startsWith("claude-haiku");
            MessageCreateParams.Builder b = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(config.maxTokens())
                    .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                            .text(config.systemPrompt())
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build()))
                    .messages(history)
                    // Automatic caching of the growing conversation prefix across agent rounds.
                    .cacheControl(CacheControlEphemeral.builder().build());
            for (Tool t : tools) b.addTool(t);
            if (!haiku) {
                b.thinking(ThinkingConfigAdaptive.builder().display(ThinkingConfigAdaptive.Display.SUMMARIZED).build());
                b.outputConfig(OutputConfig.builder().effort(effort(config.effort())).build());
            }
            if (useFallbacks.get() && (model.startsWith("claude-opus-5") || model.startsWith("claude-fable"))) {
                b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
                b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            }

            MessageAccumulator acc = MessageAccumulator.create();
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(b.build())) {
                active = stream;
                Iterator<RawMessageStreamEvent> it = stream.stream().iterator();
                while (it.hasNext()) {
                    RawMessageStreamEvent ev = acc.accumulate(it.next());
                    ev.contentBlockStart().ifPresent(start -> start.contentBlock().toolUse()
                            .ifPresent(tu -> listener.onToolCallStarted(tu.name())));
                    ev.contentBlockDelta().ifPresent(d -> {
                        d.delta().text().ifPresent(t -> listener.onText(t.text()));
                        d.delta().thinking().ifPresent(t -> listener.onThinking(t.thinking()));
                    });
                }
            } catch (RuntimeException e) {
                if (cancelled) throw new java.util.concurrent.CancellationException("Cancelled");
                throw e;
            } finally {
                active = null;
            }
            if (cancelled) throw new java.util.concurrent.CancellationException("Cancelled");

            Message m = acc.message();
            // Replay the assistant turn exactly (including thinking blocks) on the next request.
            history.add(m.toParam());

            StringBuilder text = new StringBuilder();
            List<ToolCall> calls = new ArrayList<>();
            for (ContentBlock cb : m.content()) {
                cb.text().ifPresent(t -> text.append(t.text()));
                cb.toolUse().ifPresent(tu -> calls.add(new ToolCall(tu.id(), tu.name(), toJackson(tu._input()))));
            }
            String stop = m.stopReason().map(s -> s.toString().toLowerCase(Locale.ROOT)).orElse("other");
            if (stop.equals("refusal")) {
                String why = m.stopDetails().flatMap(d -> d.explanation()).orElse("");
                text.append(text.isEmpty() ? "" : "\n\n").append("(The model declined this request").append(why.isEmpty() ? "" : ": " + why).append(")");
            }
            return new Turn(text.toString(), calls, stop, m.usage().inputTokens(), m.usage().outputTokens());
        }
    }

    private static OutputConfig.Effort effort(String e) {
        if (e == null) return OutputConfig.Effort.HIGH;
        return switch (e.toLowerCase(Locale.ROOT)) {
            case "low" -> OutputConfig.Effort.LOW;
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> OutputConfig.Effort.HIGH;
        };
    }

    private static Tool tool(ToolSpec spec) {
        JsonNode schema = spec.inputSchema();
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        schema.path("properties").properties().forEach(e ->
                props.putAdditionalProperty(e.getKey(), JsonValue.from(JSON.convertValue(e.getValue(), Object.class))));
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(r -> required.add(r.asText()));
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                // Stream large inputs (e.g. hundreds of set_blocks entries) as they're generated; inputs are validated
                // by the tool executor before running.
                .putAdditionalProperty("eager_input_streaming", JsonValue.from(true))
                .build();
    }

    private static ContentBlockParam block(Part p) {
        return switch (p) {
            case Text t -> ContentBlockParam.ofText(TextBlockParam.builder().text(t.text()).build());
            case Image i -> ContentBlockParam.ofImage(image(i));
        };
    }

    private static ImageBlockParam image(Image i) {
        return ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .data(Base64.getEncoder().encodeToString(i.data()))
                        .mediaType(Base64ImageSource.MediaType.of(i.mediaType()))
                        .build())
                .build();
    }

    /** SDK JSON value → Jackson tree. Truncated or non-object inputs become an empty object for the validator to reject. */
    private static JsonNode toJackson(JsonValue v) {
        try {
            JsonNode n = ObjectMappers.jsonMapper().valueToTree(v);
            return n == null || !n.isObject() ? JSON.createObjectNode() : JSON.readTree(n.toString());
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }
}
