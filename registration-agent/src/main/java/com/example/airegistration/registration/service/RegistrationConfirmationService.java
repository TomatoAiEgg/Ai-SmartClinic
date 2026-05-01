package com.example.airegistration.registration.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface RegistrationConfirmationService {

    Mono<String> save(ChatRequest request, RegistrationIntent intent, Map<String, Object> data);

    Mono<RegistrationConfirmationContext> consume(ChatRequest request, RegistrationIntent intent);
}
