package com.example.airegistration.gateway.controller;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.gateway.service.GatewayChatUseCase;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import com.example.airegistration.support.TraceIdSupport;

@RestController
@RequestMapping("/api")
public class GatewayChatController {

    private static final Logger log = LoggerFactory.getLogger(GatewayChatController.class);

    private final GatewayChatUseCase gatewayChatUseCase;

    public GatewayChatController(GatewayChatUseCase gatewayChatUseCase) {
        this.gatewayChatUseCase = gatewayChatUseCase;
    }

    @PostMapping("/chat")
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request, Principal principal) {
        ChatRequest tracedRequest = TraceIdSupport.ensureTraceId(request);
        String userId = principal == null ? tracedRequest.userId() : principal.getName();
        ChatRequest normalizedRequest = tracedRequest.withUserId(userId);
        log.info("[gateway] inbound chat trace_id={} chat_id={} user_id={} metadata_keys={} preview={}",
                normalizedRequest.traceId(),
                normalizedRequest.chatId(),
                normalizedRequest.userId(),
                normalizedRequest.metadata().keySet(),
                TraceIdSupport.preview(normalizedRequest.message()));
        return gatewayChatUseCase.chat(normalizedRequest)
                .doOnSuccess(response -> log.info(
                        "[gateway] outbound chat trace_id={} chat_id={} route={} confirmation={} data_keys={}",
                        normalizedRequest.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation(),
                        response.data().keySet()
                ))
                .doOnError(error -> log.warn(
                        "[gateway] chat failed trace_id={} chat_id={} reason={}",
                        normalizedRequest.traceId(),
                        normalizedRequest.chatId(),
                        error.getMessage()
                ));
    }
}
