package com.example.airegistration.gateway.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeRetrievalLogView(
        UUID id,
        String traceId,
        String chatId,
        String namespace,
        String corpusName,
        String queryText,
        int topK,
        double minScore,
        String status,
        int hitCount,
        String bestHitId,
        Double bestScore,
        long latencyMs,
        String errorMessage,
        List<String> hitIds,
        Instant createdAt
) {
    public KnowledgeRetrievalLogView {
        hitIds = hitIds == null ? List.of() : List.copyOf(hitIds);
    }
}
