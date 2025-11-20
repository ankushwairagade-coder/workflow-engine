package com.ankush.workflowEngine.registry.executors;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionResult;
import com.ankush.workflowEngine.registry.NodeExecutor;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationNodeExecutor.class);

    @Override
    public NodeType supportsType() {
        return NodeType.NOTIFY;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        String channel = String.valueOf(context.config().getOrDefault("channel", "log"));
        String template = String.valueOf(context.config().getOrDefault("template", "Workflow completed"));
        LOGGER.info("[FlowStack] Notification node {} would send '{}' via {}", context.node().getNodeKey(), template, channel);
        return NodeExecutionResult.completed(Map.of("notification", template), "notification dispatched");
    }
}
