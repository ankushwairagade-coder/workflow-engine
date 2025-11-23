package com.ankush.workflowEngine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record WorkflowEdgeRequest(
        @NotBlank(message = "Source node key is required")
        @Size(min = 1, max = 128, message = "Source node key must be between 1 and 128 characters")
        String sourceKey,
        @NotBlank(message = "Target node key is required")
        @Size(min = 1, max = 128, message = "Target node key must be between 1 and 128 characters")
        String targetKey,
        @Size(max = 1024, message = "Condition expression must not exceed 1024 characters")
        String conditionExpression,
        Map<String, Object> metadata) {
}
