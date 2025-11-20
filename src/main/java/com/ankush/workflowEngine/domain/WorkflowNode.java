package com.ankush.workflowEngine.domain;

import com.ankush.workflowEngine.enums.NodeType;
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
@Table(name = "workflow_nodes")
public class WorkflowNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(nullable = false, length = 128)
    private String nodeKey;

    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NodeType type;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(columnDefinition = "json")
    private String config;

    @Column(columnDefinition = "json")
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt;

    public WorkflowNode() {
        // JPA constructor
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
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

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
