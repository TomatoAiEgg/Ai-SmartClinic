package com.example.airegistration.rag.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SimpleTextChunker {

    private SimpleTextChunker() {
    }

    public static List<KnowledgeChunkInput> chunk(String content, int maxChars, int overlapChars) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int chunkSize = Math.max(200, maxChars);
        int overlap = Math.max(0, Math.min(overlapChars, chunkSize / 2));
        String normalized = content.trim().replaceAll("\\s+", " ");
        List<KnowledgeChunkInput> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSize);
            chunks.add(new KnowledgeChunkInput(
                    index++,
                    "TEXT",
                    "",
                    normalized.substring(start, end),
                    null,
                    Map.of("chunker", "simple-text", "maxChars", chunkSize, "overlapChars", overlap)
            ));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
