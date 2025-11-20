package com.ankush.workflowEngine.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record WorkflowEdgeRequest(
        @NotBlank String sourceKey,
        @NotBlank String targetKey,
        String conditionExpression,
        Map<String, Object> metadata) {
}
