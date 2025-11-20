package com.ankush.workflowEngine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record WorkflowDefinitionRequest(
        @NotBlank String name,
        String description,
        Map<String, Object> metadata,
        @Size(min = 1) List<@Valid WorkflowNodeRequest> nodes,
        List<@Valid WorkflowEdgeRequest> edges) {
}
