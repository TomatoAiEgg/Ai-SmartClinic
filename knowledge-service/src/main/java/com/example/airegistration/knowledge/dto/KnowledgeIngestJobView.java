package com.example.airegistration.knowledge.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KnowledgeIngestJobView(
        UUID id,
        String namespace,
        String sourceId,
        String sourceName,
        String status,
        String embeddingModel,
        Integer embeddingDimensions,
        int documentCount,
        int chunkCount,
        String errorMessage,
        Map<String, Object> metadata,
        Instant startedAt,
        Instant finishedAt
) {
    public KnowledgeIngestJobView {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
