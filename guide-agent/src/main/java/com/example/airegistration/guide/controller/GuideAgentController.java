package com.example.airegistration.guide.controller;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.agent.AgentEnvelopeMapper;
import com.example.airegistration.agent.AgentPattern;
import com.example.airegistration.agent.AgentRequestEnvelope;
import com.example.airegistration.agent.AgentResponseEnvelope;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.guide.service.GuideUseCase;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class GuideAgentController {

    private static final Logger log = LoggerFactory.getLogger(GuideAgentController.class);

    private final GuideUseCase guideUseCase;

    public GuideAgentController(GuideUseCase guideUseCase) {
        this.guideUseCase = guideUseCase;
    }

    @GetMapping("/agent/capabilities")
    public AgentCapability capabilities() {
        return new AgentCapability(
                "guide-agent",
                "Hospital guide, insurance material and process Q&A grounded by guide knowledge.",
                List.of(AgentRoute.GUIDE.name()),
                List.of(AgentRequestEnvelope.class.getSimpleName(), ChatRequest.class.getSimpleName()),
                List.of(AgentResponseEnvelope.class.getSimpleName(), ChatResponse.class.getSimpleName()),
                List.of(AgentPattern.RAG_AGENT, AgentPattern.ROUTING_AGENT),
                Map.of(
                        "executeEndpoint", "/api/agent/execute",
                        "legacyEndpoint", "/api/guide",
                        "knowledgeNamespace", "default-guide-knowledge"
                )
        );
    }

    @PostMapping("/agent/execute")
    public Mono<AgentResponseEnvelope> execute(@RequestBody AgentRequestEnvelope request,
                                               @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        long startedNanos = System.nanoTime();
        ChatRequest tracedRequest = tracedRequest(AgentEnvelopeMapper.toChatRequest(request), traceId);
        log.info("[guide-agent] execute inbound trace_id={} chat_id={} user_id={} metadata_keys={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                tracedRequest.metadata().keySet(),
                TraceIdSupport.preview(tracedRequest.message()));
        return guideUseCase.handle(tracedRequest)
                .map(response -> AgentEnvelopeMapper.toAgentResponse(response, "guide-agent", elapsedMs(startedNanos)))
                .doOnSuccess(response -> log.info(
                        "[guide-agent] execute outbound trace_id={} chat_id={} route={} latency_ms={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        response.route(),
                        response.executionMeta().latencyMs()
                ))
                .doOnError(error -> log.warn(
                        "[guide-agent] execute failed trace_id={} chat_id={} reason={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        error.getMessage()
                ));
    }

    @PostMapping("/guide")
    public Mono<ChatResponse> handle(@RequestBody ChatRequest request,
                                     @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        ChatRequest tracedRequest = TraceIdSupport.ensureTraceId(request.withTraceId(traceId));
        log.info("[guide] inbound trace_id={} chat_id={} user_id={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                TraceIdSupport.preview(tracedRequest.message()));
        return guideUseCase.handle(tracedRequest)
                .doOnSuccess(response -> log.info(
                        "[guide] outbound trace_id={} chat_id={} route={} confirmation={}",
                        tracedRequest.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[guide] failed trace_id={} chat_id={} reason={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        error.getMessage()
                ));
    }

    private ChatRequest tracedRequest(ChatRequest request, String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            return TraceIdSupport.ensureTraceId(request.withTraceId(traceId));
        }
        return TraceIdSupport.ensureTraceId(request);
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
