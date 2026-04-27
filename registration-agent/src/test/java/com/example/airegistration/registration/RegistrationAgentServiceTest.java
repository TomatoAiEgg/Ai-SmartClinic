package com.example.airegistration.registration;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.dto.ApiError;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.registration.config.McpClientProperties;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.exception.RegistrationAgentException;
import com.example.airegistration.registration.client.McpRegistrationGateway;
import com.example.airegistration.registration.service.RegistrationAgentUseCase;
import com.example.airegistration.registration.service.RegistrationConfirmationContext;
import com.example.airegistration.registration.service.RegistrationConfirmationService;
import com.example.airegistration.registration.service.RegistrationFlowPolicy;
import com.example.airegistration.registration.service.RegistrationIntentClassifier;
import com.example.airegistration.registration.service.RegistrationReplyService;
import com.example.airegistration.registration.service.RegistrationSlotExtractor;
import com.example.airegistration.registration.service.rag.RegistrationRuleService;
import com.example.airegistration.registration.service.orchestrator.RegistrationOrchestratorService;
import com.example.airegistration.registration.service.tool.RegistrationToolService;
import com.example.airegistration.registration.service.workflow.RegistrationWorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAgentServiceTest {

    private MockWebServer patientServer;
    private MockWebServer scheduleServer;
    private MockWebServer registrationServer;
    private RegistrationAgentUseCase service;
    private TestRegistrationConfirmationService confirmationService;

    @BeforeEach
    void setUp() throws IOException {
        patientServer = new MockWebServer();
        scheduleServer = new MockWebServer();
        registrationServer = new MockWebServer();
        patientServer.start();
        scheduleServer.start();
        registrationServer.start();

        McpClientProperties properties = new McpClientProperties();
        properties.setPatient(new McpClientProperties.Endpoint(patientServer.url("/").toString()));
        properties.setSchedule(new McpClientProperties.Endpoint(scheduleServer.url("/").toString()));
        properties.setRegistration(new McpClientProperties.Endpoint(registrationServer.url("/").toString()));

        McpRegistrationGateway gateway = new McpRegistrationGateway(WebClient.builder(), properties);
        RegistrationFlowPolicy flowPolicy = new RegistrationFlowPolicy(new RegistrationSlotExtractor());
        RegistrationIntentClassifier intentClassifier = Mockito.mock(RegistrationIntentClassifier.class);
        Mockito.when(intentClassifier.determineIntent(Mockito.any(ChatRequest.class)))
                .thenAnswer(invocation -> Mono.just(flowPolicy.determineIntent(invocation.getArgument(0))));
        RegistrationReplyService replyService = Mockito.mock(RegistrationReplyService.class);
        Mockito.when(replyService.reply(Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyMap()))
                .thenAnswer(invocation -> Mono.just(new ChatResponse(
                        invocation.<ChatRequest>getArgument(0).chatId(),
                        AgentRoute.REGISTRATION,
                        "AI generated reply",
                        invocation.getArgument(2),
                        Map.copyOf(invocation.getArgument(3))
                )));
        confirmationService = new TestRegistrationConfirmationService();
        RegistrationRuleService ruleService = new RegistrationRuleService(new ObjectMapper());
        RegistrationToolService toolService = new RegistrationToolService(flowPolicy, gateway, confirmationService);
        RegistrationWorkflowService workflowService =
                new RegistrationWorkflowService(flowPolicy, replyService, ruleService, toolService);
        service = new RegistrationOrchestratorService(intentClassifier, workflowService);
    }

    @AfterEach
    void tearDown() throws IOException {
        patientServer.close();
        scheduleServer.close();
        registrationServer.close();
    }

    @Test
    void shouldCreateUsingPreviewMetadataOnConfirmation() throws Exception {
        patientServer.enqueue(jsonResponse("""
                {
                  "patientId":"patient-test-001",
                  "userId":"user-test-001",
                  "name":"Alex Chen",
                  "idType":"ID_CARD",
                  "maskedIdNumber":"110***********1234",
                  "maskedPhone":"138****0001"
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-106",
                  "doctorName":"Dr. Murphy",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30",
                  "remainingSlots":4
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-106",
                  "doctorName":"Dr. Murphy",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30",
                  "remainingSlots":3
                }
                """));
        registrationServer.enqueue(jsonResponse("""
                {
                  "registrationId":"REG-1234ABCD",
                  "status":"BOOKED",
                  "message":"Registration created successfully.",
                  "patientId":"patient-test-001",
                  "departmentCode":"RESP",
                  "doctorId":"doc-106",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30"
                }
                """));

        confirmationService.seed("CONF-CREATE", RegistrationIntent.CREATE, "user-test-001", "chat-1", Map.of(
                "patientId", "patient-test-001",
                "departmentCode", "RESP",
                "doctorId", "doc-106",
                "clinicDate", "2026-04-09",
                "startTime", "14:30"
        ));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-test-001",
                "confirm create",
                Map.of(
                        "action", "create",
                        "confirmed", "true",
                        "previewed", "true",
                        "confirmationAction", "create",
                        "departmentCode", "RESP",
                        "doctorId", "doc-106",
                        "clinicDate", "2026-04-09",
                        "startTime", "14:30",
                        "confirmationId", "CONF-CREATE"
                )
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.message()).isEqualTo("AI generated reply");
        assertThat(response.data()).containsEntry("registrationId", "REG-1234ABCD");

        RecordedRequest patientRequest = patientServer.takeRequest(300, TimeUnit.MILLISECONDS);
        RecordedRequest reserveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest createRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(patientRequest).isNull();
        assertThat(reserveRequest.getPath()).isEqualTo("/api/mcp/schedules/reserve");
        assertThat(createRequest.getPath()).isEqualTo("/api/mcp/registrations");
        assertThat(createRequest.getBody().readUtf8())
                .contains("\"departmentCode\":\"RESP\"")
                .contains("\"doctorId\":\"doc-106\"")
                .contains("\"clinicDate\":\"2026-04-09\"")
                .contains("\"startTime\":\"14:30\"")
                .contains("\"externalRequestId\":\"CONF-CREATE\"")
                .contains("\"chatId\":\"chat-1\"");
    }

    @Test
    void shouldOnlyReturnPreviewWhenConfirmationHasNoPreviewContext() throws Exception {
        patientServer.enqueue(jsonResponse("""
                {
                  "patientId":"patient-test-001",
                  "userId":"user-test-001",
                  "name":"Alex Chen",
                  "idType":"ID_CARD",
                  "maskedIdNumber":"110***********1234",
                  "maskedPhone":"138****0001"
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-106",
                  "doctorName":"Dr. Murphy",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30",
                  "remainingSlots":4
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-test-001",
                "confirm create",
                Map.of(
                        "action", "create",
                        "confirmed", "true",
                        "departmentCode", "RESP",
                        "doctorId", "doc-106",
                        "clinicDate", "2026-04-09",
                        "startTime", "14:30"
                )
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("source", "registration-agent")
                .containsEntry("code", 400);

        RecordedRequest patientRequest = patientServer.takeRequest(300, TimeUnit.MILLISECONDS);
        RecordedRequest resolveRequest = scheduleServer.takeRequest(300, TimeUnit.MILLISECONDS);
        RecordedRequest reserveRequest = scheduleServer.takeRequest(300, TimeUnit.MILLISECONDS);
        RecordedRequest createRequest = registrationServer.takeRequest(300, TimeUnit.MILLISECONDS);

        assertThat(patientRequest).isNull();
        assertThat(resolveRequest).isNull();
        assertThat(reserveRequest).isNull();
        assertThat(createRequest).isNull();
    }

    @Test
    void shouldExtractExactCreateSlotFromMessage() throws Exception {
        patientServer.enqueue(jsonResponse("""
                {
                  "patientId":"patient-test-001",
                  "userId":"user-test-001",
                  "name":"Alex Chen",
                  "idType":"ID_CARD",
                  "maskedIdNumber":"110***********1234",
                  "maskedPhone":"138****0001"
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-106",
                  "doctorName":"Dr. Murphy",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30",
                  "remainingSlots":4
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-test-001",
                "book respiratory appointment doc-106 2026-04-09 14:30",
                Map.of()
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.data())
                .containsEntry("departmentCode", "RESP")
                .containsEntry("doctorId", "doc-106")
                .containsEntry("clinicDate", "2026-04-09")
                .containsEntry("startTime", "14:30");

        RecordedRequest patientRequest = patientServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest resolveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(patientRequest.getPath()).startsWith("/api/mcp/patients/default");
        assertThat(resolveRequest.getPath()).isEqualTo("/api/mcp/schedules/resolve");
        assertThat(resolveRequest.getBody().readUtf8())
                .contains("\"departmentCode\":\"RESP\"")
                .contains("\"doctorId\":\"doc-106\"")
                .contains("\"clinicDate\":\"2026-04-09\"")
                .contains("\"startTime\":\"14:30\"");
    }

    @Test
    void shouldCreatePreviewBySearchingRealSlotWhenOnlyDoctorNameProvided() throws Exception {
        patientServer.enqueue(jsonResponse("""
                {
                  "patientId":"patient-test-001",
                  "userId":"user-test-001",
                  "name":"Alex Chen",
                  "idType":"ID_CARD",
                  "maskedIdNumber":"110***********1234",
                  "maskedPhone":"138****0001"
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "slots":[
                    {
                      "departmentCode":"RESP",
                      "departmentName":"Respiratory Medicine",
                      "doctorId":"doc-106",
                      "doctorName":"Dr. Murphy",
                      "clinicDate":"2026-04-09",
                      "startTime":"14:30",
                      "remainingSlots":4
                    }
                  ]
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-test-001",
                "book Dr. Murphy appointment",
                Map.of()
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.data())
                .containsEntry("departmentCode", "RESP")
                .containsEntry("departmentName", "Respiratory Medicine")
                .containsEntry("doctorId", "doc-106")
                .containsEntry("doctorName", "Dr. Murphy");

        RecordedRequest patientRequest = patientServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest searchRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(patientRequest.getPath()).startsWith("/api/mcp/patients/default");
        assertThat(searchRequest.getPath()).isEqualTo("/api/mcp/schedules/search");
        assertThat(searchRequest.getBody().readUtf8())
                .contains("\"keyword\":\"Dr. Murphy\"");
    }

    @Test
    void shouldGuideSymptomOnlyCreateRequestsToTriageBeforeRegistration() throws Exception {
        ChatResponse response = service.handle(new ChatRequest(
                "chat-triage-first",
                "user-test-001",
                "Can I book an appointment by describing my condition",
                Map.of("action", "create")
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("requiredField", "departmentCode")
                .containsEntry("requiredAction", "triage")
                .containsEntry("suggestedRoute", "TRIAGE")
                .containsEntry("acceptsSymptomDescription", true)
                .containsEntry("nextStep", "describeSymptoms");

        RecordedRequest patientRequest = patientServer.takeRequest(300, TimeUnit.MILLISECONDS);
        RecordedRequest scheduleRequest = scheduleServer.takeRequest(300, TimeUnit.MILLISECONDS);
        RecordedRequest registrationRequest = registrationServer.takeRequest(300, TimeUnit.MILLISECONDS);

        assertThat(patientRequest).isNull();
        assertThat(scheduleRequest).isNull();
        assertThat(registrationRequest).isNull();
    }

    @Test
    void shouldReturnDeterministicMessageWhenDefaultPatientMissing() throws Exception {
        patientServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "code":404,
                          "message":"No active patient binding found for user.",
                          "details":{"userId":"user-new"}
                        }
                        """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-106",
                  "doctorName":"Dr. Murphy",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30",
                  "remainingSlots":4
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-missing-patient",
                "user-new",
                "book respiratory appointment",
                Map.of()
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.message()).isNotBlank();



        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("source", "patient-mcp-server")
                .containsEntry("requiredAction", "bindPatient");

        RecordedRequest patientRequest = patientServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest registrationRequest = registrationServer.takeRequest(300, TimeUnit.MILLISECONDS);

        assertThat(patientRequest.getPath()).startsWith("/api/mcp/patients/default");
        assertThat(registrationRequest).isNull();
    }

    @Test
    void shouldReserveNewSlotAndReleaseOldSlotWhenRescheduling() throws Exception {
        registrationServer.enqueue(jsonResponse("""
                {
                  "registrationId":"REG-1234ABCD",
                  "status":"BOOKED",
                  "message":"Registration found.",
                  "patientId":"patient-test-001",
                  "departmentCode":"RESP",
                  "doctorId":"doc-101",
                  "clinicDate":"2026-04-08",
                  "startTime":"09:00"
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-101",
                  "doctorName":"Dr. Rivera",
                  "clinicDate":"2026-04-10",
                  "startTime":"14:30",
                  "remainingSlots":4
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-101",
                  "doctorName":"Dr. Rivera",
                  "clinicDate":"2026-04-10",
                  "startTime":"14:30",
                  "remainingSlots":3
                }
                """));
        registrationServer.enqueue(jsonResponse("""
                {
                  "registrationId":"REG-1234ABCD",
                  "status":"RESCHEDULED",
                  "message":"Registration rescheduled successfully.",
                  "patientId":"patient-test-001",
                  "departmentCode":"RESP",
                  "doctorId":"doc-101",
                  "clinicDate":"2026-04-10",
                  "startTime":"14:30"
                }
                """));
        scheduleServer.enqueue(jsonResponse("""
                {
                  "departmentCode":"RESP",
                  "departmentName":"Respiratory Medicine",
                  "doctorId":"doc-101",
                  "doctorName":"Dr. Rivera",
                  "clinicDate":"2026-04-08",
                  "startTime":"09:00",
                  "remainingSlots":6
                }
                """));

        confirmationService.seed("CONF-RESCHEDULE", RegistrationIntent.RESCHEDULE, "user-test-001", "chat-1", Map.of(
                "registrationId", "REG-1234ABCD",
                "departmentCode", "RESP",
                "doctorId", "doc-101",
                "clinicDate", "2026-04-10",
                "startTime", "14:30"
        ));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-test-001",
                "confirm reschedule",
                Map.of(
                        "action", "reschedule",
                        "registrationId", "REG-1234ABCD",
                        "clinicDate", "2026-04-10",
                        "startTime", "14:30",
                        "confirmed", "true",
                        "previewed", "true",
                        "confirmationAction", "reschedule",
                        "confirmationId", "CONF-RESCHEDULE"
                )
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.message()).isEqualTo("AI generated reply");
        assertThat(response.data()).containsEntry("status", "RESCHEDULED");

        RecordedRequest queryRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest reserveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest rescheduleRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest releaseRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(queryRequest.getPath()).isEqualTo("/api/mcp/registrations/query");
        assertThat(reserveRequest.getPath()).isEqualTo("/api/mcp/schedules/reserve");
        assertThat(rescheduleRequest.getPath()).isEqualTo("/api/mcp/registrations/reschedule");
        assertThat(releaseRequest.getPath()).isEqualTo("/api/mcp/schedules/release");
        assertThat(releaseRequest.getBody().readUtf8())
                .contains("\"clinicDate\":\"2026-04-08\"")
                .contains("\"startTime\":\"09:00\"");
    }

    @Test
    void shouldTreatQueryRegistrationResultAsQueryIntent() {
        RegistrationFlowPolicy flowPolicy = new RegistrationFlowPolicy(new RegistrationSlotExtractor());

        RegistrationIntent intent = flowPolicy.determineIntent(new ChatRequest(
                "chat-query",
                "user-test-001",
                "query registration result",
                Map.of()
        ));

        assertThat(intent).isEqualTo(RegistrationIntent.QUERY);
    }

    @Test
    void shouldQueryRegistrationWithContextualShortId() throws Exception {
        registrationServer.enqueue(jsonResponse("""
                {
                  "registrationId":"REG-EB56CAF5",
                  "status":"BOOKED",
                  "message":"Registration found.",
                  "patientId":"patient-test-001",
                  "departmentCode":"RESP",
                  "doctorId":"doc-101",
                  "clinicDate":"2026-04-17",
                  "startTime":"09:00"
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-query",
                "user-test-001",
                "query appointment id eb56caf5",
                Map.of()
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.data())
                .containsEntry("registrationId", "REG-EB56CAF5")
                .containsEntry("status", "BOOKED");

        RecordedRequest queryRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(queryRequest.getPath()).isEqualTo("/api/mcp/registrations/query");
        assertThat(queryRequest.getBody().readUtf8())
                .contains("\"registrationId\":\"REG-EB56CAF5\"");
    }

    @Test
    void shouldSearchAllRegistrationsWhenQueryHasNoRegistrationId() throws Exception {
        registrationServer.enqueue(jsonResponse("""
                {
                  "records":[
                    {
                      "registrationId":"REG-1111AAAA",
                      "status":"BOOKED",
                      "message":"Registration found.",
                      "patientId":"patient-test-001",
                      "departmentCode":"RESP",
                      "doctorId":"doc-101",
                      "clinicDate":"2026-04-17",
                      "startTime":"09:00"
                    },
                    {
                      "registrationId":"REG-2222BBBB",
                      "status":"CANCELLED",
                      "message":"Registration found.",
                      "patientId":"patient-test-001",
                      "departmentCode":"DERM",
                      "doctorId":"doc-103",
                      "clinicDate":"2026-04-18",
                      "startTime":"14:00"
                    }
                  ]
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-query-list",
                "user-test-001",
                "show appointments",
                Map.of("action", "query")
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.data())
                .containsEntry("queryType", "list")
                .containsEntry("count", 2);

        RecordedRequest searchRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(searchRequest.getPath()).isEqualTo("/api/mcp/registrations/search");
        assertThat(searchRequest.getBody().readUtf8())
                .contains("\"userId\":\"user-test-001\"")
                .contains("\"clinicDate\":null")
                .contains("\"departmentCode\":null");
    }

    @Test
    void shouldSearchRegistrationsWithDateAndSymptomDepartmentFilters() throws Exception {
        registrationServer.enqueue(jsonResponse("""
                {
                  "records":[
                    {
                      "registrationId":"REG-3333CCCC",
                      "status":"BOOKED",
                      "message":"Registration found.",
                      "patientId":"patient-test-001",
                      "departmentCode":"RESP",
                      "doctorId":"doc-101",
                      "clinicDate":"2026-04-17",
                      "startTime":"09:00"
                    }
                  ]
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-query-list",
                "user-test-001",
                "show appointments on 2026-04-17",
                Map.of("action", "query", "clinicDate", "2026-04-17", "departmentCode", "RESP")
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.data())
                .containsEntry("queryType", "list")
                .containsEntry("count", 1);
        assertThat(response.data().get("filters"))
                .isEqualTo(Map.of("clinicDate", "2026-04-17", "departmentCode", "RESP"));

        RecordedRequest searchRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(searchRequest.getPath()).isEqualTo("/api/mcp/registrations/search");
        assertThat(searchRequest.getBody().readUtf8())
                .contains("\"userId\":\"user-test-001\"")
                .contains("\"clinicDate\":\"2026-04-17\"")
                .contains("\"departmentCode\":\"RESP\"");
    }

    @Test
    void shouldRejectCrossDepartmentRescheduleAccordingToWorkflowRules() throws Exception {
        registrationServer.enqueue(jsonResponse("""
                {
                  "registrationId":"REG-9999WXYZ",
                  "status":"BOOKED",
                  "message":"Registration found.",
                  "patientId":"patient-test-001",
                  "departmentCode":"RESP",
                  "doctorId":"doc-101",
                  "clinicDate":"2026-04-18",
                  "startTime":"09:00"
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-reschedule-rule",
                "user-test-001",
                "reschedule to dermatology",
                Map.of(
                        "action", "reschedule",
                        "registrationId", "REG-9999WXYZ",
                        "departmentCode", "DERM",
                        "clinicDate", "2026-04-20",
                        "startTime", "10:00"
                )
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.data())
                .containsEntry("action", "reschedule")
                .containsEntry("source", "registration-agent")
                .containsEntry("code", 400);

        RecordedRequest queryRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest scheduleRequest = scheduleServer.takeRequest(300, TimeUnit.MILLISECONDS);

        assertThat(queryRequest.getPath()).isEqualTo("/api/mcp/registrations/query");
        assertThat(scheduleRequest).isNull();
    }

    private static class TestRegistrationConfirmationService implements RegistrationConfirmationService {

        private final Map<String, RegistrationConfirmationContext> contexts = new ConcurrentHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Mono<String> save(ChatRequest request, RegistrationIntent intent, Map<String, Object> data) {
            String confirmationId = "CONF-" + sequence.incrementAndGet();
            contexts.put(confirmationId, new RegistrationConfirmationContext(
                    confirmationId,
                    expectedAction(intent),
                    request.userId(),
                    request.chatId(),
                    data
            ));
            return Mono.just(confirmationId);
        }

        @Override
        public Mono<RegistrationConfirmationContext> consume(ChatRequest request, RegistrationIntent intent) {
            String confirmationId = request.metadata().get("confirmationId");
            if (confirmationId == null || confirmationId.isBlank()) {
                return invalid("confirmationId is required.", Map.of("requiredField", "confirmationId"));
            }
            RegistrationConfirmationContext context = contexts.remove(confirmationId);
            if (context == null) {
                return invalid("confirmation context is missing.", Map.of("confirmationId", confirmationId));
            }
            if (!request.userId().equals(context.userId())) {
                return invalid("confirmation context belongs to another user.", Map.of("confirmationId", confirmationId));
            }
            String expectedAction = expectedAction(intent);
            if (!expectedAction.equals(context.action())) {
                return invalid("confirmation action mismatch.", Map.of(
                        "confirmationId", confirmationId,
                        "expectedAction", expectedAction,
                        "actualAction", context.action()
                ));
            }
            return Mono.just(context);
        }

        void seed(String confirmationId,
                  RegistrationIntent intent,
                  String userId,
                  String chatId,
                  Map<String, Object> data) {
            contexts.put(confirmationId, new RegistrationConfirmationContext(
                    confirmationId,
                    expectedAction(intent),
                    userId,
                    chatId,
                    new HashMap<>(data)
            ));
        }

        private static <T> Mono<T> invalid(String message, Map<String, Object> details) {
            return Mono.error(new RegistrationAgentException(
                    new ApiError(ApiErrorCode.INVALID_REQUEST, message, details),
                    "registration-agent"
            ));
        }

        private static String expectedAction(RegistrationIntent intent) {
            return intent.name().toLowerCase();
        }
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}

