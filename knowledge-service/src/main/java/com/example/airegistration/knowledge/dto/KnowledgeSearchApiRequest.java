package com.example.airegistration.knowledge.dto;

import java.util.Map;

public record KnowledgeSearchApiRequest(
        String namespace,
        String query,
        Integer topK,
        Double minScore,
        Map<String, Object> parameters
) {
    public KnowledgeSearchApiRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
