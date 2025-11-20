package com.ankush.workflowEngine.service;

import com.ankush.workflowEngine.domain.WorkflowDefinition;
import com.ankush.workflowEngine.domain.WorkflowEdge;
import com.ankush.workflowEngine.domain.WorkflowNode;
import com.ankush.workflowEngine.domain.WorkflowRun;
import com.ankush.workflowEngine.dto.WorkflowDefinitionRequest;
import com.ankush.workflowEngine.dto.WorkflowDefinitionResponse;
import com.ankush.workflowEngine.dto.WorkflowEdgeRequest;
import com.ankush.workflowEngine.dto.WorkflowNodeRequest;
import com.ankush.workflowEngine.enums.WorkflowStatus;
import com.ankush.workflowEngine.mapper.WorkflowMapper;
import com.ankush.workflowEngine.repository.WorkflowDefinitionRepository;
import com.ankush.workflowEngine.repository.WorkflowEdgeRepository;
import com.ankush.workflowEngine.repository.WorkflowNodeRepository;
import com.ankush.workflowEngine.repository.WorkflowRunRepository;
import com.ankush.workflowEngine.support.ErrorMessageFormatter;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowEdgeRepository edgeRepository;
    private final WorkflowRunRepository runRepository;
    private final WorkflowMapper mapper;
    private final WorkflowValidationService validationService;

    public WorkflowDefinitionService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowEdgeRepository edgeRepository,
            WorkflowRunRepository runRepository,
            WorkflowMapper mapper,
            WorkflowValidationService validationService) {
        this.definitionRepository = definitionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.runRepository = runRepository;
        this.mapper = mapper;
        this.validationService = validationService;
    }

    @Transactional
    public WorkflowDefinitionResponse createWorkflow(WorkflowDefinitionRequest request) {
        // Validate workflow structure before creating
        validationService.validateWorkflowRequest(request);
        
        List<WorkflowNodeRequest> nodeRequests = Objects.requireNonNull(request.nodes(), "nodes must not be null");
        if (nodeRequests.isEmpty()) {
            throw new IllegalArgumentException("Workflow must include at least one node");
        }

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(request.name());
        definition.setDescription(request.description());
        definition.setStatus(WorkflowStatus.DRAFT);
        definition.setVersion(nextVersion(request.name()));
        definition.setMetadata(mapper.writeJson(request.metadata()));
        definition = definitionRepository.save(definition);

        AtomicInteger orderCounter = new AtomicInteger(0);
        for (WorkflowNodeRequest nodeRequest : nodeRequests) {
            WorkflowNode node = new WorkflowNode();
            node.setWorkflowDefinition(definition);
            node.setNodeKey(nodeRequest.key());
            node.setDisplayName(nodeRequest.displayName());
            node.setType(nodeRequest.type());
            int sortOrder = nodeRequest.sortOrder() != null ? nodeRequest.sortOrder() : orderCounter.getAndIncrement();
            node.setSortOrder(sortOrder);
            node.setConfig(mapper.writeJson(nodeRequest.config()));
            node.setMetadata(mapper.writeJson(nodeRequest.metadata()));
            nodeRepository.save(node);
            definition.getNodes().add(node);
        }

        List<WorkflowEdgeRequest> edges = request.edges() == null ? List.of() : request.edges();
        for (WorkflowEdgeRequest edgeRequest : edges) {
            WorkflowEdge edge = new WorkflowEdge();
            edge.setWorkflowDefinition(definition);
            edge.setSourceKey(edgeRequest.sourceKey());
            edge.setTargetKey(edgeRequest.targetKey());
            edge.setConditionExpression(edgeRequest.conditionExpression());
            edge.setMetadata(mapper.writeJson(edgeRequest.metadata()));
            edgeRepository.save(edge);
            definition.getEdges().add(edge);
        }

        definition.getNodes().sort(Comparator.comparingInt(WorkflowNode::getSortOrder));
        return mapper.toResponse(definition);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> listDefinitions() {
        return definitionRepository.findAllWithNodesAndEdges().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponse getDefinition(Long id) {
        try {
            MDC.put("workflowId", String.valueOf(id));
            return mapper.toResponse(fetchEntityWithGraph(id));
        } finally {
            MDC.remove("workflowId");
        }
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition fetchEntity(Long id) {
        Long safeId = Objects.requireNonNull(id, ErrorMessageFormatter.workflowIdRequired());
        return definitionRepository.findById(safeId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessageFormatter.workflowNotFound(id)));
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition fetchEntityWithGraph(Long id) {
        Long safeId = Objects.requireNonNull(id, ErrorMessageFormatter.workflowIdRequired());
        return definitionRepository.findByIdWithNodesAndEdges(safeId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessageFormatter.workflowNotFound(id)));
    }

    @Transactional
    public WorkflowDefinitionResponse updateWorkflow(Long id, WorkflowDefinitionRequest request) {
        try {
            MDC.put("workflowId", String.valueOf(id));
            
            // Deduplicate nodes and edges before validation (handle cases where frontend sends duplicates)
            WorkflowDefinitionRequest deduplicatedRequest = deduplicateRequest(request);
            
            // Validate workflow structure before updating
            validationService.validateWorkflowRequest(deduplicatedRequest);
            
            // Fetch existing workflow with nodes and edges
            WorkflowDefinition definition = fetchEntityWithGraph(id);
            
            List<WorkflowNodeRequest> nodeRequests = Objects.requireNonNull(deduplicatedRequest.nodes(), "nodes must not be null");
            if (nodeRequests.isEmpty()) {
                throw new IllegalArgumentException("Workflow must include at least one node");
            }
            
            // Update workflow definition fields
            definition.setName(deduplicatedRequest.name());
            definition.setDescription(deduplicatedRequest.description());
            definition.setMetadata(mapper.writeJson(deduplicatedRequest.metadata()));
            // Note: Status and version are not updated on update - they remain as is
            
            // Clear existing nodes and edges (cascade will handle deletion)
            definition.getNodes().clear();
            definition.getEdges().clear();
            
            // Create new nodes
            AtomicInteger orderCounter = new AtomicInteger(0);
            for (WorkflowNodeRequest nodeRequest : nodeRequests) {
                WorkflowNode node = new WorkflowNode();
                node.setWorkflowDefinition(definition);
                node.setNodeKey(nodeRequest.key());
                node.setDisplayName(nodeRequest.displayName());
                node.setType(nodeRequest.type());
                int sortOrder = nodeRequest.sortOrder() != null ? nodeRequest.sortOrder() : orderCounter.getAndIncrement();
                node.setSortOrder(sortOrder);
                node.setConfig(mapper.writeJson(nodeRequest.config()));
                node.setMetadata(mapper.writeJson(nodeRequest.metadata()));
                nodeRepository.save(node);
                definition.getNodes().add(node);
            }
            
            // Create new edges
            List<WorkflowEdgeRequest> edges = deduplicatedRequest.edges() == null ? List.of() : deduplicatedRequest.edges();
            for (WorkflowEdgeRequest edgeRequest : edges) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setWorkflowDefinition(definition);
                edge.setSourceKey(edgeRequest.sourceKey());
                edge.setTargetKey(edgeRequest.targetKey());
                edge.setConditionExpression(edgeRequest.conditionExpression());
                edge.setMetadata(mapper.writeJson(edgeRequest.metadata()));
                edgeRepository.save(edge);
                definition.getEdges().add(edge);
            }
            
            // Save the updated definition
            definition = definitionRepository.save(definition);
            definition.getNodes().sort(Comparator.comparingInt(WorkflowNode::getSortOrder));
            
            return mapper.toResponse(definition);
        } finally {
            MDC.remove("workflowId");
        }
    }
    
    @Transactional
    public void deleteWorkflow(Long id) {
        try {
            MDC.put("workflowId", String.valueOf(id));
            // Fetch to ensure it exists (will throw if not found)
            WorkflowDefinition definition = fetchEntity(id);
            
            // Delete all associated workflow runs first (to avoid foreign key constraint violation)
            List<WorkflowRun> runs = runRepository.findByWorkflowDefinitionId(id);
            if (!runs.isEmpty()) {
                runRepository.deleteAll(runs);
            }
            
            // Delete the workflow (cascade will handle nodes and edges)
            definitionRepository.delete(definition);
        } finally {
            MDC.remove("workflowId");
        }
    }

    /**
     * Deduplicates nodes and edges in the request, keeping only the first occurrence of each node key
     * and edge (sourceKey, targetKey) combination.
     */
    private WorkflowDefinitionRequest deduplicateRequest(WorkflowDefinitionRequest request) {
        if (request.nodes() == null || request.nodes().isEmpty()) {
            return request;
        }
        
        // Deduplicate nodes by key (keep first occurrence)
        Set<String> seenNodeKeys = new HashSet<>();
        List<WorkflowNodeRequest> deduplicatedNodes = new ArrayList<>();
        
        for (WorkflowNodeRequest node : request.nodes()) {
            String key = node.key();
            if (key != null && !key.isBlank() && !seenNodeKeys.contains(key)) {
                seenNodeKeys.add(key);
                deduplicatedNodes.add(node);
            }
        }
        
        // Deduplicate edges by sourceKey-targetKey combination (keep first occurrence)
        List<WorkflowEdgeRequest> deduplicatedEdges = new ArrayList<>();
        if (request.edges() != null && !request.edges().isEmpty()) {
            Set<String> seenEdges = new LinkedHashSet<>();
            
            for (WorkflowEdgeRequest edge : request.edges()) {
                String edgeKey = edge.sourceKey() + "->" + edge.targetKey();
                if (!seenEdges.contains(edgeKey)) {
                    seenEdges.add(edgeKey);
                    deduplicatedEdges.add(edge);
                }
            }
        }
        
        return new WorkflowDefinitionRequest(
                request.name(),
                request.description(),
                request.metadata(),
                deduplicatedNodes,
                deduplicatedEdges.isEmpty() ? null : deduplicatedEdges
        );
    }

    private int nextVersion(String name) {
        return definitionRepository.findTopByNameOrderByVersionDesc(name)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
    }
}
