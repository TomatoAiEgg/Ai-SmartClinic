package com.example.airegistration.guide.service.orchestrator;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.guide.enums.GuideReplyScene;
import com.example.airegistration.guide.service.GuideReplyService;
import com.example.airegistration.guide.service.rag.GuideRagService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GuideOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(GuideOrchestratorService.class);

    private final GuideReplyService guideReplyService;
    private final GuideRagService guideRagService;

    public GuideOrchestratorService(GuideReplyService guideReplyService, GuideRagService guideRagService) {
        this.guideReplyService = guideReplyService;
        this.guideRagService = guideRagService;
    }

    public Mono<ChatResponse> handle(ChatRequest request) {
        Map<String, Object> context = guideRagService.buildContext(request);
        Object matchCount = context.getOrDefault("matchCount", 0);
        Object citations = context.getOrDefault("citations", java.util.List.of());
        String referenceText = String.valueOf(context.getOrDefault("referenceText", ""));
        log.info("[guide] rag context built trace_id={} chat_id={} match_count={} citations={} reference_length={}",
                request.traceId(),
                request.chatId(),
                matchCount,
                citations,
                referenceText.length());
        return guideReplyService.reply(
                request,
                GuideReplyScene.CONSULTATION,
                context
        );
    }
}
