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
public class PythonNodeExecutor implements NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PythonNodeExecutor.class);

    @Override
    public NodeType supportsType() {
        return NodeType.SCRIPT_PY;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> config = context.config();
        Map<String, Object> output = Map.of(
                "script", config.getOrDefault("script", "print('noop')"),
                "language", "python");
        context.context().merge(output);
        LOGGER.info("[FlowStack] Python node {} executed placeholder script", context.node().getNodeKey());
        return NodeExecutionResult.completed(output, "python placeholder");
    }
}
