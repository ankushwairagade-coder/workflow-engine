package com.ankush.workflowEngine.mapper;

import com.ankush.workflowEngine.domain.WorkflowDefinition;
import com.ankush.workflowEngine.domain.WorkflowEdge;
import com.ankush.workflowEngine.domain.WorkflowNode;
import com.ankush.workflowEngine.domain.WorkflowRun;
import com.ankush.workflowEngine.dto.WorkflowDefinitionResponse;
import com.ankush.workflowEngine.dto.WorkflowEdgeResponse;
import com.ankush.workflowEngine.dto.WorkflowNodeResponse;
import com.ankush.workflowEngine.dto.WorkflowRunResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public WorkflowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorkflowDefinitionResponse toResponse(WorkflowDefinition definition) {
        List<WorkflowNodeResponse> nodes = definition.getNodes().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(this::toNodeResponse)
                .collect(Collectors.toList());
        List<WorkflowEdgeResponse> edges = definition.getEdges().stream()
                .map(this::toEdgeResponse)
                .collect(Collectors.toList());
        return new WorkflowDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getStatus(),
                definition.getVersion(),
                readJson(definition.getMetadata()),
                definition.getCreatedAt(),
                definition.getUpdatedAt(),
                nodes,
                edges);
    }

    public WorkflowRunResponse toResponse(WorkflowRun run) {
        return new WorkflowRunResponse(
                run.getId(),
                run.getWorkflowDefinition().getId(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getCompletedAt(),
                readJson(run.getContextData()),
                run.getLastError());
    }

    public WorkflowNodeResponse toNodeResponse(WorkflowNode node) {
        return new WorkflowNodeResponse(
                node.getId(),
                node.getNodeKey(),
                node.getDisplayName(),
                node.getType(),
                node.getSortOrder(),
                readJson(node.getConfig()),
                readJson(node.getMetadata()));
    }

    public WorkflowEdgeResponse toEdgeResponse(WorkflowEdge edge) {
        return new WorkflowEdgeResponse(
                edge.getId(),
                edge.getSourceKey(),
                edge.getTargetKey(),
                edge.getConditionExpression(),
                readJson(edge.getMetadata()));
    }

    public String writeJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        return toJson(data);
    }

    public Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        return fromJson(json);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write JSON", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse JSON", ex);
        }
    }
}
