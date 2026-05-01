package com.example.airegistration.dto;

import java.util.Map;

public record ChatRequest(String chatId,
                          String userId,
                          String message,
                          Map<String, String> metadata,
                          String traceId) {
    public ChatRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        traceId = traceId == null ? "" : traceId.trim();
    }

    public ChatRequest(String chatId, String userId, String message, Map<String, String> metadata) {
        this(chatId, userId, message, metadata, "");
    }

    public ChatRequest withUserId(String userId) {
        return new ChatRequest(chatId, userId, message, metadata, traceId);
    }

    public ChatRequest withTraceId(String traceId) {
        return new ChatRequest(chatId, userId, message, metadata, traceId);
    }
}
