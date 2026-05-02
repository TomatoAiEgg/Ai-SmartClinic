package com.example.airegistration.knowledge.dto;

import java.util.List;
import java.util.Map;

public record KnowledgeDocumentPayload(
        String namespace,
        String sourceId,
        String sourceName,
        String documentType,
        String title,
        String version,
        String rawContent,
        Map<String, Object> metadata,
        List<KnowledgeChunkPayload> chunks,
        String status
) {
    public KnowledgeDocumentPayload {
        metadata = copyMap(metadata);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        status = status == null ? "" : status.trim();
    }

    public KnowledgeDocumentPayload(String namespace,
                                    String sourceId,
                                    String sourceName,
                                    String documentType,
                                    String title,
                                    String version,
                                    String rawContent,
                                    Map<String, Object> metadata,
                                    List<KnowledgeChunkPayload> chunks) {
        this(namespace, sourceId, sourceName, documentType, title, version, rawContent, metadata, chunks, null);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }
}
