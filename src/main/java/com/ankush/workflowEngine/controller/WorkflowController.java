package com.ankush.workflowEngine.controller;

import com.ankush.workflowEngine.dto.WorkflowDefinitionRequest;
import com.ankush.workflowEngine.dto.WorkflowDefinitionResponse;
import com.ankush.workflowEngine.service.WorkflowDefinitionService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowController.class);

    private final WorkflowDefinitionService definitionService;

    public WorkflowController(WorkflowDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDefinitionResponse create(@Valid @RequestBody WorkflowDefinitionRequest request) {
        LOGGER.debug("Creating workflow: {}", request.name());
        return definitionService.createWorkflow(request);
    }

    @GetMapping
    public List<WorkflowDefinitionResponse> list() {
        LOGGER.debug("Listing all workflows");
        return definitionService.listDefinitions();
    }

    @GetMapping("/{id}")
    public WorkflowDefinitionResponse get(@PathVariable Long id) {
        LOGGER.debug("Getting workflow: {}", id);
        return definitionService.getDefinition(id);
    }

    @PutMapping("/{id}")
    public WorkflowDefinitionResponse update(@PathVariable Long id, @Valid @RequestBody WorkflowDefinitionRequest request) {
        LOGGER.debug("Updating workflow: {}", id);
        return definitionService.updateWorkflow(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        LOGGER.debug("Deleting workflow: {}", id);
        definitionService.deleteWorkflow(id);
    }
}
