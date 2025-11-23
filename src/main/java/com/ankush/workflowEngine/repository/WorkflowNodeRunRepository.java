package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowNodeRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowNodeRunRepository extends JpaRepository<WorkflowNodeRun, Long> {

    List<WorkflowNodeRun> findByWorkflowRunId(Long workflowRunId);
    
    /**
     * Finds all node runs for multiple workflow run IDs in a single query to avoid N+1
     */
    @Query("select n from WorkflowNodeRun n where n.workflowRun.id in :runIds")
    List<WorkflowNodeRun> findByWorkflowRunIds(@Param("runIds") List<Long> runIds);
}
