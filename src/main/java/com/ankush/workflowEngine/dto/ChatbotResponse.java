package com.ankush.workflowEngine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatbotResponse(
        String response,
        WorkflowDefinitionRequest workflow,
        String error) {
    
    public static ChatbotResponse success(String response, WorkflowDefinitionRequest workflow) {
        return new ChatbotResponse(response, workflow, null);
    }
    
    public static ChatbotResponse error(String error) {
        return new ChatbotResponse(null, null, error);
    }
    
    public static ChatbotResponse message(String response) {
        return new ChatbotResponse(response, null, null);
    }
}

