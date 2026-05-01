package com.example.airegistration.rag.core;

import java.util.List;
import java.util.UUID;

public record KnowledgeIngestResult(
        UUID jobId,
        KnowledgeIngestJobStatus status,
        int documentCount,
        int chunkCount,
        List<UUID> documentIds,
        String errorMessage
) {
    public KnowledgeIngestResult {
        documentIds = List.copyOf(documentIds == null ? List.of() : documentIds);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
