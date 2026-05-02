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
    void shouldRouteRegistrationActionToRegistrationAgentExecuteEndpoint() throws Exception {
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "route":"REGISTRATION",
                  "message":"registration preview",
                  "requiresConfirmation":true,
                  "structuredData":{
                    "action":"create",
                    "departmentCode":"RESP",
                    "previewed":true,
                    "confirmationAction":"create"
                  },
                  "confirmationId":"",
                  "nextAction":"create"
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
        assertThat(request.getPath()).isEqualTo("/api/agent/execute");
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
                  "route":"TRIAGE",
                  "message":"triage suggestion",
                  "requiresConfirmation":false,
                  "structuredData":{
                    "departmentCode":"RESP",
                    "departmentName":"Respiratory",
                    "emergency":false,
                    "reason":"fever and cough fit respiratory clinic"
                  }
                }
                """));
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "route":"REGISTRATION",
                  "message":"registration preview",
                  "requiresConfirmation":true,
                  "structuredData":{
                    "action":"create",
                    "departmentCode":"RESP",
                    "previewed":true,
                    "confirmationAction":"create"
                  },
                  "confirmationId":"",
                  "nextAction":"create"
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
        Map<?, ?> orchestration = (Map<?, ?>) response.data().get("orchestration");
        assertThat(orchestration.get("mode")).isEqualTo("TRIAGE_THEN_REGISTRATION");
        assertThat(orchestration.get("status")).isEqualTo("registration_handoff");
        assertThat(orchestration.get("upstreamRoute")).isEqualTo("TRIAGE");
        Map<?, ?> handoff = (Map<?, ?>) orchestration.get("handoff");
        assertThat(handoff.get("sourceRoute")).isEqualTo("TRIAGE");
        assertThat(handoff.get("targetRoute")).isEqualTo("REGISTRATION");
        assertThat(handoff.get("departmentCode")).isEqualTo("RESP");
        assertThat(handoff.get("triageReason")).isEqualTo("fever and cough fit respiratory clinic");

        RecordedRequest triageRequest = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(triageRequest).isNotNull();
        assertThat(triageRequest.getMethod()).isEqualTo("POST");
        assertThat(triageRequest.getPath()).isEqualTo("/api/agent/execute");

        RecordedRequest registrationRequest = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(registrationRequest).isNotNull();
        assertThat(registrationRequest.getMethod()).isEqualTo("POST");
        assertThat(registrationRequest.getPath()).isEqualTo("/api/agent/execute");
        assertThat(registrationRequest.getBody().readUtf8())
                .contains("\"chatId\":\"chat-2\"")
                .contains("\"userId\":\"user-test-002\"")
                .contains("\"action\":\"create\"")
                .contains("\"departmentCode\":\"RESP\"")
                .contains("\"departmentName\":\"Respiratory\"")
                .contains("\"orchestration\":\"TRIAGE_THEN_REGISTRATION\"")
                .contains("\"handoff.mode\":\"TRIAGE_THEN_REGISTRATION\"")
                .contains("\"handoff.sourceRoute\":\"TRIAGE\"")
                .contains("\"handoff.targetRoute\":\"REGISTRATION\"")
                .contains("\"handoff.status\":\"registration_handoff\"")
                .contains("\"handoff.departmentCode\":\"RESP\"");
    }

    @Test
    void shouldRouteGuideDecisionThroughRegistryClient() throws Exception {
        when(routeClassifier.determineRoute(any(ChatRequest.class)))
                .thenReturn(Mono.just(RouteDecision.rule(AgentRoute.GUIDE, "test")));
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "route":"GUIDE",
                  "message":"guide answer",
                  "requiresConfirmation":false,
                  "structuredData":{
                    "source":"guide-agent-rag"
                  }
                }
                """));

        ChatResponse response = service.route(new ChatRequest(
                "chat-guide",
                "user-test-003",
                "where should I park?",
                Map.of()
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.GUIDE);
        assertThat(response.message()).isEqualTo("guide answer");
        assertThat(response.data()).containsEntry("source", "guide-agent-rag");

        RecordedRequest request = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/agent/execute");
    }

    @Test
    void shouldFallbackToLegacyEndpointWhenUnifiedEndpointIsUnavailable() throws Exception {
        registrationAgentServer.enqueue(new MockResponse().setResponseCode(404));
        registrationAgentServer.enqueue(jsonResponse("""
                {
                  "chatId":"chat-1",
                  "route":"REGISTRATION",
                  "message":"legacy registration preview",
                  "requiresConfirmation":true,
                  "data":{
                    "action":"create",
                    "departmentCode":"RESP"
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
        assertThat(response.message()).isEqualTo("legacy registration preview");
        assertThat(response.data()).containsEntry("departmentCode", "RESP");

        RecordedRequest unifiedRequest = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(unifiedRequest).isNotNull();
        assertThat(unifiedRequest.getPath()).isEqualTo("/api/agent/execute");

        RecordedRequest legacyRequest = registrationAgentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(legacyRequest).isNotNull();
        assertThat(legacyRequest.getPath()).isEqualTo("/api/registration");
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
