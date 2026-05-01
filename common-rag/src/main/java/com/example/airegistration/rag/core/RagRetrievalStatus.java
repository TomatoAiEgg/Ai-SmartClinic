package com.example.airegistration.rag.core;

public enum RagRetrievalStatus {
    HIT,
    EMPTY_RESULT,
    EMPTY_QUERY,
    EMBEDDING_UNAVAILABLE,
    RETRIEVAL_ERROR
}
