package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.config.OpenAiProperties;
import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import com.ankush.workflowEngine.support.TemplateRenderer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ChatGptNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGptNodeExecutor.class);

    private final RestClient.Builder restClientBuilder;
    private final OpenAiProperties properties;

    public ChatGptNodeExecutor(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
    }

    @Override
    public NodeType supportsType() {
        return NodeType.CHATGPT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> snapshot = context.context().snapshot();

        String promptTemplate = Objects.toString(config.get("prompt"), "Provide a summary");
        String resolvedPrompt = TemplateRenderer.render(promptTemplate, snapshot);
        if (resolvedPrompt == null || resolvedPrompt.isBlank()) {
            throw new NodeExecutionException("ChatGPT prompt resolved to empty value");
        }

        String model = config.get("model") != null ? config.get("model").toString() : properties.getDefaultModel();
        Double temperature = config.get("temperature") instanceof Number num ? num.doubleValue() : null;
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new NodeExecutionException("OpenAI API key is not configured");
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", List.of(Map.of("role", "user", "content", resolvedPrompt)));
        if (temperature != null) {
            request.put("temperature", temperature);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClientBuilder.build()
                    .post()
                    .uri(properties.getBaseUrl() + "/chat/completions")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            String content = extractContent(response);
            Map<String, Object> output = Map.of(
                    context.node().getNodeKey() + "::response", content,
                    "model", model,
                    "prompt", resolvedPrompt);
            context.context().merge(output);
            LOGGER.info("[FlowStack] ChatGPT node {} invoked model {}", context.node().getNodeKey(), model);
            return NodeExecutionResult.completed(output, "chatgpt response");
        } catch (RestClientException ex) {
            LOGGER.error("[FlowStack] ChatGPT node {} failed: {}", context.node().getNodeKey(), ex.getMessage());
            throw new NodeExecutionException("ChatGPT call failed", ex);
        }
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object choicesObj = response.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
            Object first = choices.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object messageObj = firstMap.get("message");
                if (messageObj instanceof Map<?, ?> messageMap) {
                    Object content = messageMap.get("content");
                    if (content != null) {
                        return content.toString();
                    }
                } else if (firstMap.get("text") != null) {
                    return firstMap.get("text").toString();
                }
            }
        }
        return "";
    }
}
