package com.example.airegistration.ai;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.ai.service.FallbackChatClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiChatClientTest {

    @Test
    void shouldRejectRequestWithoutPromptMessages() {
        FallbackChatClient fallbackChatClient = mock(FallbackChatClient.class);
        AiChatClient aiChatClient = new AiChatClient(fallbackChatClient);

        AiChatRequest request = AiChatRequest.builder("empty").build();

        assertThatThrownBy(() -> aiChatClient.call(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain");
        verifyNoInteractions(fallbackChatClient);
    }
}
