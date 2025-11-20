package com.ankush.workflowEngine.repository;

import com.ankush.workflowEngine.domain.WorkflowNode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, Long> {

    List<WorkflowNode> findByWorkflowDefinitionIdOrderBySortOrderAsc(Long workflowDefinitionId);
}
