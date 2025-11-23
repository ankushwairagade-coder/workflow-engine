package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import java.util.List;
import java.util.Map;

public record WorkflowAnalysisRequest(
        List<NodeInfo> nodes,
        List<EdgeInfo> edges,
        String workflowName,
        String workflowDescription) {
    
    public record NodeInfo(
            String key,
            NodeType type,
            String displayName,
            Map<String, Object> config) {
    }
    
    public record EdgeInfo(
            String sourceKey,
            String targetKey,
            String conditionExpression) {
    }
}

