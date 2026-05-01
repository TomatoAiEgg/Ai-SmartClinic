package com.example.airegistration.gateway;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.gateway.config.SupervisorClientProperties;
import com.example.airegistration.gateway.client.SupervisorClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class SupervisorClientTest {

    private MockWebServer supervisorServer;

    @BeforeEach
    void setUp() throws IOException {
        supervisorServer = new MockWebServer();
        supervisorServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        supervisorServer.close();
    }

    @Test
    void shouldPostChatRequestToSupervisorRouteEndpoint() throws Exception {
        supervisorServer.enqueue(jsonResponse("""
                {
                  "chatId":"chat-1",
                  "route":"REGISTRATION",
                  "message":"preview ready",
                  "requiresConfirmation":true,
                  "data":{
                    "action":"create",
                    "previewed":true,
                    "confirmationAction":"create"
                  }
                }
                """));
        SupervisorClientProperties properties = new SupervisorClientProperties();
        properties.setBaseUrl(supervisorServer.url("/").toString());
        SupervisorClient client = new SupervisorClient(WebClient.builder(), properties);

        ChatResponse response = client.route(new ChatRequest(
                "chat-1",
                "user-test-001",
                "book respiratory appointment",
                Map.of("action", "create")
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.data()).containsEntry("action", "create");

        RecordedRequest request = supervisorServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/route");
        assertThat(request.getBody().readUtf8())
                .contains("\"chatId\":\"chat-1\"")
                .contains("\"userId\":\"user-test-001\"")
                .contains("\"action\":\"create\"");
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
