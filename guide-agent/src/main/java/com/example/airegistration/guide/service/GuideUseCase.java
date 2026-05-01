package com.example.airegistration.guide.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import reactor.core.publisher.Mono;

public interface GuideUseCase {

    Mono<ChatResponse> handle(ChatRequest request);
}
