package com.ankush.workflowEngine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record WorkflowDefinitionRequest(
        @NotBlank(message = "Workflow name is required")
        @Size(min = 1, max = 255, message = "Workflow name must be between 1 and 255 characters")
        String name,
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,
        Map<String, Object> metadata,
        @Size(min = 1, message = "Workflow must contain at least one node")
        List<@Valid WorkflowNodeRequest> nodes,
        List<@Valid WorkflowEdgeRequest> edges) {
}
