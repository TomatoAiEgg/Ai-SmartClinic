package com.example.airegistration.domain;

import java.util.Map;

public record ChatResponse(String chatId, AgentRoute route, String message, boolean requiresConfirmation, Map<String, Object> data) {
    public ChatResponse {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
