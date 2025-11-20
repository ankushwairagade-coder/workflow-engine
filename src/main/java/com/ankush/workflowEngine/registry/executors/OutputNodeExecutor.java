package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OutputNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutputNodeExecutor.class);

    @Override
    public NodeType supportsType() {
        return NodeType.OUTPUT;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Object fields = context.config().getOrDefault("fields", List.of());
        Map<String, Object> output = new LinkedHashMap<>();
        if (fields instanceof List<?> fieldList) {
            for (Object field : fieldList) {
                if (field instanceof String key && context.context().snapshot().containsKey(key)) {
                    output.put(key, context.context().snapshot().get(key));
                }
            }
        }
        LOGGER.info("[FlowStack] Output node {} captured {} fields", context.node().getNodeKey(), output.size());
        return NodeExecutionResult.completed(output, "output captured");
    }
}
