package com.ankush.workflowEngine.execution;

/**
 * Event published after a workflow run is created and transaction is committed
 */
public class WorkflowRunCreatedEvent {
    private final Long runId;

    public WorkflowRunCreatedEvent(Long runId) {
        this.runId = runId;
    }

    public Long getRunId() {
        return runId;
    }
}

