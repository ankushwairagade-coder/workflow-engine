package com.ankush.workflowEngine.service;

import com.ankush.workflowEngine.dto.ChatbotResponse;
import com.ankush.workflowEngine.dto.WorkflowDefinitionRequest;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import com.ankush.workflowEngine.support.OllamaClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatbotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatbotService.class);
    
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public ChatbotService(OllamaClient ollamaClient, ObjectMapper objectMapper) {
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
    }

    public ChatbotResponse processMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            LOGGER.warn("Empty user message received");
            return ChatbotResponse.error("Please provide a message describing the workflow you want to create.");
        }
        
        // Limit message length to prevent excessive processing
        if (userMessage.length() > 2000) {
            LOGGER.warn("User message too long ({} chars), truncating", userMessage.length());
            userMessage = userMessage.substring(0, 2000) + "...";
        }
        
        try {
            // Build system prompt for workflow generation
            String systemPrompt = buildSystemPrompt();
            String fullPrompt = systemPrompt + "\n\nUser Request: " + userMessage + "\n\nGenerate the workflow JSON:";
            
            // Limit prompt size to prevent timeouts
            if (fullPrompt.length() > 3000) {
                LOGGER.warn("Prompt too long ({} chars), truncating", fullPrompt.length());
                fullPrompt = fullPrompt.substring(0, 3000) + "...";
            }
            
            LOGGER.info("Sending prompt to Ollama for workflow generation (prompt length: {})", fullPrompt.length());
            String aiResponse = ollamaClient.generateText(null, fullPrompt, 500);
            
            LOGGER.debug("Ollama response received (length: {})", aiResponse != null ? aiResponse.length() : 0);
            
            // Try to extract JSON from the response
            WorkflowDefinitionRequest workflow = extractWorkflowFromResponse(aiResponse);
            
            if (workflow != null) {
                String friendlyResponse = generateFriendlyResponse(workflow);
                return ChatbotResponse.success(friendlyResponse, workflow);
            } else {
                // If no workflow found, return the AI response as a message
                String truncatedResponse = aiResponse != null && aiResponse.length() > 500 
                    ? aiResponse.substring(0, 500) + "..." 
                    : aiResponse;
                return ChatbotResponse.message("I understand your request. Here's what I can help you with:\n\n" + 
                    truncatedResponse + "\n\nCould you provide more specific details about:\n" +
                    "- What type of workflow you need?\n" +
                    "- What nodes should be included?\n" +
                    "- What should be the execution flow?");
            }
            
        } catch (NodeExecutionException ex) {
            LOGGER.error("Ollama call failed: {}", ex.getMessage());
            return ChatbotResponse.error("The AI service is currently unavailable. Please try again later or create the workflow manually.");
        } catch (Exception ex) {
            LOGGER.error("Error processing chatbot message", ex);
            return ChatbotResponse.error("Sorry, I encountered an error. Please try again or provide more details about your workflow.");
        }
    }

    private String buildSystemPrompt() {
        return """
            You are a workflow generation assistant for FlowStack, an AI-native workflow automation platform.
            
            Your task is to convert user descriptions into workflow JSON definitions.
            
            Available Node Types:
            - INPUT: Entry point for workflow input
            - HTTP: Make REST API calls (config: method, url, headers, body)
            - SCRIPT_JS: Execute JavaScript (config: script)
            - SCRIPT_PY: Execute Python (config: script)
            - OLLAMA: Call on-device LLM (config: prompt, model)
            - CHATGPT: Call OpenAI API (config: prompt, model, temperature)
            - EMAIL: Send email (config: to, subject, body, cc, bcc)
            - IF_ELSE: Conditional branching (config: condition)
            - OUTPUT: Final output aggregation (config: fields)
            - NOTIFY: Send notifications (config: message)
            
            Workflow JSON Structure:
            {
              "name": "Workflow Name",
              "description": "Workflow description",
              "nodes": [
                {
                  "key": "node-key-1",
                  "displayName": "Node Display Name",
                  "type": "INPUT",
                  "sortOrder": 0,
                  "config": {}
                }
              ],
              "edges": [
                {
                  "sourceKey": "node-key-1",
                  "targetKey": "node-key-2",
                  "conditionExpression": null
                }
              ]
            }
            
            Rules:
            1. Always start with an INPUT node
            2. Node keys must be alphanumeric with underscores/hyphens only (e.g., "fetch-data", "process_result")
            3. Set sortOrder sequentially starting from 0
            4. For IF_ELSE nodes, edges should have conditionExpression: "true" or "false"
            5. Always end with an OUTPUT node
            6. Provide meaningful displayName for each node
            7. Configure nodes appropriately based on their type
            8. CRITICAL: Edges must ONLY reference node keys that exist in the nodes array
            9. Edge sourceKey and targetKey must match exactly the "key" field of nodes
            10. Do not create edges to nodes that don't exist
            
            Example - CORRECT:
            {
              "nodes": [
                {"key": "input-node", "type": "INPUT", ...},
                {"key": "http-node", "type": "HTTP", ...}
              ],
              "edges": [
                {"sourceKey": "input-node", "targetKey": "http-node"}
              ]
            }
            
            Example - WRONG (edge references non-existent node):
            {
              "nodes": [
                {"key": "input-node", "type": "INPUT", ...}
              ],
              "edges": [
                {"sourceKey": "input-node", "targetKey": "http-node"}  // ERROR: http-node doesn't exist!
              ]
            }
            
            Respond ONLY with valid JSON. Do not include markdown code blocks or explanations.
            """;
    }

    private WorkflowDefinitionRequest extractWorkflowFromResponse(String aiResponse) {
        try {
            // Try to find JSON in the response (might be wrapped in markdown code blocks)
            String jsonString = extractJsonFromText(aiResponse);
            
            if (jsonString == null || jsonString.trim().isEmpty()) {
                LOGGER.warn("No JSON found in AI response");
                return null;
            }
            
            // Parse JSON
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            
            // Convert to WorkflowDefinitionRequest
            WorkflowDefinitionRequest workflow = objectMapper.treeToValue(jsonNode, WorkflowDefinitionRequest.class);
            
            // Validate and normalize
            workflow = normalizeWorkflow(workflow);
            
            LOGGER.info("Successfully extracted workflow: {}", workflow.name());
            return workflow;
            
        } catch (JsonProcessingException ex) {
            LOGGER.error("Failed to parse workflow JSON from AI response", ex);
            return null;
        }
    }

    private String extractJsonFromText(String text) {
        // Try to find JSON object in the text
        // First, try to find JSON wrapped in code blocks
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\{.*?\\}\\s*```", Pattern.DOTALL);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(text);
        if (codeBlockMatcher.find()) {
            String json = codeBlockMatcher.group(0);
            // Remove code block markers
            json = json.replaceAll("```(?:json)?\\s*", "").replaceAll("```", "").trim();
            return json;
        }
        
        // Try to find JSON object directly
        Pattern jsonPattern = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);
        Matcher jsonMatcher = jsonPattern.matcher(text);
        if (jsonMatcher.find()) {
            return jsonMatcher.group(0);
        }
        
        return null;
    }

    private WorkflowDefinitionRequest normalizeWorkflow(WorkflowDefinitionRequest workflow) {
        // Ensure nodes have proper sortOrder
        List<com.ankush.workflowEngine.dto.WorkflowNodeRequest> normalizedNodes = new ArrayList<>();
        Set<String> nodeKeys = new HashSet<>();
        
        for (int i = 0; i < workflow.nodes().size(); i++) {
            var node = workflow.nodes().get(i);
            var normalizedNode = new com.ankush.workflowEngine.dto.WorkflowNodeRequest(
                node.key(),
                node.displayName(),
                node.type(),
                i, // Ensure sequential sortOrder
                node.config() != null ? node.config() : new HashMap<>(),
                node.metadata()
            );
            normalizedNodes.add(normalizedNode);
            nodeKeys.add(node.key());
        }
        
        // Validate and filter edges - remove edges that reference non-existent nodes
        List<com.ankush.workflowEngine.dto.WorkflowEdgeRequest> validEdges = new ArrayList<>();
        if (workflow.edges() != null) {
            for (var edge : workflow.edges()) {
                // Only include edge if both source and target nodes exist
                if (nodeKeys.contains(edge.sourceKey()) && nodeKeys.contains(edge.targetKey())) {
                    // Don't allow self-loops
                    if (!edge.sourceKey().equals(edge.targetKey())) {
                        validEdges.add(edge);
                    } else {
                        LOGGER.warn("Removed self-loop edge from {} to {}", edge.sourceKey(), edge.targetKey());
                    }
                } else {
                    LOGGER.warn("Removed invalid edge: {} -> {} (one or both nodes don't exist)", 
                        edge.sourceKey(), edge.targetKey());
                }
            }
        }
        
        return new WorkflowDefinitionRequest(
            workflow.name(),
            workflow.description(),
            workflow.metadata(),
            normalizedNodes,
            validEdges
        );
    }

    /**
     * Processes a message with streaming response for real-time display
     * Uses SseEmitter for Server-Sent Events streaming
     */
    public void processMessageStream(String userMessage, SseEmitter emitter) {
        try {
            if (userMessage == null || userMessage.trim().isEmpty()) {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("Please provide a message describing the workflow you want to create."));
                emitter.complete();
                return;
            }
            
            // Limit message length
            if (userMessage.length() > 2000) {
                userMessage = userMessage.substring(0, 2000) + "...";
            }
            
            // Build system prompt
            String systemPrompt = buildSystemPrompt();
            String fullPrompt = systemPrompt + "\n\nUser Request: " + userMessage + "\n\nGenerate the workflow JSON:";
            
            if (fullPrompt.length() > 3000) {
                fullPrompt = fullPrompt.substring(0, 3000) + "...";
            }
            
            LOGGER.info("Starting streaming response for chatbot (prompt length: {})", fullPrompt.length());
            
            // Stream the response from Ollama
            StringBuilder fullResponse = new StringBuilder();
            ollamaClient.generateTextStream(null, fullPrompt, 500)
                .subscribe(
                    chunk -> {
                        try {
                            fullResponse.append(chunk);
                            // Send chunk to client
                            emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(chunk));
                        } catch (IOException e) {
                            // Client disconnected - this is normal, just stop sending
                            LOGGER.debug("Client disconnected during streaming: {}", e.getMessage());
                            return; // Exit subscription gracefully
                        } catch (Exception e) {
                            LOGGER.warn("Error sending chunk: {}", e.getMessage());
                            // Don't complete with error for non-IO exceptions, just log
                        }
                    },
                    error -> {
                        LOGGER.error("Error in streaming", error);
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data("The AI service encountered an error. Please try again."));
                            emitter.complete();
                        } catch (IOException e) {
                            LOGGER.debug("Client disconnected during error handling: {}", e.getMessage());
                        } catch (Exception e) {
                            LOGGER.warn("Error sending error event: {}", e.getMessage());
                        }
                    },
                    () -> {
                        // Stream complete - try to extract workflow
                        try {
                            String completeResponse = fullResponse.toString();
                            WorkflowDefinitionRequest workflow = extractWorkflowFromResponse(completeResponse);
                            
                            try {
                                if (workflow != null) {
                                    String friendlyResponse = generateFriendlyResponse(workflow);
                                    // Send workflow data
                                    emitter.send(SseEmitter.event()
                                        .name("workflow")
                                        .data(objectMapper.writeValueAsString(workflow)));
                                    
                                    // Send final message
                                    emitter.send(SseEmitter.event()
                                        .name("complete")
                                        .data(friendlyResponse));
                                } else {
                                    // No workflow found, send the response as message
                                    String truncatedResponse = completeResponse.length() > 500 
                                        ? completeResponse.substring(0, 500) + "..." 
                                        : completeResponse;
                                    emitter.send(SseEmitter.event()
                                        .name("complete")
                                        .data("I understand your request. Here's what I can help you with:\n\n" + 
                                            truncatedResponse + "\n\nCould you provide more specific details?"));
                                }
                                
                                emitter.complete();
                            } catch (IOException e) {
                                LOGGER.debug("Client disconnected before completion: {}", e.getMessage());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error processing final response", e);
                            try {
                                emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("Error processing response. Please try again."));
                                emitter.complete();
                            } catch (IOException ioException) {
                                LOGGER.debug("Client disconnected during error handling: {}", ioException.getMessage());
                            } catch (Exception ex) {
                                LOGGER.warn("Error sending error event: {}", ex.getMessage());
                            }
                        }
                    }
                );
                
        } catch (Exception ex) {
            LOGGER.error("Error in processMessageStream", ex);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("Sorry, I encountered an error. Please try again."));
                emitter.complete();
            } catch (IOException e) {
                LOGGER.debug("Client disconnected during error handling: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Error sending error event: {}", e.getMessage());
            }
        }
    }

    private String generateFriendlyResponse(WorkflowDefinitionRequest workflow) {
        StringBuilder response = new StringBuilder();
        response.append("I've generated a workflow for you!\n\n");
        response.append("**Workflow:** ").append(workflow.name()).append("\n");
        if (workflow.description() != null && !workflow.description().isEmpty()) {
            response.append("**Description:** ").append(workflow.description()).append("\n");
        }
        response.append("\n**Nodes:** ").append(workflow.nodes().size()).append("\n");
        response.append("**Edges:** ").append(workflow.edges() != null ? workflow.edges().size() : 0).append("\n\n");
        response.append("The workflow includes:\n");
        for (var node : workflow.nodes()) {
            response.append("- ").append(node.displayName() != null ? node.displayName() : node.key())
                    .append(" (").append(node.type()).append(")\n");
        }
        response.append("\nWould you like me to create this workflow?");
        return response.toString();
    }
}

