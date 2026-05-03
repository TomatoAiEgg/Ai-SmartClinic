package com.example.airegistration.knowledge.service;

import com.example.airegistration.knowledge.dto.KnowledgeChunkView;
import com.example.airegistration.knowledge.dto.KnowledgeDocumentView;
import com.example.airegistration.knowledge.dto.KnowledgeEvaluationCaseResultRequest;
import com.example.airegistration.knowledge.dto.KnowledgeEvaluationCaseResultView;
import com.example.airegistration.knowledge.dto.KnowledgeEvaluationGroupSummary;
import com.example.airegistration.knowledge.dto.KnowledgeEvaluationResultRequest;
import com.example.airegistration.knowledge.dto.KnowledgeEvaluationRunView;
import com.example.airegistration.knowledge.dto.KnowledgeEvaluationSummary;
import com.example.airegistration.knowledge.dto.KnowledgeIngestJobView;
import com.example.airegistration.knowledge.dto.KnowledgeRetrievalLogView;
import com.example.airegistration.knowledge.dto.KnowledgeRetrievalStatsView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeAdminService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final NamedParameterJdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;

    public KnowledgeAdminService(NamedParameterJdbcOperations jdbcOperations, ObjectMapper objectMapper) {
        this.jdbcOperations = jdbcOperations;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeIngestJobView> listJobs(String namespace, Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, namespace, source_id, source_name, status, embedding_model,
                       embedding_dimensions, document_count, chunk_count, error_message,
                       CAST(metadata AS text) AS metadata_json, started_at, finished_at
                FROM knowledge_ingest_job
                WHERE 1 = 1
                """);
        query.addTextFilter("namespace", namespace);
        query.sql.append("ORDER BY started_at DESC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toJobView);
    }

    public KnowledgeIngestJobView getJob(UUID jobId) {
        try {
            return jdbcOperations.queryForObject("""
                    SELECT id, namespace, source_id, source_name, status, embedding_model,
                           embedding_dimensions, document_count, chunk_count, error_message,
                           CAST(metadata AS text) AS metadata_json, started_at, finished_at
                    FROM knowledge_ingest_job
                    WHERE id = :id
                    """, new MapSqlParameterSource("id", jobId), this::toJobView);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("knowledge ingest job not found: " + jobId);
        }
    }

    public List<KnowledgeDocumentView> listDocuments(String namespace, String status, Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, namespace, source_id, source_name, document_type, title,
                       version, status, CAST(metadata AS text) AS metadata_json,
                       created_at, updated_at
                FROM knowledge_document
                WHERE 1 = 1
                """);
        query.addTextFilter("namespace", namespace);
        query.addTextFilter("status", normalizeOptionalStatus(status));
        query.sql.append("ORDER BY updated_at DESC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toDocumentView);
    }

    public KnowledgeDocumentView getDocument(UUID documentId) {
        try {
            return jdbcOperations.queryForObject("""
                    SELECT id, namespace, source_id, source_name, document_type, title,
                           version, status, CAST(metadata AS text) AS metadata_json,
                           created_at, updated_at
                    FROM knowledge_document
                    WHERE id = :id
                    """, new MapSqlParameterSource("id", documentId), this::toDocumentView);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("knowledge document not found: " + documentId);
        }
    }

    public List<KnowledgeChunkView> listChunks(String namespace, Boolean enabled, Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, document_id, namespace, chunk_index, chunk_type, title, content,
                       enabled, embedding_model, embedding_dimensions,
                       CAST(metadata AS text) AS metadata_json, created_at, updated_at
                FROM knowledge_chunk
                WHERE 1 = 1
                """);
        query.addTextFilter("namespace", namespace);
        if (enabled != null) {
            query.sql.append("AND enabled = :enabled ");
            query.parameters.addValue("enabled", enabled);
        }
        query.sql.append("ORDER BY updated_at DESC, chunk_index ASC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toChunkView);
    }

    public List<KnowledgeRetrievalLogView> listRetrievalLogs(String namespace, Integer limit) {
        return listRetrievalLogs(namespace, null, null, limit);
    }

    public List<KnowledgeRetrievalLogView> listRetrievalLogs(String namespace, String traceId, String chatId, Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, trace_id, chat_id, namespace, corpus_name, query_text, top_k,
                       min_score, status, hit_count, best_hit_id, best_score, latency_ms,
                       error_message, CAST(hit_ids AS text) AS hit_ids_json, created_at
                FROM knowledge_retrieval_log
                WHERE 1 = 1
                """);
        query.addTextFilter("namespace", namespace);
        query.addTextFilter("trace_id", traceId);
        query.addTextFilter("chat_id", chatId);
        query.sql.append("ORDER BY created_at DESC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toRetrievalLogView);
    }

    public KnowledgeRetrievalStatsView retrievalStats(String namespace, Integer hours) {
        int windowHours = safeHours(hours);
        QueryParts query = new QueryParts("""
                SELECT
                    COALESCE(:namespaceLabel, 'ALL') AS namespace_label,
                    COUNT(*) AS total_count,
                    SUM(CASE WHEN status = 'HIT' THEN 1 ELSE 0 END) AS hit_count,
                    SUM(CASE WHEN status IN ('EMPTY_RESULT', 'EMPTY_QUERY') THEN 1 ELSE 0 END) AS empty_result_count,
                    SUM(CASE WHEN status IN ('RETRIEVAL_ERROR', 'EMBEDDING_UNAVAILABLE') THEN 1 ELSE 0 END) AS error_count,
                    AVG(latency_ms) AS avg_latency_ms,
                    AVG(best_score) AS avg_best_score,
                    MIN(created_at) AS from_at,
                    MAX(created_at) AS to_at
                FROM knowledge_retrieval_log
                WHERE created_at >= now() - (:hours * interval '1 hour')
                """);
        query.parameters.addValue("hours", windowHours);
        query.parameters.addValue("namespaceLabel", hasText(namespace) ? namespace.trim() : null);
        query.addTextFilter("namespace", namespace);
        return jdbcOperations.queryForObject(query.sql.toString(), query.parameters, this::toRetrievalStatsView);
    }

    @Transactional
    public KnowledgeEvaluationRunView saveEvaluationResult(KnowledgeEvaluationResultRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("evaluation result request cannot be null");
        }
        KnowledgeEvaluationSummary summary = request.summary() == null
                ? new KnowledgeEvaluationSummary(request.results().size(), 0, request.results().size(), 0D)
                : request.summary();
        UUID runId = UUID.randomUUID();
        jdbcOperations.update("""
                INSERT INTO knowledge_evaluation_run (
                    id, trace_id, base_url, cases_path, total_count, passed_count,
                    failed_count, pass_rate, by_group, metadata, generated_at
                )
                VALUES (
                    :id, :traceId, :baseUrl, :casesPath, :totalCount, :passedCount,
                    :failedCount, :passRate, CAST(:byGroup AS jsonb), CAST(:metadata AS jsonb), :generatedAt
                )
                """, new MapSqlParameterSource()
                .addValue("id", runId)
                .addValue("traceId", blankToNull(request.traceId()))
                .addValue("baseUrl", blankToNull(request.baseUrl()))
                .addValue("casesPath", blankToNull(request.casesPath()))
                .addValue("totalCount", summary.total())
                .addValue("passedCount", summary.passed())
                .addValue("failedCount", summary.failed())
                .addValue("passRate", summary.passRate())
                .addValue("byGroup", toJson(request.byGroup()))
                .addValue("metadata", toJson(request.metadata()))
                .addValue("generatedAt", request.generatedAt()));
        for (KnowledgeEvaluationCaseResultRequest result : request.results()) {
            jdbcOperations.update("""
                    INSERT INTO knowledge_evaluation_case_result (
                        run_id, case_id, group_name, namespace, passed, status,
                        expected_status, hit_count, matched_rank, best_score, message
                    )
                    VALUES (
                        :runId, :caseId, :groupName, :namespace, :passed, :status,
                        :expectedStatus, :hitCount, :matchedRank, :bestScore, :message
                    )
                    """, new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("caseId", blankToNull(result.id()))
                    .addValue("groupName", blankToNull(result.group()))
                    .addValue("namespace", blankToNull(result.namespace()))
                    .addValue("passed", result.passed())
                    .addValue("status", blankToNull(result.status()))
                    .addValue("expectedStatus", blankToNull(result.expectedStatus()))
                    .addValue("hitCount", result.hitCount())
                    .addValue("matchedRank", result.matchedRank())
                    .addValue("bestScore", result.bestScore())
                    .addValue("message", blankToNull(result.message())));
        }
        return getEvaluationRun(runId);
    }

    public List<KnowledgeEvaluationRunView> listEvaluationRuns(String traceId, Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, trace_id, base_url, cases_path, total_count, passed_count,
                       failed_count, pass_rate, CAST(by_group AS text) AS by_group_json,
                       CAST(metadata AS text) AS metadata_json, generated_at, created_at
                FROM knowledge_evaluation_run
                WHERE 1 = 1
                """);
        query.addTextFilter("trace_id", traceId);
        query.sql.append("ORDER BY created_at DESC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toEvaluationRunView);
    }

    public KnowledgeEvaluationRunView getEvaluationRun(UUID runId) {
        try {
            return jdbcOperations.queryForObject("""
                    SELECT id, trace_id, base_url, cases_path, total_count, passed_count,
                           failed_count, pass_rate, CAST(by_group AS text) AS by_group_json,
                           CAST(metadata AS text) AS metadata_json, generated_at, created_at
                    FROM knowledge_evaluation_run
                    WHERE id = :id
                    """, new MapSqlParameterSource("id", runId), this::toEvaluationRunView);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("knowledge evaluation run not found: " + runId);
        }
    }

    public List<KnowledgeEvaluationCaseResultView> listEvaluationCaseResults(UUID runId, Boolean passed, Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, run_id, case_id, group_name, namespace, passed, status,
                       expected_status, hit_count, matched_rank, best_score, message, created_at
                FROM knowledge_evaluation_case_result
                WHERE run_id = :runId
                """);
        query.parameters.addValue("runId", runId);
        if (passed != null) {
            query.sql.append("AND passed = :passed ");
            query.parameters.addValue("passed", passed);
        }
        query.sql.append("ORDER BY passed ASC, group_name ASC, case_id ASC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toEvaluationCaseResultView);
    }

    @Transactional
    public KnowledgeDocumentView updateDocumentStatus(UUID documentId, String status) {
        String normalizedStatus = requireDocumentStatus(status);
        boolean enabled = "ACTIVE".equals(normalizedStatus);
        int updatedDocuments = jdbcOperations.update("""
                UPDATE knowledge_document
                SET status = :status,
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("status", normalizedStatus));
        if (updatedDocuments == 0) {
            throw new NoSuchElementException("knowledge document not found: " + documentId);
        }
        jdbcOperations.update("""
                UPDATE knowledge_chunk
                SET enabled = :enabled,
                    updated_at = now()
                WHERE document_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("enabled", enabled));
        return getDocument(documentId);
    }

    private KnowledgeIngestJobView toJobView(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeIngestJobView(
                uuid(rs, "id"),
                rs.getString("namespace"),
                rs.getString("source_id"),
                rs.getString("source_name"),
                rs.getString("status"),
                rs.getString("embedding_model"),
                nullableInteger(rs, "embedding_dimensions"),
                rs.getInt("document_count"),
                rs.getInt("chunk_count"),
                rs.getString("error_message"),
                jsonMap(rs.getString("metadata_json")),
                instant(rs, "started_at"),
                instant(rs, "finished_at")
        );
    }

    private KnowledgeDocumentView toDocumentView(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeDocumentView(
                uuid(rs, "id"),
                rs.getString("namespace"),
                rs.getString("source_id"),
                rs.getString("source_name"),
                rs.getString("document_type"),
                rs.getString("title"),
                rs.getString("version"),
                rs.getString("status"),
                jsonMap(rs.getString("metadata_json")),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private KnowledgeChunkView toChunkView(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeChunkView(
                uuid(rs, "id"),
                uuid(rs, "document_id"),
                rs.getString("namespace"),
                rs.getInt("chunk_index"),
                rs.getString("chunk_type"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getBoolean("enabled"),
                rs.getString("embedding_model"),
                nullableInteger(rs, "embedding_dimensions"),
                jsonMap(rs.getString("metadata_json")),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private KnowledgeRetrievalLogView toRetrievalLogView(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeRetrievalLogView(
                uuid(rs, "id"),
                rs.getString("trace_id"),
                rs.getString("chat_id"),
                rs.getString("namespace"),
                rs.getString("corpus_name"),
                rs.getString("query_text"),
                rs.getInt("top_k"),
                rs.getDouble("min_score"),
                rs.getString("status"),
                rs.getInt("hit_count"),
                rs.getString("best_hit_id"),
                nullableDouble(rs, "best_score"),
                rs.getLong("latency_ms"),
                rs.getString("error_message"),
                jsonStringList(rs.getString("hit_ids_json")),
                instant(rs, "created_at")
        );
    }

    private KnowledgeRetrievalStatsView toRetrievalStatsView(ResultSet rs, int rowNum) throws SQLException {
        long total = rs.getLong("total_count");
        long hit = rs.getLong("hit_count");
        long empty = rs.getLong("empty_result_count");
        long error = rs.getLong("error_count");
        Double avgLatencyMs = nullableDouble(rs, "avg_latency_ms");
        return new KnowledgeRetrievalStatsView(
                rs.getString("namespace_label"),
                total,
                hit,
                empty,
                error,
                rate(hit, total),
                rate(empty, total),
                rate(error, total),
                avgLatencyMs == null ? 0D : avgLatencyMs,
                nullableDouble(rs, "avg_best_score"),
                instant(rs, "from_at"),
                instant(rs, "to_at")
        );
    }

    private KnowledgeEvaluationRunView toEvaluationRunView(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeEvaluationRunView(
                uuid(rs, "id"),
                rs.getString("trace_id"),
                rs.getString("base_url"),
                rs.getString("cases_path"),
                rs.getInt("total_count"),
                rs.getInt("passed_count"),
                rs.getInt("failed_count"),
                rs.getDouble("pass_rate"),
                jsonGroupSummaries(rs.getString("by_group_json")),
                jsonMap(rs.getString("metadata_json")),
                instant(rs, "generated_at"),
                instant(rs, "created_at")
        );
    }

    private KnowledgeEvaluationCaseResultView toEvaluationCaseResultView(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeEvaluationCaseResultView(
                uuid(rs, "id"),
                uuid(rs, "run_id"),
                rs.getString("case_id"),
                rs.getString("group_name"),
                rs.getString("namespace"),
                rs.getBoolean("passed"),
                rs.getString("status"),
                rs.getString("expected_status"),
                rs.getInt("hit_count"),
                nullableInteger(rs, "matched_rank"),
                nullableDouble(rs, "best_score"),
                rs.getString("message"),
                instant(rs, "created_at")
        );
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String requireDocumentStatus(String status) {
        String normalizedStatus = normalizeOptionalStatus(status);
        if ("DISABLED".equals(normalizedStatus)) {
            return "DRAFT";
        }
        if (normalizedStatus == null || !List.of("DRAFT", "ACTIVE", "ARCHIVED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("status must be DRAFT, ACTIVE, or ARCHIVED");
        }
        return normalizedStatus;
    }

    private int safeHours(Integer hours) {
        int resolved = hours == null ? 24 : hours;
        if (resolved <= 0) {
            return 24;
        }
        return (int) Math.min(Duration.ofDays(90).toHours(), resolved);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / (double) denominator;
    }

    private int safeLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(resolved, MAX_LIMIT);
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Map<String, Object> jsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of("_raw", json);
        }
    }

    private List<KnowledgeEvaluationGroupSummary> jsonGroupSummaries(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<String> jsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class QueryParts {
        private final StringBuilder sql;
        private final MapSqlParameterSource parameters = new MapSqlParameterSource();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void addTextFilter(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            String parameterName = "p" + parameters.getValues().size();
            sql.append("AND ").append(column).append(" = :").append(parameterName).append(' ');
            parameters.addValue(parameterName, value.trim());
        }
    }
}
