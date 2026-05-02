package com.example.airegistration.supervisor.service.orchestrator;

import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import java.util.LinkedHashMap;
import java.util.Map;

final class SupervisorHandoffMetadata {

    static final String MODE_TRIAGE_THEN_REGISTRATION = "TRIAGE_THEN_REGISTRATION";
    static final String STATUS_TRIAGE_EMERGENCY_STOP = "triage_emergency_stop";
    static final String STATUS_TRIAGE_NO_DEPARTMENT = "triage_no_department";
    static final String STATUS_REGISTRATION_HANDOFF = "registration_handoff";

    private static final String PREFIX = "handoff.";
    private static final String KEY_MODE = PREFIX + "mode";
    private static final String KEY_SOURCE_ROUTE = PREFIX + "sourceRoute";
    private static final String KEY_TARGET_ROUTE = PREFIX + "targetRoute";
    private static final String KEY_STATUS = PREFIX + "status";
    private static final String KEY_REASON = PREFIX + "reason";
    private static final String KEY_DEPARTMENT_CODE = PREFIX + "departmentCode";
    private static final String KEY_DEPARTMENT_NAME = PREFIX + "departmentName";
    private static final String KEY_EMERGENCY = PREFIX + "emergency";
    private static final String KEY_TRIAGE_REASON = PREFIX + "triageReason";

    private SupervisorHandoffMetadata() {
    }

    static Map<String, String> triageToRegistration(ChatResponse triageResponse, String status) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(KEY_MODE, MODE_TRIAGE_THEN_REGISTRATION);
        metadata.put(KEY_SOURCE_ROUTE, AgentRoute.TRIAGE.name());
        metadata.put(KEY_TARGET_ROUTE, AgentRoute.REGISTRATION.name());
        metadata.put(KEY_STATUS, status);
        metadata.put(KEY_REASON, reasonForStatus(status));
        putIfText(metadata, KEY_DEPARTMENT_CODE, value(triageResponse, "departmentCode"));
        putIfText(metadata, KEY_DEPARTMENT_NAME, value(triageResponse, "departmentName"));
        putIfText(metadata, KEY_EMERGENCY, value(triageResponse, "emergency"));
        putIfText(metadata, KEY_TRIAGE_REASON, value(triageResponse, "reason"));
        return Map.copyOf(metadata);
    }

    static Map<String, Object> responseSummary(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key.startsWith(PREFIX) && hasText(value)) {
                summary.put(key.substring(PREFIX.length()), value);
            }
        });
        return Map.copyOf(summary);
    }

    static String departmentCode(Map<String, String> metadata) {
        return stringValue(metadata == null ? null : metadata.get(KEY_DEPARTMENT_CODE));
    }

    static String departmentName(Map<String, String> metadata) {
        return stringValue(metadata == null ? null : metadata.get(KEY_DEPARTMENT_NAME));
    }

    static String emergency(Map<String, String> metadata) {
        return stringValue(metadata == null ? null : metadata.get(KEY_EMERGENCY));
    }

    static String triageReason(Map<String, String> metadata) {
        return stringValue(metadata == null ? null : metadata.get(KEY_TRIAGE_REASON));
    }

    private static String reasonForStatus(String status) {
        return switch (status) {
            case STATUS_TRIAGE_EMERGENCY_STOP -> "triage_reported_emergency";
            case STATUS_TRIAGE_NO_DEPARTMENT -> "triage_missing_department";
            case STATUS_REGISTRATION_HANDOFF -> "triage_department_resolved";
            default -> "triage_handoff";
        };
    }

    private static String value(ChatResponse response, String key) {
        if (response == null || response.data() == null) {
            return "";
        }
        return stringValue(response.data().get(key));
    }

    private static void putIfText(Map<String, String> metadata, String key, String value) {
        if (hasText(value)) {
            metadata.put(key, value.trim());
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
