package com.ankush.workflowEngine.dto;

import com.ankush.workflowEngine.enums.NodeType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisualEditorSuggestionResponse(
        List<NodeSuggestion> suggestedNodes,
        CodePrediction codePrediction,
        List<String> nextSteps,
        String explanation) {
    
    public record NodeSuggestion(
            NodeType type,
            String reason,
            String displayName,
            Map<String, Object> suggestedConfig) {
    }
    
    public record CodePrediction(
            String code,
            String language,
            String explanation) {
    }
}

