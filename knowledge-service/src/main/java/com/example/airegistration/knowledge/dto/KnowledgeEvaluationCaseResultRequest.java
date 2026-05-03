package com.example.airegistration.knowledge.dto;

public record KnowledgeEvaluationCaseResultRequest(
        String id,
        String group,
        String namespace,
        boolean passed,
        String status,
        String expectedStatus,
        int hitCount,
        Integer matchedRank,
        Double bestScore,
        String message
) {
}
