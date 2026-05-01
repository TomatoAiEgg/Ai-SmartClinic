package com.example.airegistration.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.client.AgentClient;
import com.example.airegistration.supervisor.config.AgentClientProperties;
import com.example.airegistration.supervisor.service.RouteDecision;
import com.example.airegistration.supervisor.service.SupervisorReplyService;
import com.example.airegistration.supervisor.service.SupervisorRouteClassifier;
import com.example.airegistration.supervisor.service.orchestrator.SupervisorOrchestrationPolicy;
import com.example.airegistration.supervisor.service.orchestrator.SupervisorOrchestratorService;
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
import reactor.core.publisher.Mono;

class SupervisorOrchestratorServiceTest {

    private MockWebServer registrationAgentServer;
    private SupervisorOrchestratorService service;
    private SupervisorRouteClassifier routeClassifier;

    @BeforeEach
    void setUp() throws IOException {
        registrationAgentServer = new MockWebServer();
        registrationAgentServer.start();

        AgentClientProperties properties = new AgentClientProperties();
        String registrationBaseUrl = registrationAgentServer.url("/").toString();
        properties.setRegistration(new AgentClientProperties.Endpoint(registrationBaseUrl));
        properties.setTriage(new AgentClientProperties.Endpoint(registrationBaseUrl));
        properties.setGuide(new AgentClientProperties.Endpoint(registrationBaseUrl));

        AgentClient agentClient = new AgentClient(WebClient.builder(), properties);
        routeClassifier = mock(SupervisorRouteClassifier.class);
        when(routeClassifier.determineRoute(any(ChatRequest.class)))
                .thenReturn(Mono.just(RouteDecision.rule(AgentRoute.REGISTRATION, "test")));
        service = new SupervisorOrchestratorService(
                routeClassifier,
                agentClient,
                mock(SupervisorReplyService.class),
                new SupervisorOrchestrationPolicy()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        registrationAgentServer.close();
    }

    @Test
    void shouldRouteRegistrationActionToRegistrationAgentHttpEndpoint() throws Exception {
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "chatId":"chat-1",
                  "route":"REGISTRATION",
                  "message":"registration preview",
                  "requiresConfirmation":true,
                  "data":{
                    "action":"create",
                    "departmentCode":"RESP",
                    "previewed":true,
                    "confirmationAction":"create"
                  }
                }
                """));

        ChatResponse response = service.route(new ChatRequest(
                "chat-1",
                "user-test-001",
                "book respiratory appointment",
                Map.of("action", "create", "departmentCode", "RESP")
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("departmentCode", "RESP");

        RecordedRequest request = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/registration");
        assertThat(request.getBody().readUtf8())
                .contains("\"chatId\":\"chat-1\"")
                .contains("\"userId\":\"user-test-001\"")
                .contains("\"action\":\"create\"")
                .contains("\"departmentCode\":\"RESP\"");
    }

    @Test
    void shouldOrchestrateTriageBeforeRegistrationForSymptomBasedBooking() throws Exception {
        when(routeClassifier.determineRoute(any(ChatRequest.class)))
                .thenReturn(Mono.just(RouteDecision.rule(AgentRoute.TRIAGE, "test")));
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "chatId":"chat-2",
                  "route":"TRIAGE",
                  "message":"建议优先挂呼吸内科。",
                  "requiresConfirmation":false,
                  "data":{
                    "departmentCode":"RESP",
                    "departmentName":"呼吸内科",
                    "emergency":false,
                    "reason":"发热和咳嗽更符合呼吸内科就诊范围"
                  }
                }
                """));
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "chatId":"chat-2",
                  "route":"REGISTRATION",
                  "message":"已为你找到呼吸内科可预约号源，请确认。",
                  "requiresConfirmation":true,
                  "data":{
                    "action":"create",
                    "departmentCode":"RESP",
                    "previewed":true,
                    "confirmationAction":"create"
                  }
                }
                """));

        ChatResponse response = service.route(new ChatRequest(
                "chat-2",
                "user-test-002",
                "I have fever and cough and want to book an appointment",
                Map.of()
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("departmentCode", "RESP")
                .containsKey("orchestration")
                .containsKey("upstreamTriage");

        RecordedRequest triageRequest = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(triageRequest).isNotNull();
        assertThat(triageRequest.getMethod()).isEqualTo("POST");
        assertThat(triageRequest.getPath()).isEqualTo("/api/triage");

        RecordedRequest registrationRequest = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(registrationRequest).isNotNull();
        assertThat(registrationRequest.getMethod()).isEqualTo("POST");
        assertThat(registrationRequest.getPath()).isEqualTo("/api/registration");
        assertThat(registrationRequest.getBody().readUtf8())
                .contains("\"chatId\":\"chat-2\"")
                .contains("\"userId\":\"user-test-002\"")
                .contains("\"action\":\"create\"")
                .contains("\"departmentCode\":\"RESP\"")
                .contains("\"departmentName\":\"呼吸内科\"")
                .contains("\"orchestration\":\"TRIAGE_THEN_REGISTRATION\"");
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
