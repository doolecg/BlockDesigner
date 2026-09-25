package io.blockdesigner.ai;

import io.blockdesigner.ai.provider.AiProvider;
import io.blockdesigner.ai.provider.AiProvider.AiSession;
import io.blockdesigner.ai.provider.AiProvider.Part;
import io.blockdesigner.ai.provider.AiProvider.ToolCall;
import io.blockdesigner.ai.provider.AiProvider.ToolResult;
import io.blockdesigner.ai.provider.AiProvider.Turn;
import io.blockdesigner.ai.tools.BuildTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/**
 * Runs the tool-use loop: sends the user's prompt (text + images), executes each requested build tool on the owner
 * thread (so the viewport updates live), feeds results (including screenshots) back, and repeats until the model
 * stops calling tools. A whole user turn is one undo step.
 */
public final class BuildAgent {

    /** Hooks into the host application. */
    public interface Host {
        /** Runs {@code task} on the thread that owns the scene and waits for its result. */
        <T> T onOwner(Callable<T> task) throws Exception;

        /** One-line scene summary prepended to each user message (called via {@link #onOwner}). */
        String sceneSummary();

        void beginUndoGroup(String label);

        void endUndoGroup();
    }

    /** UI callbacks; invoked from the agent's background thread. */
    public interface Events {
        default void onText(String delta) {
        }

        default void onThinking(String delta) {
        }

        default void onToolStarted(String callId, String name, String description) {
        }

        default void onToolFinished(String callId, String name, String description, boolean ok, String result, byte[] image) {
        }

        default void onRound(int round, long inputTokens, long outputTokens) {
        }

        default void onFinished(String stopReason) {
        }

        default void onError(Throwable t) {
        }
    }

    public record Options(String model, String effort, int maxRounds, int maxTokens) {
        public static Options defaults(String model) {
            return new Options(model, "high", 60, 64000);
        }
    }

    private final AiProvider provider;
    private final BuildTools tools;
    private final Host host;
    private Options options;
    private AiSession session;
    private volatile boolean cancelled;
    private volatile AiSession running;

    public BuildAgent(AiProvider provider, BuildTools tools, Host host, Options options) {
        this.provider = provider;
        this.tools = tools;
        this.host = host;
        this.options = options;
    }

    public static String systemPrompt() {
        try (InputStream in = BuildAgent.class.getResourceAsStream("/io/blockdesigner/ai/system_prompt.md")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public AiProvider provider() {
        return provider;
    }

    /** Changing model/effort starts a fresh conversation. */
    public void setOptions(Options o) {
        if (!o.model().equals(options.model()) || !o.effort().equals(options.effort())) session = null;
        options = o;
    }

    /** Forget the conversation (the build itself is untouched). */
    public void reset() {
        session = null;
    }

    public void cancel() {
        cancelled = true;
        AiSession s = running;
        if (s != null) s.cancel();
    }

    /** Blocking; call from a background thread. */
    public void run(String prompt, List<AiProvider.Image> images, Events ev) {
        cancelled = false;
        boolean grouped = false;
        try {
            if (session == null) {
                session = provider.open(new AiProvider.SessionConfig(options.model(), systemPrompt(), BuildTools.specs(),
                        options.effort(), options.maxTokens()));
            }
            AiSession s = session;
            running = s;
            String summary = host.onOwner(host::sceneSummary);
            List<Part> parts = new ArrayList<>(images);
            parts.add(new AiProvider.Text("[Scene: " + summary + "]\n\n" + prompt));

            String label = "AI: " + (prompt.length() > 40 ? prompt.substring(0, 40) + "…" : prompt);
            host.onOwner(() -> {
                host.beginUndoGroup(label);
                return null;
            });
            grouped = true;

            AiProvider.Listener l = new AiProvider.Listener() {
                @Override
                public void onText(String delta) {
                    ev.onText(delta);
                }

                @Override
                public void onThinking(String delta) {
                    ev.onThinking(delta);
                }
            };

            Turn turn = s.send(parts, l);
            ev.onRound(0, turn.inputTokens(), turn.outputTokens());
            int round = 0;
            while (!turn.toolCalls().isEmpty() && !cancelled) {
                if (++round > options.maxRounds()) {
                    ev.onText("\n\n(Stopped after " + options.maxRounds() + " tool rounds. Send another message to continue.)");
                    break;
                }
                List<ToolResult> results = new ArrayList<>();
                boolean truncated = turn.stopReason().equals("max_tokens");
                for (ToolCall call : turn.toolCalls()) {
                    String desc = BuildTools.describe(call.name(), call.input());
                    ev.onToolStarted(call.id(), call.name(), desc);
                    if (truncated || cancelled) {
                        String msg = truncated ? "Not run: the response hit max_tokens, so this call's input may be incomplete. Retry with smaller calls."
                                : "Cancelled by the user.";
                        results.add(new ToolResult(call.id(), msg, null, true));
                        ev.onToolFinished(call.id(), call.name(), desc, false, msg, null);
                        continue;
                    }
                    BuildTools.Outcome o = host.onOwner(() -> tools.execute(call.name(), call.input()));
                    byte[] png = null;
                    if (o.image() != null) {
                        try {
                            png = o.image().get(60, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            o = new BuildTools.Outcome("Rendering failed: " + e.getMessage(), null, true);
                        }
                    }
                    AiProvider.Image img = png == null ? null : new AiProvider.Image(png, "image/png");
                    results.add(new ToolResult(call.id(), o.text(), img, o.error()));
                    ev.onToolFinished(call.id(), call.name(), desc, !o.error(), o.text(), png);
                }
                if (cancelled) break;
                turn = s.sendToolResults(results, List.of(), l);
                ev.onRound(round, turn.inputTokens(), turn.outputTokens());
            }
            // Unanswered tool calls would make the next request invalid; start a fresh conversation in that case.
            if (!turn.toolCalls().isEmpty()) session = null;
            ev.onFinished(cancelled ? "cancelled" : turn.stopReason());
        } catch (CancellationException e) {
            ev.onFinished("cancelled");
            // The provider may be mid-turn with unanswered tool calls; start clean next time.
            session = null;
        } catch (Throwable t) {
            session = null;
            ev.onError(t);
        } finally {
            running = null;
            if (grouped) {
                try {
                    host.onOwner(() -> {
                        host.endUndoGroup();
                        return null;
                    });
                } catch (Exception ignored) {
                    // host shutting down
                }
            }
        }
    }
}
