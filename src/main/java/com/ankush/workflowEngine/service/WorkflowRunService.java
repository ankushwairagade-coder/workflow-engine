package com.ankush.workflowEngine.service;

import com.ankush.workflowEngine.domain.WorkflowDefinition;
import com.ankush.workflowEngine.domain.WorkflowRun;
import com.ankush.workflowEngine.dto.WorkflowRunRequest;
import com.ankush.workflowEngine.dto.WorkflowRunResponse;
import com.ankush.workflowEngine.enums.RunStatus;
import com.ankush.workflowEngine.mapper.WorkflowMapper;
import com.ankush.workflowEngine.repository.WorkflowRunRepository;
import com.ankush.workflowEngine.execution.WorkflowRunCreatedEvent;
import com.ankush.workflowEngine.support.ErrorMessageFormatter;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRunService {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowRunRepository runRepository;
    private final WorkflowMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowRunService(
            WorkflowDefinitionService definitionService,
            WorkflowRunRepository runRepository,
            WorkflowMapper mapper,
            ApplicationEventPublisher eventPublisher) {
        this.definitionService = definitionService;
        this.runRepository = runRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public WorkflowRunResponse startRun(Long workflowId, WorkflowRunRequest request) {
        try {
            MDC.put("workflowId", String.valueOf(workflowId));
            WorkflowDefinition definition = definitionService.fetchEntity(
                    Objects.requireNonNull(workflowId, ErrorMessageFormatter.workflowIdRequiredForRun()));
            WorkflowRun run = new WorkflowRun();
            run.setWorkflowDefinition(definition);
            run.setStatus(RunStatus.PENDING);
            run.setTriggerPayload(mapper.writeJson(request.input()));
            run.setContextData(mapper.writeJson(request.input()));
            run.markPending();
            run = runRepository.saveAndFlush(run);
            MDC.put("runId", String.valueOf(run.getId()));
            
            // Publish event to execute workflow after transaction commits
            eventPublisher.publishEvent(new WorkflowRunCreatedEvent(run.getId()));
            
            return mapper.toResponse(run);
        } finally {
            MDC.remove("workflowId");
            MDC.remove("runId");
        }
    }

    @Transactional(readOnly = true)
    public WorkflowRunResponse getRun(Long runId) {
        try {
            MDC.put("runId", String.valueOf(runId));
            Long safeId = Objects.requireNonNull(runId, ErrorMessageFormatter.workflowRunIdRequired());
            WorkflowRun run = runRepository.findById(safeId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorMessageFormatter.workflowRunNotFound(runId)));
            MDC.put("workflowId", String.valueOf(run.getWorkflowDefinition().getId()));
            return mapper.toResponse(run);
        } finally {
            MDC.remove("runId");
            MDC.remove("workflowId");
        }
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunResponse> listRuns() {
        return runRepository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public void updateContext(Long runId, Map<String, Object> context) {
        try {
            MDC.put("runId", String.valueOf(runId));
            Long safeId = Objects.requireNonNull(runId, ErrorMessageFormatter.workflowRunIdRequired());
            WorkflowRun run = runRepository.findById(safeId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorMessageFormatter.workflowRunNotFound(runId)));
            MDC.put("workflowId", String.valueOf(run.getWorkflowDefinition().getId()));
            run.setContextData(mapper.writeJson(context));
        } finally {
            MDC.remove("runId");
            MDC.remove("workflowId");
        }
    }
}
