package com.example.airegistration.knowledge.dto;

import java.time.Instant;

public record KnowledgeRetrievalStatsView(
        String namespace,
        long totalCount,
        long hitCount,
        long emptyResultCount,
        long errorCount,
        double hitRate,
        double emptyResultRate,
        double errorRate,
        double avgLatencyMs,
        Double avgBestScore,
        Instant from,
        Instant to
) {
}
