package com.ankush.workflowEngine.execution;

import java.util.Collections;
import java.util.Map;

public record NodeExecutionResult(boolean success, Map<String, Object> output, String message) {

    public static NodeExecutionResult completed() {
        return new NodeExecutionResult(true, Collections.emptyMap(), "completed");
    }

    public static NodeExecutionResult completed(Map<String, Object> output, String message) {
        return new NodeExecutionResult(true, output == null ? Collections.emptyMap() : output, message);
    }
}
