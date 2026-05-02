package com.example.airegistration.knowledge.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KnowledgeDocumentView(
        UUID id,
        String namespace,
        String sourceId,
        String sourceName,
        String documentType,
        String title,
        String version,
        String status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public KnowledgeDocumentView {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
