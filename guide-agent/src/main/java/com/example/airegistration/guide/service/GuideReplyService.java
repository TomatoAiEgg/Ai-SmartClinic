package com.example.airegistration.guide.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.guide.enums.GuideReplyScene;
import com.example.airegistration.guide.service.prompt.GuidePromptService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class GuideReplyService {

    private final AiChatClient aiChatClient;
    private final GuidePromptService guidePromptService;

    public GuideReplyService(AiChatClient aiChatClient, GuidePromptService guidePromptService) {
        this.aiChatClient = aiChatClient;
        this.guidePromptService = guidePromptService;
    }

    public Mono<ChatResponse> reply(ChatRequest request,
                                    GuideReplyScene scene,
                                    Map<String, Object> data) {
        return Mono.fromCallable(() -> aiChatClient.callText(AiChatRequest.builder(scene.task())
                        .systemPrompt(guidePromptService.systemPrompt())
                        .userPrompt(guidePromptService.userPrompt(request, scene, data))
                        .attributes(Map.of("agent", AgentRoute.GUIDE.name(), "scene", scene.name()))
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(message -> new ChatResponse(request.chatId(), AgentRoute.GUIDE, message, false, data));
    }
}
