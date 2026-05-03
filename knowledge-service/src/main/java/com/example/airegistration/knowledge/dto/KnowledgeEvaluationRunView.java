package com.example.airegistration.knowledge.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record KnowledgeEvaluationRunView(
        UUID id,
        String traceId,
        String baseUrl,
        String casesPath,
        int total,
        int passed,
        int failed,
        double passRate,
        List<KnowledgeEvaluationGroupSummary> byGroup,
        Map<String, Object> metadata,
        Instant generatedAt,
        Instant createdAt
) {
    public KnowledgeEvaluationRunView {
        byGroup = byGroup == null ? List.of() : List.copyOf(byGroup);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
