package com.ankush.workflowEngine.domain;

import com.ankush.workflowEngine.enums.NodeRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_node_runs")
public class WorkflowNodeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    private WorkflowRun workflowRun;

    @Column(nullable = false, length = 128)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NodeRunStatus status;

    private Instant startedAt;

    private Instant completedAt;

    @Column(columnDefinition = "json")
    private String inputPayload;

    @Column(columnDefinition = "json")
    private String outputPayload;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public WorkflowNodeRun() {
        // JPA constructor
    }

    @PrePersist
    void onCreate() {
        // ID is auto-generated
    }

    public void markRunning() {
        status = NodeRunStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void markSuccess(String output) {
        status = NodeRunStatus.SUCCESS;
        completedAt = Instant.now();
        outputPayload = output;
    }

    public void markFailed(String error) {
        status = NodeRunStatus.FAILED;
        completedAt = Instant.now();
        errorMessage = error;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkflowRun getWorkflowRun() {
        return workflowRun;
    }

    public void setWorkflowRun(WorkflowRun workflowRun) {
        this.workflowRun = workflowRun;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public NodeRunStatus getStatus() {
        return status;
    }

    public void setStatus(NodeRunStatus status) {
        this.status = status;
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

    public String getInputPayload() {
        return inputPayload;
    }

    public void setInputPayload(String inputPayload) {
        this.inputPayload = inputPayload;
    }

    public String getOutputPayload() {
        return outputPayload;
    }

    public void setOutputPayload(String outputPayload) {
        this.outputPayload = outputPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
