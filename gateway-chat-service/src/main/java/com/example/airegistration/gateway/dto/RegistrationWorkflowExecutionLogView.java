package com.example.airegistration.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationWorkflowExecutionLogView(
        UUID id,
        String executionId,
        String confirmationId,
        String traceId,
        String chatId,
        String userId,
        String workflowId,
        String intent,
        String nodeId,
        String status,
        String payload,
        Instant createdAt
) {
}
