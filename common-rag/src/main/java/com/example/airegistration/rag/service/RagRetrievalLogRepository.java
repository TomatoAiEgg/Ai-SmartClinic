package com.example.airegistration.rag.service;

import com.example.airegistration.rag.core.RagSearchHit;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

public class RagRetrievalLogRepository {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalLogRepository.class);

    private final NamedParameterJdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;

    public RagRetrievalLogRepository(NamedParameterJdbcOperations jdbcOperations, ObjectMapper objectMapper) {
        this.jdbcOperations = jdbcOperations;
        this.objectMapper = objectMapper;
    }

    public void save(RagSearchSpec spec, RagSearchRequest request, RagSearchResult result) {
        RagSearchHit best = result.hits().isEmpty() ? null : result.hits().get(0);
        try {
            jdbcOperations.update("""
                    INSERT INTO knowledge_retrieval_log (
                        trace_id, chat_id, namespace, corpus_name, query_text,
                        top_k, min_score, status, hit_count, best_hit_id,
                        best_score, latency_ms, error_message, hit_ids
                    )
                    VALUES (
                        :traceId, :chatId, :namespace, :corpusName, :queryText,
                        :topK, :minScore, :status, :hitCount, :bestHitId,
                        :bestScore, :latencyMs, :errorMessage, CAST(:hitIds AS jsonb)
                    )
                    """, new MapSqlParameterSource()
                    .addValue("traceId", request.traceId())
                    .addValue("chatId", request.chatId())
                    .addValue("namespace", result.namespace())
                    .addValue("corpusName", spec.corpusName())
                    .addValue("queryText", result.query())
                    .addValue("topK", request.topK())
                    .addValue("minScore", request.minScore())
                    .addValue("status", result.status().name())
                    .addValue("hitCount", result.hits().size())
                    .addValue("bestHitId", best == null ? null : best.id())
                    .addValue("bestScore", best == null ? null : best.score())
                    .addValue("latencyMs", result.latency().toMillis())
                    .addValue("errorMessage", result.errorMessage())
                    .addValue("hitIds", hitIdsJson(result)));
        } catch (RuntimeException ex) {
            log.warn("[rag] retrieval log write failed trace_id={} chat_id={} corpus={} namespace={} error={}",
                    request.traceId(), request.chatId(), spec.corpusName(), result.namespace(), ex.getMessage());
        }
    }

    private String hitIdsJson(RagSearchResult result) {
        try {
            return objectMapper.writeValueAsString(result.hits().stream().map(RagSearchHit::id).toList());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
