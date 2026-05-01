package com.example.airegistration.registration.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import reactor.core.publisher.Mono;

public interface RegistrationAgentUseCase {

    Mono<ChatResponse> handle(ChatRequest request);
}
