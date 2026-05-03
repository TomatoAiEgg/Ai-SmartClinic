package com.example.airegistration.knowledge.dto;

public record KnowledgeEvaluationSummary(
        int total,
        int passed,
        int failed,
        double passRate
) {
}
