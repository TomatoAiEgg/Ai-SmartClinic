package com.example.airegistration.registrationmcp.entity;

import java.util.Map;

public record RegistrationAuditRecord(
        String registrationId,
        String operationType,
        String operatorUserId,
        String chatId,
        String traceId,
        boolean success,
        String reason,
        Map<String, Object> requestPayload,
        Map<String, Object> responsePayload,
        Map<String, Object> beforeSnapshot,
        Map<String, Object> afterSnapshot
) {
    public RegistrationAuditRecord {
        requestPayload = requestPayload == null ? Map.of() : Map.copyOf(requestPayload);
        responsePayload = responsePayload == null ? Map.of() : Map.copyOf(responsePayload);
        beforeSnapshot = beforeSnapshot == null ? Map.of() : Map.copyOf(beforeSnapshot);
        afterSnapshot = afterSnapshot == null ? Map.of() : Map.copyOf(afterSnapshot);
    }

    public RegistrationAuditRecord(String registrationId,
                                   String operationType,
                                   String operatorUserId,
                                   String chatId,
                                   boolean success,
                                   String reason,
                                   Map<String, Object> requestPayload,
                                   Map<String, Object> responsePayload,
                                   Map<String, Object> beforeSnapshot,
                                   Map<String, Object> afterSnapshot) {
        this(registrationId, operationType, operatorUserId, chatId, null, success, reason,
                requestPayload, responsePayload, beforeSnapshot, afterSnapshot);
    }
}
