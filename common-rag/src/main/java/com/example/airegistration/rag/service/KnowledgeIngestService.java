package com.example.airegistration.rag.service;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.rag.core.KnowledgeChunkInput;
import com.example.airegistration.rag.core.KnowledgeDocumentInput;
import com.example.airegistration.rag.core.KnowledgeIngestJobStatus;
import com.example.airegistration.rag.core.KnowledgeIngestRequest;
import com.example.airegistration.rag.core.KnowledgeIngestResult;
import com.example.airegistration.rag.core.VectorLiteral;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

public class KnowledgeIngestService {

    private final NamedParameterJdbcOperations jdbcOperations;
    private final ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public KnowledgeIngestService(NamedParameterJdbcOperations jdbcOperations,
                                  ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                                  ObjectMapper objectMapper) {
        this(jdbcOperations, embeddingClientProvider, objectMapper, null);
    }

    public KnowledgeIngestService(NamedParameterJdbcOperations jdbcOperations,
                                  ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        this.jdbcOperations = jdbcOperations;
        this.embeddingClientProvider = embeddingClientProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplateProvider == null ? null : transactionTemplateProvider.getIfAvailable();
    }

    public KnowledgeIngestResult ingest(KnowledgeIngestRequest request) {
        FallbackEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            throw new IllegalStateException("Embedding client is unavailable");
        }

        UUID jobId = createJob(request);
        try {
            markJobRunning(jobId);
            List<PreparedDocument> preparedDocuments = prepareDocuments(request, embeddingClient);
            IngestWriteResult writeResult = writeDocuments(preparedDocuments, request);
            int chunkCount = writeResult.chunkCount();
            finishJob(jobId, KnowledgeIngestJobStatus.SUCCEEDED, request.documents().size(), chunkCount, null);
            return new KnowledgeIngestResult(
                    jobId,
                    KnowledgeIngestJobStatus.SUCCEEDED,
                    request.documents().size(),
                    chunkCount,
                    writeResult.documentIds(),
                    ""
            );
        } catch (RuntimeException ex) {
            finishJob(jobId, KnowledgeIngestJobStatus.FAILED, 0, 0, ex.getMessage());
            throw ex;
        }
    }

    private List<PreparedDocument> prepareDocuments(KnowledgeIngestRequest request,
                                                    FallbackEmbeddingClient embeddingClient) {
        List<PreparedDocument> preparedDocuments = new ArrayList<>();
        for (KnowledgeDocumentInput document : request.documents()) {
            List<PreparedChunk> preparedChunks = new ArrayList<>();
            for (KnowledgeChunkInput chunk : document.chunks()) {
                float[] embedding = embeddingClient.embed(chunk.content());
                validateEmbeddingDimensions(document, chunk, request, embedding);
                preparedChunks.add(new PreparedChunk(chunk, VectorLiteral.from(embedding), document.status()));
            }
            preparedDocuments.add(new PreparedDocument(document, preparedChunks));
        }
        return preparedDocuments;
    }

    private IngestWriteResult writeDocuments(List<PreparedDocument> preparedDocuments,
                                             KnowledgeIngestRequest request) {
        if (transactionTemplate == null) {
            return writeDocumentsInCurrentContext(preparedDocuments, request);
        }
        return transactionTemplate.execute(status -> writeDocumentsInCurrentContext(preparedDocuments, request));
    }

    private IngestWriteResult writeDocumentsInCurrentContext(List<PreparedDocument> preparedDocuments,
                                                             KnowledgeIngestRequest request) {
        List<UUID> documentIds = new ArrayList<>();
        int chunkCount = 0;
        for (PreparedDocument preparedDocument : preparedDocuments) {
            KnowledgeDocumentInput document = preparedDocument.document();
            UUID documentId = upsertDocument(document);
            documentIds.add(documentId);
            disableExistingChunks(documentId);
            for (PreparedChunk preparedChunk : preparedDocument.chunks()) {
                insertChunk(documentId, document.namespace(), preparedChunk, request);
                chunkCount++;
            }
        }
        return new IngestWriteResult(documentIds, chunkCount);
    }

    private UUID createJob(KnowledgeIngestRequest request) {
        return jdbcOperations.queryForObject("""
                INSERT INTO knowledge_ingest_job (
                    namespace, source_id, source_name, status, embedding_model,
                    embedding_dimensions, metadata
                )
                VALUES (
                    :namespace, :sourceId, :sourceName, :status, :embeddingModel,
                    :embeddingDimensions, CAST(:metadata AS jsonb)
                )
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("namespace", request.namespace())
                .addValue("sourceId", request.sourceId())
                .addValue("sourceName", request.sourceName())
                .addValue("status", KnowledgeIngestJobStatus.PENDING.name())
                .addValue("embeddingModel", request.embeddingModel())
                .addValue("embeddingDimensions", request.embeddingDimensions())
                .addValue("metadata", toJson(request.metadata())), UUID.class);
    }

    private void markJobRunning(UUID jobId) {
        jdbcOperations.update("""
                UPDATE knowledge_ingest_job
                SET status = :status
                WHERE id = :jobId
                """, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("status", KnowledgeIngestJobStatus.RUNNING.name()));
    }

    private UUID upsertDocument(KnowledgeDocumentInput document) {
        String status = normalizeDocumentStatus(document.status());
        return jdbcOperations.queryForObject("""
                INSERT INTO knowledge_document (
                    namespace, source_id, source_name, document_type, title,
                    content_sha256, version, status, metadata
                )
                VALUES (
                    :namespace, :sourceId, :sourceName, :documentType, :title,
                    :contentSha256, :version, :status, CAST(:metadata AS jsonb)
                )
                ON CONFLICT (namespace, source_id, version)
                DO UPDATE SET
                    source_name = EXCLUDED.source_name,
                    document_type = EXCLUDED.document_type,
                    title = EXCLUDED.title,
                    content_sha256 = EXCLUDED.content_sha256,
                    status = EXCLUDED.status,
                    metadata = EXCLUDED.metadata,
                    updated_at = now()
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("namespace", document.namespace())
                .addValue("sourceId", document.sourceId())
                .addValue("sourceName", document.sourceName())
                .addValue("documentType", document.documentType())
                .addValue("title", document.title())
                .addValue("contentSha256", sha256(document.rawContent()))
                .addValue("version", document.version())
                .addValue("status", status)
                .addValue("metadata", toJson(document.metadata())), UUID.class);
    }

    private void disableExistingChunks(UUID documentId) {
        jdbcOperations.update("""
                UPDATE knowledge_chunk
                SET enabled = false, updated_at = now()
                WHERE document_id = :documentId
                """, new MapSqlParameterSource("documentId", documentId));
    }

    private void insertChunk(UUID documentId,
                             String namespace,
                             PreparedChunk preparedChunk,
                             KnowledgeIngestRequest request) {
        KnowledgeChunkInput chunk = preparedChunk.chunk();
        boolean enabled = "ACTIVE".equals(normalizeDocumentStatus(preparedChunk.documentStatus()));
        jdbcOperations.update("""
                INSERT INTO knowledge_chunk (
                    document_id, namespace, chunk_index, chunk_type, title, content,
                    token_count, metadata, embedding_model, embedding_dimensions,
                    embedding, enabled
                )
                VALUES (
                    :documentId, :namespace, :chunkIndex, :chunkType, :title, :content,
                    :tokenCount, CAST(:metadata AS jsonb), :embeddingModel, :embeddingDimensions,
                    CAST(:embedding AS vector), :enabled
                )
                ON CONFLICT (document_id, chunk_index)
                DO UPDATE SET
                    chunk_type = EXCLUDED.chunk_type,
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    token_count = EXCLUDED.token_count,
                    metadata = EXCLUDED.metadata,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dimensions = EXCLUDED.embedding_dimensions,
                    embedding = EXCLUDED.embedding,
                    enabled = EXCLUDED.enabled,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("documentId", documentId)
                .addValue("namespace", namespace)
                .addValue("chunkIndex", chunk.chunkIndex())
                .addValue("chunkType", chunk.chunkType())
                .addValue("title", chunk.title())
                .addValue("content", chunk.content())
                .addValue("tokenCount", chunk.tokenCount())
                .addValue("metadata", toJson(chunk.metadata()))
                .addValue("embeddingModel", request.embeddingModel())
                .addValue("embeddingDimensions", request.embeddingDimensions())
                .addValue("embedding", preparedChunk.embedding())
                .addValue("enabled", enabled));
    }

    private void validateEmbeddingDimensions(KnowledgeDocumentInput document,
                                             KnowledgeChunkInput chunk,
                                             KnowledgeIngestRequest request,
                                             float[] embedding) {
        int actualDimensions = embedding == null ? 0 : embedding.length;
        if (actualDimensions != request.embeddingDimensions()) {
            throw new IllegalStateException(
                    "Embedding dimensions mismatch for namespace %s sourceId %s chunkIndex %d: expected %d but got %d"
                            .formatted(
                                    document.namespace(),
                                    document.sourceId(),
                                    chunk.chunkIndex(),
                                    request.embeddingDimensions(),
                                    actualDimensions
                            )
            );
        }
    }

    private void finishJob(UUID jobId,
                           KnowledgeIngestJobStatus status,
                           int documentCount,
                           int chunkCount,
                           String errorMessage) {
        jdbcOperations.update("""
                UPDATE knowledge_ingest_job
                SET status = :status,
                    document_count = :documentCount,
                    chunk_count = :chunkCount,
                    error_message = :errorMessage,
                    finished_at = now()
                WHERE id = :jobId
                """, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("status", status.name())
                .addValue("documentCount", documentCount)
                .addValue("chunkCount", chunkCount)
                .addValue("errorMessage", errorMessage));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize RAG metadata", ex);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private String normalizeDocumentStatus(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
        if ("DISABLED".equals(normalized)) {
            return "DRAFT";
        }
        if (!List.of("DRAFT", "ACTIVE", "ARCHIVED").contains(normalized)) {
            throw new IllegalArgumentException("document status must be DRAFT, ACTIVE, or ARCHIVED");
        }
        return normalized;
    }

    private record PreparedDocument(KnowledgeDocumentInput document, List<PreparedChunk> chunks) {
    }

    private record PreparedChunk(KnowledgeChunkInput chunk, String embedding, String documentStatus) {
    }

    private record IngestWriteResult(List<UUID> documentIds, int chunkCount) {
    }
}
