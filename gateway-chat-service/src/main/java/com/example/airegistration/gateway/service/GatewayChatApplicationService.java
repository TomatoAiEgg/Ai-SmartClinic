package com.example.airegistration.gateway.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.gateway.client.SupervisorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GatewayChatApplicationService implements GatewayChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(GatewayChatApplicationService.class);

    private final SupervisorClient supervisorClient;

    public GatewayChatApplicationService(SupervisorClient supervisorClient) {
        this.supervisorClient = supervisorClient;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        log.info("[gateway] forwarding to supervisor trace_id={} chat_id={} user_id={}",
                request.traceId(),
                request.chatId(),
                request.userId());
        return supervisorClient.route(request)
                .doOnSuccess(response -> log.info(
                        "[gateway] supervisor returned trace_id={} chat_id={} route={} confirmation={}",
                        request.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[gateway] supervisor call failed trace_id={} chat_id={} reason={}",
                        request.traceId(),
                        request.chatId(),
                        error.getMessage()
                ));
    }
}
