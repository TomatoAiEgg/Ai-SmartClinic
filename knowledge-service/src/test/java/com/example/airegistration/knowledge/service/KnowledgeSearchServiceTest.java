package com.example.airegistration.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.knowledge.dto.KnowledgeSearchApiRequest;
import com.example.airegistration.rag.core.RagRetrievalStatus;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.example.airegistration.rag.service.PgvectorRagSearchService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeSearchServiceTest {

    @Test
    void shouldBuildUnifiedKnowledgeSearchRequest() {
        PgvectorRagSearchService ragSearchService = mock(PgvectorRagSearchService.class);
        when(ragSearchService.search(any(RagSearchSpec.class), any(RagSearchRequest.class)))
                .thenReturn(RagSearchResult.empty(
                        "knowledge-service",
                        "default-triage-knowledge",
                        "fever cough",
                        RagRetrievalStatus.EMPTY_RESULT,
                        Duration.ZERO,
                        null
                ));
        KnowledgeSearchService searchService = new KnowledgeSearchService(ragSearchService);

        searchService.search(new KnowledgeSearchApiRequest(
                "default-triage-knowledge",
                "fever cough",
                null,
                null,
                Map.of("departmentCode", "RESP")
        ), "trace-1", "chat-1");

        ArgumentCaptor<RagSearchRequest> requestCaptor = ArgumentCaptor.forClass(RagSearchRequest.class);
        ArgumentCaptor<RagSearchSpec> specCaptor = ArgumentCaptor.forClass(RagSearchSpec.class);
        verify(ragSearchService).search(specCaptor.capture(), requestCaptor.capture());

        RagSearchRequest request = requestCaptor.getValue();
        assertThat(request.namespace()).isEqualTo("default-triage-knowledge");
        assertThat(request.query()).isEqualTo("fever cough");
        assertThat(request.topK()).isEqualTo(5);
        assertThat(request.minScore()).isZero();
        assertThat(request.parameters()).containsEntry("departmentCode", "RESP");
        assertThat(specCaptor.getValue().tableName()).isEqualTo("knowledge_chunk");
        assertThat(specCaptor.getValue().attributeColumns()).containsKeys("departmentCode", "actionTag", "sourceId");
    }
}
