package com.ankush.workflowEngine.controller;

import com.ankush.workflowEngine.dto.WorkflowRunRequest;
import com.ankush.workflowEngine.dto.WorkflowRunResponse;
import com.ankush.workflowEngine.service.WorkflowRunService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class WorkflowRunController {

    private final WorkflowRunService runService;

    public WorkflowRunController(WorkflowRunService runService) {
        this.runService = runService;
    }

    @PostMapping("/{workflowId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkflowRunResponse start(@PathVariable Long workflowId, @Valid @RequestBody WorkflowRunRequest request) {
        return runService.startRun(workflowId, request);
    }

    @GetMapping
    public List<WorkflowRunResponse> list() {
        return runService.listRuns();
    }

    @GetMapping("/{runId}")
    public WorkflowRunResponse get(@PathVariable Long runId) {
        return runService.getRun(runId);
    }
}
