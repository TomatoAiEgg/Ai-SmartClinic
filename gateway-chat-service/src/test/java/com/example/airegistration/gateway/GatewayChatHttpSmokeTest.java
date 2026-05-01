package com.example.airegistration.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.gateway.client.SupervisorClient;
import com.example.airegistration.gateway.repository.UserAccountRepository;
import com.example.airegistration.gateway.service.AuthTokenService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.auth.enabled=false",
                "app.persistence.mybatis-enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        }
)
@AutoConfigureWebTestClient
class GatewayChatHttpSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SupervisorClient supervisorClient;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldExposeChatEndpointAndForwardRequestToSupervisor() {
        ChatResponse expected = new ChatResponse(
                "chat-1",
                AgentRoute.GUIDE,
                "gateway smoke ok",
                false,
                Map.of("source", "supervisor")
        );
        when(supervisorClient.route(any(ChatRequest.class))).thenReturn(Mono.just(expected));

        ChatResponse response = webTestClient.post()
                .uri("/api/chat")
                .bodyValue(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "where is the parking lot",
                        Map.of("channel", "miniapp")
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.GUIDE);
        assertThat(response.message()).isEqualTo("gateway smoke ok");
        verify(supervisorClient).route(argThat(request ->
                "chat-1".equals(request.chatId())
                        && "user-1".equals(request.userId())
                        && "where is the parking lot".equals(request.message())
                        && "miniapp".equals(request.metadata().get("channel"))
        ));
    }
}
