package com.ankush.workflowEngine.support;

/**
 * Centralized error message formatter for consistent error messages across the application
 */
public final class ErrorMessageFormatter {

    private ErrorMessageFormatter() {
        // Utility class
    }

    // Workflow Definition errors
    public static String workflowNotFound(Long id) {
        return String.format("Workflow definition %d not found", id);
    }

    public static String workflowNotFound(String name, Integer version) {
        return String.format("Workflow definition '%s' version %d not found", name, version);
    }

    public static String workflowIdRequired() {
        return "Workflow definition id must not be null";
    }

    // Workflow Run errors
    public static String workflowRunNotFound(Long runId) {
        return String.format("Workflow run %d not found", runId);
    }

    public static String workflowRunIdRequired() {
        return "Workflow run id must not be null";
    }

    public static String workflowIdRequiredForRun() {
        return "Workflow id must not be null";
    }

    // Node errors
    public static String nodeNotFound(String nodeKey) {
        return String.format("Node '%s' not found", nodeKey);
    }

    public static String nodeKeyRequired() {
        return "Node key must not be null";
    }

    // Edge errors
    public static String edgeNotFound(Long edgeId) {
        return String.format("Edge %d not found", edgeId);
    }

    public static String invalidEdge(String sourceKey, String targetKey) {
        return String.format("Invalid edge from '%s' to '%s'", sourceKey, targetKey);
    }

    // Validation errors
    public static String workflowValidationFailed(String reason) {
        return String.format("Workflow validation failed: %s", reason);
    }

    public static String duplicateNodeKey(String nodeKey) {
        return String.format("Duplicate node key found: %s", nodeKey);
    }

    public static String invalidNodeReference(String nodeKey) {
        return String.format("Edge references non-existent node: %s", nodeKey);
    }

    public static String workflowHasCycles() {
        return "Workflow graph contains cycles";
    }

    public static String noEntryNode() {
        return "Workflow must have at least one entry node (node with no incoming edges)";
    }

    // Execution errors
    public static String nodeExecutionFailed(String nodeKey, String reason) {
        return String.format("Node '%s' execution failed: %s", nodeKey, reason);
    }

    public static String executorNotFound(String nodeType) {
        return String.format("No executor registered for node type: %s", nodeType);
    }

    // Generic errors
    public static String requiredField(String fieldName) {
        return String.format("%s must not be null", fieldName);
    }

    public static String invalidValue(String fieldName, Object value) {
        return String.format("Invalid value for %s: %s", fieldName, value);
    }
}

