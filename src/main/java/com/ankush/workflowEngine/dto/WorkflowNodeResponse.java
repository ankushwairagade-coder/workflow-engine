package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import java.util.Map;

public record WorkflowNodeResponse(
        Long id,
        String key,
        String displayName,
        NodeType type,
        Integer sortOrder,
        Map<String, Object> config,
        Map<String, Object> metadata) {
}
