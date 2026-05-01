package com.example.airegistration.rag.core;

import java.time.Duration;
import java.util.List;

public record RagSearchResult(
        String corpusName,
        String namespace,
        String query,
        RagRetrievalStatus status,
        List<RagSearchHit> hits,
        Duration latency,
        String errorMessage
) {
    public RagSearchResult {
        hits = List.copyOf(hits == null ? List.of() : hits);
        latency = latency == null ? Duration.ZERO : latency;
    }

    public static RagSearchResult empty(String corpusName,
                                        String namespace,
                                        String query,
                                        RagRetrievalStatus status,
                                        Duration latency,
                                        String errorMessage) {
        return new RagSearchResult(corpusName, namespace, query, status, List.of(), latency, errorMessage);
    }
}
