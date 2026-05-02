package com.example.airegistration.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.client.AgentRegistry;
import com.example.airegistration.supervisor.client.RegisteredAgent;
import com.example.airegistration.supervisor.config.AgentClientProperties;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class AgentRegistryTest {

    private MockWebServer agentServer;

    @BeforeEach
    void setUp() throws IOException {
        agentServer = new MockWebServer();
        agentServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        agentServer.close();
    }

    @Test
    void shouldLoadAgentCapabilitiesFromConfiguredEndpoint() throws Exception {
        agentServer.enqueue(jsonResponse("""
                {
                  "agentName":"custom-triage-agent",
                  "description":"triage capability",
                  "supportedRoutes":["TRIAGE"],
                  "inputSchemas":["AgentRequestEnvelope"],
                  "outputSchemas":["AgentResponseEnvelope"],
                  "patterns":["ROUTING_AGENT"],
                  "metadata":{"executeEndpoint":"/api/agent/execute"}
                }
                """));
        AgentRegistry registry = new AgentRegistry(WebClient.builder(), propertiesWithTriageServer());

        registry.refreshCapabilities().block();

        RegisteredAgent agent = registry.requireAgent(AgentRoute.TRIAGE);
        assertThat(agent.agentName()).isEqualTo("custom-triage-agent");
        assertThat(agent.capability()).isNotNull();
        assertThat(agent.capability().supportedRoutes()).contains("TRIAGE");

        RecordedRequest request = agentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/agent/capabilities");
    }

    @Test
    void shouldKeepConfiguredFallbackWhenCapabilityEndpointFails() {
        agentServer.enqueue(new MockResponse().setResponseCode(404));
        AgentRegistry registry = new AgentRegistry(WebClient.builder(), propertiesWithTriageServer());

        registry.refreshCapabilities().block();

        RegisteredAgent agent = registry.requireAgent(AgentRoute.TRIAGE);
        assertThat(agent.agentName()).isEqualTo("triage-agent");
        assertThat(agent.capability()).isNull();
    }

    private AgentClientProperties propertiesWithTriageServer() {
        AgentClientProperties properties = new AgentClientProperties();
        properties.setTriage(new AgentClientProperties.Endpoint(agentServer.url("/").toString()));
        properties.setRegistration(new AgentClientProperties.Endpoint(""));
        properties.setGuide(new AgentClientProperties.Endpoint(""));
        return properties;
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
