package com.example.airegistration.registration.controller;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.registration.service.RegistrationAgentUseCase;
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
public class RegistrationAgentController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAgentController.class);

    private final RegistrationAgentUseCase registrationAgentUseCase;

    public RegistrationAgentController(RegistrationAgentUseCase registrationAgentUseCase) {
        this.registrationAgentUseCase = registrationAgentUseCase;
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
}
