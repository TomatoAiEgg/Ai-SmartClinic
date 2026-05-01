package com.example.airegistration.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.service.RouteDecision;
import com.example.airegistration.supervisor.service.RouteDecisionSource;
import com.example.airegistration.supervisor.service.SupervisorRouteClassifier;
import com.example.airegistration.supervisor.service.policy.SupervisorRoutePolicy;
import com.example.airegistration.supervisor.service.prompt.SupervisorPromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;

class SupervisorRouteClassifierTest {

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private ObjectProvider<AiChatClient> aiChatClientProvider;

    private SupervisorRouteClassifier classifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(aiChatClientProvider.getIfAvailable()).thenReturn(aiChatClient);
        classifier = new SupervisorRouteClassifier(
                new SupervisorRoutePolicy(),
                new SupervisorPromptService(),
                aiChatClientProvider,
                new ObjectMapper(),
                true
        );
    }

    @Test
    void shouldUseHighConfidenceModelRouteForNaturalLanguageRequests() {
        when(aiChatClient.call(any(AiChatRequest.class))).thenReturn(new AiChatResult(
                "{\"route\":\"REGISTRATION\",\"confidence\":0.91,\"reason\":\"user wants an appointment\"}",
                "test-model",
                1
        ));

        RouteDecision decision = classifier.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "please help arrange a hospital visit tomorrow",
                Map.of()
        )).block();

        assertThat(decision).isNotNull();
        assertThat(decision.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(decision.source()).isEqualTo(RouteDecisionSource.LLM);
        assertThat(decision.ruleRoute()).isEqualTo(AgentRoute.GUIDE);
        assertThat(decision.modelRoute()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(decision.confidence()).isEqualTo(0.91D);

        ArgumentCaptor<AiChatRequest> requestCaptor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient).call(requestCaptor.capture());
        AiChatRequest aiRequest = requestCaptor.getValue();
        assertThat(aiRequest.operation()).isEqualTo("supervisor.route.classify");
        assertThat(aiRequest.systemPrompt()).contains("Allowed routes");
        assertThat(aiRequest.userPrompt())
                .contains("please help arrange a hospital visit tomorrow")
                .contains("Rule route candidate: GUIDE");
    }

    @Test
    void shouldFallbackToRuleRouteWhenModelConfidenceIsLow() {
        when(aiChatClient.call(any(AiChatRequest.class))).thenReturn(new AiChatResult(
                "{\"route\":\"REGISTRATION\",\"confidence\":0.2,\"reason\":\"uncertain\"}",
                "test-model",
                1
        ));

        RouteDecision decision = classifier.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "please help arrange a hospital visit tomorrow",
                Map.of()
        )).block();

        assertThat(decision).isNotNull();
        assertThat(decision.route()).isEqualTo(AgentRoute.GUIDE);
        assertThat(decision.source()).isEqualTo(RouteDecisionSource.FALLBACK);
        assertThat(decision.ruleRoute()).isEqualTo(AgentRoute.GUIDE);
        assertThat(decision.modelRoute()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(decision.reason()).contains("low_confidence");
    }

    @Test
    void shouldProtectTriageRuleWhenModelRoutesSymptomBookingToRegistration() {
        when(aiChatClient.call(any(AiChatRequest.class))).thenReturn(new AiChatResult(
                "{\"route\":\"REGISTRATION\",\"confidence\":0.96,\"reason\":\"user wants appointment\"}",
                "test-model",
                1
        ));

        RouteDecision decision = classifier.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "I have fever and cough and want to book an appointment",
                Map.of()
        )).block();

        assertThat(decision).isNotNull();
        assertThat(decision.route()).isEqualTo(AgentRoute.TRIAGE);
        assertThat(decision.source()).isEqualTo(RouteDecisionSource.FALLBACK);
        assertThat(decision.ruleRoute()).isEqualTo(AgentRoute.TRIAGE);
        assertThat(decision.modelRoute()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(decision.reason()).contains("protected_triage_conflict");
    }

    @Test
    void shouldSkipModelWhenActionIsExplicit() {
        RouteDecision decision = classifier.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "confirm",
                Map.of("action", "create")
        )).block();

        assertThat(decision).isNotNull();
        assertThat(decision.route()).isEqualTo(AgentRoute.REGISTRATION);
        assertThat(decision.source()).isEqualTo(RouteDecisionSource.RULE);
        verify(aiChatClient, never()).call(any());
    }

    @Test
    void shouldSkipModelForHumanReviewSafetyRule() {
        RouteDecision decision = classifier.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "chest pain and shortness of breath",
                Map.of()
        )).block();

        assertThat(decision).isNotNull();
        assertThat(decision.route()).isEqualTo(AgentRoute.HUMAN_REVIEW);
        assertThat(decision.source()).isEqualTo(RouteDecisionSource.RULE);
        verify(aiChatClient, never()).call(any());
    }
}
