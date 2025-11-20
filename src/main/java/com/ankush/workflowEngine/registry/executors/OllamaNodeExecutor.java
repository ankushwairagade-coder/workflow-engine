package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import com.ankush.workflowEngine.support.OllamaClient;
import com.ankush.workflowEngine.support.TemplateRenderer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OllamaNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaNodeExecutor.class);

    private final OllamaClient ollamaClient;

    public OllamaNodeExecutor(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public NodeType supportsType() {
        return NodeType.OLLAMA;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> snapshot = context.context().snapshot();
        String promptTemplate = String.valueOf(context.config().getOrDefault("prompt", "FlowStack prompt"));
        String resolvedPrompt = TemplateRenderer.render(promptTemplate, snapshot);
        String model = (String) context.config().get("model");
        String response = ollamaClient.generateText(model, resolvedPrompt);
        Map<String, Object> output = Map.of(
                context.node().getNodeKey() + "::response", response,
                "model", model != null ? model : "default",
                "prompt", resolvedPrompt);
        context.context().merge(output);
        LOGGER.info("[FlowStack] Ollama node {} invoked model {}", context.node().getNodeKey(), model);
        return NodeExecutionResult.completed(output, "ollama response");
    }
}
