package com.ankush.workflowEngine.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WorkflowRunRequest(@NotNull Map<String, Object> input) {
}
