package com.example.airegistration.ai.dto;

import java.util.List;
import java.util.Map;

public record AiChatRequest(
        String operation,
        String systemPrompt,
        String userPrompt,
        List<String> messages,
        Map<String, Object> attributes
) {

    public AiChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static Builder builder(String operation) {
        return new Builder(operation);
    }

    public static class Builder {

        private final String operation;
        private String systemPrompt = "";
        private String userPrompt = "";
        private List<String> messages = List.of();
        private Map<String, Object> attributes = Map.of();

        private Builder(String operation) {
            this.operation = operation;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder messages(List<String> messages) {
            this.messages = messages;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public AiChatRequest build() {
            return new AiChatRequest(operation, systemPrompt, userPrompt, messages, attributes);
        }
    }
}
