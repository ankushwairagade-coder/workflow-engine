package com.ankush.workflowEngine.execution;

import java.time.Instant;

/**
 * Represents an error that occurred during node execution
 */
public class NodeExecutionError {
    private final String code;
    private final String message;
    private final String nodeKey;
    private final Instant timestamp;
    private final boolean retryable;
    private final String stackTrace;

    public NodeExecutionError(String code, String message, String nodeKey, boolean retryable, String stackTrace) {
        this.code = code;
        this.message = message;
        this.nodeKey = nodeKey;
        this.timestamp = Instant.now();
        this.retryable = retryable;
        this.stackTrace = stackTrace;
    }

    public static NodeExecutionError fromException(Exception ex, String nodeKey) {
        String code = determineErrorCode(ex);
        boolean retryable = isRetryable(ex);
        String stackTrace = getStackTrace(ex);
        
        return new NodeExecutionError(
            code,
            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
            nodeKey,
            retryable,
            stackTrace
        );
    }

    private static String determineErrorCode(Exception ex) {
        if (ex instanceof NodeExecutionException) {
            return "NODE_EXECUTION_ERROR";
        } else if (ex instanceof IllegalArgumentException) {
            return "INVALID_INPUT";
        } else if (ex instanceof NullPointerException) {
            return "NULL_REFERENCE";
        } else {
            return "UNKNOWN_ERROR";
        }
    }

    private static boolean isRetryable(Exception ex) {
        // Network errors, timeouts, etc. are retryable
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        return message.contains("timeout") || 
               message.contains("connection") || 
               message.contains("network") ||
               ex instanceof java.net.SocketTimeoutException ||
               ex instanceof java.net.ConnectException;
    }

    private static String getStackTrace(Exception ex) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getStackTrace() {
        return stackTrace;
    }
}

