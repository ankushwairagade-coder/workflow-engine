package com.ankush.workflowEngine.controller;

import com.ankush.workflowEngine.dto.VisualEditorSuggestionRequest;
import com.ankush.workflowEngine.dto.VisualEditorSuggestionResponse;
import com.ankush.workflowEngine.dto.WorkflowAnalysisRequest;
import com.ankush.workflowEngine.dto.WorkflowAnalysisResponse;
import com.ankush.workflowEngine.dto.WorkflowPromptRequest;
import com.ankush.workflowEngine.dto.WorkflowPromptResponse;
import com.ankush.workflowEngine.service.VisualEditorAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/visual-editor/assistant")
public class VisualEditorAssistantController {

    private final VisualEditorAssistantService assistantService;

    public VisualEditorAssistantController(VisualEditorAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/suggest")
    public VisualEditorSuggestionResponse suggest(@Valid @RequestBody VisualEditorSuggestionRequest request) {
        return assistantService.getSuggestions(request);
    }

    @PostMapping(value = "/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suggestStream(@Valid @RequestBody VisualEditorSuggestionRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        assistantService.getSuggestionsStream(request, emitter);
        return emitter;
    }

    @PostMapping("/analyze")
    public WorkflowAnalysisResponse analyze(@Valid @RequestBody WorkflowAnalysisRequest request) {
        return assistantService.analyzeWorkflow(request);
    }

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@Valid @RequestBody WorkflowAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        assistantService.analyzeWorkflowStream(request, emitter);
        return emitter;
    }

    @PostMapping("/prompt")
    public WorkflowPromptResponse processPrompt(@Valid @RequestBody WorkflowPromptRequest request) {
        return assistantService.processWorkflowPrompt(request);
    }

    @PostMapping(value = "/prompt/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processPromptStream(@Valid @RequestBody WorkflowPromptRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        assistantService.processWorkflowPromptStream(request, emitter);
        return emitter;
    }
}

