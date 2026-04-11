package com.example.airegistration.registration;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
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

class RegistrationAgentServiceTest {

    private MockWebServer patientServer;
    private MockWebServer scheduleServer;
    private MockWebServer registrationServer;
    private RegistrationAgentService service;

    @BeforeEach
    void setUp() throws IOException {
        patientServer = new MockWebServer();
        scheduleServer = new MockWebServer();
        registrationServer = new MockWebServer();
        patientServer.start();
        scheduleServer.start();
        registrationServer.start();

        service = new RegistrationAgentService(
                WebClient.builder(),
                patientServer.url("/").toString(),
                scheduleServer.url("/").toString(),
                registrationServer.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        patientServer.close();
        scheduleServer.close();
        registrationServer.close();
    }

    @Test
    void shouldCreateUsingPreviewSlotMetadataOnConfirmation() throws Exception {
        patientServer.enqueue(jsonResponse("""
                {
                  "patientId":"patient-001",
                  "userId":"user-001",
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
                  "patientId":"patient-001",
                  "departmentCode":"RESP",
                  "doctorId":"doc-106",
                  "clinicDate":"2026-04-09",
                  "startTime":"14:30"
                }
                """));

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-001",
                "确认提交挂号",
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
        assertThat(response.message()).isEqualTo("Registration created successfully.");
        assertThat(response.data()).containsEntry("registrationId", "REG-1234ABCD");

        RecordedRequest patientRequest = patientServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest resolveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest reserveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest createRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(patientRequest.getPath()).startsWith("/api/mcp/patients/default");
        assertThat(resolveRequest.getPath()).isEqualTo("/api/mcp/schedules/resolve");
        assertThat(resolveRequest.getBody().readUtf8()).contains("\"doctorId\":\"doc-106\"");
        assertThat(reserveRequest.getPath()).isEqualTo("/api/mcp/schedules/reserve");
        assertThat(createRequest.getPath()).isEqualTo("/api/mcp/registrations");
        assertThat(createRequest.getBody().readUtf8())
                .contains("\"departmentCode\":\"RESP\"")
                .contains("\"doctorId\":\"doc-106\"")
                .contains("\"clinicDate\":\"2026-04-09\"")
                .contains("\"startTime\":\"14:30\"");
    }

    @Test
    void shouldReserveNewSlotAndReleaseOldSlotWhenRescheduling() throws Exception {
        registrationServer.enqueue(jsonResponse("""
                {
                  "registrationId":"REG-1234ABCD",
                  "status":"BOOKED",
                  "message":"Registration found.",
                  "patientId":"patient-001",
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
                  "patientId":"patient-001",
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

        ChatResponse response = service.handle(new ChatRequest(
                "chat-1",
                "user-001",
                "确认改约",
                Map.of(
                        "action", "reschedule",
                        "registrationId", "REG-1234ABCD",
                        "clinicDate", "2026-04-10",
                        "startTime", "14:30",
                        "confirmed", "true"
                )
        )).block();

        assertThat(response).isNotNull();
        assertThat(response.message()).isEqualTo("Registration rescheduled successfully.");
        assertThat(response.data()).containsEntry("status", "RESCHEDULED");

        RecordedRequest queryRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest resolveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest reserveRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest rescheduleRequest = registrationServer.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest releaseRequest = scheduleServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(queryRequest.getPath()).isEqualTo("/api/mcp/registrations/query");
        assertThat(resolveRequest.getPath()).isEqualTo("/api/mcp/schedules/resolve");
        assertThat(reserveRequest.getPath()).isEqualTo("/api/mcp/schedules/reserve");
        assertThat(rescheduleRequest.getPath()).isEqualTo("/api/mcp/registrations/reschedule");
        assertThat(releaseRequest.getPath()).isEqualTo("/api/mcp/schedules/release");
        assertThat(releaseRequest.getBody().readUtf8())
                .contains("\"clinicDate\":\"2026-04-08\"")
                .contains("\"startTime\":\"09:00\"");
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
