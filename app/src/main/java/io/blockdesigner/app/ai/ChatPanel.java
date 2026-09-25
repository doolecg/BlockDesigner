package io.blockdesigner.app.ai;

import io.blockdesigner.ai.BuildAgent;
import io.blockdesigner.ai.provider.AiProvider;
import io.blockdesigner.app.Workspace;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The assistant: a chat where the user describes or shows (images) what to build and iterates on it. Text and
 * thinking stream in live; each tool call shows as a chip that turns green or red when done.
 */
public final class ChatPanel extends VBox {
    private static final String[] SUGGESTIONS = {
            "A cozy medieval cottage with a garden",
            "A tall wizard tower with a spiral roof",
            "A Create-style windmill with a stone base",
            "A small Japanese pagoda by a pond",
            "A desert trading post with market stalls"};

    private final Workspace ws;
    private final AssistantController controller;
    private final VBox messages = new VBox(12);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextArea input = new TextArea();
    private final FlowPane attachments = new FlowPane(6, 6);
    private final List<AiProvider.Image> pendingImages = new ArrayList<>();
    private final Button send = new Button(null, new FontIcon(Feather.ARROW_UP));
    private final ComboBox<AiProvider> provider = new ComboBox<>();
    private final ComboBox<AiProvider.ModelInfo> model = new ComboBox<>();
    private final ComboBox<String> effort = new ComboBox<>();
    private final Label usage = new Label();
    private final VBox emptyState;
    private boolean busy;
    private Consumer<String> onTurnFinished = s -> {
    };
    private Runnable onTurnStarting = () -> {
    };
    private String lastPrompt = "";

    public ChatPanel(Workspace ws, AssistantController controller) {
        this.ws = ws;
        this.controller = controller;
        getStyleClass().add("chat-panel");
        setSpacing(8);

        // Header
        Label title = new Label("Assistant");
        title.getStyleClass().add("panel-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button newChat = io.blockdesigner.app.ui.LayersPanel.iconButton(Feather.EDIT, "New conversation (keeps the build)", () -> {
            controller.newConversation();
            messages.getChildren().setAll(emptyStateNode());
        });
        Button settings = io.blockdesigner.app.ui.LayersPanel.iconButton(Feather.SLIDERS, "AI provider settings", this::openSettings);
        HBox header = new HBox(6, title, spacer, newChat, settings);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");

        provider.getItems().setAll(controller.providers());
        provider.setValue(controller.provider(ws.settings().aiProvider));
        provider.setMaxWidth(Double.MAX_VALUE);
        provider.getStyleClass().add("small");
        provider.valueProperty().addListener((o, a, p) -> {
            ws.settings().aiProvider = p.id();
            refreshModels();
        });
        model.getStyleClass().add("small");
        model.setMaxWidth(Double.MAX_VALUE);
        model.valueProperty().addListener((o, a, m) -> {
            if (m != null) ws.settings().aiModel = m.id();
        });
        effort.getItems().setAll("low", "medium", "high", "xhigh", "max");
        effort.setValue(ws.settings().aiEffort);
        effort.getStyleClass().add("small");
        effort.setTooltip(new Tooltip("Reasoning effort: higher builds more carefully but takes longer"));
        effort.valueProperty().addListener((o, a, e) -> ws.settings().aiEffort = e);
        provider.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(AiProvider p) {
                return p == null ? "" : p.displayName();
            }

            @Override
            public AiProvider fromString(String s) {
                return null;
            }
        });
        effort.setPrefWidth(96);
        HBox modelRow = new HBox(6, model, effort);
        HBox.setHgrow(model, Priority.ALWAYS);
        VBox pickers = new VBox(6, provider, modelRow);
        refreshModels();

        // Messages
        messages.setPadding(new Insets(4, 6, 4, 2));
        messages.getStyleClass().add("chat-messages");
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        emptyState = emptyStateNode();
        messages.getChildren().add(emptyState);

        // Composer
        input.setPromptText("Describe what to build, or drop an image…\nEnter to send · Shift+Enter for a new line");
        input.setWrapText(true);
        input.setPrefRowCount(3);
        input.getStyleClass().add("chat-input");
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendOrStop();
            } else if (e.getCode() == KeyCode.V && e.isShortcutDown() && Clipboard.getSystemClipboard().hasImage()) {
                e.consume();
                attach(Clipboard.getSystemClipboard().getImage());
            }
        });
        Button attach = io.blockdesigner.app.ui.LayersPanel.iconButton(Feather.IMAGE, "Attach reference image", this::pickImage);
        send.getStyleClass().addAll("accent", "send-button");
        send.setOnAction(e -> sendOrStop());
        HBox actions = new HBox(6, attach, usage, new Region(), send);
        HBox.setHgrow(actions.getChildren().get(2), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_LEFT);
        usage.getStyleClass().add("layer-meta");
        VBox composer = new VBox(6, attachments, input, actions);
        composer.getStyleClass().add("chat-composer");

        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && e.getDragboard().getFiles().stream().anyMatch(ChatPanel::isImage)) {
                e.acceptTransferModes(TransferMode.COPY);
                e.consume();
            }
        });
        setOnDragDropped(e -> {
            boolean any = false;
            for (File f : e.getDragboard().getFiles()) {
                if (isImage(f)) {
                    attachFile(f);
                    any = true;
                }
            }
            e.setDropCompleted(any);
            e.consume();
        });

        getChildren().addAll(header, pickers, scroll, composer);
    }

    /** Called with a short label (the prompt) after each successful assistant turn, e.g. to snapshot the timeline. */
    public void setOnTurnFinished(Consumer<String> c) {
        this.onTurnFinished = c;
    }

    /** Called just before a turn starts (e.g. to snapshot the pre-assistant state). */
    public void setOnTurnStarting(Runnable r) {
        this.onTurnStarting = r;
    }

    private void refreshModels() {
        AiProvider p = provider.getValue();
        model.getItems().setAll(p.models());
        p.models().stream().filter(m -> m.id().equals(ws.settings().aiModel)).findFirst()
                .ifPresentOrElse(model::setValue, () -> {
                    if (!model.getItems().isEmpty()) model.setValue(model.getItems().getFirst());
                });
        effort.setDisable(!p.id().equals("anthropic"));
    }

    private VBox emptyStateNode() {
        Label hi = new Label("What should we build?");
        hi.getStyleClass().add("chat-empty-title");
        Label sub = new Label("Describe a structure or drop a reference image. The build appears live in the viewport, "
                + "and you can keep refining it: “make the roof steeper”, “add a chimney”.");
        sub.setWrapText(true);
        sub.getStyleClass().add("layer-meta");
        FlowPane chips = new FlowPane(6, 6);
        for (String s : SUGGESTIONS) {
            Button b = new Button(s);
            b.getStyleClass().addAll("chip", "suggestion");
            b.setOnAction(e -> {
                input.setText(s);
                sendOrStop();
            });
            chips.getChildren().add(b);
        }
        VBox v = new VBox(10, hi, sub, chips);
        v.getStyleClass().add("chat-empty");
        return v;
    }

    // ---- attachments ----------------------------------------------------------------------------------------

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif");
    }

    private void pickImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Reference image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
        List<File> files = fc.showOpenMultipleDialog(getScene().getWindow());
        if (files != null) files.forEach(this::attachFile);
    }

    private void attachFile(File f) {
        try {
            byte[] data = Files.readAllBytes(f.toPath());
            String n = f.getName().toLowerCase();
            String mime = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : n.endsWith(".gif") ? "image/gif" : "image/jpeg";
            addAttachment(downscale(data, mime));
        } catch (Exception ex) {
            ws.statusProperty().set("Could not read image: " + ex.getMessage());
        }
    }

    private void attach(Image fx) {
        int w = (int) fx.getWidth(), h = (int) fx.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        fx.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, w);
        img.setRGB(0, 0, w, h, px, 0, w);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            addAttachment(downscale(out.toByteArray(), "image/png"));
        } catch (Exception ex) {
            ws.statusProperty().set("Could not paste image: " + ex.getMessage());
        }
    }

    /** Keeps images at most ~1568px on the long side (larger ones only cost tokens). */
    private static AiProvider.Image downscale(byte[] data, String mime) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
        if (src == null) return new AiProvider.Image(data, mime);
        int max = 1568, w = src.getWidth(), h = src.getHeight();
        if (Math.max(w, h) <= max && data.length < 4_500_000) return new AiProvider.Image(data, mime);
        double s = max / (double) Math.max(w, h);
        int nw = Math.max(1, (int) (w * s)), nh = Math.max(1, (int) (h * s));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        var g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(dst, "jpg", out);
        return new AiProvider.Image(out.toByteArray(), "image/jpeg");
    }

    private void addAttachment(AiProvider.Image img) {
        pendingImages.add(img);
        ImageView iv = new ImageView(new Image(new ByteArrayInputStream(img.data())));
        iv.setFitHeight(54);
        iv.setPreserveRatio(true);
        Button remove = new Button(null, new FontIcon(Feather.X));
        remove.getStyleClass().addAll("flat", "attachment-remove");
        StackPane tile = new StackPane(iv, remove);
        StackPane.setAlignment(remove, Pos.TOP_RIGHT);
        tile.getStyleClass().add("attachment");
        remove.setOnAction(e -> {
            pendingImages.remove(img);
            attachments.getChildren().remove(tile);
        });
        attachments.getChildren().add(tile);
    }

    // ---- sending -----------------------------------------------------------------------------------------

    private void sendOrStop() {
        if (busy) {
            controller.cancel();
            return;
        }
        String text = input.getText().strip();
        if (text.isEmpty() && pendingImages.isEmpty()) return;
        if (text.isEmpty()) text = "Build what you see in this image as a Minecraft structure.";
        AiProvider p = provider.getValue();
        if (p.needsSetup()) {
            openSettings();
            if (p.needsSetup()) return;
        }
        if (ws.assets() == null) ws.statusProperty().set("Tip: load Minecraft assets so the assistant can validate block ids.");
        AiProvider.ModelInfo m = model.getValue();
        if (m == null) {
            openSettings();
            return;
        }
        messages.getChildren().remove(emptyState);
        messages.getChildren().removeIf(n -> n.getStyleClass().contains("chat-empty"));
        List<AiProvider.Image> images = List.copyOf(pendingImages);
        addUserBubble(text, images);
        input.clear();
        pendingImages.clear();
        attachments.getChildren().clear();

        lastPrompt = text.length() > 36 ? text.substring(0, 36) + "…" : text;
        onTurnStarting.run();
        BuildAgent agent = controller.agent(p.id(), m.id(), effort.getValue());
        AssistantBubble bubble = new AssistantBubble();
        messages.getChildren().add(bubble);
        setBusy(true);
        String prompt = text;
        controller.submit(() -> agent.run(prompt, images, bubble));
        ws.settings().save();
    }

    private void setBusy(boolean b) {
        busy = b;
        send.setGraphic(new FontIcon(b ? Feather.SQUARE : Feather.ARROW_UP));
        send.setTooltip(new Tooltip(b ? "Stop" : "Send (Enter)"));
        provider.setDisable(b);
        model.setDisable(b);
    }

    private void addUserBubble(String text, List<AiProvider.Image> images) {
        VBox v = new VBox(6);
        v.getStyleClass().addAll("bubble", "bubble-user");
        if (!images.isEmpty()) {
            FlowPane imgs = new FlowPane(4, 4);
            for (AiProvider.Image i : images) {
                ImageView iv = new ImageView(new Image(new ByteArrayInputStream(i.data())));
                iv.setFitHeight(80);
                iv.setPreserveRatio(true);
                imgs.getChildren().add(iv);
            }
            v.getChildren().add(imgs);
        }
        Label l = new Label(text);
        l.setWrapText(true);
        v.getChildren().add(l);
        HBox row = new HBox(v);
        row.setAlignment(Pos.CENTER_RIGHT);
        v.setMaxWidth(300);
        messages.getChildren().add(row);
    }

    private void openSettings() {
        AiSettingsDialog d = new AiSettingsDialog(getScene().getWindow(), ws.settings());
        d.showAndWait();
        ws.settings().save();
        refreshModels();
    }

    /** Streams one assistant turn: thinking, text segments and tool chips in order. */
    private final class AssistantBubble extends VBox implements BuildAgent.Events {
        private final StringBuilder pendingText = new StringBuilder();
        private final StringBuilder pendingThinking = new StringBuilder();
        private boolean flushScheduled;
        private Label currentText;
        private FlowPane currentChips;
        private Label thinkingLabel;
        private TitledPane thinkingPane;
        private final Map<String, Label> chips = new HashMap<>();
        private final Label working = new Label("Thinking…", new FontIcon(Feather.LOADER));
        private long inTok, outTok;

        AssistantBubble() {
            super(6);
            getStyleClass().addAll("bubble", "bubble-assistant");
            working.getStyleClass().add("chat-working");
            getChildren().add(working);
        }

        private synchronized void queue(StringBuilder sb, String delta) {
            sb.append(delta);
            if (!flushScheduled) {
                flushScheduled = true;
                Platform.runLater(this::flush);
            }
        }

        private void flush() {
            String t, th;
            synchronized (this) {
                t = pendingText.toString();
                th = pendingThinking.toString();
                pendingText.setLength(0);
                pendingThinking.setLength(0);
                flushScheduled = false;
            }
            if (!th.isEmpty()) {
                if (thinkingPane == null) {
                    thinkingLabel = new Label();
                    thinkingLabel.setWrapText(true);
                    thinkingLabel.getStyleClass().add("thinking-text");
                    thinkingPane = new TitledPane("Thinking", thinkingLabel);
                    thinkingPane.setExpanded(false);
                    thinkingPane.getStyleClass().add("thinking-pane");
                    getChildren().add(getChildren().indexOf(working), thinkingPane);
                }
                thinkingLabel.setText(thinkingLabel.getText() + th);
            }
            if (!t.isEmpty()) {
                if (currentText == null) {
                    currentText = new Label();
                    currentText.setWrapText(true);
                    currentText.getStyleClass().add("assistant-text");
                    getChildren().add(getChildren().indexOf(working), currentText);
                    currentChips = null;
                }
                currentText.setText(currentText.getText() + t);
                working.setText("Writing…");
            }
        }

        @Override
        public void onText(String delta) {
            queue(pendingText, delta);
        }

        @Override
        public void onThinking(String delta) {
            queue(pendingThinking, delta);
        }

        @Override
        public void onToolStarted(String callId, String name, String description) {
            Platform.runLater(() -> {
                flush();
                currentText = null;
                if (currentChips == null) {
                    currentChips = new FlowPane(4, 4);
                    getChildren().add(getChildren().indexOf(working), currentChips);
                }
                Label chip = new Label(description, new FontIcon(Feather.LOADER));
                chip.getStyleClass().addAll("tool-chip", "running");
                chips.put(callId, chip);
                currentChips.getChildren().add(chip);
                working.setText("Building…");
            });
        }

        @Override
        public void onToolFinished(String callId, String name, String description, boolean ok, String result, byte[] image) {
            Platform.runLater(() -> {
                Label chip = chips.get(callId);
                if (chip == null) return;
                chip.getStyleClass().remove("running");
                chip.getStyleClass().add(ok ? "ok" : "failed");
                chip.setGraphic(new FontIcon(ok ? (image != null ? Feather.CAMERA : Feather.CHECK) : Feather.ALERT_TRIANGLE));
                String tip = result == null ? "" : result.length() > 1200 ? result.substring(0, 1200) + "…" : result;
                chip.setTooltip(new Tooltip(tip));
                if (image != null) {
                    Image img = new Image(new ByteArrayInputStream(image));
                    chip.setOnMouseClicked(e -> showImage(img));
                    ImageView thumb = new ImageView(img);
                    thumb.setFitWidth(220);
                    thumb.setPreserveRatio(true);
                    thumb.getStyleClass().add("shot-thumb");
                    thumb.setOnMouseClicked(e -> showImage(img));
                    getChildren().add(getChildren().indexOf(working), thumb);
                    currentChips = null;
                }
            });
        }

        @Override
        public void onRound(int round, long inputTokens, long outputTokens) {
            inTok += inputTokens;
            outTok += outputTokens;
            long in = inTok, out = outTok;
            Platform.runLater(() -> usage.setText(String.format("%,d in · %,d out tokens", in, out)));
        }

        @Override
        public void onFinished(String stopReason) {
            Platform.runLater(() -> {
                flush();
                getChildren().remove(working);
                if ("cancelled".equals(stopReason)) addNote("Stopped.");
                else if ("max_tokens".equals(stopReason)) addNote("Reply was cut off (max tokens).");
                setBusy(false);
                onTurnFinished.accept(lastPrompt);
            });
        }

        @Override
        public void onError(Throwable t) {
            Platform.runLater(() -> {
                flush();
                getChildren().remove(working);
                Throwable c = t;
                while (c.getCause() != null && c.getCause() != c) c = c.getCause();
                addNote("Error: " + (c.getMessage() == null ? c.toString() : c.getMessage()));
                setBusy(false);
            });
        }

        private void addNote(String s) {
            Label l = new Label(s);
            l.setWrapText(true);
            l.getStyleClass().add("chat-note");
            getChildren().add(l);
        }
    }

    private void showImage(Image img) {
        Stage st = new Stage();
        st.initOwner(getScene().getWindow());
        st.initModality(Modality.NONE);
        st.setTitle("Assistant's view");
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(Math.min(1024, img.getWidth()));
        st.setScene(new javafx.scene.Scene(new StackPane(iv)));
        st.show();
    }
}
