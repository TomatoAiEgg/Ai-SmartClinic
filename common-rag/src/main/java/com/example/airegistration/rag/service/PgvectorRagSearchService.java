package com.example.airegistration.rag.service;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.rag.core.RagRetrievalStatus;
import com.example.airegistration.rag.core.RagSearchHit;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.example.airegistration.rag.core.VectorLiteral;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

public class PgvectorRagSearchService {

    private static final Logger log = LoggerFactory.getLogger(PgvectorRagSearchService.class);

    private final NamedParameterJdbcOperations jdbcOperations;
    private final ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider;
    private final ObjectProvider<RagRetrievalLogRepository> logRepositoryProvider;

    public PgvectorRagSearchService(NamedParameterJdbcOperations jdbcOperations,
                                    ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                                    ObjectProvider<RagRetrievalLogRepository> logRepositoryProvider) {
        this.jdbcOperations = jdbcOperations;
        this.embeddingClientProvider = embeddingClientProvider;
        this.logRepositoryProvider = logRepositoryProvider;
    }

    public RagSearchResult search(RagSearchSpec spec, RagSearchRequest request) {
        Instant startedAt = Instant.now();
        if (request.query().isBlank()) {
            RagSearchResult result = RagSearchResult.empty(
                    spec.corpusName(),
                    request.namespace(),
                    request.query(),
                    RagRetrievalStatus.EMPTY_QUERY,
                    Duration.between(startedAt, Instant.now()),
                    null
            );
            writeRetrievalLog(spec, request, result);
            return result;
        }

        FallbackEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            log.info("[rag] embedding client unavailable trace_id={} chat_id={} corpus={} namespace={}",
                    request.traceId(), request.chatId(), spec.corpusName(), request.namespace());
            RagSearchResult result = RagSearchResult.empty(
                    spec.corpusName(),
                    request.namespace(),
                    request.query(),
                    RagRetrievalStatus.EMBEDDING_UNAVAILABLE,
                    Duration.between(startedAt, Instant.now()),
                    null
            );
            writeRetrievalLog(spec, request, result);
            return result;
        }

        try {
            String vectorLiteral = VectorLiteral.from(embeddingClient.embed(request.query()));
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("namespace", request.namespace())
                    .addValue("embedding", vectorLiteral)
                    .addValue("topK", request.topK())
                    .addValue("minScore", request.minScore());
            request.parameters().forEach(parameters::addValue);

            List<RagSearchHit> hits = jdbcOperations.query(buildSql(spec), parameters, (rs, rowNum) -> toHit(spec, rs));
            Duration latency = Duration.between(startedAt, Instant.now());
            RagRetrievalStatus status = hits.isEmpty() ? RagRetrievalStatus.EMPTY_RESULT : RagRetrievalStatus.HIT;
            RagSearchHit best = hits.isEmpty() ? null : hits.get(0);
            log.info("[rag] retrieval completed trace_id={} chat_id={} corpus={} namespace={} top_k={} min_score={} status={} hit_count={} best_id={} best_score={} latency_ms={}",
                    request.traceId(),
                    request.chatId(),
                    spec.corpusName(),
                    request.namespace(),
                    request.topK(),
                    request.minScore(),
                    status,
                    hits.size(),
                    best == null ? null : best.id(),
                    best == null ? null : best.score(),
                    latency.toMillis());
            RagSearchResult result = new RagSearchResult(
                    spec.corpusName(),
                    request.namespace(),
                    request.query(),
                    status,
                    hits,
                    latency,
                    null
            );
            writeRetrievalLog(spec, request, result);
            return result;
        } catch (RuntimeException ex) {
            Duration latency = Duration.between(startedAt, Instant.now());
            log.warn("[rag] retrieval failed trace_id={} chat_id={} corpus={} namespace={} error={} latency_ms={}",
                    request.traceId(), request.chatId(), spec.corpusName(), request.namespace(), ex.getMessage(), latency.toMillis());
            RagSearchResult result = RagSearchResult.empty(
                    spec.corpusName(),
                    request.namespace(),
                    request.query(),
                    RagRetrievalStatus.RETRIEVAL_ERROR,
                    latency,
                    ex.getMessage()
            );
            writeRetrievalLog(spec, request, result);
            return result;
        }
    }

    private void writeRetrievalLog(RagSearchSpec spec, RagSearchRequest request, RagSearchResult result) {
        RagRetrievalLogRepository logRepository = logRepositoryProvider.getIfAvailable();
        if (logRepository != null) {
            logRepository.save(spec, request, result);
        }
    }

    private String buildSql(RagSearchSpec spec) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT ")
                .append(spec.idColumn()).append(" AS id, ")
                .append(spec.titleColumn()).append(" AS title, ")
                .append(spec.contentColumn()).append(" AS content, ");
        if (spec.hasMetadataColumn()) {
            sql.append("CAST(").append(spec.metadataColumn()).append(" AS text) AS metadata_json, ");
        } else {
            sql.append("NULL AS metadata_json, ");
        }

        int attributeIndex = 0;
        for (String column : spec.attributeColumns().values()) {
            sql.append(column).append(" AS attr_").append(attributeIndex++).append(", ");
        }
        sql.append("1 - (")
                .append(spec.embeddingColumn())
                .append(" <=> CAST(:embedding AS vector)) AS score ")
                .append("FROM ")
                .append(spec.tableName())
                .append(" WHERE ")
                .append(spec.namespaceColumn())
                .append(" = :namespace AND ")
                .append(spec.enabledColumn())
                .append(" = true ");

        for (String clause : spec.extraWhereClauses()) {
            sql.append("AND ").append(clause).append(' ');
        }

        sql.append("AND 1 - (")
                .append(spec.embeddingColumn())
                .append(" <=> CAST(:embedding AS vector)) >= :minScore ")
                .append("ORDER BY ")
                .append(spec.embeddingColumn())
                .append(" <=> CAST(:embedding AS vector) ")
                .append("LIMIT :topK");
        return sql.toString();
    }

    private RagSearchHit toHit(RagSearchSpec spec, ResultSet rs) throws SQLException {
        Map<String, Object> attributes = new LinkedHashMap<>();
        int attributeIndex = 0;
        for (String key : spec.attributeColumns().keySet()) {
            attributes.put(key, rs.getObject("attr_" + attributeIndex++));
        }
        return new RagSearchHit(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("metadata_json"),
                rs.getDouble("score"),
                attributes
        );
    }
}
