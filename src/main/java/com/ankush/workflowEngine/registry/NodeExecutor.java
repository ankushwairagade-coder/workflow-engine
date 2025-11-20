package com.ankush.workflowEngine.registry;

import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionContext;
import com.ankush.workflowEngine.execution.NodeExecutionResult;

public interface NodeExecutor {

    NodeType supportsType();

    NodeExecutionResult execute(NodeExecutionContext context);
}
