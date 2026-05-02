package com.example.airegistration.registration.service.workflow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record RegistrationWorkflowTraceEvent(
        String nodeId,
        String status,
        Instant occurredAt,
        Map<String, Object> data
) {
    public RegistrationWorkflowTraceEvent {
        nodeId = requireText(nodeId, "nodeId");
        status = requireText(status, "status");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        data = copyMap(data);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
