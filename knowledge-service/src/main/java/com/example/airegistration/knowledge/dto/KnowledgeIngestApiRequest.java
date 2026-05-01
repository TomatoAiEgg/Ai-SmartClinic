package com.example.airegistration.knowledge.dto;

import java.util.List;
import java.util.Map;

public record KnowledgeIngestApiRequest(
        String namespace,
        String sourceId,
        String sourceName,
        String embeddingModel,
        Integer embeddingDimensions,
        Integer chunkMaxChars,
        Integer chunkOverlapChars,
        List<KnowledgeDocumentPayload> documents,
        Map<String, Object> metadata
) {
    public KnowledgeIngestApiRequest {
        documents = documents == null ? List.of() : List.copyOf(documents);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
