package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WorkflowNodeRequest(
        @NotBlank String key,
        String displayName,
        @NotNull NodeType type,
        Integer sortOrder,
        Map<String, Object> config,
        Map<String, Object> metadata) {
}
