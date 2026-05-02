package com.example.airegistration.rag.core;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
