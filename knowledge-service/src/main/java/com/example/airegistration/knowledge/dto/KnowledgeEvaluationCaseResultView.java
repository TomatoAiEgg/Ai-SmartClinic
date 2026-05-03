package com.example.airegistration.knowledge.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeEvaluationCaseResultView(
        UUID id,
        UUID runId,
        String caseId,
        String group,
        String namespace,
        boolean passed,
        String status,
        String expectedStatus,
        int hitCount,
        Integer matchedRank,
        Double bestScore,
        String message,
        Instant createdAt
) {
}
