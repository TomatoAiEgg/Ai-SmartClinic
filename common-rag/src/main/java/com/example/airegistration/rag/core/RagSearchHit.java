package com.example.airegistration.rag.core;

import java.util.Map;

public record RagSearchHit(
        String id,
        String title,
        String content,
        String metadataJson,
        double score,
        Map<String, Object> attributes
) {
    public RagSearchHit {
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
