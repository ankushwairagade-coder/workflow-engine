package com.ankush.workflowEngine.service;

import com.ankush.workflowEngine.domain.WorkflowDefinition;
import com.ankush.workflowEngine.domain.WorkflowEdge;
import com.ankush.workflowEngine.domain.WorkflowNode;
import com.ankush.workflowEngine.domain.WorkflowNodeRun;
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
import com.ankush.workflowEngine.repository.WorkflowNodeRunRepository;
import com.ankush.workflowEngine.repository.WorkflowRunRepository;
import com.ankush.workflowEngine.support.ErrorMessageFormatter;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowDefinitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowEdgeRepository edgeRepository;
    private final WorkflowRunRepository runRepository;
    private final WorkflowNodeRunRepository nodeRunRepository;
    private final WorkflowMapper mapper;
    private final WorkflowValidationService validationService;

    public WorkflowDefinitionService(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowEdgeRepository edgeRepository,
            WorkflowRunRepository runRepository,
            WorkflowNodeRunRepository nodeRunRepository,
            WorkflowMapper mapper,
            WorkflowValidationService validationService) {
        this.definitionRepository = definitionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.runRepository = runRepository;
        this.nodeRunRepository = nodeRunRepository;
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

        // Check if a workflow with the same name already exists
        // If exists, update it instead of creating a new one
        Optional<WorkflowDefinition> existingWorkflowOpt = definitionRepository
                .findTopByNameOrderByVersionDesc(request.name());
        
        WorkflowDefinition definition;
        boolean isUpdate = existingWorkflowOpt.isPresent();
        
        if (isUpdate) {
            // Update existing workflow - fetch with nodes and edges
            definition = fetchEntityWithGraph(existingWorkflowOpt.get().getId());
            LOGGER.debug("Updating existing workflow: {} (id: {})", request.name(), definition.getId());
            
            // Update workflow definition fields
            definition.setName(request.name());
            definition.setDescription(request.description());
            definition.setMetadata(mapper.writeJson(request.metadata()));
            // Keep existing status and version - don't change them on update
            
            // Clear existing nodes and edges (cascade will handle deletion)
            definition.getNodes().clear();
            definition.getEdges().clear();
        } else {
            // Create new workflow
            definition = new WorkflowDefinition();
            definition.setName(request.name());
            definition.setDescription(request.description());
            definition.setStatus(WorkflowStatus.DRAFT);
            definition.setVersion(nextVersion(request.name()));
            definition.setMetadata(mapper.writeJson(request.metadata()));
            definition = definitionRepository.save(definition);
            LOGGER.debug("Creating new workflow: {}", request.name());
        }

        // Batch create nodes for better performance
        AtomicInteger orderCounter = new AtomicInteger(0);
        List<WorkflowNode> nodesToSave = new ArrayList<>(nodeRequests.size());
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
            nodesToSave.add(node);
            definition.getNodes().add(node);
        }
        // Batch save all nodes at once
        nodeRepository.saveAll(nodesToSave);

        // Batch create edges for better performance
        List<WorkflowEdgeRequest> edges = request.edges() == null ? List.of() : request.edges();
        if (!edges.isEmpty()) {
            List<WorkflowEdge> edgesToSave = new ArrayList<>(edges.size());
            for (WorkflowEdgeRequest edgeRequest : edges) {
                WorkflowEdge edge = new WorkflowEdge();
                edge.setWorkflowDefinition(definition);
                edge.setSourceKey(edgeRequest.sourceKey());
                edge.setTargetKey(edgeRequest.targetKey());
                edge.setConditionExpression(edgeRequest.conditionExpression());
                edge.setMetadata(mapper.writeJson(edgeRequest.metadata()));
                edgesToSave.add(edge);
                definition.getEdges().add(edge);
            }
            // Batch save all edges at once
            edgeRepository.saveAll(edgesToSave);
        }

        // Save the definition to ensure all changes are persisted
        // (especially important for updates where we cleared nodes/edges)
        definition = definitionRepository.save(definition);
        definition.getNodes().sort(Comparator.comparingInt(WorkflowNode::getSortOrder));
        return mapper.toResponse(definition);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> listDefinitions() {
        // Use pagination for large datasets - fetch in batches
        // For now, return all but consider adding pagination parameters
        List<WorkflowDefinition> definitions = definitionRepository.findAllWithNodesAndEdges();
        return definitions.stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponse getDefinition(Long id) {
        try {
            MDC.put("workflowId", String.valueOf(id));
            // Note: Not caching DTOs directly due to type deserialization issues with Redis
            // DTOs are lightweight transformations, so caching provides minimal benefit
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
            
            // Batch create new nodes for better performance
            AtomicInteger orderCounter = new AtomicInteger(0);
            List<WorkflowNode> nodesToSave = new ArrayList<>(nodeRequests.size());
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
                nodesToSave.add(node);
                definition.getNodes().add(node);
            }
            // Batch save all nodes at once
            nodeRepository.saveAll(nodesToSave);
            
            // Batch create new edges for better performance
            List<WorkflowEdgeRequest> edges = deduplicatedRequest.edges() == null ? List.of() : deduplicatedRequest.edges();
            if (!edges.isEmpty()) {
                List<WorkflowEdge> edgesToSave = new ArrayList<>(edges.size());
                for (WorkflowEdgeRequest edgeRequest : edges) {
                    WorkflowEdge edge = new WorkflowEdge();
                    edge.setWorkflowDefinition(definition);
                    edge.setSourceKey(edgeRequest.sourceKey());
                    edge.setTargetKey(edgeRequest.targetKey());
                    edge.setConditionExpression(edgeRequest.conditionExpression());
                    edge.setMetadata(mapper.writeJson(edgeRequest.metadata()));
                    edgesToSave.add(edge);
                    definition.getEdges().add(edge);
                }
                // Batch save all edges at once
                edgeRepository.saveAll(edgesToSave);
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
            
            // Batch delete all associated workflow runs and their node runs (to avoid foreign key constraint violations)
            // Use optimized query to fetch runs with definition to avoid N+1
            List<WorkflowRun> runs = runRepository.findByWorkflowDefinitionIdWithDefinition(id);
            if (!runs.isEmpty()) {
                // Collect all run IDs for batch fetching node runs (avoids N+1)
                List<Long> runIds = runs.stream().map(WorkflowRun::getId).toList();
                
                // Batch fetch all node runs in a single query instead of N queries
                List<WorkflowNodeRun> allNodeRuns = nodeRunRepository.findByWorkflowRunIds(runIds);
                
                // Batch delete all node runs
                if (!allNodeRuns.isEmpty()) {
                    nodeRunRepository.deleteAll(allNodeRuns);
                }
                
                // Batch delete all workflow runs
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
