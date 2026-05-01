package com.example.airegistration.guide.controller;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.guide.service.GuideUseCase;
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
public class GuideAgentController {

    private static final Logger log = LoggerFactory.getLogger(GuideAgentController.class);

    private final GuideUseCase guideUseCase;

    public GuideAgentController(GuideUseCase guideUseCase) {
        this.guideUseCase = guideUseCase;
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
}
