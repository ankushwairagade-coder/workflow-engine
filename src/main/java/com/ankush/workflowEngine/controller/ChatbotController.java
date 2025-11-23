package com.ankush.workflowEngine.controller;

import com.ankush.workflowEngine.dto.ChatbotRequest;
import com.ankush.workflowEngine.dto.ChatbotResponse;
import com.ankush.workflowEngine.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/chat")
    public ChatbotResponse chat(@Valid @RequestBody ChatbotRequest request) {
        return chatbotService.processMessage(request.message());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatbotRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        
        chatbotService.processMessageStream(request.message(), emitter);
        
        return emitter;
    }
}

