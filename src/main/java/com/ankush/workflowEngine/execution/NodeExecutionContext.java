package com.ankush.workflowEngine.execution;

import com.ankush.workflowEngine.domain.WorkflowNode;
import com.ankush.workflowEngine.domain.WorkflowRun;
import java.util.Map;

public record NodeExecutionContext(
        WorkflowRun run,
        WorkflowNode node,
        WorkflowContext context,
        Map<String, Object> config) {
}
