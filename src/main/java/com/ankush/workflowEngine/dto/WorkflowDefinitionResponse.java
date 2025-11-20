package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkflowDefinitionResponse(
        Long id,
        String name,
        String description,
        WorkflowStatus status,
        Integer version,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt,
        List<WorkflowNodeResponse> nodes,
        List<WorkflowEdgeResponse> edges) {
}
