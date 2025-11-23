package com.ankush.workflowEngine.dto;

import java.time.Instant;

/**
 * Standard error response DTO for consistent error format across all endpoints
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        String details) {

    public ErrorResponse(String code, String message, String path) {
        this(code, message, Instant.now(), path, null);
    }

    public ErrorResponse(String code, String message, String path, String details) {
        this(code, message, Instant.now(), path, details);
    }
}

