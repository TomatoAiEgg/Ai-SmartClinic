package com.example.airegistration.registration;

import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.service.RegistrationFlowPolicy;
import com.example.airegistration.registration.service.RegistrationIntentClassifier;
import com.example.airegistration.registration.service.RegistrationSlotExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationIntentClassifierTest {

    private final RegistrationFlowPolicy flowPolicy = new RegistrationFlowPolicy(new RegistrationSlotExtractor());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseHighConfidenceModelIntent() {
        AiChatClient aiChatClient = mock(AiChatClient.class);
        when(aiChatClient.call(any())).thenReturn(new AiChatResult(
                "{\"intent\":\"QUERY\",\"confidence\":0.95,\"reason\":\"user wants booking status\"}",
                "test-model",
                1
        ));
        RegistrationIntentClassifier classifier = new RegistrationIntentClassifier(
                flowPolicy,
                provider(aiChatClient),
                objectMapper,
                true
        );

        RegistrationIntent intent = classifier.determineIntent(new ChatRequest(
                "chat-1",
                "user-1",
                "帮我看看",
                Map.of()
        )).block();

        assertThat(intent).isEqualTo(RegistrationIntent.QUERY);
    }

    @Test
    void shouldFallbackToRuleIntentWhenModelConfidenceIsLow() {
        AiChatClient aiChatClient = mock(AiChatClient.class);
        when(aiChatClient.call(any())).thenReturn(new AiChatResult(
                "{\"intent\":\"QUERY\",\"confidence\":0.2,\"reason\":\"uncertain\"}",
                "test-model",
                1
        ));
        RegistrationIntentClassifier classifier = new RegistrationIntentClassifier(
                flowPolicy,
                provider(aiChatClient),
                objectMapper,
                true
        );

        Mono<RegistrationIntent> result = classifier.determineIntent(new ChatRequest(
                "chat-1",
                "user-1",
                "我要取消挂号",
                Map.of()
        ));

        assertThat(result.block()).isEqualTo(RegistrationIntent.CANCEL);
    }

    @Test
    void shouldFallbackToRuleIntentWhenModelClientIsUnavailable() {
        RegistrationIntentClassifier classifier = new RegistrationIntentClassifier(
                flowPolicy,
                provider(null),
                objectMapper,
                true
        );

        RegistrationIntent intent = classifier.determineIntent(new ChatRequest(
                "chat-1",
                "user-1",
                "我要改约",
                Map.of()
        )).block();

        assertThat(intent).isEqualTo(RegistrationIntent.RESCHEDULE);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AiChatClient> provider(AiChatClient aiChatClient) {
        ObjectProvider<AiChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiChatClient);
        return provider;
    }
}
