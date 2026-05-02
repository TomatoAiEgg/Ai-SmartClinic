package com.example.airegistration.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.client.AgentClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SupervisorAgentHttpSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AgentClient agentClient;

    @MockBean
    private AiChatClient aiChatClient;

    @Test
    void shouldRouteRegistrationRequestsThroughHttpEndpoint() {
        when(agentClient.call(eq(AgentRoute.REGISTRATION), any(ChatRequest.class))).thenReturn(Mono.just(new ChatResponse(
                "chat-1",
                AgentRoute.REGISTRATION,
                "registration route ok",
                true,
                Map.of("action", "create")
        )));

        ChatResponse response = webTestClient.post()
                .uri("/api/route")
                .bodyValue(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "book respiratory appointment",
                        Map.of("action", "create")
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(response.requiresConfirmation()).isTrue();
        verify(agentClient).call(eq(AgentRoute.REGISTRATION), argThat(request ->
                "chat-1".equals(request.chatId())
                        && "user-1".equals(request.userId())
                        && "create".equals(request.metadata().get("action"))
        ));
    }
}
