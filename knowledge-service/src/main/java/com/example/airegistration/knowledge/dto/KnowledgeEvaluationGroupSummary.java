package com.example.airegistration.knowledge.dto;

public record KnowledgeEvaluationGroupSummary(
        String group,
        int total,
        int passed,
        int failed,
        double passRate
) {
}
