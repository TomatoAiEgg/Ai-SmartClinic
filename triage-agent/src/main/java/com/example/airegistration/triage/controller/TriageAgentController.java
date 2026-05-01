package com.example.airegistration.triage.controller;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.triage.service.TriageUseCase;
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
public class TriageAgentController {

    private static final Logger log = LoggerFactory.getLogger(TriageAgentController.class);

    private final TriageUseCase triageUseCase;

    public TriageAgentController(TriageUseCase triageUseCase) {
        this.triageUseCase = triageUseCase;
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
}
