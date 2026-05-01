package com.example.airegistration.supervisor.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import reactor.core.publisher.Mono;

public interface SupervisorRoutingUseCase {

    Mono<ChatResponse> route(ChatRequest request);
}
