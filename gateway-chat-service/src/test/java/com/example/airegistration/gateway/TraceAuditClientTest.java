package com.example.airegistration.gateway;

import com.example.airegistration.gateway.client.TraceAuditClient;
import com.example.airegistration.gateway.config.TraceAuditClientProperties;
import com.example.airegistration.gateway.dto.TraceAuditResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class TraceAuditClientTest {

    private MockWebServer registrationServer;
    private MockWebServer registrationAgentServer;
    private MockWebServer scheduleServer;
    private MockWebServer knowledgeServer;

    @BeforeEach
    void setUp() throws IOException {
        registrationServer = new MockWebServer();
        registrationAgentServer = new MockWebServer();
        scheduleServer = new MockWebServer();
        knowledgeServer = new MockWebServer();
        registrationServer.start();
        registrationAgentServer.start();
        scheduleServer.start();
        knowledgeServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        registrationServer.close();
        registrationAgentServer.close();
        scheduleServer.close();
        knowledgeServer.close();
    }

    @Test
    void shouldFetchTraceAuditsFromAllSources() throws Exception {
        registrationAgentServer.enqueue(jsonResponse("""
                [{
                  "id":"22222222-2222-2222-2222-222222222222",
                  "executionId":"exec-001",
                  "confirmationId":"CONF-001",
                  "traceId":"trace-001",
                  "chatId":"chat-001",
                  "userId":"user-001",
                  "workflowId":"registration-workflow",
                  "intent":"CREATE",
                  "nodeId":"reserve-slot",
                  "status":"SUCCEEDED",
                  "payload":"{\\"event\\":{\\"status\\":\\"SUCCEEDED\\"}}",
                  "createdAt":"2026-05-03T02:00:00Z"
                }]
                """));
        registrationServer.enqueue(jsonResponse("""
                [{
                  "auditId":1,
                  "registrationId":"REG-001",
                  "operationType":"CREATE",
                  "traceId":"trace-001",
                  "success":true,
                  "createdAt":"2026-05-03T10:00:00+08:00"
                }]
                """));
        scheduleServer.enqueue(jsonResponse("""
                [{
                  "auditId":2,
                  "operationType":"RESERVE",
                  "traceId":"trace-001",
                  "departmentCode":"RESP",
                  "operationId":"CONF-001",
                  "success":true,
                  "remainingBefore":5,
                  "remainingAfter":4,
                  "createdAt":"2026-05-03T10:00:01+08:00"
                }]
                """));
        knowledgeServer.enqueue(jsonResponse("""
                [{
                  "id":"11111111-1111-1111-1111-111111111111",
                  "traceId":"trace-001",
                  "chatId":"chat-001",
                  "namespace":"registration-policy",
                  "corpusName":"registration",
                  "queryText":"挂号规则",
                  "topK":3,
                  "minScore":0.2,
                  "status":"HIT",
                  "hitCount":1,
                  "bestScore":0.91,
                  "latencyMs":12,
                  "hitIds":["chunk-1"],
                  "createdAt":"2026-05-03T02:00:02Z"
                }]
                """));
        TraceAuditClient client = new TraceAuditClient(WebClient.builder(), properties());

        TraceAuditResponse response = client.queryTrace("trace-001", 25).block();

        assertThat(response).isNotNull();
        assertThat(response.traceId()).isEqualTo("trace-001");
        assertThat(response.registrationWorkflowExecutions().records())
                .singleElement()
                .satisfies(record -> assertThat(record.executionId()).isEqualTo("exec-001"));
        assertThat(response.registrationAudits().records())
                .singleElement()
                .satisfies(record -> assertThat(record.registrationId()).isEqualTo("REG-001"));
        assertThat(response.scheduleInventoryAudits().records())
                .singleElement()
                .satisfies(record -> assertThat(record.operationId()).isEqualTo("CONF-001"));
        assertThat(response.knowledgeRetrievalLogs().records())
                .singleElement()
                .satisfies(record -> assertThat(record.namespace()).isEqualTo("registration-policy"));

        assertTraceRequest(registrationAgentServer.takeRequest(1, TimeUnit.SECONDS),
                "/api/registration/workflow-executions?traceId=trace-001&limit=25");
        assertTraceRequest(registrationServer.takeRequest(1, TimeUnit.SECONDS),
                "/api/mcp/registrations/audits?traceId=trace-001&limit=25");
        assertTraceRequest(scheduleServer.takeRequest(1, TimeUnit.SECONDS),
                "/api/mcp/schedules/inventory-audits?traceId=trace-001&limit=25");
        assertTraceRequest(knowledgeServer.takeRequest(1, TimeUnit.SECONDS),
                "/api/knowledge/retrieval-logs?traceId=trace-001&limit=25");
    }

    @Test
    void shouldReturnPartialResponseWhenOneSourceFails() {
        registrationAgentServer.enqueue(jsonResponse("[]"));
        registrationServer.enqueue(jsonResponse("[]"));
        scheduleServer.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
        knowledgeServer.enqueue(jsonResponse("[]"));
        TraceAuditClient client = new TraceAuditClient(WebClient.builder(), properties());

        TraceAuditResponse response = client.queryTrace("trace-partial", 10).block();

        assertThat(response).isNotNull();
        assertThat(response.registrationWorkflowExecutions().error()).isNull();
        assertThat(response.registrationAudits().error()).isNull();
        assertThat(response.scheduleInventoryAudits().records()).isEmpty();
        assertThat(response.scheduleInventoryAudits().error()).contains("503");
        assertThat(response.knowledgeRetrievalLogs().error()).isNull();
    }

    private TraceAuditClientProperties properties() {
        TraceAuditClientProperties properties = new TraceAuditClientProperties();
        properties.setRegistrationBaseUrl(registrationServer.url("/").toString());
        properties.setRegistrationAgentBaseUrl(registrationAgentServer.url("/").toString());
        properties.setScheduleBaseUrl(scheduleServer.url("/").toString());
        properties.setKnowledgeBaseUrl(knowledgeServer.url("/").toString());
        return properties;
    }

    private void assertTraceRequest(RecordedRequest request, String path) {
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo(path);
        assertThat(request.getHeader("X-Trace-Id")).isEqualTo("trace-001");
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
