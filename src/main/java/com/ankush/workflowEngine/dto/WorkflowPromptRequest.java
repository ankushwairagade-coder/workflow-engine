package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import java.util.List;
import java.util.Map;

public record WorkflowPromptRequest(
        String prompt,
        String action, // "CREATE", "CORRECT", "EXTEND", "MODIFY"
        List<NodeInfo> currentNodes,
        List<EdgeInfo> currentEdges,
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


