package com.example.airegistration.triage.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import reactor.core.publisher.Mono;

public interface TriageUseCase {

    Mono<ChatResponse> handle(ChatRequest request);
}
