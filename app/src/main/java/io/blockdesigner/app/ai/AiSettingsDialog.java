package io.blockdesigner.app.ai;

import io.blockdesigner.app.SecretStore;
import io.blockdesigner.app.Settings;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** API keys and endpoints for the AI providers. Keys are stored encrypted for the current Windows user. */
final class AiSettingsDialog extends Dialog<Void> {

    AiSettingsDialog(Window owner, Settings s) {
        initOwner(owner);
        setTitle("AI providers");
        setHeaderText("Connect an AI model");

        PasswordField anthropicKey = new PasswordField();
        boolean hasKey = s.anthropicKeyProtected != null && !s.anthropicKeyProtected.isBlank();
        anthropicKey.setPromptText(hasKey ? "•••••• saved (type to replace)" : System.getenv("ANTHROPIC_API_KEY") != null
                ? "Using ANTHROPIC_API_KEY from the environment" : "sk-ant-…");
        CheckBox fallback = new CheckBox("If a request is declined, retry automatically on a fallback model (server-side)");
        fallback.setSelected(s.aiRefusalFallback);
        Label anthropicHint = new Label("Get a key at console.anthropic.com. Without one, BlockDesigner uses ANTHROPIC_API_KEY or your `ant auth login` profile.");
        anthropicHint.setWrapText(true);
        anthropicHint.getStyleClass().add("layer-meta");

        ComboBox<String> preset = new ComboBox<>();
        preset.getItems().addAll("Ollama (local)", "LM Studio (local)", "OpenAI", "Custom");
        TextField name = new TextField(s.openAiName);
        TextField baseUrl = new TextField(s.openAiBaseUrl);
        PasswordField openAiKey = new PasswordField();
        openAiKey.setPromptText(s.openAiKeyProtected != null && !s.openAiKeyProtected.isBlank() ? "•••••• saved" : "optional for local servers");
        TextField models = new TextField(s.openAiModels);
        models.setPromptText("comma-separated, e.g. qwen3:32b, llama4");
        preset.setOnAction(e -> {
            switch (preset.getValue()) {
                case "Ollama (local)" -> {
                    name.setText("Ollama");
                    baseUrl.setText("http://localhost:11434/v1");
                }
                case "LM Studio (local)" -> {
                    name.setText("LM Studio");
                    baseUrl.setText("http://localhost:1234/v1");
                }
                case "OpenAI" -> {
                    name.setText("OpenAI");
                    baseUrl.setText("https://api.openai.com/v1");
                }
                default -> {
                }
            }
        });
        Label openHint = new Label("Any server with the OpenAI Chat Completions API and tool calling. Vision-capable models can use reference images and screenshots.");
        openHint.setWrapText(true);
        openHint.getStyleClass().add("layer-meta");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(9);
        g.setPadding(new Insets(6, 2, 2, 2));
        int r = 0;
        Label claude = new Label("Claude (Anthropic)");
        claude.getStyleClass().add("selected-block-name");
        g.add(claude, 0, r++, 2, 1);
        g.addRow(r++, new Label("API key"), anthropicKey);
        g.add(fallback, 1, r++);
        g.add(anthropicHint, 1, r++);
        g.add(new Separator(), 0, r++, 2, 1);
        Label oa = new Label("OpenAI-compatible");
        oa.getStyleClass().add("selected-block-name");
        g.add(oa, 0, r++, 2, 1);
        g.addRow(r++, new Label("Preset"), preset);
        g.addRow(r++, new Label("Display name"), name);
        g.addRow(r++, new Label("Base URL"), baseUrl);
        g.addRow(r++, new Label("API key"), openAiKey);
        g.addRow(r++, new Label("Models"), models);
        g.add(openHint, 1, r);
        GridPane.setHgrow(anthropicKey, Priority.ALWAYS);
        g.setPrefWidth(600);
        getDialogPane().setContent(g);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            if (!anthropicKey.getText().isBlank()) s.anthropicKeyProtected = SecretStore.protect(anthropicKey.getText());
            s.aiRefusalFallback = fallback.isSelected();
            s.openAiName = name.getText().strip();
            s.openAiBaseUrl = baseUrl.getText().strip();
            if (!openAiKey.getText().isBlank()) s.openAiKeyProtected = SecretStore.protect(openAiKey.getText());
            s.openAiModels = models.getText().strip();
            return null;
        });
    }
}
