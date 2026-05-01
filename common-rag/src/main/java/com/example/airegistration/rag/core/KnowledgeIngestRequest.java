package com.example.airegistration.rag.core;

import java.util.List;
import java.util.Map;

public record KnowledgeIngestRequest(
        String namespace,
        String sourceId,
        String sourceName,
        String embeddingModel,
        int embeddingDimensions,
        List<KnowledgeDocumentInput> documents,
        Map<String, Object> metadata
) {
    public KnowledgeIngestRequest {
        namespace = requireText(namespace, "namespace");
        sourceId = requireText(sourceId, "sourceId");
        sourceName = sourceName == null ? "" : sourceName.trim();
        embeddingModel = requireText(embeddingModel, "embeddingModel");
        if (embeddingDimensions <= 0) {
            throw new IllegalArgumentException("embeddingDimensions must be positive");
        }
        documents = List.copyOf(documents == null ? List.of() : documents);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
