package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.RunStatus;
import java.time.Instant;
import java.util.Map;

public record WorkflowRunResponse(
        Long id,
        Long workflowId,
        RunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> context,
        String lastError) {
}
