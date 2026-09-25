package io.blockdesigner.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/**
 * Any server speaking the OpenAI Chat Completions API with function calling: OpenAI itself, Ollama, LM Studio,
 * vLLM, OpenRouter and similar. Streams over SSE.
 */
public final class OpenAiCompatibleProvider implements AiProvider {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Endpoint settings, read fresh for each session. */
    public record Endpoint(String name, String baseUrl, String apiKey, List<String> models) {
    }

    private final String id;
    private final Supplier<Endpoint> endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiCompatibleProvider(String id, Supplier<Endpoint> endpoint) {
        this.id = id;
        this.endpoint = endpoint;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return endpoint.get().name();
    }

    @Override
    public List<ModelInfo> models() {
        List<ModelInfo> out = new ArrayList<>();
        for (String m : endpoint.get().models()) if (!m.isBlank()) out.add(new ModelInfo(m.strip(), m.strip(), true));
        return out;
    }

    @Override
    public boolean needsSetup() {
        Endpoint e = endpoint.get();
        return e.baseUrl() == null || e.baseUrl().isBlank() || e.models().stream().allMatch(String::isBlank);
    }

    @Override
    public AiSession open(SessionConfig config) {
        return new Session(endpoint.get(), config);
    }

    private final class Session implements AiSession {
        private final Endpoint ep;
        private final SessionConfig config;
        private final ArrayNode messages = JSON.createArrayNode();
        private final ArrayNode tools = JSON.createArrayNode();
        private volatile InputStream active;
        private volatile boolean cancelled;

        Session(Endpoint ep, SessionConfig config) {
            this.ep = ep;
            this.config = config;
            messages.addObject().put("role", "system").put("content", config.systemPrompt());
            for (ToolSpec t : config.tools()) {
                ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
                fn.put("name", t.name()).put("description", t.description()).set("parameters", t.inputSchema());
            }
        }

        @Override
        public Turn send(List<Part> userContent, Listener listener) throws Exception {
            messages.add(userMessage(userContent));
            return call(listener);
        }

        @Override
        public Turn sendToolResults(List<ToolResult> results, List<Part> extra, Listener listener) throws Exception {
            List<Part> images = new ArrayList<>();
            for (ToolResult r : results) {
                messages.addObject().put("role", "tool").put("tool_call_id", r.callId())
                        .put("content", (r.error() ? "ERROR: " : "") + (r.text() == null ? "" : r.text()));
                if (r.image() != null) images.add(r.image());
            }
            // Tool messages can't carry images in this API, so screenshots follow as a user message.
            List<Part> follow = new ArrayList<>();
            if (!images.isEmpty()) {
                follow.add(new Text("Screenshot(s) from render_view:"));
                follow.addAll(images);
            }
            follow.addAll(extra);
            if (!follow.isEmpty()) messages.add(userMessage(follow));
            return call(listener);
        }

        @Override
        public void cancel() {
            cancelled = true;
            InputStream in = active;
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // aborts the stream
                }
            }
        }

        private ObjectNode userMessage(List<Part> parts) {
            ObjectNode m = JSON.createObjectNode().put("role", "user");
            ArrayNode content = m.putArray("content");
            for (Part p : parts) {
                switch (p) {
                    case Text t -> content.addObject().put("type", "text").put("text", t.text());
                    case Image i -> content.addObject().put("type", "image_url").putObject("image_url")
                            .put("url", "data:" + i.mediaType() + ";base64," + Base64.getEncoder().encodeToString(i.data()));
                }
            }
            return m;
        }

        private Turn call(Listener listener) throws Exception {
            cancelled = false;
            ObjectNode body = JSON.createObjectNode();
            body.put("model", config.model());
            body.set("messages", messages);
            if (!tools.isEmpty()) body.set("tools", tools);
            body.put("stream", true);
            body.put("max_tokens", config.maxTokens());

            String base = ep.baseUrl().endsWith("/") ? ep.baseUrl().substring(0, ep.baseUrl().length() - 1) : ep.baseUrl();
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (ep.apiKey() != null && !ep.apiKey().isBlank()) rb.header("Authorization", "Bearer " + ep.apiKey().strip());

            HttpResponse<InputStream> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("HTTP " + resp.statusCode() + " from " + ep.name() + ": " + err);
            }

            StringBuilder text = new StringBuilder();
            Map<Integer, String[]> calls = new TreeMap<>(); // index -> {id, name, arguments}
            String finish = "other";
            long inTok = 0, outTok = 0;
            active = resp.body();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).strip();
                    if (data.equals("[DONE]")) break;
                    JsonNode ev = JSON.readTree(data);
                    if (ev.has("usage") && ev.get("usage").isObject()) {
                        inTok = ev.get("usage").path("prompt_tokens").asLong(inTok);
                        outTok = ev.get("usage").path("completion_tokens").asLong(outTok);
                    }
                    JsonNode choice = ev.path("choices").path(0);
                    if (choice.isMissingNode()) continue;
                    JsonNode delta = choice.path("delta");
                    String c = delta.path("content").asText("");
                    if (!c.isEmpty()) {
                        text.append(c);
                        listener.onText(c);
                    }
                    String reasoning = delta.path("reasoning_content").asText(delta.path("reasoning").asText(""));
                    if (!reasoning.isEmpty()) listener.onThinking(reasoning);
                    for (JsonNode tc : delta.path("tool_calls")) {
                        int idx = tc.path("index").asInt(calls.size());
                        String[] acc = calls.computeIfAbsent(idx, k -> new String[]{"", "", ""});
                        if (tc.hasNonNull("id")) acc[0] = tc.get("id").asText();
                        JsonNode fn = tc.path("function");
                        if (fn.hasNonNull("name") && acc[1].isEmpty()) {
                            acc[1] = fn.get("name").asText();
                            listener.onToolCallStarted(acc[1]);
                        }
                        if (fn.hasNonNull("arguments")) acc[2] += fn.get("arguments").asText();
                    }
                    if (choice.hasNonNull("finish_reason")) finish = choice.get("finish_reason").asText();
                }
            } catch (Exception e) {
                if (cancelled) throw new CancellationException("Cancelled");
                throw e;
            } finally {
                active = null;
            }
            if (cancelled) throw new CancellationException("Cancelled");

            ObjectNode assistant = messages.addObject().put("role", "assistant");
            if (text.isEmpty()) assistant.putNull("content");
            else assistant.put("content", text.toString());
            List<ToolCall> out = new ArrayList<>();
            if (!calls.isEmpty()) {
                ArrayNode tcs = assistant.putArray("tool_calls");
                int n = 0;
                for (String[] c : calls.values()) {
                    String callId = c[0].isEmpty() ? "call_" + (n++) : c[0];
                    tcs.addObject().put("id", callId).put("type", "function").putObject("function").put("name", c[1]).put("arguments", c[2]);
                    JsonNode args;
                    try {
                        args = c[2].isBlank() ? JSON.createObjectNode() : JSON.readTree(c[2]);
                    } catch (Exception e) {
                        args = JSON.createObjectNode();
                    }
                    out.add(new ToolCall(callId, c[1], args));
                }
            }
            String stop = switch (finish) {
                case "stop" -> "end_turn";
                case "tool_calls", "function_call" -> "tool_use";
                case "length" -> "max_tokens";
                case "content_filter" -> "refusal";
                default -> out.isEmpty() ? "end_turn" : "tool_use";
            };
            return new Turn(text.toString(), out, stop, inTok, outTok);
        }
    }
}
