package com.ankush.workflowEngine.execution;

import com.ankush.workflowEngine.domain.WorkflowEdge;
import com.ankush.workflowEngine.domain.WorkflowNode;
import com.ankush.workflowEngine.domain.WorkflowNodeRun;
import com.ankush.workflowEngine.domain.WorkflowRun;
import com.ankush.workflowEngine.enums.NodeRunStatus;
import com.ankush.workflowEngine.enums.NodeType;
import com.ankush.workflowEngine.mapper.WorkflowMapper;
import com.ankush.workflowEngine.registry.NodeExecutor;
import com.ankush.workflowEngine.registry.NodeRegistry;
import com.ankush.workflowEngine.repository.WorkflowEdgeRepository;
import com.ankush.workflowEngine.repository.WorkflowNodeRepository;
import com.ankush.workflowEngine.repository.WorkflowNodeRunRepository;
import com.ankush.workflowEngine.repository.WorkflowRunRepository;
import com.ankush.workflowEngine.support.ErrorMessageFormatter;
import com.ankush.workflowEngine.support.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class WorkflowExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExecutor.class);

    private final WorkflowRunRepository runRepository;
    private final WorkflowNodeRepository nodeRepository;
    private final WorkflowEdgeRepository edgeRepository;
    private final WorkflowNodeRunRepository nodeRunRepository;
    private final NodeRegistry nodeRegistry;
    private final WorkflowMapper mapper;

    public WorkflowExecutor(
            WorkflowRunRepository runRepository,
            WorkflowNodeRepository nodeRepository,
            WorkflowEdgeRepository edgeRepository,
            WorkflowNodeRunRepository nodeRunRepository,
            NodeRegistry nodeRegistry,
            WorkflowMapper mapper) {
        this.runRepository = runRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.nodeRunRepository = nodeRunRepository;
        this.nodeRegistry = nodeRegistry;
        this.mapper = mapper;
    }

    @Async("workflowAsyncExecutor")
    public void enqueue(Long runId) {
        execute(runId);
    }

    /**
     * Listens for workflow run created events after transaction commits
     * This ensures the run is visible in the database before execution starts
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("workflowAsyncExecutor")
    public void handleWorkflowRunCreated(WorkflowRunCreatedEvent event) {
        execute(event.getRunId());
    }

    /**
     * Executes a workflow run. This method runs in its own transaction
     * since it's called asynchronously after the creating transaction commits.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void execute(Long runId) {
        Long safeId = Objects.requireNonNull(runId, ErrorMessageFormatter.workflowRunIdRequired());
        
        // Set MDC for logging context
        MDC.put("runId", runId.toString());
        
        try {
        WorkflowRun run = runRepository.findById(safeId)
                    .orElseThrow(() -> new IllegalArgumentException(ErrorMessageFormatter.workflowRunNotFound(runId)));
            
            MDC.put("workflowId", run.getWorkflowDefinition().getId().toString());
            
        run.markRunning();
        runRepository.save(run);

        WorkflowContext context = WorkflowContext.fromMap(mapper.readJson(run.getContextData()));
            
            // Build graph structure
            List<WorkflowNode> allNodes = nodeRepository.findByWorkflowDefinitionIdOrderBySortOrderAsc(
                    run.getWorkflowDefinition().getId());
            List<WorkflowEdge> allEdges = edgeRepository.findByWorkflowDefinitionId(
                run.getWorkflowDefinition().getId());

            Map<String, WorkflowNode> nodeMap = allNodes.stream()
                    .collect(Collectors.toMap(WorkflowNode::getNodeKey, n -> n));
            
            // Build adjacency list for graph traversal
            Map<String, List<WorkflowEdge>> adjacencyList = buildAdjacencyList(allEdges);
            
            // Find entry nodes (nodes with no incoming edges)
            Set<String> nodesWithIncomingEdges = allEdges.stream()
                    .map(WorkflowEdge::getTargetKey)
                    .collect(Collectors.toSet());
            
            List<String> entryNodes = allNodes.stream()
                    .map(WorkflowNode::getNodeKey)
                    .filter(key -> !nodesWithIncomingEdges.contains(key))
                    .collect(Collectors.toList());
            
            if (entryNodes.isEmpty()) {
                // Fallback to sequential execution if no edges defined
                executeSequentially(run, context, allNodes);
            } else {
                // Execute using graph traversal
                executeGraph(run, context, nodeMap, adjacencyList, entryNodes);
            }

            run.setContextData(mapper.writeJson(context.snapshot()));
            run.markCompleted();
            runRepository.save(run);
            
            LOGGER.info("Workflow run {} completed successfully", runId);
            
        } catch (Exception ex) {
            LOGGER.error("Workflow execution failed for run {}", runId, ex);
            handleWorkflowFailure(runId, ex);
        } finally {
            MDC.clear();
        }
    }

    private void executeGraph(WorkflowRun run, WorkflowContext context,
                             Map<String, WorkflowNode> nodeMap,
                             Map<String, List<WorkflowEdge>> adjacencyList,
                             List<String> entryNodes) {
        Set<String> executedNodes = new HashSet<>();
        Queue<String> queue = new LinkedList<>(entryNodes);

        while (!queue.isEmpty()) {
            String currentNodeKey = queue.poll();
            
            if (executedNodes.contains(currentNodeKey)) {
                continue; // Skip already executed nodes
            }

            WorkflowNode node = nodeMap.get(currentNodeKey);
            if (node == null) {
                LOGGER.warn("Node {} not found in node map", currentNodeKey);
                continue;
            }

            // Execute the node
            NodeExecutionError error = executeNode(run, node, context);
            executedNodes.add(currentNodeKey);

            if (error != null) {
                // Node execution failed, stop workflow
                return;
            }

            // Determine next nodes based on edges and node type
            List<String> nextNodes = determineNextNodes(node, context, adjacencyList);
            
            // Add next nodes to queue
            for (String nextNodeKey : nextNodes) {
                if (!executedNodes.contains(nextNodeKey)) {
                    queue.offer(nextNodeKey);
                }
            }
        }
    }

    private List<String> determineNextNodes(WorkflowNode currentNode, WorkflowContext context,
                                           Map<String, List<WorkflowEdge>> adjacencyList) {
        List<WorkflowEdge> outgoingEdges = adjacencyList.getOrDefault(
                currentNode.getNodeKey(), Collections.emptyList());

        if (outgoingEdges.isEmpty()) {
            return Collections.emptyList();
        }

        // If this is an IF/ELSE node, check the result
        if (currentNode.getType() == NodeType.IF_ELSE) {
            return determineIfElseNextNodes(currentNode, context, outgoingEdges);
        }

        // For other nodes, evaluate edge conditions
        List<String> nextNodes = new ArrayList<>();
        for (WorkflowEdge edge : outgoingEdges) {
            if (shouldFollowEdge(edge, context)) {
                nextNodes.add(edge.getTargetKey());
            }
        }

        return nextNodes;
    }

    private List<String> determineIfElseNextNodes(WorkflowNode ifElseNode, WorkflowContext context,
                                                  List<WorkflowEdge> outgoingEdges) {
        // Get the result from IF/ELSE node execution
        String resultKey = ifElseNode.getNodeKey() + "::result";
        Object resultValue = context.snapshot().get(resultKey);
        
        boolean conditionResult = false;
        if (resultValue instanceof Boolean) {
            conditionResult = (Boolean) resultValue;
        } else if (resultValue != null) {
            conditionResult = Boolean.parseBoolean(resultValue.toString());
        }

        List<String> nextNodes = new ArrayList<>();
        
        // Look for edges with condition "true" or "false"
        for (WorkflowEdge edge : outgoingEdges) {
            String condition = edge.getConditionExpression();
            if (condition != null && !condition.isBlank()) {
                // Evaluate edge condition
                String renderedCondition = TemplateRenderer.render(condition, context.snapshot());
                boolean edgeCondition = Boolean.parseBoolean(renderedCondition);
                
                if (edgeCondition == conditionResult) {
                    nextNodes.add(edge.getTargetKey());
                }
            } else {
                // No condition means always follow (default path)
                nextNodes.add(edge.getTargetKey());
            }
        }

        // If no matching edges, follow the first edge without condition
        if (nextNodes.isEmpty() && !outgoingEdges.isEmpty()) {
            WorkflowEdge defaultEdge = outgoingEdges.stream()
                    .filter(e -> e.getConditionExpression() == null || e.getConditionExpression().isBlank())
                    .findFirst()
                    .orElse(outgoingEdges.get(0));
            nextNodes.add(defaultEdge.getTargetKey());
        }

        return nextNodes;
    }

    private boolean shouldFollowEdge(WorkflowEdge edge, WorkflowContext context) {
        String condition = edge.getConditionExpression();
        if (condition == null || condition.isBlank()) {
            return true; // No condition means always follow
        }

        try {
            String renderedCondition = TemplateRenderer.render(condition, context.snapshot());
            return Boolean.parseBoolean(renderedCondition);
        } catch (Exception ex) {
            LOGGER.warn("Failed to evaluate edge condition: {}", condition, ex);
            return false;
        }
    }

    private NodeExecutionError executeNode(WorkflowRun run, WorkflowNode node, WorkflowContext context) {
        // Add node context to MDC
        String previousNodeKey = MDC.get("nodeKey");
        MDC.put("nodeKey", node.getNodeKey());
        MDC.put("nodeType", node.getType().name());
        
        try {
            WorkflowNodeRun nodeRun = new WorkflowNodeRun();
            nodeRun.setWorkflowRun(run);
            nodeRun.setNodeKey(node.getNodeKey());
            nodeRun.setStatus(NodeRunStatus.PENDING);
            nodeRun.setInputPayload(mapper.writeJson(context.snapshot()));
            nodeRun = nodeRunRepository.save(nodeRun);

            try {
                nodeRun.markRunning();
                nodeRunRepository.save(nodeRun);

                NodeExecutor executor = nodeRegistry.getExecutor(node.getType());
                NodeExecutionResult result = executor.execute(new NodeExecutionContext(
                        run,
                        node,
                        context,
                        mapper.readJson(node.getConfig())));
                
                context.merge(result.output());
                nodeRun.markSuccess(mapper.writeJson(result.output()));
                nodeRunRepository.save(nodeRun);
                
                LOGGER.debug("Node {} executed successfully", node.getNodeKey());
                return null; // Success
                
            } catch (NodeExecutionException ex) {
                NodeExecutionError error = NodeExecutionError.fromException(ex, node.getNodeKey());
                LOGGER.error("Node {} execution failed: {}", node.getNodeKey(), error.getMessage(), ex);
                
                nodeRun.markFailed(error.getMessage());
                nodeRunRepository.save(nodeRun);
                
                run.setContextData(mapper.writeJson(context.snapshot()));
                run.markFailed(error.getMessage());
                runRepository.save(run);
                
                return error;
                
            } catch (Exception ex) {
                NodeExecutionError error = NodeExecutionError.fromException(ex, node.getNodeKey());
                LOGGER.error("Unexpected error executing node {}: {}", node.getNodeKey(), error.getMessage(), ex);
                
                nodeRun.markFailed(error.getMessage());
                nodeRunRepository.save(nodeRun);
                
                run.setContextData(mapper.writeJson(context.snapshot()));
                run.markFailed(error.getMessage());
                runRepository.save(run);
                
                return error;
            }
        } finally {
            // Restore previous node key or remove if it was null
            if (previousNodeKey != null) {
                MDC.put("nodeKey", previousNodeKey);
            } else {
                MDC.remove("nodeKey");
            }
            MDC.remove("nodeType");
        }
    }

    private void executeSequentially(WorkflowRun run, WorkflowContext context, List<WorkflowNode> nodes) {
        for (WorkflowNode node : nodes) {
            NodeExecutionError error = executeNode(run, node, context);
            if (error != null) {
                return; // Stop on error
            }
        }
    }

    private Map<String, List<WorkflowEdge>> buildAdjacencyList(List<WorkflowEdge> edges) {
        Map<String, List<WorkflowEdge>> adjacencyList = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            adjacencyList.computeIfAbsent(edge.getSourceKey(), k -> new ArrayList<>()).add(edge);
        }
        return adjacencyList;
    }

    private void handleWorkflowFailure(Long runId, Exception ex) {
        try {
            Optional<WorkflowRun> runOpt = runRepository.findById(runId);
            if (runOpt.isPresent()) {
                WorkflowRun run = runOpt.get();
                String errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                run.markFailed(errorMessage);
        runRepository.save(run);
            } else {
                LOGGER.warn("Workflow run {} not found when trying to save failure state", runId);
            }
        } catch (Exception saveEx) {
            LOGGER.error("Failed to save workflow failure state for run {}", runId, saveEx);
        }
    }
}
