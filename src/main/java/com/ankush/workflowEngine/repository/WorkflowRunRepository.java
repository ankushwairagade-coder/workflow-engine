package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
    
    List<WorkflowRun> findByWorkflowDefinitionId(Long workflowDefinitionId);
    
    /**
     * Finds all workflow runs with their workflow definitions fetched to avoid N+1 queries
     */
    @Query("select distinct r from WorkflowRun r left join fetch r.workflowDefinition order by r.createdAt desc")
    List<WorkflowRun> findAllWithWorkflowDefinition();
    
    /**
     * Finds a workflow run by ID with its workflow definition fetched to avoid N+1 queries
     */
    @Query("select distinct r from WorkflowRun r left join fetch r.workflowDefinition where r.id = :id")
    Optional<WorkflowRun> findByIdWithWorkflowDefinition(@Param("id") Long id);
    
    /**
     * Finds workflow runs by workflow definition ID with workflow definition fetched
     */
    @Query("select distinct r from WorkflowRun r left join fetch r.workflowDefinition where r.workflowDefinition.id = :workflowDefinitionId")
    List<WorkflowRun> findByWorkflowDefinitionIdWithDefinition(@Param("workflowDefinitionId") Long workflowDefinitionId);
}
