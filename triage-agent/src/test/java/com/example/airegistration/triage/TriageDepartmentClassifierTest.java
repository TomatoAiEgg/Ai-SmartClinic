package com.example.airegistration.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.DepartmentSuggestion;
import com.example.airegistration.triage.service.TriageDepartmentClassifier;
import com.example.airegistration.triage.service.policy.TriagePolicy;
import com.example.airegistration.triage.service.prompt.TriagePromptService;
import com.example.airegistration.triage.service.rag.TriageKnowledgeHit;
import com.example.airegistration.triage.service.rag.TriageRagContext;
import com.example.airegistration.triage.service.rag.TriageRagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

class TriageDepartmentClassifierTest {

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private ObjectProvider<AiChatClient> aiChatClientProvider;

    @Mock
    private ObjectProvider<TriageRagService> ragServiceProvider;

    @Mock
    private TriageRagService ragService;

    private TriageDepartmentClassifier classifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(aiChatClientProvider.getIfAvailable()).thenReturn(aiChatClient);
        when(ragServiceProvider.getIfAvailable()).thenReturn(null);
        classifier = new TriageDepartmentClassifier(
                new TriagePolicy(),
                new TriagePromptService(),
                aiChatClientProvider,
                ragServiceProvider,
                new ObjectMapper(),
                true,
                false
        );
    }

    @Test
    void shouldUseRuleSuggestionWhenSpecialtyRuleMatches() {
        DepartmentSuggestion suggestion = classifier.suggestDepartment(new ChatRequest(
                "chat-1",
                "user-1",
                "我眼睛有点不舒服想挂个号",
                Map.of()
        )).block();

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.departmentCode()).isEqualTo("OPH");
        verify(aiChatClient, never()).call(any());
    }

    @Test
    void shouldUseModelWhenRuleFallsBackToGeneralMedicine() {
        when(aiChatClient.call(any(AiChatRequest.class))).thenReturn(new AiChatResult(
                "{\"departmentCode\":\"NEURO\",\"departmentName\":\"神经内科\",\"emergency\":false,\"confidence\":0.89,\"reason\":\"头部不适建议神经内科\"}",
                "test-model",
                1
        ));

        DepartmentSuggestion suggestion = classifier.suggestDepartment(new ChatRequest(
                "chat-1",
                "user-1",
                "我最近总是感觉脑袋发沉",
                Map.of()
        )).block();

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.departmentCode()).isEqualTo("NEURO");
        assertThat(suggestion.departmentName()).isEqualTo("神经内科");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient).call(captor.capture());
        assertThat(captor.getValue().operation()).isEqualTo("triage.department.classify");
        assertThat(captor.getValue().systemPrompt()).contains("Allowed department codes");
        assertThat(captor.getValue().userPrompt()).contains("RAG evidence");
    }

    @Test
    void shouldFallbackToRuleSuggestionWhenModelConfidenceIsLow() {
        when(aiChatClient.call(any(AiChatRequest.class))).thenReturn(new AiChatResult(
                "{\"departmentCode\":\"NEURO\",\"confidence\":0.2,\"reason\":\"uncertain\"}",
                "test-model",
                1
        ));

        DepartmentSuggestion suggestion = classifier.suggestDepartment(new ChatRequest(
                "chat-1",
                "user-1",
                "我最近总是感觉脑袋发沉",
                Map.of()
        )).block();

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.departmentCode()).isEqualTo("GEN");
    }

    @Test
    void shouldUseRagFallbackWhenModelClientIsUnavailable() {
        when(aiChatClientProvider.getIfAvailable()).thenReturn(null);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        when(ragService.retrieve(any(ChatRequest.class))).thenReturn(Mono.just(new TriageRagContext(List.of(
                hit("NEURO", "神经内科", "triage-neuro-001", 0.87D)
        ))));

        DepartmentSuggestion suggestion = classifier.suggestDepartment(new ChatRequest(
                "chat-1",
                "user-1",
                "我最近总是感觉脑袋发沉",
                Map.of()
        )).block();

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.departmentCode()).isEqualTo("NEURO");
        assertThat(suggestion.reason()).contains("triage-neuro-001");
    }

    private TriageKnowledgeHit hit(String departmentCode, String departmentName, String evidenceId, double score) {
        TriageKnowledgeHit hit = new TriageKnowledgeHit();
        hit.setDepartmentCode(departmentCode);
        hit.setDepartmentName(departmentName);
        hit.setEvidenceId(evidenceId);
        hit.setTitle("test");
        hit.setContent("test");
        hit.setScore(score);
        return hit;
    }
}
