package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowNodeRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowNodeRunRepository extends JpaRepository<WorkflowNodeRun, Long> {

    List<WorkflowNodeRun> findByWorkflowRunId(Long workflowRunId);
}
