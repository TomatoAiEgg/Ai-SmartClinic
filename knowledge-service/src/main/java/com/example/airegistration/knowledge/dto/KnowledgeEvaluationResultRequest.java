package com.example.airegistration.knowledge.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record KnowledgeEvaluationResultRequest(
        Instant generatedAt,
        String traceId,
        String baseUrl,
        String casesPath,
        KnowledgeEvaluationSummary summary,
        List<KnowledgeEvaluationGroupSummary> byGroup,
        List<KnowledgeEvaluationCaseResultRequest> results,
        Map<String, Object> metadata
) {
    public KnowledgeEvaluationResultRequest {
        byGroup = byGroup == null ? List.of() : List.copyOf(byGroup);
        results = results == null ? List.of() : List.copyOf(results);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
