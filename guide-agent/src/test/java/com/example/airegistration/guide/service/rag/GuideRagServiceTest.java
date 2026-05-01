package com.example.airegistration.guide.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.dto.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GuideRagServiceTest {

    @Test
    void shouldRetrieveGuideKnowledgeFromPgvector() {
        GuideKnowledgeMapper mapper = mock(GuideKnowledgeMapper.class);
        FallbackEmbeddingClient embeddingClient = mock(FallbackEmbeddingClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FallbackEmbeddingClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(embeddingClient);
        when(embeddingClient.embed("医保材料需要带什么？")).thenReturn(new float[]{0.1F, 0.2F});
        when(mapper.search("test-guide", "[0.1,0.2]", 3, 0.55D)).thenReturn(List.of(hit()));

        GuideRagService service = new GuideRagService(
                mapper,
                provider,
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
        verify(mapper).search(eq("test-guide"), eq("[0.1,0.2]"), eq(3), eq(0.55D));
    }

    @Test
    void shouldReturnEmptyContextWhenEmbeddingClientIsUnavailable() {
        GuideKnowledgeMapper mapper = mock(GuideKnowledgeMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FallbackEmbeddingClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        GuideRagService service = new GuideRagService(
                mapper,
                provider,
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

        assertThat(context).containsEntry("matchCount", 0);
        assertThat(context.get("referenceText").toString()).contains("未检索到");
        verifyNoInteractions(mapper);
    }

    private GuideKnowledgeHit hit() {
        GuideKnowledgeHit hit = new GuideKnowledgeHit();
        hit.setCitationId("guide-official-001");
        hit.setSourceId("official-guide");
        hit.setSourceName("官方导诊知识");
        hit.setDocumentId("doc-001");
        hit.setTitle("医保材料");
        hit.setContent("医保就诊通常需要准备身份证件和医保电子凭证。");
        hit.setMetadataJson("{\"sourceType\":\"official_notice\"}");
        hit.setScore(0.88D);
        return hit;
    }
}
