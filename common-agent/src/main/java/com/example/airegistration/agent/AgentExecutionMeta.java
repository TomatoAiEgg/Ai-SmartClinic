package com.example.airegistration.agent;

import java.util.List;
import java.util.Map;

public record AgentExecutionMeta(
        String agentName,
        String model,
        long latencyMs,
        boolean fallbackUsed,
        int retryCount,
        List<String> evidenceIds,
        Map<String, Object> attributes
) {
    public AgentExecutionMeta {
        agentName = requireText(agentName, "agentName");
        model = model == null ? "" : model.trim();
        latencyMs = Math.max(0L, latencyMs);
        retryCount = Math.max(0, retryCount);
        evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    public static AgentExecutionMeta empty(String agentName) {
        return new AgentExecutionMeta(agentName, "", 0L, false, 0, List.of(), Map.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
