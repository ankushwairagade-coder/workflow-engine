package com.ankush.workflowEngine.domain;

import com.ankush.workflowEngine.enums.RunStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(columnDefinition = "json")
    private String triggerPayload;

    @Column(columnDefinition = "json")
    private String contextData;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    public WorkflowRun() {
        // JPA constructor
    }

    public void markPending() {
        status = RunStatus.PENDING;
        createdAt = Instant.now();
    }

    public void markRunning() {
        status = RunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void markCompleted() {
        status = RunStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String error) {
        status = RunStatus.FAILED;
        completedAt = Instant.now();
        lastError = error;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getTriggerPayload() {
        return triggerPayload;
    }

    public void setTriggerPayload(String triggerPayload) {
        this.triggerPayload = triggerPayload;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

}
