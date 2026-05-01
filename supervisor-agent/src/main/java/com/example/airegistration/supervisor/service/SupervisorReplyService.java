package com.example.airegistration.supervisor.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.enums.SupervisorReplyScene;
import com.example.airegistration.supervisor.service.prompt.SupervisorPromptService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SupervisorReplyService {

    private final AiChatClient aiChatClient;
    private final SupervisorPromptService supervisorPromptService;

    public SupervisorReplyService(AiChatClient aiChatClient,
                                  SupervisorPromptService supervisorPromptService) {
        this.aiChatClient = aiChatClient;
        this.supervisorPromptService = supervisorPromptService;
    }

    public Mono<ChatResponse> reply(ChatRequest request,
                                    AgentRoute route,
                                    SupervisorReplyScene scene,
                                    Map<String, Object> data) {
        return Mono.fromCallable(() -> aiChatClient.callText(AiChatRequest.builder(scene.task())
                        .systemPrompt(supervisorPromptService.systemPrompt())
                        .userPrompt(supervisorPromptService.userPrompt(request, route, scene, data))
                        .attributes(Map.of("agent", "SUPERVISOR", "route", route.name(), "scene", scene.name()))
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(message -> new ChatResponse(request.chatId(), route, message, false, data));
    }
}
