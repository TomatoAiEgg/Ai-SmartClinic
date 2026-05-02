package com.example.airegistration.registration.controller;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.agent.AgentEnvelopeMapper;
import com.example.airegistration.agent.AgentPattern;
import com.example.airegistration.agent.AgentRequestEnvelope;
import com.example.airegistration.agent.AgentResponseEnvelope;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.registration.service.RegistrationAgentUseCase;
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
public class RegistrationAgentController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAgentController.class);

    private final RegistrationAgentUseCase registrationAgentUseCase;

    public RegistrationAgentController(RegistrationAgentUseCase registrationAgentUseCase) {
        this.registrationAgentUseCase = registrationAgentUseCase;
    }

    @GetMapping("/agent/capabilities")
    public AgentCapability capabilities() {
        return new AgentCapability(
                "registration-agent",
                "Registration create/query/cancel/reschedule workflow with confirmation guarded write actions.",
                List.of(AgentRoute.REGISTRATION.name()),
                List.of(AgentRequestEnvelope.class.getSimpleName(), ChatRequest.class.getSimpleName()),
                List.of(AgentResponseEnvelope.class.getSimpleName(), ChatResponse.class.getSimpleName()),
                List.of(AgentPattern.STATE_GRAPH, AgentPattern.REACT_TOOL_AGENT, AgentPattern.HUMAN_IN_THE_LOOP,
                        AgentPattern.RAG_AGENT, AgentPattern.DETERMINISTIC_TOOL),
                Map.of(
                        "executeEndpoint", "/api/agent/execute",
                        "legacyEndpoint", "/api/registration",
                        "policyNamespace", "default-registration-policy"
                )
        );
    }

    @PostMapping("/agent/execute")
    public Mono<AgentResponseEnvelope> execute(@RequestBody AgentRequestEnvelope request,
                                               @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        long startedNanos = System.nanoTime();
        ChatRequest tracedRequest = tracedRequest(AgentEnvelopeMapper.toChatRequest(request), traceId);
        log.info("[registration-agent] execute inbound trace_id={} chat_id={} user_id={} metadata_keys={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                tracedRequest.metadata().keySet(),
                TraceIdSupport.preview(tracedRequest.message()));
        return registrationAgentUseCase.handle(tracedRequest)
                .map(response -> AgentEnvelopeMapper.toAgentResponse(response, "registration-agent", elapsedMs(startedNanos)))
                .doOnSuccess(response -> log.info(
                        "[registration-agent] execute outbound trace_id={} chat_id={} route={} confirmation={} latency_ms={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        response.route(),
                        response.requiresConfirmation(),
                        response.executionMeta().latencyMs()
                ))
                .doOnError(error -> log.warn(
                        "[registration-agent] execute failed trace_id={} chat_id={} reason={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        error.getMessage()
                ));
    }

    @PostMapping("/registration")
    public Mono<ChatResponse> handle(@RequestBody ChatRequest request,
                                     @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        ChatRequest tracedRequest = TraceIdSupport.ensureTraceId(request.withTraceId(traceId));
        log.info("[registration] inbound trace_id={} chat_id={} user_id={} metadata_keys={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                tracedRequest.metadata().keySet(),
                TraceIdSupport.preview(tracedRequest.message()));
        return registrationAgentUseCase.handle(tracedRequest)
                .doOnSuccess(response -> log.info(
                        "[registration] outbound trace_id={} chat_id={} route={} confirmation={} data_keys={}",
                        tracedRequest.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation(),
                        response.data().keySet()
                ))
                .doOnError(error -> log.warn(
                        "[registration] failed trace_id={} chat_id={} reason={}",
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
