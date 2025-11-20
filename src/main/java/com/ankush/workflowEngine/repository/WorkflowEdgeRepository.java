package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowEdge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, Long> {

    List<WorkflowEdge> findByWorkflowDefinitionId(Long workflowDefinitionId);
}
