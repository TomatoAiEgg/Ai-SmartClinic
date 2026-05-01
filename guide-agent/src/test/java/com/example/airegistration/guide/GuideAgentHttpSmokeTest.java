package com.example.airegistration.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.guide.service.rag.GuideRagService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GuideAgentHttpSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AiChatClient aiChatClient;

    @MockBean
    private GuideRagService guideRagService;

    @Test
    void shouldExposeGuideEndpointWithRagContext() {
        when(aiChatClient.callText(any())).thenReturn("guide smoke ok");
        when(guideRagService.buildContext(any())).thenReturn(Map.of(
                "source", "guide-agent-rag",
                "retriever", "pgvector",
                "matchCount", 1,
                "citations", List.of("guide-test-001"),
                "referenceText", "test guide evidence"
        ));

        ChatResponse response = webTestClient.post()
                .uri("/api/guide")
                .bodyValue(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "医保材料需要带什么？",
                        Map.of()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.GUIDE);
        assertThat(response.message()).isEqualTo("guide smoke ok");
        assertThat(response.data()).containsEntry("source", "guide-agent-rag");
        assertThat(((Number) response.data().get("matchCount")).intValue()).isGreaterThan(0);
        @SuppressWarnings("unchecked")
        List<String> citations = (List<String>) response.data().get("citations");
        assertThat(citations).isNotEmpty();
        verify(aiChatClient).callText(any());
    }
}
