package com.example.airegistration.triage.controller;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.agent.AgentEnvelopeMapper;
import com.example.airegistration.agent.AgentPattern;
import com.example.airegistration.agent.AgentRequestEnvelope;
import com.example.airegistration.agent.AgentResponseEnvelope;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.triage.service.TriageUseCase;
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
public class TriageAgentController {

    private static final Logger log = LoggerFactory.getLogger(TriageAgentController.class);

    private final TriageUseCase triageUseCase;

    public TriageAgentController(TriageUseCase triageUseCase) {
        this.triageUseCase = triageUseCase;
    }

    @GetMapping("/agent/capabilities")
    public AgentCapability capabilities() {
        return new AgentCapability(
                "triage-agent",
                "Symptom triage, emergency red flag detection and department suggestion.",
                List.of(AgentRoute.TRIAGE.name()),
                List.of(AgentRequestEnvelope.class.getSimpleName(), ChatRequest.class.getSimpleName()),
                List.of(AgentResponseEnvelope.class.getSimpleName(), ChatResponse.class.getSimpleName()),
                List.of(AgentPattern.ROUTING_AGENT, AgentPattern.RAG_AGENT),
                Map.of(
                        "executeEndpoint", "/api/agent/execute",
                        "legacyEndpoint", "/api/triage",
                        "knowledgeNamespace", "default-triage-knowledge"
                )
        );
    }

    @PostMapping("/agent/execute")
    public Mono<AgentResponseEnvelope> execute(@RequestBody AgentRequestEnvelope request,
                                               @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        long startedNanos = System.nanoTime();
        ChatRequest tracedRequest = tracedRequest(AgentEnvelopeMapper.toChatRequest(request), traceId);
        log.info("[triage-agent] execute inbound trace_id={} chat_id={} user_id={} metadata_keys={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                tracedRequest.metadata().keySet(),
                TraceIdSupport.preview(tracedRequest.message()));
        return triageUseCase.handle(tracedRequest)
                .map(response -> AgentEnvelopeMapper.toAgentResponse(response, "triage-agent", elapsedMs(startedNanos)))
                .doOnSuccess(response -> log.info(
                        "[triage-agent] execute outbound trace_id={} chat_id={} route={} latency_ms={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        response.route(),
                        response.executionMeta().latencyMs()
                ))
                .doOnError(error -> log.warn(
                        "[triage-agent] execute failed trace_id={} chat_id={} reason={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        error.getMessage()
                ));
    }

    @PostMapping("/triage")
    public Mono<ChatResponse> handle(@RequestBody ChatRequest request,
                                     @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        ChatRequest tracedRequest = TraceIdSupport.ensureTraceId(request.withTraceId(traceId));
        log.info("[triage] inbound trace_id={} chat_id={} user_id={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                TraceIdSupport.preview(tracedRequest.message()));
        return triageUseCase.handle(tracedRequest)
                .doOnSuccess(response -> log.info(
                        "[triage] outbound trace_id={} chat_id={} route={} confirmation={}",
                        tracedRequest.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[triage] failed trace_id={} chat_id={} reason={}",
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
