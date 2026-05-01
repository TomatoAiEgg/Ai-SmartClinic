package com.example.airegistration.rag.core;

import java.util.Map;

public record KnowledgeChunkInput(
        int chunkIndex,
        String chunkType,
        String title,
        String content,
        Integer tokenCount,
        Map<String, Object> metadata
) {
    public KnowledgeChunkInput {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        chunkType = chunkType == null || chunkType.isBlank() ? "TEXT" : chunkType.trim();
        title = title == null ? "" : title.trim();
        content = requireText(content, "content");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
