package com.example.airegistration.knowledge.dto;

import java.util.Map;

public record KnowledgeChunkPayload(
        Integer chunkIndex,
        String chunkType,
        String title,
        String content,
        Integer tokenCount,
        Map<String, Object> metadata
) {
    public KnowledgeChunkPayload {
        metadata = copyMap(metadata);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }
}
