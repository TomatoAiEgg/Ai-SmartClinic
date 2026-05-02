package com.example.airegistration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentEnvelopeTest {

    @Test
    void shouldMergeRequestMetadataWithoutMutatingOriginal() {
        AgentRequestEnvelope envelope = new AgentRequestEnvelope(
                "trace-1",
                "chat-1",
                "user-1",
                "book appointment",
                Map.of("routeReason", "symptom_with_booking"),
                Map.of()
        );

        AgentRequestEnvelope merged = envelope.withMetadata(Map.of("departmentCode", "RESP"));

        assertThat(envelope.metadata()).doesNotContainKey("departmentCode");
        assertThat(merged.metadata())
                .containsEntry("routeReason", "symptom_with_booking")
                .containsEntry("departmentCode", "RESP");
    }

    @Test
    void shouldDefaultExecutionMetaWhenMissing() {
        AgentResponseEnvelope response = new AgentResponseEnvelope(
                "TRIAGE",
                "ok",
                Map.of(),
                false,
                null,
                null,
                null
        );

        assertThat(response.executionMeta().agentName()).isEqualTo("TRIAGE");
        assertThat(response.executionMeta().fallbackUsed()).isFalse();
    }

    @Test
    void shouldMapLegacyChatContractsToAgentEnvelopes() {
        ChatRequest chatRequest = new ChatRequest(
                "chat-1",
                "user-1",
                "book respiratory appointment",
                Map.of("action", "create"),
                "trace-1"
        );

        AgentRequestEnvelope agentRequest = AgentEnvelopeMapper.toAgentRequest(chatRequest);

        assertThat(agentRequest.traceId()).isEqualTo("trace-1");
        assertThat(agentRequest.chatId()).isEqualTo("chat-1");
        assertThat(agentRequest.metadata()).containsEntry("action", "create");

        ChatResponse chatResponse = new ChatResponse(
                "chat-1",
                AgentRoute.REGISTRATION,
                "preview",
                true,
                Map.of(
                        "confirmationId", "confirm-1",
                        "confirmationAction", "create",
                        "citations", java.util.List.of("policy-1")
                )
        );

        AgentResponseEnvelope agentResponse = AgentEnvelopeMapper.toAgentResponse(chatResponse, "registration-agent", 12L);

        assertThat(agentResponse.route()).isEqualTo("REGISTRATION");
        assertThat(agentResponse.confirmationId()).isEqualTo("confirm-1");
        assertThat(agentResponse.nextAction()).isEqualTo("create");
        assertThat(agentResponse.executionMeta().agentName()).isEqualTo("registration-agent");
        assertThat(agentResponse.executionMeta().latencyMs()).isEqualTo(12L);
        assertThat(agentResponse.executionMeta().evidenceIds()).containsExactly("policy-1");
    }
}
