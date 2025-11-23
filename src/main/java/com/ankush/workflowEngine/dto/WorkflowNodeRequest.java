package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record WorkflowNodeRequest(
        @NotBlank(message = "Node key is required")
        @Size(min = 1, max = 128, message = "Node key must be between 1 and 128 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Node key must contain only alphanumeric characters, underscores, or hyphens")
        String key,
        @Size(max = 255, message = "Display name must not exceed 255 characters")
        String displayName,
        @NotNull(message = "Node type is required")
        NodeType type,
        Integer sortOrder,
        Map<String, Object> config,
        Map<String, Object> metadata) {
}
