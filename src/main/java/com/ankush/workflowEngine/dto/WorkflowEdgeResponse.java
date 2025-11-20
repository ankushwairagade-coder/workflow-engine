package com.ankush.workflowEngine.dto;

import java.util.Map;

public record WorkflowEdgeResponse(
        Long id,
        String sourceKey,
        String targetKey,
        String conditionExpression,
        Map<String, Object> metadata) {
}
