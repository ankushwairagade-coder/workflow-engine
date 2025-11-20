package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowDefinition;
import com.ankush.workflowEngine.enums.WorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    List<WorkflowDefinition> findAllByStatusOrderByUpdatedAtDesc(WorkflowStatus status);

    Optional<WorkflowDefinition> findTopByNameOrderByVersionDesc(String name);

    @Query("select distinct w from WorkflowDefinition w left join fetch w.nodes left join fetch w.edges")
    List<WorkflowDefinition> findAllWithNodesAndEdges();

    @Query("select distinct w from WorkflowDefinition w left join fetch w.nodes left join fetch w.edges where w.id = :id")
    Optional<WorkflowDefinition> findByIdWithNodesAndEdges(@Param("id") Long id);
}
