package com.example.airegistration.rag.core;

import java.util.Map;

public record RagSearchRequest(
        String traceId,
        String chatId,
        String namespace,
        String query,
        int topK,
        double minScore,
        Map<String, Object> parameters
) {
    public RagSearchRequest {
        namespace = requireText(namespace, "namespace");
        query = query == null ? "" : query.trim();
        topK = Math.max(1, topK);
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
