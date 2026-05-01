package com.example.airegistration.triage.service.rag;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.rag.core.RagSearchHit;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.example.airegistration.rag.service.PgvectorRagSearchService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@ConditionalOnProperty(prefix = "app.ai.triage-rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TriageRagService {

    private static final Logger log = LoggerFactory.getLogger(TriageRagService.class);

    private static final RagSearchSpec SEARCH_SPEC = new RagSearchSpec(
            "triage-knowledge",
            "knowledge_chunk",
            "id",
            "title",
            "content",
            "embedding",
            "namespace",
            "enabled",
            "metadata",
            Map.of(
                    "departmentCode", "metadata ->> 'departmentCode'",
                    "departmentName", "metadata ->> 'departmentName'",
                    "emergency", "metadata ->> 'emergency'"
            ),
            null
    );

    private final PgvectorRagSearchService ragSearchService;
    private final String namespace;
    private final int topK;
    private final double minScore;

    public TriageRagService(PgvectorRagSearchService ragSearchService,
                            @Value("${app.ai.triage-rag.namespace:default-triage-knowledge}") String namespace,
                            @Value("${app.ai.triage-rag.top-k:4}") int topK,
                            @Value("${app.ai.triage-rag.min-score:0.58}") double minScore) {
        this.ragSearchService = ragSearchService;
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
        RagSearchResult result = ragSearchService.search(SEARCH_SPEC, new RagSearchRequest(
                request.traceId(),
                request.chatId(),
                namespace,
                request.message(),
                topK,
                minScore,
                Map.of()
        ));
        TriageRagContext context = new TriageRagContext(result.hits().stream()
                .map(TriageRagService::toTriageHit)
                .toList());
        TriageKnowledgeHit best = context.hits().isEmpty() ? null : context.hits().get(0);
        log.info("[triage-rag] retrieval completed trace_id={} chat_id={} namespace={} top_k={} min_score={} hit_count={} best_evidence={} best_department={} best_score={}",
                request.traceId(),
                request.chatId(),
                namespace,
                topK,
                minScore,
                context.hits().size(),
                best == null ? null : best.getEvidenceId(),
                best == null ? null : best.getDepartmentCode(),
                best == null ? null : best.getScore());
        return context;
    }

    private static TriageKnowledgeHit toTriageHit(RagSearchHit hit) {
        TriageKnowledgeHit triageHit = new TriageKnowledgeHit();
        triageHit.setEvidenceId(hit.id());
        triageHit.setTitle(hit.title());
        triageHit.setContent(hit.content());
        triageHit.setDepartmentCode(stringAttribute(hit, "departmentCode"));
        triageHit.setDepartmentName(stringAttribute(hit, "departmentName"));
        triageHit.setEmergency(booleanAttribute(hit, "emergency"));
        triageHit.setScore(hit.score());
        return triageHit;
    }

    private static String stringAttribute(RagSearchHit hit, String key) {
        Object value = hit.attributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean booleanAttribute(RagSearchHit hit, String key) {
        Object value = hit.attributes().get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
