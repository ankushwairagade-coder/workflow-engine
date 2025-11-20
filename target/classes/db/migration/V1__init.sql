CREATE TABLE workflow_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    metadata JSON,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE workflow_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL,
    node_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(255),
    type VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    config JSON,
    metadata JSON,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_node_definition FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions(id) ON DELETE CASCADE
);
CREATE INDEX idx_nodes_definition ON workflow_nodes (workflow_definition_id);

CREATE TABLE workflow_edges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL,
    source_key VARCHAR(128) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    condition_expression VARCHAR(1024),
    metadata JSON,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_edge_definition FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions(id) ON DELETE CASCADE
);
CREATE INDEX idx_edges_definition ON workflow_edges (workflow_definition_id);

CREATE TABLE workflow_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    trigger_payload JSON,
    context_data JSON,
    last_error TEXT,
    CONSTRAINT fk_run_definition FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions(id)
);
CREATE INDEX idx_runs_definition ON workflow_runs (workflow_definition_id);

CREATE TABLE workflow_node_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT NOT NULL,
    node_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    input_payload JSON,
    output_payload JSON,
    error_message TEXT,
    CONSTRAINT fk_node_run_run FOREIGN KEY (workflow_run_id) REFERENCES workflow_runs(id) ON DELETE CASCADE
);
CREATE INDEX idx_node_runs_run ON workflow_node_runs (workflow_run_id);
