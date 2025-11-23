package com.ankush.workflowEngine.service;

import com.ankush.workflowEngine.dto.VisualEditorSuggestionRequest;
import com.ankush.workflowEngine.dto.VisualEditorSuggestionResponse;
import com.ankush.workflowEngine.dto.WorkflowAnalysisRequest;
import com.ankush.workflowEngine.dto.WorkflowAnalysisResponse;
import com.ankush.workflowEngine.dto.WorkflowPromptRequest;
import com.ankush.workflowEngine.dto.WorkflowPromptResponse;
import com.ankush.workflowEngine.dto.WorkflowDefinitionRequest;
import com.ankush.workflowEngine.dto.WorkflowNodeRequest;
import com.ankush.workflowEngine.dto.WorkflowEdgeRequest;
import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.execution.NodeExecutionException;
import com.ankush.workflowEngine.support.OllamaClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class VisualEditorAssistantService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisualEditorAssistantService.class);
    
    private final OllamaClient ollamaClient;

    public VisualEditorAssistantService(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public VisualEditorSuggestionResponse getSuggestions(VisualEditorSuggestionRequest request) {
        try {
            // For very large workflows, skip AI and use fallback immediately
            if (request.nodes().size() > 50) {
                LOGGER.debug("Workflow too large ({} nodes), using fallback suggestions", request.nodes().size());
                return generateFallbackSuggestions(request);
            }
            
            // Build context-aware prompt
            String prompt = buildSuggestionPrompt(request);
            
            LOGGER.debug("Getting AI suggestions for workflow with {} nodes", request.nodes().size());
            
            // Use shorter token limit (150) for faster suggestions
            String aiResponse;
            try {
                aiResponse = ollamaClient.generateText(null, prompt, 150);
                
                // Validate response
                if (aiResponse == null || aiResponse.trim().isEmpty()) {
                    LOGGER.warn("Empty AI response received, using fallback");
                    return generateFallbackSuggestions(request);
                }
            } catch (NodeExecutionException ex) {
                // If timeout or other error, use fallback
                LOGGER.warn("AI call failed, using fallback suggestions: {}", ex.getMessage());
                return generateFallbackSuggestions(request);
            }
            
            // Parse AI response and generate suggestions
            VisualEditorSuggestionResponse response = parseSuggestions(aiResponse, request);
            
            // Ensure we have at least some suggestions
            if (response.suggestedNodes().isEmpty()) {
                LOGGER.warn("No suggestions parsed from AI response, using fallback");
                return generateFallbackSuggestions(request);
            }
            
            return response;
            
        } catch (Exception ex) {
            LOGGER.error("Error getting visual editor suggestions", ex);
            return generateFallbackSuggestions(request);
        }
    }

    private String buildSuggestionPrompt(VisualEditorSuggestionRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("FlowStack workflow suggestions.\n");
        prompt.append("Nodes: ").append(request.nodes().size());
        
        // Limit to last 3 nodes for speed (reduced from 5)
        if (!request.nodes().isEmpty()) {
            int startIdx = Math.max(0, request.nodes().size() - 3);
            prompt.append(" (last 3: ");
            for (int i = startIdx; i < request.nodes().size(); i++) {
                var node = request.nodes().get(i);
                prompt.append(node.key()).append("[").append(node.type()).append("]");
                if (i < request.nodes().size() - 1) prompt.append(",");
            }
            prompt.append(")");
        }
        
        // Limit edges to 3 for speed
        if (!request.edges().isEmpty() && request.edges().size() <= 3) {
            prompt.append(" Edges: ");
            for (int i = 0; i < Math.min(request.edges().size(), 3); i++) {
                var edge = request.edges().get(i);
                prompt.append(edge.sourceKey()).append("->").append(edge.targetKey());
                if (i < Math.min(request.edges().size(), 3) - 1) prompt.append(",");
            }
        }
        
        if (request.selectedNodeKey() != null) {
            prompt.append(" Selected: ").append(request.selectedNodeKey()).append("[").append(request.selectedNodeType()).append("]");
        }
        
        prompt.append("\nSuggest 2-3 next node types. Format:\n");
        prompt.append("SUGGESTED_NODES:\n- INPUT: reason\n- HTTP: reason\n");
        prompt.append("NEXT_STEPS:\n- Step\n");
        
        if (request.selectedNodeKey() != null) {
            prompt.append("CODE_SUGGESTION:\nCode: [code]\n");
        }
        
        // Limit prompt size to prevent timeouts
        String promptStr = prompt.toString();
        if (promptStr.length() > 500) {
            LOGGER.warn("Prompt too long ({} chars), truncating", promptStr.length());
            promptStr = promptStr.substring(0, 500) + "...";
        }
        
        return promptStr;
    }

    private VisualEditorSuggestionResponse parseSuggestions(
            String aiResponse, 
            VisualEditorSuggestionRequest request) {
        
        List<VisualEditorSuggestionResponse.NodeSuggestion> suggestedNodes = new ArrayList<>();
        VisualEditorSuggestionResponse.CodePrediction codePrediction = null;
        List<String> nextSteps = new ArrayList<>();
        
        // Simple parsing - extract suggestions from AI response
        String[] lines = aiResponse.split("\n");
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("SUGGESTED_NODES:")) {
                currentSection = "nodes";
                continue;
            } else if (line.startsWith("NEXT_STEPS:")) {
                currentSection = "steps";
                continue;
            } else if (line.startsWith("CODE_SUGGESTION:")) {
                currentSection = "code";
                continue;
            }
            
            if (currentSection.equals("nodes") && line.startsWith("-")) {
                // Parse node suggestion: "- NodeType: reason"
                String content = line.substring(1).trim();
                int colonIndex = content.indexOf(':');
                if (colonIndex > 0) {
                    String nodeTypeStr = content.substring(0, colonIndex).trim();
                    String reason = content.substring(colonIndex + 1).trim();
                    
                    // Clean up node type string - remove common prefixes/suffixes
                    nodeTypeStr = nodeTypeStr.toUpperCase()
                        .replace("NODETYPE", "")
                        .replace("TYPE", "")
                        .trim();
                    
                    // Try to parse the node type
                    NodeType nodeType = null;
                    try {
                        nodeType = NodeType.valueOf(nodeTypeStr);
                    } catch (IllegalArgumentException e) {
                        // Try common variations
                        nodeTypeStr = nodeTypeStr.replace("_", "");
                        try {
                            nodeType = NodeType.valueOf(nodeTypeStr);
                        } catch (IllegalArgumentException e2) {
                            // Try to find partial match
                            for (NodeType type : NodeType.values()) {
                                if (type.name().equalsIgnoreCase(nodeTypeStr) || 
                                    type.name().contains(nodeTypeStr) ||
                                    nodeTypeStr.contains(type.name())) {
                                    nodeType = type;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (nodeType != null) {
                        suggestedNodes.add(new VisualEditorSuggestionResponse.NodeSuggestion(
                            nodeType,
                            reason,
                            getDefaultDisplayName(nodeType),
                            getDefaultConfig(nodeType)
                        ));
                    } else {
                        LOGGER.warn("Invalid node type in AI response: '{}'. Available types: {}", 
                            nodeTypeStr, java.util.Arrays.toString(NodeType.values()));
                    }
                }
            } else if (currentSection.equals("steps") && line.startsWith("-")) {
                nextSteps.add(line.substring(1).trim());
            } else if (currentSection.equals("code")) {
                if (line.startsWith("Language:")) {
                    // Language info - can be used for future enhancements
                    // Continue to next line for code
                } else if (line.startsWith("Code:")) {
                    String code = line.substring("Code:".length()).trim();
                    codePrediction = new VisualEditorSuggestionResponse.CodePrediction(
                        code,
                        request.selectedNodeType() == NodeType.SCRIPT_JS ? "javascript" :
                        request.selectedNodeType() == NodeType.SCRIPT_PY ? "python" : "prompt",
                        "AI-generated code suggestion"
                    );
                }
            }
        }
        
        // Fallback if parsing failed
        if (suggestedNodes.isEmpty()) {
            suggestedNodes = generateDefaultNodeSuggestions(request);
        }
        if (nextSteps.isEmpty()) {
            nextSteps = generateDefaultNextSteps(request);
        }
        
        return new VisualEditorSuggestionResponse(
            suggestedNodes,
            codePrediction,
            nextSteps,
            aiResponse
        );
    }

    private List<VisualEditorSuggestionResponse.NodeSuggestion> generateDefaultNodeSuggestions(
            VisualEditorSuggestionRequest request) {
        List<VisualEditorSuggestionResponse.NodeSuggestion> suggestions = new ArrayList<>();
        
        // Analyze workflow to suggest next nodes
        boolean hasInput = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.INPUT);
        boolean hasOutput = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.OUTPUT);
        boolean hasHttp = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.HTTP);
        boolean hasAi = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.OLLAMA || n.type() == NodeType.CHATGPT);
        
        if (!hasInput) {
            suggestions.add(new VisualEditorSuggestionResponse.NodeSuggestion(
                NodeType.INPUT,
                "Every workflow needs a starting point",
                "Start",
                new HashMap<>()
            ));
        }
        
        if (hasHttp && !hasAi) {
            suggestions.add(new VisualEditorSuggestionResponse.NodeSuggestion(
                NodeType.OLLAMA,
                "Add AI processing after fetching data",
                "AI Analysis",
                Map.of("prompt", "Analyze the data: {{http-node::body}}")
            ));
        }
        
        if (!hasOutput) {
            suggestions.add(new VisualEditorSuggestionResponse.NodeSuggestion(
                NodeType.OUTPUT,
                "Complete your workflow with an output node",
                "End",
                new HashMap<>()
            ));
        }
        
        return suggestions;
    }

    private List<String> generateDefaultNextSteps(VisualEditorSuggestionRequest request) {
        List<String> steps = new ArrayList<>();
        
        boolean hasInput = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.INPUT);
        boolean hasOutput = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.OUTPUT);
        
        if (!hasInput) {
            steps.add("Add an INPUT node to start your workflow");
        }
        
        if (request.nodes().size() == 1 && hasInput) {
            steps.add("Add processing nodes (HTTP, Script, or AI) after the input");
        }
        
        if (!hasOutput && request.nodes().size() > 1) {
            steps.add("Add an OUTPUT node to complete your workflow");
        }
        
        if (request.edges().isEmpty() && request.nodes().size() > 1) {
            steps.add("Connect your nodes by dragging from one node to another");
        }
        
        return steps;
    }

    private VisualEditorSuggestionResponse generateFallbackSuggestions(
            VisualEditorSuggestionRequest request) {
        return new VisualEditorSuggestionResponse(
            generateDefaultNodeSuggestions(request),
            null,
            generateDefaultNextSteps(request),
            "Using default suggestions"
        );
    }

    private String getDefaultDisplayName(NodeType type) {
        return switch (type) {
            case INPUT -> "Start";
            case OUTPUT -> "End";
            case HTTP -> "API Call";
            case SCRIPT_JS -> "JavaScript";
            case SCRIPT_PY -> "Python";
            case OLLAMA -> "AI Analysis";
            case CHATGPT -> "ChatGPT";
            case EMAIL -> "Send Email";
            case IF_ELSE -> "Condition";
            case NOTIFY -> "Notification";
        };
    }

    private Map<String, Object> getDefaultConfig(NodeType type) {
        Map<String, Object> config = new HashMap<>();
        switch (type) {
            case INPUT -> {
                // INPUT nodes typically don't need config
            }
            case OUTPUT -> {
                // OUTPUT nodes typically don't need config
            }
            case HTTP -> {
                config.put("method", "GET");
                config.put("url", "https://api.example.com/endpoint");
            }
            case SCRIPT_JS -> {
                config.put("script", "return { result: 'value' };");
            }
            case SCRIPT_PY -> {
                config.put("script", "return { 'result': 'value' }");
            }
            case OLLAMA, CHATGPT -> {
                config.put("prompt", "Process the data: {{input}}");
            }
            case EMAIL -> {
                config.put("to", "user@example.com");
                config.put("subject", "Workflow Result");
                config.put("body", "Result: {{result}}");
            }
            case IF_ELSE -> {
                config.put("condition", "{{value}} > 0");
            }
            case NOTIFY -> {
                config.put("message", "Notification: {{result}}");
            }
        }
        return config;
    }

    /**
     * Analyzes workflow and provides corrections and improvements
     */
    public WorkflowAnalysisResponse analyzeWorkflow(WorkflowAnalysisRequest request) {
        try {
            // First, perform structural analysis
            List<WorkflowAnalysisResponse.WorkflowIssue> issues = analyzeWorkflowStructure(request);
            List<WorkflowAnalysisResponse.FlowCorrection> corrections = generateCorrections(request, issues);
            List<WorkflowAnalysisResponse.NodeSuggestion> missingNodes = suggestMissingNodes(request);
            
            // Use AI for intelligent analysis
            String aiAnalysis = getAIAnalysis(request, issues);
            
            boolean isValid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
            
            return new WorkflowAnalysisResponse(
                issues,
                corrections,
                missingNodes,
                aiAnalysis,
                isValid
            );
        } catch (Exception ex) {
            LOGGER.error("Error analyzing workflow", ex);
            return new WorkflowAnalysisResponse(
                List.of(),
                List.of(),
                List.of(),
                "Error analyzing workflow: " + ex.getMessage(),
                false
            );
        }
    }

    private List<WorkflowAnalysisResponse.WorkflowIssue> analyzeWorkflowStructure(
            WorkflowAnalysisRequest request) {
        List<WorkflowAnalysisResponse.WorkflowIssue> issues = new ArrayList<>();
        
        Set<String> nodesWithIncomingEdges = request.edges().stream()
            .map(WorkflowAnalysisRequest.EdgeInfo::targetKey)
            .collect(Collectors.toSet());
        
        Set<String> nodesWithOutgoingEdges = request.edges().stream()
            .map(WorkflowAnalysisRequest.EdgeInfo::sourceKey)
            .collect(Collectors.toSet());
        
        // Check for missing INPUT node
        boolean hasInput = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.INPUT);
        if (!hasInput) {
            issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                "MISSING_INPUT",
                "ERROR",
                "Workflow must start with an INPUT node",
                null,
                "Add an INPUT node as the entry point"
            ));
        }
        
        // Check for missing OUTPUT node
        boolean hasOutput = request.nodes().stream()
            .anyMatch(n -> n.type() == NodeType.OUTPUT);
        if (!hasOutput && request.nodes().size() > 1) {
            issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                "MISSING_OUTPUT",
                "WARNING",
                "Workflow should end with an OUTPUT node",
                null,
                "Add an OUTPUT node to complete the workflow"
            ));
        }
        
        // Check for orphaned nodes (nodes with no connections)
        for (var node : request.nodes()) {
            boolean hasIncoming = nodesWithIncomingEdges.contains(node.key());
            boolean hasOutgoing = nodesWithOutgoingEdges.contains(node.key());
            
            if (!hasIncoming && !hasOutgoing && node.type() != NodeType.INPUT && node.type() != NodeType.OUTPUT) {
                issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                    "ORPHANED_NODE",
                    "WARNING",
                    "Node '" + node.key() + "' is not connected to any other node",
                    node.key(),
                    "Connect this node to other nodes or remove it"
                ));
            }
        }
        
        // Check for disconnected flows (nodes that can't be reached from INPUT)
        if (hasInput) {
            String inputNodeKey = request.nodes().stream()
                .filter(n -> n.type() == NodeType.INPUT)
                .findFirst()
                .map(WorkflowAnalysisRequest.NodeInfo::key)
                .orElse(null);
            
            if (inputNodeKey != null) {
                Set<String> reachableNodes = findReachableNodes(inputNodeKey, request.edges());
                for (var node : request.nodes()) {
                    if (!reachableNodes.contains(node.key()) && node.type() != NodeType.INPUT) {
                        issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                            "DISCONNECTED_FLOW",
                            "ERROR",
                            "Node '" + node.key() + "' cannot be reached from INPUT node",
                            node.key(),
                            "Add a connection path from INPUT to this node"
                        ));
                    }
                }
            }
        }
        
        // Check for incomplete configurations
        for (var node : request.nodes()) {
            Map<String, Object> config = node.config() != null ? node.config() : new HashMap<>();
            
            if (node.type() == NodeType.HTTP) {
                if (!config.containsKey("url") || config.get("url") == null || 
                    String.valueOf(config.get("url")).trim().isEmpty()) {
                    issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                        "INCOMPLETE_CONFIG",
                        "ERROR",
                        "HTTP node '" + node.key() + "' is missing URL configuration",
                        node.key(),
                        "Configure the URL for this HTTP node"
                    ));
                }
            } else if (node.type() == NodeType.SCRIPT_JS || node.type() == NodeType.SCRIPT_PY) {
                if (!config.containsKey("script") || config.get("script") == null ||
                    String.valueOf(config.get("script")).trim().isEmpty()) {
                    issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                        "INCOMPLETE_CONFIG",
                        "WARNING",
                        "Script node '" + node.key() + "' has no script code",
                        node.key(),
                        "Add script code to this node"
                    ));
                }
            } else if (node.type() == NodeType.OLLAMA || node.type() == NodeType.CHATGPT) {
                if (!config.containsKey("prompt") || config.get("prompt") == null ||
                    String.valueOf(config.get("prompt")).trim().isEmpty()) {
                    issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                        "INCOMPLETE_CONFIG",
                        "ERROR",
                        "AI node '" + node.key() + "' is missing prompt",
                        node.key(),
                        "Add a prompt for this AI node"
                    ));
                }
            } else if (node.type() == NodeType.EMAIL) {
                if (!config.containsKey("to") || config.get("to") == null) {
                    issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                        "INCOMPLETE_CONFIG",
                        "ERROR",
                        "Email node '" + node.key() + "' is missing recipient (to)",
                        node.key(),
                        "Configure email recipient"
                    ));
                }
            } else if (node.type() == NodeType.IF_ELSE) {
                if (!config.containsKey("condition") || config.get("condition") == null ||
                    String.valueOf(config.get("condition")).trim().isEmpty()) {
                    issues.add(new WorkflowAnalysisResponse.WorkflowIssue(
                        "INCOMPLETE_CONFIG",
                        "ERROR",
                        "IF_ELSE node '" + node.key() + "' is missing condition",
                        node.key(),
                        "Add a condition expression"
                    ));
                }
            }
        }
        
        return issues;
    }

    private Set<String> findReachableNodes(String startNode, List<WorkflowAnalysisRequest.EdgeInfo> edges) {
        Set<String> reachable = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        
        queue.add(startNode);
        reachable.add(startNode);
        
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            if (visited.contains(current)) continue;
            visited.add(current);
            
            for (var edge : edges) {
                if (edge.sourceKey().equals(current) && !reachable.contains(edge.targetKey())) {
                    reachable.add(edge.targetKey());
                    queue.add(edge.targetKey());
                }
            }
        }
        
        return reachable;
    }

    private List<WorkflowAnalysisResponse.FlowCorrection> generateCorrections(
            WorkflowAnalysisRequest request,
            List<WorkflowAnalysisResponse.WorkflowIssue> issues) {
        List<WorkflowAnalysisResponse.FlowCorrection> corrections = new ArrayList<>();
        
        for (var issue : issues) {
            switch (issue.type()) {
                case "MISSING_INPUT" -> {
                    corrections.add(new WorkflowAnalysisResponse.FlowCorrection(
                        "ADD_NODE",
                        "Add INPUT node to start workflow",
                        new WorkflowAnalysisResponse.NodeSuggestion(
                            NodeType.INPUT,
                            "input-node",
                            "Start",
                            "Every workflow needs an entry point",
                            new HashMap<>(),
                            null
                        ),
                        null,
                        null,
                        null
                    ));
                }
                case "MISSING_OUTPUT" -> {
                    String lastNodeKey = request.nodes().isEmpty() ? null : 
                        request.nodes().get(request.nodes().size() - 1).key();
                    corrections.add(new WorkflowAnalysisResponse.FlowCorrection(
                        "ADD_NODE",
                        "Add OUTPUT node to complete workflow",
                        new WorkflowAnalysisResponse.NodeSuggestion(
                            NodeType.OUTPUT,
                            "output-node",
                            "End",
                            "Complete workflow with output",
                            new HashMap<>(),
                            lastNodeKey
                        ),
                        null,
                        null,
                        null
                    ));
                }
                case "ORPHANED_NODE" -> {
                    // Suggest connecting orphaned node
                    if (issue.nodeKey() != null) {
                        String inputNodeKey = request.nodes().stream()
                            .filter(n -> n.type() == NodeType.INPUT)
                            .findFirst()
                            .map(WorkflowAnalysisRequest.NodeInfo::key)
                            .orElse(null);
                        
                        if (inputNodeKey != null) {
                            corrections.add(new WorkflowAnalysisResponse.FlowCorrection(
                                "ADD_EDGE",
                                "Connect orphaned node to workflow",
                                null,
                                new WorkflowAnalysisResponse.EdgeSuggestion(
                                    inputNodeKey,
                                    issue.nodeKey(),
                                    null,
                                    "Connect from INPUT to orphaned node"
                                ),
                                null,
                                null
                            ));
                        }
                    }
                }
                case "INCOMPLETE_CONFIG" -> {
                    if (issue.nodeKey() != null) {
                        var node = request.nodes().stream()
                            .filter(n -> n.key().equals(issue.nodeKey()))
                            .findFirst()
                            .orElse(null);
                        
                        if (node != null) {
                            Map<String, Object> fixedConfig = fixNodeConfig(node.type(), node.config());
                            corrections.add(new WorkflowAnalysisResponse.FlowCorrection(
                                "FIX_CONFIG",
                                issue.suggestion(),
                                null,
                                null,
                                new WorkflowAnalysisResponse.ConfigFix(
                                    issue.nodeKey(),
                                    fixedConfig,
                                    issue.suggestion()
                                ),
                                null
                            ));
                        }
                    }
                }
            }
        }
        
        return corrections;
    }

    private Map<String, Object> fixNodeConfig(NodeType type, Map<String, Object> currentConfig) {
        Map<String, Object> fixed = currentConfig != null ? new HashMap<>(currentConfig) : new HashMap<>();
        
        switch (type) {
            case INPUT -> {
                // INPUT nodes typically don't need config
            }
            case OUTPUT -> {
                // OUTPUT nodes typically don't need config
            }
            case HTTP -> {
                if (!fixed.containsKey("url") || fixed.get("url") == null) {
                    fixed.put("url", "https://api.example.com/endpoint");
                }
                if (!fixed.containsKey("method")) {
                    fixed.put("method", "GET");
                }
            }
            case SCRIPT_JS -> {
                if (!fixed.containsKey("script") || fixed.get("script") == null) {
                    fixed.put("script", "return { result: 'value' };");
                }
            }
            case SCRIPT_PY -> {
                if (!fixed.containsKey("script") || fixed.get("script") == null) {
                    fixed.put("script", "return { 'result': 'value' }");
                }
            }
            case OLLAMA, CHATGPT -> {
                if (!fixed.containsKey("prompt") || fixed.get("prompt") == null) {
                    fixed.put("prompt", "Process the data: {{input}}");
                }
            }
            case EMAIL -> {
                if (!fixed.containsKey("to")) {
                    fixed.put("to", List.of("user@example.com"));
                }
                if (!fixed.containsKey("subject")) {
                    fixed.put("subject", "Workflow Result");
                }
                if (!fixed.containsKey("body")) {
                    fixed.put("body", "Result: {{result}}");
                }
            }
            case IF_ELSE -> {
                if (!fixed.containsKey("condition") || fixed.get("condition") == null) {
                    fixed.put("condition", "{{value}} > 0");
                }
            }
            case NOTIFY -> {
                if (!fixed.containsKey("message") || fixed.get("message") == null) {
                    fixed.put("message", "Notification: {{result}}");
                }
            }
        }
        
        return fixed;
    }

    private List<WorkflowAnalysisResponse.NodeSuggestion> suggestMissingNodes(
            WorkflowAnalysisRequest request) {
        List<WorkflowAnalysisResponse.NodeSuggestion> suggestions = new ArrayList<>();
        
        boolean hasInput = request.nodes().stream().anyMatch(n -> n.type() == NodeType.INPUT);
        boolean hasOutput = request.nodes().stream().anyMatch(n -> n.type() == NodeType.OUTPUT);
        boolean hasHttp = request.nodes().stream().anyMatch(n -> n.type() == NodeType.HTTP);
        boolean hasAi = request.nodes().stream().anyMatch(n -> n.type() == NodeType.OLLAMA || n.type() == NodeType.CHATGPT);
        
        if (!hasInput) {
            suggestions.add(new WorkflowAnalysisResponse.NodeSuggestion(
                NodeType.INPUT,
                "input-node",
                "Start",
                "Workflow needs an entry point",
                new HashMap<>(),
                null
            ));
        }
        
        if (hasHttp && !hasAi) {
            String httpNodeKey = request.nodes().stream()
                .filter(n -> n.type() == NodeType.HTTP)
                .findFirst()
                .map(WorkflowAnalysisRequest.NodeInfo::key)
                .orElse(null);
            suggestions.add(new WorkflowAnalysisResponse.NodeSuggestion(
                NodeType.OLLAMA,
                "ai-analysis-node",
                "AI Analysis",
                "Add AI processing after HTTP call",
                Map.of("prompt", "Analyze the data: {{" + httpNodeKey + "::body}}"),
                httpNodeKey
            ));
        }
        
        if (!hasOutput && request.nodes().size() > 1) {
            String lastNodeKey = request.nodes().get(request.nodes().size() - 1).key();
            suggestions.add(new WorkflowAnalysisResponse.NodeSuggestion(
                NodeType.OUTPUT,
                "output-node",
                "End",
                "Complete workflow with output",
                new HashMap<>(),
                lastNodeKey
            ));
        }
        
        return suggestions;
    }

    private String getAIAnalysis(WorkflowAnalysisRequest request, 
                                 List<WorkflowAnalysisResponse.WorkflowIssue> issues) {
        try {
            // Skip AI for very large workflows
            if (request.nodes().size() > 50) {
                return "Workflow analysis completed. " + issues.size() + " issue(s) found.";
            }
            
            // Use concise prompt and limit tokens for faster response
            StringBuilder prompt = new StringBuilder();
            prompt.append("Workflow: ").append(request.workflowName() != null ? request.workflowName() : "Untitled");
            prompt.append(" (").append(request.nodes().size()).append(" nodes)\n");
            
            if (!issues.isEmpty()) {
                int errorCount = (int) issues.stream().filter(i -> "ERROR".equals(i.severity())).count();
                int warningCount = issues.size() - errorCount;
                prompt.append("Issues: ").append(errorCount).append(" errors, ").append(warningCount).append(" warnings\n");
            }
            
            prompt.append("Brief assessment and 2-3 suggestions.\n");
            
            // Limit prompt size
            String promptStr = prompt.toString();
            if (promptStr.length() > 300) {
                promptStr = promptStr.substring(0, 300);
            }
            
            // Use shorter token limit (200) for faster analysis
            String aiResponse = ollamaClient.generateText(null, promptStr, 200);
            return aiResponse != null && !aiResponse.trim().isEmpty() 
                ? aiResponse 
                : "Workflow analysis completed. " + issues.size() + " issue(s) found.";
        } catch (Exception ex) {
            LOGGER.warn("Error getting AI analysis, using fallback: {}", ex.getMessage());
            return "Workflow analysis completed. " + issues.size() + " issue(s) found.";
        }
    }

    /**
     * Processes a user prompt to create, modify, extend, or correct a workflow
     */
    public WorkflowPromptResponse processWorkflowPrompt(WorkflowPromptRequest request) {
        try {
            // Validate request
            if (request.prompt() == null || request.prompt().trim().isEmpty()) {
                LOGGER.warn("Empty prompt received");
                return new WorkflowPromptResponse(
                    "Please provide a prompt describing what you want to do with the workflow.",
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    null,
                    "Enter a prompt in the AI Assistant to get started."
                );
            }
            
            if (request.action() == null) {
                LOGGER.warn("Missing action in request");
                return generateFallbackPromptResponse(request, "Missing action type");
            }
            
            LOGGER.info("Processing workflow prompt: action={}, prompt length={}, nodes={}, edges={}", 
                request.action(), 
                request.prompt().length(),
                request.currentNodes() != null ? request.currentNodes().size() : 0,
                request.currentEdges() != null ? request.currentEdges().size() : 0);
            
            // Build comprehensive prompt for AI
            String aiPrompt = buildWorkflowPrompt(request);
            
            // Check if we can use fast fallback for simple requests
            if (canUseFastFallback(request)) {
                LOGGER.debug("Using fast fallback for simple request");
                return generateIntelligentResponse(request);
            }
            
            // Skip AI for very large workflows
            if (request.currentNodes() != null && request.currentNodes().size() > 50) {
                LOGGER.debug("Workflow too large ({} nodes), using intelligent fallback", request.currentNodes().size());
                return generateIntelligentResponse(request);
            }
            
            // Limit prompt size to prevent timeouts
            if (aiPrompt.length() > 1000) {
                LOGGER.warn("Prompt too long ({} chars), truncating", aiPrompt.length());
                aiPrompt = aiPrompt.substring(0, 1000) + "...";
            }
            
            // Get AI response with timeout handling and token limit for speed
            String aiResponse;
            try {
                // Use shorter token limit (250) for faster response
                aiResponse = ollamaClient.generateText(null, aiPrompt, 250);
                if (aiResponse == null || aiResponse.trim().isEmpty()) {
                    LOGGER.warn("Empty AI response received");
                    return generateIntelligentResponse(request);
                }
            } catch (NodeExecutionException ex) {
                LOGGER.warn("Failed to get AI response (likely timeout), using intelligent fallback: {}", ex.getMessage());
                return generateIntelligentResponse(request);
            } catch (Exception ex) {
                LOGGER.error("Failed to get AI response, using intelligent fallback", ex);
                return generateIntelligentResponse(request);
            }
            
            LOGGER.debug("AI response received, length={}", aiResponse.length());
            
            // Parse AI response and generate structured changes
            WorkflowPromptResponse response = parseWorkflowPromptResponse(aiResponse, request);
            
            // Validate response has at least some changes or explanation
            if (response.nodesToAdd().isEmpty() && 
                response.edgesToAdd().isEmpty() && 
                response.nodesToModify().isEmpty() && 
                response.nodesToRemove().isEmpty() &&
                (response.explanation() == null || response.explanation().trim().isEmpty())) {
                LOGGER.warn("AI response contained no actionable changes, using intelligent fallback");
                return generateIntelligentResponse(request);
            }
            
            return response;
            
        } catch (Exception ex) {
            LOGGER.error("Error processing workflow prompt", ex);
            return generateFallbackPromptResponse(request, 
                "An error occurred: " + ex.getMessage());
        }
    }

    private String buildWorkflowPrompt(WorkflowPromptRequest request) {
        StringBuilder prompt = new StringBuilder();
        
        // Concise system prompt
        prompt.append("FlowStack workflow assistant. Action: ").append(request.action()).append("\n");
        prompt.append("User: ").append(request.prompt()).append("\n\n");
        
        // Only include essential workflow state (limit to 10 nodes max for speed)
        if (request.currentNodes() != null && !request.currentNodes().isEmpty()) {
            int nodeCount = Math.min(request.currentNodes().size(), 10);
            prompt.append("Current nodes (").append(nodeCount).append("): ");
            for (int i = 0; i < nodeCount; i++) {
                var node = request.currentNodes().get(i);
                prompt.append(node.key()).append("[").append(node.type()).append("]");
                if (i < nodeCount - 1) prompt.append(", ");
            }
            prompt.append("\n");
            
            if (request.currentEdges() != null && !request.currentEdges().isEmpty() && request.currentEdges().size() <= 10) {
                prompt.append("Edges: ");
                for (int i = 0; i < Math.min(request.currentEdges().size(), 10); i++) {
                    var edge = request.currentEdges().get(i);
                    prompt.append(edge.sourceKey()).append("->").append(edge.targetKey());
                    if (i < Math.min(request.currentEdges().size(), 10) - 1) prompt.append(", ");
                }
                prompt.append("\n");
            }
        }
        
        // Concise node types (only essential info)
        prompt.append("Node types: INPUT, OUTPUT, HTTP(method,url), SCRIPT_JS(script), SCRIPT_PY(script), ");
        prompt.append("OLLAMA(prompt), CHATGPT(prompt), EMAIL(to,subject,body), IF_ELSE(condition), NOTIFY(message)\n\n");
        
        // Action-specific concise instructions
        switch (request.action()) {
            case "CREATE" -> prompt.append("Create workflow: INPUT -> processing nodes -> OUTPUT. Connect all.\n");
            case "EXTEND" -> prompt.append("Add nodes and connect to existing workflow.\n");
            case "MODIFY" -> prompt.append("Update node configs. Preserve structure.\n");
            case "CORRECT" -> prompt.append("Fix: add missing INPUT/OUTPUT, connect orphaned nodes, add configs.\n");
        }
        
        // Concise response format
        prompt.append("\nResponse format:\n");
        prompt.append("EXPLANATION: [brief]\n");
        prompt.append("NODES_TO_ADD:\n");
        prompt.append("  - type: [TYPE] key: [key] displayName: [name] reason: [why] config: {fields} insertAfterNodeKey: [key?]\n");
        prompt.append("EDGES_TO_ADD:\n");
        prompt.append("  - sourceKey: [key] targetKey: [key] conditionExpression: [expr?] reason: [why]\n");
        prompt.append("NODES_TO_MODIFY:\n");
        prompt.append("  - nodeKey: [key] updatedConfig: {fields} reason: [why]\n");
        prompt.append("NODES_TO_REMOVE:\n");
        prompt.append("  - nodeKey: [key] reason: [why]\n");
        prompt.append("NEXT_STEPS: [guidance]\n");
        
        return prompt.toString();
    }

    private WorkflowPromptResponse parseWorkflowPromptResponse(
            String aiResponse, 
            WorkflowPromptRequest request) {
        
        List<WorkflowPromptResponse.NodeToAdd> nodesToAdd = new ArrayList<>();
        List<WorkflowPromptResponse.EdgeToAdd> edgesToAdd = new ArrayList<>();
        List<WorkflowPromptResponse.NodeToModify> nodesToModify = new ArrayList<>();
        List<WorkflowPromptResponse.NodeToRemove> nodesToRemove = new ArrayList<>();
        String explanation = "";
        String nextSteps = "";
        WorkflowDefinitionRequest completeWorkflow = null;
        
        try {
            // Parse the AI response
            String[] lines = aiResponse.split("\n");
            String currentSection = "";
            Map<String, Object> currentItem = new HashMap<>();
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("EXPLANATION:")) {
                    explanation = line.substring("EXPLANATION:".length()).trim();
                    currentSection = "";
                    continue;
                } else if (line.startsWith("NODES_TO_ADD:")) {
                    currentSection = "nodes_to_add";
                    continue;
                } else if (line.startsWith("EDGES_TO_ADD:")) {
                    currentSection = "edges_to_add";
                    continue;
                } else if (line.startsWith("NODES_TO_MODIFY:")) {
                    currentSection = "nodes_to_modify";
                    continue;
                } else if (line.startsWith("NODES_TO_REMOVE:")) {
                    currentSection = "nodes_to_remove";
                    continue;
                } else if (line.startsWith("NEXT_STEPS:")) {
                    nextSteps = line.substring("NEXT_STEPS:".length()).trim();
                    currentSection = "";
                    continue;
                } else if (line.startsWith("COMPLETE_WORKFLOW:")) {
                    currentSection = "complete_workflow";
                    continue;
                }
                
                // Parse items
                if (line.startsWith("-") || line.startsWith("  -")) {
                    // Save previous item if exists
                    if (!currentItem.isEmpty()) {
                        saveParsedItem(currentSection, currentItem, nodesToAdd, edgesToAdd, 
                            nodesToModify, nodesToRemove);
                        currentItem = new HashMap<>();
                    }
                    continue;
                }
                
                // Parse key-value pairs
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim().replaceAll("^[-\\s]+", "");
                        String value = parts[1].trim();
                        currentItem.put(key, value);
                    }
                }
            }
            
            // Save last item
            if (!currentItem.isEmpty()) {
                saveParsedItem(currentSection, currentItem, nodesToAdd, edgesToAdd, 
                    nodesToModify, nodesToRemove);
            }
            
            // Generate explanation if missing
            if (explanation.isEmpty()) {
                explanation = generateExplanation(request, nodesToAdd, edgesToAdd, nodesToModify, nodesToRemove);
            }
            
            // Generate next steps if missing
            if (nextSteps.isEmpty()) {
                nextSteps = generateNextSteps(request, nodesToAdd, edgesToAdd, nodesToModify, nodesToRemove);
            }
            
            // For CREATE action, build complete workflow
            if ("CREATE".equals(request.action()) && !nodesToAdd.isEmpty()) {
                completeWorkflow = buildCompleteWorkflow(request, nodesToAdd, edgesToAdd);
            }
            
        } catch (Exception ex) {
            LOGGER.warn("Error parsing AI response, using fallback: {}", ex.getMessage());
            // Fallback to intelligent defaults
            return generateIntelligentResponse(request);
        }
        
        return new WorkflowPromptResponse(
            explanation,
            nodesToAdd,
            edgesToAdd,
            nodesToModify,
            nodesToRemove,
            completeWorkflow,
            nextSteps
        );
    }

    private void saveParsedItem(
            String section,
            Map<String, Object> item,
            List<WorkflowPromptResponse.NodeToAdd> nodesToAdd,
            List<WorkflowPromptResponse.EdgeToAdd> edgesToAdd,
            List<WorkflowPromptResponse.NodeToModify> nodesToModify,
            List<WorkflowPromptResponse.NodeToRemove> nodesToRemove) {
        
        try {
            if ("nodes_to_add".equals(section)) {
                String typeStr = String.valueOf(item.getOrDefault("type", "HTTP"));
                String key = String.valueOf(item.getOrDefault("key", 
                    typeStr.toLowerCase() + "-" + System.currentTimeMillis()));
                String displayName = String.valueOf(item.getOrDefault("displayName", key));
                String reason = String.valueOf(item.getOrDefault("reason", "Added by AI"));
                String insertAfter = item.containsKey("insertAfterNodeKey") 
                    ? String.valueOf(item.get("insertAfterNodeKey")) : null;
                
                Map<String, Object> config = extractConfig(item);
                
                try {
                    NodeType type = NodeType.valueOf(typeStr.toUpperCase());
                    nodesToAdd.add(new WorkflowPromptResponse.NodeToAdd(
                        type,
                        key,
                        displayName,
                        reason,
                        config,
                        insertAfter
                    ));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid node type: {}", typeStr);
                }
            } else if ("edges_to_add".equals(section)) {
                String sourceKey = String.valueOf(item.getOrDefault("sourceKey", ""));
                String targetKey = String.valueOf(item.getOrDefault("targetKey", ""));
                String condition = item.containsKey("conditionExpression") 
                    ? String.valueOf(item.get("conditionExpression")) : null;
                String reason = String.valueOf(item.getOrDefault("reason", "Added by AI"));
                
                if (!sourceKey.isEmpty() && !targetKey.isEmpty()) {
                    edgesToAdd.add(new WorkflowPromptResponse.EdgeToAdd(
                        sourceKey,
                        targetKey,
                        condition,
                        reason
                    ));
                }
            } else if ("nodes_to_modify".equals(section)) {
                String nodeKey = String.valueOf(item.getOrDefault("nodeKey", ""));
                String reason = String.valueOf(item.getOrDefault("reason", "Modified by AI"));
                Map<String, Object> updatedConfig = extractConfig(item);
                
                if (!nodeKey.isEmpty()) {
                    nodesToModify.add(new WorkflowPromptResponse.NodeToModify(
                        nodeKey,
                        updatedConfig,
                        reason
                    ));
                }
            } else if ("nodes_to_remove".equals(section)) {
                String nodeKey = String.valueOf(item.getOrDefault("nodeKey", ""));
                String reason = String.valueOf(item.getOrDefault("reason", "Removed by AI"));
                
                if (!nodeKey.isEmpty()) {
                    nodesToRemove.add(new WorkflowPromptResponse.NodeToRemove(
                        nodeKey,
                        reason
                    ));
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Error saving parsed item: {}", ex.getMessage());
        }
    }

    private Map<String, Object> extractConfig(Map<String, Object> item) {
        Map<String, Object> config = new HashMap<>();
        
        // Extract common config fields
        String[] configKeys = {"url", "method", "script", "prompt", "to", "subject", 
            "body", "condition", "message", "headers", "apiKey"};
        
        for (String key : configKeys) {
            if (item.containsKey(key)) {
                Object value = item.get(key);
                // Remove quotes if present
                if (value instanceof String) {
                    String strValue = (String) value;
                    if (strValue.startsWith("\"") && strValue.endsWith("\"")) {
                        strValue = strValue.substring(1, strValue.length() - 1);
                    }
                    config.put(key, strValue);
                } else {
                    config.put(key, value);
                }
            }
        }
        
        return config;
    }

    private String generateExplanation(
            WorkflowPromptRequest request,
            List<WorkflowPromptResponse.NodeToAdd> nodesToAdd,
            List<WorkflowPromptResponse.EdgeToAdd> edgesToAdd,
            List<WorkflowPromptResponse.NodeToModify> nodesToModify,
            List<WorkflowPromptResponse.NodeToRemove> nodesToRemove) {
        
        StringBuilder explanation = new StringBuilder();
        explanation.append("Based on your request to ").append(request.action().toLowerCase())
                  .append(" the workflow:\n\n");
        
        if (!nodesToAdd.isEmpty()) {
            explanation.append("• Adding ").append(nodesToAdd.size())
                      .append(" new node(s) to the workflow\n");
        }
        if (!edgesToAdd.isEmpty()) {
            explanation.append("• Adding ").append(edgesToAdd.size())
                      .append(" new connection(s) between nodes\n");
        }
        if (!nodesToModify.isEmpty()) {
            explanation.append("• Modifying ").append(nodesToModify.size())
                      .append(" existing node(s)\n");
        }
        if (!nodesToRemove.isEmpty()) {
            explanation.append("• Removing ").append(nodesToRemove.size())
                      .append(" node(s) from the workflow\n");
        }
        
        return explanation.toString();
    }

    private String generateNextSteps(
            WorkflowPromptRequest request,
            List<WorkflowPromptResponse.NodeToAdd> nodesToAdd,
            List<WorkflowPromptResponse.EdgeToAdd> edgesToAdd,
            List<WorkflowPromptResponse.NodeToModify> nodesToModify,
            List<WorkflowPromptResponse.NodeToRemove> nodesToRemove) {
        
        StringBuilder steps = new StringBuilder();
        
        if (!nodesToAdd.isEmpty() || !edgesToAdd.isEmpty()) {
            steps.append("1. Review the suggested changes\n");
            steps.append("2. Click 'Apply All Changes' to implement them\n");
            steps.append("3. Verify the workflow structure in the visual editor\n");
        }
        
        if (!nodesToModify.isEmpty()) {
            steps.append("4. Check modified node configurations\n");
        }
        
        steps.append("5. Test the workflow to ensure it works as expected\n");
        
        return steps.toString();
    }

    private WorkflowDefinitionRequest buildCompleteWorkflow(
            WorkflowPromptRequest request,
            List<WorkflowPromptResponse.NodeToAdd> nodesToAdd,
            List<WorkflowPromptResponse.EdgeToAdd> edgesToAdd) {
        
        List<WorkflowNodeRequest> workflowNodes = new ArrayList<>();
        List<WorkflowEdgeRequest> workflowEdges = new ArrayList<>();
        
        // Convert nodes to add
        for (int i = 0; i < nodesToAdd.size(); i++) {
            var nodeToAdd = nodesToAdd.get(i);
            workflowNodes.add(new WorkflowNodeRequest(
                nodeToAdd.key(),
                nodeToAdd.displayName(),
                nodeToAdd.type(),
                i,
                nodeToAdd.config(),
                null
            ));
        }
        
        // Convert edges to add
        for (var edgeToAdd : edgesToAdd) {
            workflowEdges.add(new WorkflowEdgeRequest(
                edgeToAdd.sourceKey(),
                edgeToAdd.targetKey(),
                edgeToAdd.conditionExpression(),
                null
            ));
        }
        
        return new WorkflowDefinitionRequest(
            request.workflowName() != null ? request.workflowName() : "AI Generated Workflow",
            request.workflowDescription(),
            null,
            workflowNodes,
            workflowEdges
        );
    }

    private WorkflowPromptResponse generateIntelligentResponse(WorkflowPromptRequest request) {
        List<WorkflowPromptResponse.NodeToAdd> nodesToAdd = new ArrayList<>();
        List<WorkflowPromptResponse.EdgeToAdd> edgesToAdd = new ArrayList<>();
        
        // Intelligent defaults based on action
        if ("CREATE".equals(request.action())) {
            // Create a basic workflow structure
            nodesToAdd.add(new WorkflowPromptResponse.NodeToAdd(
                NodeType.INPUT,
                "input-1",
                "Start",
                "Entry point for workflow",
                new HashMap<>(),
                null
            ));
            
            nodesToAdd.add(new WorkflowPromptResponse.NodeToAdd(
                NodeType.HTTP,
                "http-1",
                "API Call",
                "Process user request",
                Map.of("method", "GET", "url", "https://api.example.com/endpoint"),
                "input-1"
            ));
            
            nodesToAdd.add(new WorkflowPromptResponse.NodeToAdd(
                NodeType.OUTPUT,
                "output-1",
                "End",
                "Workflow completion",
                new HashMap<>(),
                "http-1"
            ));
            
            edgesToAdd.add(new WorkflowPromptResponse.EdgeToAdd(
                "input-1", "http-1", null, "Connect start to API call"
            ));
            edgesToAdd.add(new WorkflowPromptResponse.EdgeToAdd(
                "http-1", "output-1", null, "Connect API call to output"
            ));
        } else if ("EXTEND".equals(request.action()) && 
                   request.currentNodes() != null && !request.currentNodes().isEmpty()) {
            // Add a processing node after the last node
            String lastNodeKey = request.currentNodes().get(request.currentNodes().size() - 1).key();
            nodesToAdd.add(new WorkflowPromptResponse.NodeToAdd(
                NodeType.OLLAMA,
                "ai-process-" + System.currentTimeMillis(),
                "AI Processing",
                "Add AI processing step",
                Map.of("prompt", "Process the data: {{" + lastNodeKey + "::body}}"),
                lastNodeKey
            ));
        }
        
        return new WorkflowPromptResponse(
            "Generated workflow changes based on your prompt. Please review and apply.",
            nodesToAdd,
            edgesToAdd,
            new ArrayList<>(),
            new ArrayList<>(),
            null,
            "Review the suggested changes and click 'Apply All Changes' to implement them."
        );
    }

    private WorkflowPromptResponse generateFallbackPromptResponse(
            WorkflowPromptRequest request, 
            String errorMessage) {
        
        return new WorkflowPromptResponse(
            "Error processing prompt: " + errorMessage + ". Using fallback suggestions.",
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            null,
            "Please try rephrasing your prompt or try again later."
        );
    }

    /**
     * Determines if we can skip AI call and use fast fallback for simple requests
     */
    private boolean canUseFastFallback(WorkflowPromptRequest request) {
        // Use fast fallback if:
        // 1. Very simple prompts (less than 20 chars)
        // 2. CORRECT action with no nodes (nothing to correct)
        // 3. MODIFY action with no nodes (nothing to modify)
        
        if (request.prompt() != null && request.prompt().trim().length() < 20) {
            return true;
        }
        
        if (("CORRECT".equals(request.action()) || "MODIFY".equals(request.action())) &&
            (request.currentNodes() == null || request.currentNodes().isEmpty())) {
            return true;
        }
        
        return false;
    }

    /**
     * Streams suggestions progressively for better UX
     */
    public void getSuggestionsStream(VisualEditorSuggestionRequest request, SseEmitter emitter) {
        try {
            if (request.nodes().size() > 50) {
                LOGGER.debug("Workflow too large ({} nodes), using fallback suggestions", request.nodes().size());
                VisualEditorSuggestionResponse fallback = generateFallbackSuggestions(request);
                emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fallback)));
                emitter.complete();
                return;
            }

            String prompt = buildSuggestionPrompt(request);
            LOGGER.debug("Streaming AI suggestions for workflow with {} nodes", request.nodes().size());

            StringBuilder fullResponse = new StringBuilder();
            ollamaClient.generateTextStream(null, prompt, 150)
                .subscribe(
                    chunk -> {
                        try {
                            fullResponse.append(chunk);
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
                        } catch (IOException e) {
                            LOGGER.error("Error sending chunk", e);
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        LOGGER.warn("AI call failed, using fallback: {}", error.getMessage());
                        try {
                            VisualEditorSuggestionResponse fallback = generateFallbackSuggestions(request);
                            emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fallback)));
                            emitter.complete();
                        } catch (IOException e) {
                            LOGGER.debug("Client disconnected during fallback: {}", e.getMessage());
                        } catch (Exception e) {
                            LOGGER.warn("Error sending fallback: {}", e.getMessage());
                        }
                    },
                    () -> {
                        try {
                            String aiResponse = fullResponse.toString();
                            VisualEditorSuggestionResponse response = parseSuggestions(aiResponse, request);
                            
                            if (response.suggestedNodes().isEmpty()) {
                                response = generateFallbackSuggestions(request);
                            }
                            
                            try {
                                emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response)));
                                emitter.complete();
                            } catch (IOException e) {
                                // Client disconnected - this is normal
                                LOGGER.debug("Client disconnected before completion: {}", e.getMessage());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error processing final response", e);
                            try {
                                emitter.send(SseEmitter.event().name("error").data("Error processing response"));
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
            LOGGER.error("Error in getSuggestionsStream", ex);
            try {
                emitter.send(SseEmitter.event().name("error").data("Error getting suggestions"));
                emitter.complete();
            } catch (IOException e) {
                LOGGER.debug("Client disconnected during error handling: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Error sending error event: {}", e.getMessage());
            }
        }
    }

    /**
     * Streams workflow analysis progressively
     */
    public void analyzeWorkflowStream(WorkflowAnalysisRequest request, SseEmitter emitter) {
        try {
            LOGGER.debug("Streaming workflow analysis");

            // First, perform structural analysis (non-streaming)
            List<WorkflowAnalysisResponse.WorkflowIssue> issues = analyzeWorkflowStructure(request);
            List<WorkflowAnalysisResponse.FlowCorrection> corrections = generateCorrections(request, issues);
            List<WorkflowAnalysisResponse.NodeSuggestion> missingNodes = suggestMissingNodes(request);

            // Stream the AI analysis explanation
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Workflow: ").append(request.workflowName() != null ? request.workflowName() : "Untitled");
            promptBuilder.append(" (").append(request.nodes().size()).append(" nodes)\n");
            
            if (!issues.isEmpty()) {
                int errorCount = (int) issues.stream().filter(i -> "ERROR".equals(i.severity())).count();
                int warningCount = issues.size() - errorCount;
                promptBuilder.append("Issues: ").append(errorCount).append(" errors, ").append(warningCount).append(" warnings\n");
            }
            
            promptBuilder.append("Brief assessment and 2-3 suggestions.\n");
            
            String aiPrompt = promptBuilder.toString();
            if (aiPrompt.length() > 300) {
                aiPrompt = aiPrompt.substring(0, 300);
            }
            StringBuilder fullResponse = new StringBuilder();
            
            ollamaClient.generateTextStream(null, aiPrompt, 250)
                .subscribe(
                    chunk -> {
                        try {
                            fullResponse.append(chunk);
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
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
                        LOGGER.warn("AI analysis failed, using fallback: {}", error.getMessage());
                        try {
                            String fallbackAnalysis = "Workflow analysis completed. " + issues.size() + " issue(s) found.";
                            boolean isValid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
                            WorkflowAnalysisResponse fallback = new WorkflowAnalysisResponse(
                                issues, corrections, missingNodes, fallbackAnalysis, isValid
                            );
                            emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fallback)));
                            emitter.complete();
                        } catch (IOException e) {
                            LOGGER.debug("Client disconnected during fallback: {}", e.getMessage());
                        } catch (Exception e) {
                            LOGGER.warn("Error sending fallback: {}", e.getMessage());
                        }
                    },
                    () -> {
                        try {
                            String aiResponse = fullResponse.toString();
                            String aiAnalysis = (aiResponse != null && !aiResponse.trim().isEmpty()) 
                                ? aiResponse 
                                : "Workflow analysis completed. " + issues.size() + " issue(s) found.";
                            
                            boolean isValid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
                            WorkflowAnalysisResponse response = new WorkflowAnalysisResponse(
                                issues, corrections, missingNodes, aiAnalysis, isValid
                            );
                            
                            try {
                                emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response)));
                                emitter.complete();
                            } catch (IOException e) {
                                LOGGER.debug("Client disconnected before completion: {}", e.getMessage());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error processing final analysis", e);
                            try {
                                emitter.send(SseEmitter.event().name("error").data("Error processing analysis"));
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
            LOGGER.error("Error in analyzeWorkflowStream", ex);
            try {
                emitter.send(SseEmitter.event().name("error").data("Error analyzing workflow"));
                emitter.complete();
            } catch (IOException e) {
                LOGGER.debug("Client disconnected during error handling: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Error sending error event: {}", e.getMessage());
            }
        }
    }

    /**
     * Streams workflow prompt processing progressively
     */
    public void processWorkflowPromptStream(WorkflowPromptRequest request, SseEmitter emitter) {
        try {
            if (canUseFastFallback(request)) {
                LOGGER.debug("Using fast fallback for prompt processing");
                WorkflowPromptResponse fallback = generateIntelligentResponse(request);
                emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fallback)));
                emitter.complete();
                return;
            }

            String aiPrompt = buildWorkflowPrompt(request);
            LOGGER.debug("Streaming workflow prompt processing");

            StringBuilder fullResponse = new StringBuilder();
            ollamaClient.generateTextStream(null, aiPrompt, 250)
                .subscribe(
                    chunk -> {
                        try {
                            fullResponse.append(chunk);
                            emitter.send(SseEmitter.event().name("chunk").data(chunk));
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
                        LOGGER.warn("AI prompt processing failed, using fallback: {}", error.getMessage());
                        try {
                            WorkflowPromptResponse fallback = generateIntelligentResponse(request);
                            emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fallback)));
                            emitter.complete();
                        } catch (IOException e) {
                            LOGGER.debug("Client disconnected during fallback: {}", e.getMessage());
                        } catch (Exception e) {
                            LOGGER.warn("Error sending fallback: {}", e.getMessage());
                        }
                    },
                    () -> {
                        try {
                            String aiResponse = fullResponse.toString();
                            WorkflowPromptResponse response = parseWorkflowPromptResponse(aiResponse, request);
                            
                            if (response.nodesToAdd().isEmpty() && 
                                response.edgesToAdd().isEmpty() && 
                                response.nodesToModify().isEmpty() && 
                                response.nodesToRemove().isEmpty() &&
                                (response.explanation() == null || response.explanation().trim().isEmpty())) {
                                response = generateIntelligentResponse(request);
                            }
                            
                            try {
                                emitter.send(SseEmitter.event().name("complete").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response)));
                                emitter.complete();
                            } catch (IOException e) {
                                LOGGER.debug("Client disconnected before completion: {}", e.getMessage());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error processing final prompt response", e);
                            try {
                                emitter.send(SseEmitter.event().name("error").data("Error processing prompt"));
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
            LOGGER.error("Error in processWorkflowPromptStream", ex);
            try {
                emitter.send(SseEmitter.event().name("error").data("Error processing prompt"));
                emitter.complete();
            } catch (IOException e) {
                LOGGER.debug("Client disconnected during error handling: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Error sending error event: {}", e.getMessage());
            }
        }
    }
}

