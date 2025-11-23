-- Add indexes for frequently queried columns to improve query performance

-- Index on workflow_definitions for name and version (used for versioning)
CREATE INDEX idx_workflow_definitions_name_version ON workflow_definitions(name, version);

-- Index on workflow_definitions for status (used for filtering)
CREATE INDEX idx_workflow_definitions_status ON workflow_definitions(status);

-- Index on workflow_definitions for created_at (used for sorting)
CREATE INDEX idx_workflow_definitions_created_at ON workflow_definitions(created_at DESC);

-- Index on workflow_definitions for updated_at (used for sorting)
CREATE INDEX idx_workflow_definitions_updated_at ON workflow_definitions(updated_at DESC);

-- Index on workflow_nodes for node_key (used for lookups and uniqueness checks)
CREATE INDEX idx_workflow_nodes_node_key ON workflow_nodes(node_key);

-- Index on workflow_nodes for type (used for filtering)
CREATE INDEX idx_workflow_nodes_type ON workflow_nodes(type);

-- Index on workflow_edges for source_key and target_key (used for graph traversal)
CREATE INDEX idx_workflow_edges_source_key ON workflow_edges(source_key);
CREATE INDEX idx_workflow_edges_target_key ON workflow_edges(target_key);

-- Index on workflow_runs for status (used for filtering)
CREATE INDEX idx_workflow_runs_status ON workflow_runs(status);

-- Index on workflow_runs for created_at (used for sorting)
CREATE INDEX idx_workflow_runs_created_at ON workflow_runs(created_at DESC);

-- Index on workflow_runs for started_at (used for sorting and filtering)
CREATE INDEX idx_workflow_runs_started_at ON workflow_runs(started_at DESC);

-- Index on workflow_runs for completed_at (used for sorting and filtering)
CREATE INDEX idx_workflow_runs_completed_at ON workflow_runs(completed_at DESC);

-- Index on workflow_node_runs for node_key (used for lookups)
CREATE INDEX idx_workflow_node_runs_node_key ON workflow_node_runs(node_key);

-- Index on workflow_node_runs for status (used for filtering)
CREATE INDEX idx_workflow_node_runs_status ON workflow_node_runs(status);

-- Composite index on workflow_node_runs for workflow_run_id and status (common query pattern)
CREATE INDEX idx_workflow_node_runs_run_status ON workflow_node_runs(workflow_run_id, status);

