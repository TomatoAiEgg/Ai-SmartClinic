package com.example.airegistration.gateway.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import reactor.core.publisher.Mono;

public interface GatewayChatUseCase {

    Mono<ChatResponse> chat(ChatRequest request);
}
