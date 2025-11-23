package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowAnalysisResponse(
        List<WorkflowIssue> issues,
        List<FlowCorrection> corrections,
        List<NodeSuggestion> missingNodes,
        String analysisSummary,
        boolean isValid) {
    
    public record WorkflowIssue(
            String type, // "MISSING_INPUT", "MISSING_OUTPUT", "ORPHANED_NODE", "DISCONNECTED_FLOW", "INCOMPLETE_CONFIG"
            String severity, // "ERROR", "WARNING", "INFO"
            String message,
            String nodeKey,
            String suggestion) {
    }
    
    public record FlowCorrection(
            String type, // "ADD_NODE", "ADD_EDGE", "FIX_CONFIG", "REMOVE_NODE"
            String description,
            NodeSuggestion nodeToAdd,
            EdgeSuggestion edgeToAdd,
            ConfigFix configFix,
            String nodeKeyToRemove) {
    }
    
    public record NodeSuggestion(
            NodeType type,
            String key,
            String displayName,
            String reason,
            Map<String, Object> suggestedConfig,
            String insertAfterNodeKey) {
    }
    
    public record EdgeSuggestion(
            String sourceKey,
            String targetKey,
            String conditionExpression,
            String reason) {
    }
    
    public record ConfigFix(
            String nodeKey,
            Map<String, Object> fixedConfig,
            String reason) {
    }
}

