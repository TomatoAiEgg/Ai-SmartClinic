package com.example.airegistration.ai;

import com.example.airegistration.ai.dto.AiChatRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiChatRequestTest {

    @Test
    void shouldNormalizeNullCollections() {
        AiChatRequest request = new AiChatRequest("operation", "", "", null, null);

        assertThat(request.messages()).isEmpty();
        assertThat(request.attributes()).isEmpty();
    }

    @Test
    void shouldCopyMessagesAndAttributes() {
        List<String> messages = new ArrayList<>();
        messages.add("hello");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("agent", "REGISTRATION");

        AiChatRequest request = new AiChatRequest("operation", "", "", messages, attributes);
        messages.add("changed");
        attributes.put("agent", "TRIAGE");

        assertThat(request.messages()).containsExactly("hello");
        assertThat(request.attributes()).containsEntry("agent", "REGISTRATION");
        assertThatThrownBy(() -> request.messages().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.attributes().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
