package com.example.airegistration.guide.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.rag.core.RagRetrievalStatus;
import com.example.airegistration.rag.core.RagSearchHit;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.example.airegistration.rag.service.PgvectorRagSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuideRagServiceTest {

    @Test
    void shouldRetrieveGuideKnowledgeFromPgvector() {
        PgvectorRagSearchService searchService = mock(PgvectorRagSearchService.class);
        when(searchService.search(any(RagSearchSpec.class), any(RagSearchRequest.class))).thenReturn(result(hit()));

        GuideRagService service = new GuideRagService(
                searchService,
                new ObjectMapper(),
                true,
                "test-guide",
                3,
                0.55D
        );

        Map<String, Object> context = service.buildContext(new ChatRequest(
                "chat-1",
                "user-1",
                "医保材料需要带什么？",
                Map.of()
        ));

        assertThat(context).containsEntry("source", "guide-agent-rag");
        assertThat(context).containsEntry("retriever", "pgvector");
        assertThat(context).containsEntry("matchCount", 1);
        assertThat(context.get("referenceText").toString()).contains("医保材料");
        @SuppressWarnings("unchecked")
        List<String> citations = (List<String>) context.get("citations");
        assertThat(citations).containsExactly("guide-official-001");
        verify(searchService).search(any(RagSearchSpec.class), any(RagSearchRequest.class));
    }

    @Test
    void shouldReturnEmptyContextWhenDisabled() {
        PgvectorRagSearchService searchService = mock(PgvectorRagSearchService.class);

        GuideRagService service = new GuideRagService(
                searchService,
                new ObjectMapper(),
                false,
                "test-guide",
                3,
                0.55D
        );

        Map<String, Object> context = service.buildContext(new ChatRequest(
                "chat-1",
                "user-1",
                "医保材料需要带什么？",
                Map.of()
        ));

        assertThat(context).containsEntry("matchCount", 0);
        assertThat(context.get("referenceText").toString()).contains("未检索到");
        verifyNoInteractions(searchService);
    }

    private RagSearchResult result(RagSearchHit hit) {
        return new RagSearchResult(
                "guide-knowledge",
                "test-guide",
                "医保材料需要带什么？",
                RagRetrievalStatus.HIT,
                List.of(hit),
                Duration.ofMillis(12),
                null
        );
    }

    private RagSearchHit hit() {
        return new RagSearchHit(
                "guide-official-001",
                "医保材料",
                "医保就诊通常需要准备身份证件和医保电子凭证。",
                "{\"sourceType\":\"official_notice\"}",
                0.88D,
                Map.of(
                        "sourceId", "official-guide",
                        "sourceName", "官方导诊知识",
                        "documentId", "doc-001"
                )
        );
    }
}
