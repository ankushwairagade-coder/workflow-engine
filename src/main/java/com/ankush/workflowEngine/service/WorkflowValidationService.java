package com.ankush.workflowEngine.service;

import com.ankush.workflowEngine.domain.WorkflowDefinition;
import com.ankush.workflowEngine.domain.WorkflowEdge;
import com.ankush.workflowEngine.domain.WorkflowNode;
import com.ankush.workflowEngine.dto.WorkflowDefinitionRequest;
import com.ankush.workflowEngine.dto.WorkflowEdgeRequest;
import com.ankush.workflowEngine.dto.WorkflowNodeRequest;
import com.ankush.workflowEngine.support.ErrorMessageFormatter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkflowValidationService {

    /**
     * Validates a workflow definition request before creation
     * @throws IllegalArgumentException if validation fails
     */
    public void validateWorkflowRequest(WorkflowDefinitionRequest request) {
        List<String> errors = new ArrayList<>();

        // Basic validation
        if (request.nodes() == null || request.nodes().isEmpty()) {
            errors.add("Workflow must contain at least one node");
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        // Collect node keys and check for duplicates
        Set<String> nodeKeys = new HashSet<>();
        Map<String, Integer> keyCounts = new HashMap<>();
        
        for (WorkflowNodeRequest node : request.nodes()) {
            String key = node.key();
            if (key == null || key.isBlank()) {
                errors.add("Node key cannot be null or blank");
                continue;
            }
            nodeKeys.add(key);
            keyCounts.put(key, keyCounts.getOrDefault(key, 0) + 1);
        }
        
        // Check for duplicate node keys - if Set size != List size, there are duplicates
        int totalNodes = request.nodes().size();
        int uniqueKeys = nodeKeys.size();
        
        if (uniqueKeys != totalNodes) {
            // Find which keys are duplicated (appear more than once)
            List<String> duplicateKeysWithCounts = keyCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(entry -> String.format("%s (%d times)", entry.getKey(), entry.getValue()))
                    .sorted()
                    .collect(Collectors.toList());
            
            if (!duplicateKeysWithCounts.isEmpty()) {
                String duplicateKeysStr = String.join(", ", duplicateKeysWithCounts);
                errors.add(ErrorMessageFormatter.duplicateNodeKey(duplicateKeysStr));
            } else {
                // This shouldn't happen, but handle edge case
                errors.add("Duplicate node keys detected but could not identify which keys");
            }
        }

        // Validate edges reference existing nodes
        if (request.edges() != null) {
            for (WorkflowEdgeRequest edge : request.edges()) {
                if (!nodeKeys.contains(edge.sourceKey())) {
                    errors.add(ErrorMessageFormatter.invalidNodeReference(edge.sourceKey()));
                }
                if (!nodeKeys.contains(edge.targetKey())) {
                    errors.add(ErrorMessageFormatter.invalidNodeReference(edge.targetKey()));
                }
                if (edge.sourceKey().equals(edge.targetKey())) {
                    errors.add(ErrorMessageFormatter.invalidEdge(edge.sourceKey(), edge.targetKey()));
                }
            }
        }

        // Check for cycles
        if (request.edges() != null && !request.edges().isEmpty()) {
            if (hasCycles(nodeKeys, request.edges())) {
                errors.add(ErrorMessageFormatter.workflowHasCycles());
            }
        }

        // Ensure at least one entry node (node with no incoming edges)
        if (request.edges() != null && !request.edges().isEmpty()) {
            Set<String> nodesWithIncomingEdges = request.edges().stream()
                    .map(WorkflowEdgeRequest::targetKey)
                    .collect(Collectors.toSet());
            
            long entryNodes = nodeKeys.stream()
                    .filter(key -> !nodesWithIncomingEdges.contains(key))
                    .count();
            
            if (entryNodes == 0) {
                errors.add(ErrorMessageFormatter.noEntryNode());
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageFormatter.workflowValidationFailed(String.join("; ", errors)));
        }
    }

    /**
     * Validates an existing workflow definition
     */
    public void validateWorkflowDefinition(WorkflowDefinition definition) {
        List<String> errors = new ArrayList<>();

        Set<String> nodeKeys = definition.getNodes().stream()
                .map(WorkflowNode::getNodeKey)
                .collect(Collectors.toSet());

        // Check for duplicate node keys
        if (nodeKeys.size() != definition.getNodes().size()) {
            errors.add(ErrorMessageFormatter.duplicateNodeKey("multiple"));
        }

        // Validate edges reference existing nodes
        for (WorkflowEdge edge : definition.getEdges()) {
            if (!nodeKeys.contains(edge.getSourceKey())) {
                errors.add(ErrorMessageFormatter.invalidNodeReference(edge.getSourceKey()));
            }
            if (!nodeKeys.contains(edge.getTargetKey())) {
                errors.add(ErrorMessageFormatter.invalidNodeReference(edge.getTargetKey()));
            }
            if (edge.getSourceKey().equals(edge.getTargetKey())) {
                errors.add(ErrorMessageFormatter.invalidEdge(edge.getSourceKey(), edge.getTargetKey()));
            }
        }

        // Check for cycles
        if (!definition.getEdges().isEmpty()) {
            if (hasCycles(nodeKeys, definition.getEdges())) {
                errors.add(ErrorMessageFormatter.workflowHasCycles());
            }
        }

        // Ensure at least one entry node
        if (!definition.getEdges().isEmpty()) {
            Set<String> nodesWithIncomingEdges = definition.getEdges().stream()
                    .map(WorkflowEdge::getTargetKey)
                    .collect(Collectors.toSet());
            
            long entryNodes = nodeKeys.stream()
                    .filter(key -> !nodesWithIncomingEdges.contains(key))
                    .count();
            
            if (entryNodes == 0) {
                errors.add(ErrorMessageFormatter.noEntryNode());
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageFormatter.workflowValidationFailed(String.join("; ", errors)));
        }
    }

    /**
     * Detects cycles in the workflow graph using DFS
     */
    private boolean hasCycles(Set<String> nodeKeys, List<WorkflowEdgeRequest> edges) {
        // Build adjacency list
        Map<String, List<String>> adjacencyList = new HashMap<>();
        for (String key : nodeKeys) {
            adjacencyList.put(key, new ArrayList<>());
        }
        
        for (WorkflowEdgeRequest edge : edges) {
            adjacencyList.get(edge.sourceKey()).add(edge.targetKey());
        }

        return hasCyclesDFS(nodeKeys, adjacencyList);
    }

    private boolean hasCycles(Set<String> nodeKeys, Set<WorkflowEdge> edges) {
        // Build adjacency list
        Map<String, List<String>> adjacencyList = new HashMap<>();
        for (String key : nodeKeys) {
            adjacencyList.put(key, new ArrayList<>());
        }
        
        for (WorkflowEdge edge : edges) {
            adjacencyList.get(edge.getSourceKey()).add(edge.getTargetKey());
        }

        return hasCyclesDFS(nodeKeys, adjacencyList);
    }

    private boolean hasCyclesDFS(Set<String> nodeKeys, Map<String, List<String>> adjacencyList) {
        // Track visited nodes and recursion stack
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : nodeKeys) {
            if (hasCycleDFS(node, adjacencyList, visited, recursionStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycleDFS(String node, Map<String, List<String>> adjacencyList,
                                Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true; // Cycle detected
        }
        
        if (visited.contains(node)) {
            return false; // Already processed
        }

        visited.add(node);
        recursionStack.add(node);

        for (String neighbor : adjacencyList.getOrDefault(node, Collections.emptyList())) {
            if (hasCycleDFS(neighbor, adjacencyList, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }
}

