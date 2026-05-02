package com.example.airegistration.rag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.rag.core.KnowledgeChunkInput;
import com.example.airegistration.rag.core.KnowledgeDocumentInput;
import com.example.airegistration.rag.core.KnowledgeIngestRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

class KnowledgeIngestServiceTest {

    @Test
    void shouldFailFastAndRecordJobWhenEmbeddingDimensionsMismatch() {
        NamedParameterJdbcOperations jdbcOperations = mock(NamedParameterJdbcOperations.class);
        FallbackEmbeddingClient embeddingClient = mock(FallbackEmbeddingClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FallbackEmbeddingClient> embeddingProvider = mock(ObjectProvider.class);
        KnowledgeIngestService ingestService = new KnowledgeIngestService(
                jdbcOperations,
                embeddingProvider,
                new ObjectMapper()
        );

        UUID jobId = UUID.randomUUID();
        when(embeddingProvider.getIfAvailable()).thenReturn(embeddingClient);
        when(jdbcOperations.queryForObject(contains("INSERT INTO knowledge_ingest_job"), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(jobId);
        when(embeddingClient.embed("chunk content")).thenReturn(new float[]{0.1F, 0.2F});

        KnowledgeIngestRequest request = new KnowledgeIngestRequest(
                "test-namespace",
                "test-source",
                "Test Source",
                "text-embedding-v2",
                3,
                List.of(new KnowledgeDocumentInput(
                        "test-namespace",
                        "test-source",
                        "Test Source",
                        "TEXT",
                        "Test Document",
                        "v1",
                        "raw content",
                        Map.of(),
                        List.of(new KnowledgeChunkInput(
                                0,
                                "TEXT",
                                "Test Chunk",
                                "chunk content",
                                null,
                                Map.of()
                        ))
                )),
                Map.of()
        );

        assertThatThrownBy(() -> ingestService.ingest(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 3 but got 2");

        verify(jdbcOperations).update(contains("UPDATE knowledge_ingest_job"), any(MapSqlParameterSource.class));
        verify(jdbcOperations, never()).update(contains("INSERT INTO knowledge_chunk"), any(MapSqlParameterSource.class));
    }
}
