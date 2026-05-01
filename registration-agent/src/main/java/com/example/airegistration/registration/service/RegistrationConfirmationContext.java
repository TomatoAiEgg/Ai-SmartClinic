package com.example.airegistration.registration.service;

import java.util.Map;

public record RegistrationConfirmationContext(
        String confirmationId,
        String action,
        String userId,
        String chatId,
        Map<String, Object> data
) {
    public RegistrationConfirmationContext {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
