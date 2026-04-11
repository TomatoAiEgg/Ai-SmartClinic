package com.example.airegistration.ai;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingResponse;

public record FallbackEmbeddingResult(String model, int attempt, EmbeddingResponse response) {

    public List<float[]> embeddings() {
        if (response == null || response.getResults() == null) {
            return List.of();
        }
        return response.getResults().stream()
                .map(result -> result.getOutput())
                .toList();
    }
}
