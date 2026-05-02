package com.example.airegistration.knowledge.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KnowledgeChunkView(
        UUID id,
        UUID documentId,
        String namespace,
        int chunkIndex,
        String chunkType,
        String title,
        String content,
        Boolean enabled,
        String embeddingModel,
        Integer embeddingDimensions,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public KnowledgeChunkView {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
