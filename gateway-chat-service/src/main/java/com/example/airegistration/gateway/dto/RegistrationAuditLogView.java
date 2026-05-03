package com.example.airegistration.gateway.dto;

import java.time.OffsetDateTime;

public record RegistrationAuditLogView(
        Long auditId,
        String registrationId,
        String operationType,
        String operatorUserId,
        String chatId,
        String sourceService,
        Boolean success,
        String reason,
        String traceId,
        String requestPayload,
        String responsePayload,
        String beforeSnapshot,
        String afterSnapshot,
        OffsetDateTime createdAt
) {
}
