package com.example.airegistration.registration.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import com.example.airegistration.registration.service.prompt.RegistrationPromptService;
import com.example.airegistration.registration.service.rag.RegistrationRuleService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class RegistrationReplyService {

    private final AiChatClient aiChatClient;
    private final RegistrationPromptService registrationPromptService;
    private final RegistrationRuleService registrationRuleService;

    public RegistrationReplyService(AiChatClient aiChatClient,
                                    RegistrationPromptService registrationPromptService,
                                    RegistrationRuleService registrationRuleService) {
        this.aiChatClient = aiChatClient;
        this.registrationPromptService = registrationPromptService;
        this.registrationRuleService = registrationRuleService;
    }

    public Mono<ChatResponse> reply(ChatRequest request,
                                    RegistrationReplyScene scene,
                                    boolean requiresConfirmation,
                                    Map<String, Object> data) {
        Map<String, Object> ruleContext = registrationRuleService.buildContext(request, scene, requiresConfirmation, data);
        return Mono.fromCallable(() -> aiChatClient.callText(AiChatRequest.builder(scene.task())
                        .systemPrompt(registrationPromptService.systemPrompt())
                        .userPrompt(registrationPromptService.userPrompt(
                                request,
                                scene,
                                requiresConfirmation,
                                data,
                                ruleContext
                        ))
                        .attributes(Map.of(
                                "agent", AgentRoute.REGISTRATION.name(),
                                "scene", scene.name(),
                                "requiresConfirmation", requiresConfirmation,
                                "ruleIds", String.valueOf(ruleContext.get("ruleIds")),
                                "policyIds", String.valueOf(ruleContext.get("policyIds"))
                        ))
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(message -> new ChatResponse(
                        request.chatId(),
                        AgentRoute.REGISTRATION,
                        message,
                        requiresConfirmation,
                        data
                ));
    }
}
