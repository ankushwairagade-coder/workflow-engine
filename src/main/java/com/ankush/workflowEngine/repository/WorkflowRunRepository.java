package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
    List<WorkflowRun> findByWorkflowDefinitionId(Long workflowDefinitionId);
}
