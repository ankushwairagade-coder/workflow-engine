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
public class JavaScriptNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptNodeExecutor.class);

    @Override
    public NodeType supportsType() {
        return NodeType.SCRIPT_JS;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> output = Map.of(
                "script", config.getOrDefault("script", "console.log('noop')"),
                "language", "javascript");
        context.context().merge(output);
        LOGGER.info("[FlowStack] JS node {} executed placeholder script", context.node().getNodeKey());
        LOGGER.info("JS Output: {}",output.toString());
        return NodeExecutionResult.completed(output, "javascript placeholder");
    }
}
