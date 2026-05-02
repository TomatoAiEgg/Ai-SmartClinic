package com.example.airegistration.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.agent.AgentRequestEnvelope;
import com.example.airegistration.agent.AgentResponseEnvelope;
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

    @Test
    void shouldExposeUnifiedAgentExecuteEndpoint() {
        when(aiChatClient.callText(any())).thenReturn("triage execute ok");

        AgentResponseEnvelope response = webTestClient.post()
                .uri("/api/agent/execute")
                .bodyValue(new AgentRequestEnvelope(
                        "trace-1",
                        "chat-1",
                        "user-1",
                        "cough and fever for two days",
                        Map.of("routeReason", "test"),
                        Map.of()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentResponseEnvelope.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.TRIAGE.name());
        assertThat(response.message()).isEqualTo("triage execute ok");
        assertThat(response.structuredData())
                .containsEntry("departmentCode", "RESP")
                .containsEntry("emergency", false);
        assertThat(response.executionMeta().agentName()).isEqualTo("triage-agent");
        assertThat(response.executionMeta().latencyMs()).isGreaterThanOrEqualTo(0L);
        verify(aiChatClient).callText(any());
    }

    @Test
    void shouldExposeAgentCapabilities() {
        AgentCapability capability = webTestClient.get()
                .uri("/api/agent/capabilities")
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentCapability.class)
                .returnResult()
                .getResponseBody();

        assertThat(capability).isNotNull();
        assertThat(capability.agentName()).isEqualTo("triage-agent");
        assertThat(capability.supportedRoutes()).contains(AgentRoute.TRIAGE.name());
    }
}
