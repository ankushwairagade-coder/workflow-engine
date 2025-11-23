package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowPromptResponse(
        String explanation,
        List<NodeToAdd> nodesToAdd,
        List<EdgeToAdd> edgesToAdd,
        List<NodeToModify> nodesToModify,
        List<NodeToRemove> nodesToRemove,
        WorkflowDefinitionRequest completeWorkflow,
        String nextSteps) {
    
    public record NodeToAdd(
            NodeType type,
            String key,
            String displayName,
            String reason,
            Map<String, Object> config,
            String insertAfterNodeKey) {
    }
    
    public record EdgeToAdd(
            String sourceKey,
            String targetKey,
            String conditionExpression,
            String reason) {
    }
    
    public record NodeToModify(
            String nodeKey,
            Map<String, Object> updatedConfig,
            String reason) {
    }
    
    public record NodeToRemove(
            String nodeKey,
            String reason) {
    }
}


