package com.example.airegistration.ai;

import org.springframework.ai.chat.model.ChatResponse;

public record FallbackChatResult(String model, int attempt, ChatResponse response) {

    public String content() {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
