package com.example.airegistration.rag.core;

import java.util.List;
import java.util.Map;

public record KnowledgeDocumentInput(
        String namespace,
        String sourceId,
        String sourceName,
        String documentType,
        String title,
        String version,
        String rawContent,
        Map<String, Object> metadata,
        List<KnowledgeChunkInput> chunks
) {
    public KnowledgeDocumentInput {
        namespace = requireText(namespace, "namespace");
        sourceId = requireText(sourceId, "sourceId");
        sourceName = requireText(sourceName, "sourceName");
        documentType = documentType == null || documentType.isBlank() ? "TEXT" : documentType.trim();
        title = requireText(title, "title");
        version = version == null || version.isBlank() ? "v1" : version.trim();
        rawContent = requireText(rawContent, "rawContent");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        chunks = List.copyOf(chunks == null ? List.of() : chunks);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
