package io.blockdesigner.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A chat model backend. Implementations own their native conversation format (so provider-specific state such as
 * thinking blocks round-trips exactly); the agent loop only sees {@link AiSession}.
 */
public interface AiProvider {

    String id();

    String displayName();

    /** Models to offer in the UI, best default first. */
    List<ModelInfo> models();

    /** True when no key/endpoint is configured yet. */
    boolean needsSetup();

    AiSession open(SessionConfig config) throws Exception;

    record ModelInfo(String id, String label, boolean vision) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @param effort reasoning effort hint: low, medium, high, xhigh or max (providers may ignore it)
     */
    record SessionConfig(String model, String systemPrompt, List<ToolSpec> tools, String effort, int maxTokens) {
    }

    /** A client-side tool the model may call, described by JSON Schema. */
    record ToolSpec(String name, String description, JsonNode inputSchema) {
    }

    /** One piece of user content. */
    sealed interface Part permits Text, Image {
    }

    record Text(String text) implements Part {
    }

    /** Encoded image bytes (PNG/JPEG/WebP/GIF). */
    record Image(byte[] data, String mediaType) implements Part {
    }

    record ToolCall(String id, String name, JsonNode input) {
    }

    /** Output of a tool. {@code image} may be null. */
    record ToolResult(String callId, String text, Image image, boolean error) {
    }

    /**
     * One model response.
     *
     * @param stopReason provider stop reason, normalised to end_turn, tool_use, max_tokens, refusal or other
     */
    record Turn(String text, List<ToolCall> toolCalls, String stopReason, long inputTokens, long outputTokens) {
    }

    /** Streaming callbacks, invoked on the provider's thread. */
    interface Listener {
        default void onText(String delta) {
        }

        default void onThinking(String delta) {
        }

        /** A tool call started streaming (its input may still be arriving). */
        default void onToolCallStarted(String name) {
        }
    }

    interface AiSession {
        Turn send(List<Part> userContent, Listener listener) throws Exception;

        /** Returns tool results for the previous turn's calls, plus optional extra user content. */
        Turn sendToolResults(List<ToolResult> results, List<Part> extra, Listener listener) throws Exception;

        /** Aborts an in-flight request from another thread. */
        void cancel();
    }
}
