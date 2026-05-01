package com.example.airegistration.guide.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.guide.service.orchestrator.GuideOrchestratorService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GuideApplicationService implements GuideUseCase {

    private final GuideOrchestratorService guideOrchestratorService;

    public GuideApplicationService(GuideOrchestratorService guideOrchestratorService) {
        this.guideOrchestratorService = guideOrchestratorService;
    }

    @Override
    public Mono<ChatResponse> handle(ChatRequest request) {
        return guideOrchestratorService.handle(request);
    }
}
