package com.example.airegistration.ai.dto;

public record AiChatResult(
        String content,
        String model,
        int attempt
) {
}
