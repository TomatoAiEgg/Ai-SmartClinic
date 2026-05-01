package com.example.airegistration.registration.service.rag;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationPolicyRagServiceTest {

    @Test
    void shouldRetrieveRegistrationPolicyFromPgvector() {
        RegistrationPolicyMapper mapper = mock(RegistrationPolicyMapper.class);
        FallbackEmbeddingClient embeddingClient = mock(FallbackEmbeddingClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FallbackEmbeddingClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(embeddingClient);
        when(embeddingClient.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(new float[]{0.1F, 0.2F});
        when(mapper.search("test-policy", "CANCEL", "[0.1,0.2]", 4, 0.55D)).thenReturn(List.of(hit()));

        RegistrationPolicyRagService service = new RegistrationPolicyRagService(
                mapper,
                provider,
                new ObjectMapper(),
                true,
                "test-policy",
                4,
                0.55D
        );

        Map<String, Object> context = service.buildContext(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "cancel my appointment",
                        Map.of()
                ),
                RegistrationReplyScene.CANCEL_PREVIEW,
                true,
                Map.of("action", "cancel", "registrationId", "REG-001")
        );

        assertThat(context).containsEntry("source", "registration-policy-rag");
        assertThat(context).containsEntry("retriever", "pgvector");
        assertThat(context).containsEntry("matchCount", 1);
        assertThat(context).containsEntry("actionTag", "CANCEL");
        assertThat(context.get("referenceText").toString()).contains("Cancel policy");
        @SuppressWarnings("unchecked")
        List<String> policyIds = (List<String>) context.get("policyIds");
        assertThat(policyIds).containsExactly("policy-cancel-001");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snippets = (List<Map<String, Object>>) context.get("snippets");
        assertThat(snippets.get(0).get("metadata")).isEqualTo(Map.of("sourceType", "policy"));
        verify(mapper).search(eq("test-policy"), eq("CANCEL"), eq("[0.1,0.2]"), eq(4), eq(0.55D));
    }

    @Test
    void shouldReturnEmptyContextWhenEmbeddingClientIsUnavailable() {
        RegistrationPolicyMapper mapper = mock(RegistrationPolicyMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FallbackEmbeddingClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        RegistrationPolicyRagService service = new RegistrationPolicyRagService(
                mapper,
                provider,
                new ObjectMapper(),
                true,
                "test-policy",
                4,
                0.55D
        );

        Map<String, Object> context = service.buildContext(new ChatRequest(
                        "chat-1",
                        "user-1",
                        "cancel my appointment",
                        Map.of()
                ),
                RegistrationReplyScene.CANCEL_PREVIEW,
                true,
                Map.of("action", "cancel")
        );

        assertThat(context).containsEntry("matchCount", 0);
        assertThat(context.get("referenceText").toString()).contains("No registration policy evidence");
        verifyNoInteractions(mapper);
    }

    private RegistrationPolicyHit hit() {
        RegistrationPolicyHit hit = new RegistrationPolicyHit();
        hit.setPolicyId("policy-cancel-001");
        hit.setSourceId("hospital-policy");
        hit.setSourceName("Hospital policy");
        hit.setDocumentId("doc-001");
        hit.setPolicyType("cancel");
        hit.setActionTag("CANCEL");
        hit.setTitle("Cancel policy");
        hit.setContent("Cancellation must be confirmed before execution.");
        hit.setMetadataJson("{\"sourceType\":\"policy\"}");
        hit.setScore(0.91D);
        return hit;
    }
}
