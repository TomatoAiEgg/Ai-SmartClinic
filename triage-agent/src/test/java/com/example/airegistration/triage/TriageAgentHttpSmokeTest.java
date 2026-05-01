package com.example.airegistration.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TriageAgentHttpSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AiChatClient aiChatClient;

    @Test
    void shouldExposeTriageEndpointWithRealPolicyAndReplyFlow() {
        when(aiChatClient.callText(any())).thenReturn("triage smoke ok");

        ChatResponse response = webTestClient.post()
                .uri("/api/triage")
                .bodyValue(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "cough and fever for two days",
                        Map.of()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.TRIAGE);
        assertThat(response.message()).isEqualTo("triage smoke ok");
        assertThat(response.data())
                .containsEntry("departmentCode", "RESP")
                .containsEntry("emergency", false);
        verify(aiChatClient).callText(any());
    }
}
