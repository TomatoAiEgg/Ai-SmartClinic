package com.example.airegistration.registration.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.rag.core.RagRetrievalStatus;
import com.example.airegistration.rag.core.RagSearchHit;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.example.airegistration.rag.service.PgvectorRagSearchService;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationPolicyRagServiceTest {

    @Test
    void shouldRetrieveRegistrationPolicyFromPgvector() {
        PgvectorRagSearchService searchService = mock(PgvectorRagSearchService.class);
        when(searchService.search(any(RagSearchSpec.class), any(RagSearchRequest.class))).thenReturn(result(hit()));

        RegistrationPolicyRagService service = new RegistrationPolicyRagService(
                searchService,
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
        verify(searchService).search(any(RagSearchSpec.class), any(RagSearchRequest.class));
    }

    @Test
    void shouldReturnEmptyContextWhenDisabled() {
        PgvectorRagSearchService searchService = mock(PgvectorRagSearchService.class);

        RegistrationPolicyRagService service = new RegistrationPolicyRagService(
                searchService,
                new ObjectMapper(),
                false,
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
        verifyNoInteractions(searchService);
    }

    private RagSearchResult result(RagSearchHit hit) {
        return new RagSearchResult(
                "registration-policy",
                "test-policy",
                "cancel my appointment",
                RagRetrievalStatus.HIT,
                List.of(hit),
                Duration.ofMillis(12),
                null
        );
    }

    private RagSearchHit hit() {
        return new RagSearchHit(
                "policy-cancel-001",
                "Cancel policy",
                "Cancellation must be confirmed before execution.",
                "{\"sourceType\":\"policy\"}",
                0.91D,
                Map.of(
                        "sourceId", "hospital-policy",
                        "sourceName", "Hospital policy",
                        "documentId", "doc-001",
                        "policyType", "cancel",
                        "actionTag", "CANCEL"
                )
        );
    }
}
