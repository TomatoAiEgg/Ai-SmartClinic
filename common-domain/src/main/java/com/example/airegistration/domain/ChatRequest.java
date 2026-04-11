package com.example.airegistration.domain;

import java.util.Map;

public record ChatRequest(String chatId, String userId, String message, Map<String, String> metadata) {
    public ChatRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
