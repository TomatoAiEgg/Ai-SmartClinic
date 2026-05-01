package com.example.airegistration.supervisor.controller;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.supervisor.service.SupervisorRoutingUseCase;
import com.example.airegistration.support.TraceIdSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class SupervisorAgentController {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgentController.class);

    private final SupervisorRoutingUseCase supervisorRoutingUseCase;

    public SupervisorAgentController(SupervisorRoutingUseCase supervisorRoutingUseCase) {
        this.supervisorRoutingUseCase = supervisorRoutingUseCase;
    }

    @PostMapping("/route")
    public Mono<ChatResponse> route(@RequestBody ChatRequest request,
                                    @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        ChatRequest tracedRequest = TraceIdSupport.ensureTraceId(request.withTraceId(traceId));
        log.info("[supervisor] inbound route trace_id={} chat_id={} user_id={} metadata_keys={} preview={}",
                tracedRequest.traceId(),
                tracedRequest.chatId(),
                tracedRequest.userId(),
                tracedRequest.metadata().keySet(),
                TraceIdSupport.preview(tracedRequest.message()));
        return supervisorRoutingUseCase.route(tracedRequest)
                .doOnSuccess(response -> log.info(
                        "[supervisor] outbound route trace_id={} chat_id={} route={} confirmation={}",
                        tracedRequest.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[supervisor] route failed trace_id={} chat_id={} reason={}",
                        tracedRequest.traceId(),
                        tracedRequest.chatId(),
                        error.getMessage()
                ));
    }
}
