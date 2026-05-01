package com.example.airegistration.agent;

import static org.assertj.core.api.Assertions.assertThat;

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
}
