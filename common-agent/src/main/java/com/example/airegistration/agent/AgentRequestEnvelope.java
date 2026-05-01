package com.example.airegistration.agent;

import java.util.Map;

public record AgentRequestEnvelope(
        String traceId,
        String chatId,
        String userId,
        String message,
        Map<String, Object> metadata,
        Map<String, Object> sessionContext
) {
    public AgentRequestEnvelope {
        traceId = traceId == null ? "" : traceId.trim();
        chatId = requireText(chatId, "chatId");
        userId = requireText(userId, "userId");
        message = message == null ? "" : message.trim();
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        sessionContext = Map.copyOf(sessionContext == null ? Map.of() : sessionContext);
    }

    public AgentRequestEnvelope withMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata == null || additionalMetadata.isEmpty()) {
            return this;
        }
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(metadata);
        merged.putAll(additionalMetadata);
        return new AgentRequestEnvelope(traceId, chatId, userId, message, merged, sessionContext);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
