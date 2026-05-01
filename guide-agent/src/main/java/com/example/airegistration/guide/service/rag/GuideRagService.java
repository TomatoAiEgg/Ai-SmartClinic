package com.example.airegistration.guide.service.rag;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.dto.ChatRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GuideRagService {

    private static final Logger log = LoggerFactory.getLogger(GuideRagService.class);

    private final GuideKnowledgeMapper mapper;
    private final ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final int topK;
    private final double minScore;
    private final boolean enabled;

    public GuideRagService(GuideKnowledgeMapper mapper,
                           ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                           ObjectMapper objectMapper,
                           @Value("${app.ai.guide-rag.enabled:true}") boolean enabled,
                           @Value("${app.ai.guide-rag.namespace:default-guide-knowledge}") String namespace,
                           @Value("${app.ai.guide-rag.top-k:3}") int topK,
                           @Value("${app.ai.guide-rag.min-score:0.55}") double minScore) {
        this.mapper = mapper;
        this.embeddingClientProvider = embeddingClientProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.namespace = namespace;
        this.topK = Math.max(1, topK);
        this.minScore = minScore;
    }

    public Map<String, Object> buildContext(ChatRequest request) {
        String query = request.message() == null ? "" : request.message().trim();
        if (!enabled || query.isBlank()) {
            return emptyContext(query);
        }

        FallbackEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            log.info("[guide-rag] embedding client unavailable trace_id={} chat_id={}",
                    request.traceId(),
                    request.chatId());
            return emptyContext(query);
        }

        try {
            String queryEmbedding = toVectorLiteral(embeddingClient.embed(query));
            List<GuideKnowledgeHit> hits = mapper.search(namespace, queryEmbedding, topK, minScore);
            GuideKnowledgeHit best = hits.isEmpty() ? null : hits.get(0);
            log.info("[guide-rag] retrieval completed trace_id={} chat_id={} namespace={} top_k={} min_score={} hit_count={} best_citation={} best_score={}",
                    request.traceId(),
                    request.chatId(),
                    namespace,
                    topK,
                    minScore,
                    hits.size(),
                    best == null ? null : best.getCitationId(),
                    best == null ? null : best.getScore());
            return context(query, hits);
        } catch (RuntimeException ex) {
            log.warn("[guide-rag] retrieval failed, use empty evidence trace_id={} chat_id={} error={}",
                    request.traceId(),
                    request.chatId(),
                    ex.getMessage());
            return emptyContext(query);
        }
    }

    private Map<String, Object> context(String query, List<GuideKnowledgeHit> hits) {
        return Map.of(
                "source", "guide-agent-rag",
                "retriever", "pgvector",
                "query", query,
                "matchCount", hits.size(),
                "citations", hits.stream().map(GuideKnowledgeHit::getCitationId).toList(),
                "referenceText", buildReferenceText(hits),
                "knowledgeSources", hits.stream()
                        .collect(LinkedHashMap::new,
                                (sources, hit) -> sources.putIfAbsent(
                                        hit.getSourceId(),
                                        Map.of(
                                                "sourceId", hit.getSourceId(),
                                                "sourceName", hit.getSourceName()
                                        )
                                ),
                                LinkedHashMap::putAll)
                        .values()
                        .stream()
                        .toList(),
                "snippets", hits.stream()
                        .map(hit -> Map.of(
                                "citationId", hit.getCitationId(),
                                "id", hit.getDocumentId(),
                                "sourceId", hit.getSourceId(),
                                "sourceName", hit.getSourceName(),
                                "title", hit.getTitle(),
                                "content", hit.getContent(),
                                "score", hit.getScore(),
                                "metadata", metadata(hit)
                        ))
                        .toList()
        );
    }

    private Map<String, Object> emptyContext(String query) {
        return context(query, List.of());
    }

    private String buildReferenceText(List<GuideKnowledgeHit> hits) {
        if (hits.isEmpty()) {
            return """
                    未检索到可直接支持该问题的导诊知识片段。
                    只能提供通用就诊建议，并明确具体院内规则以前台、导诊台或官方通知为准。
                    """.strip();
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < hits.size(); index++) {
            GuideKnowledgeHit hit = hits.get(index);
            if (index > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(index + 1)
                    .append(". [")
                    .append(hit.getCitationId())
                    .append("] ")
                    .append(hit.getSourceName())
                    .append(" - ")
                    .append(hit.getTitle())
                    .append(": ")
                    .append(hit.getContent());
        }
        return builder.toString();
    }

    private Map<String, Object> metadata(GuideKnowledgeHit hit) {
        if (hit.getMetadataJson() == null || hit.getMetadataJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(hit.getMetadataJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("[guide-rag] invalid metadata ignored citation_id={} error={}",
                    hit.getCitationId(),
                    ex.getMessage());
            return Map.of();
        }
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
