package com.example.airegistration.triage.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.triage.enums.TriageReplyScene;
import com.example.airegistration.triage.service.prompt.TriagePromptService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class TriageReplyService {

    private final AiChatClient aiChatClient;
    private final TriagePromptService triagePromptService;

    public TriageReplyService(AiChatClient aiChatClient,
                              TriagePromptService triagePromptService) {
        this.aiChatClient = aiChatClient;
        this.triagePromptService = triagePromptService;
    }

    public Mono<ChatResponse> reply(ChatRequest request,
                                    TriageReplyScene scene,
                                    Map<String, Object> data) {
        return Mono.fromCallable(() -> aiChatClient.callText(AiChatRequest.builder(scene.task())
                        .systemPrompt(triagePromptService.systemPrompt())
                        .userPrompt(triagePromptService.userPrompt(request, scene, data))
                        .attributes(Map.of("agent", AgentRoute.TRIAGE.name(), "scene", scene.name()))
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(message -> new ChatResponse(request.chatId(), AgentRoute.TRIAGE, message, false, data));
    }
}
