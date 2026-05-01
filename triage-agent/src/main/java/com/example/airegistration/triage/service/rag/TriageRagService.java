package com.example.airegistration.triage.service.rag;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.dto.ChatRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@ConditionalOnProperty(prefix = "app.ai.triage-rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TriageRagService {

    private static final Logger log = LoggerFactory.getLogger(TriageRagService.class);

    private final TriageKnowledgeMapper mapper;
    private final ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider;
    private final String namespace;
    private final int topK;
    private final double minScore;

    public TriageRagService(TriageKnowledgeMapper mapper,
                            ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                            @Value("${app.ai.triage-rag.namespace:default-triage-knowledge}") String namespace,
                            @Value("${app.ai.triage-rag.top-k:4}") int topK,
                            @Value("${app.ai.triage-rag.min-score:0.58}") double minScore) {
        this.mapper = mapper;
        this.embeddingClientProvider = embeddingClientProvider;
        this.namespace = namespace;
        this.topK = Math.max(1, topK);
        this.minScore = minScore;
    }

    public Mono<TriageRagContext> retrieve(ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            return Mono.just(TriageRagContext.empty());
        }
        return Mono.fromCallable(() -> retrieveBlocking(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("[triage-rag] retrieval failed, use empty evidence trace_id={} chat_id={} error={}",
                            request.traceId(),
                            request.chatId(),
                            ex.getMessage());
                    return Mono.just(TriageRagContext.empty());
                });
    }

    private TriageRagContext retrieveBlocking(ChatRequest request) {
        FallbackEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            log.info("[triage-rag] embedding client unavailable trace_id={} chat_id={}",
                    request.traceId(),
                    request.chatId());
            return TriageRagContext.empty();
        }

        String queryEmbedding = toVectorLiteral(embeddingClient.embed(request.message()));
        List<TriageKnowledgeHit> hits = mapper.search(namespace, queryEmbedding, topK, minScore);
        TriageKnowledgeHit best = hits.isEmpty() ? null : hits.get(0);
        log.info("[triage-rag] retrieval completed trace_id={} chat_id={} namespace={} top_k={} min_score={} hit_count={} best_evidence={} best_department={} best_score={}",
                request.traceId(),
                request.chatId(),
                namespace,
                topK,
                minScore,
                hits.size(),
                best == null ? null : best.getEvidenceId(),
                best == null ? null : best.getDepartmentCode(),
                best == null ? null : best.getScore());
        return new TriageRagContext(hits);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        builder.append(']');
        return builder.toString();
    }
}
