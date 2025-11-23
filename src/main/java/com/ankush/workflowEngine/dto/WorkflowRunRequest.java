package com.ankush.workflowEngine.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WorkflowRunRequest(
        @NotNull(message = "Input data is required")
        Map<String, Object> input) {
}
