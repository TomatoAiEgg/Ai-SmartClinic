package com.example.airegistration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.agent.AgentRequestEnvelope;
import com.example.airegistration.agent.AgentResponseEnvelope;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.service.rag.RegistrationPolicyRagService;
import com.example.airegistration.registration.service.tool.RegistrationToolService;
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
        properties = "app.ai.registration-intent.llm-enabled=false"
)
@AutoConfigureWebTestClient
class RegistrationAgentHttpSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RegistrationToolService registrationToolService;

    @MockBean
    private AiChatClient aiChatClient;

    @MockBean
    private RegistrationPolicyRagService registrationPolicyRagService;

    @Test
    void shouldExposeRegistrationEndpointForCreatePreviewFlow() {
        when(registrationToolService.fetchDefaultPatient(anyString(), anyString())).thenReturn(Mono.just(new PatientSummary(
                "patient-1",
                "user-1",
                "Alex",
                "ID",
                "********1234",
                "138****0000",
                "SELF",
                true
        )));
        when(registrationToolService.previewCreateSlot(any(ChatRequest.class), any(), any())).thenReturn(Mono.just(new SlotSummary(
                "RESP",
                "Respiratory",
                "doctor-1",
                "Dr. Chen",
                "2026-04-25",
                "09:00",
                3
        )));
        when(registrationToolService.saveConfirmation(any(ChatRequest.class), eq(RegistrationIntent.CREATE), anyMap()))
                .thenReturn(Mono.just("confirm-1"));
        when(aiChatClient.callText(any())).thenReturn("registration smoke ok");

        ChatResponse response = webTestClient.post()
                .uri("/api/registration")
                .bodyValue(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "book a respiratory clinic slot",
                        Map.of("action", "create", "departmentCode", "RESP")
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(response.message()).isEqualTo("registration smoke ok");
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("confirmationAction", "create")
                .containsEntry("confirmationId", "confirm-1")
                .containsEntry("departmentCode", "RESP")
                .containsEntry("doctorId", "doctor-1");
        verify(registrationToolService).fetchDefaultPatient(anyString(), eq("user-1"));
        verify(registrationToolService).saveConfirmation(any(ChatRequest.class), eq(RegistrationIntent.CREATE), anyMap());
        verify(aiChatClient).callText(any());
    }

    @Test
    void shouldExposeUnifiedAgentExecuteEndpointForCreatePreviewFlow() {
        when(registrationToolService.fetchDefaultPatient(anyString(), anyString())).thenReturn(Mono.just(new PatientSummary(
                "patient-1",
                "user-1",
                "Alex",
                "ID",
                "********1234",
                "138****0000",
                "SELF",
                true
        )));
        when(registrationToolService.previewCreateSlot(any(ChatRequest.class), any(), any())).thenReturn(Mono.just(new SlotSummary(
                "RESP",
                "Respiratory",
                "doctor-1",
                "Dr. Chen",
                "2026-04-25",
                "09:00",
                3
        )));
        when(registrationToolService.saveConfirmation(any(ChatRequest.class), eq(RegistrationIntent.CREATE), anyMap()))
                .thenReturn(Mono.just("confirm-1"));
        when(aiChatClient.callText(any())).thenReturn("registration execute ok");

        AgentResponseEnvelope response = webTestClient.post()
                .uri("/api/agent/execute")
                .bodyValue(new AgentRequestEnvelope(
                        "trace-1",
                        "chat-1",
                        "user-1",
                        "book a respiratory clinic slot",
                        Map.of("action", "create", "departmentCode", "RESP"),
                        Map.of()
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentResponseEnvelope.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.route()).isEqualTo(AgentRoute.REGISTRATION.name());
        assertThat(response.message()).isEqualTo("registration execute ok");
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.confirmationId()).isEqualTo("confirm-1");
        assertThat(response.nextAction()).isEqualTo("create");
        assertThat(response.structuredData())
                .containsEntry("action", "create")
                .containsEntry("confirmationId", "confirm-1")
                .containsEntry("departmentCode", "RESP");
        assertThat(response.executionMeta().agentName()).isEqualTo("registration-agent");
        verify(registrationToolService).saveConfirmation(any(ChatRequest.class), eq(RegistrationIntent.CREATE), anyMap());
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
        assertThat(capability.agentName()).isEqualTo("registration-agent");
        assertThat(capability.supportedRoutes()).contains(AgentRoute.REGISTRATION.name());
    }
}
